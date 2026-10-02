package com.tclbrowser.tv;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs the downloaded Gecko engine APK through every channel available on
 * the device. Some TCL devices neither expose a standard installer activity
 * nor allow reading files inside the caller's private Android/data directory.
 *
 * Channels, in order:
 *   1. PackageInstaller session API (API 21+). This is the preferred path: it
 *      streams the APK into a system install session and reports the exact
 *      status code and failure message back to a status receiver.
 *   2. Copy the APK to the public Download directory and enumerate every
 *      activity that can install/view an APK at runtime.
 *   3. Hidden PackageManager.installPackage framework method.
 *   4. Root "pm install" (works on userdebug/eng builds).
 *   5. Launch a known helper app (TV guard, file manager, Huan helper).
 */
public final class ApkInstaller {

    private static final String TAG = "ApkInstaller";
    private static final String PUBLIC_APK_NAME = "GeckoEngine.apk";
    private static final String MIME_APK = "application/vnd.android.package-archive";
    private static final String SESSION_ENTRY = "base.apk";

    private static final String[] HELPER_PACKAGES = {
        "com.tcl.tvweishi",
        "com.tcl.securityapp",
        "tv.huan.tvhelper",
        "com.tcl.tvappmanager",
        "com.tcl.ui_mediaCenter",
        "com.tcl.mediacenter",
        "com.tcl.common.viewer",
    };

    public static final class Result {
        public final boolean success;
        public final String summary;
        public final String report;
        public final String publicPath;

        Result(boolean success, String summary, String report, String publicPath) {
            this.success = success;
            this.summary = summary;
            this.report = report;
            this.publicPath = publicPath;
        }
    }

    private ApkInstaller() {}

    /**
     * Publish a private APK into the public Download directory so installers
     * and file managers can read it. Returns the public file, or null.
     */
    public static File publish(File source) {
        return publishToDownloads(source, new StringBuilder());
    }

