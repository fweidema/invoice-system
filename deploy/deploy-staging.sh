#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=deploy/lib/common.sh
source "$SCRIPT_DIRECTORY/lib/common.sh"

readonly STAGING_BRANCH="${STAGING_BRANCH:-main}"

require_command curl
require_command docker
require_command git
require_command sqlite3
require_file "$REPOSITORY_ROOT/mvnw"
require_file "$REPOSITORY_ROOT/scripts/prepare-runtime.sh"

cd "$REPOSITORY_ROOT"

current_branch="$(git branch --show-current)"
[ "$current_branch" = "$STAGING_BRANCH" ] \
    || fail "Staging checkout must be on branch '$STAGING_BRANCH', current branch is '$current_branch'."

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    fail "Tracked files contain local changes. Commit or revert them before deployment."
fi

log_info "Preparing persistent runtime directories."
./scripts/prepare-runtime.sh

log_info "Creating mandatory pre-deployment backup."
backup_directory="$("$SCRIPT_DIRECTORY/backup-staging.sh" | tail -n 1)"
log_info "Pre-deployment backup: $backup_directory"

log_info "Updating branch '$STAGING_BRANCH' with fast-forward only."
git pull --ff-only origin "$STAGING_BRANCH"

log_info "Building and testing Java 21 application."
./mvnw clean verify

export INVOICE_VCS_REF
export INVOICE_VERSION
export INVOICE_BUILD_DATE
INVOICE_VCS_REF="$(git rev-parse --verify HEAD)"
INVOICE_VERSION="$(git describe --always --dirty --tags)"
INVOICE_BUILD_DATE="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

log_info "Building Docker images for version $INVOICE_VERSION ($INVOICE_VCS_REF)."
docker compose --profile watch --profile ui build

log_info "Updating staging containers."
docker compose --profile watch --profile ui up -d --remove-orphans \
    invoice-worker-watch invoice-worker-api invoice-worker-ui

if ! "$SCRIPT_DIRECTORY/check-staging.sh"; then
    log_error "Deployment healthcheck failed."
    log_error "Backup available for manual restore: $backup_directory"
    exit 1
fi

log_info "Staging deployment completed successfully."
