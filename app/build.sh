#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_SRC="$TASK_ROOT/app"
TASK_OUT="$TASK_ROOT/.local/android/app-build"
TASK_SDK="${ADPASS_ANDROID_SDK:-/Users/junee/Library/Android/sdk}"
TASK_BUILD="$TASK_SDK/build-tools/36.0.0"
TASK_JAR="$TASK_SDK/platforms/android-34/android.jar"
TASK_OCR="$TASK_ROOT/.local/android/ocr/aar"
[[ -f "$TASK_OCR/classes.jar" ]] || { print -u2 'Run ./app/prepare-ocr.sh once before building.'; exit 1; }
[[ "$(shasum -a 256 "$TASK_SRC/assets/tessdata/chi_sim.traineddata" | cut -d ' ' -f 1)" == 4fef2d1306c8e87616d4d3e4c6c67faf5d44be3342290cf8f2f0f6e3aa7e735b ]] || { print -u2 'OCR model checksum mismatch'; exit 1; }
export JAVA_HOME="${ADPASS_JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
TASK_MANIFEST="$TASK_SRC/AndroidManifest.xml"
TASK_APK="$TASK_ROOT/dist/ADSkip-0.2.2.apk"
if [[ "${ADSKIP_TEST_BUILD:-0}" == 1 ]]; then
  # Private device-test variant, never the distributable APK.
  mkdir -p "$TASK_OUT"
  sed 's/android:debuggable="false"/android:debuggable="true"/' "$TASK_MANIFEST" > "$TASK_OUT/test-manifest.xml"
  TASK_MANIFEST="$TASK_OUT/test-manifest.xml"
  TASK_APK="$TASK_OUT/test.apk"
fi
rm -rf "$TASK_OUT/classes" "$TASK_OUT/dex" "$TASK_OUT/res" "$TASK_OUT/gen" "$TASK_OUT/native"
mkdir -p "$TASK_OUT/classes" "$TASK_OUT/dex" "$TASK_OUT/res" "$TASK_OUT/gen" "$TASK_ROOT/dist"
"$TASK_BUILD/aapt2" compile --dir "$TASK_SRC/res" -o "$TASK_OUT/res"
"$TASK_BUILD/aapt2" link -I "$TASK_JAR" -A "$TASK_SRC/assets" --manifest "$TASK_MANIFEST" --java "$TASK_OUT/gen" -o "$TASK_OUT/unsigned.apk" "$TASK_OUT/res/"*.flat
javac --release 11 -classpath "$TASK_JAR:$TASK_OCR/classes.jar" -d "$TASK_OUT/classes" "$TASK_SRC/src/io/adpass/app/"*.java "$TASK_OUT/gen/io/adpass/app/R.java" "$TASK_ROOT/tools/android-probe/src/io/adpass/probe/PageRules.java"
jar cf "$TASK_OUT/classes.jar" -C "$TASK_OUT/classes" .
"$TASK_BUILD/d8" --lib "$TASK_JAR" --min-api 26 --output "$TASK_OUT/dex" "$TASK_OUT/classes.jar" "$TASK_OCR/classes.jar"
zip -q -j "$TASK_OUT/unsigned.apk" "$TASK_OUT/dex/classes.dex"
mkdir -p "$TASK_OUT/native/lib"
cp -R "$TASK_OCR/jni/"* "$TASK_OUT/native/lib/"
(cd "$TASK_OUT/native" && zip -q -r "$TASK_OUT/unsigned.apk" lib)
if [[ ! -f "$TASK_OUT/preview.keystore" ]]; then
  keytool -genkeypair -keystore "$TASK_OUT/preview.keystore" -storepass android -keypass android -alias adpass -dname 'CN=ADPass Preview' -keyalg RSA -keysize 2048 -validity 10000
fi
"$TASK_BUILD/zipalign" -f 4 "$TASK_OUT/unsigned.apk" "$TASK_OUT/aligned.apk"
"$TASK_BUILD/apksigner" sign --ks "$TASK_OUT/preview.keystore" --ks-pass pass:android --key-pass pass:android --out "$TASK_APK" "$TASK_OUT/aligned.apk"
"$TASK_BUILD/apksigner" verify "$TASK_APK"
print "$TASK_APK"
