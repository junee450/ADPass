package io.adpass.app;

/** Whole-line whitelist; countdowns, subtitles and fuzzy matches cannot authorize a swipe. */
public final class VisualRules {
    public static String detect(String text, int confidence) {
        if (text == null || confidence < 80) return "content";
        String flat = text.replaceAll("\\s+", "");
        if (flat.matches(".*[0-9０-９].*") || flat.contains("倒计时") || flat.contains("后可") || flat.contains("即将")) return "ad-waiting";
        for (String line : text.split("\\r?\\n")) {
            String normalized = line.replaceAll("[\\s^↑《》<>»·•]", "");
            if (normalized.matches("(?:上滑|向上滑动)继续(?:观看|看)(?:剧集|短剧)")) return "ad-ready";
        }
        return "content";
    }
    private VisualRules() { }
}
