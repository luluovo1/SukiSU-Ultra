package com.sukisu.ultra.ui.webui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.webkit.WebViewAssetLoader;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Handler class to open files from file system by root access
 * For more information about android storage please refer to
 * <a href="https://developer.android.com/guide/topics/data/data-storage">Android Developers
 * Docs: Data and file storage overview</a>.
 * <p class="note">
 * To avoid leaking user or app data to the web, make sure to choose {@code directory}
 * carefully, and assume any file under this directory could be accessed by any web page subject
 * to same-origin rules.
 * <p>
 * A typical usage would be like:
 * <pre class="prettyprint">
 * File publicDir = new File(context.getFilesDir(), "public");
 * // Host "files/public/" in app's data directory under:
 * // http://appassets.androidplatform.net/public/...
 * WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
 *          .addPathHandler("/public/", new InternalStoragePathHandler(context, publicDir))
 *          .build();
 * </pre>
 */
public final class SuFilePathHandler implements WebViewAssetLoader.PathHandler {
    /**
     * Default value to be used as MIME type if guessing MIME type failed.
     */
    public static final String DEFAULT_MIME_TYPE = "text/plain";
    private static final String TAG = "SuFilePathHandler";
    /**
     * Forbidden subdirectories of {@link Context#getDataDir} that cannot be exposed by this
     * handler. They are forbidden as they often contain sensitive information.
     * <p class="note">
     * Note: Any future addition to this list will be considered breaking changes to the API.
     */
    private static final String[] FORBIDDEN_DATA_DIRS =
            new String[]{"/data/data", "/data/system"};

    @NonNull
    private final File mDirectory;

    /**
     * App-private mirror of {@link #mDirectory}; null when unavailable.
     */
    private File mCacheDir;

    /**
     * True once {@link #prepareCache()} successfully built the mirror.
     */
    private volatile boolean mCacheReady;

    private final Shell mShell;
    private final InsetsSupplier mInsetsSupplier;
    private final OnInsetsRequestedListener mOnInsetsRequestedListener;
    private final Context mContext;

