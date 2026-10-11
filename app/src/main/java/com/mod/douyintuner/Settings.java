package com.mod.douyintuner;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 目标进程侧读取模块开关。
 * 用 XSharedPreferences（LSPosed 下不需要 prefs 全局可读），进程内只读一次；
 * 面板里也提示过：改开关要强制停止抖音再打开才生效。
 */
final class Settings {

    private static XSharedPreferences sp;
    private static boolean broken;

    private Settings() {
    }

    static synchronized boolean on(String key) {
        if (sp == null) {
            if (broken) {
                return true;
            }
            try {
                sp = new XSharedPreferences(Config.MODULE_PKG, Config.PREFS);
            } catch (Throwable t) {
                broken = true;
                Log.k("读不到模块开关，按默认全开处理: " + t);
                return true;
            }
        }
        try {
            return sp.getBoolean(key, true);
        } catch (Throwable t) {
            broken = true;
            Log.k("读开关失败，按默认全开处理: " + t);
            return true;
        }
    }
}
