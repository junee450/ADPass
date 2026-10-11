package io.adpass.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class AppState {
    static final String TARGET = "com.phoenix.read";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("adpass", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static int cooldown(Context c) { return Math.max(8, Math.min(30, prefs(c).getInt("cooldown", 8))); }
    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("enabled", enabled).apply();
        log(c, enabled ? "自动跳过已开启" : "自动跳过已暂停");
        if (!enabled) { status(c, "paused"); Notice.cancel(c); }
        else Notice.show(c);
    }
    static boolean accessible(Context c) {
        AccessibilityManager manager = (AccessibilityManager)c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && c.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                && SkipService.class.getName().equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }
    static void status(Context c, String state) {
        SharedPreferences p = prefs(c);
        if (!state.equals(p.getString("status", ""))) p.edit().putString("status", state).apply();
    }
    static String day() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }
    static int today(Context c) { return day().equals(prefs(c).getString("day", "")) ? prefs(c).getInt("today", 0) : 0; }
    static void completed(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().putInt("total", p.getInt("total", 0) + 1).putString("day", day()).putInt("today", today(c) + 1).apply();
        log(c, "已执行一次广告上滑");
    }
    static synchronized void log(Context c, String message) {
        try {
            JSONArray old = new JSONArray(prefs(c).getString("logs", "[]")), result = new JSONArray();
            JSONObject entry = new JSONObject(); entry.put("time", System.currentTimeMillis()); entry.put("message", message);
            result.put(entry);
            for (int i = 0; i < Math.min(old.length(), 99); i++) result.put(old.get(i));
            prefs(c).edit().putString("logs", result.toString()).apply();
        } catch (Exception ignored) { }
    }
    static String logs(Context c) {
        try {
            JSONArray entries = new JSONArray(prefs(c).getString("logs", "[]"));
            if (entries.length() == 0) return "还没有操作记录。\n开启后，完成的上滑会显示在这里。";
            StringBuilder text = new StringBuilder();
            SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT);
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                text.append(f.format(new Date(entry.getLong("time")))).append("\n").append(entry.getString("message")).append("\n\n");
            }
            return text.toString().trim();
        } catch (Exception ignored) { return "暂无记录"; }
    }
}
