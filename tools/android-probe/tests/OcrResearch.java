package io.adpass.tests;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import io.adpass.app.HeaderOcr;
import java.util.ArrayList;
import java.util.Collections;

/** Diagnostic replay: timing excludes PNG decoding and includes mask, OCR and checks. */
public final class OcrResearch extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private int pss() { Debug.MemoryInfo info = new Debug.MemoryInfo(); Debug.getMemoryInfo(info); return info.getTotalPss(); }
    @Override public void onStart() {
        Bundle out = new Bundle(); StringBuilder log = new StringBuilder();
        int baseline = pss(), failed = 0; ArrayList<Double> warm = new ArrayList<>();
        try (HeaderOcr reader = new HeaderOcr(getTargetContext())) {
            String[] names = getTargetContext().getAssets().list("research"); boolean first = true;
            for (String name : names) {
                if (name.contains("phone-")) continue; // Compressed-video sequence is checked by OcrPhoneReplay.
                Bitmap image;
                try (java.io.InputStream in = getTargetContext().getAssets().open("research/" + name)) { image = BitmapFactory.decodeStream(in); }
                Rect region = HeaderOcr.headerBounds(new Rect(0, 0, image.getWidth(), image.getHeight()),
                    name.contains("phone-") ? new Rect(1081, 60, 1136, 89) : new Rect(2036, 92, 2114, 131),
                    java.util.Collections.singletonList(name.contains("phone-") ? new Rect(758, 44, 1038, 104) : new Rect(1534, 67, 1969, 155)));
                Bitmap crop = Bitmap.createBitmap(image, region.left, region.top, region.width(), region.height()); image.recycle();
                try {
                    for (int repeat = 0; repeat < 3; repeat++) {
                        long begin = SystemClock.elapsedRealtimeNanos(); HeaderOcr.Result result = reader.read(crop);
                        double elapsed = (SystemClock.elapsedRealtimeNanos()-begin)/1000000.0;
                        log.append(name).append(" #").append(repeat).append(" ").append(result.kind).append(" confidence=").append(result.confidence)
                            .append(" ms=").append(String.format(java.util.Locale.ROOT,"%.2f",elapsed)).append(first ? " cold-init" : " warm").append("\n");
                        if (repeat == 0) log.append("raw=").append(result.text.replace("\n", " | ")).append("\n");
                        if (!first) warm.add(elapsed); first = false;
                        if (name.startsWith("waiting-")) {
                            // A faded/uncertain countdown may be unrecognized; it must never authorize a swipe.
                            if (result.kind.equals("ad-ready")) failed++;
                        } else {
                            boolean ad = !name.startsWith("normal-");
                            if (ad != result.kind.equals("ad-ready")) failed++;
                        }
                    }
                } finally { crop.recycle(); }
            }
            Collections.sort(warm);
            if (!warm.isEmpty()) log.append("warm_count=").append(warm.size()).append(" median_ms=").append(warm.get(warm.size()/2))
                .append(" p95_ms=").append(warm.get((int)Math.ceil(warm.size()*.95)-1)).append("\n");
            log.append("process_pss_baseline_kb=").append(baseline).append(" process_pss_after_kb=").append(pss()).append("\n");
        } catch (Throwable e) { failed++; log.append(e.toString()).append("\n"); }
        log.append("RESULT: ").append(failed == 0 ? "PASS" : "FAIL").append(" failures=").append(failed).append("\n");
        out.putString("stream",log.toString()); finish(failed == 0 ? -1 : 0, out);
    }
}
