package com.tclbrowser.tv;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;

import java.io.File;

public final class Downloads {

    private Downloads() {}

    public static long enqueue(Context context, String url, String userAgent,
            String contentDisposition, String mimeType) {
        try {
            DownloadManager manager =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(false);
            request.setTitle(fileName);
            request.setDescription(url);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            if (mimeType != null && mimeType.length() > 0) {
                request.setMimeType(mimeType);
            }
            request.allowScanningByMediaScanner();

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            return manager.enqueue(request);
        } catch (Throwable t) {
            return -1L;
        }
    }

    public static void openDownloads(Context context) {
        try {
            DownloadManager manager =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return;
            }
            // Fallback: try opening the Downloads directory via a file manager
            try {
                File dlDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                Intent fileIntent = new Intent(Intent.ACTION_VIEW);
                fileIntent.setDataAndType(Uri.fromFile(dlDir),
                        "resource/folder");
                fileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (fileIntent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(fileIntent);
                    return;
                }
            } catch (Throwable ignored) {
                // fall through
            }
            // Last resort: show a toast with the download path
            if (context instanceof android.app.Activity) {
                android.widget.Toast.makeText(context,
                        "Download manager not available. Files are saved to: "
                        + Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
                        android.widget.Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignored) {}
    }
}
