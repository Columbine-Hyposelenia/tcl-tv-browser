package com.tclbrowser.devinfo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Audits the package-install environment: the unknown-sources switch, root
 * availability, every activity/service that can handle an APK install, and
 * which packages actually hold the privileged INSTALL_PACKAGES permission.
 */
public final class InstallerAudit {

    private static final String MIME_APK = "application/vnd.android.package-archive";
    private static final String PERM_INSTALL_PACKAGES = "android.permission.INSTALL_PACKAGES";

    private static final String[] SERVICE_ACTIONS = {
            "android.intent.action.APPSTORE_INSTALL_APK",
            "com.tcl.packageinstaller.service.InstallerService",
            "com.tcl.packageinstaller.service.UninstallerService"
    };

    private static final String[] PACKAGE_KEYWORDS = {
            "install", "package", "security", "weishi", "huan",
            "mediacenter", "viewer", "appstore", "defcontainer", "tvapp", "filemanager"
    };

    private InstallerAudit() {
    }

    public static String audit(Context context) {
        StringBuilder sb = new StringBuilder();
        PackageManager pm = context.getPackageManager();

        appendUnknownSources(context, sb);
        appendRootProbe(sb);
        appendActivityHandlers(context, pm, sb);
        appendServiceHandlers(context, pm, sb);
        appendRelevantPackages(pm, sb);

        return sb.toString();
    }

    private static void appendUnknownSources(Context context, StringBuilder sb) {
        sb.append("Unknown sources (INSTALL_NON_MARKET_APPS): ");
        try {
            int value = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.INSTALL_NON_MARKET_APPS, -1);
            sb.append(value == 1 ? "ENABLED (1)" : value == 0 ? "DISABLED (0)" : "unknown (" + value + ")");
        } catch (Throwable t) {
            sb.append("unreadable: ").append(t.getMessage());
        }
        sb.append("\n");
    }

    private static void appendRootProbe(StringBuilder sb) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            InputStream is = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            String result = new String(out.toByteArray()).trim();
            if (finished && result.contains("uid=0")) {
                sb.append("Root via 'su': AVAILABLE -> ").append(result).append("\n");
            } else if (finished) {
                sb.append("Root via 'su': denied (output: ").append(result).append(")\n");
            } else {
                sb.append("Root via 'su': timed out waiting for authorization\n");
            }
        } catch (Throwable t) {
            sb.append("Root via 'su': unavailable (").append(t.getMessage()).append(")\n");
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void appendActivityHandlers(Context context, PackageManager pm, StringBuilder sb) {
        File probe = new File(context.getCacheDir(), "probe.apk");
        Uri uri = Uri.fromFile(probe);

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(uri, MIME_APK);
        sb.append("\nHandlers for ACTION_VIEW (apk):\n");
        appendActivities(pm, viewIntent, sb);

        Intent installIntent = new Intent("android.intent.action.INSTALL_PACKAGE");
        installIntent.setDataAndType(uri, MIME_APK);
        sb.append("Handlers for ACTION_INSTALL_PACKAGE:\n");
        appendActivities(pm, installIntent, sb);
    }

    private static void appendActivities(PackageManager pm, Intent intent, StringBuilder sb) {
        try {
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            if (list == null || list.isEmpty()) {
                sb.append("  (none)\n");
                return;
            }
            for (ResolveInfo ri : list) {
                sb.append("  ").append(ri.activityInfo.packageName)
                        .append("/").append(ri.activityInfo.name)
                        .append(" exported=").append(ri.activityInfo.exported)
                        .append("\n");
            }
        } catch (Throwable t) {
            sb.append("  query failed: ").append(t.getMessage()).append("\n");
        }
    }

    private static void appendServiceHandlers(Context context, PackageManager pm, StringBuilder sb) {
        sb.append("\nInstall-related service handlers:\n");
        for (String action : SERVICE_ACTIONS) {
            sb.append("Action ").append(action).append(":\n");
            Intent intent = new Intent(action);
            try {
                List<ResolveInfo> list = pm.queryIntentServices(intent, 0);
                if (list == null || list.isEmpty()) {
                    sb.append("  (none)\n");
                    continue;
                }
                for (ResolveInfo ri : list) {
                    ServiceInfo si = ri.serviceInfo;
                    sb.append("  ").append(si.packageName).append("/").append(si.name)
                            .append(" exported=").append(si.exported)
                            .append(" permission=").append(si.permission)
                            .append("\n");
                }
            } catch (Throwable t) {
                sb.append("  query failed: ").append(t.getMessage()).append("\n");
            }
        }
    }

    private static void appendRelevantPackages(PackageManager pm, StringBuilder sb) {
        sb.append("\nInstaller/security related packages:\n");
        try {
            List<PackageInfo> all = pm.getInstalledPackages(0);
            for (PackageInfo pi : all) {
                String name = pi.packageName == null ? "" : pi.packageName.toLowerCase();
                if (!matchesKeyword(name)) {
                    continue;
                }
                boolean holdsInstall = pm.checkPermission(PERM_INSTALL_PACKAGES, pi.packageName)
                        == PackageManager.PERMISSION_GRANTED;
                boolean system = (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                sb.append("  ").append(pi.packageName)
                        .append(" v").append(pi.versionName)
                        .append(" system=").append(system)
                        .append(" HOLDS_INSTALL_PACKAGES=").append(holdsInstall)
                        .append("\n");
                appendExportedServices(pm, pi.packageName, sb);
            }
        } catch (Throwable t) {
            sb.append("  enumeration failed: ").append(t.getMessage()).append("\n");
        }
    }

    private static void appendExportedServices(PackageManager pm, String packageName, StringBuilder sb) {
        try {
            PackageInfo full = pm.getPackageInfo(packageName, PackageManager.GET_SERVICES);
            if (full.services == null) {
                return;
            }
            for (ServiceInfo si : full.services) {
                if (si.exported) {
                    sb.append("      exported service: ").append(si.name)
                            .append(" permission=").append(si.permission)
                            .append("\n");
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean matchesKeyword(String name) {
        for (String keyword : PACKAGE_KEYWORDS) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
