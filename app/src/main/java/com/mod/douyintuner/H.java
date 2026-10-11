package com.mod.douyintuner;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * hook 辅助：所有目标先做类/方法存在性判断，缺失就记一条跳过日志。
 * 抖音版本变动时模块退化成“少生效”，而不是把自己变成崩溃源。
 */
final class H {

    private H() {
    }

    static Class<?> cls(ClassLoader cl, String name) {
        Class<?> c = XposedHelpers.findClassIfExists(name, cl);
        if (c == null) {
            Log.k("跳过(类不存在): " + name);
        }
        return c;
    }

    /** 按签名精确挂钩一个方法，签名不匹配只记日志。 */
    static void hook(Class<?> c, String method, Object... typesAndCallback) {
        if (c == null) {
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(c, method, typesAndCallback);
        } catch (Throwable t) {
            Log.k("跳过(方法不匹配): " + c.getName() + "#" + method);
        }
    }

    /** 挂钩同名全部重载（含参数未知的情况）；一个都没挂上记日志。 */
    static void hookAll(Class<?> c, String method, XC_MethodHook cb) {
        if (c == null) {
            return;
        }
        Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(c, method, cb);
        if (unhooks.isEmpty()) {
            Log.k("跳过(方法不匹配): " + c.getName() + "#" + method);
        }
    }

    /** 读/写实例字段，失败返回 null（不抛）。 */
    static Object field(Object obj, String name) {
        if (obj == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(obj, name);
        } catch (Throwable t) {
            return null;
        }
    }

    static void setField(Object obj, String name, Object value) {
        if (obj == null) {
            return;
        }
        try {
            XposedHelpers.setObjectField(obj, name, value);
        } catch (Throwable ignored) {
            // 字段不存在就算了，不值得为它中断其余 hook
        }
    }

    /** 无参反射调用，失败返回 null。 */
    static Object call(Object obj, String name) {
        if (obj == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(obj, name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 同名方法的全部重载都替换成固定返回值。 */
    static void ret(Class<?> c, String method, Object value) {
        hookAll(c, method, XC_MethodReplacement.returnConstant(value));
    }

    /** 拦截用替换：布尔→false，void→不执行，其余→零值。
     * 用于参数签名不确定、但确定要拦下来的方法。
     */
    static XC_MethodReplacement block() {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) {
                Class<?> rt = returnType(param.method);
                if (rt == boolean.class) {
                    return Boolean.FALSE;
                }
                if (rt == int.class) {
                    return 0;
                }
                if (rt == long.class) {
                    return 0L;
                }
                return null;
            }
        };
    }

    /** MethodHookParam.method 声明成 Member，构造器没有返回类型，统一在这里换算。 */
    static Class<?> returnType(java.lang.reflect.Member member) {
        return member instanceof java.lang.reflect.Method
                ? ((java.lang.reflect.Method) member).getReturnType()
                : void.class;
    }

    /** 同名方法的全部重载都“不执行原逻辑”。 */
    static void stop(Class<?> c, String method) {
        hookAll(c, method, block());
    }

    static String typeOf(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }

    /** 挂钩该类全部构造器。 */
    static void hookCtors(Class<?> c, XC_MethodHook cb) {
        if (c == null) {
            return;
        }
        Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllConstructors(c, cb);
        if (unhooks.isEmpty()) {
            Log.k("跳过(构造器不匹配): " + c.getName());
        }
    }

    /**
     * 挂钩该类自己声明的全部方法（含私有）。用在方法名不可知、但必须监视
     * 它每次调用的场景（例如长按面板里顺带拿到当前视频对象）。
     */
    static void hookEveryMethod(Class<?> c, XC_MethodHook cb) {
        if (c == null) {
            return;
        }
        int n = 0;
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, cb);
                n++;
            } catch (Throwable ignored) {
                // 个别方法挂不上不影响其余
            }
        }
        Log.i("挂钩 " + c.getName() + " 的全部方法: " + n + " 个");
    }
}
