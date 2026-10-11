package com.mod.douyintuner;

import android.app.AndroidAppHelper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 无水印直链：复制链接时把文案里的抖音短链换成无水印直链。
 *
 * 短链（https://v.douyin.com/xxx/）本身不含视频信息，所以要先在内存里找到当前这条
 * Aweme，再取它的播放地址。两个方向同时做：
 *   抓视频 —— 长按面板、SharePackage、CopyLinkFunction/ShareActionImpl 全方法；
 *   改文案 —— 抖音复制走的是 ClipboardUtil.copyToClipboard → ClipData.newPlainText
 *             → 一层 lancet 包装，不经过 ClipboardManager.setPrimaryClip，
 *             所以这三处（外加 setPrimaryClip）都挂。
 */
final class DlHooks {

    private static final String AWEME = "com.ss.android.ugc.aweme.feed.model.Aweme";
    private static final String LPP_PARAMS =
            "com.ss.android.ugc.aweme.feed.long_press_panel.model.LongPressPanelParams";
    private static final String LPP_MODULE =
            "com.ss.android.ugc.aweme.feed.long_press_panel.modules.LongPressPanelAbsModule";
    private static final String SHARE_PACKAGE = "com.ss.android.ugc.aweme.sharer.ui.SharePackage";
    private static final String COPY_LINK =
            "com.ss.android.ugc.aweme.im.share.aweme.only.sharepanel.function.CopyLinkFunction";
    private static final String SHARE_ACTION = "com.ss.android.ugc.aweme.share.ShareActionImpl";
    private static final String CLIP_UTIL = "com.ss.android.ugc.aweme.utils.ClipboardUtil";
    /**
     * 抖音自己的剪贴板门面（X.1ehs）：持有 ClipboardManager、缓存 ClipData，复制链接/复制口令
     * 都从这里出去。真机日志里的复制栈是 X.1ehs#LJIJJ → X.0TRC#LJLJLJ → setPrimaryClip，
     * 所以这里是比框架 API 更靠前的拦截点。
     */
    private static final String CLIP_FUNNEL = "X.1ehs";
    private static final String LABEL = "DouyinTuner";
    /**
     * 分享面板「复制链接」真正现造链接的地方：LinkChannelPresenter 点击后走
     * X.0wDH#execute(Context, SharePackage)，里面的链接/文案全部来自这个类的方法，
     * 拿到 SharePackage（X.0wCz，字段 a 就是 Aweme）后抖音自己把结果写进剪贴板。
     * 在这四个方法上就地换掉，比事后改剪贴板稳（不依赖剪贴板实现与线程）。
     */
    private static final String VIDEO_LINK_PARAMS =
            "com.ss.android.ugc.aweme.feed.share.VideoShareLinkParams";
    private static final String[] LINK_TEXT_METHODS = {"LJIILIIL", "LJIIJ", "LJIIJJI", "LJIILJJIL"};

    /** 反射遍历节点上限：Aweme 通常就在 1-2 层内，够用又不会把整个对象图走一遍。 */
    private static final int WALK_BUDGET = 400;
    /** 改写失败最多记几条，避免反复复制时刷屏。 */
    private static final int FAIL_LOG_MAX = 5;
    private static final int TEXT_LOG_MAX = 80;

    private static final long TOAST_GAP_MS = 3000L;

    private static volatile Object lastAweme;
    private static int failures;
    private static boolean funnelSeen;
    private static long lastToast;

    private DlHooks() {
    }

