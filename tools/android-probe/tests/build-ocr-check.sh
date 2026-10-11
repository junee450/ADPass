#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TASK_OUT="$TASK_ROOT/.local/android/ocr-check"
TASK_BUILD=/Users/junee/Library/Android/sdk/build-tools/36.0.0
TASK_JAR=/Users/junee/Library/Android/sdk/platforms/android-34/android.jar
TASK_OCR="$TASK_ROOT/.local/android/ocr/aar"
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$TASK_OUT/classes" "$TASK_OUT/dex" "$TASK_OUT/assets/tessdata" "$TASK_OUT/native/lib"
rm -rf "$TASK_OUT/assets/research"
mkdir -p "$TASK_OUT/assets/research"
cp "$TASK_ROOT/artifacts/hongguo-landscape/fullscreen.png" "$TASK_OUT/assets/research/normal-fullscreen.png"
cp "$TASK_ROOT/artifacts/hongguo-landscape/next-title.png" "$TASK_OUT/assets/research/normal-controls.png"
cp "$TASK_ROOT/artifacts/hongguo-landscape/ad-now.png" "$TASK_OUT/assets/research/ad-original-paused.png"
for name in ad01 ad02-fresh ad03 ad05-entry ad06-fresh; do
  for phase in ad-00s ad-27s paused; do
    [[ ! -f "$TASK_ROOT/artifacts/hongguo-landscape-a11y/$name/$phase.png" ]] || cp "$TASK_ROOT/artifacts/hongguo-landscape-a11y/$name/$phase.png" "$TASK_OUT/assets/research/$name-$phase.png"
  done
done
cp "${ADSKIP_OCR_MODEL:-$TASK_ROOT/app/assets/tessdata/chi_sim.traineddata}" "$TASK_OUT/assets/tessdata/chi_sim.traineddata"
for name in ad-now fullscreen next-title; do cp "$TASK_ROOT/artifacts/hongguo-landscape/$name.png" "$TASK_OUT/assets/"; done
if [[ -f "$TASK_ROOT/artifacts/hongguo-landscape-badge/fresh-three/trial-02-before.png" ]]; then
  cp "$TASK_ROOT/artifacts/hongguo-landscape-badge/fresh-three/trial-02-before.png" "$TASK_OUT/assets/research/waiting-native-7s.png"
  cp "$TASK_ROOT/artifacts/hongguo-landscape-badge/fresh-three/trial-02-after-3200ms.png" "$TASK_OUT/assets/research/waiting-native-3s.png"
  cp "$TASK_ROOT/artifacts/hongguo-landscape-badge/countdown-later-ready.png" "$TASK_OUT/assets/research/ad-countdown-finished.png"
fi
if [[ -f "$TASK_ROOT/artifacts/hongguo-landscape-badge/app-fresh-01/ad-entry.png" ]]; then
  cp "$TASK_ROOT/artifacts/hongguo-landscape-badge/app-fresh-01/ad-entry.png" "$TASK_OUT/assets/research/waiting-native-8s.png"
  cp "$TASK_ROOT/artifacts/hongguo-landscape-badge/app-fresh-01/1791705826498-ad-waiting.png" "$TASK_OUT/assets/research/waiting-native-4s.png"
  cp "$TASK_ROOT/artifacts/hongguo-landscape-badge/app-fresh-01/1791705832001-ad-waiting.png" "$TASK_OUT/assets/research/ad-swisse-popup.png"
  cp "$TASK_ROOT/artifacts/hongguo-landscape-badge/app-fresh-01/1791705842481-ad-waiting.png" "$TASK_OUT/assets/research/ad-swisse-ready-late.png"
fi
if [[ -d "$TASK_ROOT/artifacts/hongguo-device-countdown/fixtures" ]]; then
  cp "$TASK_ROOT/artifacts/hongguo-device-countdown/fixtures/"*.png "$TASK_OUT/assets/research/"
fi
cat > "$TASK_OUT/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.adpass.ocrcheck"><uses-sdk android:minSdkVersion="30" android:targetSdkVersion="34"/><application android:label="ADSkip OCR 验收" android:extractNativeLibs="true"/><instrumentation android:name="io.adpass.tests.OcrPhoneReplay" android:targetPackage="io.adpass.ocrcheck"/><instrumentation android:name="io.adpass.tests.OcrCheck" android:targetPackage="io.adpass.ocrcheck"/><instrumentation android:name="io.adpass.tests.OcrResearch" android:targetPackage="io.adpass.ocrcheck"/></manifest>
EOF
"$TASK_BUILD/aapt2" link -I "$TASK_JAR" -A "$TASK_OUT/assets" --manifest "$TASK_OUT/AndroidManifest.xml" -o "$TASK_OUT/unsigned.apk"
javac --release 11 -classpath "$TASK_JAR:$TASK_OCR/classes.jar" -d "$TASK_OUT/classes" "$TASK_ROOT/tools/android-probe/tests/OcrCheck.java" "$TASK_ROOT/tools/android-probe/tests/OcrResearch.java" "$TASK_ROOT/tools/android-probe/tests/OcrPhoneReplay.java" "$TASK_ROOT/app/src/io/adpass/app/SkipGate.java" "$TASK_ROOT/app/src/io/adpass/app/HeaderOcr.java" "$TASK_ROOT/app/src/io/adpass/app/VisualRules.java"
jar cf "$TASK_OUT/classes.jar" -C "$TASK_OUT/classes" .
"$TASK_BUILD/d8" --lib "$TASK_JAR" --min-api 30 --output "$TASK_OUT/dex" "$TASK_OUT/classes.jar" "$TASK_OCR/classes.jar"
zip -q -j "$TASK_OUT/unsigned.apk" "$TASK_OUT/dex/classes.dex"
cp -R "$TASK_OCR/jni/arm64-v8a" "$TASK_OUT/native/lib/"
(cd "$TASK_OUT/native" && zip -q -r "$TASK_OUT/unsigned.apk" lib)
"$TASK_BUILD/zipalign" -f 4 "$TASK_OUT/unsigned.apk" "$TASK_OUT/aligned.apk"
"$TASK_BUILD/apksigner" sign --ks "$TASK_ROOT/.local/android/app-build/preview.keystore" --ks-pass pass:android --key-pass pass:android --out "$TASK_OUT/check.apk" "$TASK_OUT/aligned.apk"
