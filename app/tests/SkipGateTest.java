package io.adpass.app;

/** Checks event bursts, interrupted confirmation and safety gates. */
public final class SkipGateTest {
    private static int checks;
    private static void expect(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        SkipGate g = new SkipGate();
        expect(!g.ready("portrait:1", 1000, 0, 8000), "first sighting cannot swipe");
        expect(!g.ready("portrait:1", 1100, 0, 8000), "event burst cannot substitute for confirmation");
        expect(!g.ready("portrait:1", 1899, 0, 8000), "900ms confirmation interval enforced");
        expect(g.ready("portrait:1", 1900, 0, 8000), "stable ready ad confirms");
        g.reset(); // countdown, ordinary content, pause and lock all interrupt confirmation
        expect(!g.ready("portrait:1", 2200, 0, 8000), "interrupted candidate must confirm again");
        expect(!g.ready("letterbox:1", 3200, 0, 8000), "rotation restarts confirmation");
        expect(g.ready("letterbox:1", 4100, 0, 8000), "letterbox may confirm after delay");
        g.reset();
        expect(!g.ready("portrait:2", 5000, 4000, 8000), "new ad first observation");
        expect(!g.ready("portrait:2", 11999, 4000, 8000), "cooldown blocks repeated swipe");
        expect(g.ready("portrait:2", 12000, 4000, 8000), "cooldown boundary releases candidate");
        expect(!g.ready(null, 13000, 0, 8000), "missing candidate clears gate");
        expect(!g.ready("portrait:2", 14000, 0, 8000), "missing candidate resets confirmation");
        expect(SkipGate.eligible(true, true, true, true, false, false, "ad-ready"), "portrait ready ad eligible");
        expect(!SkipGate.eligible(false, true, true, true, false, false, "ad-ready"), "master pause");
        expect(!SkipGate.eligible(true, false, true, true, false, false, "ad-ready"), "other app excluded");
        expect(!SkipGate.eligible(true, true, false, true, false, false, "ad-ready"), "screen off excluded");
        expect(!SkipGate.eligible(true, true, true, false, false, false, "ad-ready"), "locked screen excluded");
        expect(!SkipGate.eligible(true, true, true, true, true, false, "ad-ready"), "overlapping gesture excluded");
        expect(SkipGate.eligible(true, true, true, true, false, true, "ad-ready"), "native landscape confirmed ad eligible");
        expect(!SkipGate.eligible(true, true, true, true, false, true, "content"), "ordinary native landscape excluded");
        for (String kind : new String[]{"live-preview", "ad-waiting", "content", "", null})
            expect(!SkipGate.eligible(true, true, true, true, false, false, kind), "excluded page: " + kind);
        System.out.println("PASS: " + checks + " confirmation and action-gate checks");
    }
}
