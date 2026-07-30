#!/usr/bin/env bash

set -Eeuo pipefail

DEPLOY_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
readonly DEPLOY_DIRECTORY
REPOSITORY_ROOT="$(cd "$DEPLOY_DIRECTORY/.." && pwd -P)"
readonly REPOSITORY_ROOT

readonly STAGING_RUNTIME_DIR="${STAGING_RUNTIME_DIR:-$REPOSITORY_ROOT/runtime}"
readonly STAGING_CONFIG_FILE="${STAGING_CONFIG_FILE:-$REPOSITORY_ROOT/docker/application.properties}"
readonly STAGING_BACKUP_DIR="${STAGING_BACKUP_DIR:-$REPOSITORY_ROOT/backup/staging}"
readonly STAGING_DATABASE_FILE="${STAGING_DATABASE_FILE:-$STAGING_RUNTIME_DIR/database/invoice-system.db}"

log_info() {
    printf '[INFO] %s\n' "$*"
}

log_error() {
    printf '[ERROR] %s\n' "$*" >&2
}

fail() {
    log_error "$*"
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command is unavailable: $1"
}

require_file() {
    [ -f "$1" ] || fail "Required file does not exist: $1"
}

require_directory() {
    [ -d "$1" ] || fail "Required directory does not exist: $1"
}

canonical_path() {
    realpath -m -- "$1"
}

require_path_below() {
    local path
    local root
    path="$(canonical_path "$1")"
    root="$(canonical_path "$2")"
    case "$path" in
        "$root"/*)
            printf '%s\n' "$path"
            ;;
        *)
            fail "Path must be below $root: $path"
            ;;
    esac
}

staging_timestamp() {
    if [ -n "${STAGING_TIMESTAMP:-}" ]; then
        printf '%s\n' "$STAGING_TIMESTAMP"
    else
        date -u '+%Y%m%dT%H%M%SZ'
    fi
}

git_revision() {
    git -C "$REPOSITORY_ROOT" rev-parse --verify HEAD 2>/dev/null || printf 'unknown\n'
}
