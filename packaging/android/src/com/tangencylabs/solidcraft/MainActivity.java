package com.tangencylabs.solidcraft;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SolidCraft CAD v0.21.0 — Samsung Galaxy S26 Ultra Edition (r3 Play Store Release Build)
 */
public class MainActivity extends Activity {
    private static final String TAG = "SolidCraftS26U";
    private static final int FILE_CHOOSER_RESULT_CODE = 1001;

    private FrameLayout mRoot;
    private WebView mWebView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private ServerSocket mServerSocket;
    private ExecutorService mServerPool;
    private volatile int mLoopbackPort = 0;
    private volatile boolean mRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(Color.parseColor("#14161B"));
        setContentView(mRoot);

        try {
            configureImmersiveWindow();
        } catch (Throwable t) {
            Log.w(TAG, "configureImmersiveWindow warning: " + t.getMessage());
        }

        try {
            startLoopbackServer();
        } catch (Throwable t) {
            Log.w(TAG, "startLoopbackServer warning: " + t.getMessage());
        }

        try {
            initWebView();
        } catch (Throwable t) {
            showDiagnosticError("Startup exception in initWebView", t);
        }
    }

    private void initWebView() {
        try {
            boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            WebView.setWebContentsDebuggingEnabled(isDebuggable);
        } catch (Throwable ignored) {
        }

        mWebView = new WebView(this);
        mWebView.setBackgroundColor(Color.parseColor("#14161B"));

        configureWebSettings(mWebView.getSettings());

        try {
            mWebView.clearCache(true);
        } catch (Throwable ignored) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ServiceWorkerController swController = ServiceWorkerController.getInstance();
                swController.getServiceWorkerWebSettings().setAllowContentAccess(true);
                swController.getServiceWorkerWebSettings().setAllowFileAccess(true);
                swController.getServiceWorkerWebSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                swController.setServiceWorkerClient(new ServiceWorkerClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                        if (request != null && request.getUrl() != null) {
                            return interceptAssetRequest(request.getUrl().toString());
                        }
                        return null;
                    }
                });
            }
        } catch (Throwable ignored) {
        }

        mWebView.addJavascriptInterface(new SolidCraftAndroidBridge(), "SolidCraftAndroid");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    if (request != null && request.getUrl() != null) {
                        WebResourceResponse resp = interceptAssetRequest(request.getUrl().toString());
                        if (resp != null) {
                            return resp;
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "shouldInterceptRequest error: " + t.getMessage());
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                try {
                    if (url != null) {
                        WebResourceResponse resp = interceptAssetRequest(url);
                        if (resp != null) {
                            return resp;
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "shouldInterceptRequest(url) error: " + t.getMessage());
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() != null) {
                    if (request == null) return false;
                }
                String url = request.getUrl().toString();
                if (url.startsWith("http://127.0.0.1") ||
                    url.startsWith("http://localhost") ||
                    url.startsWith("https://appassets.androidplatform.net") ||
                    url.startsWith("http://appassets.androidplatform.net")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Throwable ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyFullscreenInsets();
                pushScreenMetricsToWeb();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    String failingUrl = request.getUrl() != null ? request.getUrl().toString() : "";
                    Log.w(TAG, "Main frame load error on " + failingUrl);
                    if (!failingUrl.startsWith("https://appassets.androidplatform.net")) {
                        view.loadUrl("https://appassets.androidplatform.net/index.html?sample");
                        return;
                    }
                }
                super.onReceivedError(view, request, error);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.w(TAG, "WebView render process gone, recreating WebView");
                try {
                    if (mRoot != null && mWebView != null) {
                        mRoot.removeView(mWebView);
                        mWebView.destroy();
                        mWebView = null;
                    }
                    initWebView();
                } catch (Throwable ignored) {
                }
                return true;
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                if (cm != null) {
                    Log.d(TAG, "[JS:" + cm.lineNumber() + "] " + cm.message());
                }
                return true;
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(Intent.createChooser(intent, "Open CAD / STEP / STL File"), FILE_CHOOSER_RESULT_CODE);
                    return true;
                } catch (Throwable t) {
                    mFilePathCallback = null;
                    return false;
                }
            }
        });

        mRoot.addView(mWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        final String startUrl = (mLoopbackPort > 0)
                ? ("http://127.0.0.1:" + mLoopbackPort + "/index.html?sample")
                : "https://appassets.androidplatform.net/index.html?sample";
        Log.i(TAG, "Launching SolidCraft S26 Ultra at " + startUrl);
        mWebView.loadUrl(startUrl);
    }

    private void showDiagnosticError(String title, Throwable t) {
        Log.e(TAG, title, t);
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#14161B"));
        tv.setPadding(48, 96, 48, 48);
        tv.setTextSize(13f);
        tv.setText(title + ":\n\n" + sw.toString());
        sv.addView(tv);
        if (mRoot != null) {
            mRoot.removeAllViews();
            mRoot.addView(sv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.TOP));
        }
    }

    private void configureWebSettings(WebSettings settings) {
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

    private void configureImmersiveWindow() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    private void applyFullscreenInsets() {
        try {
            Window window = getWindow();
            if (window == null) {
                return;
            }
            View decor = window.getDecorView();
            if (decor == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                @SuppressWarnings("deprecation")
                int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
                decor.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {
        }
    }

    private WebResourceResponse interceptAssetRequest(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        if (!(rawUrl.startsWith("https://appassets.androidplatform.net")
                || rawUrl.startsWith("http://appassets.androidplatform.net")
                || rawUrl.startsWith("http://127.0.0.1")
                || rawUrl.startsWith("http://localhost"))) {
            return null;
        }

        String cleanPath = extractAssetPath(rawUrl);
        if (cleanPath.equals("sw.js") || cleanPath.endsWith("/sw.js")) {
            byte[] noopSw = "self.addEventListener('install',function(){self.skipWaiting();});self.addEventListener('activate',function(e){e.waitUntil(self.registration.unregister());});"
                    .getBytes(StandardCharsets.UTF_8);
            return buildResponse("application/javascript", "UTF-8", new ByteArrayInputStream(noopSw), noopSw.length);
        }
        if (cleanPath.equals("favicon.ico")) {
            cleanPath = "icons/32x32/ai.storyteller.solidcraft.png";
        }

        AssetStreamHolder holder = openAssetStream(cleanPath);
        if (holder == null && !cleanPath.contains(".")) {
            holder = openAssetStream("index.html");
            cleanPath = "index.html";
        }
        if (holder == null) {
            Log.w(TAG, "Asset not found in interceptAssetRequest: " + cleanPath + " (url=" + rawUrl + ")");
            return null;
        }

        String mime = guessMimeType(cleanPath);
        String encoding = (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("svg"))
                ? "UTF-8"
                : null;
        return buildResponse(mime, encoding, holder.stream, holder.length);
    }

    private WebResourceResponse buildResponse(String mimeType, String encoding, InputStream stream, long length) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "*");
        headers.put("Cross-Origin-Opener-Policy", "same-origin");
        headers.put("Cross-Origin-Embedder-Policy", "require-corp");
        headers.put("Cross-Origin-Resource-Policy", "cross-origin");
        headers.put("Cache-Control", "no-store, no-cache, must-revalidate");
        if (length >= 0) {
            headers.put("Content-Length", Long.toString(length));
        }
        return new WebResourceResponse(mimeType, encoding, 200, "OK", headers, stream);
    }

    private static class AssetStreamHolder {
        final InputStream stream;
        final long length;

        AssetStreamHolder(InputStream stream, long length) {
            this.stream = stream;
            this.length = length;
        }
    }

    private AssetStreamHolder openAssetStream(String cleanPath) {
        try {
            AssetFileDescriptor afd = getAssets().openFd(cleanPath);
            long len = afd.getLength();
            return new AssetStreamHolder(new BufferedInputStream(afd.createInputStream(), 65536), len);
        } catch (Throwable ignored) {
        }

        try {
            InputStream in = getAssets().open(cleanPath, android.content.res.AssetManager.ACCESS_STREAMING);
            return new AssetStreamHolder(new BufferedInputStream(in, 65536), -1L);
        } catch (Throwable ignored) {
        }

        try {
            List<String> apkPaths = new ArrayList<>();
            ApplicationInfo appInfo = getApplicationInfo();
            if (appInfo.sourceDir != null) {
                apkPaths.add(appInfo.sourceDir);
            }
            if (appInfo.splitSourceDirs != null) {
                for (String split : appInfo.splitSourceDirs) {
                    if (split != null) apkPaths.add(split);
                }
            }
            for (String apkPath : apkPaths) {
                final ZipFile zipFile = new ZipFile(apkPath);
                ZipEntry entry = zipFile.getEntry("assets/" + cleanPath);
                if (entry != null) {
                    InputStream raw = zipFile.getInputStream(entry);
                    long size = entry.getSize();
                    InputStream wrapped = new FilterInputStream(new BufferedInputStream(raw, 65536)) {
                        @Override
                        public void close() throws IOException {
                            try {
                                super.close();
                            } finally {
                                try {
                                    zipFile.close();
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    };
                    return new AssetStreamHolder(wrapped, size);
                } else {
                    zipFile.close();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "ZipFile fallback error for " + cleanPath + ": " + t.getMessage());
        }
        return null;
    }

    private static String extractAssetPath(String urlOrPath) {
        String p = urlOrPath;
        int schemeIdx = p.indexOf("://");
        if (schemeIdx >= 0) {
            int slashIdx = p.indexOf('/', schemeIdx + 3);
            p = (slashIdx >= 0) ? p.substring(slashIdx + 1) : "";
        }
        int qIdx = p.indexOf('?');
        if (qIdx >= 0) {
            p = p.substring(0, qIdx);
        }
        int hIdx = p.indexOf('#');
        if (hIdx >= 0) {
            p = p.substring(0, hIdx);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isEmpty() || p.endsWith("/")) {
            p = p + "index.html";
        }
        return p;
    }

    private static String guessMimeType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".json") || lower.endsWith(".webmanifest")) return "application/json";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private void startLoopbackServer() {
        final CountDownLatch readyLatch = new CountDownLatch(1);
        mRunning = true;
        mServerPool = Executors.newCachedThreadPool();
        mServerPool.execute(() -> {
            try {
                mServerSocket = new ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"));
                mLoopbackPort = mServerSocket.getLocalPort();
                readyLatch.countDown();
                while (mRunning && !mServerSocket.isClosed()) {
                    final Socket client = mServerSocket.accept();
                    mServerPool.execute(() -> handleLoopbackClient(client));
                }
            } catch (Throwable t) {
                readyLatch.countDown();
                if (mRunning) {
                    Log.w(TAG, "Loopback server stopped: " + t.getMessage());
                }
            }
        });
        try {
            readyLatch.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleLoopbackClient(Socket client) {
        try (Socket sock = client) {
            sock.setSoTimeout(15000);
            sock.setTcpNoDelay(true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String rawTarget = parts.length > 1 ? parts[1] : "/";

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // drain
            }

            OutputStream out = sock.getOutputStream();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                String resp = "HTTP/1.1 204 No Content\r\n"
                        + "Access-Control-Allow-Origin: *\r\n"
                        + "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n"
                        + "Access-Control-Allow-Headers: *\r\n"
                        + "Connection: close\r\n\r\n";
                out.write(resp.getBytes(StandardCharsets.US_ASCII));
                out.flush();
                return;
            }

            String cleanPath = extractAssetPath(rawTarget);
            if (cleanPath.equals("sw.js") || cleanPath.endsWith("/sw.js")) {
                byte[] body = "self.addEventListener('install',function(){self.skipWaiting();});".getBytes(StandardCharsets.UTF_8);
                writeHttpHeaders(out, "200 OK", "application/javascript; charset=utf-8", body.length);
                if (!"HEAD".equalsIgnoreCase(method)) {
                    out.write(body);
                }
                out.flush();
                return;
            }
            if (cleanPath.equals("favicon.ico")) {
                cleanPath = "icons/32x32/ai.storyteller.solidcraft.png";
            }

            AssetStreamHolder holder = openAssetStream(cleanPath);
            if (holder == null && !cleanPath.contains(".")) {
                holder = openAssetStream("index.html");
                cleanPath = "index.html";
            }
            if (holder == null) {
                byte[] notFound = ("Not Found: " + cleanPath).getBytes(StandardCharsets.UTF_8);
                writeHttpHeaders(out, "404 Not Found", "text/plain; charset=utf-8", notFound.length);
                out.write(notFound);
                out.flush();
                return;
            }

            String mime = guessMimeType(cleanPath);
            try (InputStream assetIn = holder.stream) {
                writeHttpHeaders(out, "200 OK", mime, holder.length);
                if (!"HEAD".equalsIgnoreCase(method)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = assetIn.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }
                out.flush();
            }
        } catch (Throwable ignored) {
        }
    }

    private void writeHttpHeaders(OutputStream out, String status, String contentType, long contentLength) throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(status).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        if (contentLength >= 0) {
            sb.append("Content-Length: ").append(contentLength).append("\r\n");
        }
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Cross-Origin-Opener-Policy: same-origin\r\n");
        sb.append("Cross-Origin-Embedder-Policy: require-corp\r\n");
        sb.append("Cross-Origin-Resource-Policy: cross-origin\r\n");
        sb.append("Cache-Control: no-store\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private float computeAutoScaleForDisplay() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthPx = dm.widthPixels;
        int heightPx = dm.heightPixels;
        float density = dm.density > 0.5f ? dm.density : 3.0f;
        float wDp = widthPx / density;
        float hDp = heightPx / density;
        boolean landscape = wDp >= hDp;
        float targetW = landscape ? 1220.0f : 680.0f;
        float targetH = landscape ? 560.0f : 900.0f;
        float raw = Math.min(wDp / targetW, hDp / targetH);
        float minScale = landscape ? 0.65f : 0.55f;
        return Math.max(minScale, Math.min(1.25f, Math.round(raw * 50.0f) / 50.0f));
    }

    private void pushScreenMetricsToWeb() {
        if (mWebView == null) {
            return;
        }
        final float recommendedScale = computeAutoScaleForDisplay();
        mWebView.post(() -> {
            if (mWebView == null) return;
            String js = "try { window.__SOLIDCRAFT_S26_RECOMMENDED_SCALE = " + recommendedScale + "; "
                    + "window.dispatchEvent(new Event('resize')); } catch(e) {}";
            mWebView.evaluateJavascript(js, null);
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyFullscreenInsets();
        pushScreenMetricsToWeb();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullscreenInsets();
            pushScreenMetricsToWeb();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (mFilePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
                mFilePathCallback.onReceiveValue(results);
                mFilePathCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null) {
            mWebView.evaluateJavascript(
                    "try { document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', code: 'Escape', bubbles: true})); } catch(e) {}",
                    null);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        mRunning = false;
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (Throwable ignored) {
            }
        }
        if (mServerPool != null) {
            mServerPool.shutdownNow();
        }
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }

    public class SolidCraftAndroidBridge {
        @JavascriptInterface
        public float getRecommendedUiScale() {
            return computeAutoScaleForDisplay();
        }

        @JavascriptInterface
        public String getDeviceModel() {
            return Build.MANUFACTURER + " " + Build.MODEL;
        }

        @JavascriptInterface
        public void saveDownloadedFile(String fileName, String base64Data) {
            try {
                String safeName = (fileName == null || fileName.trim().isEmpty())
                        ? "solidcraft-export.stl"
                        : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                boolean savedViaMediaStore = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        ContentResolver resolver = getContentResolver();
                        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri != null) {
                            try (OutputStream os = resolver.openOutputStream(uri)) {
                                if (os != null) {
                                    os.write(bytes);
                                    savedViaMediaStore = true;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (!savedViaMediaStore) {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                        dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    }
                    File outFile = new File(dir, safeName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(bytes);
                    }
                }
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Saved to Downloads: " + safeName,
                        Toast.LENGTH_LONG).show());
                Log.i(TAG, "Exported CAD file: " + safeName);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to save downloaded file: " + t.getMessage());
            }
        }
    }
}
