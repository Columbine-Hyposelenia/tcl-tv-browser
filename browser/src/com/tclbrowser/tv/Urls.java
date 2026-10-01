package com.tclbrowser.tv;

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Pattern;

public final class Urls {

    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?$");

    private static final String SEARCH_ENGINE = "https://cn.bing.com/search?q=";

    private Urls() {}

    public static String normalize(String input) {
        if (input == null) return "";
        String text = input.trim();
        if (text.isEmpty()) return "";

        String lower = text.toLowerCase();
        if (lower.startsWith("about:")) return text;
        if (lower.startsWith("javascript:")) return text;
        if (lower.startsWith("file://")) return text;
        if (lower.startsWith("content://")) return text;

        if (text.contains(" ") || !looksLikeUrl(text)) {
            return searchUrl(text);
        }

        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            text = "http://" + text;
        }
        return text;
    }

    public static String searchUrl(String query) {
        try {
            return SEARCH_ENGINE + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return SEARCH_ENGINE + Uri.encode(query);
        }
    }

    public static boolean looksLikeUrl(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true;
        if (lower.startsWith("localhost")) return true;
        if (DOMAIN_PATTERN.matcher(text).matches()) return true;
        return false;
    }

    public static String host(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : url;
        } catch (Throwable t) {
            return url;
        }
    }

    public static String decode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Throwable t) {
            return value;
        }
    }
}
