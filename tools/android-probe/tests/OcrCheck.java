package io.adpass.tests;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import io.adpass.app.HeaderOcr;

public final class OcrCheck extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); StringBuilder log = new StringBuilder(); int failed = 0;
        try (HeaderOcr reader = new HeaderOcr(getTargetContext())) {
            String[] fixtures = {"ad-now", "fullscreen", "next-title"};
            for (String name : fixtures) {
                Bitmap image;
                try (java.io.InputStream in = getTargetContext().getAssets().open(name + ".png")) { image = BitmapFactory.decodeStream(in); }
                Rect region = HeaderOcr.headerBounds(new Rect(0, 0, image.getWidth(), image.getHeight()),
                    name.contains("phone-") ? new Rect(1081, 60, 1136, 89) : new Rect(2036, 92, 2114, 131),
                    java.util.Collections.singletonList(name.contains("phone-") ? new Rect(758, 44, 1038, 104) : new Rect(1534, 67, 1969, 155)));
                Bitmap crop = Bitmap.createBitmap(image, region.left, region.top, region.width(), region.height());
                image.recycle();
                HeaderOcr.Result read;
                try { read = reader.read(crop); } finally { crop.recycle(); }
                String expected = name.equals("ad-now") ? "ad-ready" : "content";
                log.append(name).append(": ").append(read.kind).append(" confidence=").append(read.confidence).append(" text=").append(read.text.replace('\n','|')).append('\n');
                if (!read.kind.equals(expected)) failed++;
            }
        } catch (Throwable e) { failed++; log.append(e.toString()); }
        log.append("RESULT: ").append(failed == 0 ? "PASS" : "FAIL").append(" (3 OCR fixtures, failures=").append(failed).append(")\n");
        result.putString("stream", log.toString()); finish(failed==0 ? -1 : 0, result);
    }
}
