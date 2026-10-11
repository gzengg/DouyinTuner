package com.mod.douyintuner;

import android.app.AndroidAppHelper;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 模块入口（见 assets/xposed_init）：只处理抖音，逐项按面板开关挂钩。 */
public class XposedHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Override
    public void initZygote(StartupParam startupParam) {
        // 无需 zygote 期改动
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!Config.TARGET_PKG.equals(lp.packageName)) {
            return;
        }
        final ClassLoader cl = lp.classLoader;
        Log.k("==== 抖音精简助手加载 " + lp.packageName + " v" + versionName()
                + "（针对 " + Config.TARGET_VERSION + " 编写，缺项会自动跳过）");

        final StringBuilder ok = new StringBuilder();
        final int[] count = {0};
        step(ok, count, Config.K_MEM_BG, "后台保活清理", () -> MemHooks.keepAlive(cl));
        step(ok, count, Config.K_MEM_CACHE, "预加载与缓存上限", () -> MemHooks.preload(cl));
        step(ok, count, Config.K_UI_TAB_MALL, "底部 tab 白名单", () -> UiHooks.tabBottom(cl));
        step(ok, count, Config.K_UI_CHANNEL_GROUP, "顶部频道白名单", () -> UiHooks.channelTop(cl));
        step(ok, count, Config.K_UI_SIDEBAR, "侧边栏营销入口", () -> UiHooks.sidebar(cl));
        step(ok, count, Config.K_UI_POPUP, "活动弹窗与浮层", () -> UiHooks.popup(cl));
        step(ok, count, Config.K_TEEN, "未成年人模式弹窗", () -> TeenHooks.block(cl));
        step(ok, count, Config.K_AD_SPLASH, "去开屏广告", () -> AdHooks.splash(cl));
        step(ok, count, Config.K_AD_FEED, "过滤信息流广告", () -> FeedHooks.feedItems(cl));
        step(ok, count, Config.K_UPDATE, "禁止升级与自更新下载", () -> UpdateHooks.block(cl));
        step(ok, count, Config.K_DL_NOWM, "无水印直链复制", () -> DlHooks.nowm(cl));
        step(ok, count, Config.K_ONCE_VIEW, "闪图可反复查看", () -> OnceViewHooks.keep(cl));
        step(ok, count, Config.K_RECALL, "消息防撤回", () -> RecallHooks.keep(cl));
        step(ok, count, Config.K_COLD_BOOT, "冷启动加速", () -> ColdBootHooks.boost(cl));
        step(ok, count, Config.K_CFG_OVERRIDE, "配置覆盖", () -> ConfigHooks.abOverride(cl));
        step(ok, count, Config.K_TELEMETRY, "停埋点上报", () -> TelemetryHooks.silence(cl));
        step(ok, count, Config.K_HOTFIX, "禁用热修", () -> HotfixHooks.disable(cl));
        step(ok, count, Config.K_REDBADGE, "红点静默", () -> UiHooks.redbadge(cl));

        Log.k("==== 已挂钩 " + count + " 项：" + ok + "（有跳过/异常行说明那几项没生效）");
    }

    /** 单个功能：开关关了就跳过，单点异常不影响其余功能。 */
    private static void step(StringBuilder done, int[] count, String key, String label, Runnable body) {
        if (!Settings.on(key)) {
            Log.i("开关关闭，跳过: " + label);
            return;
        }
        try {
            body.run();
            if (done.length() > 0) {
                done.append('、');
            }
            done.append(label);
            count[0]++;
        } catch (Throwable t) {
            Log.k(label + " 挂钩异常: " + t);
        }
    }

    private static String versionName() {
        try {
            Context app = AndroidAppHelper.currentApplication();
            if (app == null) {
                return "?";
            }
            return app.getPackageManager().getPackageInfo(Config.TARGET_PKG, 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }
}
