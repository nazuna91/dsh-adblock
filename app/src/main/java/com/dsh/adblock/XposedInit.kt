package com.dsh.adblock

import android.app.Activity
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 入口：只对目标包生效。
 *
 * 目标包：com.clggjv.xcjfmd.ffo
 * 结构：Flutter 壳 + 五家聚合广告（穿山甲 / 优量汇 / 快手 / 百度 / 倍孜）
 * 策略：SDK 的 show/load 直接短路；Flutter MethodChannel 拦广告调用；广告 Activity 一进来就关。
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in TARGETS) return
        log("loaded in ${lpparam.packageName} (${lpparam.processName})")

        hookPangle(lpparam)
        hookGdt(lpparam)
        hookKs(lpparam)
        hookBaidu(lpparam)
        hookBeizi(lpparam)
        hookFlutter(lpparam)
        hookAdActivities(lpparam)
    }

    // ── 穿山甲 ──────────────────────────────────────────────────────
    private fun hookPangle(lp: XC_LoadPackage.LoadPackageParam) {
        for (cls in listOf(
            "com.bytedance.sdk.openadsdk.TTAppOpenAd",
            "com.bytedance.sdk.openadsdk.TTRewardExpressAd",
            "com.bytedance.sdk.openadsdk.TTFullScreenVideoAd",
            "com.bytedance.sdk.openadsdk.TTInterstitialAd",
        )) {
            safeHook(cls, lp) { clazz ->
                clazz.declaredMethods
                    .filter { it.name == "show" }
                    .forEach { m ->
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                log("pangle ${clazz.simpleName}.show -> blocked")
                                param.result = null
                            }
                        })
                    }
            }
        }
        safeHook("com.bytedance.sdk.openadsdk.TTNativeExpressAd", lp) { clazz ->
            clazz.declaredMethods
                .filter { it.name == "render" || it.name.startsWith("set") }
                .forEach { m ->
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (m.name == "render") param.result = null
                            }
                        })
                    } catch (_: Throwable) { /* ignore */ }
                }
        }
        // 加载器：全部短路，让应用走"没有广告"分支
        safeHook("com.bytedance.sdk.openadsdk.TTAdNative", lp) { clazz ->
            clazz.declaredMethods
                .filter { it.name.startsWith("load") }
                .forEach { m ->
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                log("pangle ${m.name} -> blocked")
                                param.result = null
                            }
                        })
                    } catch (_: Throwable) { /* ignore */ }
                }
        }
    }

    // ── 优量汇 ──────────────────────────────────────────────────────
    private fun hookGdt(lp: XC_LoadPackage.LoadPackageParam) {
        for (cls in listOf(
            "com.qq.e.ads.splash.SplashAD",
            "com.qq.e.ads.rewardvideo.RewardVideoAD",
            "com.qq.e.ads.interstitial2.UnifiedInterstitialAD",
            "com.qq.e.ads.nativ.NativeUnifiedAD",
        )) {
            safeHook(cls, lp) { clazz ->
                clazz.declaredMethods
                    .filter { it.name.startsWith("show") || it.name == "loadAd" || it.name == "loadData" }
                    .forEach { m ->
                        try {
                            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    log("gdt ${clazz.simpleName}.${m.name} -> blocked")
                                    param.result = null
                                }
                            })
                        } catch (_: Throwable) { /* ignore */ }
                    }
            }
        }
    }

    // ── 快手 ────────────────────────────────────────────────────────
    private fun hookKs(lp: XC_LoadPackage.LoadPackageParam) {
        for (cls in listOf(
            "com.kwad.sdk.api.KsSplashScreenAd",
            "com.kwad.sdk.api.KsRewardVideoAd",
            "com.kwad.sdk.api.KsFullScreenVideoAd",
            "com.kwad.sdk.api.KsInterstitialAd",
            "com.kwad.sdk.api.KsFeedAd",
        )) {
            safeHook(cls, lp) { clazz ->
                clazz.declaredMethods
                    .filter { it.name.startsWith("show") || it.name.startsWith("setAdInteractionListener") }
                    .forEach { m ->
                        try {
                            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (m.name.startsWith("show")) {
                                        log("ks ${clazz.simpleName}.${m.name} -> blocked")
                                        param.result = null
                                    }
                                }
                            })
                        } catch (_: Throwable) { /* ignore */ }
                    }
            }
        }
    }

    // ── 百度 ────────────────────────────────────────────────────────
    private fun hookBaidu(lp: XC_LoadPackage.LoadPackageParam) {
        for (cls in listOf(
            "com.baidu.mobads.sdk.api.SplashAd",
            "com.baidu.mobads.sdk.api.RewardedVideoAd",
            "com.baidu.mobads.sdk.api.InterstitialAd",
            "com.baidu.mobads.sdk.api.NativeAd",
        )) {
            safeHook(cls, lp) { clazz ->
                clazz.declaredMethods
                    .filter { it.name.startsWith("show") }
                    .forEach { m ->
                        try {
                            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    log("baidu ${clazz.simpleName}.${m.name} -> blocked")
                                    param.result = null
                                }
                            })
                        } catch (_: Throwable) { /* ignore */ }
                    }
            }
        }
    }

    // ── 倍孜 ────────────────────────────────────────────────────────
    private fun hookBeizi(lp: XC_LoadPackage.LoadPackageParam) {
        for (cls in listOf(
            "com.beizi.ad.RewardVideoAd",
            "com.beizi.ad.InterstitialAd",
            "com.beizi.ad.AdView",
        )) {
            safeHook(cls, lp) { clazz ->
                clazz.declaredMethods
                    .filter { it.name.startsWith("show") || it.name == "loadAd" }
                    .forEach { m ->
                        try {
                            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    log("beizi ${clazz.simpleName}.${m.name} -> blocked")
                                    param.result = null
                                }
                            })
                        } catch (_: Throwable) { /* ignore */ }
                    }
            }
        }
    }

    // ── Flutter MethodChannel ───────────────────────────────────────
    private fun hookFlutter(lp: XC_LoadPackage.LoadPackageParam) {
        safeHook("io.flutter.plugin.common.MethodChannel", lp) { clazz ->
            XposedHelpers.findAndHookMethod(
                clazz, "invokeMethod",
                String::class.java, Any::class.java, Any::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val channel = param.args[0] as? String ?: return
                        val method = param.args[1] as? String ?: return
                        val key = "$channel/$method".lowercase()
                        if (AD_KEYWORDS.any { key.contains(it) }) {
                            log("flutter blocked: $channel -> $method")
                            param.result = null
                        }
                    }
                })
        }
    }

    // ── 广告 Activity 兜底 ──────────────────────────────────────────
    private fun hookAdActivities(lp: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            Activity::class.java, "onCreate", android.os.Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val act = param.thisObject as? Activity ?: return
                    val name = act.javaClass.name.lowercase()
                    if (AD_ACTIVITIES.any { name.contains(it) }) {
                        log("finish ad activity: ${act.javaClass.name}")
                        try { act.finish() } catch (_: Throwable) { /* ignore */ }
                    }
                }
            })
    }

    private inline fun safeHook(
        className: String,
        lp: XC_LoadPackage.LoadPackageParam,
        block: (Class<*>) -> Unit,
    ) {
        try {
            block(XposedHelpers.findClass(className, lp.classLoader))
        } catch (t: Throwable) {
            log("skip $className (${t.javaClass.simpleName})")
        }
    }

    private fun log(msg: String) = XposedBridge.log("[DSH-AdBlock] $msg")

    companion object {
        val TARGETS = setOf("com.clggjv.xcjfmd.ffo")

        val AD_KEYWORDS = listOf(
            "reward", "splash", "interstitial", "nativead", "feedad", "banner",
            "/ad/", "adsdk", "admanager", "adconfig",
        )

        val AD_ACTIVITIES = listOf(
            "splashad", "rewardvideo", "rewardactivity", "interstitialactivity",
            "adactivity", "adwebview", "ksreward", "ksfullscreen", "mobreward",
            "beizi", "appopen", "ttdownloader",
        )
    }
}
