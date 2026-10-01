# TCL 电视浏览器

为 TCL 老款 Android 电视（Amlogic T968 / Android 5.1.1 / 32 位）定制的电视浏览器。
大屏默认桌面（电脑）版，重点适配遥控方向键操控的虚拟鼠标，同时支持实体键盘与鼠标。

## 架构

受欢视助手安装通道限制，主安装包必须 ≤10MB，而现代浏览器内核（GeckoView 144 的 libxul 约 113MB）无法塞进主包。
因此采用「小主包 + 动态下载引擎」：

| 组件 | 包名 | 大小 | 说明 |
| --- | --- | --- | --- |
| 主 App | `com.tclbrowser.tv` | 约 45KB | 系统 WebView 浏览器 + 引擎引导/安装 |
| 引擎 | `com.tclbrowser.gecko` | 约 84MB | GeckoView 144（最后支持 Android 5.1 的版本），独立完整浏览器 |

首次使用时在主 App 菜单选择「安装 Gecko 引擎」，下载安装引擎后即可用现代内核浏览、播放视频。
引擎 APK 通过 GitHub Release 托管。

## 目录

- `browser/`   主 App 工程（手工流水线，无 Gradle）
- `engine/`    GeckoView 引擎工程（手工流水线，无 Gradle）
- `devinfo/`   设备信息采集/内核探针工具
- `kernel_research/`  关键调研资料与参考源码（Gecko API、AOSP WebViewFactory 等）
- `prepare_libs.py`、`gen_manifest.py`、`gen_library_R.py` 引擎构建辅助脚本

## 目标设备

- SoC：Amlogic T968（4×Cortex-A53），GPU Mali-T830 MP2
- 系统：Android 5.1.1（SDK 22），ABI `armeabi-v7a`
- 内存约 1.2GB，闪存 8GB，屏幕 1920×1080
- 系统 WebView 为 Chromium 39（TCL 私钥签名，无法替换）

## 构建

两个工程均使用「aapt → javac → d8/r8 → aapt add → zipalign → apksigner」手工流水线，
需要 Android SDK（build-tools 34、platform android-30）、JDK 17。

> 引擎 dex 转换必须使用较新的 R8（9.5+），旧版 d8（8.2）对 AndroidX KMP 产物会抛 NPE。

```bash
cd browser && bash build.sh   # 产出 bin/browser.apk
cd engine  && bash build.sh   # 产出 bin/engine.apk
```

## 键盘快捷键

`F5/Ctrl+R` 刷新 · `Ctrl+L/F6` 地址栏 · `Ctrl+F` 查找 · `Ctrl+J` 下载 ·
`Ctrl+T` 主页 · `Ctrl+±/0` 缩放 · `Alt+方向` 前进/后退 · `F1` 帮助
