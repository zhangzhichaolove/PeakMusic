#!/usr/bin/env bash
# Complete native CI entry point. Destructive fixtures: disposable emulator only, never personal data.
set -euo pipefail
serial="${1:?Pass the dedicated emulator serial}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Only disposable emulator-* targets are accepted.' >&2; exit 2; }
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
[[ "$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]] || {
  echo 'The complete native suite requires API 33+.' >&2; exit 2;
}
cd "$(dirname "$0")/.."
trap 'adb -s "$serial" shell am force-stop com.chao.peakmusic >/dev/null 2>&1 || true' EXIT
scripts/generate-local-fixtures.sh
out=app/build/verification/native-ci
mkdir -p "$out"
rm -f "$out/result.txt"
for script in verify-experience verify-sources verify-library-batch verify-local-library verify-diagnostics verify-queue-release; do
  scripts/"$script".sh "$serial" 2>&1 | tee "$out/$script.txt"
done
printf 'NATIVE_CI_OK\n' | tee "$out/result.txt"