    static void nowm(ClassLoader cl) {
        XC_MethodHook capture = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                capture(param.thisObject, param.args);
            }
        };
        Class<?> params = H.cls(cl, LPP_PARAMS);
        H.hookCtors(params, capture);
        H.hookEveryMethod(params, capture);
        H.hookEveryMethod(H.cls(cl, LPP_MODULE), capture);
        H.hookCtors(H.cls(cl, SHARE_PACKAGE), capture);
        H.hookEveryMethod(H.cls(cl, SHARE_PACKAGE), capture);
        H.hookCtors(H.cls(cl, COPY_LINK), capture);
        H.hookEveryMethod(H.cls(cl, COPY_LINK), capture);
        H.hookEveryMethod(H.cls(cl, SHARE_ACTION), capture);

        // 主拦截点：抖音自己生成分享链接/口令文案的地方，就地换成无水印直链
        Class<?> videoLink = H.cls(cl, VIDEO_LINK_PARAMS);
        for (String m : LINK_TEXT_METHODS) {
            H.hookAll(videoLink, m, linkText());
        }

        // 抖音自己的剪贴板门面：复制链接/复制口令都过这里（X.1ehs 持有 ClipboardManager 与 ClipData）
        H.hookEveryMethod(H.cls(cl, CLIP_FUNNEL), funnel());
        // 框架层兜底：ClipboardUtil.copyToClipboard → ClipData.newPlainText → setPrimaryClip
        H.hook(H.cls(cl, CLIP_UTIL), "copyToClipboard", String.class, Context.class, String.class, textArg());
        H.hook(ClipData.class, "newPlainText", CharSequence.class, CharSequence.class, textArg());
        H.hook(ClipboardManager.class, "setPrimaryClip", ClipData.class, textArg());
        Log.i("无水印直链：已挂钩 " + VIDEO_LINK_PARAMS + " 的链接生成方法"
                + " + 剪贴板门面 X.1ehs + ClipboardUtil/ClipData/ClipboardManager");
    }

    /**
     * 分享链接生成方法的返回值替换：只有 SharePackage 里确实带着 Aweme（视频分享）才动，
     * 名片/主页/直播间等分享一律原样返回。
     */
    private static XC_MethodHook linkText() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object arg = param.args != null && param.args.length > 0 ? param.args[0] : null;
                Object aweme = find(arg);
                if (aweme == null) {
                    return;
                }
                lastAweme = aweme;
                Object result = param.getResult();
                if (!(result instanceof String)) {
                    return;
                }
                String out = rewriteWith(aweme, (String) result);
                if (out != null) {
                    param.setResult(out);
                    Log.k("分享链接已换成无水印直链: " + out);
                    toast("已把分享链接换成无水印直链");
                }
            }
        };
    }

    /** 抖音剪贴板门面：把参数里的 ClipData/String 换掉（复制链接最终都过 X.1ehs#LJIJJ)。 */
    private static XC_MethodHook funnel() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // 第一次被调用就记一笔：证明门面挂对了（哪怕这次复制的东西里没有抖音链接）
                if (!funnelSeen) {
                    funnelSeen = true;
                    Log.i("剪贴板门面已生效：X.1ehs#" + param.method.getName() + " 被调用");
                }
                rewriteArgs(param.args);
            }
        };
    }

    /** 扫参数里带抖音链接的 ClipData/String 并换掉（改写点共用）。 */
    private static void rewriteArgs(Object[] args) {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String text;
            if (arg instanceof ClipData) {
                text = textOf((ClipData) arg);
            } else if (arg instanceof CharSequence) {
                text = arg.toString();
            } else {
                continue;
            }
            String out = rewrite(text);
            if (out == null) {
                continue;
            }
            args[i] = arg instanceof ClipData ? ClipData.newPlainText(LABEL, out) : out;
            Log.k("剪贴板已换成无水印直链: " + out);
            return;
        }
    }

    /** 框架层那几个改写点（ClipboardUtil / newPlainText / setPrimaryClip）共用。 */
    private static XC_MethodHook textArg() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                rewriteArgs(param.args);
            }
        };
    }

    /** 需要改写时返回新文案，否则 null。 */
    private static String rewrite(String text) {
        if (text == null || !looksLikeLink(text)) {
            return null; // 复制别的东西（文案、口令）一律不动
        }
        String out = rewriteWith(lastAweme, text);
        if (out != null) {
            return out;
        }
        String direct = clean(urlToken(text)); // 文本里本来就是 playwm 链接
        if (direct == null) {
            fail(text);
            return null;
        }
        out = swapLink(text, direct);
        return out.equals(text) ? null : out;
    }

    /** 用给定视频的无水印直链换掉文本里的那个链接；没有可换的返回 null。 */
    private static String rewriteWith(Object aweme, String text) {
        if (text == null || !looksLikeLink(text)) {
            return null;
        }
        String direct = fromAweme(aweme);
        if (direct == null) {
            return null;
        }
        String out = swapLink(text, direct);
        return out.equals(text) ? null : out;
    }

    /** 只换文案里的那个链接，前后的分享文案保留（复制链接复制的是整段文案）。 */
    private static String swapLink(String text, String direct) {
        int start = text.indexOf("http");
        if (start < 0) {
            return text;
        }
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        String token = text.substring(start, end);
        if (!looksLikeLink(token)) {
            return text;
        }
        return text.substring(0, start) + direct + text.substring(end);
    }

    private static String urlToken(String text) {
        int start = text.indexOf("http");
        if (start < 0) {
            return null;
        }
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

    private static void fail(String text) {
        if (failures++ >= FAIL_LOG_MAX) {
            return;
        }
        String shown = text.length() > TEXT_LOG_MAX ? text.substring(0, TEXT_LOG_MAX) + "…" : text;
        Log.k("剪贴板链接未改写（没抓到视频）: " + shown + " <- " + caller());
        toast("没抓到视频：先点开视频再复制链接");
    }

    /** 真机上看结果用：日志要导出才有，Toast 当场就能看见。3 秒内只弹一次。 */
    private static void toast(final String msg) {
        long now = System.currentTimeMillis();
        if (now - lastToast < TOAST_GAP_MS) {
            return;
        }
        lastToast = now;
        try {
            Context ctx = AndroidAppHelper.currentApplication();
            if (ctx == null) {
                return;
            }
            final Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Toast.makeText(app, "抖音精简助手：" + msg, Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {
                        // Toast 弹不出来不影响改写
                    }
                }
            });
        } catch (Throwable ignored) {
            // 拿不到上下文就只留日志
        }
    }

    /**
     * Video 里播放/下载地址的取值方法名：40.7.0 把字段与方法都混淆成了字母，
     * 没有 getPlayAddr() 这种名字，所以把返回 UrlModel 的几个方法都当候选。
     */
    private static final String[] VIDEO_URL_GETTERS =
            {"LJIIIIZZ", "LJIILIIL", "LJIILJJIL", "LJIILL", "LJIILLIIL", "LJIIJ"};

    /** 复制动作的调用栈：跳过框架帧，只留前 4 个抖音自己的帧。 */
    private static String caller() {
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (StackTraceElement f : new Throwable().getStackTrace()) {
            String c = f.getClassName();
            if (c.startsWith("java.") || c.startsWith("de.robv.") || c.startsWith("com.mod.douyintuner.")) {
                continue;
            }
            if (kept++ >= 4) {
                break;
            }
            sb.append(c).append('#').append(f.getMethodName()).append(' ');
        }
        return sb.toString();
    }

    private static String textOf(ClipData clip) {
        if (clip.getItemCount() == 0) {
            return null;
        }
        CharSequence cs = clip.getItemAt(0).getText();
        return cs == null ? null : cs.toString();
    }

    private static boolean looksLikeLink(String s) {
        return s != null && (s.contains("douyin.com") || s.contains("iesdouyin.com"));
    }

    /** 当前视频的无水印地址；没有捕获到视频时返回 null。优先 /play/ 这种长期有效的直链，材质直链有时效。 */
    private static String fromAweme(Object aweme) {
        List<String> urls = videoUrls(aweme);
        for (String url : urls) {
            if (url.contains("/play/") || url.contains("/aweme/v1/play/")) {
                return url;
            }
        }
        return urls.isEmpty() ? null : urls.get(0);
    }

    /**
     * 视频地址候选（按可靠性排序）：字段名/方法名随版本变，所以能取到的都取，
     * 用的时候谁先下得下来就用谁（分享页保存控件就是这么用的）。
     */
    static List<String> videoUrls(Object aweme) {
        List<String> out = new ArrayList<>();
        Object video = aweme == null ? null : H.call(aweme, "getVideo");
        if (video == null) {
            return out;
        }
        addCandidate(out, H.call(video, "getPlayAddr"));
        addCandidate(out, H.field(video, "playAddr"));
        for (String getter : VIDEO_URL_GETTERS) {
            addCandidate(out, H.call(video, getter));
        }
        addCandidate(out, H.call(video, "getDownloadAddr"));
        addCandidate(out, H.field(video, "downloadAddr"));
        return out;
    }

    /** UrlModel 里的全部 urlList（同一视频常有多个 CDN 域名，都留着当备用）。 */
    static void addCandidate(List<String> out, Object urlModel) {
        List<String> urls = urlList(urlModel);
        for (String url : urls) {
            if (!out.contains(url)) {
                out.add(url);
            }
        }
    }

    static List<String> urlList(Object urlModel) {
        List<String> out = new ArrayList<>();
        if (urlModel == null) {
            return out;
        }
        Object list = H.call(urlModel, "getUrlList");
        if (list == null) {
            list = H.field(urlModel, "urlList");
        }
        if (list instanceof List) {
            for (Object url : (List<?>) list) {
                if (url instanceof String) {
                    String cleaned = clean((String) url);
                    if (cleaned != null) {
                        out.add(cleaned);
                    }
                }
            }
        }
        return out;
    }

    private static String clean(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        return url.replace("/playwm/", "/play/").replace("&watermark=1", "");
    }

    /**
     * 在 thisObject 与参数里翻出 Aweme：分享流程里 Aweme 可能直接是参数，也可能裹在
     * LongPressPanelParams（字段 a）、LongPressPanelAbsModule（字段 e）或 SharePackage 的
     * extras 里，所以按字段递归找，而不是只看参数。
     */
    private static void capture(Object self, Object[] args) {
        Object aweme = find(self);
        if (aweme == null && args != null) {
            for (Object arg : args) {
                aweme = find(arg);
                if (aweme != null) {
                    break;
                }
            }
        }
        if (aweme != null) {
            if (lastAweme == null) {
                Log.i("已抓到当前视频，复制链接时会改写为无水印直链");
            }
            lastAweme = aweme;
        }
    }

    private static Object find(Object root) {
        if (root == null) {
            return null;
        }
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        ArrayDeque<Object> queue = new ArrayDeque<>();
        queue.add(root);
        seen.add(root);
        int budget = WALK_BUDGET;
        while (!queue.isEmpty() && budget-- > 0) {
            Object o = queue.poll();
            if (isAweme(o)) {
                return o;
            }
            if (o instanceof Collection) {
                for (Object e : (Collection<?>) o) {
                    enqueue(e, queue, seen);
                }
                continue;
            }
            if (o instanceof Bundle) { // extras 里塞的分享数据
                for (String key : ((Bundle) o).keySet()) {
                    try {
                        enqueue(((Bundle) o).get(key), queue, seen);
                    } catch (Throwable ignored) {
                        // 个别 key 取不出来就算了
                    }
                }
                continue;
            }
            String cn = o.getClass().getName();
            if (cn.startsWith("java.") || cn.startsWith("android.") || cn.startsWith("androidx.")
                    || cn.startsWith("kotlin.")) {
                continue;
            }
            for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        enqueue(f.get(o), queue, seen);
                    } catch (Throwable ignored) {
                        // 隐藏 API 字段读不到就跳过
                    }
                }
            }
        }
        return null;
    }

    private static void enqueue(Object value, ArrayDeque<Object> queue, Set<Object> seen) {
        if (value != null && seen.add(value)) {
            queue.add(value);
        }
    }

    private static boolean isAweme(Object o) {
        if (o == null) {
            return false;
        }
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            if (AWEME.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
