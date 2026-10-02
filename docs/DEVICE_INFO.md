# 设备关键信息（持续开发参考）

> 本文档是 tcl-tv-browser 的目标设备「档案」，供后续开发与排障使用。
> 标注 **[已验证]** 的来自真机实测/探针/反编译；标注 **[待验证]** 的为假设，需真机数据确认。
> 最后更新：v1.7 开发中（2026-10-02）。

---

## 1. 设备概览

| 项目 | 参数 |
| --- | --- |
| 品牌 / 机型 | TCL 智能电视，工程代号 **p331**，标签 `tvname=[TV]` |
| 厂商（ROM） | TCL Multimedia（ro.build.company） |
| SoC | **Amlogic T968**（ro.hardware=amlogic，board/平台 gxbaby） |
| 系统 | **Android 5.1.1 LMY47V（API 22）**，userdebug + dev-keys |
| 构建日期 | 2020-08-26（固件号 00502001） |
| CPU | 4× ARM Cortex-A53（硬件 64 位，但系统/应用跑 **32 位**） |
| GPU | ARM **Mali-T830**，驱动 r7p0-02rel0（实际 GLES 3.1） |
| 内存 | 1228 MB（约 1.2 GB） |
| 闪存 | /data 与 sdcard 可用约 3.86 GB（标称 8 GB） |
| 屏幕 | 1920×1080 @60Hz，densityDpi 240（density 1.5，DPR 1.5） |
| ABI | **armeabi-v7a / armeabi**（64 位 abilist 为空） |
| 系统 WebView | **Chromium 39**（TCL 私钥签名，不可替换） |
| 网络 | WiFi wlan0（如 192.168.1.4），时区 Asia/Shanghai，语言 zh/CN |

---

## 2. 硬件详情

### 2.1 SoC / CPU
- Amlogic T968：四核 Cortex-A53。**[已验证]**
- 硬件支持 AArch64，但本机 `ro.product.cpu.abilist64=[]`，整个系统是 32 位镜像，
  因此 **只能使用 armeabi-v7a（32 位）原生库**；64 位 .so 不会被加载。**[已验证]**
- `ro.product.cpu.abilist = armeabi-v7a,armeabi`；`ro.product.cpu.abi = armeabi-v7a`。

### 2.2 GPU（重点：声明能力 ≠ 实际能力）
- 型号字符串：`ARM – Mali-T830`；驱动 `OpenGL ES 3.1 v1.r7p0-02rel0.9a2b627d8986d580e85bfed4bda9e2d8`。**[已验证, about:support]**
- **实际能力：OpenGL ES 3.1 / GLSL ES 3.10，WebGL1 与 WebGL2 均可用**（inProcess:true）。
- 但系统属性 `ro.opengles.version = 131072`（即对外只声明 **GLES 2.0**）。
  - 影响：部分库/应用只读该属性会误判为 GLES2；GeckoView 运行时实测到 3.1，故 WebGL2 可用。
- WebGL 扩展包含 EGL_ANDROID_image_native_buffer / recordable / framebuffer_target、
  KHR_image / fence_sync / partial_update、ARM_mali_shader_binary 等。
- **WebGPU 不可用**：`navigator.gpu = null`；WEBGPU 在 Android 被 blocklist。
- 决策日志（about:support）：
  - `WEBRENDER` default available；合成器 = **WebRender（OpenGL/GLES）**。
  - `WEBRENDER_COMPOSITOR` default disabled（FEATURE_FAILURE_DISABLED）。
  - `WEBRENDER_PARTIAL` env blocklisted by gfxInfo（**bug 1680087**：Mali-T6xx/T7xx partial present 白块闪烁，已默认禁用 partial present）。
  - `WEBRENDER_SHADER_CACHE` / `OPTIMIZED_SHADERS` env blocklisted（**bug 1689064**：Mali-T6xx optimized shaders 破坏渲染，已强制 use-optimized-shaders=false）。
  - `WEBRENDER_ANGLE` OS not supported；`GL_NORM16_TEXTURES` blocklisted（MISSING_EXTENSION）。
- Canvas/Content 2D 后端：**skia**（AzureCanvasBackend / AzureContentBackend = skia）。

### 2.3 内存 / 存储
- RAM 总量 1228 MB；探针报告可用约 493 MB。
- 单应用 VM heap 上限约 192 MB；large memory class 约 512 MB（engine 已声明 `largeHeap`）。
- /data 与 /sdcard 可用空间约 3859 MB。
- 网页进程在内存紧张时会被系统 **kill（onKill）**；与 GPU 驱动 **crash（onCrash）** 是两条不同路径，
  必须分别计数以定位根因。**[已验证机制；本机主因待计数确认]**

