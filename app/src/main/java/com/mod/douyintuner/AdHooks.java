package com.mod.douyintuner;

import android.app.Activity;
import android.os.Bundle;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;

/**
 * 开屏广告：拦展示入口 + 兜底关掉开屏广告页。
 *
 * 开屏链路的业务方法名几乎全被混淆（LIZ/LJII…），能按名字稳定挂的只有
 * SplashAdServiceImpl.LJJIIJZLJL(FragmentActivity, boolean, String, Aweme) 这个展示入口，
 * 以及 SplashAdActivity 的生命周期。返回 false 即“这次不展示开屏广告”。
 */
final class AdHooks {

    private static final String SPLASH_SVC =
            "com.bytedance.ies.ugc.aweme.commercialize.splash.service.SplashAdServiceImpl";
    private static final String SPLASH_ACT =
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity";

    private AdHooks() {
    }

    static void splash(ClassLoader cl) {
        Class<?> svc = H.cls(cl, SPLASH_SVC);
        H.hookAll(svc, "LJJIIJZLJL", XC_MethodReplacement.returnConstant(Boolean.FALSE));

        // 兜底：万一还有别的路径起了开屏广告页，进去就关。
        Class<?> act = H.cls(cl, SPLASH_ACT);
        H.hook(act, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Log.i("开屏广告页已启动，直接关闭");
                ((Activity) param.thisObject).finish();
            }
        });
        Log.i("开屏广告：已挂钩展示入口与兜底关闭");
    }
}
