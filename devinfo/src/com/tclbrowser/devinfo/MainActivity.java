package com.tclbrowser.devinfo;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.app.ActivityManager;
import android.util.TypedValue;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String LINE = "-------------------------------------------";

    private TextView output;
    private TextView status;
    private ScrollView scroll;
    private String mainReport;
    private String fullProps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        collect();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121314);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(6));

        Button refresh = makeButton("Refresh");
        Button export = makeButton("Export Report");
        Button top = makeButton("Top");
        bar.addView(refresh);
        bar.addView(export);
        bar.addView(top);

        status = new TextView(this);
        status.setTextColor(0xFF7FD4CC);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(dp(12), 0, dp(12), 0);
        status.setText("");

        output = new TextView(this);
        output.setTextColor(0xFFD9D9D9);
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setPadding(dp(14), dp(10), dp(14), dp(24));
        output.setLineSpacing(2f, 1.0f);
        output.setFocusable(true);
        output.setFocusableInTouchMode(true);

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(output, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        root.addView(bar);
        root.addView(status);
        root.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { collect(); }
        });
        export.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportReport(); }
        });
        top.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scroll.scrollTo(0, 0); }
        });
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------
    // Collect
    // ------------------------------------------------------------------

    private void collect() {
        status.setText("Collecting...");
        new Thread(new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("TCL TV device report\n");
                sb.append("Generated: ").append(new java.util.Date()).append("\n");
                sb.append("App: com.tclbrowser.devinfo\n\n");

                sectionBuild(sb);
                sectionAbi(sb);
                sectionCpu(sb);
                sectionMemory(sb);
                sectionDisplay(sb);
                sectionGl(sb);
                sectionWebView(sb);
                sectionKernel(sb);
                sectionCodecs(sb);
                sectionInput(sb);
                sectionStorage(sb);
                sectionNetwork(sb);
                sectionFeatures(sb);
                sectionPropsFiltered(sb);
                sectionInstaller(sb);

                final String report = sb.toString();
                fullProps = exec("getprop");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        mainReport = report;
                        output.setText(mainReport);
                        scroll.scrollTo(0, 0);
                        status.setText("Collect done. Press Export to save full report.");
                    }
                });
            }
        }, "devinfo-collect").start();
    }

    private void sectionInstaller(StringBuilder sb) {
        header(sb, "Installer environment");
        sb.append(InstallerAudit.audit(getApplicationContext()));
    }

    private void header(StringBuilder sb, String title) {
        sb.append("\n").append(LINE).append("\n[ ").append(title).append(" ]\n").append(LINE).append("\n");
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private void sectionBuild(StringBuilder sb) {
        header(sb, "Build / System");
        sb.append("Brand: ").append(Build.BRAND).append("\n");
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        sb.append("Model: ").append(Build.MODEL).append("\n");
        sb.append("Device: ").append(Build.DEVICE).append("\n");
        sb.append("Product: ").append(Build.PRODUCT).append("\n");
        sb.append("Board: ").append(Build.BOARD).append("\n");
        sb.append("Hardware: ").append(Build.HARDWARE).append("\n");
        sb.append("Bootloader: ").append(Build.BOOTLOADER).append("\n");
        sb.append("Radio: ").append(Build.RADIO).append("\n");
        sb.append("Type: ").append(Build.TYPE).append("\n");
        sb.append("Tags: ").append(Build.TAGS).append("\n");
        sb.append("Display: ").append(Build.DISPLAY).append("\n");
        sb.append("ID: ").append(Build.ID).append("\n");
        sb.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n");
        sb.append("Host: ").append(Build.HOST).append("\n");
        sb.append("User: ").append(Build.USER).append("\n");
        sb.append("Android release: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("Android SDK: ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Incremental: ").append(Build.VERSION.INCREMENTAL).append("\n");
        sb.append("Codename: ").append(Build.VERSION.CODENAME).append("\n");
        sb.append("UiMode: ").append(uiMode()).append("\n");
    }

    private String uiMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        if (mode == Configuration.UI_MODE_TYPE_TELEVISION) return "TELEVISION";
        if (mode == Configuration.UI_MODE_TYPE_NORMAL) return "NORMAL";
        return String.valueOf(mode);
    }

    private void sectionAbi(StringBuilder sb) {
        header(sb, "CPU / ABI");
        sb.append("Build.CPU_ABI: ").append(Build.CPU_ABI).append("\n");
        sb.append("Build.CPU_ABI2: ").append(Build.CPU_ABI2).append("\n");
        sb.append("Supported ABIs: ");
        if (Build.SUPPORTED_ABIS != null) {
            for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(Build.SUPPORTED_ABIS[i]);
            }
        }
        sb.append("\n");
        sb.append("ro.product.cpu.abi: ").append(getProp("ro.product.cpu.abi")).append("\n");
        sb.append("ro.product.cpu.abilist: ").append(getProp("ro.product.cpu.abilist")).append("\n");
        sb.append("ro.product.cpu.abilist32: ").append(getProp("ro.product.cpu.abilist32")).append("\n");
        sb.append("ro.product.cpu.abilist64: ").append(getProp("ro.product.cpu.abilist64")).append("\n");
        sb.append("ro.board.platform: ").append(getProp("ro.board.platform")).append("\n");
        sb.append("ro.arch: ").append(getProp("ro.arch")).append("\n");
    }

    private void sectionCpu(StringBuilder sb) {
        header(sb, "/proc/cpuinfo");
        String cpu = readFile("/proc/cpuinfo");
        sb.append(cpu != null ? cpu : "(cannot read)");
        if (cpu != null && !cpu.endsWith("\n")) sb.append("\n");
    }

    private void sectionMemory(StringBuilder sb) {
        header(sb, "Memory");
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        sb.append("Total RAM: ").append(mi.totalMem / 1024 / 1024).append(" MB\n");
        sb.append("Available RAM: ").append(mi.availMem / 1024 / 1024).append(" MB\n");
        sb.append("Threshold: ").append(mi.threshold / 1024 / 1024).append(" MB\n");
        sb.append("Low memory: ").append(mi.lowMemory).append("\n");
        Runtime rt = Runtime.getRuntime();
        sb.append("VM heap max: ").append(rt.maxMemory() / 1024 / 1024).append(" MB\n");
        sb.append("VM free: ").append(rt.freeMemory() / 1024 / 1024).append(" MB\n");
                sb.append("Large memory class: ").append(am.getLargeMemoryClass()).append(" MB\n");
        sb.append("Memory class: ").append(am.getMemoryClass()).append(" MB\n");
        sb.append("\n/proc/meminfo (first lines):\n");
        String mem = readFile("/proc/meminfo");
        if (mem != null) {
            String[] lines = mem.split("\n");
            for (int i = 0; i < Math.min(lines.length, 12); i++) sb.append(lines[i]).append("\n");
        }
    }

    private void sectionDisplay(StringBuilder sb) {
        header(sb, "Display");
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display d = wm.getDefaultDisplay();
        android.graphics.Point real = new android.graphics.Point();
        d.getRealSize(real);
        android.util.DisplayMetrics realMetrics = new android.util.DisplayMetrics();
        d.getRealMetrics(realMetrics);
        android.util.DisplayMetrics appMetrics = new android.util.DisplayMetrics();
        d.getMetrics(appMetrics);
        sb.append("Display name: ").append(d.getName()).append("\n");
        sb.append("Real size: ").append(real.x).append(" x ").append(real.y).append("\n");
        sb.append("App size: ").append(appMetrics.widthPixels).append(" x ").append(appMetrics.heightPixels).append("\n");
        sb.append("RealMetrics density: ").append(realMetrics.density).append("\n");
        sb.append("densityDpi: ").append(realMetrics.densityDpi).append("\n");
        sb.append("scaledDensity: ").append(realMetrics.scaledDensity).append("\n");
        sb.append("xdpi: ").append(realMetrics.xdpi).append("\n");
        sb.append("ydpi: ").append(realMetrics.ydpi).append("\n");
        sb.append("Refresh rate: ").append(d.getRefreshRate()).append(" Hz\n");
        sb.append("ro.sf.lcd_density: ").append(getProp("ro.sf.lcd_density")).append("\n");
        sb.append("persist.sys.framebuffer.native.width: ").append(getProp("persist.sys.framebuffer.native.width")).append("\n");
        sb.append("persist.sys.framebuffer.native.height: ").append(getProp("persist.sys.framebuffer.native.height")).append("\n");
        sb.append("Configuration smallestScreenWidthDp: ").append(getResources().getConfiguration().smallestScreenWidthDp).append("\n");
        sb.append("Configuration screenWidthDp: ").append(getResources().getConfiguration().screenWidthDp).append("\n");
    }

    private void sectionGl(StringBuilder sb) {
        header(sb, "GPU / OpenGL ES");
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ConfigurationInfo ci = am.getDeviceConfigurationInfo();
        sb.append("System reported GLES version: ").append(ci.getGlEsVersion()).append("\n");
        sb.append("ro.opengles.version: ").append(getProp("ro.opengles.version")).append("\n");

        GlInfo g3 = readGles(3);
        GlInfo g = g3.version != null ? g3 : readGles(2);
        if (g.version != null) {
            sb.append("GL vendor: ").append(g.vendor).append("\n");
            sb.append("GL renderer: ").append(g.renderer).append("\n");
            sb.append("GL version: ").append(g.version).append("\n");
            sb.append("GLSL: ").append(g.glsl).append("\n");
            sb.append("GL extensions: ").append(g.ext).append("\n");
            sb.append("Has WebGL2 base (GLES3): ").append(g3.version != null).append("\n");
        } else {
            sb.append("(offscreen GL context not available)\n");
        }
    }

    private static class GlInfo {
        String vendor, renderer, version, glsl, ext;
    }

    private GlInfo readGles(int clientVersion) {
        GlInfo info = new GlInfo();
        EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] major = new int[2];
        if (!EGL14.eglInitialize(dpy, major, 0, major, 1)) return info;
        int[] cfgAttr = {
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] nCfg = new int[1];
        EGL14.eglChooseConfig(dpy, cfgAttr, 0, configs, 0, 1, nCfg, 0);
        if (nCfg[0] == 0) return info;
        int[] ctxAttr = {EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE};
        EGLContext ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
        if (ctx == EGL14.EGL_NO_CONTEXT) return info;
        int[] surfAttr = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
        EGLSurface surf = EGL14.eglCreatePbufferSurface(dpy, configs[0], surfAttr, 0);
        if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return info;
        info.vendor = GLES20.glGetString(GLES20.GL_VENDOR);
        info.renderer = GLES20.glGetString(GLES20.GL_RENDERER);
        info.version = GLES20.glGetString(GLES20.GL_VERSION);
        info.glsl = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION);
        info.ext = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(dpy, surf);
        EGL14.eglDestroyContext(dpy, ctx);
        EGL14.eglTerminate(dpy);
        return info;
    }

    private void sectionWebView(StringBuilder sb) {
        header(sb, "WebView / Browser engine");
        String ua = "n/a";
        try {
            ua = android.webkit.WebSettings.getDefaultUserAgent(this);
        } catch (Throwable t) {
            ua = "error: " + t;
        }
        sb.append("Default UA: ").append(ua).append("\n\n");
        String[] candidates = {
            "com.google.android.webview",
            "com.android.webview",
            "com.android.chrome",
            "com.google.android.apps.chrome",
            "com.google.android.webview.beta",
            "com.tencent.mtt",
            "com.tencent.tbs",
            "com.mi.globalbrowser",
            "com.android.browser",
            "org.mozilla.firefox",
            "com.explore.web.browser"
        };
        PackageManager pm = getPackageManager();
        for (String pkg : candidates) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                sb.append("FOUND: ").append(pkg)
                  .append(" version=").append(pi.versionName)
                  .append(" (").append(pi.versionCode).append(")\n");
            } catch (PackageManager.NameNotFoundException e) {
                sb.append("missing: ").append(pkg).append("\n");
            }
        }
        sb.append("\nWebView provider (reflection):\n");
        try {
            Class<?> factory = Class.forName("android.webkit.WebViewFactory");
            Object provider = null;
            try {
                java.lang.reflect.Method m = factory.getDeclaredMethod("getWebViewContextAndSetProvider");
                m.setAccessible(true);
                Object ret = m.invoke(null);
                if (ret != null) provider = ret;
            } catch (Throwable ignored) { }
            sb.append("provider object: ").append(provider).append("\n");
        } catch (Throwable t) {
            sb.append("WebViewFactory not accessible: ").append(t).append("\n");
        }
    }

    private void sectionKernel(StringBuilder sb) {
        header(sb, "Kernel replacement probe");
        android.content.res.Resources sysRes = android.content.res.Resources.getSystem();
        int cfgId = sysRes.getIdentifier("config_webViewPackageName", "string", "android");
        String cfgName = cfgId != 0 ? sysRes.getString(cfgId) : "(resource not found)";
        sb.append("config_webViewPackageName: ").append(cfgName).append("\n");
        sb.append("config resource id: 0x").append(Integer.toHexString(cfgId)).append("\n");

        PackageManager pm = getPackageManager();
        String[] targets = {"com.android.webview", "com.google.android.webview", "android"};
        for (String pkg : targets) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                sb.append("\n--- ").append(pkg).append(" ---\n");
                sb.append("version: ").append(pi.versionName)
                  .append(" (").append(pi.versionCode).append(")\n");
                if (pi.applicationInfo != null) {
                    sb.append("sourceDir: ").append(pi.applicationInfo.sourceDir).append("\n");
                    String pAbi = "(n/a)";
                    try {
                        java.lang.reflect.Field f = ApplicationInfo.class.getField("primaryCpuAbi");
                        Object v = f.get(pi.applicationInfo);
                        if (v != null) pAbi = v.toString();
                    } catch (Throwable ignored) {}
                    sb.append("primaryCpuAbi: ").append(pAbi).append("\n");
                    sb.append("nativeLibraryDir: ").append(pi.applicationInfo.nativeLibraryDir).append("\n");
                    int fl = pi.applicationInfo.flags;
                    sb.append("flags: 0x").append(Integer.toHexString(fl)).append("\n");
                    sb.append("isSystemApp: ").append((fl & ApplicationInfo.FLAG_SYSTEM) != 0)
                      .append(" isUpdatedSystemApp: ")
                      .append((fl & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0).append("\n");
                }
                Signature[] sigs = pi.signatures;
                if (sigs != null) {
                    for (int i = 0; i < sigs.length; i++) {
                        byte[] cert = sigs[i].toByteArray();
                        sb.append("signature[").append(i).append("] size=").append(cert.length).append("\n");
                        sb.append("  MD5: ").append(digestHex(cert, "MD5")).append("\n");
                        sb.append("  SHA1: ").append(digestHex(cert, "SHA-1")).append("\n");
                        sb.append("  SHA256: ").append(digestHex(cert, "SHA-256")).append("\n");
                        try {
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            X509Certificate x509 = (X509Certificate) cf.generateCertificate(
                                    new ByteArrayInputStream(cert));
                            sb.append("  subject: ").append(x509.getSubjectDN().getName()).append("\n");
                            sb.append("  issuer: ").append(x509.getIssuerDN().getName()).append("\n");
                        } catch (Throwable t) {
                            sb.append("  x509 parse error: ").append(t).append("\n");
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                sb.append("\n--- ").append(pkg).append(": NOT INSTALLED ---\n");
            }
        }
    }

    private String digestHex(byte[] data, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] out = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Throwable t) {
            return "error: " + t;
        }
    }

    private void sectionCodecs(StringBuilder sb) {
        header(sb, "Media decoders (hardware)");
        int count = MediaCodecList.getCodecCount();
        for (int i = 0; i < count; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            String[] types = info.getSupportedTypes();
            for (String type : types) {
                String tag = "";
                if (type.contains("avc") || type.contains("h264")) tag = "  <== H.264";
                if (type.contains("hevc") || type.contains("h265")) tag = "  <== H.265";
                if (type.contains("vp9")) tag = "  <== VP9";
                if (type.contains("av01")) tag = "  <== AV1";
                if (type.contains("vp8")) tag = "  <== VP8";
                sb.append((info.isEncoder() ? "[E] " : "[D] "))
                  .append(info.getName()).append(" -> ").append(type).append(tag).append("\n");
            }
        }
    }

    private void sectionInput(StringBuilder sb) {
        header(sb, "Input devices");
        int[] ids = InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null) continue;
            sb.append("id=").append(d.getId())
              .append(" name=").append(d.getName())
              .append(" vendor=").append(d.getVendorId())
              .append(" product=").append(d.getProductId())
              .append("\n");
            sb.append("  sources=").append(sourcesToString(d.getSources()))
              .append(" keyboard=").append(keyboardType(d.getKeyboardType()))
              .append(" hasKey(DPAD_CENTER)=").append(d.hasKeys(KeyEvent.KEYCODE_DPAD_CENTER)[0])
              .append("\n");
        }
        sb.append("\n/proc/bus/input/devices:\n");
        String input = readFile("/proc/bus/input/devices");
        sb.append(input != null ? input : "(cannot read)");
    }

    private String sourcesToString(int sources) {
        StringBuilder sb = new StringBuilder();
        appendSource(sb, sources, InputDevice.SOURCE_KEYBOARD, "KEYBOARD");
        appendSource(sb, sources, InputDevice.SOURCE_DPAD, "DPAD");
        appendSource(sb, sources, InputDevice.SOURCE_GAMEPAD, "GAMEPAD");
        appendSource(sb, sources, InputDevice.SOURCE_TOUCHSCREEN, "TOUCHSCREEN");
        appendSource(sb, sources, InputDevice.SOURCE_MOUSE, "MOUSE");
        appendSource(sb, sources, InputDevice.SOURCE_STYLUS, "STYLUS");
        appendSource(sb, sources, InputDevice.SOURCE_JOYSTICK, "JOYSTICK");
        appendSource(sb, sources, 0x1000008, "TOUCHPAD");
        if (sb.length() == 0) sb.append("0x").append(Integer.toHexString(sources));
        return sb.toString();
    }

    private void appendSource(StringBuilder sb, int sources, int mask, String name) {
        if ((sources & mask) == mask) {
            if (sb.length() > 0) sb.append("|");
            sb.append(name);
        }
    }

    private String keyboardType(int type) {
        switch (type) {
            case InputDevice.KEYBOARD_TYPE_ALPHABETIC: return "ALPHABETIC";
            case InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC: return "NON_ALPHABETIC";
            case InputDevice.KEYBOARD_TYPE_NONE: return "NONE";
            default: return String.valueOf(type);
        }
    }

    private void sectionStorage(StringBuilder sb) {
        header(sb, "Storage");
        describeStorage(sb, "data", Environment.getDataDirectory());
        describeStorage(sb, "system", Environment.getRootDirectory());
        describeStorage(sb, "cache", Environment.getDownloadCacheDirectory());
        describeStorage(sb, "external", Environment.getExternalStorageDirectory());
        sb.append("External storage state: ").append(Environment.getExternalStorageState()).append("\n");
        sb.append("External files dir: ").append(getExternalFilesDir(null)).append("\n");
    }

    private void describeStorage(StringBuilder sb, String name, File path) {
        try {
            StatFs s = new StatFs(path.getPath());
            long total = s.getBlockCountLong() * s.getBlockSizeLong();
            long avail = s.getAvailableBlocksLong() * s.getBlockSizeLong();
            sb.append(name).append(" (").append(path.getPath()).append("): total=")
              .append(total / 1024 / 1024).append("MB avail=")
              .append(avail / 1024 / 1024).append("MB\n");
        } catch (Throwable t) {
            sb.append(name).append(": ").append(t).append("\n");
        }
    }

    private void sectionNetwork(StringBuilder sb) {
        header(sb, "Network");
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo active = cm.getActiveNetworkInfo();
        sb.append("Active: ").append(active != null ? active.toString() : "none").append("\n");
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nis)) {
                sb.append("\nInterface: ").append(ni.getName())
                  .append(" display=").append(ni.getDisplayName())
                  .append(" up=").append(ni.isUp())
                  .append(" loopback=").append(ni.isLoopback())
                  .append(" mtu=").append(ni.getMTU())
                  .append("\n");
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder ms = new StringBuilder("  mac=");
                    for (byte b : mac) ms.append(String.format("%02x:", b));
                    sb.append(ms).append("\n");
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                for (InetAddress a : Collections.list(addrs)) {
                    sb.append("  addr: ").append(a.getHostAddress())
                      .append(a.isLoopbackAddress() ? " (loopback)" : "")
                      .append(a.getAddress().length == 16 ? " (IPv6)" : " (IPv4)")
                      .append("\n");
                }
            }
        } catch (Throwable t) {
            sb.append("network enumeration error: ").append(t).append("\n");
        }
    }

    private void sectionFeatures(StringBuilder sb) {
        header(sb, "Package / Features");
        PackageManager pm = getPackageManager();
        String[] feats = {
            PackageManager.FEATURE_LEANBACK,
            "android.software.leanback_only",
            PackageManager.FEATURE_TOUCHSCREEN,
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH,
            PackageManager.FEATURE_FAKETOUCH,
            PackageManager.FEATURE_FAKETOUCH_MULTITOUCH_DISTINCT,
            "android.hardware.hdmi.cec",
            PackageManager.FEATURE_LIVE_TV,
            "android.software.picture_in_picture",
            PackageManager.FEATURE_WIFI,
            PackageManager.FEATURE_WIFI_DIRECT,
            PackageManager.FEATURE_BLUETOOTH,
            PackageManager.FEATURE_USB_HOST,
            PackageManager.FEATURE_USB_ACCESSORY,
            PackageManager.FEATURE_GAMEPAD,
            PackageManager.FEATURE_MICROPHONE,
            PackageManager.FEATURE_CAMERA,
            PackageManager.FEATURE_LOCATION,
            PackageManager.FEATURE_LOCATION_GPS,
            PackageManager.FEATURE_SCREEN_LANDSCAPE,
            PackageManager.FEATURE_SCREEN_PORTRAIT,
            "android.hardware.hdmi",
            "android.hardware.type.television"
        };
        Set<String> reported = new HashSet<String>();
        for (FeatureInfo fi : pm.getSystemAvailableFeatures()) {
            if (fi.name != null) reported.add(fi.name);
        }
        for (String f : feats) {
            sb.append(pm.hasSystemFeature(f) ? "yes " : "no  ").append(f).append("\n");
        }
        sb.append("\nAll reported features:\n");
        for (String f : reported) sb.append("  ").append(f).append("\n");
        try {
            PackageInfo pi = pm.getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            sb.append("\nRequested permissions:\n");
            if (pi.requestedPermissions != null) {
                for (String p : pi.requestedPermissions) sb.append("  ").append(p).append("\n");
            }
        } catch (Throwable ignored) { }
    }

    private void sectionPropsFiltered(StringBuilder sb) {
        header(sb, "Key system properties (ro.*)");
        String gp = exec("getprop");
        if (gp == null) {
            sb.append("(getprop failed)\n");
            return;
        }
        String[] prefixes = {
            "ro.product", "ro.sf", "ro.board", "ro.opengles", "ro.hardware",
            "ro.build", "ro.media", "ro.config", "ro.tvservice", "ro.tcl",
            "persist.sys", "media.", "dalvik.vm", "debug.sf", "ro.hdmi",
            "ro.vendor", "ro.amlogic", "sys.usb", "wifi."
        };
        for (String line : gp.split("\n")) {
            for (String p : prefixes) {
                if (line.startsWith("[" + p)) {
                    sb.append(line).append("\n");
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    private void exportReport() {
        if (mainReport == null) {
            status.setText("No report yet.");
            return;
        }
        String body = mainReport
            + "\n\n" + LINE + "\n[ FULL getprop ]\n" + LINE + "\n"
            + (fullProps != null ? fullProps : "(n/a)");

        String path = null;
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                File f = new File(dir, "device_report.txt");
                writeFile(f, body);
                path = f.getAbsolutePath();
            }
        } catch (Throwable t) {
            status.setText("External export failed: " + t);
        }
        try {
            File f2 = new File(getExternalFilesDir(null), "device_report.txt");
            writeFile(f2, body);
            if (path == null) path = f2.getAbsolutePath();
            status.setText("Exported: " + path);
        } catch (Throwable t) {
            if (path == null) status.setText("Export failed: " + t);
        }
    }

    private void writeFile(File f, String content) throws Exception {
        FileWriter w = new FileWriter(f);
        w.write(content);
        w.close();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String getProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = c.getMethod("get", String.class, String.class);
            Object v = m.invoke(null, key, "");
            if (v != null && v.toString().length() > 0) return v.toString();
        } catch (Throwable t) {
            // fall through to shell
        }
        String v = exec("getprop " + key);
        return v != null ? v.trim() : "";
    }

    private String readFile(String path) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (br != null) {
                try { br.close(); } catch (Exception ignored) { }
            }
        }
    }

    private String exec(String cmd) {
        Process p = null;
        BufferedReader br = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            br = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (br != null) {
                try { br.close(); } catch (Exception ignored) { }
            }
            if (p != null) p.destroy();
        }
    }
}