### 2.4 显示
- 分辨率/刷新率：1920×1080 @60Hz；scales 1.5 / 1.0；DisplayCount 1。
- densityDpi=240 → density=1.5，CSS DPR=1.5。布局/图标按此换算。

---

## 3. 系统与软件

### 3.1 Build 指纹
```
ro.build.description = p331-userdebug 5.1.1 LMY47V 20200826 dev-keys
ro.build.fingerprint = Android/p331/p331:5.1.1/LMY47V/20200826:userdebug/dev-keys
ro.build.id = LMY47V   ro.build.type = userdebug   ro.build.tags = dev-keys
ro.build.host = ubuntu199231   ro.build.user = cq
ro.product.manufacturer = amlogic   ro.product.brand = Android
ro.product.model = AOSP on p331
ro.product.otaupdateurl = http://10.28.11.53:8080/otaupdate/update
```
- userdebug + dev-keys：adbd 通常具备调试能力（可能 root），是拿 logcat 的潜在通道。

### 3.2 系统 WebView（不可替换）
- 系统当前 WebView：`com.android.webview` **version 39（20200826-arm, 300001）**，
  路径 `/system/app/webview/webview.apk`，primaryCpuAbi armeabi-v7a。
- **TCL 私有签名**（subject/issuer CN=TCL, OU=TCL, O=TCL, L=Shenzhen...）。
- WebView provider reflection = **null**；Google WebView / Chrome / 各浏览器均缺失。
- Android 5.1 无动态切换 WebView 机制，**无法覆盖/升级系统 WebView**。**[已验证死路]**
- 因此 browser 下载器里的系统 WebView 仅作轻量引导，现代能力全部依赖 engine(GeckoView)。

### 3.3 安装器 / 安全相关包（探针实测）
- 未知来源 `INSTALL_NON_MARKET_APPS = ENABLED(1)`。
- Root `su`：**unavailable**（exec [su,-c,id] 失败）。
- APK 的 VIEW/INSTALL handler：`com.android.packageinstaller/.PackageInstallerActivity`（exported=true）。
- 持有 `INSTALL_PACKAGES=true` 的包：
  - `com.tcl.packageinstaller.service`（TCL 安装服务，v5.01.50000）
  - `com.android.packageinstaller`
  - `tv.huan.tvhelper`（**欢视助手** v6.0.2g_1）
  - `com.tcl.tvweishi`（电视卫士）、`com.tcl.ui_mediaCenter`（媒体中心）
- TCL 自定义 action `com.tcl.packageinstaller.service.InstallerService` 等无外部 handler。
- 无 `android.intent.action.APPSTORE_INSTALL_APK` handler。

---

## 4. 输入设备与输入模型（重点）

### 4.1 可用输入
- **红外/蓝牙遥控器**：方向键、OK/确认、返回、菜单（部分带数字键、T9 字母）。
- **USB 键盘**（用户实物为带背光全键盘）。
- **实体鼠标**（可选；onGenericMotion 检测到 SOURCE_MOUSE 即隐藏虚拟鼠标）。

### 4.2 关键教训：不能用设备类型“自动”区分遥控与键盘 **[已验证踩坑]**
- v1.6 曾用 `InputDevice.getKeyboardType() == KEYBOARD_TYPE_ALPHABETIC` 判定 USB 键盘，
  但 **TCL 遥控器在本机也可能被识别为 ALPHABETIC**，导致遥控方向键被错误交给页面、
  **虚拟鼠标不出现、浏览器失控**。
- `KeyEvent.getSource()`（SOURCE_KEYBOARD / SOURCE_DPAD）在不同固件上同样不稳定。

### 4.3 现行稳健方案：自适应“学习式”判定（v1.7）
- 维护已证实为真键盘的 deviceId 集合 `keyboardDevices`。
- 当某设备发出 **字母键 KEYCODE_A..Z** 或 **Ctrl/Alt 组合**（遥控器不可能产生）时，
  才把该 deviceId 注册为键盘（且 deviceId 必须 ≥ 0）。
- 分流结果：
  - **未注册设备（=遥控器）**：方向键移动虚拟鼠标、OK/Enter 点击（生命线，永远可用）。
  - **已注册 USB 键盘**：方向键/PageUp·Dn/Home·End/Tab/空格/字母/回车全部交给聚焦的
    GeckoView 原生处理（桌面式滚动、焦点、文字输入），不移动虚拟鼠标。
  - 键盘在“被学习之前”，方向键也驱动鼠标（安全降级，绝不失控）。
