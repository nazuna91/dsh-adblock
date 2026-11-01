# DSH AdBlock —— 聚合广告跳过模块

针对 `com.clggjv.xcjfmd.ffo` 的 LSPosed 模块。该应用是 **Flutter 壳 + 五家聚合广告**，模块从 SDK 层、Flutter 通道层、Activity 层同时下手。

## 目标应用分析

```text
包名        com.clggjv.xcjfmd.ffo
体积        100.5 MB
架构        Flutter（libflutter.so / libapp.so / libdartjni.so）+ libmpv
加固        assets/libjgccxf*.so + libmaparmor.so（自定义壳，dex 加密）
广告 SDK    穿山甲 Pangle   com.bytedance.sdk.openadsdk
            优量汇 GDT     com.qq.e.ads / com.qq.e.comm
            快手 KS        com.kwad.sdk.api
            百度 Mobads    com.baidu.mobads.sdk.api（另有 assets/bdxadsdk.jar）
            倍孜 BeiZi     com.beizi.ad
第三方检测   com.ss.android.ugc.aweme（抖音包名白名单，用于唤起）
```

`classes.dex` 里搜不到广告类名——因为 dex 被壳加密，运行时才解密。所以模块**不依赖静态类名分析**，直接按 SDK 的公开 API 签名 Hook，运行时找不到就跳过。

## 三层 Hook

```text
① SDK 层     show() / load() 短路：广告不播、加载直接返回
             覆盖穿山甲开屏/激励/全屏/插屏、GDT 开屏/激励/插屏/原生、
             快手四类、百度四类、倍孜三类

② Flutter 层  io.flutter.plugin.common.MethodChannel.invokeMethod
             channel/method 命中 reward/splash/ad 等关键词 → 直接返回 null

③ Activity 层 Activity.onCreate 后检查类名，命中 splashad/rewardvideo/
             adactivity 等 → finish()，兜住 SDK 自己拉起的广告页
```

## 构建

需要 JDK 17 + Android SDK 34。三种方式：

**Android Studio**
```text
打开本目录 → Sync → Build → Build APK(s)
产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

**命令行**
```bash
./gradlew assembleRelease
```

**GitHub Actions**（本仓库已带 workflow）
```text
push 到 main → Actions → Build APK → 下载 artifact
```

产物需要签名才能装：`apksigner sign --ks your.jks app-release-unsigned.apk`，或直接用 debug 包。

## 安装

```text
1. 设备已 root + 装 LSPosed
2. 安装 APK（debug 或自己签名的 release）
3. LSPosed → 模块 → 启用「DSH AdBlock」
4. 作用域勾选 com.clggjv.xcjfmd.ffo
5. 强制停止目标应用 → 重新打开
```

## 验证

```bash
adb logcat -s LSPosed-Bridge | grep DSH-AdBlock
```

正常会看到：

```text
[DSH-AdBlock] loaded in com.clggjv.xcjfmd.ffo
[DSH-AdBlock] pangle TTAppOpenAd.show -> blocked
[DSH-AdBlock] flutter blocked: xxx/ad -> showSplash
[DSH-AdBlock] finish ad activity: com.bytedance.sdk.openadsdk.stub.activity.Stub_Activity
```

## 已知局限

```text
· 壳会检测 Xposed（libmaparmor 有反调试），被检测到可能闪退 → 先用 LSPosed 的"隐藏"功能
· Flutter 侧若用 Pigeon/FFI 直连 SDK，MethodChannel 那层拦不到，只靠 ①②
· "看完广告给奖励"的逻辑如果卡在 SDK 回调上，短路后可能拿不到奖励
  → 需要的话再加一层：Hook listener 的 onRewardVerify 主动回调成功
· 应用更新换 SDK 版本后，类名/方法名可能变，日志里看 skip 行确认
```

## 目录

```text
app/src/main/java/com/dsh/adblock/XposedInit.kt   全部 hook 逻辑
app/src/main/assets/xposed_init                   模块入口声明
app/src/main/AndroidManifest.xml                  xposedmodule 元数据 + 作用域
app/build.gradle.kts                              compileOnly Xposed API 82
.github/workflows/build.yml                       CI 出包
```
