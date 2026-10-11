package io.adpass.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int INK = Color.rgb(26, 32, 47), MUTED = Color.rgb(116, 124, 143), BRAND = Color.rgb(101, 87, 238);
    private TextView status, explanation, accessLabel, today, total;
    private Switch master, landscapeOcr;
    private Button access, notification;
    private final Button[] cooldown = new Button[3];
    private boolean syncing;
    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density * n + .5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(dp(3), 1f); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void space(LinearLayout l, int height) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(view, p);
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(18), dp(18), dp(18), dp(18)); c.setBackground(shape(Color.WHITE, 22)); add(parent, c, 14); return c;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : INK); b.setBackground(shape(primary ? BRAND : Color.rgb(240, 241, 247), 14));
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setElevation(0); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout body = column(); body.setPadding(dp(22), dp(22), dp(22), dp(30)); scroll.addView(body); setContentView(scroll);
        LinearLayout heading = row();
        ImageView logo = new ImageView(this); logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(44), dp(44)); logoParams.rightMargin = dp(10); heading.addView(logo, logoParams);
        TextView name = text("ADSkip", 30, INK, true); heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView version = text("初版 0.2.2", 11, BRAND, true); version.setPadding(dp(10), dp(6), dp(10), dp(6)); version.setBackground(shape(Color.rgb(235, 232, 255), 20)); heading.addView(version);
        body.addView(heading); add(body, text("少一点打断，多一点剧情", 13, MUTED, false), 3);
        LinearLayout hero = column(); hero.setPadding(dp(22), dp(22), dp(22), dp(24));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(43, 40, 80), Color.rgb(22, 24, 41)});
        gradient.setCornerRadius(dp(26)); hero.setBackground(gradient); add(body, hero, 22);
        TextView tag = text("●  红果广告助手", 12, Color.rgb(188, 182, 255), true); hero.addView(tag);
        status = text("等待开启", 26, Color.WHITE, true); status.setId(R.id.status_title); add(hero, status, 16);
        explanation = text("开启无障碍后，让可跳过的广告自动划走。", 13, Color.rgb(199, 200, 218), false); add(hero, explanation, 8);

        LinearLayout controls = card(body), toggleRow = row(), labels = column();
        labels.addView(text("自动跳过", 17, INK, true)); add(labels, text("横屏广告先尝试上滑一次", 12, MUTED, false), 4);
        toggleRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        master = new Switch(this); master.setId(R.id.master_switch); master.setContentDescription("自动跳过"); master.setShowText(false); toggleRow.addView(master); controls.addView(toggleRow);
        accessLabel = text("无障碍尚未开启", 12, MUTED, false); add(controls, accessLabel, 14);
        LinearLayout actions = row();
        access = button("开启无障碍", true); access.setId(R.id.enable_accessibility); actions.addView(access, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button open = button("打开红果 ↗", false); open.setId(R.id.open_hongguo);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, dp(48), 1); op.leftMargin = dp(10); actions.addView(open, op); add(controls, actions, 10);
        access.setOnClickListener(v -> guide()); open.setOnClickListener(v -> openHongguo());
        master.setOnCheckedChangeListener((view, checked) -> {
            if (syncing) return;
            if (checked && !AppState.accessible(this)) { refresh(); guide(); return; }
            AppState.setEnabled(this, checked); refresh();
        });

        LinearLayout counts = row(); counts.setPadding(dp(4), dp(18), dp(4), dp(4));
        LinearLayout first = column(), second = column();
        today = text("0", 27, INK, true); today.setId(R.id.today_count); first.addView(today); first.addView(text("今日完成上滑", 12, MUTED, false));
        total = text("0", 27, INK, true); total.setId(R.id.total_count); second.addView(total); second.addView(text("累计完成上滑", 12, MUTED, false));
        counts.addView(first, new LinearLayout.LayoutParams(0, -2, 1)); counts.addView(second, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(counts);

        LinearLayout settings = card(body); settings.addView(text("跳过后的冷却时间", 16, INK, true));
        add(settings, text("两次确认后才上滑，避免反复触发。", 12, MUTED, false), 5);
        LinearLayout choices = row(); int[] seconds = {8, 12, 20}; int[] ids = {R.id.cooldown_8, R.id.cooldown_12, R.id.cooldown_20};
        for (int i = 0; i < 3; i++) {
            final int duration = seconds[i]; Button choice = button(duration + " 秒", false); choice.setId(ids[i]); cooldown[i] = choice;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(42), 1); if (i > 0) cp.leftMargin = dp(8); choices.addView(choice, cp);
            choice.setOnClickListener(v -> { AppState.prefs(this).edit().putInt("cooldown", duration).apply(); refresh(); });
        }
        add(settings, choices, 14);

        LinearLayout visual = card(body), visualRow = row(), visualLabels = column();
        visualLabels.addView(text("横屏辅助识别", 16, INK, true));
        add(visualLabels, text("首次上滑未退出时识别提示", 12, MUTED, false), 4);
        visualRow.addView(visualLabels, new LinearLayout.LayoutParams(0, -2, 1));
        landscapeOcr = new Switch(this); landscapeOcr.setId(R.id.landscape_ocr);
        landscapeOcr.setContentDescription("横屏辅助识别"); landscapeOcr.setShowText(false); visualRow.addView(landscapeOcr); visual.addView(visualRow);
        add(visual, text("横屏广告先尝试上滑一次，未退出时用此功能确认继续观看提示。截图即时丢弃，不保存、不上传。需要 Android 11 或以上。", 12, MUTED, false), 12);
        landscapeOcr.setOnCheckedChangeListener((view, checked) -> {
            if (syncing) return;
            if (!checked) { AppState.prefs(this).edit().putBoolean("landscape-ocr", false).apply(); refresh(); return; }
            refresh();
            new AlertDialog.Builder(this).setTitle("开启横屏辅助识别")
                .setMessage("横屏广告会先尝试上滑一次。若广告未退出，开启此功能后会临时截取屏幕，在本机识别继续观看提示；截图和识别文字不保存、不上传。\n\n倒计时或无法确认时等待，连续确认可继续观看后再上滑。你可以随时关闭此开关或自动跳过。")
                .setPositiveButton("开启", (dialog, which) -> { AppState.prefs(this).edit().putBoolean("landscape-ocr", true).apply(); refresh(); })
                .setNegativeButton("取消", null).show();
        });

        LinearLayout scope = card(body); scope.addView(text("初版支持范围", 16, INK, true));
        scopeRow(scope, "红果可跳过广告", "已验证", BRAND);
        scopeRow(scope, "直播卡片", "待验证", MUTED);
        scopeRow(scope, "原生横屏上滑广告", "先试滑一次", BRAND);
        add(scope, text("直播卡片仍待验证；无法确认可继续观看的广告保持等待。", 12, MUTED, false), 12);

        Button logs = button("查看操作记录  →", false); logs.setId(R.id.view_logs); add(body, logs, 16); logs.setOnClickListener(v -> showLogs());
        notification = button("启用通知栏暂停入口", false); add(body, notification, 10);
        notification.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            else { Notice.show(this); Toast.makeText(this, "开启自动跳过后，运行通知会显示暂停入口", Toast.LENGTH_SHORT).show(); }
        });
        TextView footer = text("仅本机处理 · 不保存截图 · 不上传页面内容\nAndroid 初版 0.2.2", 11, MUTED, false); footer.setGravity(Gravity.CENTER); add(body, footer, 20);
        refresh();
    }
    private void scopeRow(LinearLayout parent, String label, String state, int color) {
        LinearLayout r = row(); TextView name = text(label, 13, INK, false); r.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(text(state, 11, color, true)); add(parent, r, 12);
    }
    @Override public void onResume() {
        super.onResume();
        if (!AppState.accessible(this) && AppState.enabled(this)) AppState.setEnabled(this, false);
        AppState.prefs(this).registerOnSharedPreferenceChangeListener(this); refresh();
    }
    @Override public void onPause() { AppState.prefs(this).unregisterOnSharedPreferenceChangeListener(this); super.onPause(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) { runOnUiThread(this::refresh); }
    private void refresh() {
        if (master == null) return;
        boolean accessible = AppState.accessible(this), enabled = AppState.enabled(this);
        syncing = true; master.setChecked(accessible && enabled); master.setEnabled(accessible);
        landscapeOcr.setChecked(AppState.prefs(this).getBoolean("landscape-ocr", false)); landscapeOcr.setEnabled(Build.VERSION.SDK_INT >= 30); syncing = false;
        String state = AppState.prefs(this).getString("status", "waiting"), title, detail;
        if (!accessible) { title = "先开启无障碍"; detail = "允许读取红果页面文字和执行上滑，再打开自动跳过。"; }
        else if (!enabled) { title = "助手已暂停"; detail = "开启后，横屏广告先试滑一次；未退出时可用辅助识别。"; }
        else {
            title = "助手已就绪"; detail = "横屏广告先尝试上滑一次；其他广告确认可继续观看后上滑。";
            if (state.equals("ad-waiting")) { title = "等待广告倒计时"; detail = "页面允许继续观看后，才会自动上滑。"; }
            else if (state.equals("confirming")) { title = "正在确认广告"; detail = "连续确认可跳过提示，避免误划正常内容。"; }
            else if (state.equals("cooldown")) { title = "正在冷却"; detail = "已完成一次操作，稍后再处理下一条广告。"; }
            else if (state.equals("trial-confirming")) { title = "正在确认横屏广告"; detail = "连续检测到广告标记后，先尝试上滑一次。"; }
            else if (state.equals("trial-checking")) { title = "正在检查广告是否退出"; detail = "确认返回剧集；广告仍在时使用辅助识别，不反复试滑。"; }
            else if (state.equals("visual-disabled")) { title = "横屏广告需要辅助识别"; detail = "首次试滑未退出。开启横屏辅助识别后，可确认何时允许继续观看。"; }
            else if (state.equals("visual-reading")) { title = "正在识别横屏提示"; detail = "在本机确认可继续观看提示后才上滑。"; }
            else if (state.equals("visual-unavailable")) { title = "横屏识别暂不可用"; detail = "未能读取画面，保持等待并稍后重试。"; }
            else if (state.equals("live-pending")) { title = "直播暂不处理"; detail = "直播卡片仍待验证，本次不会自动操作。"; }
            else if (state.equals("screen-off")) { title = "屏幕关闭时暂停"; detail = "解锁并回到红果后，再继续识别。"; }
            else if (state.equals("interrupted")) { title = "检测暂时中断"; detail = "返回红果后重试；也可关闭再开启自动跳过。"; }
        }
        status.setText(title); explanation.setText(detail);
        accessLabel.setText(accessible ? "● 无障碍已开启 · 仅处理红果" : "○ 无障碍尚未开启");
        accessLabel.setTextColor(accessible ? BRAND : MUTED); access.setText(accessible ? "无障碍设置" : "开启无障碍");
        today.setText(String.valueOf(AppState.today(this))); total.setText(String.valueOf(AppState.prefs(this).getInt("total", 0)));
        int[] seconds = {8, 12, 20};
        for (int i = 0; i < 3; i++) {
            boolean chosen = AppState.cooldown(this) == seconds[i]; cooldown[i].setTextColor(chosen ? Color.WHITE : INK);
            cooldown[i].setBackground(shape(chosen ? BRAND : Color.rgb(240, 241, 247), 12)); cooldown[i].setSelected(chosen);
            cooldown[i].setContentDescription(seconds[i] + "秒冷却" + (chosen ? "，已选中" : ""));
        }
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            notification.setText("通知栏暂停入口已可用");
    }
    private void guide() {
        new AlertDialog.Builder(this).setTitle("开启 ADSkip 无障碍服务")
            .setMessage("ADSkip 会读取红果当前页面。原生横屏广告连续出现广告标记后先尝试上滑一次；未退出时不反复试滑。其他广告在明确允许继续观看时上滑。\n\n可另行开启横屏辅助识别：试滑未退出时临时截屏，在本机确认继续观看提示。截图和页面文字不保存、不上传。你可以随时关闭自动跳过。\n\n进入系统设置后，开启“ADSkip 广告助手”，返回这里再打开开关。")
            .setPositiveButton("前往系统设置", (dialog, which) -> {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }).setNegativeButton("稍后", null).show();
    }
    private void openHongguo() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(AppState.TARGET);
        if (launch == null) { Toast.makeText(this, "请先安装红果短剧", Toast.LENGTH_LONG).show(); return; }
        try { startActivity(launch); } catch (Exception e) { Toast.makeText(this, "暂时无法打开红果", Toast.LENGTH_LONG).show(); }
    }
    private void showLogs() {
        TextView content = text(AppState.logs(this), 13, INK, false); content.setPadding(dp(22), dp(8), dp(22), dp(10)); content.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("最近操作记录 · 最多100条").setView(scroll)
            .setPositiveButton("完成", null).setNeutralButton("清空记录", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
            AppState.prefs(this).edit().putString("logs", "[]").apply(); content.setText(AppState.logs(this));
        })); dialog.show();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); if (code == 1) { Notice.show(this); refresh(); }
    }
}
