package com.mod.douyintuner;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 信息流广告：在数据源头（FeedItemList）把广告条目摘掉，而不是让它渲染成空卡片。
 *
 * 抖音自己的广告判定是 Aweme.isAd 字段（getAd() 只是它的 getter）。这里读字段，
 * 不依赖 getter 有没有被别的模块改过；过滤结果回写 items 字段，后续谁读都是干净列表。
 */
final class FeedHooks {

    private static final String FEED_LIST = "com.ss.android.ugc.aweme.feed.model.FeedItemList";

    private FeedHooks() {
    }

    static void feedItems(ClassLoader cl) {
        Class<?> c = H.cls(cl, FEED_LIST);
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (!(result instanceof List)) {
                    return;
                }
                List<?> src = (List<?>) result;
                if (src.isEmpty()) {
                    return;
                }
                List<Object> kept = new ArrayList<>(src.size());
                int dropped = 0;
                for (Object aweme : src) {
                    if (isAd(aweme)) {
                        dropped++;
                        continue;
                    }
                    kept.add(aweme);
                }
                if (dropped == 0) {
                    return;
                }
                // 回写字段：调用方多半直接读 public items，只改返回值会两边不一致。
                H.setField(param.thisObject, "items", kept);
                param.setResult(kept);
                Log.i("信息流广告：本页摘除 " + dropped + "/" + src.size() + " 条");
            }
        };
        H.hookAll(c, "getItems", filter);
        H.hookAll(c, "getItemsNotNull", filter);
        Log.i("信息流广告：已在 FeedItemList 源头挂过滤");
    }

    private static boolean isAd(Object aweme) {
        if (aweme == null) {
            return false;
        }
        Object flag = H.field(aweme, "isAd");
        if (flag instanceof Boolean && (Boolean) flag) {
            return true;
        }
        Object ad = H.call(aweme, "getAd");
        return ad instanceof Boolean && (Boolean) ad;
    }
}
