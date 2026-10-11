package com.mod.douyintuner;

import android.app.AndroidAppHelper;
import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

/**
 * 模块统一日志：logcat（LSPosed）+ 模块私有目录 hook.log。
 *
 * hook 跑在抖音进程里，模块私有目录靠 createPackageContext 拿到；拿不到就只写 logcat。
 * 两级：
 * <ul>
 *   <li>{@link #k(String)} 关键日志：加载、安装摘要、异常/跳过、以及少数几个用户可见行为的结果。默认就这些。
 *   <li>{@link #i(String)} 细节日志：逐个事件一行。默认不输出，排查时把 {@link #VERBOSE} 改 true。
 * </ul>
 */
public final class Log {

    /** 排查开关：打开后每个事件都会打一行（很吵）。 */
    private static final boolean VERBOSE = false;

    private static Context ctx;
    private static boolean ctxResolved;

    /** 关键日志：始终输出。 */
    public static void k(String msg) {
        write(msg);
    }

    /** 细节日志：默认丢弃（只保留在代码里供排查）。 */
    public static void i(String msg) {
        if (VERBOSE) {
            write(msg);
        }
    }

    private static void write(String msg) {
        XposedBridge.log(Config.TAG + " " + msg);

        Context c = moduleContext();
        if (c == null) {
            return;
        }
        String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + msg + "\n";
        try {
            File f = new File(c.getFilesDir(), Config.LOG_FILE);
            if (f.length() > Config.LOG_MAX_BYTES) {
                new FileWriter(f, false).close(); // 超限直接截断，不做轮转
            }
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {
            // 日志写不进去不影响功能
        }
    }

    /** 模块私有目录；拿不到返回 null（配置覆盖表这类文件就跳过）。 */
    static File moduleDir() {
        Context c = moduleContext();
        return c == null ? null : c.getFilesDir();
    }

    /** 模块自身上下文：hook 进程里要跨包取；失败返回 null，调用方降级处理。 */
    private static Context moduleContext() {
        if (!ctxResolved) {
            ctxResolved = true;
            try {
                Context app = AndroidAppHelper.currentApplication();
                ctx = app == null ? null
                        : app.createPackageContext(Config.MODULE_PKG, Context.CONTEXT_IGNORE_SECURITY);
            } catch (Throwable t) {
                ctx = null;
            }
        }
        return ctx;
    }
}
