package com.mod.douyintuner;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 冷启动加速：两处落点，一个管播放器侧的「降级」实验，一个管 Lego 启动任务。
 *
 * 1) 播放器降级开关不走 ABMock 的 getter，值最终由
 *    com.ss.android.ugc.aweme.video.simplayer.PlayerSettingServiceImpl.LIZ(String,Type,Object,boolean)
 *    从 Keva 里读出来，所以在这一层按 key 白名单替换返回值即可（key 默认 false=不降级，
 *    返回 true 就走省流程分支）。报告里 §4.3 推荐的 6 项都在这儿开。
 *
 * 2) Lego 的全部任务/服务/请求都汇聚到 X.0ngb.LIZJ(Task,int,int)，传进来的 task 本身就是
 *    LegoComponent。按类名摘掉「和首页首帧无关」的启动任务（定位/地图/小程序/锁优化），
 *    其余一律放行 —— 播放器内核、SPI、崩溃兜底、布局 inflate 绝对不能碰。
 *
 * 保守起见只摘 5 个 WorkType 明确的（已读字节码确认）任务，并把每次冷启动实际经过调度点的
 * 任务类名前 40 个打一次日志，便于按真机日志再决定要不要扩表。
 */
final class ColdBootHooks {

    private static final String PLAYER_SETTING = "com.ss.android.ugc.aweme.video.simplayer.PlayerSettingServiceImpl";
    private static final String SCHEDULER = "X.0ngb";
    private static final String LEGO_COMPONENT = "com.ss.android.ugc.aweme.lego.LegoComponent";
    private static final String TASK_PREFIX = "com.ss.android.ugc.aweme.legoImp.task";

    /** 默认 false=不降级；返回 true 才走省流程分支。 */
    private static final String[] DEGRADE_KEYS = {
            "cold_boot_play_skip_config_read",
            "cold_boot_preload_manager_degrade",
            "cold_boot_video_effect_degrade",
            "cold_boot_video_plugin_degrade",
            "cold_boot_video_brightness_module_degrade",
            "cold_boot_video_volume_balance_degrade",
    };

    /** 和首页首帧无关的启动任务，摘掉不省首帧之外没坏处（WorkType.MAIN/BACKGROUND 已确认）。 */
    private static final String[] SKIP_TASKS = {
            "com.ss.android.ugc.aweme.legoImp.task.ColdBootLocateManagerTask",
            "com.ss.android.ugc.aweme.legoImp.task.ColdBootLightLocateTask",
            "com.ss.android.ugc.aweme.legoImp.task.MiniAppInitTask",
            "com.ss.android.ugc.aweme.legoImp.task.mainProcessTask.MapInitializerTask",
            "com.ss.android.ugc.aweme.legoImp.task.mainProcessTask.LockOptTask",
    };

    private static final int OBSERVE_MAX = 40;
    private static final Set<String> OBSERVED = new HashSet<>();

    private ColdBootHooks() {
    }

    static void boost(ClassLoader cl) {
        playerDegrade(cl);
        legoSkip(cl);
    }

    /** 播放器侧冷启降级开关：按 key 覆盖成 true。 */
    private static void playerDegrade(ClassLoader cl) {
        final Class<?> setting = H.cls(cl, PLAYER_SETTING);
        if (setting == null) {
            return;
        }
        final Map<String, String> overrides = ConfigHooks.load();
        H.hook(setting, "LIZ", String.class, Type.class, Object.class, boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object first = param.args.length > 0 ? param.args[0] : null;
                        if (!(first instanceof String)) {
                            return;
                        }
                        String key = (String) first;
                        Boolean value = want(key, overrides);
                        if (value == null) {
                            return;
                        }
                        param.setResult(value);
                        if (loggedKeys.add(key)) {
                            Log.i("冷启动：播放器开关 " + key + " 覆盖为 " + value);
                        }
                    }
                });
        Log.i("冷启动：已挂钩 " + PLAYER_SETTING + "#LIZ，降级开关 " + DEGRADE_KEYS.length + " 项");
    }

    /** overrides.txt 里显式写了的 key 听用户的，其余按内置白名单。 */
    private static Boolean want(String key, Map<String, String> overrides) {
        String raw = overrides.get(key);
        if (raw != null) {
            return parseBool(raw);
        }
        for (String k : DEGRADE_KEYS) {
            if (k.equals(key)) {
                return Boolean.TRUE;
            }
        }
        return null;
    }

    private static Boolean parseBool(String raw) {
        String v = raw.trim();
        if ("1".equals(v) || "true".equalsIgnoreCase(v)) {
            return Boolean.TRUE;
        }
        if ("0".equals(v) || "false".equalsIgnoreCase(v)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** Lego 调度汇聚点：摘掉无关启动任务，并把经过点位的任务类名记一份清单。 */
    private static void legoSkip(ClassLoader cl) {
        final Class<?> scheduler = H.cls(cl, SCHEDULER);
        final Class<?> component = H.cls(cl, LEGO_COMPONENT);
        if (scheduler == null || component == null) {
            return;
        }
        H.hookAll(scheduler, "LIZJ", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object task = param.args.length > 0 ? param.args[0] : null;
                if (task == null || !component.isInstance(task)) {
                    return; // 非 Lego 的 nimbleworker 任务，一律放行
                }
                String name = task.getClass().getName();
                if (skip(name)) {
                    param.setResult(null);
                    if (loggedTasks.add(name)) {
                        Log.i("冷启动：跳过启动任务 " + name);
                    }
                    return;
                }
                if (name.startsWith(TASK_PREFIX) && OBSERVED.size() < OBSERVE_MAX && OBSERVED.add(name)) {
                    Log.i("冷启动：启动任务经过 " + name);
                }
            }
        });
        Log.i("冷启动：已挂钩 " + SCHEDULER + "#LIZJ，跳过任务 " + SKIP_TASKS.length + " 个");
    }

    private static boolean skip(String name) {
        for (String s : SKIP_TASKS) {
            if (s.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> loggedKeys = new HashSet<>();
    private static final Set<String> loggedTasks = new HashSet<>();
}
