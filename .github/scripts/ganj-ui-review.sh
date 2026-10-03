#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p ui-review
capture() {
  for image in subscriptions-dark config-picker-dark connection-selected subscriptions-light-large subscriptions-unknown-large config-picker-light-large; do
    adb exec-out run-as com.ganj.vpn cat "cache/$image.png" > "ui-review/$image.png" 2>/dev/null || true
  done
  adb logcat -d -b crash > ui-review/crash.txt || true
}
trap capture EXIT
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb install -r apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
timeout 300 adb shell am instrument -w -r -e class com.ganj.vpn.ui.SubscriptionSelectionTest com.ganj.vpn.test/androidx.test.runner.AndroidJUnitRunner | tee ui-review/instrumentation.txt
grep -E '^OK \([1-9][0-9]* tests?\)' ui-review/instrumentation.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' ui-review/instrumentation.txt
