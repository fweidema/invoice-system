#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=deploy/lib/common.sh
source "$SCRIPT_DIRECTORY/lib/common.sh"

usage() {
    printf 'Usage: %s --yes <backup-directory>\n' "$0" >&2
}

confirmed=false
backup_argument=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --yes)
            confirmed=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -*)
            usage
            fail "Unknown option: $1"
            ;;
        *)
            if [ -n "$backup_argument" ]; then
                usage
                fail "Only one backup directory may be specified."
            fi
            backup_argument="$1"
            ;;
    esac
    shift
done

[ "$confirmed" = true ] || fail "Restore requires explicit confirmation with --yes."
[ -n "$backup_argument" ] || {
    usage
    fail "Backup directory is required."
}

require_command docker
require_command realpath
require_command sqlite3

backup_directory="$(require_path_below "$backup_argument" "$STAGING_BACKUP_DIR")"
require_directory "$backup_directory"
require_file "$backup_directory/database/invoice-system.db"
require_file "$backup_directory/config/application.properties"
require_file "$backup_directory/manifest.properties"

mkdir -p "$(dirname "$STAGING_DATABASE_FILE")" "$(dirname "$STAGING_CONFIG_FILE")"

log_info "Stopping staging services before restore."
(
    cd "$REPOSITORY_ROOT"
    docker compose --profile watch --profile ui stop \
        invoice-worker-watch invoice-worker-api invoice-worker-ui
)

database_temporary="$STAGING_DATABASE_FILE.restore"
configuration_temporary="$STAGING_CONFIG_FILE.restore"
trap 'rm -f -- "$database_temporary" "$configuration_temporary"' EXIT

cp -- "$backup_directory/database/invoice-system.db" "$database_temporary"
sqlite3 "$database_temporary" "PRAGMA quick_check;" | grep -qx 'ok' \
    || fail "Restored SQLite database failed verification."
cp -- "$backup_directory/config/application.properties" "$configuration_temporary"

rm -f -- "$STAGING_DATABASE_FILE-wal" "$STAGING_DATABASE_FILE-shm"
mv -- "$database_temporary" "$STAGING_DATABASE_FILE"
mv -- "$configuration_temporary" "$STAGING_CONFIG_FILE"
trap - EXIT

log_info "Database and configuration restored from: $backup_directory"
log_info "Services remain stopped. Run ./deploy/deploy-staging.sh after verification."
