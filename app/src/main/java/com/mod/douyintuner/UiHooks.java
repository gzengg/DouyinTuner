package com.mod.douyintuner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 界面精简：顶部频道/底部 tab 白名单、侧边栏营销入口、活动弹窗、红点静默。
 *
 * 统一手法是「过滤数据列表」而不是让某个扩展点返回 null —— 后者调用方常常直接
 * newInstance()，会变成崩溃。列表元素是 tab/频道的 id 字符串：
 * LJIJJ() 是底部 tab id 列表，LJJLJ() 是顶部频道 id 列表。
 */
final class UiHooks {

    private static final String TAB_SVC = "com.ss.android.ugc.aweme.homepage.tab.HomeTabServiceImpl";
    /**
     * 列表的实际提供方（HomeTabServiceImpl.LJIJJ 只是转发到它们）：
     */
    private static final String[] TAB_SOURCES = {"X.0grI", "X.0grK"};
    private static final String BOTTOM_METHOD = "LJIJJ";
    private static final String TOP_METHOD = "LJJLJ";

    /** 底部白名单：首页、信息、我。 */
    private static final Set<String> KEEP_BOTTOM = ids(
            "homepage_hot", "homepage_home", "homepage_hot_container",
            "homepage_notification", "homepage_profile");
    /** 顶部白名单：推荐/关注；直播的 id 在代码里查不到，只能按关键字兜底保留。 */
    private static final Set<String> KEEP_TOP = ids(
            "homepage_hot", "homepage_hot_container", "homepage_follow");

    /** 侧边栏/个人页里判定为营销入口的关键词（只匹配 id 文案）。 */
    private static final String[] MARKETING_KEYS = {"mall", "ecom", "shop", "groupon", "commerce"};

    private static final String POP_HANDLER =
            "com.ss.android.ugc.aweme.api.popview.AbsForceDynamicPopViewHandler";

    private static final String REDBADGE_SVC = "com.ss.android.newmedia.redbadge.RedbadgeHandler";
    private static final String REDBADGE_MGR =
            "com.ss.android.ugc.awemepushlib.manager.AwemeRedBadgerManager";

    /** 每个列表只打一次原始 id 列表，抖音改版时照日志校准白名单。 */
    private static final Set<String> LOGGED = new HashSet<>();

    private UiHooks() {
    }

    static void tabBottom(ClassLoader cl) {
        whitelist(cl, true, KEEP_BOTTOM, "底部白名单");
    }

    static void channelTop(ClassLoader cl) {
        whitelist(cl, false, KEEP_TOP, "顶部白名单");
    }

