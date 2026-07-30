#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=deploy/lib/common.sh
source "$SCRIPT_DIRECTORY/lib/common.sh"

readonly API_URL="${STAGING_API_URL:-http://127.0.0.1:${INVOICE_API_PORT:-8080}/api/health}"
readonly UI_URL="${STAGING_UI_URL:-http://127.0.0.1:${INVOICE_UI_PORT:-8081}/health}"
readonly CHECK_ATTEMPTS="${STAGING_CHECK_ATTEMPTS:-30}"
readonly CHECK_INTERVAL_SECONDS="${STAGING_CHECK_INTERVAL_SECONDS:-2}"
readonly REQUIRED_SERVICES=(invoice-worker-watch invoice-worker-api invoice-worker-ui)

require_command curl
require_command docker
require_command sqlite3

if ! [[ "$CHECK_ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
    fail "STAGING_CHECK_ATTEMPTS must be a positive integer."
fi
if ! [[ "$CHECK_INTERVAL_SECONDS" =~ ^[0-9]+$ ]]; then
    fail "STAGING_CHECK_INTERVAL_SECONDS must be a non-negative integer."
fi

log_info "Checking Docker daemon."
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable."

cd "$REPOSITORY_ROOT"
docker compose --profile watch --profile ui config --quiet \
    || fail "Docker Compose configuration is invalid."

container_id() {
    docker compose --profile watch --profile ui ps -q "$1"
}

container_ready() {
    local service="$1"
    local id
    local status
    local health
    id="$(container_id "$service")"
    [ -n "$id" ] || return 1
    status="$(docker inspect --format '{{.State.Status}}' "$id")"
    [ "$status" = "running" ] || return 1
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id")"
    [ "$health" = "healthy" ] || [ "$health" = "none" ]
}

http_ready() {
    curl --fail --silent --show-error --max-time 5 "$API_URL" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' \
        && [ "$(curl --fail --silent --show-error --max-time 5 "$UI_URL")" = "OK" ]
}

for ((attempt = 1; attempt <= CHECK_ATTEMPTS; attempt++)); do
    ready=true
    for service in "${REQUIRED_SERVICES[@]}"; do
        if ! container_ready "$service"; then
            ready=false
        fi
    done
    if [ "$ready" = true ] && http_ready; then
        break
    fi
    if [ "$attempt" -eq "$CHECK_ATTEMPTS" ]; then
        fail "Staging services did not become healthy after $CHECK_ATTEMPTS checks."
    fi
    log_info "Services are not healthy yet ($attempt/$CHECK_ATTEMPTS); waiting."
    sleep "$CHECK_INTERVAL_SECONDS"
done

require_file "$STAGING_DATABASE_FILE"
sqlite3 "$STAGING_DATABASE_FILE" "PRAGMA quick_check;" | grep -qx 'ok' \
    || fail "SQLite database quick_check failed: $STAGING_DATABASE_FILE"

log_info "Docker, API, UI, database and required containers are healthy."
log_info "Git revision: $(git_revision)"
for service in "${REQUIRED_SERVICES[@]}"; do
    id="$(container_id "$service")"
    image_version="$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$id")"
    image_revision="$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$id")"
    log_info "$service: version=$image_version revision=$image_revision"
done
