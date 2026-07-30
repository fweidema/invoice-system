#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIRECTORY="$(mktemp -d)"
readonly TEST_DIRECTORY
cleanup() {
    rm -rf -- "$TEST_DIRECTORY"
}
trap cleanup EXIT

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
readonly REPOSITORY_ROOT
readonly TEST_RUNTIME="$TEST_DIRECTORY/runtime"
readonly TEST_CONFIG="$TEST_DIRECTORY/config/application.properties"
readonly TEST_BACKUPS="$TEST_DIRECTORY/backups"
readonly FAKE_BIN="$TEST_DIRECTORY/bin"

mkdir -p "$TEST_RUNTIME/database" "$TEST_RUNTIME/archive" "$TEST_RUNTIME/manual-review" \
    "$(dirname "$TEST_CONFIG")" "$FAKE_BIN"
printf 'database-v1\n' >"$TEST_RUNTIME/database/invoice-system.db"
printf 'archive-v1\n' >"$TEST_RUNTIME/archive/invoice.pdf"
printf 'review-v1\n' >"$TEST_RUNTIME/manual-review/review.pdf"
printf 'ai.provider=mock\n' >"$TEST_CONFIG"

cat >"$FAKE_BIN/sqlite3" <<'EOF'
#!/usr/bin/env bash
set -eu
database="$1"
statement="$2"
case "$statement" in
    ".backup '"*)
        destination="${statement#".backup '"}"
        destination="${destination%\'}"
        cp -- "$database" "$destination"
        ;;
    "PRAGMA quick_check;")
        printf 'ok\n'
        ;;
    *)
        exit 2
        ;;
esac
EOF

cat >"$FAKE_BIN/docker" <<EOF
#!/usr/bin/env bash
set -eu
printf '%s\n' "\$*" >>"$TEST_DIRECTORY/docker-calls"
EOF
chmod +x "$FAKE_BIN/sqlite3" "$FAKE_BIN/docker"

export PATH="$FAKE_BIN:$PATH"
export STAGING_RUNTIME_DIR="$TEST_RUNTIME"
export STAGING_CONFIG_FILE="$TEST_CONFIG"
export STAGING_BACKUP_DIR="$TEST_BACKUPS"
export STAGING_TIMESTAMP="20260730T120000Z"

backup_output="$("$REPOSITORY_ROOT/deploy/backup-staging.sh")"
backup_directory="$(printf '%s\n' "$backup_output" | tail -n 1)"

test -f "$backup_directory/database/invoice-system.db"
test -f "$backup_directory/config/application.properties"
test -f "$backup_directory/archive.tar.gz"
test -f "$backup_directory/manual-review.tar.gz"
grep -q '^created_at=20260730T120000Z$' "$backup_directory/manifest.properties"

printf 'database-v2\n' >"$TEST_RUNTIME/database/invoice-system.db"
printf 'ai.provider=openai\n' >"$TEST_CONFIG"

"$REPOSITORY_ROOT/deploy/restore-staging.sh" --yes "$backup_directory"

grep -qx 'database-v1' "$TEST_RUNTIME/database/invoice-system.db"
grep -qx 'ai.provider=mock' "$TEST_CONFIG"
grep -q 'compose --profile watch --profile ui stop' "$TEST_DIRECTORY/docker-calls"

if "$REPOSITORY_ROOT/deploy/restore-staging.sh" "$backup_directory" >/dev/null 2>&1; then
    printf 'Restore without --yes unexpectedly succeeded.\n' >&2
    exit 1
fi

printf 'Backup and restore tests passed.\n'
