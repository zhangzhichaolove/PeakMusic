#!/usr/bin/env bash
# Disposable API 33+ emulator only. Resets only diagnostic caches and restores the clipboard.
set -euo pipefail
serial="${1:?Pass a disposable emulator serial}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Only disposable emulator-* targets are accepted.' >&2; exit 2; }
[[ "$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]] || {
  echo 'Diagnostic experience tests require API 33+.' >&2; exit 2;
}
cd "$(dirname "$0")/.."
out=app/build/verification/diagnostics
mkdir -p "$out"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  --console=plain | tee "$out/build.txt"
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
trap 'adb -s "$serial" shell am force-stop com.chao.peakmusic >/dev/null 2>&1 || true' EXIT
adb -s "$serial" shell am instrument -w -r -e class com.chao.peakmusic.base.DiagnosticsExperienceTest \
  com.chao.peakmusic.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
grep -q 'OK (2 tests)' "$out/instrumentation.txt"
adb -s "$serial" pull /sdcard/Android/data/com.chao.peakmusic/files/verification-diagnostics "$out/"
