#!/usr/bin/env bash
# Disposable API 33+ emulator only. Inserts/removes owned MediaStore fixtures, not existing songs.
set -euo pipefail
serial="${1:?Pass a disposable emulator serial}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Only disposable emulator-* targets are accepted.' >&2; exit 2; }
cd "$(dirname "$0")/.."
out=app/build/verification/local
mkdir -p "$out"
scripts/generate-local-fixtures.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  --console=plain | tee "$out/build.txt"
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
granted=false
if adb -s "$serial" shell dumpsys package com.chao.peakmusic | grep -q 'android.permission.READ_MEDIA_AUDIO: granted=true'; then granted=true; fi
adb -s "$serial" shell am force-stop com.chao.peakmusic
rm -f "$out/api_address.xml"
if adb -s "$serial" shell run-as com.chao.peakmusic test -f shared_prefs/api_address.xml; then
  adb -s "$serial" exec-out run-as com.chao.peakmusic cat shared_prefs/api_address.xml > "$out/api_address.xml"
fi
restore() {
  adb -s "$serial" shell am force-stop com.chao.peakmusic >/dev/null 2>&1 || true
  if [[ -f "$out/api_address.xml" ]]; then
    adb -s "$serial" exec-in run-as com.chao.peakmusic sh -c "cat > shared_prefs/api_address.xml" < "$out/api_address.xml" || true
  else adb -s "$serial" shell run-as com.chao.peakmusic rm -f shared_prefs/api_address.xml || true; fi
  if [[ "$granted" == false ]]; then adb -s "$serial" shell pm revoke com.chao.peakmusic android.permission.READ_MEDIA_AUDIO || true; fi
}
trap restore EXIT
adb -s "$serial" shell pm grant com.chao.peakmusic android.permission.READ_MEDIA_AUDIO
adb -s "$serial" shell am instrument -w -r -e class com.chao.peakmusic.LocalLibraryExperienceTest \
  com.chao.peakmusic.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
grep -q 'OK (1 test)' "$out/instrumentation.txt"
adb -s "$serial" pull /sdcard/Android/data/com.chao.peakmusic/files/verification-local "$out/"
