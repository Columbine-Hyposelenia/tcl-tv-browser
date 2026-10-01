package com.tclbrowser.tv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VideoSniffer {

    private static final String[] VIDEO_EXTENSIONS = {
            ".mp4", ".m3u8", ".flv", ".webm", ".mov", ".m4v", ".mkv",
            ".ts", ".avi", ".asf", ".rmvb", ".rm", ".mpg", ".mpeg", ".vob"
    };

    private final LinkedHashMap<String, String> videos = new LinkedHashMap<String, String>();

    public void reset() {
        videos.clear();
    }

    public void inspect(String url) {
        if (url == null) return;
        if (isVideo(url) && !videos.containsKey(url)) {
            videos.put(url, labelFor(url));
        }
    }

    public void inspectMime(String url, String mime) {
        if (url == null) return;
        if (mime != null && mime.startsWith("video/") && !videos.containsKey(url)) {
            videos.put(url, labelFor(url));
        }
    }

    public static boolean isVideo(String url) {
        if (url == null) return false;
        String path = url.toLowerCase();
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        for (String ext : VIDEO_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        return false;
    }

    private String labelFor(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        return Urls.decode(path);
    }

    public List<Map.Entry<String, String>> getVideos() {
        return new ArrayList<Map.Entry<String, String>>(videos.entrySet());
    }

    public int count() {
        return videos.size();
    }
}
