#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="$TASK_ROOT/.local/android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="$TASK_ROOT/.local/android/avd"
export ANDROID_USER_HOME="$TASK_ROOT/.local/android/user"
exec "$ANDROID_HOME/emulator/emulator" -avd ADPass_API_34 -port 5554 -gpu auto -memory 3072 -no-boot-anim -no-snapshot -no-metrics "$@"
