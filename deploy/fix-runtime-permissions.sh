#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=deploy/lib/common.sh
source "$SCRIPT_DIRECTORY/lib/common.sh"

readonly RUNTIME_UID="${INVOICE_RUNTIME_UID:-10001}"
readonly RUNTIME_GROUP="${INVOICE_RUNTIME_GROUP:-invoice-runtime}"
readonly DEFAULT_RUNTIME_GID=10001
readonly REQUIRED_RUNTIME_DIRECTORIES=(
    input
    ocr
    work
    manual-review
    error
    archive
    database
    logs
)

require_command chmod
require_command chown
require_command cut
require_command find
require_command realpath

[[ "$RUNTIME_UID" =~ ^[0-9]+$ ]] \
    || fail "INVOICE_RUNTIME_UID must be a numeric user ID: $RUNTIME_UID"

if [ -n "${INVOICE_RUNTIME_GID:-}" ]; then
    RUNTIME_GID="$INVOICE_RUNTIME_GID"
elif group_record="$(getent group "$RUNTIME_GROUP" 2>/dev/null)"; then
    RUNTIME_GID="$(printf '%s\n' "$group_record" | cut -d: -f3)"
else
    RUNTIME_GID="$DEFAULT_RUNTIME_GID"
    log_info "Host group '$RUNTIME_GROUP' was not found; using numeric GID $RUNTIME_GID."
fi
readonly RUNTIME_GID

[[ "$RUNTIME_GID" =~ ^[0-9]+$ ]] \
    || fail "INVOICE_RUNTIME_GID must be a numeric group ID: $RUNTIME_GID"

runtime_directory="$(canonical_path "$STAGING_RUNTIME_DIR")"
readonly runtime_directory
repository_root="$(canonical_path "$REPOSITORY_ROOT")"
readonly repository_root

[ "$runtime_directory" != "/" ] \
    || fail "Refusing to repair permissions for filesystem root."
[ "$runtime_directory" != "$repository_root" ] \
    || fail "Refusing to repair permissions for repository root."
require_directory "$runtime_directory"

for relative_directory in "${REQUIRED_RUNTIME_DIRECTORIES[@]}"; do
    require_directory "$runtime_directory/$relative_directory"
done

if [ "$(id -u)" -eq 0 ]; then
    readonly ADMIN_COMMAND=()
elif command -v sudo >/dev/null 2>&1 && sudo -n true >/dev/null 2>&1; then
    readonly ADMIN_COMMAND=(sudo -n)
else
    fail "Runtime permission repair requires root or passwordless sudo. Run 'sudo ./deploy/fix-runtime-permissions.sh' or configure narrowly scoped sudo access."
fi

run_as_admin() {
    "${ADMIN_COMMAND[@]}" "$@"
}

log_info "Repairing runtime permissions below: $runtime_directory"
log_info "Target owner: UID $RUNTIME_UID; group: $RUNTIME_GROUP (GID $RUNTIME_GID)."

run_as_admin find "$runtime_directory" -xdev -exec chown -h "$RUNTIME_UID:$RUNTIME_GID" {} +
run_as_admin find "$runtime_directory" -xdev -type d -exec chmod 2775 {} +
run_as_admin find "$runtime_directory" -xdev -type f -exec chmod 0664 {} +

log_info "Runtime permission repair completed. No files were deleted or moved."
