package io.adpass.probe;

import java.util.List;

/** Recognizes UI controls, never arbitrary video subtitles or a drama title. */
public final class PageRules {
    public static final class Cue {
        public final String text, id;
        public Cue(String text, String id) {
            this.text = text.replaceAll("\\s+", "");
            this.id = id == null ? "" : id;
        }
    }
    public static String detect(List<Cue> cues) {
        boolean adReady = false, adWaiting = false;
        boolean trustedHint = false, adBadge = false;
        boolean liveBadge = false, liveEntry = false, trustedLiveEntry = false;
        for (Cue c : cues) {
            boolean hint = c.id.equals("com.phoenix.read:id/aq8");
            if (c.text.equals("广告")) adBadge = true;
            if (c.text.matches("(?:(?:上滑|向上滑动)继续(?:观看|看)|继续上滑观看)(?:短剧|剧集)?")) {
                adReady = true;
                if (hint) trustedHint = true;
            }
            if (hint && (c.text.matches(".*[0-9]+(?:秒|s)后.*") || c.text.contains("即将进入广告")
                || c.text.equals("倒计时结束后即可滑动"))) adWaiting = true;
            if (c.text.equals("直播中") || c.text.equals("正在直播")) liveBadge = true;
            if (c.text.matches("(?:点击)?进入直播(?:间)?") || c.text.equals("点击观看直播")
                || c.text.equals("去直播间抢购")) {
                liveEntry = true;
                // This exact ID and label are linked in this APK's res/l/bnp.xml.
                // Its runtime accessibility exposure still needs a live-card fixture.
                if (c.id.equals("com.phoenix.read:id/jjy") && c.text.equals("点击进入直播间")) trustedLiveEntry = true;
            }
        }
        // A countdown must never be mistaken for a currently available skip.
        if (adWaiting) return "ad-waiting";
        if (adReady && (trustedHint || adBadge)) return "ad-ready";
        if (trustedLiveEntry || (liveBadge && liveEntry)) return "live-preview";
        return "content";
    }
    private PageRules() { }
}
