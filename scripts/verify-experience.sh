#!/usr/bin/env bash
# Installs test APKs and resets ONLY this app's data on a disposable emulator.
set -euo pipefail
serial="${1:?Pass a disposable emulator serial, e.g. emulator-5580}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Only disposable emulator-* targets are accepted.' >&2; exit 2; }
cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell pm clear com.chao.peakmusic
adb -s "$serial" shell pm revoke com.chao.peakmusic android.permission.READ_MEDIA_AUDIO
adb -s "$serial" shell pm revoke com.chao.peakmusic android.permission.POST_NOTIFICATIONS
mkdir -p app/build/verification/experience
adb -s "$serial" shell am instrument -w -r -e class com.chao.peakmusic.MusicExperienceTest,com.chao.peakmusic.PlayerExperienceTest,com.chao.peakmusic.service.QueueTransferExperienceTest \
  com.chao.peakmusic.test/androidx.test.runner.AndroidJUnitRunner | tee app/build/verification/experience/instrumentation.txt
grep -q 'OK (7 tests)' app/build/verification/experience/instrumentation.txt
adb -s "$serial" pull /sdcard/Android/data/com.chao.peakmusic/files/verification app/build/verification/experience/
