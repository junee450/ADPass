package io.adpass.probe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Disposable, offline probe for one fresh emulator. Auto swipe is off by default. */
public class ProbeService extends AccessibilityService {
    private static final String TARGET = "com.phoenix.read";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean autoSwipe = false;
    private int hits = 0;
    private long lastSwipe = -10000;
    private long samples = 0;
    private String previous = "";
    private String previousCandidate = "";
    private boolean gesturePending = false;
    private long researchUntil;
    private boolean researchAd;
    private int researchAds;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            sample();
            handler.postDelayed(this, SystemClock.elapsedRealtime() < researchUntil ? 250 : 1000);
        }
    };
    @Override protected void onServiceConnected() {
        // Research probe only: force fresh provider reads to exclude stale-cache artifacts.
        if (Build.VERSION.SDK_INT >= 33) setCacheEnabled(false);
        append("events.ndjson", "{\"type\":\"connected\",\"auto\":false}");
        handler.post(poll);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (SystemClock.elapsedRealtime() >= researchUntil || !TARGET.contentEquals(e.getPackageName() == null ? "" : e.getPackageName())) return;
        try {
            JSONObject v = new JSONObject(); v.put("time", System.currentTimeMillis());
            v.put("type", AccessibilityEvent.eventTypeToString(e.getEventType()));
            v.put("text", e.getText().toString()); v.put("description", e.getContentDescription());
            v.put("class", e.getClassName()); v.put("changes", e.getContentChangeTypes());
            AccessibilityNodeInfo source = e.getSource(); JSONArray nodes = new JSONArray();
            if (source != null) collect(source, nodes, 60, "source");
            v.put("source", nodes); append("research-events.ndjson", v.toString());
        } catch (Exception ignored) { }
    }
    @Override public void onInterrupt() { hits = 0; }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    private boolean targetActive() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root != null && TARGET.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())
            && ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()
            && !((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
    }
    private void append(String name, String line) {
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), name), true)) {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { android.util.Log.e("ADPassProbe", "write", e); }
    }
    private void event(String type, String detail) {
        JSONObject v = new JSONObject();
        try { v.put("time", System.currentTimeMillis()); v.put("type", type); v.put("detail", detail); }
        catch(Exception ignored) { }
        append("events.ndjson", v.toString());
    }
    private void collect(AccessibilityNodeInfo node, JSONArray nodes, int depth, String path) throws Exception {
        if (node == null || depth > 60 || nodes.length() >= 1500) return;
        Rect b = new Rect(); node.getBoundsInScreen(b);
        CharSequence t = node.getText(), d = node.getContentDescription();
        if (SystemClock.elapsedRealtime() < researchUntil || (t != null && t.length() > 0) || (d != null && d.length() > 0) || node.getViewIdResourceName() != null) {
            JSONObject v = new JSONObject();
            v.put("text", t == null ? "" : t.toString());
            v.put("description", d == null ? "" : d.toString());
            v.put("id", node.getViewIdResourceName());
            v.put("bounds", new JSONArray(new int[]{b.left, b.top, b.right, b.bottom}));
            v.put("visible", node.isVisibleToUser());
            v.put("class", String.valueOf(node.getClassName()));
            v.put("clickable", node.isClickable());
            v.put("path", path); v.put("children", node.getChildCount());
            v.put("enabled", node.isEnabled()); v.put("selected", node.isSelected());
            v.put("hint", node.getHintText()); v.put("state", node.getStateDescription());
            v.put("tooltip", node.getTooltipText()); v.put("actions", node.getActions());
            v.put("important", node.isImportantForAccessibility());
            nodes.put(v);
        }
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), nodes, depth + 1, path + "/" + i);
    }
    private void sample() {
        try {
            File cmd = new File(getFilesDir(), "command.txt");
            String command = "";
            if (cmd.exists()) {
                command = new String(Files.readAllBytes(cmd.toPath()), StandardCharsets.UTF_8).trim();
                cmd.delete();
            }
            if (command.equals("auto-on")) { autoSwipe = true; hits = 0; event("auto", "on"); }
            if (command.equals("auto-off")) { autoSwipe = false; hits = 0; event("auto", "off"); }
            if (command.equals("research-on")) { autoSwipe = false; hits = 0; researchUntil = SystemClock.elapsedRealtime() + 180000; event("research", "on:250ms:180s:auto-off"); append("research-events.ndjson", "{\"time\":" + System.currentTimeMillis() + ",\"type\":\"research-start\"}"); }
            if (command.equals("research-off")) { researchUntil = 0; event("research", "off"); }
            if (!targetActive()) { hits = 0; return; }
            long start = SystemClock.elapsedRealtimeNanos();
            AccessibilityNodeInfo root = getRootInActiveWindow();
            root.refresh();
            JSONArray nodes = new JSONArray(); collect(root, nodes, 0, "root");
            JSONArray queries = new JSONArray();
            if (SystemClock.elapsedRealtime() < researchUntil) {
                for (String query : new String[]{"上滑", "继续", "秒", "跳过"})
                    for (AccessibilityNodeInfo found : root.findAccessibilityNodeInfosByText(query)) collect(found, queries, 0, "text:" + query);
                for (String id : new String[]{"aq8", "ar1", "k8", "jjy"})
                    for (AccessibilityNodeInfo found : root.findAccessibilityNodeInfosByViewId(TARGET + ":id/" + id)) collect(found, queries, 0, "id:" + id);
            }
            String signature = nodes.toString();
            Rect window = new Rect(); root.getBoundsInScreen(window);
            List<PageRules.Cue> cues = new ArrayList<>();
            for (int i=0; i<nodes.length(); i++) {
                JSONObject n = nodes.getJSONObject(i);
                JSONArray bounds = n.getJSONArray("bounds");
                Rect b = new Rect(bounds.getInt(0), bounds.getInt(1), bounds.getInt(2), bounds.getInt(3));
                if (n.getBoolean("visible") && !b.isEmpty() && window.contains(b)) {
                    cues.add(new PageRules.Cue(n.getString("text") + n.getString("description"), n.optString("id")));
                }
            }
            String kind = PageRules.detect(cues);
            boolean badge = false;
            for (PageRules.Cue cue : cues) if (cue.text.equals("广告")) badge = true;
            if (SystemClock.elapsedRealtime() < researchUntil && badge && !researchAd) {
                final int serial = ++researchAds;
                event("research-ad-enter", "serial=" + serial);
                for (int delay : new int[]{0, 600, 1200, 2400, 4000, 7000, 11000}) {
                    final int offset = delay;
                    handler.postDelayed(() -> {
                        if (researchAd && researchAds == serial && SystemClock.elapsedRealtime() < researchUntil)
                            capture("research-ad-" + serial + "-" + offset + "ms");
                    }, delay);
                }
            }
            researchAd = badge;
            boolean match = kind.equals("ad-ready") || kind.equals("live-preview");
            String candidate = kind + ":" + window.toShortString() + ":" + root.getWindowId();
            samples++;
            JSONObject snapshot = new JSONObject();
            snapshot.put("time", System.currentTimeMillis()); snapshot.put("sample", samples);
            snapshot.put("package", TARGET); snapshot.put("matched", match); snapshot.put("auto", autoSwipe);
            snapshot.put("kind", kind);
            snapshot.put("window", new JSONArray(new int[]{window.left, window.top, window.right, window.bottom}));
            snapshot.put("readMs", (SystemClock.elapsedRealtimeNanos() - start)/1000000.0);
            snapshot.put("nodes", nodes);
            snapshot.put("queries", queries);
            snapshot.put("windowId", root.getWindowId());
            if (Build.VERSION.SDK_INT >= 33) snapshot.put("cacheEnabled", isCacheEnabled());
            if (SystemClock.elapsedRealtime() < researchUntil) append("research-samples.ndjson", snapshot.toString());
            Files.write(new File(getFilesDir(), "latest.json").toPath(), snapshot.toString(2).getBytes(StandardCharsets.UTF_8));
            if (!signature.equals(previous) || match || samples%15==0) append("samples.ndjson", snapshot.toString());
            previous = signature;
            if (command.equals("capture")) capture("manual");
            if (command.equals("swipe")) swipe("manual-playback-test", window);
            hits = match ? (candidate.equals(previousCandidate) ? hits + 1 : 1) : 0;
            previousCandidate = candidate;
            if (match && hits == 1) capture(kind + "-candidate");
            if (autoSwipe && hits >= 2 && !gesturePending && SystemClock.elapsedRealtime() - lastSwipe >= 8000) {
                swipe(kind + "-two-hits", window); hits = 0;
            }
        } catch(Exception e) { event("error", e.toString()); }
    }
    private void capture(String label) {
        if (!targetActive()) return;
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                Bitmap hardware = null, software = null;
                try {
                    if (!targetActive()) { event("screenshot-discarded", "foreground-changed"); return; }
                    hardware = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                    if (hardware == null) throw new IllegalStateException("null bitmap");
                    software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    String name = label + "-" + System.currentTimeMillis() + ".png";
                    try(FileOutputStream out = new FileOutputStream(new File(getFilesDir(), name))) {
                        software.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    event("screenshot", name);
                } catch(Exception e) { event("screenshot-error", e.toString()); }
                finally {
                    if(software != null) software.recycle();
                    if(hardware != null) hardware.recycle();
                    result.getHardwareBuffer().close();
                }
            }
            @Override public void onFailure(int errorCode) { event("screenshot-failure", "code=" + errorCode); }
        });
    }
    private void swipe(String reason, Rect window) {
        if (!targetActive()) return;
        AccessibilityNodeInfo current = getRootInActiveWindow();
        if (current == null) return;
        Rect currentWindow = new Rect(); current.getBoundsInScreen(currentWindow);
        if (window.isEmpty() || !window.equals(currentWindow)) { hits = 0; return; }
        lastSwipe = SystemClock.elapsedRealtime();
        float x = window.exactCenterX();
        float startY = window.top + window.height()*.75f;
        float endY = window.top + window.height()*.25f;
        Path path = new Path(); path.moveTo(x,startY); path.lineTo(x,endY);
        GestureDescription g = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 350)).build();
        boolean accepted = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                gesturePending = false;
                event("gesture-completed", reason);
                handler.postDelayed(() -> capture("after-gesture"), 1200);
            }
            @Override public void onCancelled(GestureDescription gesture) { gesturePending = false; event("gesture-cancelled", reason); }
        }, handler);
        gesturePending = accepted;
        event("gesture-dispatched", reason + ":accepted=" + accepted + ":window=" + window.toShortString()
            + ":path=" + x + "," + startY + "->" + x + "," + endY);
    }
}
