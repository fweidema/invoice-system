#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd -P)"
cd "$repo_root"

runtime_database_dir="$repo_root/runtime/database"
mkdir -p "$runtime_database_dir"
canonical_runtime_database_dir="$(cd "$runtime_database_dir" && pwd -P)"

yes_flag=0
database="runtime/database/invoice-system.db"

absolute_path() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s\n' "$repo_root/$1" ;;
  esac
}

validate_database_path() {
  local requested_database="$1"
  local absolute_database
  local database_basename
  local canonical_database

  absolute_database="$(absolute_path "$requested_database")"
  database_basename="$(basename "$absolute_database")"

  case "$database_basename" in
    *.db|*.sqlite|*.sqlite3)
      ;;
    *)
      echo "Refusing to reset non-SQLite database file: $requested_database" >&2
      return 3
      ;;
  esac

  if [ -e "$absolute_database" ] || [ -L "$absolute_database" ]; then
    if ! canonical_database="$(realpath "$absolute_database" 2>/dev/null)"; then
      echo "Refusing to reset unresolved database path: $requested_database" >&2
      return 3
    fi
  elif ! canonical_database="$(realpath -m "$absolute_database" 2>/dev/null)"; then
    echo "Refusing to normalize database path: $requested_database" >&2
    return 3
  fi

  if [ -d "$canonical_database" ]; then
    echo "Refusing to reset database directory: $requested_database" >&2
    return 3
  fi

  case "$canonical_database" in
    "$canonical_runtime_database_dir"/*)
      printf '%s\n' "$canonical_database"
      ;;
    *)
      echo "Refusing to reset database outside local runtime/database: $requested_database" >&2
      return 3
      ;;
  esac
}

main() {
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

  local canonical_database
  canonical_database="$(validate_database_path "$database")"

  if docker compose ps --status running 2>/dev/null | grep -q "invoice-worker"; then
    echo "Refusing to reset database while invoice-system containers are running. Run scripts/dev-stop.sh first." >&2
    exit 3
  fi

  if [ "$yes_flag" -ne 1 ]; then
    echo "This deletes the local development SQLite database and sidecar files:"
    echo "  $canonical_database"
    read -r -p "Type RESET to continue: " confirmation
    if [ "$confirmation" != "RESET" ]; then
      echo "Reset cancelled."
      exit 1
    fi
  fi

  rm -f "$canonical_database" "$canonical_database-shm" "$canonical_database-wal"
  echo "Local development database reset: $canonical_database"
}

if [ "${DEV_RESET_DB_TEST_MODE:-0}" != "1" ]; then
  main "$@"
fi
