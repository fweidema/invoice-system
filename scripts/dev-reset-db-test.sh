#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd -P)"
cd "$repo_root"

export DEV_RESET_DB_TEST_MODE=1
# shellcheck source=scripts/dev-reset-db.sh
source "$repo_root/scripts/dev-reset-db.sh"

inside_temp_dir="$(mktemp -d "$canonical_runtime_database_dir/dev-reset-db-test.XXXXXX")"
outside_temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/dev-reset-db-outside.XXXXXX")"
cleanup() {
  rm -rf "$inside_temp_dir" "$outside_temp_dir"
}
trap cleanup EXIT

pass_count=0

pass() {
  pass_count=$((pass_count + 1))
}

assert_allowed() {
  local path="$1"
  local expected="$2"
  local actual

  if ! actual="$(validate_database_path "$path")"; then
    echo "Expected allowed path, got rejection: $path" >&2
    exit 1
  fi

  if [ "$actual" != "$expected" ]; then
    echo "Expected canonical path '$expected', got '$actual' for '$path'" >&2
    exit 1
  fi

  pass
}

assert_rejected() {
  local path="$1"

  if validate_database_path "$path" >/dev/null 2>&1; then
    echo "Expected rejected path, got acceptance: $path" >&2
    exit 1
  fi

  pass
}

touch "$inside_temp_dir/allowed.db"
touch "$inside_temp_dir/allowed.sqlite"
touch "$inside_temp_dir/allowed.sqlite3"
touch "$inside_temp_dir/rejected.txt"
touch "$outside_temp_dir/outside.db"
mkdir "$inside_temp_dir/directory.db"

assert_allowed \
  "runtime/database/$(basename "$inside_temp_dir")/allowed.db" \
  "$inside_temp_dir/allowed.db"
assert_allowed \
  "$inside_temp_dir/allowed.sqlite" \
  "$inside_temp_dir/allowed.sqlite"
assert_allowed \
  "$inside_temp_dir/allowed.sqlite3" \
  "$inside_temp_dir/allowed.sqlite3"
assert_allowed \
  "$inside_temp_dir/new-file.db" \
  "$inside_temp_dir/new-file.db"
assert_allowed \
  "$inside_temp_dir/missing/new-file.db" \
  "$inside_temp_dir/missing/new-file.db"

assert_rejected "runtime/database/../../outside.db"
assert_rejected "$canonical_runtime_database_dir"
assert_rejected "$inside_temp_dir/directory.db"
assert_rejected "$inside_temp_dir/rejected.txt"
assert_rejected "$outside_temp_dir/outside.db"

if ln -s "$outside_temp_dir/outside.db" "$inside_temp_dir/outside-link.db" 2>/dev/null; then
  assert_rejected "$inside_temp_dir/outside-link.db"
else
  echo "Skipping symlink target regression; symlink creation is unavailable in this environment." >&2
fi

if ln -s "$outside_temp_dir" "$inside_temp_dir/outside-dir-link" 2>/dev/null; then
  assert_rejected "$inside_temp_dir/outside-dir-link/through-link.db"
else
  echo "Skipping symlink parent regression; symlink creation is unavailable in this environment." >&2
fi

echo "dev-reset-db path validation tests passed: $pass_count"
