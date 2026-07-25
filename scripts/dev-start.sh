#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

profile="${1:-api}"
service="invoice-worker-api"

case "$profile" in
  api)
    compose_args=(--profile api up -d invoice-worker-api)
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
    echo "Usage: $0 [api|watch|batch]" >&2
    exit 2
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for dev-start." >&2
  exit 2
fi

"$script_dir/prepare-runtime.sh"
docker compose "${compose_args[@]}"
docker compose ps "$service"

if [ "$profile" = "api" ]; then
  port="${INVOICE_API_PORT:-8080}"
  health_url="http://127.0.0.1:${port}/api/health"
  echo "Checking API health at $health_url"
  for attempt in 1 2 3 4 5; do
    if command -v curl >/dev/null 2>&1 && curl -fsS "$health_url" >/dev/null; then
      echo "API healthcheck passed"
      exit 0
    fi
    sleep 2
  done
  echo "API healthcheck did not pass. Inspect with: docker compose logs $service" >&2
  exit 1
fi