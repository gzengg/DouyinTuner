package com.mod.douyintuner;

/** 模块与 hook 共用的常量：目标应用、预置文件名、开关 key 与文案。 */
public final class Config {

    public static final String MODULE_PKG = "com.mod.douyintuner";
    public static final String TARGET_PKG = "com.ss.android.ugc.aweme";
    public static final String TARGET_VERSION = "40.7.0";
    public static final int TARGET_VERSION_CODE = 400701;
    public static final String TAG = "DouyinTuner";

    public static final String PREFS = "douyintuner";
    public static final String LOG_FILE = "hook.log";
    public static final long LOG_MAX_BYTES = 256 * 1024;

    public static final String K_MEM_BG = "mem_bg_kill";
    public static final String K_MEM_CACHE = "mem_fg_cache";
    public static final String K_UI_TAB_MALL = "ui_tab_mall";
    public static final String K_UI_CHANNEL_GROUP = "ui_channel_groupbuy";
    public static final String K_UI_SIDEBAR = "ui_sidebar_marketing";
    public static final String K_UI_POPUP = "ui_popup";
    public static final String K_TEEN = "teen_dialog";
    public static final String K_AD_SPLASH = "ad_splash";
    public static final String K_AD_FEED = "ad_feed";
    public static final String K_UPDATE = "update_block";
    public static final String K_DL_NOWM = "dl_nowm";
    public static final String K_ONCE_VIEW = "once_view";
    public static final String K_RECALL = "recall_keep";
    public static final String K_COLD_BOOT = "cold_boot";
    public static final String K_HIDE_ICON = "hide_icon";
    public static final String K_CFG_OVERRIDE = "cfg_override";
    public static final String K_TELEMETRY = "telemetry_off";
    public static final String K_HOTFIX = "hotfix_off";
    public static final String K_REDBADGE = "redbadge_off";

    /** 配置覆盖表：模块私有目录下的一行一个 key=value。 */
    public static final String OVERRIDE_FILE = "overrides.txt";

    /** 面板展示顺序：{key, 标题, 说明}，默认全部开启。 */
    public static final String[][] SWITCHES = {
            {K_MEM_BG, "后台保活清理", "保留消息推送，只禁其它后台服务/定时任务（需重启抖音生效）"},
            {K_MEM_CACHE, "预加载与缓存上限", "降低视频预加载池与图片/视频缓存上限，不动起播速度"},
            {K_UI_TAB_MALL, "底部只留「首页/信息/我」", "底部 tab 白名单，朋友/商城/发布按钮等入口隐藏"},
            {K_UI_CHANNEL_GROUP, "顶部只留「推荐/关注/直播」", "首页顶部频道白名单，团购/同城/朋友等隐藏"},
            {K_UI_SIDEBAR, "移除侧边栏营销入口", "侧边栏与个人页的商城/带货/营销入口"},
            {K_UI_POPUP, "去活动弹窗与浮层", "开屏活动弹窗、首页营销浮层"},
            {K_TEEN, "拦未成年人模式弹窗", "青少年模式引导/切换/时长提醒弹窗不显示（设置里主动打开的也拦）"},
            {K_AD_SPLASH, "去开屏广告", "开屏广告直接跳过"},
            {K_AD_FEED, "过滤信息流广告", "在数据源头摘除广告条目，不再留下空位"},
            {K_UPDATE, "禁止升级与自更新下载", "拦升级弹窗、禁安装包下载与安装引导，保留资源包热更新"},
            {K_DL_NOWM, "无水印直链复制", "长按视频 → 分享 → 复制链接，剪贴板里的短链换成无水印直链"},
            {K_ONCE_VIEW, "闪图可反复查看", "私信闪图/一次性查看不再变「已看过」，可以反复打开（本地标记与服务端上报一起拦）"},
            {K_RECALL, "消息防撤回", "被撤回的消息仍显示原文/原图（不删标记、不改数据，只让判定点认为没撤回）"},
            {K_COLD_BOOT, "冷启动加速", "开播放器冷启降级开关 + 摘掉定位/地图/小程序等无关启动任务（冷启动日志会打任务清单）"},
            {K_CFG_OVERRIDE, "配置覆盖（进阶）", "按 overrides.txt 覆盖 ABMock 配置项，改完需重启抖音"},
            {K_TELEMETRY, "停埋点上报", "AppLog 强制上报直接拦掉，事件不再出网"},
            {K_HOTFIX, "禁用热修", "拦 Reparo applyPatch，抖音不再后台热修"},
            {K_REDBADGE, "红点静默", "角标/红点更新拦掉（推送消息本身保留）"},
    };

    private Config() {}
}
