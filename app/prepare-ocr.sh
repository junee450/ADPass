#!/bin/zsh
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_OCR="$TASK_ROOT/.local/android/ocr"
TASK_AAR="$TASK_OCR/tesseract4android-4.9.0.aar"
TASK_HASH=bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a
mkdir -p "$TASK_OCR"
if [[ ! -f "$TASK_AAR" ]]; then
  curl --fail --location --max-time 60 'https://jitpack.io/cz/adaptech/tesseract4android/tesseract4android/4.9.0/tesseract4android-4.9.0.aar' -o "$TASK_AAR.tmp"
  [[ "$(shasum -a 256 "$TASK_AAR.tmp" | cut -d ' ' -f 1)" == "$TASK_HASH" ]] || { print -u2 'OCR archive checksum mismatch'; exit 1; }
  mv "$TASK_AAR.tmp" "$TASK_AAR"
fi
[[ "$(shasum -a 256 "$TASK_AAR" | cut -d ' ' -f 1)" == "$TASK_HASH" ]] || { print -u2 'OCR archive checksum mismatch'; exit 1; }
mkdir -p "$TASK_OCR/aar"
unzip -qo "$TASK_AAR" -d "$TASK_OCR/aar"
print 'Verified Tesseract4Android 4.9.0'