- 焦点管理：popup 打开时 `popupView.requestFocus()`；popup 关闭后 `geckoView.requestFocus()`。

### 4.4 虚拟鼠标精准化（v1.6 起）
- 速度常量（VirtualMouse.java）：`BASE_SPEED=155`、`MAX_SPEED=560`、`ACCEL=470`
  （早期 190/820/950 过冲严重）。
- 每次**全新一次移动**（所有方向松开后再按）或**方向立即反转**，加速都从基础速度重新起步，
  便于微调命中小目标；对角线位移 ×0.7071 归一。
- 指针事件经 `root.dispatchTouchEvent` 注入（source=TOUCHSCREEN）；
  popup 显示时 target=popupFrame，关闭后 target=root。
- 边缘滚动：贴边持续方向键触发 PageUp/Down（EDGE=6，间隔 160ms）。

---

## 5. 视频 / 媒体管线（重中之重）

### 5.1 硬件解码能力（about:support 全部 available）
- `HARDWARE_VIDEO_DECODING / ENCODING` available；
  `H264 / HEVC / VP8 / VP9 / AV1` 的 HW_DECODE 均 available；VP8/VP9 HW_ENCODE available。
- OMX 组件：`OMX.amlogic.hevc.decoder.awesome`(H.265)、`OMX.amlogic.avc.decoder.awesome`(H.264)、
  `OMX.amlogic.mpeg4.decoder.awesome` 等。

### 5.2 现存问题
- **视频“有声无画面”，实为画面在播但亮度极低、肉眼难辨**（B站移动端、抖音、云游戏均现）。
  用户明确：不能只按“单一结论”处理，需避免所有可能（颜色范围/外部纹理采样/合成路径等）。
- **左上角白条**：是**系统级**视频不兼容标志，任何 App/网页在视频不兼容时都可能出现，
  视频能正常显示即消失。**不要用遮罩去盖它**，根因解决后自然消失。
- 抖音极卡顿，可能卡死（声音也卡）甚至死机；等待后出现英文提示即 onCrash/onKill。
- B站桌面端点视频 → 出现“弹窗窗口 - 返回键/点击关闭”横条后闪退；
  B站移动端可进详情页但播放发暗。

### 5.3 架构根因（前序研究结论）
- GeckoView 整页（含视频）由 **WebRender 经 GLES 合成**：
  视频帧 `MediaCodec → SurfaceTexture → external OES texture → WebRender GLES 采样合成`。
- webcompat issue 223207：H.264 在 Android **无软解**、总走 MediaCodec/SurfaceTexture；
  VP9/AV1 关硬解后走 **ffvpx 软解，颜色即正确** → 问题定位在 SurfaceTexture/OES 采样路径。
- bugzilla 1933055：Chrome 用 SurfaceControl/HWC 不受影响，Firefox 总用 GLES 合成受驱动 bug 影响。
- **GeckoView SurfaceView 后端在本机不渲染**（v1.4 实证，深色 cover 还会一直遮挡）→ 已回退 TextureView。

### 5.4 候选解决路径（待验证，勿盲目提交）
- 对支持 VP9/AV1 的站点（如 B站），尝试经 ffvpx 软解获得正确颜色（CPU 代价，需性能评估）；
  抖音主 H.264/HEVC 不适用。
- 研究 Mali r7p0 外部 OES 纹理颜色范围（limited/full range）与 WebRender 采样的具体修正。
- 借鉴云视听的 OES/颜色管理（见 5.5）。

### 5.5 云视听小电视（对标，包名 com.xiaodianshi.tv.yst v1.8.8）
- 流畅、占用 <30MB，自带“修复视频加载”机制；核心是 B站开源 **IjkPlayer（FFmpeg+SDL）**
  直接渲染原生 Surface，并深度适配 Amlogic。
- 关键参数（IjkMediaPlayAdapter）：mediacodec=1、k_android_variable_codec=1、
  packet-buffering=0、max-buffer-size=0、min-frames=2、disable_preroll=1、framedrop=5、
  enable_duration_calc=1。
- **IjkPlayer 无法直接接管网页 `<video>`**（Gecko 管线无外部播放器接口），对云游戏也无关
  （云游戏走 WebRTC/WebGL）；可复用其 Amlogic 解码参数与 BiliOESRetrieval 等 OES/颜色管理做法。
- 本地副本：`research/ibili/iBiliTV.apk`、反编译 `research/ibili/src`（仓库外，不入 git）。

