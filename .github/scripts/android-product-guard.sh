#!/usr/bin/env bash
set -Eeuo pipefail

android_main="apps/android/app/src/main"

if [[ ! -d "$android_main" ]]; then
  printf '%s\n' "Android production source directory is missing." >&2
  exit 1
fi

forbidden_api_pattern='(ClipboardManager|ACTION_OPEN_DOCUMENT|ACTION_GET_CONTENT|ActivityResultContracts\.GetContent|BarcodeScanner|QRCodeReader|mockSubscriptionState|mockPurchasedService|mockServers|import(Config|Profile)|manual(Config|Profile)|startsWith\("(vless|vmess|trojan|shadowsocks|ss)://)'
# Exact scheme literals are allowed only as privacy-redaction denylist values. A URI with payload is
# always blocked, as are parser/import entry points covered by forbidden_api_pattern above.
forbidden_uri_pattern='(vless|vmess|trojan|shadowsocks|ss)://[^"[:space:]]{4,}'

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

# A successful server catalog is not a connection path. Production must sign one
# device-bound request for the selected managed server; the old placeholder
# silently prevented all real connection-profile issuance.
owner="$android_main/java/com/ganj/vpn/composition/GanjCompositionOwner.kt"
if [[ ! -f "$owner" ]] || grep -Fq 'connectionContext = ConnectionProfileContextProvider { null }' "$owner"; then
  printf '%s\n' "Android connection profile context must not be a null placeholder." >&2
  exit 1
fi
if ! grep -Fq 'connectionContext = AndroidConnectionProfileContextProvider(' "$owner"; then
  printf '%s\n' "Android selected-server device-proof context is not wired to production." >&2
  exit 1
fi

printf '%s\n' "Android subscription-only product guard passed."
