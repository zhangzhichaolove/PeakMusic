#!/usr/bin/env bash
# Disposable emulator only. Test-signs a COPY of the minified APK with the public debug key.
set -euo pipefail
serial="${1:?Pass a disposable emulator serial}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Only disposable emulator-* targets are accepted.' >&2; exit 2; }
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
apksigner="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort | tail -1)"
[[ -x "$apksigner" ]] || { echo 'Android SDK apksigner is required.' >&2; exit 2; }
scripts/generate-local-fixtures.sh
./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
server_pid=""
port=""
restore_debug() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  if [[ -n "$port" ]]; then adb -s "$serial" reverse --remove "tcp:$port" >/dev/null 2>&1 || true; fi
  adb -s "$serial" shell am force-stop com.chao.peakmusic >/dev/null 2>&1 || true
  adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null 2>&1 || true
  # pm clear below starts from revoked permissions; local browsing temporarily needs audio access.
  adb -s "$serial" shell pm revoke com.chao.peakmusic android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true
  # This script resets app data, so this API setting belongs to the fixture. Do not leave a
  # dead loopback endpoint behind, and do not remove an unrelated setting after early failure.
  if [[ -n "$port" ]] && adb -s "$serial" shell run-as com.chao.peakmusic cat shared_prefs/api_address.xml 2>/dev/null \
      | grep -Fq "http://127.0.0.1:$port/"; then
    adb -s "$serial" shell run-as com.chao.peakmusic rm -f shared_prefs/api_address.xml || true
  fi
  if [[ -f app/build/verification/queue-release/standard-test.apk ]]; then
    adb -s "$serial" install -r app/build/verification/queue-release/standard-test.apk >/dev/null 2>&1 || true
  fi
}
trap restore_debug EXIT
out=app/build/verification/queue-release
mkdir -p "$out"
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$out/standard-test.apk"
rm -f "$out/api-port.txt"
python3 scripts/queue-release-api.py "$out/api-port.txt" > "$out/api-server.txt" 2>&1 &
server_pid=$!
for ((attempt=0; attempt<600; attempt++)); do
  [[ -s "$out/api-port.txt" ]] && break
  kill -0 "$server_pid"
  sleep 0.05
done
port="$(cat "$out/api-port.txt")"
adb -s "$serial" reverse "tcp:$port" "tcp:$port"
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell pm clear com.chao.peakmusic
adb -s "$serial" shell pm grant com.chao.peakmusic android.permission.READ_MEDIA_AUDIO
adb -s "$serial" shell am instrument -w -r -e class com.chao.peakmusic.service.QueueTransferExperienceTest \
  -e stage_for_release true -e release_api_url "http://127.0.0.1:$port/" \
  com.chao.peakmusic.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/prepare.txt"
grep -q 'OK (1 test)' "$out/prepare.txt"
# This is a test fixture copy, never the production release signing configuration.
"$apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$out/app-release-test-signed.apk" app/build/outputs/apk/release/app-release-unsigned.apk
adb -s "$serial" install -r "$out/app-release-test-signed.apk"
./gradlew :app:assembleDebugAndroidTest -PplatformReleaseSmoke=true --console=plain
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell am instrument -w -r \
  com.chao.peakmusic.test/com.chao.peakmusic.ReleaseQueueInstrumentation | tee "$out/release-test.txt"
grep -q 'RELEASE_QUEUE_OK' "$out/release-test.txt"
grep -q 'RELEASE_LIBRARY_BATCH_OK' "$out/release-test.txt"
grep -q 'RELEASE_LOCAL_LIBRARY_OK' "$out/release-test.txt"
grep -q 'RELEASE_DIAGNOSTIC_PRIVACY_OK' "$out/release-test.txt"
adb -s "$serial" pull /sdcard/Android/data/com.chao.peakmusic/files/verification-release "$out/"
# EXIT trap stops synthetic audio and restores the standard debug app, including on failure.
echo 'Minified Release verified the 10,000-track queue, library batch actions, real local-category playback and disabled diagnostic capture.'
