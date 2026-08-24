#!/usr/bin/env bash
set -Eeuo pipefail

if (( $# != 2 )); then
  printf '%s\n' 'Usage: verify-libxray-embedding.sh <official-libXray.aar> <application.aab>' >&2
  exit 64
fi

official_aar="$1"
application_aab="$2"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
lock="$repo_root/UPSTREAM.lock.json"
test -s "$official_aar"
test -s "$application_aab"
test -s "$lock"

native_library="$(jq -er '.libXray.androidArtifact.nativeLibrary' "$lock")"
expected_abis="$(jq -r '.libXray.androidArtifact.expectedAbis[]' "$lock" | sort)"
aar_abis="$(
  unzip -Z1 "$official_aar" |
    sed -nE "s#^jni/([^/]+)/${native_library//./\\.}\$#\\1#p" |
    sort -u
)"
aab_abis="$(
  unzip -Z1 "$application_aab" |
    sed -nE "s#^base/lib/([^/]+)/${native_library//./\\.}\$#\\1#p" |
    sort -u
)"
if [[ "$aar_abis" != "$expected_abis" || "$aab_abis" != "$expected_abis" ]]; then
  printf '%s\n' 'Official AAR or application AAB has an unexpected libXray ABI set.' >&2
  exit 1
fi

while IFS= read -r abi; do
  aar_entry="jni/$abi/$native_library"
  aab_entry="base/lib/$abi/$native_library"
  aar_sha="$(unzip -p "$official_aar" "$aar_entry" | sha256sum | cut -d ' ' -f 1)"
  aab_sha="$(unzip -p "$application_aab" "$aab_entry" | sha256sum | cut -d ' ' -f 1)"
  if [[ "$aar_sha" != "$aab_sha" ]]; then
    printf 'Final AAB libXray binary differs from pinned official build for ABI %s.\n' "$abi" >&2
    exit 1
  fi
done <<< "$expected_abis"

printf '%s\n' 'Final AAB embeds only the pinned official-source libXray native binaries.'
