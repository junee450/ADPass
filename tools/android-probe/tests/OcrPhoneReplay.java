package io.adpass.tests;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import io.adpass.app.HeaderOcr;
import io.adpass.app.SkipGate;
import java.util.Collections;

/** Compressed video may remain uncertain; require safe countdowns and two consecutive ready frames. */
public final class OcrPhoneReplay extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        StringBuilder log = new StringBuilder(); Bundle out = new Bundle(); int failed = 0;
        try (HeaderOcr reader = new HeaderOcr(getTargetContext())) {
            Rect window = new Rect(0,0,1280,576), badge = new Rect(1081,60,1136,89), hint = new Rect(758,44,1038,104);
            // Pixel annotations from video, not an accessibility dump from the user's phone.
            Rect nativeRegion = HeaderOcr.headerBounds(window,badge,Collections.singletonList(hint));
            Rect insetRegion = HeaderOcr.headerBounds(new Rect(134,0,1146,576),badge,Collections.singletonList(hint));
            if (!nativeRegion.equals(hint) || !insetRegion.equals(hint)) throw new AssertionError("Letterbox changed crop");
            Rect shiftedWindow = new Rect(window); shiftedWindow.offset(50,20);
            Rect shiftedBadge = new Rect(badge); shiftedBadge.offset(50,20);
            Rect shiftedHint = new Rect(hint); shiftedHint.offset(50,20);
            if (!HeaderOcr.headerBounds(shiftedWindow,shiftedBadge,Collections.singletonList(shiftedHint)).equals(shiftedHint))
                throw new AssertionError("Display offset changed crop");
            for (int mode=0; mode<3; mode++) {
                String modeName = mode==0 ? "legacy-fixed" : mode==1 ? "badge-relative" : "native-container";
                SkipGate gate = new SkipGate(); int readyFrames=0, actionSecond=-1;
                Rect region = mode==0 ? new Rect(832,28,1088,92) : mode==1 ? HeaderOcr.headerBounds(window,badge) : nativeRegion;
                for (int second=18; second<=32; second++) {
                    String name = (second<25 ? "waiting-" : "ad-") + "phone-" + String.format(java.util.Locale.ROOT,"%03d",second) + ".png";
                    HeaderOcr.Result result = read(reader,name,region);
                    log.append(modeName).append(" second=").append(second).append(" kind=").append(result.kind).append(" confidence=").append(result.confidence).append('\n');
                    if (second<25 && result.kind.equals("ad-ready")) failed++;
                    if (result.kind.equals("ad-ready")) {
                        readyFrames++;
                        if (actionSecond<0 && gate.ready("same-ad",second*1000L,18000L,8000L)) actionSecond=second;
                    } else gate.reset();
                }
                log.append(modeName).append(" ready_frames=").append(readyFrames).append(" simulated_retry_second=").append(actionSecond).append('\n');
                if ((mode==0 && actionSecond>=0) || (mode>0 && actionSecond<25)) failed++;
                for (int second : new int[]{12,17}) {
                    HeaderOcr.Result result=read(reader,"normal-phone-"+String.format(java.util.Locale.ROOT,"%03d",second)+".png",region);
                    log.append(modeName).append(" normal=").append(second).append(" kind=").append(result.kind).append('\n');
                    if (result.kind.equals("ad-ready")) failed++;
                }
            }
        } catch (Throwable e) { failed++; log.append(e.toString()).append('\n'); }
        log.append("RESULT: ").append(failed==0?"PASS":"FAIL").append(" failures=").append(failed).append('\n');
        out.putString("stream",log.toString()); finish(failed==0?-1:0,out);
    }
    private HeaderOcr.Result read(HeaderOcr reader,String name,Rect region) throws Exception {
        Bitmap image;
        try (java.io.InputStream in=getTargetContext().getAssets().open("research/"+name)) { image=BitmapFactory.decodeStream(in); }
        Bitmap crop=Bitmap.createBitmap(image,region.left,region.top,region.width(),region.height()); image.recycle();
        try { return reader.read(crop); } finally { crop.recycle(); }
    }
}
