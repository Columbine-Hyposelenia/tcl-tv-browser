package com.tclbrowser.tv;

import android.content.Context;
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

    private static final int MAX_ATTEMPTS_PER_URL = 2;
    private static final long RETRY_DELAY_MS = 2000;

    /** Single-URL convenience (tries that URL up to MAX_ATTEMPTS_PER_URL times). */
    public void download(final String url, final Listener listener) {
        download(new String[]{url}, listener);
    }

    /**
     * Try each mirror URL in order. For every URL we retry up to
     * MAX_ATTEMPTS_PER_URL times, then move to the next mirror. This is
     * essential on mainland China TVs where github.com direct connections
     * are often blocked / timing out.
     */
    public void download(final String[] urls, final Listener listener) {
        if (urls == null || urls.length == 0) {
            postError(listener, "没有可用的下载地址");
            return;
        }
        cancelled = false;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                String lastError = "未知错误";
                for (int mi = 0; mi < urls.length && !cancelled; mi++) {
                    String url = urls[mi];
                    String mirrorLabel = urls.length > 1
                            ? ("镜像 " + (mi + 1) + "/" + urls.length) : "";
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_URL && !cancelled; attempt++) {
                        String err = attemptDownload(url, listener, attempt, mirrorLabel);
                        if (err == null) return;
                        lastError = err;
                        if (cancelled) return;
                        if (attempt < MAX_ATTEMPTS_PER_URL) {
                            postStatus(listener, (mirrorLabel.length() > 0 ? mirrorLabel + "  " : "")
                                    + "重试 (" + attempt + "/" + (MAX_ATTEMPTS_PER_URL - 1)
                                    + ")...  " + err);
                            try { Thread.sleep(RETRY_DELAY_MS); }
                            catch (InterruptedException ie) { return; }
                        }
                    }
                    // All retries for this mirror exhausted; try next mirror
                    if (mi < urls.length - 1 && !cancelled) {
                        postStatus(listener, "当前镜像不可用，切换下一个镜像...");
                        try { Thread.sleep(800); } catch (InterruptedException ie) { return; }
                    }
                }
                postError(listener, "所有镜像均下载失败：" + lastError
                        + "。请检查网络连接，或在手机上下载后通过U盘/文件管理器安装到电视。");
            }
        }, "kernel-download");
        worker.start();
    }

    /**
     * @return null on success, error message on failure
     */
    private String attemptDownload(String url, Listener listener, int attempt, String mirrorLabel) {
        HttpURLConnection conn = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            String prefix = (mirrorLabel != null && mirrorLabel.length() > 0) ? mirrorLabel + "  " : "";
            postStatus(listener, prefix + (attempt == 1 ? "正在连接..." : "正在重试连接..."));
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

            // Integrity checks
            long actual = target.length();
            if (actual < 1024 * 1024) {
                return "文件过小 (" + actual + " bytes)，可能下载不完整";
            }
            if (total > 0 && actual != total) {
                return "下载不完整：期望 " + total + " bytes，实际 " + actual + " bytes";
            }
            // APK is a ZIP archive; verify the local file header magic "PK\x03\x04"
            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(target);
                int b0 = fis.read();
                int b1 = fis.read();
                int b2 = fis.read();
                int b3 = fis.read();
                fis.close();
                if (b0 != 0x50 || b1 != 0x4B || b2 != 0x03 || b3 != 0x04) {
                    return "文件不是有效的 APK（ZIP 头校验失败），可能被镜像返回了错误页面";
                }
            } catch (Throwable t) {
                return "文件校验失败：" + t.getMessage();
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
