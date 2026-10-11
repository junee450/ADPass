package io.adpass.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.ResultIterator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Single-worker offline OCR. No screenshot or recognized page text is persisted. */
public final class HeaderOcr implements AutoCloseable {
    private final Context context;
    private TessBaseAPI tess;
    public HeaderOcr(Context context) { this.context = context.getApplicationContext(); }
    /** Locate the hint beside the accessible ad label, independently of black side bars/insets.
     * Thirteen label heights cover the arrow and the longest verified countdown line.
     * The narrow vertical band and gap before the label exclude video/coupon digits. */
    public static Rect headerBounds(Rect window, Rect badge) {
        if (window.isEmpty() || badge.isEmpty() || !window.contains(badge)) return new Rect();
        int h = badge.height();
        Rect region = new Rect(badge.left - h * 13, badge.top - (int)Math.ceil(h * 1.1),
            badge.left - (int)Math.ceil(h * 1.1), badge.bottom + (int)Math.ceil(h * .7));
        return region.intersect(window) ? region : new Rect();
    }
    /** Prefer the empty native hint container when accessibility supplies its geometry. */
    public static Rect headerBounds(Rect window, Rect badge, java.util.List<Rect> emptyViews) {
        Rect best = null; double bestScore = Double.MAX_VALUE;
        for (Rect b : emptyViews) {
            int h = badge.height(), gap = badge.left - b.right;
            if (h <= 0 || !window.contains(b) || b.width() < h*4 || b.width() > h*14
                || b.height() < h || b.height() > h*3 || gap < h*.3 || gap > h*3
                || Math.abs(b.exactCenterY()-badge.exactCenterY()) > h) continue;
            double score = gap + Math.abs(b.exactCenterY()-badge.exactCenterY());
            if (score < bestScore) { best = b; bestScore = score; }
        }
        return best == null ? headerBounds(window, badge) : new Rect(best);
    }
    private void init() throws Exception {
        if (tess != null) return;
        File base = new File(context.getFilesDir(), "ocr-best-v1"), dir = new File(base, "tessdata");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("OCR model directory");
        File model = new File(dir, "chi_sim.traineddata");
        if (!model.isFile() || model.length() < 100000) {
            File temporary = new File(dir, "chi_sim.tmp");
            try (InputStream in = context.getAssets().open("tessdata/chi_sim.traineddata"); FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] bytes = new byte[8192]; int count;
                while ((count = in.read(bytes)) > 0) out.write(bytes, 0, count);
            }
            if (!temporary.renameTo(model)) throw new IllegalStateException("OCR model installation");
        }
        TessBaseAPI instance = new TessBaseAPI();
        if (!instance.init(base.getAbsolutePath(), "chi_sim", TessBaseAPI.OEM_LSTM_ONLY)) { instance.recycle(); throw new IllegalStateException("OCR initialization"); }
        instance.setPageSegMode(11); tess = instance;
    }
    public static final class Result {
        public final String text, kind;
        public final int confidence;
        Result(String text, int confidence, String kind) { this.text = text; this.confidence = confidence; this.kind = kind; }
    }
    /** The caller supplies only the upper-right header crop, never a whole-screen image. */
    public Result read(Bitmap header) throws Exception {
        init();
        // The verified overlay has white text on a dark translucent panel.
        // Isolate white glyphs from the video background, then enlarge small type.
        int width = header.getWidth(), height = header.getHeight();
        int[] pixels = new int[width * height]; header.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            int r = (pixels[i] >> 16) & 255, g = (pixels[i] >> 8) & 255, b = pixels[i] & 255;
            pixels[i] = Math.min(r, Math.min(g, b)) > 180 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 35 ? 0xff000000 : 0xffffffff;
        }
        Bitmap mask = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        Bitmap enlarged = Bitmap.createScaledBitmap(mask, width * 2, height * 2, true); mask.recycle();
        try {
            tess.setImage(enlarged); String raw = tess.getUTF8Text();
            // Never remove an uncertain countdown digit while filtering a small icon.
            if (VisualRules.detect(raw, 100).equals("ad-waiting")) return new Result(raw, tess.meanConfidence(), "ad-waiting");
            ResultIterator iterator = tess.getResultIterator();
            if (iterator != null) {
                try {
                    iterator.begin(); StringBuilder line = new StringBuilder(); int confidence = 100;
                    do {
                        if (iterator.isAtBeginningOf(2) && line.length() > 0) {
                            if (VisualRules.detect(line.toString(), confidence).equals("ad-ready")) return new Result(raw, confidence, "ad-ready");
                            line.setLength(0); confidence = 100;
                        }
                        String word = iterator.getUTF8Text(3); if (word == null || word.isEmpty()) continue;
                        float score = iterator.confidence(3);
                        int[] box = iterator.getBoundingBox(3), lineBox = iterator.getBoundingBox(2);
                        // Ignore only low-confidence, small glyphs, such as the up-chevron.
                        // Uncertain full-height words remain and block a whole-line match.
                        if (score < 80 && box[3]-box[1] < (lineBox[3]-lineBox[1])*.75f) continue;
                        line.append(word); confidence = Math.min(confidence, (int)score);
                    } while (iterator.next(3));
                    if (VisualRules.detect(line.toString(), confidence).equals("ad-ready")) return new Result(raw, confidence, "ad-ready");
                } finally { iterator.delete(); }
            }
            return new Result(raw, tess.meanConfidence(), "content");
        }
        finally { tess.clear(); enlarged.recycle(); }
    }
    @Override public void close() { if (tess != null) { tess.recycle(); tess = null; } }
}
