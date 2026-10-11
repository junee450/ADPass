#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec "$TASK_ROOT/.local/android/sdk/platform-tools/adb" -s emulator-5554 "$@"
