#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_LABEL="${1:-screen}"
if [[ ! "$TASK_LABEL" =~ ^[a-zA-Z0-9_-]+$ ]]; then
  print -u2 'Capture label must contain only letters, digits, underscores and hyphens.'
  exit 1
fi
TASK_OUTPUT="$TASK_ROOT/artifacts/hongguo-verification"
mkdir -p "$TASK_OUTPUT"
"$TASK_ROOT/scripts/adb.sh" exec-out screencap -p > "$TASK_OUTPUT/$TASK_LABEL.png"
TASK_SERVICES="$("$TASK_ROOT/scripts/adb.sh" shell settings get secure enabled_accessibility_services)"
if [[ "$TASK_SERVICES" == *"io.adpass.probe/io.adpass.probe.ProbeService"* ]]; then
  print -u2 'Screenshot saved. Skipping UIAutomator because it interrupts the active accessibility probe. Use run-as io.adpass.probe cat files/latest.json to read probe observations.'
  exit 0
fi
"$TASK_ROOT/scripts/adb.sh" shell rm -f /sdcard/adpass-window.xml
TASK_DUMP="$("$TASK_ROOT/scripts/adb.sh" shell uiautomator dump --compressed /sdcard/adpass-window.xml 2>&1)"
print -r -- "$TASK_DUMP"
if [[ "$TASK_DUMP" != *"UI hierchary dumped to:"* ]]; then
  print -u2 'UI hierarchy unavailable; screenshot retained. No previous XML was reused.'
  exit 1
fi
"$TASK_ROOT/scripts/adb.sh" pull /sdcard/adpass-window.xml "$TASK_OUTPUT/$TASK_LABEL.xml"
"$TASK_ROOT/scripts/adb.sh" exec-out screencap -p > "$TASK_OUTPUT/$TASK_LABEL.png"
