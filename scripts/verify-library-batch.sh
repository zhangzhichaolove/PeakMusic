#!/usr/bin/env bash
# Only temporary fixture rows/files are removed. Never clears the app's saved library.
set -euo pipefail
serial="${1:?Pass a disposable emulator serial}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Only disposable emulator-* targets are accepted.' >&2; exit 2; }
cd "$(dirname "$0")/.."
out=app/build/verification/batch
mkdir -p "$out"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  --console=plain | tee "$out/build.txt"
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell am instrument -w -r -e class com.chao.peakmusic.LibraryBatchExperienceTest \
  com.chao.peakmusic.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
grep -q 'OK (2 tests)' "$out/instrumentation.txt"
adb -s "$serial" pull /sdcard/Android/data/com.chao.peakmusic/files/verification-batch "$out/"
