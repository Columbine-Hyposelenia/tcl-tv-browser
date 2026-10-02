package com.tclbrowser.tv;

import android.webkit.WebViewClient;

public final class ErrorPages {

    private ErrorPages() {}

    public static String page(int errorCode, String description, String failingUrl) {
        String title = titleFor(errorCode);
        String detail = description != null ? description : "";
        String safeUrl = failingUrl != null ? escape(failingUrl) : "";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box;}"
                + "html{background:#0b0e14;}"
                + "body{background:#0b0e14;font-family:'Segoe UI','PingFang SC',Arial,sans-serif;"
                + "min-height:100vh;display:flex;align-items:center;justify-content:center;}"
                + ".card{text-align:center;padding:40px;max-width:560px;}"
                + ".code{font-size:84px;font-weight:700;color:#5b7cff;line-height:1;}"
                + ".title{font-size:26px;font-weight:600;color:#e8ecf4;margin-top:16px;}"
                + ".desc{font-size:15px;color:#94a0b8;margin-top:10px;line-height:1.6;}"
                + ".url{font-size:13px;color:#7d88a0;margin-top:22px;word-break:break-all;"
                + "background:#141b2d;padding:10px 14px;border-radius:8px;}"
                + "</style></head><body><div class='card'>"
                + "<div class='code'>!</div>"
                + "<div class='title'>" + escape(title) + "</div>"
                + "<div class='desc'>" + escape(detail) + "</div>"
                + "<div class='url'>" + safeUrl + "</div>"
                + "</div></body></html>";
    }

    private static String titleFor(int errorCode) {
        switch (errorCode) {
            case WebViewClient.ERROR_HOST_LOOKUP:
                return "无法解析此网站";
            case WebViewClient.ERROR_CONNECT:
                return "无法连接到服务器";
            case WebViewClient.ERROR_TIMEOUT:
                return "连接超时";
            case WebViewClient.ERROR_BAD_URL:
                return "网址无效";
            case WebViewClient.ERROR_UNSUPPORTED_SCHEME:
                return "不支持的链接类型";
            case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                return "安全连接失败";
            case WebViewClient.ERROR_FILE_NOT_FOUND:
                return "文件不存在";
            default:
                return "网页无法加载";
        }
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
