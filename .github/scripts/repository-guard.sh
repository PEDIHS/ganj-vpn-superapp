#!/usr/bin/env bash
set -Eeuo pipefail

forbidden_path_pattern='(^|/)(google-services\.json|GoogleService-Info\.plist|local\.properties|secrets\.properties|error_log|[^/]*\.(apk|aab|apks|zip|jks|keystore|p12|pfx|pem|key|mobileprovision|sql\.gz|log))$'
forbidden_paths="$(
  {
    git ls-files | grep -E "$forbidden_path_pattern" || true
    git ls-files | grep -E '(^|/)\.(env($|\.)|firebase/)' | grep -Ev '\.env\.example$' || true
    git ls-files | grep -E '(^|/)(receipts|runtime|storage)/' || true
  } | sort -u
)"

if [[ -n "$forbidden_paths" ]]; then
  printf '%s\n' 'Forbidden secret, runtime or generated artifacts are tracked:' >&2
  printf '%s\n' "$forbidden_paths" >&2
  exit 1
fi

secret_pattern='(-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|gh[pousr]_[A-Za-z0-9_]{30,255}|xox[baprs]-[A-Za-z0-9-]{10,}|sk_live_[0-9A-Za-z]{16,}|[0-9]{8,10}:[A-Za-z0-9_-]{30,})'
secret_files="$(
  git grep -Il -E "$secret_pattern" -- . \
    ':(exclude).github/scripts/repository-guard.sh' || true
)"

if [[ -n "$secret_files" ]]; then
  printf '%s\n' 'Possible credential material was found in these tracked files:' >&2
  printf '%s\n' "$secret_files" >&2
  printf '%s\n' 'Do not print the matching value. Rotate it and purge it from history.' >&2
  exit 1
fi

oversized=0
while IFS= read -r -d '' tracked_file; do
  [[ -f "$tracked_file" ]] || continue
  size="$(stat -c '%s' "$tracked_file")"
  if (( size > 10485760 )); then
    printf 'Tracked file exceeds the 10 MiB source limit: %s (%s bytes)\n' \
      "$tracked_file" "$size" >&2
    oversized=1
  fi
done < <(git ls-files -z)

if (( oversized != 0 )); then
  exit 1
fi

printf '%s\n' 'Repository guard passed.'
