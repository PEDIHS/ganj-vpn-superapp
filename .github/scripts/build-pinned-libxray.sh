#!/usr/bin/env bash
set -Eeuo pipefail

if (( $# != 1 )); then
  printf '%s\n' 'Usage: build-pinned-libxray.sh <empty-output-directory>' >&2
  exit 64
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
lock="$repo_root/UPSTREAM.lock.json"
output="$1"
test -s "$lock"
mkdir -p "$output"
if find "$output" -mindepth 1 -print -quit | grep -q .; then
  printf '%s\n' 'Pinned libXray output directory must be empty.' >&2
  exit 1
fi

read_lock() {
  jq -er "$1" "$lock"
}

repository="$(read_lock '.libXray.repository')"
tag="$(read_lock '.libXray.tag')"
commit="$(read_lock '.libXray.commit')"
go_version="$(read_lock '.libXray.toolchain.go')"
gomobile_module="$(read_lock '.libXray.toolchain.gomobileModule')"
gomobile_version="$(read_lock '.libXray.toolchain.gomobileVersion')"
android_api="$(read_lock '.libXray.toolchain.androidApi')"
ndk_version="$(read_lock '.libXray.toolchain.androidNdk')"
linker_flags="$(read_lock '.libXray.toolchain.linkerFlags')"
xray_module="$(read_lock '.libXray.xrayCore.module')"
xray_version="$(read_lock '.libXray.xrayCore.version')"
xray_license_sha="$(read_lock '.libXray.xrayCore.licenseSha256')"
requested_group="$(read_lock '.libXray.gradleOverride.requestedGroup')"
requested_artifact="$(read_lock '.libXray.gradleOverride.requestedArtifact')"
requested_version="$(read_lock '.libXray.gradleOverride.requestedVersion')"
native_library="$(read_lock '.libXray.androidArtifact.nativeLibrary')"
minimum_alignment="$(read_lock '.libXray.androidArtifact.minimumPageAlignmentBytes')"

work="$(mktemp -d "${RUNNER_TEMP:-/tmp}/ganj-libxray-build.XXXXXX")"
cleanup() {
  find "$work" -type f -exec chmod u+w {} + 2>/dev/null || true
  rm -rf "$work"
}
trap cleanup EXIT
source_dir="$work/source"

git init -q "$source_dir"
git -C "$source_dir" remote add origin "$repository"
git -C "$source_dir" fetch -q --depth=1 origin "refs/tags/$tag:refs/tags/$tag"
git -C "$source_dir" checkout -q --detach "$tag"
actual_commit="$(git -C "$source_dir" rev-parse HEAD)"
tag_commit="$(git -C "$source_dir" rev-parse "$tag^{commit}")"
if [[ "$actual_commit" != "$commit" || "$tag_commit" != "$commit" ]]; then
  printf '%s\n' 'Official libXray tag or HEAD does not match UPSTREAM.lock.json.' >&2
  exit 1
fi

while IFS=$'\t' read -r relative expected_sha; do
  test -f "$source_dir/$relative"
  actual_sha="$(sha256sum "$source_dir/$relative" | cut -d ' ' -f 1)"
  if [[ "$actual_sha" != "$expected_sha" ]]; then
    printf 'Pinned libXray source hash mismatch: %s\n' "$relative" >&2
    exit 1
  fi
done < <(jq -r '
  (.libXray.sourceFiles | to_entries[] | [.key, .value] | @tsv),
  ([.libXray.license.path, .libXray.license.sha256] | @tsv)
' "$lock")

if [[ "$(go env GOVERSION)" != "go$go_version" ]]; then
  printf '%s\n' 'Go toolchain does not match UPSTREAM.lock.json.' >&2
  exit 1
fi
if [[ -z "${ANDROID_NDK_HOME:-}" || ! -s "$ANDROID_NDK_HOME/source.properties" ]]; then
  printf '%s\n' 'Pinned Android NDK is unavailable.' >&2
  exit 1
fi
actual_ndk="$(sed -nE 's/^Pkg\.Revision[[:space:]]*=[[:space:]]*//p' "$ANDROID_NDK_HOME/source.properties")"
if [[ "$actual_ndk" != "$ndk_version" ]]; then
  printf '%s\n' 'Android NDK revision does not match UPSTREAM.lock.json.' >&2
  exit 1
fi

export CGO_ENABLED=1
export GOBIN="$work/bin"
export PATH="$GOBIN:$PATH"
export GOTOOLCHAIN=local
export GOPROXY="https://proxy.golang.org,direct"
export GOSUMDB="sum.golang.org"

pushd "$source_dir" >/dev/null
go mod download
go mod verify
resolved_xray="$(go list -m -f '{{.Version}}' "$xray_module")"
if [[ "$resolved_xray" != "$xray_version" ]]; then
  printf '%s\n' 'Resolved Xray-core module does not match UPSTREAM.lock.json.' >&2
  exit 1
fi

xray_json="$(go mod download -json "$xray_module@$xray_version")"
xray_dir="$(jq -er '.Dir' <<< "$xray_json")"
actual_xray_license_sha="$(sha256sum "$xray_dir/LICENSE" | cut -d ' ' -f 1)"
if [[ "$actual_xray_license_sha" != "$xray_license_sha" ]]; then
  printf '%s\n' 'Xray-core license hash does not match UPSTREAM.lock.json.' >&2
  exit 1
fi

go test ./... -count=1 -timeout 15m
go install "$gomobile_module/cmd/gobind@$gomobile_version"
go install "$gomobile_module/cmd/gomobile@$gomobile_version"
gomobile init

aar="$work/libXray.aar"
sources="$work/libXray-sources.jar"
gomobile bind \
  -target android \
  -androidapi "$android_api" \
  -ldflags="$linker_flags" \
  -o "$aar" \
  .
test -s "$aar"
test -s "$sources"
git diff --exit-code
popd >/dev/null

unzip -tqq "$aar"
unzip -Z1 "$aar" | grep -Fxq 'AndroidManifest.xml'
unzip -Z1 "$aar" | grep -Fxq 'classes.jar'
while IFS= read -r abi; do
  entry="jni/$abi/$native_library"
  unzip -Z1 "$aar" | grep -Fxq "$entry"
  native_file="$work/$abi-$native_library"
  unzip -p "$aar" "$entry" > "$native_file"
  test -s "$native_file"
  load_segments=0
  while IFS= read -r alignment; do
    ((load_segments += 1))
    if (( alignment < minimum_alignment )); then
      printf 'Native library page alignment is below policy for ABI %s.\n' "$abi" >&2
      exit 1
    fi
  done < <(readelf -lW "$native_file" | awk '$1 == "LOAD" { print $NF }')
  if (( load_segments == 0 )); then
    printf 'No ELF LOAD segments found for ABI %s.\n' "$abi" >&2
    exit 1
  fi
done < <(jq -r '.libXray.androidArtifact.expectedAbis[]' "$lock")

evidence="$output/evidence"
mkdir -p "$evidence"
install -m 0644 "$lock" "$evidence/UPSTREAM.lock.json"
install -m 0644 "$aar" "$evidence/libXray.aar"
install -m 0644 "$sources" "$evidence/libXRay-sources.jar"
install -m 0644 "$source_dir/LICENSE" "$evidence/libXray-LICENSE-MIT.txt"
install -m 0644 "$xray_dir/LICENSE" "$evidence/Xray-core-LICENSE-MPL-2.0.txt"
install -m 0644 "$source_dir/go.mod" "$evidence/libXray-go.mod"
install -m 0644 "$source_dir/go.sum" "$evidence/libXray-go.sum"
install -m 0644 "$lock" "$evidence/UPSTREAM.lock.json"

group_path="${requested_group//./\/}"
maven_dir="$output/maven/$group_path/$requested_artifact/$requested_version"
mkdir -p "$maven_dir"
install -m 0644 "$aar" "$maven_dir/$requested_artifact-$requested_version.aar"
printf '%s\n' \
  '<?xml version="1.0" encoding="UTF-8"?>' \
  '<project xmlns="http://maven.apache.org/POM/4.0.0">' \
  '  <modelVersion>4.0.0</modelVersion>' \
  "  <groupId>$requested_group</groupId>" \
  "  <artifactId>$requested_artifact</artifactId>" \
  "  <version>$requested_version</version>" \
  '  <packaging>aar</packaging>' \
  '  <name>Ganj CI override rebuilt from official XTLS libXray source</name>' \
  "  <url>${repository%.git}</url>" \
  '  <licenses><license><name>MIT License</name><url>https://opensource.org/license/mit</url></license></licenses>' \
  '  <properties>' \
  "    <ganj.upstream.tag>$tag</ganj.upstream.tag>" \
  "    <ganj.upstream.commit>$commit</ganj.upstream.commit>" \
  '  </properties>' \
  '</project>' \
  > "$maven_dir/$requested_artifact-$requested_version.pom"

aar_sha="$(sha256sum "$aar" | cut -d ' ' -f 1)"
sources_sha="$(sha256sum "$sources" | cut -d ' ' -f 1)"
tree="$(git -C "$source_dir" rev-parse HEAD^{tree})"
jq -n \
  --arg repository "$repository" \
  --arg tag "$tag" \
  --arg commit "$commit" \
  --arg tree "$tree" \
  --arg go_version "$go_version" \
  --arg gomobile_version "$gomobile_version" \
  --arg ndk_version "$ndk_version" \
  --arg xray_version "$xray_version" \
  --arg aar_sha256 "$aar_sha" \
  --arg sources_sha256 "$sources_sha" \
  --arg built_at_utc "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
  '{
    schemaVersion: 1,
    repository: $repository,
    tag: $tag,
    commit: $commit,
    sourceTree: $tree,
    goVersion: $go_version,
    gomobileVersion: $gomobile_version,
    androidNdkVersion: $ndk_version,
    xrayCoreVersion: $xray_version,
    aarSha256: $aar_sha256,
    sourcesJarSha256: $sources_sha256,
    builtAtUtc: $built_at_utc
  }' > "$evidence/libxray-provenance.json"

(
  cd "$evidence"
  sha256sum \
    UPSTREAM.lock.json \
    Xray-core-LICENSE-MPL-2.0.txt \
    libXRay-sources.jar \
    libXray-LICENSE-MIT.txt \
    libXray-go.mod \
    libXray-go.sum \
    libXray.aar \
    libxray-provenance.json \
    > SHA256SUMS
)
printf 'Pinned official-source libXray build completed: %s\n' "$aar_sha"
