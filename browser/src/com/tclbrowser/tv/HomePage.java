package com.tclbrowser.tv;

public final class HomePage {

    private HomePage() {}

    private static final String[][] SITES = {
        {"云原神", "https://ys.mihoyo.com/cloud/", "#6b8afd"},
        {"哔哩哔哩", "https://www.bilibili.com", "#f78fb3"},
        {"抖音", "https://www.douyin.com", "#5fd0c5"},
        {"微博", "https://weibo.com", "#ff9a8b"},
        {"知乎", "https://www.zhihu.com", "#7fb8ff"},
        {"百度", "https://www.baidu.com", "#9bb8ff"},
        {"优酷", "https://www.youku.com", "#6fdfa8"},
        {"爱奇艺", "https://www.iqiyi.com", "#92c6ff"},
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
            + "html,body{min-height:100%;width:100%;}"
            + "html{background:#0b0e14;}"
            + "body{font-family:'Segoe UI','PingFang SC','Microsoft YaHei',Arial,sans-serif;"
            + "color:#e8ecf4;"
            + "background-color:#0b0e14;"
            + "background-image:linear-gradient(180deg,#151c2b 0%,#0d1119 42%,#0a0c12 100%);"
            + "background-attachment:fixed;"
            + "min-height:100vh;}"
            + ".shell{position:relative;max-width:1180px;margin:0 auto;"
            + "padding:46px 22px 30px;}"
            + ".head{text-align:center;}"
            + ".logo{font-size:36px;font-weight:700;letter-spacing:1px;color:#f3f6ff;}"
            + ".sub{margin-top:10px;color:#94a0b8;font-size:15px;}"
            + ".search{margin:26px auto 0;display:flex;align-items:center;"
            + "background:#141b2d;border:1px solid #25304a;border-radius:28px;"
            + "max-width:620px;height:54px;padding:0 24px;color:#a6b0c6;"
            + "font-size:16px;text-decoration:none;}"
            + ".grid{margin-top:42px;display:flex;flex-wrap:wrap;justify-content:center;}"
            + ".card{width:150px;margin:10px;background:#141a28;"
            + "border:1px solid #232c42;border-radius:16px;height:120px;"
            + "display:flex;flex-direction:column;align-items:center;justify-content:center;"
            + "text-decoration:none;color:#e2e8f5;}"
            + ".card:hover,.card:focus{background:#1b2438;border-color:#4a64c8;outline:none;}"
            + ".dot{width:40px;height:40px;border-radius:13px;display:block;"
            + "box-shadow:0 8px 20px rgba(0,0,0,.4);}"
            + ".label{margin-top:14px;font-size:16px;font-weight:500;}"
            + ".foot{margin-top:30px;text-align:center;color:#5d6880;font-size:13px;}"
            + "</style></head><body>"
            + "<div class='shell'>"
            + "<div class='head'><div class='logo'>TCL Browser</div>"
            + "<div class='sub'>使用地址栏搜索，或点击下方网站</div>"
            + "<a class='search' href='https://cn.bing.com'>搜索网络</a></div>"
            + "<div class='grid'>" + cards + "</div>"
            + "<div class='foot'>遥控方向键移动光标，确认键点击</div>"
            + "</div>"
            + "</body></html>";
    }

    public static String url() {
        return "about:home";
    }
}
