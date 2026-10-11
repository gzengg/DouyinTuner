package com.mod.douyintuner;

import android.app.Dialog;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 未成年人模式弹窗：这类弹窗有十来个类，继承链还不一样 —— 有的 extends BottomSheetDialog
 * 自己没声明 show()（例如 NewUpdatedTeenGuideDialogV2），逐个类挂 show 挂不全。
 *
 * 所以统一挂 android.app.Dialog.show()：所有 Dialog 最终都会走到这里（Material 的
 * BottomSheetDialog.show 也会 super.show()），再用实例类名前缀只拦抖音青少年模式包。
 */
final class TeenHooks {

    private static final String TEEN_PKG = "com.ss.android.ugc.aweme.compliance.protection.teenmode.";
    private static final String DIALOG_FRAGMENT = "androidx.fragment.app.DialogFragment";

    private TeenHooks() {
    }

    static void block(ClassLoader cl) {
        H.hook(Dialog.class, "show", filter("Dialog"));
        Class<?> fragment = H.cls(cl, DIALOG_FRAGMENT);
        XC_MethodHook onShow = filter("DialogFragment");
        H.hookAll(fragment, "show", onShow);
        H.hookAll(fragment, "showNow", onShow);
        Log.i("未成年模式：已挂 Dialog/DialogFragment 显示过滤 -> " + TEEN_PKG);
    }

    private static XC_MethodHook filter(final String kind) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isTeen(param.thisObject)) {
                    param.setResult(null); // void 方法，吞掉即等于不显示
                    Log.i("拦下未成年模式弹窗(" + kind + "): " + param.thisObject.getClass().getName());
                }
            }
        };
    }

    private static boolean isTeen(Object o) {
        return o != null && o.getClass().getName().startsWith(TEEN_PKG);
    }
}
