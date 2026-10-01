package com.tclbrowser.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class KernelInstaller {

    public interface Listener {
        void onStatus(String status);
        void onProgress(int percent, long downloaded, long total);
        void onReady(String apkPath);
        void onError(String error);
    }

    private static final String KERNEL_DIR = "kernel";
    private static final String KERNEL_FILE = "webview-update.apk";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;
    private volatile boolean cancelled;

    public KernelInstaller(Context context) {
        this.context = context;
    }

    public static int currentMajor(Context context) {
        try {
            String ua = WebSettings.getDefaultUserAgent(context);
            Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(ua);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Throwable ignored) {}
        return 0;
    }

    public static boolean needsUpgrade(Context context) {
        return currentMajor(context) < 90;
    }

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2500;

    public void download(final String url, final Listener listener) {
        cancelled = false;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                String lastError = "未知错误";
                for (int attempt = 1; attempt <= MAX_ATTEMPTS && !cancelled; attempt++) {
                    String err = attemptDownload(url, listener, attempt);
                    if (err == null) return;
                    lastError = err;
                    if (cancelled) return;
                    if (attempt < MAX_ATTEMPTS) {
                        postStatus(listener, "连接异常，正在重试 (" + attempt + "/"
                                + (MAX_ATTEMPTS - 1) + ")...  " + err);
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    } else {
                        postError(listener, "多次重试后仍下载失败：" + lastError);
                    }
                }
            }
        }, "kernel-download");
        worker.start();
    }

    /**
     * @return null on success, error message on failure
     */
    private String attemptDownload(String url, Listener listener, int attempt) {
        HttpURLConnection conn = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            postStatus(listener, attempt == 1 ? "正在连接..." : "正在重试连接...");
            File target = targetFile();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (target.exists()) target.delete();

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(45000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/vnd.android.package-archive");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.0.0 Safari/537.36");

            // Android 5.1 does not enable TLS 1.2 by default; GitHub requires it.
            if (conn instanceof HttpsURLConnection) {
                applyTLS12((HttpsURLConnection) conn);
            }

            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return "HTTP " + code;
            }

            long total = conn.getContentLength();
            String totalHeader = conn.getHeaderField("Content-Length");
            if (total <= 0 && totalHeader != null) {
                try { total = Long.parseLong(totalHeader); } catch (Throwable ignored) {}
            }

            input = conn.getInputStream();
            output = new FileOutputStream(target);
            byte[] buffer = new byte[65536];
            long downloaded = 0;
            int lastPercent = -1;
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (cancelled) {
                    output.close();
                    if (target.exists()) target.delete();
                    return "已取消";
                }
                output.write(buffer, 0, read);
                downloaded += read;
                int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                if (percent != lastPercent) {
                    lastPercent = percent;
                    postProgress(listener, percent, downloaded, total);
                }
            }
            output.flush();
            output.close();
            input.close();

            if (target.length() < 1024 * 1024) {
                return "文件过小 (" + target.length() + " bytes)，可能下载不完整";
            }
            postReady(listener, target.getAbsolutePath());
            return null;
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.length() == 0) msg = t.getClass().getSimpleName();
            return msg;
        } finally {
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Force-enable TLS 1.2 on Android 5.1 where it is supported but not
     * enabled by default in HttpsURLConnection.
     */
    private static void applyTLS12(HttpsURLConnection conn) {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, null, null);
            conn.setSSLSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
        } catch (NoSuchAlgorithmException e) {
            // TLS not available at all; leave default
        } catch (KeyManagementException e) {
            // leave default
        }
    }

    /**
     * Wraps an SSLSocketFactory and forces TLS 1.2 (and 1.1/1.0 fallback)
     * on every created socket. Required for Android 5.1 talking to modern
     * HTTPS endpoints that refuse TLS 1.0.
     */
    private static class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private static final String[] PROTOCOLS = {"TLSv1.2", "TLSv1.1", "TLSv1"};

        Tls12SocketFactory(SSLSocketFactory base) {
            this.delegate = base;
        }

        private javax.net.ssl.SSLSocket patch(javax.net.ssl.SSLSocket s) {
            try {
                s.setEnabledProtocols(PROTOCOLS);
            } catch (Throwable ignored) {}
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override public java.net.Socket createSocket() throws IOException {
            return patch((SSLSocket) delegate.createSocket());
        }
        @Override public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws IOException {
            return patch((SSLSocket) delegate.createSocket(s, host, port, autoClose));
        }
        @Override public java.net.Socket createSocket(String host, int port) throws IOException {
            return patch((SSLSocket) delegate.createSocket(host, port));
        }
        @Override public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws IOException {
            return patch((SSLSocket) delegate.createSocket(host, port, localHost, localPort));
        }
        @Override public java.net.Socket createSocket(java.net.InetAddress host, int port) throws IOException {
            return patch((SSLSocket) delegate.createSocket(host, port));
        }
        @Override public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
            return patch((SSLSocket) delegate.createSocket(address, port, localAddress, localPort));
        }
    }

    public void cancel() {
        cancelled = true;
        if (worker != null) worker.interrupt();
    }

    public File targetFile() {
        File dir = context.getExternalFilesDir(KERNEL_DIR);
        if (dir == null) {
            dir = new File(context.getFilesDir(), KERNEL_DIR);
        }
        return new File(dir, KERNEL_FILE);
    }

    public static boolean isDownloaded(Context context) {
        File f = new KernelInstaller(context).targetFile();
        return f.exists() && f.length() > 1024 * 1024;
    }

    /**
     * Launch the system package installer for the given APK.
     * Uses ACTION_INSTALL_PACKAGE and tries to pin the package installer
     * explicitly so that file managers (e.g. Baidu Netdisk) do not hijack
     * the VIEW intent.
     */
    public static void installApk(Activity activity, String path) {
        File file = new File(path);
        if (!file.exists()) return;
        Uri uri = Uri.fromFile(file);

        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Try the standard package installer first to avoid hijacking by
        // third-party file managers / cloud storage apps.
        String[] installers = {
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        };
        for (String pkg : installers) {
            try {
                Intent pinned = new Intent(intent);
                pinned.setPackage(pkg);
                activity.startActivity(pinned);
                return;
            } catch (Throwable ignored) {
                // try next
            }
        }
        // Fallback: un-pinned intent, system will resolve
        try {
            activity.startActivity(intent);
        } catch (Throwable t) {
            // Last resort: ACTION_VIEW
            try {
                Intent view = new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(uri, "application/vnd.android.package-archive");
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(view);
            } catch (Throwable ignored) {}
        }
    }

    private void postStatus(final Listener l, final String s) {
        main.post(new Runnable() {
            @Override public void run() { l.onStatus(s); }
        });
    }

    private void postProgress(final Listener l, final int p, final long d, final long t) {
        main.post(new Runnable() {
            @Override public void run() { l.onProgress(p, d, t); }
        });
    }

    private void postReady(final Listener l, final String path) {
        main.post(new Runnable() {
            @Override public void run() { l.onReady(path); }
        });
    }

    private void postError(final Listener l, final String e) {
        main.post(new Runnable() {
            @Override public void run() { l.onError(e); }
        });
    }
}
