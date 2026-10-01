package com.tclbrowser.tv;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class Intents {

    private Intents() {}

    public static void handleNonHttp(Context context, String url) {
        if (url == null) return;
        try {
            if (url.startsWith("intent://")) {
                handleIntentUri(context, url);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            // The target app is not installed; silently ignore on TV.
        }
    }

    private static void handleIntentUri(Context context, String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return;
            } catch (ActivityNotFoundException ignored) {}
            String fallback = intent.getStringExtra("browser_fallback_url");
            if (fallback != null) {
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(fallback));
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(view);
                return;
            }
            String pkg = intent.getPackage();
            if (pkg != null) {
                try {
                    Intent market = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + pkg));
                    market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(market);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void openExternal(Context context, Uri uri, String type) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, type);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }
}
