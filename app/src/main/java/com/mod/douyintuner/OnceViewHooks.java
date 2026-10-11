package com.mod.douyintuner;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 闪图（私信「一次性查看 / 阅后即焚」）持久化：让它永远不进入「已看过」状态，可以反复打开。
 *
 * 闪图没有独立的文件生命周期 —— 「看过了」是消息级的标记，写在 Message.localExt 的
 * a:once_view_local_done 与 ext 的 s:once_view_done 上，媒体文件只是普通 Fresco 缓存。
 * 写入与上报都集中在同一个类里（40.7.0 是 X.1M77），所以一个类两个方法就够：
 *
 *   LJII(Message, boolean)  写 localExt + 落库
 *   LJI(Message)            标本地已看 + POST /aweme/v1/im/read_once/mark
 *
 * 只改本地不改上报的话，服务端会把 s:once_view_done 回写下来，「看起来没生效」，
 * 所以两个都必须拦。判定侧（X.1M42 的「我是否看过」「是否已过期」）一起钳成 false 兜底：
 * 装模块之前已经标过已看的老消息，靠这两条才能重新打开。
 */
final class OnceViewHooks {

    /** 「已查看」的唯一写入/上报点。 */
    private static final String FUNNEL = "X.1M77";
    /** 判定：「我是否看过」（返回 Boolean）、「是否已看过/已过期」（返回 boolean）。 */
    private static final String STATE = "X.1M42";
    /** 文字版闪图（TextOnceView）的时间戳写入点。 */
    private static final String TEXT = "X.0x3a";

    private OnceViewHooks() {
    }

    static void keep(ClassLoader cl) {
        Class<?> funnel = H.cls(cl, FUNNEL);
        H.hookAll(funnel, "LJII", skip("不写 a:once_view_local_done"));
        H.hookAll(funnel, "LJI", skip("不标本地已看、不上报 read_once/mark"));

        Class<?> state = H.cls(cl, STATE);
        H.hookAll(state, "LIZIZ", always(false, "恒判未看过"));
        H.hookAll(state, "LIZJ", always(false, "恒判未过期"));

        H.hookAll(H.cls(cl, TEXT), "LIZLLL", skip("不写 a:once_view_done_time"));

        Log.i("闪图持久化：已挂钩 " + FUNNEL + "#LJII/LJI、" + STATE + "#LIZIZ/LIZJ、"
                + TEXT + "#LIZLLL（图片不加密可反复看；视频是 DRM，只保证可反复打开）");
    }

    /** 整个方法不执行。 */
    private static XC_MethodHook skip(final String why) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(null);
                logOnce(why);
            }
        };
    }

    /** 布尔返回值恒为 value（对 boolean / Boolean 都成立）。 */
    private static XC_MethodHook always(final boolean value, final String why) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.getResult() instanceof Boolean && ((Boolean) param.getResult()) == value) {
                    return;
                }
                param.setResult(value);
                logOnce(why);
            }
        };
    }

    private static void logOnce(String why) {
        if (!logged) {
            logged = true;
            Log.k("闪图持久化已生效: " + why);
        }
    }

    private static volatile boolean logged;
}
