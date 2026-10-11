package io.adpass.app;

/** Reject uncertain, incomplete, countdown and embedded text before authorizing OCR. */
public final class VisualRulesTest {
    private static int checks;
    private static void expect(String text, int confidence, String kind) {
        checks++;
        String actual = VisualRules.detect(text, confidence);
        if (!actual.equals(kind)) throw new AssertionError(text + " -> " + actual);
    }
    public static void main(String[] args) {
        expect("上滑继续观看剧集", 87, "ad-ready");
        expect("上 滑 继续 观看 剧 集", 80, "ad-ready");
        expect("向上滑动继续看短剧", 91, "ad-ready");
        expect("↑ 上滑继续观看剧集\n广告", 95, "ad-ready");
        expect("上滑继续观看剧集", 79, "content");
        expect("上清继续观看剧集", 99, "content");
        expect("不要上滑继续观看剧集", 99, "content");
        expect("入上滑继续观看剧集", 99, "content");
        expect("上滑继续观看剧集，领优惠券", 99, "content");
        expect("上滑继续观看", 99, "content");
        expect("上滑继续\n观看剧集", 99, "content");
        expect("5秒后上滑继续观看剧集", 95, "ad-waiting");
        expect("上滑继续观看剧集\n５秒", 95, "ad-waiting");
        expect("上滑继续观看剧集\n倒计时", 95, "ad-waiting");
        expect("上滑继续观看剧集\n即将进入广告", 95, "ad-waiting");
        expect("上滑继续观看剧集\n后可滑动", 95, "ad-waiting");
        expect("广告", 95, "content");
        expect("点击进入直播间", 95, "content");
        expect("第6集", 99, "ad-waiting");
        expect("这乡里乡亲的", 95, "content");
        expect("", 0, "content");
        expect(null, 99, "content");
        System.out.println("PASS: " + checks + " visual prompt and countdown checks");
    }
}