---

## 6. 网络

- 电视直连 GitHub **TCP 443 超时**；引擎 APK 必须走镜像。
- **可用镜像**：`gh-proxy.com`、`cors.isteed.cc`（发布后均需核对 content-length）。
- 已验证不可用：ghproxy.com、mirror.ghproxy.com、ghp.ci、github.moeyy.xyz、
  gh.api.99988866.xyz、gh.llkk.cc、download.nust.na。
- browser 内置 3 个 URL（镜像×2 + 直连兜底），顺序尝试。

---

## 7. 安装机制与打包陷阱（攻坚记录）

### 7.1 标准安装为何不弹界面
- 标准 `ACTION_VIEW/INSTALL_PACKAGE` intent、`PackageInstaller` Session、root `pm install`
  在真机均不弹安装界面（无 root）。
- **欢视助手**靠反射隐藏 API `PackageManager.installPackage` + 自有 `IPackageInstallObserver`，
  其签名（欢网）在 TCL 白名单；复制该反射不可行（我方签名不在白名单）。
- 结论：最终由**用户在欢视助手内置安装器里手动安装** engine.apk。

### 7.2 APK 打包陷阱（“是包的问题”——用户判断正确）
- engine APK 曾有 14 个核心条目名被错误加 `./` 前缀
  （classes.dex、12 个 lib、omni.ja）。
- Android 5.1 按**精确路径**匹配，导致找不到 dex / ABI 库，必然安装失败
  （手机端也提示安装包异常）。
- 修复：Python 逐字节重写去除 `./` → zipalign → engine.keystore 重签，真机安装成功。

---

## 8. 架构与模块

| 模块 | 包名 | 作用 | 产物 |
| --- | --- | --- | --- |
| browser | `com.tclbrowser.tv` | 系统 WebView 轻量下载/引导器（约 58KB），突破欢视 10MB 限制 | browser/bin/browser.apk |
| engine | `com.tclbrowser.gecko` | GeckoView 144 完整浏览器（约 84MB），真正使用的浏览器 | engine/bin/engine.apk |
| devinfo | `com.tclbrowser.devinfo` | 设备探针（构建信息、安装器审计等） | devinfo/bin/devinfo.apk |

- engine 装好后，电视端可删除 browser（一次性下载器）；但仓库保留 browser 作为精简分发工具。
- engine 关键信息：versionCode 1 / versionName 144.0，minSdk 21 / targetSdk 22，largeHeap，
  Gecko 多进程（含 88 service）；12 个 armeabi-v7a .so（libxul 约 113MB 等）；assets/omni.ja 约 13.66MB。

---

## 9. 构建与发布

### 9.1 工具链
- JDK17 `/usr/lib/jvm/java-17-openjdk-amd64`（编译 engine）；
  JDK11 `/usr/lib/jvm/java-11-openjdk-amd64`（smali/baksmali、browser、jadx）。
- Android SDK `/home/user/android-sdk`，build-tools 35.0.0（aapt/d8/zipalign/apksigner），
  platforms android-37.0（裁剪桩）。**aapt 必须用绝对路径传 APK**。
- GeckoView AAR：144.0.20251027123126（最后支持 Android 5.1 的大版本线）。
- smali/baksmali 2.5.2（运行需把 research/tools 下所有 jar 加入 classpath；主类
  `org.jf.baksmali.Main` / `org.jf.smali.Main`，assemble `-a 22`）。

### 9.2 engine 自举构建（engine/rebuild.sh）
1. provision（JDK / GeckoView AAR）；
2. `aapt dump` 当前 OUT_APK（绝对路径）→ `gen_r_from_apk.py` 从
   “spec resource 0x… com.tclbrowser.gecko:type/name”行解析生成 **ID 正确的 R.java**
   （解决合并 geckoview 资源导致的 ID 错位/电话图标问题；跳过 styleable）；
3. JDK17 javac（classpath geckoview.jar:android.jar）→ d8 应用类；
4. baksmali 旧 APK 删除 `com/tclbrowser`，baksmali 新 dex，合并 → smali 汇编；
5. Python 重打包替换 classes.dex、弃 META-INF → zipalign -p 4 →
   apksigner（engine.keystore，storepass/keypass=android，alias ge）→ verify。
- dex 合并保留 org.mozilla、androidx、kotlinx.coroutines、snakeyaml、重定位 ExoPlayer、
  org.webrtc 等传递依赖（合并后 dex 约 8.3MB）。

