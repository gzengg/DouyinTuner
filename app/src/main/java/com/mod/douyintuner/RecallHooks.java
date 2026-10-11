package com.mod.douyintuner;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 消息防撤回：让被撤回的消息按原内容渲染。
 *
 * 撤回链路从不覆写 Message.content、也不删附件，只是往 ext 里写标记：
 *   接收端 X.1UdN#V2(CommonPreSaveData, MessageBody) 写 s:is_recalled=true
 *   发送端 X.1UdX#y3(Message)                       写 s:is_recalled=true
 * 而聊天页用哪个气泡，由撤回气泡的匹配谓词 X.0x2T#LJIILIIL(Message) 决定，它的实现就是
 * `return message.isRecalled();`。
 *
 * 所以只改这一个 getter（unobfuscated，也在 classes46.dex）：撤回标记照旧落库、IM 状态机
 * 完全不受影响，只是所有判定点都认为「没撤回」，于是原消息按本来的类型渲染 —— 文字、
 * 图片、视频都是原样。比去删 ext 标记更稳：服务端重推撤回命令也改不了这个返回值。
 */
final class RecallHooks {

    private static final String MESSAGE = "com.bytedance.im.core.model.Message";

    private RecallHooks() {
    }

    static void keep(ClassLoader cl) {
        H.hook(H.cls(cl, MESSAGE), "isRecalled", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(param.getResult())) {
                    return;
                }
                param.setResult(Boolean.FALSE);
                if (!logged) {
                    logged = true;
                    Log.k("防撤回已生效: " + MESSAGE + "#isRecalled() 恒为 false，撤回气泡不再匹配");
                }
            }
        });
        Log.i("消息防撤回：已挂钩 " + MESSAGE + "#isRecalled（原文/原图按原 content 渲染）");
    }

    private static volatile boolean logged;
}
