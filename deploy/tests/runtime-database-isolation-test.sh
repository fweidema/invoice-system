#!/usr/bin/env bash

set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
readonly SOURCE_ROOT
readonly DOCKER_CONFIGURATION="$SOURCE_ROOT/docker/application.properties"

database_configuration="$(
    sed -n 's/^[[:space:]]*persistence\.databaseFile[[:space:]]*=[[:space:]]*//p' \
        "$DOCKER_CONFIGURATION"
)"
[ "$database_configuration" = "/data/database/invoice-system.db" ] || {
    printf 'Docker production database must be /data/database/invoice-system.db, found: %s\n' \
        "$database_configuration" >&2
    exit 1
}

if grep -R -n -E 'invoice-worker/data/invoice-system\.db|persistence\.databaseFile=data/invoice-system\.db' \
    "$SOURCE_ROOT/compose.yaml" \
    "$SOURCE_ROOT/docker" \
    "$SOURCE_ROOT/deploy/backup-staging.sh" \
    "$SOURCE_ROOT/deploy/check-staging.sh" \
    "$SOURCE_ROOT/deploy/deploy-staging.sh" \
    "$SOURCE_ROOT/deploy/restore-staging.sh" \
    "$SOURCE_ROOT/deploy/lib" \
    "$SOURCE_ROOT/scripts/prepare-runtime.sh" \
    "$SOURCE_ROOT/scripts/container-self-check.sh" \
    "$SOURCE_ROOT/scripts/host-self-check.sh" \
    "$SOURCE_ROOT/config"; then
    printf 'Production or deployment configuration references the legacy repository database.\n' >&2
    exit 1
fi

mount_count="$(
    grep -c -- './runtime/database:/data/database' "$SOURCE_ROOT/compose.yaml"
)"
[ "$mount_count" -eq 4 ] || {
    printf 'Expected all four Compose services to mount runtime/database, found: %s\n' \
        "$mount_count" >&2
    exit 1
}

if git -C "$SOURCE_ROOT" ls-files --error-unmatch \
    invoice-worker/data/invoice-system.db >/dev/null 2>&1; then
    printf 'Legacy runtime database must not be tracked by Git.\n' >&2
    exit 1
fi

git check-ignore -q "$SOURCE_ROOT/invoice-worker/data/invoice-system.db" || {
    printf 'Legacy runtime database path is not ignored.\n' >&2
    exit 1
}

printf 'Runtime database isolation tests passed.\n'
