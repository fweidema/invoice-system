#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
readonly SCRIPT_DIRECTORY
REPOSITORY_ROOT="$(cd "$SCRIPT_DIRECTORY/.." && pwd -P)"
readonly REPOSITORY_ROOT
readonly RUNTIME_DIRECTORY="${STAGING_RUNTIME_DIR:-$REPOSITORY_ROOT/runtime}"

runtime_dirs=(
  "input"
  "ocr"
  "work"
  "manual-review"
  "error"
  "archive"
  "database"
  "logs"
)

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

printf '[INFO] Preparing invoice-system runtime directories below %s\n' "$RUNTIME_DIRECTORY"
for relative_directory in "${runtime_dirs[@]}"; do
  directory="$RUNTIME_DIRECTORY/$relative_directory"
  mkdir -p -- "$directory" \
    || fail "Cannot create required runtime directory: $directory"

  probe_file="$(mktemp "$directory/.invoice-runtime-write-test.XXXXXX")" \
    || fail "Required runtime directory is not writable: $directory. Run deploy/fix-runtime-permissions.sh as documented."
  rm -f -- "$probe_file" \
    || fail "Cannot remove runtime write test: $probe_file"

  printf '[INFO] Ready and writable: %s\n' "$directory"
done

printf '[INFO] Runtime preparation complete; existing files and permissions were not changed.\n'
