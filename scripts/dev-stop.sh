#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for dev-stop." >&2
  exit 2
fi

echo "Stopping local development containers. Persistent runtime data is kept."
docker compose --profile api --profile watch stop invoice-worker invoice-worker-api invoice-worker-watch