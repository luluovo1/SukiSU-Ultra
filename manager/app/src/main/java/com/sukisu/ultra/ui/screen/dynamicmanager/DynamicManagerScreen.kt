package com.sukisu.ultra.ui.screen.dynamicmanager

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.screen.susfs.component.ConfirmDialog
import com.sukisu.ultra.ui.util.dynamicManagerClear
import com.sukisu.ultra.ui.util.dynamicManagerGet
import com.sukisu.ultra.ui.util.dynamicManagerSet
import com.sukisu.ultra.ui.util.dynamicManagerSetApkFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HASH_REGEX = Regex("^[0-9a-f]{64}$")
private const val MIN_CERT_SIZE = 0x100
private const val MAX_CERT_SIZE = 0x1000

@Composable
fun DynamicManagerScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var currentSignature by remember { mutableStateOf("") }
    var manualDialogVisible by remember { mutableStateOf(false) }
    var clearConfirmVisible by remember { mutableStateOf(false) }

    fun toast(resId: Int) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }

    suspend fun refreshState() {
        isLoading = true
        currentSignature = withContext(Dispatchers.IO) { dynamicManagerGet() }
        isLoading = false
    }

    LaunchedEffect(Unit) { refreshState() }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { dynamicManagerSetApkFromUri(uri) }
            toast(if (ok) R.string.dynamic_manager_success else R.string.dynamic_manager_failed)
            if (ok) refreshState()
        }
    }

    val actions = DynamicManagerActions(
        onBack = { navigator.pop() },
        onRefresh = { scope.launch { refreshState() } },
        onPickApk = { apkPicker.launch(arrayOf("*/*")) },
        onOpenManualDialog = { manualDialogVisible = true },
        onDismissManualDialog = { manualDialogVisible = false },
        onApplyManual = { sizeText, hash ->
            val size = sizeText.trim().toIntOrNull()
            val normalizedHash = hash.trim().lowercase()
            if (size == null || size !in MIN_CERT_SIZE..MAX_CERT_SIZE || !HASH_REGEX.matches(normalizedHash)) {
                toast(R.string.dynamic_manager_invalid_input)
            } else {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { dynamicManagerSet(size, normalizedHash) }
                    toast(if (ok) R.string.dynamic_manager_success else R.string.dynamic_manager_failed)
                    if (ok) {
                        manualDialogVisible = false
                        refreshState()
                    }
                }
            }
        },
        onOpenClearConfirm = { clearConfirmVisible = true },
        onDismissClearConfirm = { clearConfirmVisible = false },
        onConfirmClear = {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { dynamicManagerClear() }
                toast(if (ok) R.string.dynamic_manager_cleared else R.string.dynamic_manager_failed)
                clearConfirmVisible = false
                if (ok) refreshState()
            }
        },
    )

    val state = DynamicManagerUiState(
        isLoading = isLoading,
        currentSignature = currentSignature,
        manualDialogVisible = manualDialogVisible,
        clearConfirmVisible = clearConfirmVisible,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> DynamicManagerMiuix(state = state, actions = actions)
        UiMode.Material -> DynamicManagerMaterial(state = state, actions = actions)
    }

    if (clearConfirmVisible) {
        ConfirmDialog(
            showDialog = true,
            onDismiss = actions.onDismissClearConfirm,
            onConfirm = actions.onConfirmClear,
            titleRes = R.string.dynamic_manager_clear_confirm_title,
            messageRes = R.string.dynamic_manager_clear_confirm_message,
        )
    }
}