    /**
     * Start a PackageInstaller session install. The result is delivered
     * asynchronously to a broadcast registered for statusAction. Returns true
     * if the session was created and committed.
     */
    public static boolean startSessionInstall(Context context, File apk, String statusAction) {
        PackageInstaller installer = obtainInstaller(context);
        if (installer == null) {
            Log.e(TAG, "PackageInstaller service unavailable");
            return false;
        }
        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            FileInputStream in = new FileInputStream(apk);
            OutputStream out = session.openWrite(SESSION_ENTRY, 0, apk.length());
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            session.fsync(out);
            in.close();
            out.close();

            Intent statusIntent = new Intent(statusAction);
            statusIntent.setPackage(context.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pending = PendingIntent.getBroadcast(
                    context, sessionId, statusIntent, flags);
            session.commit(pending.getIntentSender());
            Log.i(TAG, "Install session " + sessionId + " committed");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "startSessionInstall failed: " + rootMessage(t));
            return false;
        } finally {
            if (session != null) {
                try { session.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Obtain the PackageInstaller via its system-service name. The compile
     * jar used here strips Context.getPackageInstaller(), but that method is
     * backed by the "package_installer" service on real devices.
     */
    private static PackageInstaller obtainInstaller(Context context) {
        Object service = context.getSystemService("package_installer");
        if (service instanceof PackageInstaller) {
            return (PackageInstaller) service;
        }
        return null;
    }

    /**
     * Map a PackageInstaller status code to a readable name.
     */
    public static String statusName(int status) {
        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                return "SUCCESS";
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                return "PENDING_USER_ACTION";
            case PackageInstaller.STATUS_FAILURE:
                return "FAILURE";
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "FAILURE_ABORTED";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "FAILURE_BLOCKED";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "FAILURE_CONFLICT";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "FAILURE_INCOMPATIBLE";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "FAILURE_INVALID";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "FAILURE_STORAGE";
            default:
                return "UNKNOWN(" + status + ")";
        }
    }

    /**
     * Synchronous fallback chain used when the session API is unavailable.
     * Installs the APK at privatePath and returns a detailed Result.
     */
    public static Result install(Activity activity, String privatePath) {
        StringBuilder log = new StringBuilder();
        File source = new File(privatePath);
        if (!source.exists()) {
            return new Result(false, "APK file missing",
                    "APK not found: " + privatePath, null);
        }

        File publicFile = publishToDownloads(source, log);
        String publicPath = publicFile != null ? publicFile.getAbsolutePath() : null;

        List<Uri> candidates = new ArrayList<Uri>();
        if (publicFile != null) {
            candidates.add(Uri.fromFile(publicFile));
        }
        candidates.add(Uri.fromFile(source));

        for (Uri uri : candidates) {
            ComponentName launched = tryAllInstallActivities(activity, uri, log);
            if (launched != null) {
                return new Result(true,
                        "Installer launched: " + launched.flattenToShortString(),
                        log.toString(), publicPath);
            }
        }

        if (publicFile != null && tryFrameworkInstall(activity, Uri.fromFile(publicFile), log)) {
            return new Result(true, "Framework install invoked",
                    log.toString(), publicPath);
        }

        File rootTarget = publicFile != null ? publicFile : source;
        RootResult rootResult = runRootInstall(rootTarget);
        append(log, rootResult.diag);
        if (rootResult.success) {
            return new Result(true, "Root pm install succeeded",
                    log.toString(), publicPath);
        }

        String helper = launchHelperApp(activity, log);
        if (helper != null) {
            String summary = "Helper app opened: " + helper
                    + ". Locate " + PUBLIC_APK_NAME + " in the Download folder.";
            return new Result(false, summary, log.toString(), publicPath);
        }

        String report = log.toString()
                + "\nNo installer activity, framework channel, root channel, or helper "
                + "app was available. Install manually from a USB drive or ADB "
                + "(adb install <apk>).";
        return new Result(false, "No install channel available", report, publicPath);
    }

    private static File publishToDownloads(File source, StringBuilder log) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) {
                append(log, "Could not create Download directory");
                return null;
            }
            File target = new File(dir, PUBLIC_APK_NAME);
            if (target.exists() && !target.delete()) {
                append(log, "Could not replace existing " + target.getName());
            }
            if (!copyFile(source, target)) {
                append(log, "Failed to copy APK to Download directory");
                return null;
            }
            try {
                target.setReadable(true, false);
            } catch (Throwable ignored) {}
            if (target.length() == source.length()) {
                append(log, "APK published to " + target.getAbsolutePath()
                        + " (" + target.length() + " bytes)");
                return target;
            }
            append(log, "Public APK size mismatch; copy may be incomplete");
            return target;
        } catch (Throwable t) {
            append(log, "publishToDownloads error: " + t);
            return null;
        }
    }

    private static ComponentName tryAllInstallActivities(Activity activity, Uri uri,
            StringBuilder log) {
        PackageManager pm = activity.getPackageManager();
        List<HandlerTarget> handlers = new ArrayList<HandlerTarget>();
        collectHandlers(pm, Intent.ACTION_INSTALL_PACKAGE, uri, handlers, log);
        collectHandlers(pm, Intent.ACTION_VIEW, uri, handlers, log);
        orderHandlers(handlers);

        for (HandlerTarget target : handlers) {
            try {
                Intent intent = new Intent(target.action);
                intent.setDataAndType(uri, MIME_APK);
                intent.setComponent(target.component);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                activity.startActivity(intent);
                append(log, "Started installer activity "
                        + target.component.flattenToShortString() + " via " + target.action);
                return target.component;
            } catch (Throwable t) {
                append(log, "Could not start "
                        + target.component.flattenToShortString() + ": " + t);
            }
        }
        return null;
    }

    private static void collectHandlers(PackageManager pm, String action, Uri uri,
            List<HandlerTarget> out, StringBuilder log) {
        try {
            Intent intent = new Intent(action);
            intent.setDataAndType(uri, MIME_APK);
            List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
            if (resolved == null) {
                return;
            }
            for (ResolveInfo ri : resolved) {
                if (ri.activityInfo == null) {
                    continue;
                }
                ComponentName cn = new ComponentName(ri.activityInfo.packageName,
                        ri.activityInfo.name);
                boolean known = false;
                for (HandlerTarget existing : out) {
                    if (existing.component.equals(cn)) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    out.add(new HandlerTarget(cn, action));
                    append(log, "Found handler for " + action + ": "
                            + cn.flattenToShortString());
                }
            }
        } catch (Throwable t) {
            append(log, "collectHandlers(" + action + ") error: " + t);
        }
    }

    private static void orderHandlers(List<HandlerTarget> handlers) {
        List<HandlerTarget> preferred = new ArrayList<HandlerTarget>();
        List<HandlerTarget> others = new ArrayList<HandlerTarget>();
        for (HandlerTarget target : handlers) {
            String pkg = target.component.getPackageName().toLowerCase();
            if (pkg.contains("packageinstaller") || pkg.contains("installer")) {
                preferred.add(target);
            } else {
                others.add(target);
            }
        }
        handlers.clear();
        handlers.addAll(preferred);
        handlers.addAll(others);
    }

    private static final class HandlerTarget {
        final ComponentName component;
        final String action;
        HandlerTarget(ComponentName component, String action) {
            this.component = component;
            this.action = action;
        }
    }

    private static boolean tryFrameworkInstall(Context context, Uri uri, StringBuilder log) {
        try {
            PackageManager pm = context.getPackageManager();
            Class<?> observerClass =
                    Class.forName("android.content.pm.IPackageInstallObserver");
            Method m = PackageManager.class.getMethod("installPackage",
                    Uri.class, observerClass, int.class, String.class);
            m.invoke(pm, uri, null, 0, context.getPackageName());
            append(log, "PackageManager.installPackage invoked");
            return true;
        } catch (Throwable t) {
            append(log, "Framework install unavailable: " + rootMessage(t));
            return false;
        }
    }

    private static final class RootResult {
        final boolean success;
        final String diag;
        RootResult(boolean success, String diag) {
            this.success = success;
            this.diag = diag;
        }
    }

    /**
     * Attempt a root install. Before invoking the package manager, lower the
     * dex2oat compiler filter to interpret-only so the large GeckoView DEX
     * does not exhaust the limited RAM during AOT compilation. All output,
     * including the exact Failure reason, is captured.
     */
    private static RootResult runRootInstall(File apk) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("su");
            pb.redirectErrorStream(true);
            process = pb.start();
            OutputStream os = process.getOutputStream();
            String script =
                    "setprop dalvik.vm.dex2oat-filter interpret-only\n"
                    + "pm install -r " + apk.getAbsolutePath() + "\n"
                    + "exit\n";
            os.write(script.getBytes());
            os.flush();
            os.close();

            InputStream is = process.getInputStream();
            StringBuilder out = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) > 0) {
                out.append(new String(buffer, 0, read));
            }
            int exit = process.waitFor();
            String output = out.toString();
            boolean success = output.contains("Success");
            String diag = "Root install output (exit " + exit + "):\n"
                    + output + "\n";
            return new RootResult(success, diag);
        } catch (Throwable t) {
            return new RootResult(false,
                    "Root install unavailable: " + rootMessage(t) + "\n");
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    private static String launchHelperApp(Activity activity, StringBuilder log) {
        PackageManager pm = activity.getPackageManager();
        String dynamicHelper = findFileManager(pm, log);

        for (String pkg : HELPER_PACKAGES) {
            if (launchPackage(activity, pkg, log)) {
                return pkg;
            }
        }
        if (dynamicHelper != null && launchPackage(activity, dynamicHelper, log)) {
            return dynamicHelper;
        }
        return null;
    }

    private static String findFileManager(PackageManager pm, StringBuilder log) {
        try {
            Intent probe = new Intent(Intent.ACTION_GET_CONTENT);
            probe.setType("file/*");
            List<ResolveInfo> list = pm.queryIntentActivities(probe, 0);
            if (list != null) {
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo != null) {
                        String pkg = ri.activityInfo.packageName;
                        String name = pkg.toLowerCase();
                        if (name.contains("file") || name.contains("manager")
                                || name.contains("explorer")) {
                            append(log, "Discovered file manager: " + pkg);
                            return pkg;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            append(log, "findFileManager error: " + t);
        }
        return null;
    }

    private static boolean launchPackage(Activity activity, String pkg, StringBuilder log) {
        try {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            append(log, "Launched helper app " + pkg);
            return true;
        } catch (Throwable t) {
            append(log, "Could not launch " + pkg + ": " + rootMessage(t));
            return false;
        }
    }

    private static boolean copyFile(File source, File target) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target);
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "copyFile failed", t);
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + (msg != null ? (": " + msg) : "");
    }

    private static void append(StringBuilder log, String line) {
        Log.i(TAG, line);
        log.append(line).append('\n');
    }
}
