#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIRECTORY="$(mktemp -d)"
readonly TEST_DIRECTORY
cleanup() {
    rm -rf -- "$TEST_DIRECTORY"
}
trap cleanup EXIT

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
readonly SOURCE_ROOT
readonly TEST_RUNTIME="$TEST_DIRECTORY/runtime"
readonly FAKE_BIN="$TEST_DIRECTORY/bin"
readonly REQUIRED_DIRECTORIES=(input ocr work manual-review error archive database logs)

mkdir -p "$FAKE_BIN"

STAGING_RUNTIME_DIR="$TEST_RUNTIME" "$SOURCE_ROOT/scripts/prepare-runtime.sh"
for relative_directory in "${REQUIRED_DIRECTORIES[@]}"; do
    test -d "$TEST_RUNTIME/$relative_directory"
done

existing_file="$TEST_RUNTIME/archive/existing.pdf"
printf 'unchanged\n' >"$existing_file"
chmod 0640 "$existing_file"
mode_before="$(stat -c '%a' "$existing_file")"
STAGING_RUNTIME_DIR="$TEST_RUNTIME" "$SOURCE_ROOT/scripts/prepare-runtime.sh"
STAGING_RUNTIME_DIR="$TEST_RUNTIME" "$SOURCE_ROOT/scripts/prepare-runtime.sh"
mode_after="$(stat -c '%a' "$existing_file")"
[ "$mode_before" = "$mode_after" ]

readonly BLOCKED_RUNTIME="$TEST_DIRECTORY/blocked-runtime"
mkdir -p "$BLOCKED_RUNTIME/input"
chmod 0555 "$BLOCKED_RUNTIME/input"
if preparation_error="$(
    STAGING_RUNTIME_DIR="$BLOCKED_RUNTIME" "$SOURCE_ROOT/scripts/prepare-runtime.sh" 2>&1
)"; then
    printf 'Runtime preparation unexpectedly accepted a non-writable directory.\n' >&2
    exit 1
fi
chmod 0755 "$BLOCKED_RUNTIME/input"
printf '%s\n' "$preparation_error" | grep -q 'Required runtime directory is not writable'

cat >"$FAKE_BIN/id" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = "-u" ]; then
    printf '2000\n'
    exit 0
fi
exec /usr/bin/id "$@"
EOF
cat >"$FAKE_BIN/sudo" <<'EOF'
#!/usr/bin/env bash
exit 1
EOF
chmod +x "$FAKE_BIN/id" "$FAKE_BIN/sudo"

if permission_error="$(
    PATH="$FAKE_BIN:$PATH" STAGING_RUNTIME_DIR="$TEST_RUNTIME" \
        "$SOURCE_ROOT/deploy/fix-runtime-permissions.sh" 2>&1
)"; then
    printf 'Permission repair unexpectedly succeeded without administrative rights.\n' >&2
    exit 1
fi
printf '%s\n' "$permission_error" | grep -q 'requires root or passwordless sudo'

current_uid="$(id -u)"
current_gid="$(id -g)"
current_group="$(id -gn)"
cat >"$FAKE_BIN/sudo" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = "-n" ]; then
    shift
fi
if [ "${1:-}" = "true" ]; then
    exit 0
fi
exec "$@"
EOF
chmod +x "$FAKE_BIN/sudo"

PATH="$FAKE_BIN:$PATH" \
    STAGING_RUNTIME_DIR="$TEST_RUNTIME" \
    INVOICE_RUNTIME_UID="$current_uid" \
    INVOICE_RUNTIME_GID="$current_gid" \
    INVOICE_RUNTIME_GROUP="$current_group" \
    "$SOURCE_ROOT/deploy/fix-runtime-permissions.sh"

[ "$(stat -c '%a' "$TEST_RUNTIME")" = "2775" ]
[ "$(stat -c '%a' "$TEST_RUNTIME/archive")" = "2775" ]
[ "$(stat -c '%a' "$existing_file")" = "664" ]
[ "$(stat -c '%u' "$existing_file")" = "$current_uid" ]
[ "$(stat -c '%g' "$existing_file")" = "$current_gid" ]

printf 'Runtime preparation and permission repair tests passed.\n'
