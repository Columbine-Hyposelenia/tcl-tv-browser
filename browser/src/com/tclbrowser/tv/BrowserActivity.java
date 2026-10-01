package com.tclbrowser.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

public class BrowserActivity extends Activity implements Tab.Callback, ChromeToolbar.Listener {

    private static final String GECKO_PKG = "com.tclbrowser.gecko";
    private static final String GECKO_APK_URL = "https://github.com/Columbine-Hyposelenia/tcl-tv-browser/releases/download/v1.0/engine.apk";
    private static final long MOUSE_HIDE_MS = 4500;

    private FrameLayout root;
    private LinearLayout chromeWrap;
    private ChromeToolbar toolbar;
    private FrameLayout contentFrame;
    private FrameLayout fullscreenFrame;
    private VirtualMouse mouse;
    private Tab tab;
    private VideoSniffer sniffer;
    private KernelInstaller installer;

    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean immersive;
    private boolean desktopMode = true;
    private boolean geckoInstalled;
    private AlertDialog downloadDialog;
    private int textZoom = 100;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideMouse = new Runnable() {
        @Override public void run() { if (mouse != null) mouse.hide(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFF0B0E16);

        chromeWrap = new LinearLayout(this);
        chromeWrap.setOrientation(LinearLayout.VERTICAL);
        chromeWrap.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        toolbar = new ChromeToolbar(this);
        chromeWrap.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentFrame = new FrameLayout(this);
        chromeWrap.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(chromeWrap);

        fullscreenFrame = new FrameLayout(this);
        fullscreenFrame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenFrame.setBackgroundColor(0xFF000000);
        fullscreenFrame.setVisibility(View.GONE);
        root.addView(fullscreenFrame);

        sniffer = new VideoSniffer();
        tab = new Tab(this, this);
        View tabView = tab.createView(this);
        contentFrame.addView(tabView);
        tab.setRequestInspector(new Tab.RequestInspector() {
            @Override public void onRequest(String url) { sniffer.inspect(url); }
        });

        mouse = new VirtualMouse(this, root);
        mouse.addToWindow(root);
        mouse.setScrollListener(new VirtualMouse.ScrollListener() {
            @Override public void onScroll(float vertical, float horizontal) {
                View target = customView != null ? customView : tab.getWebView();
                if (target == null) return;
                if (vertical > 0) {
                    if (target instanceof WebView) ((WebView) target).pageUp(false);
                    else target.scrollBy(0, -target.getHeight() / 2);
                } else if (vertical < 0) {
                    if (target instanceof WebView) ((WebView) target).pageDown(false);
                    else target.scrollBy(0, target.getHeight() / 2);
                }
                if (horizontal != 0) {
                    target.scrollBy((int) (horizontal * target.getWidth() / 2), 0);
                }
            }
        });

        installer = new KernelInstaller(this);
        toolbar.setListener(this);
        toolbar.setDesktop(desktopMode);

        geckoInstalled = isPackageInstalled(GECKO_PKG);

        String launchUrl = urlFromIntent(getIntent());
        if (launchUrl != null) {
            tab.loadUrl(launchUrl);
        } else {
            tab.loadHome();
        }
        setContentView(root);
    }

    private String urlFromIntent(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            return intent.getData().toString();
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String u = urlFromIntent(intent);
        if (u != null) tab.loadUrl(u);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tab != null) tab.onResume();
        geckoInstalled = isPackageInstalled(GECKO_PKG);
        updateNavState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tab != null) tab.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(hideMouse);
        if (downloadDialog != null && downloadDialog.isShowing()) downloadDialog.dismiss();
        if (tab != null) tab.destroy();
        if (mouse != null) mouse.releaseAll();
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void showMouse() {
        if (!mouse.isVisible()) mouse.show();
        handler.removeCallbacks(hideMouse);
        handler.postDelayed(hideMouse, MOUSE_HIDE_MS);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int code = event.getKeyCode();

        if (toolbar.getUrlBar().isEditing()) {
            if (code == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_UP) {
                toolbar.getUrlBar().clearFocusAndKeyboard();
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_ESCAPE) {
                toolbar.getUrlBar().clearFocusAndKeyboard();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (handleShortcut(code, event)) {
                return true;
            }
            if (event.getRepeatCount() == 0 && handlePageKey(code, event)) {
                return true;
            }
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (mouse.handleKeyDown(code, event)) {
                showMouse();
                return true;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (mouse.handleKeyUp(code, event)) {
                showMouse();
                return true;
            }
        }

        if (code == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_UP) {
            if (customView != null) {
                hideCustomView();
                return true;
            }
            if (immersive) {
                setImmersiveMode(false);
                return true;
            }
            if (tab.canGoBack()) {
                tab.goBack();
                return true;
            }
            if (!tab.isOnHome()) {
                tab.loadHome();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleShortcut(int code, KeyEvent event) {
        boolean ctrl = event.isCtrlPressed();
        boolean alt = event.isAltPressed();
        boolean shift = event.isShiftPressed();
        boolean first = event.getRepeatCount() == 0;

        if (code == KeyEvent.KEYCODE_F5 || (ctrl && code == KeyEvent.KEYCODE_R)) {
            tab.reload();
            return true;
        }
        if ((ctrl && code == KeyEvent.KEYCODE_L)
                || (alt && code == KeyEvent.KEYCODE_D)
                || code == KeyEvent.KEYCODE_F6) {
            focusUrlBar();
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_F) {
            if (first) showFindDialog();
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_J) {
            Downloads.openDownloads(this);
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_T) {
            if (first) { tab.loadHome(); setImmersiveMode(false); }
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_W) {
            if (first) finish();
            return true;
        }
        if (ctrl && (code == KeyEvent.KEYCODE_EQUALS || code == KeyEvent.KEYCODE_PLUS)) {
            changeZoom(10);
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_MINUS) {
            changeZoom(-10);
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_0) {
            resetZoom();
            return true;
        }
        if (alt && code == KeyEvent.KEYCODE_DPAD_LEFT) {
            tab.goBack();
            return true;
        }
        if (alt && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            tab.goForward();
            return true;
        }
        if (alt && code == KeyEvent.KEYCODE_MOVE_HOME) {
            tab.loadHome();
            return true;
        }
        if (code == KeyEvent.KEYCODE_F1) {
            if (first) showShortcutsHelp();
            return true;
        }
        if (code == KeyEvent.KEYCODE_F10) {
            if (first) openMenu();
            return true;
        }
        if (code == KeyEvent.KEYCODE_F11) {
            if (first) setImmersiveMode(!immersive);
            return true;
        }
        if (code == KeyEvent.KEYCODE_F12) {
            if (first) showVideos();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (first) openMenu();
            return true;
        }
        if (code == KeyEvent.KEYCODE_SEARCH) {
            if (first) focusUrlBar();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BOOKMARK) {
            if (first) showVideos();
            return true;
        }
        if (code == KeyEvent.KEYCODE_ESCAPE) {
            if (customView != null) {
                hideCustomView();
            } else if (immersive) {
                setImmersiveMode(false);
            } else {
                tab.stopLoading();
            }
            return true;
        }
        return false;
    }

    private boolean handlePageKey(int code, KeyEvent event) {
        boolean shift = event.isShiftPressed();
        WebView wv = tab.getWebView();
        if (wv == null) return false;

        if (code == KeyEvent.KEYCODE_SPACE) {
            if (shift) wv.pageUp(false); else wv.pageDown(false);
            return true;
        }
        if (code == KeyEvent.KEYCODE_PAGE_DOWN) {
            wv.pageDown(false);
            return true;
        }
        if (code == KeyEvent.KEYCODE_PAGE_UP) {
            wv.pageUp(false);
            return true;
        }
        if (code == KeyEvent.KEYCODE_MOVE_HOME) {
            wv.pageUp(true);
            return true;
        }
        if (code == KeyEvent.KEYCODE_MOVE_END) {
            wv.pageDown(true);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL) {
            if (tab.canGoBack()) {
                tab.goBack();
                return true;
            }
        }
        return false;
    }

    private void changeZoom(int delta) {
        WebView wv = tab.getWebView();
        if (wv == null) return;
        textZoom = wv.getSettings().getTextZoom();
        textZoom = Math.max(50, Math.min(300, textZoom + delta));
        wv.getSettings().setTextZoom(textZoom);
        toast("Zoom " + textZoom + "%");
    }

    private void resetZoom() {
        WebView wv = tab.getWebView();
        if (wv == null) return;
        textZoom = 100;
        wv.getSettings().setTextZoom(100);
        toast("Zoom 100%");
    }

    private void showFindDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Find in page");
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 20);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(input);

        final WebView wv = tab.getWebView();
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("查找")
                .setView(holder)
                .setPositiveButton("下一个", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (wv != null) wv.findNext(true);
                    }
                })
                .setNegativeButton("上一个", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (wv != null) wv.findNext(false);
                    }
                })
                .create();
        if (wv != null) {
            input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
                @Override public boolean onEditorAction(android.widget.TextView v, int actionId,
                        KeyEvent ev) {
                    String q = input.getText().toString();
                    if (q.length() > 0) wv.findAll(q);
                    return true;
                }
            });
        }
        d.show();
    }

    private void showShortcutsHelp() {
        final String[] rows = {
            "F5 / Ctrl+R  -  Reload",
            "Esc  -  Stop / Exit fullscreen",
            "Alt+Left / Alt+Right  -  Back / Forward",
            "Backspace  -  Back",
            "Ctrl+L / F6 / Alt+D  -  Address bar",
            "Space / PageDown  -  Scroll down",
            "PageUp  -  Scroll up",
            "Home / End  -  Top / Bottom",
            "Ctrl + / Ctrl -  -  Zoom in / out",
            "Ctrl+0  -  Reset zoom",
            "Ctrl+F  -  Find in page",
            "Ctrl+J  -  Downloads",
            "Ctrl+T  -  Home",
            "Ctrl+W  -  Close",
            "F10  -  Menu",
            "F11  -  Fullscreen UI",
            "F12  -  Sniff videos",
            "D-Pad  -  Move cursor, OK to click"
        };
        new AlertDialog.Builder(this)
                .setTitle("键盘快捷键")
                .setItems(rows, null)
                .setPositiveButton("Close", null)
                .show();
    }

    private Toast toastInstance;

    private void toast(String message) {
        try {
            if (toastInstance == null) {
                toastInstance = android.widget.Toast.makeText(this, message,
                        android.widget.Toast.LENGTH_SHORT);
            } else {
                toastInstance.setText(message);
            }
            toastInstance.show();
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                && (event.getAction() == MotionEvent.ACTION_HOVER_MOVE
                    || event.getAction() == MotionEvent.ACTION_SCROLL)) {
            if (mouse.isVisible()) mouse.hide();
        }
        return super.onGenericMotionEvent(event);
    }

    private void focusUrlBar() {
        toolbar.getUrlBar().focusForInput();
    }

    private void setImmersiveMode(boolean value) {
        immersive = value;
        toolbar.setVisibility(value ? View.GONE : View.VISIBLE);
    }

    private void updateNavState() {
        if (tab == null || toolbar == null) return;
        toolbar.setCanBack(tab.canGoBack());
        toolbar.setCanForward(tab.canGoForward());
    }

    @Override
    public void onProgress(Tab t, int progress) {
        toolbar.setProgress(progress);
        toolbar.setLoading(progress < 100);
        updateNavState();
    }

    @Override
    public void onTitleChanged(Tab t, String title) {
        updateNavState();
    }

    @Override
    public void onUrlChanged(Tab t, String url) {
        if (url == null || url.contains("home.local") || url.startsWith("about:")) {
            toolbar.setUrl("");
        } else {
            toolbar.setUrl(url);
        }
        updateNavState();
    }

    @Override
    public void onPageFinished(Tab t, String url) {
        toolbar.setProgress(100);
        toolbar.setLoading(false);
        updateNavState();
    }

    @Override
    public void onEnterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        customView = view;
        customCallback = callback;
        fullscreenFrame.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        fullscreenFrame.setVisibility(View.VISIBLE);
        chromeWrap.setVisibility(View.GONE);
        mouse.setTarget(fullscreenFrame);
        showMouse();
    }

    @Override
    public void onExitFullscreen() {
        hideCustomView();
    }

    private void hideCustomView() {
        if (customView != null) {
            fullscreenFrame.removeView(customView);
            if (customCallback != null) {
                try { customCallback.onCustomViewHidden(); } catch (Throwable ignored) {}
                customCallback = null;
            }
            customView = null;
        }
        fullscreenFrame.setVisibility(View.GONE);
        chromeWrap.setVisibility(View.VISIBLE);
        mouse.setTarget(root);
    }

    @Override
    public void onDownload(String url, String userAgent, String contentDisposition,
            String mimeType, long contentLength) {
        Downloads.enqueue(this, url, userAgent, contentDisposition, mimeType);
    }

    @Override
    public void onOpenNewTab(String url) {
        if (url != null) tab.loadUrl(url);
    }

    @Override
    public void onShowFileChooser(ValueCallback<Uri[]> callback) {
        try { callback.onReceiveValue(null); } catch (Throwable ignored) {}
    }

    @Override
    public void onBack() {
        if (!tab.goBack()) updateNavState();
    }

    @Override
    public void onForward() {
        tab.goForward();
    }

    @Override
    public void onReload() {
        tab.reload();
    }

    @Override
    public void onUrlSubmit(String text) {
        tab.loadUrl(text);
        toolbar.getUrlBar().clearFocusAndKeyboard();
    }

    @Override
    public void onToggleDesktop() {
        desktopMode = !desktopMode;
        tab.setDesktopMode(desktopMode);
        toolbar.setDesktop(desktopMode);
        tab.reload();
    }

    @Override
    public void onMenu() {
        openMenu();
    }

    private void openMenu() {
        final String[] items = {
            desktopMode ? "桌面版：开" : "桌面版：关",
            "刷新",
            immersive ? "退出全屏" : "全屏界面",
            "嗅探视频 (" + sniffer.count() + ")",
            "下载管理",
            "主页",
            "清除缓存",
            geckoInstalled ? "启动 Gecko 浏览器" : "安装 Gecko 引擎"
        };
        new AlertDialog.Builder(this)
                .setTitle("菜单")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: onToggleDesktop(); break;
                            case 1: tab.reload(); break;
                            case 2: setImmersiveMode(!immersive); break;
                            case 3: showVideos(); break;
                            case 4: Downloads.openDownloads(BrowserActivity.this); break;
                            case 5: tab.loadHome(); break;
                            case 6: clearCache(); break;
                            case 7: handleGecko(); break;
                        }
                    }
                })
                .show();
    }

    private void showVideos() {
        final List<Map.Entry<String, String>> videos = sniffer.getVideos();
        if (videos.isEmpty()) {
            alert("本页未找到视频。");
            return;
        }
        String[] names = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++) {
            names[i] = videos.get(i).getValue();
        }
        new AlertDialog.Builder(this)
                .setTitle("视频")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        tab.loadUrl(videos.get(which).getKey());
                    }
                })
                .show();
    }

    private void handleGecko() {
        if (geckoInstalled) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(GECKO_PKG);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            }
            return;
        }
        downloadGecko();
    }

    private void downloadGecko() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 20);
        box.setPadding(pad, pad / 2, pad, pad / 2);
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        final TextView status = new TextView(this);
        status.setText("正在连接...");
        box.addView(bar);
        box.addView(status);

        downloadDialog = new AlertDialog.Builder(this)
                .setTitle("Gecko 引擎")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        installer.cancel();
                    }
                })
                .create();
        downloadDialog.show();

        installer.download(GECKO_APK_URL, new KernelInstaller.Listener() {
            @Override public void onStatus(String s) {
                status.setText(s);
            }
            @Override public void onProgress(int percent, long downloaded, long total) {
                bar.setProgress(percent);
                status.setText(percent + "%   " + formatSize(downloaded)
                        + " / " + formatSize(total));
            }
            @Override public void onReady(String apkPath) {
                if (downloadDialog != null && downloadDialog.isShowing()) {
                    downloadDialog.dismiss();
                }
                KernelInstaller.installApk(BrowserActivity.this, apkPath);
            }
            @Override public void onError(String error) {
                status.setText("失败：" + error);
                if (downloadDialog != null) {
                    downloadDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("关闭");
                }
            }
        });
    }

    private void clearCache() {
        try {
            if (tab.getWebView() != null) {
                tab.getWebView().clearCache(true);
                tab.getWebView().clearFormData();
            }
            deleteDatabase("webview.db");
            deleteDatabase("webviewCache.db");
            alert("缓存已清除。");
        } catch (Throwable t) {
            alert("清除缓存失败。");
        }
    }

    private void alert(String message) {
        new AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 MB";
        double mb = bytes / 1048576.0;
        if (mb >= 1) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0);
    }
}
