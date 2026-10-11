package com.mod.douyintuner;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;

/**
 * 内存 / 运存相关：
 * 1) 后台驻留：保留推送，只把其它保活服务改成 START_NOT_STICKY；
 * 2) 前台缓存：收窄视频预加载池（不禁用预加载，保起播速度）。
 */
final class MemHooks {

    private static final String LANCET = "com.ss.android.ugc.aweme.lancet.KeepAliveLancet";
    private static final String KEEP_ALIVE = "com.bytedance.mira.am.KeepAlive";
    private static final String PRELOAD_CONFIG =
            "com.ss.android.ugc.aweme.video.preload.model.PreloadStrategyConfig";

    /** 预加载池上限：抖音默认会按服务端策略同时预热很多条视频。 */
    private static final int MAX_PRELOAD_TASKS = 3;

    private MemHooks() {
    }

    /** START_NOT_STICKY：服务被杀后系统不再自动拉起。 */
    static void keepAlive(ClassLoader cl) {
        // 抖音自己的保活闸门，返回值就是 onStartCommand 的返回值。
        Class<?> lancet = H.cls(cl, LANCET);
        H.hookAll(lancet, "LIZ", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) {
                if (H.returnType(param.method) == int.class) {
                    return 2;
                }
                return null;
            }
        });

        // mira 1px 前台保活服务兜底。
        H.hookAll(H.cls(cl, KEEP_ALIVE), "onStartCommand", XC_MethodReplacement.returnConstant(2));
        H.hookAll(H.cls(cl, KEEP_ALIVE + "$InnerService"), "onStartCommand",
                XC_MethodReplacement.returnConstant(2));
        Log.i("后台保活：已改为 START_NOT_STICKY");
    }

    static void preload(ClassLoader cl) {
        Class<?> cfg = H.cls(cl, PRELOAD_CONFIG);
        H.hookCtors(cfg, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object tasks = H.field(param.thisObject, "tasks");
                if (!(tasks instanceof List)) {
                    return;
                }
                List<?> list = (List<?>) tasks;
                int size = list.size();
                if (size <= MAX_PRELOAD_TASKS) {
                    return;
                }
                H.setField(param.thisObject, "tasks",
                        new ArrayList<Object>(list.subList(0, MAX_PRELOAD_TASKS)));
                Log.i("预加载池收窄: " + size + " -> " + MAX_PRELOAD_TASKS);
            }
        });
        Log.i("前台缓存：已挂钩预加载策略");
    }
}