    private static Set<String> ids(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static void whitelist(ClassLoader cl, boolean bottom, Set<String> keep, String label) {
        onTabLists(cl, only(bottom, keep));
        // 兜底：按 id 查位置/存在性时也当成没有，避免别处索引到已隐藏的 tab。
        Class<?> svc = H.cls(cl, TAB_SVC);
        H.hookAll(svc, "getTabPosition", notFound(bottom, keep, -1));
        H.hookAll(svc, "LJJIZ", notFound(bottom, keep, Boolean.FALSE));
        H.hookAll(svc, "LJJJI", notFound(bottom, keep, ""));
        Log.i("界面精简 " + label + " 保留 " + keep);
    }

    /** 侧边栏营销入口：按关键词过滤同名列表，命中才改动。 */
    static void sidebar(ClassLoader cl) {
        onTabLists(cl, dropByKeyword(MARKETING_KEYS));
        Log.i("侧边栏：已挂钩营销入口过滤 " + Arrays.toString(MARKETING_KEYS));
    }

    static void popup(ClassLoader cl) {
        Class<?> handler = H.cls(cl, POP_HANDLER);
        // LLJ / LLIZLLLIL 是动态弹窗（Pizza / popview）的显示闸门，恒 false 即不展示。
        H.hookAll(handler, "LLJ", H.block());
        H.hookAll(handler, "LLIZLLLIL", H.block());
        Log.i("活动弹窗：已挂钩 popview 显示闸门");
    }

    /** 红点/角标静默：拦角标更新与红点服务，推送消息本身不动。 */
    static void redbadge(ClassLoader cl) {
        H.stop(H.cls(cl, REDBADGE_SVC), "handleMsg");
        H.stop(H.cls(cl, REDBADGE_SVC), "onStartCommand");
        Class<?> mgr = H.cls(cl, REDBADGE_MGR);
        H.stop(mgr, "LIZJ"); // 更新角标
        H.stop(mgr, "onUpdateBadgeEvent");
        Log.i("红点：已挂钩角标静默");
    }

    /** 两个列表方法上挂同一个处理器：LJIJJ=底部 tab id，LJJLJ=顶部频道 id。 */
    private static void onTabLists(ClassLoader cl, XC_MethodHook handler) {
        for (String name : TAB_SOURCES) {
            Class<?> c = H.cls(cl, name);
            H.hookAll(c, BOTTOM_METHOD, handler);
            H.hookAll(c, TOP_METHOD, handler);
        }
        H.hookAll(H.cls(cl, TAB_SVC), BOTTOM_METHOD, handler); // 转发层兜底
    }

    /**
     * 只对明确是 tab/频道 id 的条目动手（homepage_*），结构类 id（各类 *_container 之外的
     * 内部标识）一律放过，避免把底部栏自己的容器摘掉。
     */
    private static boolean drop(String id, Set<String> keep, boolean bottom) {
        if (id == null || !id.startsWith("homepage_")) {
            return false;
        }
        if (keep.contains(id)) {
            return false;
        }
        // 直播频道 id 未确认（代码里只有 refresh_homepage_tablive 这类事件名），顶部按关键字兜底保留
        return bottom || !id.contains("live");
    }

    private static XC_MethodHook only(final boolean bottom, final Set<String> keep) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (!(result instanceof List)) {
                    return;
                }
                if (BOTTOM_METHOD.equals(param.method.getName()) != bottom) {
                    return; // 这个开关只管自己那一栏
                }
                List<?> src = (List<?>) result;
                List<Object> kept = new ArrayList<>(src.size());
                List<String> all = new ArrayList<>(src.size());
                List<String> dropped = new ArrayList<>();
                for (Object item : src) {
                    String id = idOf(item);
                    all.add(id);
                    if (drop(id, keep, bottom)) {
                        dropped.add(id);
                    } else {
                        kept.add(item);
                    }
                }
                logOnce(bottom + param.method.getName(), all);
                if (!dropped.isEmpty()) {
                    Log.i("界面精简: 摘掉 " + dropped + " (" + param.method.getName() + ")");
                    param.setResult(kept);
                }
            }
        };
    }

    private static XC_MethodHook dropByKeyword(final String[] keys) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (!(result instanceof List)) {
                    return;
                }
                List<?> src = (List<?>) result;
                List<Object> kept = new ArrayList<>(src.size());
                boolean changed = false;
                for (Object item : src) {
                    String id = idOf(item);
                    if (id != null && matches(id, keys)) {
                        changed = true;
                        Log.i("隐藏营销入口: " + id + " (" + param.method.getName() + ")");
                        continue;
                    }
                    kept.add(item);
                }
                if (changed) {
                    param.setResult(kept);
                }
            }
        };
    }

    /** 命中要隐藏的 id 时把结果换成给定值，否则原样。 */
    private static XC_MethodHook notFound(final boolean bottom, final Set<String> keep, final Object value) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof String)) {
                    return;
                }
                if (drop((String) param.args[0], keep, bottom)) {
                    param.setResult(value);
                }
            }
        };
    }

    private static String idOf(Object item) {
        if (item instanceof String) {
            return (String) item;
        }
        Object id = H.call(item, "LIZ");
        if (id instanceof String) {
            return (String) id;
        }
        id = H.field(item, "LIZ");
        return id instanceof String ? (String) id : null;
    }

    private static boolean matches(String id, String[] keys) {
        String lower = id.toLowerCase();
        for (String key : keys) {
            if (lower.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** 每个列表整份 id 只记一次：抖音改版后靠这行日志就能算出新的白名单。 */
    private static void logOnce(String key, List<String> all) {
        boolean first;
        synchronized (LOGGED) {
            first = LOGGED.add(key);
        }
        if (first) {
            Log.i("tab 原始列表 " + key + " -> " + all);
        }
    }
}
