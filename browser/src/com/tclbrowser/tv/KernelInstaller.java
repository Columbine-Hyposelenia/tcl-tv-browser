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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                for (int attempt = 1; attempt <= MAX_ATTEMPTS && !cancelled; attempt++) {
                    if (attemptDownload(url, listener, attempt)) return;
                    if (cancelled) return;
                    if (attempt < MAX_ATTEMPTS) {
                        postStatus(listener, "连接异常，正在重试 (" + attempt + "/"
                                + (MAX_ATTEMPTS - 1) + ")...");
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    } else {
                        postError(listener, "多次重试后仍下载失败");
                    }
                }
            }
        }, "kernel-download");
        worker.start();
    }

    private boolean attemptDownload(String url, Listener listener, int attempt) {
        HttpURLConnection conn = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            postStatus(listener, attempt == 1 ? "正在连接..." : "正在重试...");
            File target = targetFile();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (target.exists()) target.delete();

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/vnd.android.package-archive");
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return false;
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
                    return false;
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
                return false;
            }
            postReady(listener, target.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
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

    public static void installApk(Activity activity, String path) {
        try {
            File file = new File(path);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file),
                    "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable ignored) {}
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
