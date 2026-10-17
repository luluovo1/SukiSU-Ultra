package com.sukisu.ultra.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.ModuleRepositoryImpl
import com.sukisu.ultra.ui.util.AppIconCache
import com.sukisu.ultra.ui.util.createRootShell
import com.sukisu.ultra.ui.util.withMainUserUid
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel
import java.io.File

/**
 * Maximum number of activity recreations after WebView renderer deaths
 * before giving up and showing an error (prevents restart loops caused by
 * an environment that kills the renderer immediately on every load).
 */
internal const val MAX_RENDERER_RESTARTS = 2

/**
 * Build the intent for the next renderer recovery attempt. The counter
 * travels in the intent so a crash loop can not reset it by recreating
 * the activity.
 */
internal fun Activity.nextRestartIntent(): Intent {
    val next = Intent(intent)
    next.putExtra(EXTRA_RENDERER_RESTARTS, intent.getIntExtra(EXTRA_RENDERER_RESTARTS, 0) + 1)
    return next
}

internal const val EXTRA_RENDERER_RESTARTS = "webui_renderer_restarts"

fun Activity.setTaskDescription(label: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(label))
    } else {
        val taskDescription = ActivityManager.TaskDescription.Builder()
            .setLabel(label)
            .build()
        setTaskDescription(taskDescription)
    }
}

@SuppressLint("SetJavaScriptEnabled")
internal suspend fun prepareWebView(
    activity: Activity,
    moduleId: String,
    webUIState: WebUIState,
) {
    withContext(Dispatchers.IO) {
        val repo = ModuleRepositoryImpl()
        val modules = repo.getModules().getOrDefault(emptyList())
        val moduleInfo = modules.find { info -> info.id == moduleId }

        if (moduleInfo == null) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.no_such_module, moduleId))
            }
            return@withContext
        }

        if (!moduleInfo.hasWebUi || !moduleInfo.enabled || moduleInfo.update || moduleInfo.remove) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.module_unavailable, moduleInfo.name))
            }
            return@withContext
        }

        webUIState.moduleName = moduleInfo.name
        webUIState.modDir = "/data/adb/modules/${moduleId}"

        if (SuperUserViewModel.apps.isEmpty()) {
            SuperUserViewModel().fetchAppList()
        }
        val shell = createRootShell(true)
        webUIState.rootShell = shell

        // Mirror the module webroot into app-private storage before the
        // WebView starts loading. The previous behavior read every asset
        // through the root shell (one `dd` command per 8 KiB block — see
        // libsu ShellIO), which stalled module pages with large assets
        // (e.g. multi-megabyte fonts) long enough for the WebView renderer
        // to be killed, leaving a permanently white page.
        val webRoot = File("${webUIState.modDir}/webroot")
        val assetHandler = SuFilePathHandler(
            activity,
            webRoot,
            shell,
            { webUIState.currentInsets },
            { enable -> webUIState.isInsetsEnabled = enable }
        )
        assetHandler.prepareCache()

        withContext(Dispatchers.Main) {
            activity.setTaskDescription(activity.getString(R.string.app_name) + " - ${moduleInfo.name}")

            val webView = WebView(activity)
            webView.setBackgroundColor(Color.TRANSPARENT)

            val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            WebView.setWebContentsDebuggingEnabled(prefs.getBoolean("enable_web_debugging", false))

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
            }

            val webViewAssetLoader = WebViewAssetLoader.Builder()
                .setDomain("mui.kernelsu.org")
                .addPathHandler("/", assetHandler)
                .build()

            // WebViewClient
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url
                    if (url.scheme.equals("ksu", ignoreCase = true) && url.host.equals("icon", ignoreCase = true)) {
                        val packageName = url.path?.substring(1)
                        if (!packageName.isNullOrEmpty()) {
                            val appInfo = SuperUserViewModel.apps
                                .find { it.packageName == packageName }
                                ?.packageInfo?.applicationInfo
                            if (appInfo != null) {
                                val icon = AppIconCache.loadIconSync(activity, appInfo.withMainUserUid(activity), 512)
                                val stream = java.io.ByteArrayOutputStream()
                                icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                return WebResourceResponse(
                                    "image/png", null, 200, "OK",
                                    mapOf("Access-Control-Allow-Origin" to "*"),
                                    java.io.ByteArrayInputStream(stream.toByteArray())
                                )
                            } else {
                                val errorMsg = "No such package"
                                val errorStream = java.io.ByteArrayInputStream(errorMsg.toByteArray(Charsets.UTF_8))
                                return WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    404,
                                    "Not Found",
                                    mapOf("Access-Control-Allow-Origin" to "*"),
                                    errorStream
                                )
                            }
                        }
                    }
                    return webViewAssetLoader.shouldInterceptRequest(url)
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    webUIState.webCanGoBack = view?.canGoBack() ?: false
                    if (webUIState.isInsetsEnabled) webUIState.webView?.evaluateJavascript(webUIState.currentInsets.js, null)
                    super.doUpdateVisitedHistory(view, url, isReload)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    // Surface main-document failures instead of leaving an
                    // empty white page; sub-resource failures are logged
                    // only (pages may reference optional assets).
                    val isMainDocument = request?.isForMainFrame ?: false
                    val message = error?.description?.toString() ?: "unknown error"
                    android.util.Log.e(
                        "WebUI",
                        "load error mainFrame=$isMainDocument url=${request?.url}: $message"
                    )
                    if (isMainDocument && view != null) {
                        webUIState.uiEvent = WebUIEvent.Error(
                            activity.getString(R.string.module_webui_load_failed, message)
                        )
                    }
                    super.onReceivedError(view, request, error)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: android.webkit.RenderProcessGoneDetail?
                ): Boolean {
                    // Without this callback a killed renderer leaves the
                    // WebView showing a blank surface forever while the
                    // activity stays alive. Rebuild the page from scratch
                    // instead, retrying at most twice so a crash loop can
                    // not pin the UI in a restart cycle.
                    val wasCrash = detail?.didCrash() != false
                    val currentIntent = activity.intent
                    val restarts = currentIntent?.getIntExtra(EXTRA_RENDERER_RESTARTS, 0) ?: 0
                    android.util.Log.e(
                        "WebUI",
                        "render process gone (crash=$wasCrash, restarts=$restarts)"
                    )
                    if (restarts >= MAX_RENDERER_RESTARTS) {
                        webUIState.uiEvent = WebUIEvent.Error(
                            activity.getString(R.string.module_webui_renderer_crashed)
                        )
                    } else if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.startActivity(activity.nextRestartIntent())
                        activity.finish()
                    }
                    // We handled the situation: do not let the framework
                    // kill the whole process as well.
                    return true
                }
            }

            // WebChromeClient
            webView.webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    if (message == null || result == null) return false
                    webUIState.uiEvent = WebUIEvent.ShowAlert(message, result)
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    if (message == null || result == null) return false
                    webUIState.uiEvent = WebUIEvent.ShowConfirm(message, result)
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    if (message == null || result == null || defaultValue == null) return false
                    webUIState.uiEvent = WebUIEvent.ShowPrompt(message, defaultValue, result)
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
                ): Boolean {
                    webUIState.filePathCallback?.onReceiveValue(null)
                    webUIState.filePathCallback = filePathCallback

                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    webUIState.uiEvent = WebUIEvent.ShowFileChooser(intent)
                    return true
                }
            }

            // JS Interface
            val webviewInterface = WebViewInterface(webUIState)
            webUIState.webView = webView
            webView.addJavascriptInterface(webviewInterface, "ksu")
            webUIState.uiEvent = WebUIEvent.WebViewReady
        }
    }
}