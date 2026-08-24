#!/usr/bin/env bash
set -Eeuo pipefail

android_main="apps/android/app/src/main"

if [[ ! -d "$android_main" ]]; then
  printf '%s\n' "Android production source directory is missing." >&2
  exit 1
fi

forbidden_api_pattern='(ClipboardManager|ACTION_OPEN_DOCUMENT|ACTION_GET_CONTENT|ActivityResultContracts\.GetContent|BarcodeScanner|QRCodeReader|mockSubscriptionState|mockPurchasedService|mockServers)'
forbidden_uri_pattern='(vless|vmess|trojan|shadowsocks|ss)://'

if grep -RInE --include='*.kt' --include='*.java' "$forbidden_api_pattern" "$android_main"; then
  printf '%s\n' "Manual configuration import API found in production Android source." >&2
  exit 1
fi

if grep -RInE --include='*.kt' --include='*.java' --include='*.xml' "$forbidden_uri_pattern" "$android_main"; then
  printf '%s\n' "Raw VPN URI found in production Android source." >&2
  exit 1
fi

if find "$android_main" -type f \( -iname '*mock*' -o -iname '*fake*' \) -print -quit | grep -q .; then
  printf '%s\n' "Mock or fake implementation found in production Android source." >&2
  exit 1
fi

printf '%s\n' "Android subscription-only product guard passed."