    /**
     * Creates PathHandler for app's internal storage.
     * The directory to be exposed must be inside either the application's internal data
     * directory {@link Context#getDataDir} or cache directory {@link Context#getCacheDir}.
     * External storage is not supported for security reasons, as other apps with
     * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE} may be able to modify the
     * files.
     * <p>
     * Exposing the entire data or cache directory is not permitted, to avoid accidentally
     * exposing sensitive application files to the web. Certain existing subdirectories of
     * {@link Context#getDataDir} are also not permitted as they are often sensitive.
     * These files are ({@code "app_webview/"}, {@code "databases/"}, {@code "lib/"},
     * {@code "shared_prefs/"} and {@code "code_cache/"}).
     * <p>
     * The application should typically use a dedicated subdirectory for the files it intends to
     * expose and keep them separate from other files.
     *
     * @param context                   {@link Context} that is used to access app's internal storage.
     * @param directory                 the absolute path of the exposed app internal storage directory from
     *                                  which files can be loaded.
     * @param rootShell                 {@link Shell} instance with root access to read files.
     * @param insetsSupplier            {@link InsetsSupplier} to provide window insets for styling web content.
     * @param onInsetsRequestedListener {@link OnInsetsRequestedListener} to notify when insets are requested.
     * @throws IllegalArgumentException if the directory is not allowed.
     */
    public SuFilePathHandler(@NonNull Context context, @NonNull File directory, Shell rootShell, @NonNull InsetsSupplier insetsSupplier, OnInsetsRequestedListener onInsetsRequestedListener) {
        try {
            mContext = context;
            mInsetsSupplier = insetsSupplier;
            mOnInsetsRequestedListener = onInsetsRequestedListener;
            mDirectory = new File(getCanonicalDirPath(directory));
            if (!isAllowedInternalStorageDir(context)) {
                throw new IllegalArgumentException("The given directory \"" + directory
                        + "\" doesn't exist under an allowed app internal storage directory");
            }
            mShell = rootShell;

            // Local mirror of the module webroot. Files are fetched through
            // the root shell only once when the mirror is (re)built; every
            // WebView request afterwards is served from app-private storage.
            // The previous in-place reading path executed a `dd ... ; echo`
            // command per 8 KiB block (see libsu ShellIO), so a single 18 MB
            // font in a module webroot cost thousands of shell round trips
            // and stalled page loads long enough for the renderer process to
            // be killed, which showed up as an unresponsive white page.
            File cacheBase = context.getExternalCacheDir();
            if (cacheBase == null) cacheBase = context.getCacheDir();
            String moduleId = new File(mDirectory.getPath()).getName();
            mCacheDir = new File(cacheBase, "webui-" + moduleId);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to resolve the canonical path for the given directory: "
                            + directory.getPath(), e);
        }
    }

    /**
     * Mirror the exposed module directory into app-private storage so that
     * WebView requests never touch the root shell on the hot path. Safe to
     * call on a background thread; re-copies only when the source directory
     * metadata changed.
     *
     * @return true when the cache is usable.
     */
    public boolean prepareCache() {
        if (mCacheDir == null || mShell == null) return false;
        // getCanonicalFileIfChild() below requires the parent directory to
        // exist; on the very first open the mirror does not exist yet.
        if (!mCacheDir.isDirectory() && !mCacheDir.mkdirs()) {
            return false;
        }
        try {
            // Compare source and cache timestamps: the mirror is only
            // rebuilt when the module was (re)installed or updated.
            String srcStamp = firstLine(runRoot("stat -c %Y '" + mDirectory.getPath() + "'"));
            if (srcStamp == null) return false;
            File stampFile = new File(mCacheDir, ".stamp");
            if (stampFile.isFile()) {
                String cached = firstLine(runRoot("cat '" + stampFile.getPath() + "'"));
                if (srcStamp.equals(cached)) {
                    mCacheReady = true;
                    return true;
                }
            }
            // (Re)build the mirror. `cp -a` keeps permissions and mtime; the
            // explicit chmod makes the tree readable for the renderer process
            // which runs under a sandboxed uid.
            String dst = mCacheDir.getPath();
            String cmd =
                    "rm -rf '" + dst + "' && " +
                    "mkdir -p '" + dst + "' && " +
                    "cp -a '" + mDirectory.getPath() + "'/. '" + dst + "/' && " +
                    "chmod -R u=rwX,go=rX '" + dst + "' && " +
                    "echo '" + srcStamp + "' > '" + dst + "/.stamp'";
            String out = runRoot(cmd);
            if (out == null) return false;
            // cp -a preserved the source directory mtime; the stamp file is
            // written last, so a torn copy can not masquerade as fresh.
            boolean ok = new File(dst, ".stamp").isFile();
            mCacheReady = ok;
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare webroot cache", e);
            return false;
        }
    }

    private String runRoot(String cmd) {
        try {
            java.util.List<String> stdout = new java.util.ArrayList<>();
            java.util.List<String> stderr = new java.util.ArrayList<>();
            Shell.Result result = mShell.newJob().add(cmd).to(stdout, stderr).exec();
            if (!result.isSuccess() && stdout.isEmpty()) return null;
            return String.join("\n", stdout);
        } catch (Exception e) {
            Log.e(TAG, "root command failed: " + cmd, e);
            return null;
        }
    }

    private static String firstLine(String s) {
        if (s == null) return null;
        int idx = s.indexOf('\n');
        String line = (idx >= 0 ? s.substring(0, idx) : s).trim();
        return line.isEmpty() ? null : line;
    }

    /**
     * Resolve the request path against the local mirror, refusing path
     * traversal outside of it.
     */
    private File cachedFile(String path) {
        if (mCacheDir == null || !mCacheReady) return null;
        try {
            File file = getCanonicalFileIfChild(mCacheDir, path);
            if (file != null && file.isFile()) return file;
        } catch (IOException ignored) {
        }
        return null;
    }

    private InputStream openCached(File file) throws IOException {
        InputStream fis = new FileInputStream(file);
        return handleSvgzStream(file.getPath(), fis);
    }

    public static String getCanonicalDirPath(@NonNull File file) throws IOException {
        String canonicalPath = file.getCanonicalPath();
        if (!canonicalPath.endsWith("/")) canonicalPath += "/";
        return canonicalPath;
    }

    public static File getCanonicalFileIfChild(@NonNull File parent, @NonNull String child)
            throws IOException {
        String parentCanonicalPath = getCanonicalDirPath(parent);
        String childCanonicalPath = new File(parent, child).getCanonicalPath();
        if (childCanonicalPath.startsWith(parentCanonicalPath)) {
            return new File(childCanonicalPath);
        }
        return null;
    }

    @NonNull
    private static InputStream handleSvgzStream(@NonNull String path,
                                                @NonNull InputStream stream) throws IOException {
        return path.endsWith(".svgz") ? new GZIPInputStream(stream) : stream;
    }

    public static InputStream openFile(@NonNull File file, @NonNull Shell shell) throws IOException {
        SuFile suFile = new SuFile(file.getAbsolutePath());
        suFile.setShell(shell);
        InputStream fis = SuFileInputStream.open(suFile);
        return handleSvgzStream(file.getPath(), fis);
    }

    /**
     * Use {@link MimeUtil#getMimeFromFileName} to guess MIME type or return the
     * {@link #DEFAULT_MIME_TYPE} if it can't guess.
     *
     * @param filePath path of the file to guess its MIME type.
     * @return MIME type guessed from file extension or {@link #DEFAULT_MIME_TYPE}.
     */
    @NonNull
    public static String guessMimeType(@NonNull String filePath) {
        String mimeType = MimeUtil.getMimeFromFileName(filePath);
        return mimeType == null ? DEFAULT_MIME_TYPE : mimeType;
    }

    private boolean isAllowedInternalStorageDir(@NonNull Context context) throws IOException {
        String dir = getCanonicalDirPath(mDirectory);

        for (String forbiddenPath : FORBIDDEN_DATA_DIRS) {
            if (dir.startsWith(forbiddenPath)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Opens the requested file from the exposed data directory.
     * <p>
     * The matched prefix path used shouldn't be a prefix of a real web path. Thus, if the
     * requested file cannot be found or is outside the mounted directory a
     * {@link WebResourceResponse} object with a {@code null} {@link InputStream} will be
     * returned instead of {@code null}. This saves the time of falling back to network and
     * trying to resolve a path that doesn't exist. A {@link WebResourceResponse} with
     * {@code null} {@link InputStream} will be received as an HTTP response with status code
     * {@code 404} and no body.
     * <p class="note">
     * The MIME type for the file will be determined from the file's extension using
     * {@link java.net.URLConnection#guessContentTypeFromName}. Developers should ensure that
     * files are named using standard file extensions. If the file does not have a
     * recognised extension, {@code "text/plain"} will be used by default.
     *
     * @param path the suffix path to be handled.
     * @return {@link WebResourceResponse} for the requested file.
     */
    @Override
    @WorkerThread
    @NonNull
    public WebResourceResponse handle(@NonNull String path) {
        if ("internal/insets.css".equals(path)) {
            if (mOnInsetsRequestedListener != null) {
                mOnInsetsRequestedListener.onInsetsRequested(true);
            }
            String css = mInsetsSupplier.get().getCss();
            return new WebResourceResponse(
                    "text/css",
                    "utf-8",
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        if ("internal/colors.css".equals(path)) {
            SharedPreferences prefs = mContext.getSharedPreferences("settings", Context.MODE_PRIVATE);
            int colorMode = prefs.getInt("color_mode", 0);
            String uiMode = prefs.getString("ui_mode", "miuix");
            String css = "";
            if ((colorMode >= 3 && colorMode <= 6) || "material".equals(uiMode)) {
                css = MonetColorsProvider.INSTANCE.getColorsCss();
            }
            return new WebResourceResponse(
                    "text/css",
                    "utf-8",
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        try {
            // Hot path: serve from the local mirror prepared by
            // prepareCache(). Only cache misses fall back to the slow
            // root-shell reading path below.
            File cached = cachedFile(path);
            if (cached != null) {
                InputStream cachedStream = openCached(cached);
                String cachedMime = guessMimeType(path);
                return new WebResourceResponse(cachedMime, null, cachedStream);
            }
            File file = getCanonicalFileIfChild(mDirectory, path);
            if (file != null) {
                InputStream is = openFile(file, mShell);
                String mimeType = guessMimeType(path);
                return new WebResourceResponse(mimeType, null, is);
            } else {
                Log.e(TAG, String.format(
                        "The requested file: %s is outside the mounted directory: %s", path,
                        mDirectory));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error opening the requested path: " + path, e);
        }
        return new WebResourceResponse(null, null, null);
    }

    public interface InsetsSupplier {
        @NonNull
        Insets get();
    }

    public interface OnInsetsRequestedListener {
        void onInsetsRequested(boolean enable);
    }
}
