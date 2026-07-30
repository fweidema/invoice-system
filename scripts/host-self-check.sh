#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
readonly SCRIPT_DIRECTORY
REPOSITORY_ROOT="$(cd "$SCRIPT_DIRECTORY/.." && pwd -P)"
readonly REPOSITORY_ROOT
readonly WATCH_SERVICE=invoice-worker-watch

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "Docker is required for the host self-check."
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable."

cd "$REPOSITORY_ROOT"
docker compose --profile watch config --quiet \
  || fail "Docker Compose configuration is invalid."

container_id="$(docker compose --profile watch ps -q "$WATCH_SERVICE")"
[ -n "$container_id" ] || fail "Watch container is not running: $WATCH_SERVICE"

container_status="$(docker inspect --format '{{.State.Status}}' "$container_id")"
[ "$container_status" = "running" ] \
  || fail "Watch container is not running: $WATCH_SERVICE ($container_status)"

printf '[INFO] Running container self-check in %s.\n' "$WATCH_SERVICE"
docker compose --profile watch exec -T "$WATCH_SERVICE" /app/container-self-check.sh
printf '[INFO] Host self-check passed.\n'
