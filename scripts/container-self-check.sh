#!/usr/bin/env bash
set -euo pipefail

if [ "${INVOICE_CONTAINER_SELF_CHECK:-}" != "1" ]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to run the container self-check from the host." >&2
    exit 1
  fi
  docker compose run --rm --entrypoint /app/container-self-check.sh \
    -e INVOICE_CONTAINER_SELF_CHECK=1 \
    invoice-worker
  exit $?
fi

required_dirs=(
  "/data/input"
  "/data/ocr"
  "/data/archive"
  "/data/database"
  "/data/logs"
)

check_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing command: $command_name" >&2
    exit 1
  fi
  echo "OK: $command_name available"
}

check_writable_dir() {
  local dir="$1"
  if [ ! -d "$dir" ]; then
    echo "Missing directory: $dir" >&2
    exit 1
  fi
  if [ ! -w "$dir" ]; then
    echo "Directory is not writable: $dir" >&2
    exit 1
  fi
  echo "OK: $dir exists and is writable"
}

check_command java
check_command ocrmypdf
check_command tesseract

if [ ! -f /app/invoice-worker.jar ]; then
  echo "Missing JAR: /app/invoice-worker.jar" >&2
  exit 1
fi
echo "OK: /app/invoice-worker.jar exists"

if [ ! -f /config/application.properties ]; then
  echo "Missing configuration: /config/application.properties" >&2
  exit 1
fi
echo "OK: /config/application.properties exists"

if ! tesseract --list-langs 2>/dev/null | grep -qx "deu"; then
  echo "Missing Tesseract language: deu" >&2
  exit 1
fi
echo "OK: Tesseract language deu installed"

for dir in "${required_dirs[@]}"; do
  check_writable_dir "$dir"
done

probe_file="/data/database/.self-check-write-test"
touch "$probe_file"
rm -f "$probe_file"
echo "OK: database directory accepts file creation"

echo "Container self-check passed"