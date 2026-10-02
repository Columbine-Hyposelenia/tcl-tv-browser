package com.tclbrowser.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class GeckoEngineActivity extends Activity {

    private static final String CLOUD_URL = "https://ys.mihoyo.com/cloud/";

    private static GeckoRuntime runtime;

    private FrameLayout root;
    private LinearLayout chromeWrap;
    private ChromeToolbar toolbar;
    private FrameLayout contentFrame;
    private FrameLayout popupFrame;
    private GeckoView geckoView;
    private GeckoSession session;
    private GeckoView popupView;
    private GeckoSession popupSession;
    private VirtualMouse mouse;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean desktopMode = true;
    private boolean immersive;
    private boolean pageFullscreen;
    private boolean canBack;
    private boolean canForward;
    private boolean loading;
    private int crashCount;
    private int killCount;
    private Toast toastInstance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        buildLayout();
        ensureRuntime();
        createMainSession();

        String launchUrl = urlFromIntent(getIntent());
        if (launchUrl != null) {
            session.loadUri(launchUrl);
        } else {
            loadHome();
        }
        setContentView(root);
    }

    private void buildLayout() {
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

        popupFrame = new FrameLayout(this);
        popupFrame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        popupFrame.setBackgroundColor(0xFF000000);
        popupFrame.setVisibility(View.GONE);
        root.addView(popupFrame);

        mouse = new VirtualMouse(this, root);
        mouse.addToWindow(root);
        mouse.setScrollListener(new VirtualMouse.ScrollListener() {
            @Override public void onScroll(float vertical, float horizontal) {
                View target = popupFrame.getVisibility() == View.VISIBLE ? popupView : geckoView;
                if (target == null) return;
                int vc = vertical < 0 ? KeyEvent.KEYCODE_PAGE_DOWN : KeyEvent.KEYCODE_PAGE_UP;
                target.dispatchKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, vc, 0));
                target.dispatchKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, vc, 0));
            }
        });

        toolbar.setListener(new ToolbarListener());
        toolbar.setDesktop(desktopMode);
    }

    private void ensureRuntime() {
        if (runtime != null) {
            runtime.attachTo(this);
            return;
        }
        GeckoRuntimeSettings s = new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .webFontsEnabled(true)
                .aboutConfigEnabled(true)
                .consoleOutput(false)
                .remoteDebuggingEnabled(false)
                .contentBlocking(new ContentBlocking.Settings.Builder()
                        .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                        .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                        .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                        .build())
                .build();
        runtime = GeckoRuntime.create(this, s);
        runtime.setDelegate(new GeckoRuntime.Delegate() {
            @Override public void onShutdown() { finish(); }
        });
    }

    private GeckoSessionSettings buildSettings() {
        return new GeckoSessionSettings.Builder()
                .userAgentMode(desktopMode
                        ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(desktopMode
                        ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                        : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER)
                .allowJavascript(true)
                .build();
    }

    private void createMainSession() {
        geckoView = new GeckoView(this);
        geckoView.setFocusable(true);
        geckoView.setFocusableInTouchMode(true);
        geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        contentFrame.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        session = new GeckoSession(buildSettings());
        attachDelegates(session, false);
        geckoView.setSession(session);
        session.open(runtime);
        geckoView.requestFocus();
    }

    private void attachDelegates(final GeckoSession s, final boolean isPopup) {
        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession session, String title) {
                if (!isPopup) updateNavState();
            }
            @Override public void onFullScreen(GeckoSession session, boolean fullscreen) {
                if (isPopup) return;
                pageFullscreen = fullscreen;
                if (fullscreen) {
                    toolbar.setVisibility(View.GONE);
                    mouse.setTarget(root);
                    showMouse();
                } else {
                    toolbar.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onCloseRequest(GeckoSession session) {
                if (isPopup) closePopup();
            }
            @Override public void onExternalResponse(GeckoSession session, WebResponse response) {
                handleDownload(response);
            }
            @Override public void onCrash(GeckoSession s) {
                crashCount++;
                if (isPopup) {
                    recoverPopup("网页进程崩溃，弹窗已自动关闭");
                } else {
                    recoverMain("网页进程崩溃，正在自动恢复…");
                }
            }
            @Override public void onKill(GeckoSession s) {
                killCount++;
                if (isPopup) {
                    recoverPopup("内存不足，弹窗已自动关闭");
                } else {
                    recoverMain("系统内存不足，网页被终止，正在恢复…");
                }
            }
        });

        s.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession session, String url) {
                loading = true;
                if (!isPopup) toolbar.setLoading(true);
            }
            @Override public void onPageStop(GeckoSession session, boolean success) {
                loading = false;
                if (!isPopup) {
                    toolbar.setLoading(false);
                    toolbar.setProgress(100);
                }
            }
            @Override public void onProgressChange(GeckoSession session, int progress) {
                if (!isPopup) {
                    toolbar.setProgress(progress);
                    toolbar.setLoading(progress < 100);
                }
            }
            @Override public void onSecurityChange(GeckoSession session,
                    GeckoSession.ProgressDelegate.SecurityInformation info) {
            }
        });

        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onLocationChange(GeckoSession session, String url,
                    java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                    Boolean hasUserGesture) {
                if (isPopup) return;
                if (url == null || url.startsWith("data:text/html")) {
                    toolbar.setUrl("");
                } else {
                    toolbar.setUrl(url);
                }
                updateNavState();
            }
            @Override public void onCanGoBack(GeckoSession session, boolean value) {
                canBack = value;
                if (!isPopup) toolbar.setCanBack(value);
            }
            @Override public void onCanGoForward(GeckoSession session, boolean value) {
                canForward = value;
                if (!isPopup) toolbar.setCanForward(value);
            }
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session,
                    GeckoSession.NavigationDelegate.LoadRequest request) {
                return GeckoResult.allow();
            }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession session,
                    String uri) {
                return openPopup(uri);
            }
            @Override public GeckoResult<String> onLoadError(GeckoSession session, String uri,
                    WebRequestError	error) {
                return GeckoResult.fromValue(buildErrorPage(uri, error));
            }
        });

        s.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session,
                    GeckoSession.PermissionDelegate.ContentPermission perm) {
                return GeckoResult.fromValue(
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW);
            }
            @Override public void onAndroidPermissionsRequest(GeckoSession session,
                    String[] permissions, Callback callback) {
                if (callback != null) {
                    callback.grant();
                }
            }
        });

        s.setPromptDelegate(new EnginePrompt(this, s));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private GeckoSession activeSession() {
        return popupFrame.getVisibility() == View.VISIBLE && popupSession != null
                ? popupSession : session;
    }

    private View activeView() {
        return popupFrame.getVisibility() == View.VISIBLE && popupView != null
                ? popupView : geckoView;
    }

    private void recoverMain(final String message) {
        toast(message);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    // After a crash the session is closed; reopen it to recover,
                    // then reload the home page. open() on an open session throws,
                    // which is harmless and ignored.
                    try { session.open(runtime); } catch (Throwable ignored) {}
                    geckoView.requestFocus();
                    loadHome();
                } catch (Throwable t) {
                    toast("自动恢复失败，请退出后重新打开浏览器");
                }
            }
        }, 300);
    }

    private void recoverPopup(final String message) {
        toast(message);
        handler.post(new Runnable() {
            @Override public void run() { closePopup(); }
        });
    }

    private void loadHome() {
        // Base64 data URI avoids the form-URL-encoding issues (spaces
        // becoming '+', quotes being percent-encoded) that broke rendering.
        String encoded = Base64.encodeToString(
                HomePage.html().getBytes(java.nio.charset.Charset.forName("UTF-8")),
                Base64.NO_WRAP);
        session.loadUri("data:text/html;base64," + encoded);
        toolbar.setUrl("");
    }

    private void navigate(String text) {
        String url = Urls.normalize(text);
        if (popupFrame.getVisibility() == View.VISIBLE) {
            popupSession.loadUri(url);
        } else {
            session.loadUri(url);
        }
        toolbar.getUrlBar().clearFocusAndKeyboard();
    }

    private void toggleDesktop() {
        desktopMode = !desktopMode;
        applyDesktopTo(session);
        if (popupSession != null) applyDesktopTo(popupSession);
        toolbar.setDesktop(desktopMode);
        activeSession().reload();
    }

    private void applyDesktopTo(GeckoSession s) {
        GeckoSessionSettings settings = s.getSettings();
        settings.setUserAgentMode(desktopMode
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        settings.setViewportMode(desktopMode
                ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
    }

    private void setImmersiveMode(boolean value) {
        immersive = value;
        toolbar.setVisibility(value ? View.GONE : View.VISIBLE);
    }

    private void updateNavState() {
        if (toolbar == null) return;
        toolbar.setCanBack(canBack);
        toolbar.setCanForward(canForward);
    }

    private void showMouse() {
        mouse.show();
        handler.removeCallbacks(hideMouseRunnable);
        handler.postDelayed(hideMouseRunnable, 4500);
    }

    private final Runnable hideMouseRunnable = new Runnable() {
        @Override public void run() {
            if (mouse.isVisible() && !pageFullscreen) mouse.hide();
        }
    };

    private void toast(final String message) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    if (toastInstance == null) {
                        toastInstance = Toast.makeText(GeckoEngineActivity.this, message,
                                Toast.LENGTH_SHORT);
                    } else {
                        toastInstance.setText(message);
                    }
                    toastInstance.show();
                } catch (Throwable ignored) {}
            }
        });
    }

    private String errorDescription(int code) {
        switch (code) {
            case WebRequestError.ERROR_UNKNOWN_HOST: return "无法找到服务器，请检查网址是否正确";
            case WebRequestError.ERROR_CONNECTION_REFUSED: return "服务器拒绝了连接";
            case WebRequestError.ERROR_NET_TIMEOUT: return "连接超时，请检查网络后重试";
            case WebRequestError.ERROR_NET_RESET:
            case WebRequestError.ERROR_NET_INTERRUPT: return "连接被中断，请刷新重试";
            case WebRequestError.ERROR_OFFLINE: return "设备未连接到网络";
            case WebRequestError.ERROR_SECURITY_SSL:
            case WebRequestError.ERROR_SECURITY_BAD_CERT:
            case WebRequestError.ERROR_BAD_HSTS_CERT: return "网站安全证书存在问题";
            case WebRequestError.ERROR_PROXY_CONNECTION_REFUSED:
            case WebRequestError.ERROR_UNKNOWN_PROXY_HOST: return "代理服务器连接失败";
            case WebRequestError.ERROR_CONTENT_CRASHED: return "页面崩溃，请刷新重试";
            case WebRequestError.ERROR_MALFORMED_URI: return "网址格式错误";
            case WebRequestError.ERROR_REDIRECT_LOOP: return "页面重定向次数过多";
            case WebRequestError.ERROR_FILE_NOT_FOUND: return "文件不存在";
            case WebRequestError.ERROR_FILE_ACCESS_DENIED: return "文件访问被拒绝";
            case WebRequestError.ERROR_UNKNOWN_PROTOCOL:
            case WebRequestError.ERROR_UNKNOWN_SOCKET_TYPE: return "不支持的网址协议";
            case WebRequestError.ERROR_UNSAFE_CONTENT_TYPE:
            case WebRequestError.ERROR_INVALID_CONTENT_ENCODING: return "网页内容无法解析";
            case WebRequestError.ERROR_PORT_BLOCKED: return "网络端口被限制";
            case WebRequestError.ERROR_DATA_URI_TOO_LONG: return "网址过长";
            case WebRequestError.ERROR_HTTPS_ONLY: return "需要 HTTPS 安全连接";
            default: return "网络错误，错误码 " + code;
        }
    }

    private String buildErrorPage(String uri, WebRequestError error) {
        String safeUrl = uri != null ? uri.replace("'", "") : "";
        String desc = errorDescription(error.code);
        String page = "<!DOCTYPE html><html lang='zh'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
            + "<style>"
            + "*{margin:0;padding:0;box-sizing:border-box;}"
            + "html{background:#0b0e14;}"
            + "body{background:#0b0e14;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',Arial,sans-serif;"
            + "min-height:100vh;display:flex;align-items:center;justify-content:center;}"
            + ".card{text-align:center;padding:40px;max-width:560px;}"
            + ".code{font-size:84px;font-weight:700;color:#5b7cff;line-height:1;}"
            + ".title{font-size:26px;font-weight:600;color:#e8ecf4;margin-top:16px;}"
            + ".desc{font-size:15px;color:#94a0b8;margin-top:10px;line-height:1.6;}"
            + ".url{font-size:13px;color:#7d88a0;margin-top:22px;word-break:break-all;"
            + "background:#141b2d;padding:10px 14px;border-radius:8px;}"
            + "</style></head><body><div class='card'>"
            + "<div class='code'>!</div>"
            + "<div class='title'>无法打开此页面</div>"
            + "<div class='desc'>" + desc + "</div>"
            + "<div class='url'>" + safeUrl + "</div>"
            + "</div></body></html>";
        return "data:text/html;charset=utf-8," + Uri.encode(page);
    }

    private class ToolbarListener implements ChromeToolbar.Listener {
        @Override public void onBack() {
            if (popupFrame.getVisibility() == View.VISIBLE) {
                closePopup();
            } else {
                session.goBack();
            }
        }
        @Override public void onForward() {
            activeSession().goForward();
        }
        @Override public void onReload() {
            if (loading) {
                activeSession().stop();
            } else {
                activeSession().reload();
            }
        }
        @Override public void onUrlSubmit(String text) {
            navigate(text);
        }
        @Override public void onToggleDesktop() {
            toggleDesktop();
        }
        @Override public void onMenu() {
            openMenu();
        }
    }

    private void openMenu() {
        final boolean popupShown = popupFrame.getVisibility() == View.VISIBLE;
        final String[] items = {
            desktopMode ? "桌面UA：开" : "桌面UA：关",
            "刷新页面",
            immersive ? "退出全屏界面" : "全屏界面",
            "返回主页",
            "云原神",
            "下载管理",
            "故障诊断信息",
            popupShown ? "关闭弹窗" : "键盘快捷键"
        };
        new AlertDialog.Builder(this)
                .setTitle("菜单")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: toggleDesktop(); break;
                            case 1: activeSession().reload(); break;
                            case 2: setImmersiveMode(!immersive); break;
                            case 3: loadHome(); break;
                            case 4:
                                if (popupShown) closePopup();
                                session.loadUri(CLOUD_URL);
                                break;
                            case 5: openDownloads(); break;
                            case 6: openSupport(); break;
                            case 7:
                                if (popupShown) closePopup();
                                else showShortcutsHelp();
                                break;
                        }
                    }
                })
                .show();
    }

    private void openDownloads() {
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            toast("下载目录：" + dir.getAbsolutePath());
        }
    }

    private void openSupport() {
        activeSession().loadUri("about:support");
        toast("崩溃 " + crashCount + " 次 / 内存终止 " + killCount
                + " 次，请查看“图形(Graphics)”部分");
    }

    private void showShortcutsHelp() {
        final String[] rows = {
            "F5 / Ctrl+R  -  刷新页面",
            "Esc  -  停止加载 / 退出全屏",
            "Alt+← / Alt+→  -  后退 / 前进",
            "Delete  -  后退",
            "Ctrl+L / F6  -  选中地址栏",
            "空格 / PgDn  -  向下翻页",
            "PgUp  -  向上翻页",
            "Home / End  -  页首 / 页尾",
            "Ctrl+加号 / Ctrl+减号  -  放大 / 缩小",
            "Ctrl+0  -  重置缩放",
            "Ctrl+F  -  页内查找",
            "Ctrl+T  -  返回主页",
            "Ctrl+W  -  退出浏览器",
            "F10 / 菜单键  -  打开菜单",
            "F11  -  全屏界面",
            "方向键  -  移动光标，确认键点击"
        };
        new AlertDialog.Builder(this)
                .setTitle("键盘快捷键")
                .setItems(rows, null)
                .setPositiveButton("关闭", null)
                .show();
    }

    private GeckoResult<GeckoSession> openPopup(String uri) {
        if (popupFrame.getVisibility() == View.VISIBLE) {
            closePopup();
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        TextView closeBar = new TextView(this);
        closeBar.setText("  弹窗窗口  -  返回键 / 点击关闭");
        closeBar.setTextColor(0xFFFFFFFF);
        closeBar.setBackgroundColor(0xFF1F2430);
        closeBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        closeBar.setFocusable(true);
        wrap.addView(closeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        popupView = new GeckoView(this);
        popupView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        popupView.setFocusable(true);
        wrap.addView(popupView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        popupFrame.addView(wrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        popupSession = new GeckoSession(buildSettings());
        attachDelegates(popupSession, true);
        popupView.setSession(popupSession);
        popupSession.open(runtime);
        if (uri != null) popupSession.loadUri(uri);

        popupView.requestFocus();
        popupFrame.setVisibility(View.VISIBLE);
        mouse.setTarget(popupFrame);
        showMouse();

        closeBar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                closePopup();
            }
        });
        return GeckoResult.fromValue(popupSession);
    }

    private void closePopup() {
        if (popupSession != null) {
            try { popupSession.close(); } catch (Throwable ignored) {}
            popupSession = null;
        }
        popupFrame.removeAllViews();
        popupView = null;
        popupFrame.setVisibility(View.GONE);
        mouse.setTarget(root);
        if (geckoView != null) geckoView.requestFocus();
    }

    private void handleDownload(final WebResponse response) {
        toast("Download started");
        new Thread(new Runnable() {
            @Override public void run() {
                InputStream in = null;
                OutputStream out = null;
                try {
                    String disposition = response.headers != null
                            ? response.headers.get("Content-Disposition") : null;
                    String ctype = response.headers != null
                            ? response.headers.get("Content-Type") : null;
                    String name = URLUtil.guessFileName(response.uri, disposition, ctype);
                    if (name == null || name.length() == 0) name = "download.bin";
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File target = uniqueFile(dir, name);
                    in = response.body;
                    out = new FileOutputStream(target);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                    toast("已下载：" + target.getName());
                    scanFile(target);
                } catch (Exception e) {
                    toast("下载失败");
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignored) {}
                    try { if (out != null) out.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(dir, base + "(" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        return f;
    }

    private void scanFile(File f) {
        try {
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(Uri.fromFile(f));
            sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }

    private boolean isFullKeyboard(final InputDevice device) {
        return device != null
                && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int code = event.getKeyCode();
        boolean fullKeyboard = isFullKeyboard(event.getDevice());

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

        if (action == KeyEvent.ACTION_DOWN && handleShortcut(code, event)) {
            return true;
        }

        // A real alphabetic keyboard behaves like a desktop browser: navigation,
        // scrolling, text entry and focus keys go straight to the focused
        // GeckoView, without moving the virtual mouse.
        if (fullKeyboard) {
            if (code == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_UP) {
                if (popupFrame.getVisibility() == View.VISIBLE) {
                    closePopup();
                    return true;
                }
                if (pageFullscreen) return super.dispatchKeyEvent(event);
                if (immersive) {
                    setImmersiveMode(false);
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        // Remote / non-alphabetic controls: arrow and OK keys drive the mouse.
        if (event.getRepeatCount() == 0 && action == KeyEvent.ACTION_DOWN
                && handlePageKey(code, event)) {
            return true;
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
            if (popupFrame.getVisibility() == View.VISIBLE) {
                closePopup();
                return true;
            }
            if (pageFullscreen) {
                return super.dispatchKeyEvent(event);
            }
            if (immersive) {
                setImmersiveMode(false);
                return true;
            }
            if (canBack) {
                session.goBack();
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
            activeSession().reload();
            return true;
        }
        if ((ctrl && code == KeyEvent.KEYCODE_L)
                || (alt && code == KeyEvent.KEYCODE_D)
                || code == KeyEvent.KEYCODE_F6) {
            toolbar.getUrlBar().focusForInput();
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_F) {
            if (first) showFindDialog();
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_J) {
            openDownloads();
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_T) {
            if (first) {
                if (popupFrame.getVisibility() == View.VISIBLE) closePopup();
                loadHome();
                setImmersiveMode(false);
            }
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_W) {
            if (first) finish();
            return true;
        }
        if (ctrl && (code == KeyEvent.KEYCODE_EQUALS || code == KeyEvent.KEYCODE_PLUS)) {
            changeZoom(0.1f);
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_MINUS) {
            changeZoom(-0.1f);
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_0) {
            resetZoom();
            return true;
        }
        if (alt && code == KeyEvent.KEYCODE_DPAD_LEFT) {
            activeSession().goBack();
            return true;
        }
        if (alt && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            activeSession().goForward();
            return true;
        }
        if (alt && code == KeyEvent.KEYCODE_MOVE_HOME) {
            loadHome();
            return true;
        }
        if (code == KeyEvent.KEYCODE_F1) {
            if (first) showShortcutsHelp();
            return true;
        }
        if (code == KeyEvent.KEYCODE_F10 || code == KeyEvent.KEYCODE_MENU) {
            if (first) openMenu();
            return true;
        }
        if (code == KeyEvent.KEYCODE_F11) {
            if (first) setImmersiveMode(!immersive);
            return true;
        }
        if (code == KeyEvent.KEYCODE_SEARCH) {
            if (first) toolbar.getUrlBar().focusForInput();
            return true;
        }
        if (code == KeyEvent.KEYCODE_ESCAPE) {
            if (popupFrame.getVisibility() == View.VISIBLE) {
                closePopup();
            } else if (pageFullscreen) {
                return false;
            } else if (immersive) {
                setImmersiveMode(false);
            } else {
                activeSession().stop();
            }
            return true;
        }
        return false;
    }

    private boolean handlePageKey(int code, KeyEvent event) {
        View target = activeView();
        if (target == null) return false;
        boolean shift = event.isShiftPressed();
        int mapped = -1;
        switch (code) {
            case KeyEvent.KEYCODE_SPACE:
                mapped = shift ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                mapped = KeyEvent.KEYCODE_PAGE_DOWN;
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                mapped = KeyEvent.KEYCODE_PAGE_UP;
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                mapped = KeyEvent.KEYCODE_MOVE_HOME;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                mapped = KeyEvent.KEYCODE_MOVE_END;
                break;
        }
        if (mapped >= 0) {
            long now = SystemClock.uptimeMillis();
            target.dispatchKeyEvent(new KeyEvent(now, now,
                    KeyEvent.ACTION_DOWN, mapped, 0));
            target.dispatchKeyEvent(new KeyEvent(now, now,
                    KeyEvent.ACTION_UP, mapped, 0));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL && !shift
                && popupFrame.getVisibility() != View.VISIBLE) {
            if (canBack) {
                session.goBack();
                return true;
            }
        }
        return false;
    }

    private float fontFactor = 1.0f;

    private void changeZoom(float delta) {
        fontFactor = Math.max(0.5f, Math.min(3.0f, fontFactor + delta));
        if (runtime != null) runtime.getSettings().setFontSizeFactor(fontFactor);
        toast(Math.round(fontFactor * 100) + "%");
    }

    private void resetZoom() {
        fontFactor = 1.0f;
        if (runtime != null) runtime.getSettings().setFontSizeFactor(1.0f);
        toast("100%");
    }

    private void showFindDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入查找内容");
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(dp(20), dp(10), dp(20), 0);
        holder.addView(input);

        final GeckoSession target = activeSession();
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("页内查找")
                .setView(holder)
                .setPositiveButton("下一个", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String q = input.getText().toString();
                        if (q.length() > 0) {
                            target.getFinder().find(q,
                                    GeckoSession.FINDER_FIND_FORWARD
                                            | GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
                        }
                    }
                })
                .setNegativeButton("上一个", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String q = input.getText().toString();
                        if (q.length() > 0) {
                            target.getFinder().find(q,
                                    GeckoSession.FINDER_FIND_BACKWARDS
                                            | GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
                        }
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        try { target.getFinder().clear(); } catch (Throwable ignored) {}
                    }
                })
                .create();
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                String q = input.getText().toString();
                if (q.length() > 0) {
                    target.getFinder().find(q,
                            GeckoSession.FINDER_FIND_FORWARD
                                    | GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
                }
                return true;
            }
        });
        d.show();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            if (mouse.isVisible()) mouse.hide();
        }
        return super.onGenericMotionEvent(event);
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
        if (u != null) session.loadUri(u);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) session.setActive(true);
        if (popupSession != null) popupSession.setActive(true);
        updateNavState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) session.setActive(false);
        if (popupSession != null) popupSession.setActive(false);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideMouseRunnable);
        if (popupSession != null) {
            try { popupSession.close(); } catch (Throwable ignored) {}
        }
        if (session != null) {
            try { session.close(); } catch (Throwable ignored) {}
        }
        if (mouse != null) mouse.releaseAll();
        super.onDestroy();
    }
}
