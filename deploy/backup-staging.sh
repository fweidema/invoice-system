#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=deploy/lib/common.sh
source "$SCRIPT_DIRECTORY/lib/common.sh"

require_command realpath
require_command sqlite3
require_command tar
require_file "$STAGING_CONFIG_FILE"
require_directory "$STAGING_RUNTIME_DIR/archive"
require_directory "$STAGING_RUNTIME_DIR/manual-review"

timestamp="$(staging_timestamp)"
backup_directory="$(canonical_path "$STAGING_BACKUP_DIR/$timestamp")"
require_path_below "$backup_directory" "$STAGING_BACKUP_DIR" >/dev/null

if [ -e "$backup_directory" ]; then
    fail "Backup destination already exists: $backup_directory"
fi

log_info "Creating staging backup: $backup_directory"
mkdir -p "$backup_directory/database" "$backup_directory/config"
backup_complete=false
cleanup_incomplete_backup() {
    if [ "$backup_complete" != true ]; then
        log_error "Removing incomplete backup: $backup_directory"
        rm -rf -- "$backup_directory"
    fi
}
trap cleanup_incomplete_backup EXIT

if [ -f "$STAGING_DATABASE_FILE" ]; then
    database_backup="$backup_directory/database/invoice-system.db"
    sqlite_destination="${database_backup//\'/\'\'}"
    sqlite3 "$STAGING_DATABASE_FILE" ".backup '$sqlite_destination'"
    sqlite3 "$database_backup" "PRAGMA quick_check;" | grep -qx 'ok' \
        || fail "SQLite verification failed for backup: $database_backup"
    log_info "SQLite database backed up and verified."
else
    : >"$backup_directory/database/NOT_PRESENT"
    log_info "SQLite database does not exist yet; absence recorded."
fi

cp -- "$STAGING_CONFIG_FILE" "$backup_directory/config/application.properties"
tar -C "$STAGING_RUNTIME_DIR" -czf "$backup_directory/archive.tar.gz" archive
tar -C "$STAGING_RUNTIME_DIR" -czf "$backup_directory/manual-review.tar.gz" manual-review

{
    printf 'created_at=%s\n' "$timestamp"
    printf 'git_revision=%s\n' "$(git_revision)"
    printf 'database_source=%s\n' "$STAGING_DATABASE_FILE"
    printf 'configuration_source=%s\n' "$STAGING_CONFIG_FILE"
} >"$backup_directory/manifest.properties"

backup_complete=true
trap - EXIT
log_info "Staging backup completed: $backup_directory"
printf '%s\n' "$backup_directory"
