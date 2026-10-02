package com.tclbrowser.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles launching an APK installer through every channel available on the
 * device. The system WebView-based browser downloads the Gecko engine APK and
 * must hand it off to a package installer, but some TCL devices neither expose
 * a standard installer activity nor allow reading files inside the caller's
 * private Android/data directory. This class:
 *   1. copies the APK to the public Download directory so any installer can
 *      read it,
 *   2. enumerates every activity that can install/view an APK at runtime,
 *   3. tries the hidden PackageManager.installPackage framework method,
 *   4. tries a root "pm install" (works on userdebug/eng builds),
 *   5. falls back to launching known helper apps (TV guard, file manager,
 *      Huan helper),
 *   6. returns a detailed, human-readable diagnostic report.
 */
public final class ApkInstaller {

    private static final String TAG = "ApkInstaller";
    private static final String PUBLIC_APK_NAME = "GeckoEngine.apk";
    private static final String MIME_APK = "application/vnd.android.package-archive";

    // Known helper apps that may offer file browsing / installation on TCL
    // and Amlogic devices. The first entry that is installed is launched.
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
     * Install the APK located at privatePath. Returns a Result describing the
     * outcome with a full diagnostic report.
     */
    public static Result install(Activity activity, String privatePath) {
        StringBuilder log = new StringBuilder();
        File source = new File(privatePath);
        if (!source.exists()) {
            return new Result(false, "APK file missing",
                    "APK not found: " + privatePath, null);
        }

        // Step 1: publish the APK to the public Download directory so that
        // installers and file managers can actually read it. Files under
        // Android/data/<caller>/ are not readable by other apps.
        File publicFile = publishToDownloads(source, log);
        String publicPath = publicFile != null ? publicFile.getAbsolutePath() : null;

        // Step 2: enumerate and launch every activity that can install the
        // APK, for both the public and (if present) private locations.
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

        // Step 3: hidden framework install. Requires INSTALL_PACKAGES and
        // normally fails for third-party apps, but costs nothing to try.
        if (publicFile != null && tryFrameworkInstall(activity, Uri.fromFile(publicFile), log)) {
            return new Result(true, "Framework install invoked",
                    log.toString(), publicPath);
        }

        // Step 4: root pm install (userdebug/eng builds may allow su).
        File rootTarget = publicFile != null ? publicFile : source;
        if (tryRootInstall(rootTarget, log)) {
            return new Result(true, "Root pm install succeeded",
                    log.toString(), publicPath);
        }

        // Step 5: launch a known helper app where the user can locate the
        // APK in the Download folder and install it manually.
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

    /**
     * Copy the APK into the public Download directory and make it world
     * readable. Returns the public file, or null if it could not be created.
     */
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
            // Best-effort: make the file readable by every package.
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

    /**
     * Query and launch every activity that claims to handle APK installation
     * or viewing for the given URI. Returns the launched component, or null.
     */
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

    /**
     * Move stock package installers to the front so they are tried first.
     */
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

    /** A resolved installer component paired with the action it registered. */
    private static final class HandlerTarget {
        final ComponentName component;
        final String action;
        HandlerTarget(ComponentName component, String action) {
            this.component = component;
            this.action = action;
        }
    }

    /**
     * Invoke the hidden PackageManager.installPackage method via reflection.
     * Requires the INSTALL_PACKAGES signature permission; typically throws
     * SecurityException for ordinary apps.
     */
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

    /**
     * Attempt a root install via "su -c pm install -r <path>". This works on
     * userdebug/eng builds where su is reachable from app processes.
     */
    private static boolean tryRootInstall(File apk, StringBuilder log) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            OutputStream os = process.getOutputStream();
            String cmd = "pm install -r " + apk.getAbsolutePath() + "\n"
                    + "exit\n";
            os.write(cmd.getBytes());
            os.flush();
            os.close();
            int exit = process.waitFor();
            if (exit == 0) {
                append(log, "Root pm install reported success");
                return true;
            }
            append(log, "su pm install exited with code " + exit);
            return false;
        } catch (Throwable t) {
            append(log, "Root install unavailable: " + rootMessage(t));
            return false;
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Launch the first installed helper app (TV guard, file manager, Huan
     * helper). Returns the launched package name, or null.
     */
    private static String launchHelperApp(Activity activity, StringBuilder log) {
        PackageManager pm = activity.getPackageManager();

        // Also discover any installed file manager at runtime.
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
