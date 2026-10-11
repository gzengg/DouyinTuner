package com.mod.douyintuner;

/**
 * 禁用热修：抖音的 Reparo 引擎通过 WandTrick.applyPatch 在启动后替换方法实现。
 * 拦掉它，你 hook 到的方法就不会在运行中途被平台热修覆盖掉。
 *
 * applyPatch 在 WandTrick 上是 abstract（挂它会报 "Cannot hook abstract methods"），
 * 真正干活的是 getInstanceWithoutInit() 里 new 出来的两个匿名实现 X.0AvB / X.0Av6。
 */
final class HotfixHooks {

    private static final String WAND_TRICK = "com.bytedance.reparo.core.WandTrick";
    private static final String[] WAND_IMPLS = {"X.0AvB", "X.0Av6"};

    private HotfixHooks() {
    }

    static void disable(ClassLoader cl) {
        Class<?> wand = H.cls(cl, WAND_TRICK);
        H.stop(wand, "loadSelfFix");
        H.stop(wand, "deoptimizeCallGraph");
        for (String impl : WAND_IMPLS) {
            H.stop(H.cls(cl, impl), "applyPatch");
        }
        Log.i("热修：Reparo applyPatch 已拦截 " + java.util.Arrays.toString(WAND_IMPLS));
    }
}
