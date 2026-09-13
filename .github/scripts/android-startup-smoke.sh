#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p startup-evidence
fixture_pid=""
capture() {
  if [[ -n "$fixture_pid" ]]; then kill "$fixture_pid" 2>/dev/null || true; fi
  adb logcat -d -b crash > startup-evidence/crash.txt || true
  adb logcat -d > startup-evidence/emulator-logcat.txt || true
  adb exec-out run-as com.ganj.vpn cat cache/vpn-failure.png > startup-evidence/vpn-failure.png 2>/dev/null || true
  adb exec-out run-as com.ganj.vpn cat cache/vpn-failure.xml > startup-evidence/vpn-failure.xml 2>/dev/null || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml startup-evidence/window.xml >/dev/null 2>&1 || true
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
api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
for attempt in 1 2; do
  if (( api >= 29 )); then
    if (( attempt == 1 )); then adb shell cmd uimode night no; else adb shell cmd uimode night yes; fi
  fi
  adb shell am force-stop com.ganj.vpn
  adb shell am start -W -n com.ganj.vpn/.MainActivity > "startup-evidence/launch-$attempt.txt"
  sleep 15
  adb shell pidof com.ganj.vpn > "startup-evidence/pid-$attempt.txt"
  test -s "startup-evidence/pid-$attempt.txt"
  adb shell dumpsys activity activities > "startup-evidence/activity-$attempt.txt"
  grep -E 'mResumedActivity|topResumedActivity' "startup-evidence/activity-$attempt.txt" | grep -F 'com.ganj.vpn/.MainActivity'
  adb exec-out screencap -p > "startup-evidence/screen-$attempt.png"
done
adb logcat -d -b crash > startup-evidence/crash.txt
! grep -E 'FATAL EXCEPTION|Fatal signal' startup-evidence/crash.txt
adb install -r apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python3 .github/scripts/vless-tun-fixture.py > startup-evidence/fixture.log 2>&1 &
fixture_pid=$!
for attempt in {1..30}; do
  if grep -q 'fixture ready' startup-evidence/fixture.log; then break; fi
  sleep 0.1
done
grep -q 'fixture ready' startup-evidence/fixture.log
timeout 180 adb shell am instrument -w -r com.ganj.vpn.test/androidx.test.runner.AndroidJUnitRunner | tee startup-evidence/instrumentation.txt
grep -E '^OK \([1-9][0-9]* tests?\)' startup-evidence/instrumentation.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' startup-evidence/instrumentation.txt
