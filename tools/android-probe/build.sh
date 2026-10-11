#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TASK_SRC="$TASK_ROOT/tools/android-probe"
TASK_OUT="$TASK_ROOT/.local/android/probe-build"
TASK_SDK=/Users/junee/Library/Android/sdk
TASK_BUILD="$TASK_SDK/build-tools/36.0.0"
TASK_JAR="$TASK_SDK/platforms/android-34/android.jar"
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$TASK_OUT/classes" "$TASK_OUT/dex" "$TASK_OUT/res"
"$TASK_BUILD/aapt2" compile --dir "$TASK_SRC/res" -o "$TASK_OUT/res"
"$TASK_BUILD/aapt2" link -I "$TASK_JAR" --manifest "$TASK_SRC/AndroidManifest.xml" -o "$TASK_OUT/probe-unsigned.apk" "$TASK_OUT/res/"*.flat
javac --release 11 -classpath "$TASK_JAR" -d "$TASK_OUT/classes" "$TASK_SRC/src/io/adpass/probe/"*.java
jar cf "$TASK_OUT/classes.jar" -C "$TASK_OUT/classes" .
"$TASK_BUILD/d8" --lib "$TASK_JAR" --min-api 30 --output "$TASK_OUT/dex" "$TASK_OUT/classes.jar"
zip -q -j "$TASK_OUT/probe-unsigned.apk" "$TASK_OUT/dex/classes.dex"
if [[ ! -f "$TASK_OUT/debug.keystore" ]]; then
  keytool -genkeypair -keystore "$TASK_OUT/debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=ADPass Emulator Test' -keyalg RSA -keysize 2048 -validity 3650
fi
"$TASK_BUILD/zipalign" -f 4 "$TASK_OUT/probe-unsigned.apk" "$TASK_OUT/probe-aligned.apk"
"$TASK_BUILD/apksigner" sign --ks "$TASK_OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$TASK_OUT/probe.apk" "$TASK_OUT/probe-aligned.apk"
"$TASK_BUILD/apksigner" verify "$TASK_OUT/probe.apk"
print "$TASK_OUT/probe.apk"
