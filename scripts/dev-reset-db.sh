#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

yes_flag=0
database="runtime/database/invoice-system.db"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --yes)
      yes_flag=1
      shift
      ;;
    --database)
      database="${2:-}"
      if [ -z "$database" ]; then
        echo "--database requires a path." >&2
        exit 2
      fi
      shift 2
      ;;
    *)
      echo "Usage: $0 [--yes] [--database runtime/database/invoice-system.db]" >&2
      exit 2
      ;;
  esac
done

case "$database" in
  runtime/database/*|./runtime/database/*)
    ;;
  *)
    echo "Refusing to reset database outside local runtime/database: $database" >&2
    exit 3
    ;;
esac

if docker compose ps --status running 2>/dev/null | grep -q "invoice-worker"; then
  echo "Refusing to reset database while invoice-system containers are running. Run scripts/dev-stop.sh first." >&2
  exit 3
fi

if [ "$yes_flag" -ne 1 ]; then
  echo "This deletes the local development SQLite database and sidecar files:"
  echo "  $database"
  read -r -p "Type RESET to continue: " confirmation
  if [ "$confirmation" != "RESET" ]; then
    echo "Reset cancelled."
    exit 1
  fi
fi

mkdir -p "$(dirname "$database")"
rm -f "$database" "$database-shm" "$database-wal"
echo "Local development database reset: $database"