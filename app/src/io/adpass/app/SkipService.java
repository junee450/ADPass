package io.adpass.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.adpass.probe.PageRules;

public final class SkipService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SkipGate gate = new SkipGate();
    private LandscapeAttempt landscapeAttempt;
    private boolean trialFallbackLogged;
    private boolean scheduled, pending, registered;
    private boolean visualBusy, visualRegionLogged;
    private int reportedVisualOutcomes;
    private long generation;
    private final ExecutorService visualWorker = Executors.newSingleThreadExecutor();
    private HeaderOcr ocr;
    private final Runnable scan = () -> { scheduled = false; inspect(); };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            cancelScan(); gate.reset();
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) schedule(500);
            else AppState.status(c, "screen-off");
        }
    };
    @Override protected void onServiceConnected() {
        landscapeAttempt = new LandscapeAttempt(AppState.prefs(this).getBoolean("landscape-tried", false));
        AppState.prefs(this).registerOnSharedPreferenceChangeListener(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        registered = true;
        AppState.log(this, "无障碍服务已连接");
        if (AppState.enabled(this)) { Notice.show(this); schedule(150); }
        else AppState.status(this, "paused");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (AppState.enabled(this) && AppState.TARGET.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) schedule(180);
    }
    @Override public void onInterrupt() { cancelScan(); gate.reset(); AppState.status(this, "interrupted"); }
    @Override public void onDestroy() {
        cancelScan(); gate.reset();
        releaseOcr(); visualWorker.shutdown();
        AppState.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
        if (registered) unregisterReceiver(screen);
        AppState.status(this, "disconnected"); Notice.cancel(this);
        super.onDestroy();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (!key.equals("enabled") && !key.equals("cooldown") && !key.equals("landscape-ocr")) return;
        cancelScan(); gate.reset();
        if (!AppState.enabled(this) || !prefs.getBoolean("landscape-ocr", false)) releaseOcr();
        if (AppState.enabled(this)) { Notice.show(this); schedule(100); }
        else { AppState.status(this, "paused"); Notice.cancel(this); }
    }
    private void schedule(long delay) {
        if (!AppState.enabled(this) || scheduled) return;
        scheduled = true; handler.postDelayed(scan, delay);
    }
    private void cancelScan() {
        handler.removeCallbacks(scan); scheduled = false; generation++; visualBusy = false;
        if (landscapeAttempt != null) landscapeAttempt.interruptObservation();
    }
    private void releaseOcr() {
        visualWorker.execute(() -> { if (ocr != null) { ocr.close(); ocr = null; } });
    }
    private boolean awake() { return ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive(); }
    private boolean unlocked() { return !((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked(); }
    private AccessibilityNodeInfo targetRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !AppState.TARGET.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return null;
        for (AccessibilityWindowInfo window : getWindows()) {
            if (!window.isFocused()) continue;
            AccessibilityNodeInfo focused = window.getRoot();
            if (focused != null && !AppState.TARGET.contentEquals(focused.getPackageName() == null ? "" : focused.getPackageName())) return null;
        }
        return root;
    }
    private static final class Page {
        final Rect window = new Rect(), badge = new Rect();
        final List<Rect> emptyViews = new ArrayList<>();
        String kind;
        int windowId;
        boolean adBadge;
        boolean landscapeBadge, dramaPlayer;
        int fingerprint;
    }
    private Page read(AccessibilityNodeInfo root) {
        Page page = new Page(); root.getBoundsInScreen(page.window); page.windowId = root.getWindowId();
        List<PageRules.Cue> cues = new ArrayList<>();
        boolean[] markers = new boolean[3];
        collect(root, page, cues, markers, 0, new int[]{0}); page.kind = PageRules.detect(cues);
        for (PageRules.Cue cue : cues) {
            if (cue.text.equals("广告")) page.adBadge = true;
            page.fingerprint = 31 * page.fingerprint + cue.text.hashCode();
        }
        page.dramaPlayer = markers[2] || (markers[0] && markers[1]);
        return page;
    }
    private void collect(AccessibilityNodeInfo node, Page page, List<PageRules.Cue> cues, boolean[] markers, int depth, int[] visited) {
        if (node == null || depth > 60 || ++visited[0] > 1500) return;
        Rect b = new Rect(); node.getBoundsInScreen(b);
        if (node.isVisibleToUser() && !b.isEmpty() && page.window.contains(b)) {
            String id = node.getViewIdResourceName();
            CharSequence text = node.getText(), description = node.getContentDescription();
            String value = (text == null ? "" : text.toString()) + (description == null ? "" : description.toString());
            if (!value.isEmpty()) cues.add(new PageRules.Cue(value, id));
            else if (node.getChildCount() == 0) page.emptyViews.add(new Rect(b));
            value = value.replaceAll("\\s+", "");
            if (value.equals("广告") && b.left >= page.window.left + page.window.width()*.65f
                && b.bottom <= page.window.top + page.window.height()*.25f) {
                page.landscapeBadge = true; page.badge.set(b);
            }
            if ((AppState.TARGET + ":id/k8").equals(id) && value.matches(".+第[0-9]+集")) markers[0] = true;
            if ((AppState.TARGET + ":id/ar1").equals(id) && node.isClickable()) markers[1] = true;
            if ((AppState.TARGET + ":id/i0a").equals(id) && value.contains("微短剧备字")) markers[2] = true;
        }
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), page, cues, markers, depth + 1, visited);
    }
    private void inspect() {
        try {
            if (!AppState.enabled(this)) { gate.reset(); AppState.status(this, "paused"); return; }
            if (!awake() || !unlocked()) { gate.reset(); landscapeAttempt.interruptObservation(); AppState.status(this, "screen-off"); return; }
            AccessibilityNodeInfo root = targetRoot();
            if (root == null) { gate.reset(); landscapeAttempt.interruptObservation(); AppState.status(this, "waiting"); return; }
            Page page = read(root);
            if (pending) { schedule(500); return; }
            if (landscapeAttempt.observe(page.adBadge, page.kind.equals("content") && page.dramaPlayer, SystemClock.elapsedRealtime())) {
                AppState.prefs(this).edit().putBoolean("landscape-tried", false).apply();
                trialFallbackLogged = false; visualRegionLogged = false; reportedVisualOutcomes = 0; gate.reset();
                releaseOcr();
                AppState.log(this, "已确认横屏广告退出并返回剧集");
            }
            boolean landscape = page.window.width() > page.window.height();
            if (page.window.isEmpty()) { gate.reset(); schedule(1000); return; }
            if (page.kind.equals("live-preview")) { gate.reset(); AppState.status(this, "live-pending"); schedule(1500); return; }
            if (landscape && page.adBadge && page.kind.equals("content")) {
                if (page.landscapeBadge && landscapeAttempt.canTry()) {
                    long now = System.currentTimeMillis(), last = AppState.prefs(this).getLong("last-action", 0);
                    boolean cooling = last != 0 && now - last < AppState.cooldown(this)*1000L;
                    AppState.status(this, cooling ? "cooldown" : "trial-confirming");
                    String key = "badge:" + page.window.toShortString() + ":" + page.windowId;
                    if (gate.ready(key, now, last, AppState.cooldown(this)*1000L)) perform(page, false, 0, true);
                    schedule(1000); return;
                }
                if (landscapeAttempt.verifying(SystemClock.elapsedRealtime())) {
                    gate.reset(); AppState.status(this, "trial-checking"); schedule(500); return;
                }
                if (!landscapeAttempt.canTry() && !trialFallbackLogged) {
                    trialFallbackLogged = true;
                    AppState.log(this, Build.VERSION.SDK_INT >= 30 && AppState.prefs(this).getBoolean("landscape-ocr", false)
                        ? "横屏试滑后广告仍在，改用提示识别" : "横屏试滑后广告仍在，等待辅助识别开启");
                }
                if (Build.VERSION.SDK_INT < 30 || !AppState.prefs(this).getBoolean("landscape-ocr", false)) {
                    gate.reset(); AppState.status(this, "visual-disabled"); schedule(1500); return;
                }
                if (!visualBusy) inspectVisual(page);
                return;
            }
            if (!landscapeAttempt.canTry() && !page.adBadge && page.kind.equals("content")) {
                gate.reset(); AppState.status(this, "trial-checking"); schedule(700); return;
            }
            consume(page, false, 0);
        } catch (Exception e) {
            gate.reset(); AppState.status(this, "interrupted"); AppState.log(this, "检测暂时中断，将稍后重试"); schedule(2000);
        }
    }
    private void consume(Page page, boolean visual, long capturedAt) {
            if (!SkipGate.eligible(AppState.enabled(this), true, true, true, pending, false, page.kind)) {
                gate.reset(); AppState.status(this, page.kind.equals("ad-waiting") ? "ad-waiting" : "watching"); schedule(1000); return;
            }
            long now = System.currentTimeMillis(), last = AppState.prefs(this).getLong("last-action", 0);
            long cooldown = AppState.cooldown(this) * 1000L;
            String key = (visual ? "visual:" : "nodes:") + page.window.toShortString() + ":" + page.windowId + ":" + page.fingerprint;
            AppState.status(this, last != 0 && now - last < cooldown ? "cooldown" : "confirming");
            if (gate.ready(key, now, last, cooldown)) perform(page, visual, capturedAt);
            schedule(1000);
    }
    private boolean sameAd(Page expected) {
        if (!AppState.enabled(this) || !AppState.prefs(this).getBoolean("landscape-ocr", false) || !awake() || !unlocked()) return false;
        AccessibilityNodeInfo root = targetRoot(); if (root == null) return false;
        Page current = read(root);
        return current.window.equals(expected.window) && current.windowId == expected.windowId && current.adBadge
            && current.badge.equals(expected.badge)
            && current.fingerprint == expected.fingerprint && current.kind.equals("content") && current.window.width() > current.window.height();
    }
    private void visualOutcome(String kind) {
        int bit = kind.equals("ad-ready") ? 1 : kind.equals("ad-waiting") ? 2
            : kind.equals("visual-unavailable") ? 4 : kind.equals("stale") ? 8 : 16;
        if ((reportedVisualOutcomes & bit) != 0) return;
        reportedVisualOutcomes |= bit;
        String message = kind.equals("ad-ready") ? "已完整识别横屏继续观看提示，等待确认及冷却"
            : kind.equals("ad-waiting") ? "识别到横屏广告倒计时，继续等待"
            : kind.equals("visual-unavailable") ? "横屏截图或识别暂不可用，将稍后重试"
            : kind.equals("stale") ? "横屏识别结果已过期，将重新检查"
            : "横屏提示未完整确认，将继续识别";
        AppState.log(this, message);
    }
    private void inspectVisual(Page expected) {
        visualBusy = true; final long token = generation, capturedAt = SystemClock.elapsedRealtime();
        AppState.status(this, "visual-reading");
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            private void failed(String state) {
                if (token != generation) return;
                visualBusy = false; gate.reset(); if (state.equals("visual-unavailable")) visualOutcome(state);
                AppState.status(SkipService.this, state); schedule(2000);
            }
            @Override public void onFailure(int code) { failed("visual-unavailable"); }
            @Override public void onSuccess(ScreenshotResult result) {
                Bitmap hardware = null, software = null, header = null;
                try {
                    if (token != generation || !sameAd(expected)) { failed("waiting"); return; }
                    hardware = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                    if (hardware == null) { failed("visual-unavailable"); return; }
                    software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    Rect region = HeaderOcr.headerBounds(expected.window, expected.badge, expected.emptyViews);
                    if (region.isEmpty() || !new Rect(0, 0, software.getWidth(), software.getHeight()).contains(region)) {
                        failed("visual-unavailable"); return;
                    }
                    if (!visualRegionLogged) {
                        visualRegionLogged = true;
                        AppState.log(SkipService.this, region.equals(HeaderOcr.headerBounds(expected.window, expected.badge))
                            ? "横屏提示区域按广告标记定位" : "横屏提示区域由无障碍布局定位");
                    }
                    header = Bitmap.createBitmap(software, region.left, region.top, region.width(), region.height());
                    Bitmap input = header; header = null;
                    try {
                        visualWorker.execute(() -> {
                            HeaderOcr.Result recognition = null;
                            try { if (ocr == null) ocr = new HeaderOcr(SkipService.this); recognition = ocr.read(input); }
                            catch (Throwable ignored) { }
                            finally { input.recycle(); }
                            HeaderOcr.Result reading = recognition;
                            handler.post(() -> {
                                if (token != generation) return;
                                visualBusy = false;
                                if (!sameAd(expected)) { gate.reset(); schedule(1000); return; }
                                if (reading == null) { failed("visual-unavailable"); return; }
                                if (SystemClock.elapsedRealtime() - capturedAt > 2000) { visualOutcome("stale"); gate.reset(); schedule(1000); return; }
                                visualOutcome(reading.kind);
                                expected.kind = reading.kind;
                                consume(expected, true, capturedAt);
                            });
                        });
                    } catch (java.util.concurrent.RejectedExecutionException e) { input.recycle(); failed("visual-unavailable"); }
                } catch (Throwable e) { failed("visual-unavailable"); }
                finally {
                    if (header != null) header.recycle(); if (software != null) software.recycle(); if (hardware != null) hardware.recycle();
                    result.getHardwareBuffer().close();
                }
            }
        });
    }
    private void perform(Page expected, boolean visual, long capturedAt) {
        perform(expected, visual, capturedAt, false);
    }
    private void perform(Page expected, boolean visual, long capturedAt, boolean trial) {
        if (!AppState.enabled(this) || !awake() || !unlocked() || pending) { gate.reset(); return; }
        AccessibilityNodeInfo root = targetRoot();
        if (root == null) { gate.reset(); return; }
        Page current = read(root);
        if (!current.window.equals(expected.window) || current.windowId != expected.windowId
            || (trial ? !landscapeAttempt.canTry() || !current.landscapeBadge || !current.adBadge
                || current.window.width() <= current.window.height() || !current.kind.equals("content")
                : current.fingerprint != expected.fingerprint || (visual ? !sameAd(expected)
                    || SystemClock.elapsedRealtime() - capturedAt > 2000 : !current.kind.equals("ad-ready")))) { gate.reset(); return; }
        long now = System.currentTimeMillis();
        long last = AppState.prefs(this).getLong("last-action", 0);
        if (last != 0 && now - last < AppState.cooldown(this) * 1000L) return;
        float x = current.window.exactCenterX();
        Path path = new Path(); path.moveTo(x, current.window.top + current.window.height() * .75f);
        path.lineTo(x, current.window.top + current.window.height() * .25f);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 350)).build();
        pending = true; gate.reset();
        if (trial) {
            landscapeAttempt.attempted(SystemClock.elapsedRealtime());
            trialFallbackLogged = false;
            AppState.prefs(this).edit().putBoolean("landscape-tried", true).apply();
            AppState.log(this, "根据横屏广告标记尝试上滑一次");
        }
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                pending = false; AppState.completed(SkipService.this);
                if (trial) {
                    landscapeAttempt.attempted(SystemClock.elapsedRealtime());
                    AppState.log(SkipService.this, "横屏试滑已完成，正在检查是否退出");
                }
                if (visual) AppState.log(SkipService.this, "已完成横屏广告上滑");
                AppState.status(SkipService.this, AppState.enabled(SkipService.this) ? (trial ? "trial-checking" : "watching") : "paused"); schedule(trial ? 500 : 1200);
            }
            @Override public void onCancelled(GestureDescription g) {
                pending = false; AppState.log(SkipService.this, "上滑被系统取消"); schedule(1500);
            }
        }, handler);
        if (accepted) AppState.prefs(this).edit().putLong("last-action", now).apply();
        else { pending = false; AppState.log(this, "系统未接受上滑，已暂停自动跳过"); AppState.setEnabled(this, false); }
    }
}
