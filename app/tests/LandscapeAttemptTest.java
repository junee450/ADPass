package io.adpass.app;

public final class LandscapeAttemptTest {
    private static int checks;
    private static void expect(boolean value, String name) {
        checks++; if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        LandscapeAttempt attempt = new LandscapeAttempt(false);
        expect(attempt.canTry(), "new encounter permits one tentative swipe");
        attempt.attempted(1000);
        expect(!attempt.canTry(), "attempt consumed before system callback");
        expect(attempt.verifying(2799), "allow UI to settle instead of immediately retrying");
        expect(!attempt.verifying(2800), "still present can fall back after settle window");
        expect(!attempt.observe(true, false, 3000), "remaining advertisement is not success");
        expect(!attempt.canTry(), "failure must not rearm another blind swipe");
        expect(!attempt.observe(false, false, 4000), "badge disappearance alone is insufficient");
        expect(!attempt.observe(false, true, 4100), "first drama observation not sufficient");
        expect(!attempt.observe(false, true, 4799), "stable drama interval required");
        expect(!attempt.observe(true, true, 4800), "stale drama together with badge interrupts confirmation");
        expect(!attempt.observe(false, true, 5000), "confirmation restarts after badge returns");
        attempt.interruptObservation();
        expect(!attempt.observe(false, true, 7000), "pause/other-app gap cannot complete confirmation");
        expect(!attempt.observe(false, true, 7699), "new stable interval after interruption");
        expect(attempt.observe(false, true, 7700), "confirmed drama return clears one-attempt latch");
        expect(attempt.canTry(), "next actual encounter may attempt once");
        expect(!attempt.observe(false, true, 9000), "normal playback cannot produce repeated success logs");
        attempt.attempted(10000);
        attempt.interruptObservation();
        expect(!attempt.canTry(), "pause or preference changes cannot rearm same ad");
        LandscapeAttempt restarted = new LandscapeAttempt(true);
        expect(!restarted.canTry(), "persisted latch survives service recreation");
        expect(!restarted.verifying(1), "recreated service can fall back without another attempt");
        expect(!restarted.observe(false, true, 2000), "recreated service still confirms actual drama");
        expect(restarted.observe(false, true, 2700), "stable drama also clears persisted encounter");
        expect(restarted.canTry(), "next encounter after recreation allowed");
        System.out.println("PASS: " + checks + " one-attempt and return-verification checks");
    }
}
