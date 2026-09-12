#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p startup-evidence
capture() {
  adb logcat -d -b crash > startup-evidence/crash.txt || true
  # Isolated guest emulator only: expose startup failures in CI logs.
  if grep -E 'FATAL EXCEPTION|Fatal signal' startup-evidence/crash.txt; then
    sed -n '1,140p' startup-evidence/crash.txt
  fi
  adb shell dumpsys activity activities > startup-evidence/activities.txt || true
  adb exec-out screencap -p > startup-evidence/screen.png || true
}
trap capture EXIT
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.ganj.vpn
adb logcat -c
for attempt in 1 2; do
  adb shell am force-stop com.ganj.vpn
  adb shell am start -W -n com.ganj.vpn/.MainActivity > "startup-evidence/launch-$attempt.txt"
  sleep 15
  adb shell pidof com.ganj.vpn > "startup-evidence/pid-$attempt.txt"
  test -s "startup-evidence/pid-$attempt.txt"
  adb shell dumpsys activity activities > "startup-evidence/activity-$attempt.txt"
  grep -E 'mResumedActivity|topResumedActivity' "startup-evidence/activity-$attempt.txt" | grep -F 'com.ganj.vpn/.MainActivity'
done
adb logcat -d -b crash > startup-evidence/crash.txt
! grep -E 'FATAL EXCEPTION|Fatal signal' startup-evidence/crash.txt
