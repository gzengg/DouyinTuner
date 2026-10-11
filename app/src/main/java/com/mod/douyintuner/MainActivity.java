package com.mod.douyintuner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/** 模块面板：逐项开关（SharedPreferences 持久化）+ 配置覆盖表入口；桌面图标开关单独放在页面最底部。 */
public class MainActivity extends Activity {

    private SharedPreferences sp;
    private Boolean iconVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        root.addView(heading("抖音精简助手 v" + versionName()));
        root.addView(body("目标：抖音 " + Config.TARGET_VERSION + "（" + Config.TARGET_PKG + "）\n"
                + "开关改动后需要强制停止抖音再打开才生效。", 13));

        for (String[] sw : Config.SWITCHES) {
            root.addView(switchRow(sw[0], sw[1], sw[2]));
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button("配置覆盖表", v -> editOverrides()));
        root.addView(buttons);

        root.addView(body("配置覆盖表：" + overrideFile().getAbsolutePath(), 11));

        // 放在整个页面最底部：桌面图标的可见性不属于「抖音内功能开关」那一组
        root.addView(heading("桌面图标"));
        root.addView(switchRow(Config.K_HIDE_ICON, "隐藏桌面图标",
                "隐藏后从 LSPosed 模块列表长按本模块 →「模块设置」打开本面板", false));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        applyIconVisible(!sp.getBoolean(Config.K_HIDE_ICON, true));
    }

    /** 桌面图标就是 launcher activity-alias，开关直接控制它的启用状态。 */
    private void applyIconVisible(boolean visible) {
        if (iconVisible != null && iconVisible == visible) {
            return; // 避免每次开面板都弹一次 Toast
        }
        iconVisible = visible;
        ComponentName alias = new ComponentName(this, getPackageName() + ".Launcher");
        int state = visible
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
        Toast.makeText(this, visible ? "桌面图标已恢复" : "桌面图标已隐藏：LSPosed 模块列表里长按本模块 →「模块设置」可再打开",
                Toast.LENGTH_LONG).show();
    }

    /** 每个开关一行：标题 + 说明，状态存 SharedPreferences（默认开启）。 */
    private View switchRow(String key, String title, String desc) {
        return switchRow(key, title, desc, true);
    }

    private View switchRow(String key, String title, String desc, boolean def) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, 0);

        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(15);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            sp.edit().putBoolean(key, on).apply();
            if (Config.K_HIDE_ICON.equals(key)) {
                applyIconVisible(!on);
            }
        });
        box.addView(sw);
        box.addView(body(desc, 12));
        return box;
    }

    private TextView heading(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private TextView body(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    private File overrideFile() {
        return new File(getFilesDir(), Config.OVERRIDE_FILE);
    }

    /**
     * 配置覆盖表只是个 key=value 文本，面板里给个入口把模板复制到剪贴板，
     * 用户自己往这个文件里加行（需要 root 文件管理器或 adb push）。
     */
    private void editOverrides() {
        File file = overrideFile();
        String template = "# 每行一个 AB 配置项，格式 key=value，# 开头是注释。\n"
                + "# key 是抖音自己的字符串键名，写错或不存在时该项不生效（不会崩）。\n"
                + "# 例（这几个键名取自本机 APK 的字符串表）：\n"
                + "# player_setting_enable_ab_cache=false\n"
                + "# 改完需要强制停止抖音再打开。\n";
        boolean existed = file.isFile();
        try {
            if (!existed) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(template.getBytes());
                }
            }
        } catch (Throwable t) {
            toast("写不了配置文件: " + t);
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("DouyinTuner", file.getAbsolutePath()));
            toast(existed ? "路径已复制，改完重开抖音" : "已生成模板，路径已复制");
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
