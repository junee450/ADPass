#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ $# != 1 || ! -f "$1" ]]; then
  print -u2 'Usage: scripts/install-hongguo.sh /absolute/path/to/hongguo.apk'
  exit 1
fi
TASK_APK="$1"
if [[ "$TASK_APK" != *.apk ]]; then
  TASK_APK_DIR="$(mktemp -d /tmp/adpass-apk.XXXXXX)"
  trap 'rm -rf "$TASK_APK_DIR"' EXIT
  cp "$1" "$TASK_APK_DIR/hongguo.apk"
  TASK_APK="$TASK_APK_DIR/hongguo.apk"
fi
"$TASK_ROOT/scripts/adb.sh" install -r "$TASK_APK"
