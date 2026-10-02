#!/usr/bin/env bash
set -Eeuo pipefail

if (( $# == 0 )); then
  printf '%s\n' 'Usage: android-artifact-guard.sh <apk-or-aab> [...]' >&2
  exit 64
fi

for artifact in "$@"; do
  if [[ ! -f "$artifact" || ! -s "$artifact" ]]; then
    printf 'Android artifact is missing or empty: %s\n' "$artifact" >&2
    exit 1
  fi

  case "$artifact" in
    *.apk|*.aab) ;;
    *)
      printf 'Unsupported Android artifact type: %s\n' "$artifact" >&2
      exit 1
      ;;
  esac

  size="$(stat -c '%s' "$artifact")"
  if (( size > 209715200 )); then
    printf 'Android artifact exceeds the 200 MiB policy limit: %s\n' "$artifact" >&2
    exit 1
  fi

  unzip -tqq "$artifact"
  python3 "$(dirname "$0")/test-apk-png.py"
  python3 "$(dirname "$0")/check-apk-png.py" "$artifact"
  entries="$(mktemp)"
  trap 'rm -f "$entries"' EXIT
  unzip -Z1 "$artifact" > "$entries"

  forbidden_entry_pattern='(^|/)(google-services\.json|GoogleService-Info\.plist|local\.properties|secrets\.properties|[^/]*\.(jks|keystore|p12|pfx|pem|key|mobileprovision))$'
  if grep -Ei "$forbidden_entry_pattern" "$entries" >/dev/null; then
    printf 'Forbidden signing, credential or local configuration entry found in: %s\n' "$artifact" >&2
    exit 1
  fi

  if [[ "$artifact" == *.aab ]]; then
    grep -Fxq 'BundleConfig.pb' "$entries"
    grep -Fxq 'base/manifest/AndroidManifest.xml' "$entries"
    grep -Eq '^base/dex/classes([0-9]+)?\.dex$' "$entries"
  else
    grep -Fxq 'AndroidManifest.xml' "$entries"
    grep -Eq '^classes([0-9]+)?\.dex$' "$entries"
  fi

  if strings "$artifact" | grep -aE -m1 \
      '(-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{30,255}|sk_live_[0-9A-Za-z]{16,})' \
      >/dev/null; then
    printf 'Possible credential material found in generated Android artifact: %s\n' "$artifact" >&2
    exit 1
  fi

  rm -f "$entries"
  trap - EXIT
  printf 'Android artifact guard passed: %s (%s bytes)\n' "$artifact" "$size"
done
