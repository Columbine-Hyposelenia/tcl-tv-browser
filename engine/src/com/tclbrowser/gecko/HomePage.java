package com.tclbrowser.gecko;

public final class HomePage {

    private HomePage() {}

    private static final String[][] SITES = {
        {"云原神", "https://ys.mihoyo.com/cloud/", "#6b8afd"},
        {"哔哩哔哩", "https://www.bilibili.com", "#f78fb3"},
        {"抖音", "https://www.douyin.com", "#7fd8c0"},
        {"微博", "https://weibo.com", "#ff9a8b"},
        {"知乎", "https://www.zhihu.com", "#8fc7ff"},
        {"百度", "https://www.baidu.com", "#9bb8ff"},
        {"优酷", "https://www.youku.com", "#7ee0b0"},
        {"爱奇艺", "https://www.iqiyi.com", "#9ed0ff"},
        {"腾讯视频", "https://v.qq.com", "#ffb27f"},
        {"斗鱼直播", "https://www.douyu.com", "#ff9aa2"},
        {"虎牙直播", "https://www.huya.com", "#ffd28a"},
        {"YouTube", "https://www.youtube.com", "#ff8f8f"},
    };

    public static String html() {
        StringBuilder cards = new StringBuilder();
        for (String[] s : SITES) {
            cards.append("<a class='card' href='").append(s[1]).append("'>")
                 .append("<span class='dot' style='background:").append(s[2]).append("'></span>")
                 .append("<span class='label'>").append(s[0]).append("</span></a>");
        }
        return "<!DOCTYPE html><html lang='zh'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Home</title><style>"
            + "*{margin:0;padding:0;box-sizing:border-box;}"
            + "html,body{height:100%;}"
            + "body{background:radial-gradient(1200px 600px at 50% -10%,#16203a,#0b0e16 70%);"
            + "font-family:'Segoe UI','PingFang SC',Arial,sans-serif;color:#e8ecf4;"
            + "display:flex;flex-direction:column;align-items:center;overflow:hidden;}"
            + ".head{margin-top:6vh;text-align:center;}"
            + ".logo{font-size:34px;font-weight:700;letter-spacing:1px;"
            + "background:linear-gradient(90deg,#7fb0ff,#9b8cff);"
            + "-webkit-background-clip:text;background-clip:text;color:transparent;}"
            + ".sub{margin-top:8px;color:#8a93a8;font-size:14px;}"
            + ".search{margin-top:26px;display:flex;align-items:center;"
            + "background:#141b2d;border:1px solid #232c45;border-radius:26px;"
            + "width:min(620px,86vw);height:52px;padding:0 22px;color:#9aa4bd;"
            + "font-size:16px;text-decoration:none;}"
            + ".grid{margin-top:46px;display:grid;"
            + "grid-template-columns:repeat(6,minmax(120px,1fr));gap:18px;"
            + "width:min(1180px,92vw);}"
            + ".card{background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.07);"
            + "border-radius:16px;height:118px;display:flex;flex-direction:column;"
            + "align-items:center;justify-content:center;gap:14px;text-decoration:none;"
            + "color:#dfe5f2;transition:transform .12s,border-color .12s,background .12s;}"
            + ".card:hover,.card:focus{transform:translateY(-3px);background:rgba(123,160,255,0.14);"
            + "border-color:#5b7cff;outline:none;}"
            + ".dot{width:38px;height:38px;border-radius:12px;display:block;"
            + "box-shadow:0 6px 18px rgba(0,0,0,.35);}"
            + ".label{font-size:16px;font-weight:500;}"
            + ".foot{margin-top:auto;padding:18px;color:#5a6378;font-size:12px;}"
            + "</style></head><body>"
            + "<div class='head'><div class='logo'>TCL Browser</div>"
            + "<div class='sub'>&#26032; use the address bar or pick a site below</div>"
            + "<a class='search' href='https://cn.bing.com'>&#128269; Search the web</a></div>"
            + "<div class='grid'>" + cards + "</div>"
            + "<div class='foot'>Remote D-Pad moves the cursor, OK to click</div>"
            + "</body></html>";
    }

    public static String url() {
        return "about:home";
    }
}
