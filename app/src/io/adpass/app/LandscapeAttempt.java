package io.adpass.app;

/** One tentative swipe per ad encounter. Only a stable drama return rearms it. */
public final class LandscapeAttempt {
    private boolean attempted;
    private long settleUntil, playerSince = -1;
    public LandscapeAttempt(boolean alreadyAttempted) { attempted = alreadyAttempted; }
    public boolean canTry() { return !attempted; }
    public void attempted(long now) { attempted = true; settleUntil = now + 1800; playerSince = -1; }
    public boolean verifying(long now) { return attempted && now < settleUntil; }
    public void interruptObservation() { playerSince = -1; }
    public boolean observe(boolean adBadge, boolean dramaPlayer, long now) {
        if (adBadge || !dramaPlayer) { playerSince = -1; return false; }
        if (playerSince < 0) { playerSince = now; return false; }
        if (now - playerSince < 700 || !attempted) return false;
        attempted = false; settleUntil = 0; playerSince = -1;
        return true;
    }
}
