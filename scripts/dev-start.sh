#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"
source "$script_dir/lib/dev-healthcheck.sh"

profile="${1:-api}"
service="invoice-worker-api"

case "$profile" in
  api)
    compose_args=(--profile api up -d invoice-worker-api)
    ;;
  ui|full)
    service="invoice-worker-ui"
    compose_services=(invoice-worker-watch invoice-worker-api invoice-worker-ui)
    compose_args=(--profile watch --profile ui up -d "${compose_services[@]}")
    ;;
  watch)
    service="invoice-worker-watch"
    compose_args=(--profile watch up -d invoice-worker-watch)
    ;;
  batch)
    service="invoice-worker"
    compose_args=(up -d invoice-worker)
    ;;
  *)
    echo "Usage: $0 [api|watch|batch|ui|full]" >&2
    exit 2
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for dev-start." >&2
  exit 2
fi

"$script_dir/prepare-runtime.sh"
if [[ "$profile" = "ui" || "$profile" = "full" ]]; then
  if ! docker compose "${compose_args[@]}"; then
    for service in "${compose_services[@]}"; do
      container_id="$(docker compose ps -q "$service" 2>/dev/null || true)"
      if [[ -z "$container_id" ]]; then
        echo "Service $service failed (state=not running, health=unknown)." >&2
      else
        state="$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null || echo "unknown|unknown")"
        echo "Service $service failed (state=${state%%|*}, health=${state#*|})." >&2
      fi
      echo "Inspect logs with: docker compose logs --tail=100 $service" >&2
    done
    exit 1
  fi
  startup_timeout="${DEV_START_TIMEOUT_SECONDS:-60}"
  poll_interval="${DEV_START_POLL_SECONDS:-2}"
  if ! [[ "$startup_timeout" =~ ^[0-9]+$ ]] || (( startup_timeout < 1 )) \
      || ! [[ "$poll_interval" =~ ^[0-9]+$ ]]; then
    echo "DEV_START_TIMEOUT_SECONDS must be positive and DEV_START_POLL_SECONDS must be non-negative." >&2
    exit 2
  fi
  start_time="$SECONDS"
  all_services_ready=false
  while (( SECONDS - start_time < startup_timeout )); do
    all_services_ready=true
    for service in "${compose_services[@]}"; do
      container_id="$(docker compose ps -q "$service" 2>/dev/null || true)"
      if [[ -z "$container_id" ]]; then
        all_services_ready=false
        continue
      fi
      if ! state="$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null)"; then
        all_services_ready=false
        continue
      fi
      container_state="${state%%|*}"
      health_state="${state#*|}"
      if [[ "$container_state" == "exited" || "$container_state" == "dead" || "$health_state" == "unhealthy" ]]; then
        echo "Service $service failed (state=$container_state, health=$health_state)." >&2
        echo "Inspect logs with: docker compose logs --tail=100 $service" >&2
        exit 1
      fi
      if [[ "$container_state" != "running" || "$health_state" != "healthy" ]]; then
        all_services_ready=false
      fi
    done
    if [[ "$all_services_ready" == true ]]; then
      break
    fi
    if (( poll_interval > 0 )); then
      sleep "$poll_interval"
    fi
  done
  if [[ "$all_services_ready" != true ]]; then
    for service in "${compose_services[@]}"; do
      container_id="$(docker compose ps -q "$service" 2>/dev/null || true)"
      if [[ -z "$container_id" ]]; then
        echo "Service $service failed (state=not running, health=unknown)." >&2
        echo "Inspect logs with: docker compose logs --tail=100 $service" >&2
        exit 1
      fi
      state="$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null || echo "unknown|unknown")"
      echo "Service $service did not become ready before timeout (state=${state%%|*}, health=${state#*|})." >&2
      echo "Inspect logs with: docker compose logs --tail=100 $service" >&2
    done
    exit 1
  fi
  docker compose ps "${compose_services[@]}"
else
  docker compose "${compose_args[@]}"
  docker compose ps "$service"
fi

if [[ "$profile" = "api" || "$profile" = "ui" || "$profile" = "full" ]]; then
  port="${INVOICE_API_PORT:-8080}"
  health_url="http://127.0.0.1:${port}/api/health"
  if [[ "$profile" = "ui" || "$profile" = "full" ]]; then
    health_url="http://127.0.0.1:${INVOICE_UI_PORT:-8081}/health"
  fi
  echo "Checking application health at $health_url"
  healthcheck_passed=false
  for attempt in 1 2 3 4 5; do
    if command -v curl >/dev/null 2>&1 && dev_curl_local_healthcheck "$health_url"; then
      echo "Application healthcheck passed"
      healthcheck_passed=true
      break
    fi
    if (( attempt < 5 )); then
      sleep 2
    fi
  done
  if [[ "$healthcheck_passed" != true ]]; then
    echo "Application healthcheck did not pass. Inspect with: docker compose logs $service" >&2
    exit 1
  fi
fi

if [[ "$profile" = "ui" || "$profile" = "full" ]]; then
  api_url="http://127.0.0.1:${INVOICE_API_PORT:-8080}/api/health"
  if ! command -v curl >/dev/null 2>&1 || ! dev_curl_local_healthcheck "$api_url"; then
    echo "Service invoice-worker-api healthcheck failed at $api_url." >&2
    echo "Inspect logs with: docker compose logs --tail=100 invoice-worker-api" >&2
    exit 1
  fi
fi
