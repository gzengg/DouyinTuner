package com.mod.douyintuner;

/**
 * 埋点静默：AppLog 的强制上报直接拦掉，事件只在本地攒着不再出网。
 *
 * 没有动 AppLog 的 did/iid/设备指纹，也没有拦 APM 的底层网络发送 —— 那些地方返回值
 * 被上游直接用，拦掉容易空指针，收益也不如这里直观。
 */
final class TelemetryHooks {

    private static final String APPLOG = "com.bytedance.applog.AppLog";

    private TelemetryHooks() {
    }

    static void silence(ClassLoader cl) {
        Class<?> appLog = H.cls(cl, APPLOG);
        H.stop(appLog, "flush");
        H.stop(appLog, "flushAsync");
        H.stop(appLog, "forceTimeoutReportSync");
        Log.i("埋点：AppLog 强制上报已拦，事件不再出网");
    }
}
