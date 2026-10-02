package com.tclbrowser.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tab {

    public interface Callback {
        void onProgress(Tab tab, int progress);
        void onTitleChanged(Tab tab, String title);
        void onUrlChanged(Tab tab, String url);
        void onPageFinished(Tab tab, String url);
        void onEnterFullscreen(View view, WebChromeClient.CustomViewCallback callback);
        void onExitFullscreen();
        void onDownload(String url, String userAgent, String contentDisposition,
                        String mimeType, long contentLength);
        void onOpenNewTab(String url);
        void onShowFileChooser(ValueCallback<android.net.Uri[]> callback);
    }

    public interface RequestInspector {
        void onRequest(String url);
    }

    private final Activity activity;
    private final Callback callback;
    private WebView webView;
    private FrameLayout container;
    private String pendingUrl;
    private boolean desktopMode = true;
    private boolean fullscreen;
    private RequestInspector inspector;

    public Tab(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public View createView(Context context) {
        container = new FrameLayout(context);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView = new WebView(context);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configureWebView();
        container.addView(webView);
        if (pendingUrl != null) {
            if ("about:home".equals(pendingUrl)) {
                loadHome();
            } else {
                webView.loadUrl(pendingUrl);
            }
            pendingUrl = null;
        }
        return container;
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setDefaultTextEncodingName("UTF-8");
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setGeolocationEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        applyUserAgent();

        // Dark background to avoid white flash when loading pages / going back
        webView.setBackgroundColor(0xFF0B0E14);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollbarFadingEnabled(true);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new BrowserWebViewClient());
        webView.setWebChromeClient(new BrowserWebChromeClient());
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                if (callback != null) {
                    callback.onDownload(url, userAgent, contentDisposition, mimeType, contentLength);
                }
            }
        });
    }

    private void applyUserAgent() {
        WebSettings s = webView.getSettings();
        if (!desktopMode) {
            s.setUserAgentString(null);
            return;
        }
        String base = WebSettings.getDefaultUserAgent(activity);
        String chromeVer = extractChromeVersion(base);
        String desktop = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/" + chromeVer + " Safari/537.36";
        s.setUserAgentString(desktop);
    }

    private static String extractChromeVersion(String ua) {
        Matcher m = Pattern.compile("Chrome/([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)").matcher(ua);
        if (m.find()) return m.group(1);
        Matcher m2 = Pattern.compile("Chrome/([0-9]+\\.[0-9]+)").matcher(ua);
        if (m2.find()) return m2.group(1) + ".0.0";
        return "95.0.4638.0";
    }

    public void setDesktopMode(boolean desktop) {
        this.desktopMode = desktop;
        if (webView != null) {
            applyUserAgent();
        }
    }

    public void setRequestInspector(RequestInspector inspector) {
        this.inspector = inspector;
    }

    public void loadUrl(String url) {
        if (url == null) return;
        String normalized = Urls.normalize(url);
        if (webView != null) {
            webView.loadUrl(normalized);
        } else {
            pendingUrl = normalized;
        }
    }

    public void loadHome() {
        if (webView != null) {
            // Reset zoom so the home page always renders at 100% regardless
            // of the zoom level left by the previous page.
            webView.setInitialScale(0);
            webView.getSettings().setTextZoom(100);
            webView.loadDataWithBaseURL("https://home.local/", HomePage.html(),
                    "text/html", "UTF-8", null);
        } else {
            pendingUrl = "about:home";
        }
    }

    public void reload() {
        if (webView != null) webView.reload();
    }

    public void stopLoading() {
        if (webView != null) webView.stopLoading();
    }

    public boolean goBack() {
        if (webView != null && webView.canGoBack()) {
            // Let WebView restore the previous page from its history / cache.
            // This avoids the flicker caused by re-loading the home page via
            // loadDataWithBaseURL on every back navigation.
            webView.goBack();
            return true;
        }
        return false;
    }

    public boolean isOnHome() {
        String u = getUrl();
        return u != null && u.contains("home.local");
    }

    public boolean goForward() {
        if (webView != null && webView.canGoForward()) {
            webView.goForward();
            return true;
        }
        return false;
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public boolean canGoForward() {
        return webView != null && webView.canGoForward();
    }

    public String getUrl() {
        return webView != null ? webView.getUrl() : pendingUrl;
    }

    public String getTitle() {
        return webView != null ? webView.getTitle() : null;
    }

    public WebView getWebView() {
        return webView;
    }

    public View getView() {
        return container;
    }

    public void onPause() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    public void onResume() {
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    public void destroy() {
        if (webView != null) {
            try {
                ((ViewGroup) container).removeView(webView);
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {}
            webView = null;
        }
    }

    public void injectJs(String script) {
        if (webView != null) {
            webView.evaluateJavascript(script, null);
        }
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    private class BrowserWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url == null) return false;
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return false;
            }
            if (url.startsWith("intent://")) {
                Intents.handleNonHttp(activity, url);
                return true;
            }
            if (URLUtil.isNetworkUrl(url)) return false;
            Intents.handleNonHttp(activity, url);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view,
                WebResourceRequest request) {
            if (inspector != null && request != null && request.getUrl() != null) {
                inspector.onRequest(request.getUrl().toString());
            }
            return null;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (inspector != null && url != null) {
                inspector.onRequest(url);
            }
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (callback != null) callback.onUrlChanged(Tab.this, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (callback != null) {
                callback.onUrlChanged(Tab.this, url);
                callback.onPageFinished(Tab.this, url);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                                    String failingUrl) {
            String page = ErrorPages.page(errorCode, description, failingUrl);
            view.loadDataWithBaseURL("about:blank", page, "text/html", "UTF-8", null);
        }
    }

    private class BrowserWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (callback != null) callback.onProgress(Tab.this, newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (callback != null) callback.onTitleChanged(Tab.this, title);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            fullscreen = true;
            if (Tab.this.callback != null) Tab.this.callback.onEnterFullscreen(view, callback);
        }

        @Override
        public void onHideCustomView() {
            fullscreen = false;
            if (Tab.this.callback != null) Tab.this.callback.onExitFullscreen();
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Dialogs.jsAlert(activity, message, result);
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            Dialogs.jsConfirm(activity, message, result);
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                   JsPromptResult result) {
            Dialogs.jsPrompt(activity, message, defaultValue, result);
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.grant(request.getResources());
        }

        @Override
        public boolean onShowFileChooser(WebView webView,
                ValueCallback<android.net.Uri[]> filePathCallback,
                FileChooserParams fileChooserParams) {
            if (callback != null) {
                callback.onShowFileChooser(filePathCallback);
                return true;
            }
            return false;
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return true;
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                Message resultMsg) {
            return false;
        }
    }
}
