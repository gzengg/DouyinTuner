package com.mod.douyintuner;

import java.util.Collections;

import de.robv.android.xposed.XC_MethodReplacement;

/**
 * 升级链路：掐掉「检查更新」入口，就不再会有升级弹窗和安装包下载。
 *
 * 只拦检查/请求与安装引导；gecko/lynx 资源包热更新走的是另一条链路，不受影响。
 * 类名方法名都按 40.7.0 写死，缺一个只跳过并记日志。
 */
final class UpdateHooks {

    private static final String CHECK_REQ = "com.ss.android.ugc.aweme.requesttask.idle.CheckUpdateRequest";
    private static final String UPDATE_SVC = "com.ss.android.ugc.aweme.impl.AppUpdateService";
    private static final String INSTALL_HANDLER = "com.ss.android.ugc.aweme.update.AbsUpdateGrayApkInstallHandler";

    private UpdateHooks() {
    }

    static void block(ClassLoader cl) {
        H.stop(H.cls(cl, CHECK_REQ), "request");
        H.stop(H.cls(cl, CHECK_REQ), "run");

        Class<?> svc = H.cls(cl, UPDATE_SVC);
        H.stop(svc, "LJIILJJIL");                       // 轮询/触发升级检查
        H.ret(svc, "LJ", Collections.emptyList());      // 待发起的升级请求列表直接给空

        // 已经下过包的老设备：安装引导也一并拦掉。
        Class<?> install = H.cls(cl, INSTALL_HANDLER);
        for (String m : new String[]{"LLILZIL", "LLIZ", "LLJI", "LLJIJIL"}) {
            H.stop(install, m);
        }
        H.hookAll(install, "LLJ", XC_MethodReplacement.returnConstant(Boolean.FALSE));
        H.hookAll(install, "LLIZLLLIL", XC_MethodReplacement.returnConstant(Boolean.FALSE));
        Log.i("升级链路：已拦截检查更新与安装引导（资源包热更新保留）");
    }
}
