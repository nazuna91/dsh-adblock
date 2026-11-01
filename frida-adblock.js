/*
 * frida-adblock.js —— 目标 com.clggjv.xcjfmd.ffo 的广告拦截脚本
 *
 * 用法：
 *   frida -U -f com.clggjv.xcjfmd.ffo -l frida-adblock.js --no-pause
 *   或附加： frida -U com.clggjv.xcjfmd.ffo -l frida-adblock.js
 *
 * 和 LSPosed 模块同一套逻辑，用来在写模块前快速验证 hook 点。
 */

const TAG = "[adblock]";
const log = (m) => console.log(`${TAG} ${m}`);

const SIGS = [
  ["com.bytedance.sdk.openadsdk.TTAppOpenAd", ["show"]],
  ["com.bytedance.sdk.openadsdk.TTRewardExpressAd", ["show"]],
  ["com.bytedance.sdk.openadsdk.TTFullScreenVideoAd", ["show"]],
  ["com.bytedance.sdk.openadsdk.TTInterstitialAd", ["show"]],
  ["com.qq.e.ads.splash.SplashAD", ["showAd"]],
  ["com.qq.e.ads.rewardvideo.RewardVideoAD", ["show"]],
  ["com.qq.e.ads.interstitial2.UnifiedInterstitialAD", ["show", "showFullScreenAD"]],
  ["com.kwad.sdk.api.KsSplashScreenAd", ["show"]],
  ["com.kwad.sdk.api.KsRewardVideoAd", ["showRewardVideoAd"]],
  ["com.kwad.sdk.api.KsFullScreenVideoAd", ["showFullScreenVideoAd"]],
  ["com.kwad.sdk.api.KsInterstitialAd", ["showInterstitialAd"]],
  ["com.baidu.mobads.sdk.api.SplashAd", ["show"]],
  ["com.baidu.mobads.sdk.api.RewardedVideoAd", ["show"]],
  ["com.beizi.ad.RewardVideoAd", ["show"]],
];

const AD_KEY = ["reward", "splash", "interstitial", "nativead", "feedad", "/ad/", "adsdk"];

function hookShow(className, methods) {
  try {
    const cls = Java.use(className);
    methods.forEach((m) => {
      const overloads = cls[m].overloads;
      overloads.forEach((ov) => {
        ov.implementation = function () {
          log(`${className}.${m} -> blocked`);
          return null;
        };
      });
    });
  } catch (e) {
    log(`skip ${className}: ${e.message}`);
  }
}

function hookActivityFinish() {
  try {
    const Activity = Java.use("android.app.Activity");
    const keywords = [
      "splashad", "rewardvideo", "rewardactivity", "interstitialactivity",
      "adactivity", "adwebview", "ksreward", "ksfullscreen", "mobreward",
      "beizi", "appopen",
    ];
    Activity.onCreate.overload("android.os.Bundle").implementation = function (b) {
      this.onCreate(b);
      try {
        const name = this.getClass().getName().toLowerCase();
        if (keywords.some((k) => name.includes(k))) {
          log(`finish ad activity: ${this.getClass().getName()}`);
          this.finish();
        }
      } catch (_) { /* ignore */ }
    };
  } catch (e) {
    log(`activity hook failed: ${e.message}`);
  }
}

function hookFlutter() {
  try {
    const MC = Java.use("io.flutter.plugin.common.MethodChannel");
    MC.invokeMethod.overload("java.lang.String", "java.lang.Object", "java.lang.Object").implementation =
      function (channel, method, arg) {
        try {
          const key = `${channel}/${method}`.toLowerCase();
          if (AD_KEY.some((k) => key.includes(k))) {
            log(`flutter blocked: ${channel} -> ${method}`);
            return null;
          }
        } catch (_) { /* ignore */ }
        return this.invokeMethod(channel, method, arg);
      };
  } catch (e) {
    log(`flutter hook failed: ${e.message}`);
  }
}

function dumpLoadedAdClasses() {
  const names = Java.enumerateLoadedClassesSync().filter((n) =>
    /openadsdk|qq\.e|kwad|baidu\.mobads|beizi/.test(n)
  );
  log(`loaded ad classes: ${names.length}`);
  names.slice(0, 40).forEach((n) => log(`  ${n}`));
}

Java.perform(() => {
  log("attached");
  SIGS.forEach(([cls, methods]) => hookShow(cls, methods));
  hookActivityFinish();
  hookFlutter();
  setTimeout(dumpLoadedAdClasses, 5000);
});
