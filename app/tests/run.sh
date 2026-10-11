#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TASK_JAVA="${ADPASS_JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}/bin"
TASK_OUT="$TASK_ROOT/.local/android/app-tests"
mkdir -p "$TASK_OUT"
"$TASK_JAVA/javac" --release 11 -d "$TASK_OUT" "$TASK_ROOT/app/src/io/adpass/app/SkipGate.java" "$TASK_ROOT/app/src/io/adpass/app/VisualRules.java" "$TASK_ROOT/app/src/io/adpass/app/LandscapeAttempt.java" "$TASK_ROOT/app/tests/SkipGateTest.java" "$TASK_ROOT/app/tests/VisualRulesTest.java" "$TASK_ROOT/app/tests/LandscapeAttemptTest.java"
"$TASK_JAVA/java" -cp "$TASK_OUT" io.adpass.app.SkipGateTest
"$TASK_JAVA/java" -cp "$TASK_OUT" io.adpass.app.VisualRulesTest
"$TASK_JAVA/java" -cp "$TASK_OUT" io.adpass.app.LandscapeAttemptTest
python3 "$TASK_ROOT/tools/android-probe/tests/replay.py"
