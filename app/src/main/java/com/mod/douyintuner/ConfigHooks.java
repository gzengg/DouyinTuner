package com.mod.douyintuner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

/**
 * ABMock 配置覆盖：抖音所有 AB/服务端配置读取都走 SettingsManager / ABManager 的这几个
 * 类型化 getter，从 key 上替换返回值就能直接关掉一大批行为，不必逐个 hook 业务代码。
 *
 * 只覆盖 overrides.txt 里列出的 key —— 白名单式，登录/风控相关的 key 不在表里就原样透传。
 * 文件在模块私有目录（面板里能看到路径），改完要重启抖音才生效。
 */
final class ConfigHooks {

    private static final String SETTINGS_MANAGER = "com.bytedance.ies.abmock.SettingsManager";
    private static final String AB_MANAGER = "com.bytedance.ies.abmock.ABManager";

    private static final String[] SETTINGS_METHODS = {
            "LIZ", "LIZIZ", "LIZJ", "LIZLLL", "LJ", "LJFF",
            "getValue", "getValueSafely", "getBooleanValue", "getIntValue",
            "getLongValue", "getDoubleValue", "getStringValue",
    };

    private static final String[] AB_METHODS = {
            "getValue", "getBooleanValue", "getIntValue", "getLongValue",
            "getDoubleValue", "getFloatValue", "getStringValue",
    };

    private ConfigHooks() {
    }

    static void abOverride(ClassLoader cl) {
        final Map<String, String> overrides = load();
        if (overrides.isEmpty()) {
            Log.i("配置覆盖：overrides.txt 为空或读不到，跳过（路径 " + overridePath() + "）");
            return;
        }
        final Set<String> logged = new HashSet<>();
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = mappedKey(param.args, overrides);
                if (key == null) {
                    return;
                }
                Object value = convert(H.returnType(param.method), overrides.get(key));
                if (value == null) {
                    return;
                }
                if (logged.add(key)) {
                    Log.i("配置覆盖 " + key + " = " + overrides.get(key) + "（原值 " + param.getResult() + "）");
                }
                param.setResult(value);
            }
        };
        Class<?> sm = H.cls(cl, SETTINGS_MANAGER);
        for (String m : SETTINGS_METHODS) {
            H.hookAll(sm, m, hook);
        }
        Class<?> am = H.cls(cl, AB_MANAGER);
        for (String m : AB_METHODS) {
            H.hookAll(am, m, hook);
        }
        Log.i("配置覆盖：已挂钩 ABMock getter，覆盖 " + overrides.size() + " 项");
    }

    static String overridePath() {
        File dir = Log.moduleDir();
        return dir == null ? Config.OVERRIDE_FILE : new File(dir, Config.OVERRIDE_FILE).getAbsolutePath();
    }

    /** 找出这次调用里用到的、我们表里有的 key；没有就整条调用不管。 */
    private static String mappedKey(Object[] args, Map<String, String> overrides) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String && overrides.containsKey(arg)) {
                return (String) arg;
            }
        }
        return null;
    }

    private static Object convert(Class<?> type, String text) {
        if (text == null) {
            return null;
        }
        try {
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.valueOf(text);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.valueOf(text);
            }
            if (type == long.class || type == Long.class) {
                return Long.valueOf(text);
            }
            if (type == float.class || type == Float.class) {
                return Float.valueOf(text);
            }
            if (type == double.class || type == Double.class) {
                return Double.valueOf(text);
            }
            if (type == String.class) {
                return text;
            }
        } catch (NumberFormatException ignored) {
            // 表里写错类型就当这项不存在
        }
        return null;
    }

    static Map<String, String> load() {
        Map<String, String> map = new HashMap<>();
        File dir = Log.moduleDir();
        if (dir == null) {
            return map;
        }
        File file = new File(dir, Config.OVERRIDE_FILE);
        if (!file.isFile()) {
            return map;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq <= 0) {
                    continue;
                }
                map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (Throwable t) {
            Log.k("配置覆盖：读 " + Config.OVERRIDE_FILE + " 失败 " + t);
        }
        return map;
    }
}
