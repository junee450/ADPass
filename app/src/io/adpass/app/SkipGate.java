package io.adpass.app;

/** Two independent observations, interrupted by content, pause or rotation. */
public final class SkipGate {
    private String candidate = "";
    private long firstSeen;
    public void reset() { candidate = ""; firstSeen = 0; }
    public boolean ready(String key, long now, long lastAction, long cooldown) {
        if (key == null || key.isEmpty()) { reset(); return false; }
        if (!candidate.equals(key)) { candidate = key; firstSeen = now; return false; }
        return now - firstSeen >= 900 && (lastAction == 0 || now - lastAction >= cooldown);
    }
    public static boolean eligible(boolean enabled, boolean target, boolean awake,
                                   boolean unlocked, boolean pending, boolean landscape, String kind) {
        return enabled && target && awake && unlocked && !pending && "ad-ready".equals(kind);
    }
}