### 9.3 发布后必做
- 更新 browser 源码 `GECKO_APK_URLS` 到新版本并重 build browser.apk；
- Release 上传 engine.apk 与 browser.apk；
- 核对 gh-proxy / cors.isteed.cc 的 content-length 与本地一致。

### 9.4 版本历史
| 版本 | 状态 / 要点 |
| --- | --- |
| v1.0–v1.2 | 早期打通 |
| v1.3 | TextureView 能打开；图标错位、视频发暗 |
| v1.4 | SurfaceView 真机打不开（**损坏，勿用**） |
| v1.5 | 回退 TextureView + 图标修复 + about:support；仍闪退/发暗 |
| v1.6 | 崩溃自动恢复、键盘/遥控分流（键盘判定不可靠导致失控）、鼠标精准化 |
| v1.7 | 学习式键盘判定（恢复遥控可控）、设备信息归档 |

---

## 10. 已验证死路（勿重复）
- GitHub 直连电视超时（仅 gh-proxy.com / cors.isteed.cc 可用，见 §6）。
- 无法覆盖系统 WebView（TCL 私钥签名、Android 5.1 无动态切换、provider=null）。
- 标准 intent / PackageInstaller Session / 无 root pm install 均不弹界面。
- 复制欢视助手反射 installPackage 不可行（签名不在白名单）。
- GeckoView SurfaceView 后端本机不渲染（v1.4 实证）。
- H.264 在 Android GeckoView 无软解，总走 MediaCodec。
- 云视听 IjkPlayer 无法迁移到网页视频或云游戏。
- 用 keyboardType/getSource 自动区分遥控与键盘不可靠（v1.6 实证）。

---

## 11. 云游戏目标（第二优先级）

- 目标站点：
  - 米哈游：云原神 `https://ys.mihoyo.com/cloud/`、云·崩坏：星穹铁道（云崩铁）。
  - 库洛：云·鸣潮。
- 现状：云原神登录页、排队页（预计等待、畅玩卡、免费时长）真机显示正常；**进游戏后的操控未测**。
- 通用技术原理（待逐站核实）：低延迟云游戏通常以 **WebRTC** 传视频/音频流，
  经 **WebRTC data channel / WebSocket** 回传手柄/键鼠输入，UI 层用 WebGL/WebTransport 等。
- 下一步：抓取并确认各站实际使用的协议（WebRTC vs WebCodecs+WebTransport）、
  输入映射与编码格式，再在 engine 中针对性适配（WebGL2 可用、WebRTC 原生库已在 dex）。

---

## 12. 待办 / 下一步
1. v1.7 真机确认：遥控方向键必出虚拟鼠标、不再失控；崩溃自动恢复是否生效。
2. 菜单 → 故障诊断信息，回报“崩溃 X 次 / 内存终止 Y 次”，判定闪退主因（驱动 vs OOM）。
3. 视频发暗/白条：按 §5.4 路径实验（VP9 ffvpx、OES 颜色范围），不做遮罩。
4. 键盘桌面化体验复核（方向滚动、Tab 焦点、文字输入）。
5. 云游戏：进游戏操控测试 + 各站协议研究与适配。

---

## 13. 关键 ID / 路径速查
- 仓库：https://github.com/Columbine-Hyposelenia/tcl-tv-browser
- 本地仓库：`/home/user/Doubao/chats/38445209063873794/tcl-tv-browser`
- engine drawable ID（resources.arsc，已核对）：
  ic_back=0x7f020000；ic_close=0x7f020007；ic_desktop=0x7f020008；
  ic_forward=0x7f020009；ic_launcher=0x7f02000b；**ic_menu=0x7f02000c**；ic_reload=0x7f02000d。
  （ic_call_*=0x7f020001..006 为 GeckoView 自带，R.java 修正后不再误用。）
- 欢视助手：包名 `tv.huan.tvhelper`；官方直链
  `https://dl-appstore.huan.tv/project/ott/tvhelper/TVhelper_general_1.0.0_20231023174019_release.apk`；上传限制 10MB。
- 云视听下载（须完整 query + TV UA + Referer，否则 403）：
  `https://dl.hdslb.com/mobile/latest/android_tv_yst/iBiliTV-master.apk?t=20261002&spm_id_from=333.47.b_646f776e6c6f61642d6c696e6c696e6b.5`
- configFilePath 机制（备用）：`GeckoRuntimeSettings.Builder().configFilePath(path)`
  可让 GeckoView 无条件读取 YAML（env/args/prefs，release 也生效）；assets 文件需先复制到 filesDir。
