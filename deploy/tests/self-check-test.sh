#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIRECTORY="$(mktemp -d)"
readonly TEST_DIRECTORY
cleanup() {
  rm -rf -- "$TEST_DIRECTORY"
}
trap cleanup EXIT

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
readonly SOURCE_ROOT
readonly DATA_DIRECTORY="$TEST_DIRECTORY/data"
readonly FAKE_BIN="$TEST_DIRECTORY/bin"
readonly APPLICATION_JAR="$TEST_DIRECTORY/invoice-worker.jar"
readonly CONFIGURATION_FILE="$TEST_DIRECTORY/application.properties"
readonly PROCESS_FILE="$TEST_DIRECTORY/cmdline"
readonly REQUIRED_RUNTIME_DIRECTORIES=(input ocr work manual-review error archive database logs)

mkdir -p "$FAKE_BIN"
for relative_directory in "${REQUIRED_RUNTIME_DIRECTORIES[@]}"; do
  mkdir -p "$DATA_DIRECTORY/$relative_directory"
done
printf 'jar\n' >"$APPLICATION_JAR"
printf 'ai.provider=mock\n' >"$CONFIGURATION_FILE"
printf 'java\0-jar\0%s\0watch\0--config\0%s\0' \
  "$APPLICATION_JAR" "$CONFIGURATION_FILE" >"$PROCESS_FILE"

cat >"$FAKE_BIN/java" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat >"$FAKE_BIN/ocrmypdf" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat >"$FAKE_BIN/tesseract" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = "--list-langs" ]; then
  printf 'List of available languages (1):\ndeu\n'
  exit 0
fi
exit 1
EOF
chmod +x "$FAKE_BIN/java" "$FAKE_BIN/ocrmypdf" "$FAKE_BIN/tesseract"

run_container_check() {
  PATH="$FAKE_BIN:$PATH" \
    INVOICE_SELF_CHECK_APPLICATION_JAR="$APPLICATION_JAR" \
    INVOICE_SELF_CHECK_CONFIGURATION_FILE="$CONFIGURATION_FILE" \
    INVOICE_SELF_CHECK_DATA_DIRECTORY="$DATA_DIRECTORY" \
    INVOICE_SELF_CHECK_PROCESS_COMMAND_LINE_FILE="$PROCESS_FILE" \
    "$SOURCE_ROOT/scripts/container-self-check.sh"
}

run_container_check

printf 'sh\0-c\0sleep 60\0' >"$PROCESS_FILE"
if process_error="$(run_container_check 2>&1)"; then
  printf 'Container check unexpectedly accepted a non-Java main process.\n' >&2
  exit 1
fi
printf '%s\n' "$process_error" | grep -q 'main container process is not the invoice-worker Java process'

if grep -Eq 'docker( compose)?' "$SOURCE_ROOT/scripts/container-self-check.sh"; then
  printf 'Container self-check unexpectedly contains Docker host communication.\n' >&2
  exit 1
fi

readonly HOST_TEST_REPOSITORY="$TEST_DIRECTORY/repository"
mkdir -p "$HOST_TEST_REPOSITORY/scripts"
cp "$SOURCE_ROOT/scripts/host-self-check.sh" "$HOST_TEST_REPOSITORY/scripts/host-self-check.sh"

cat >"$FAKE_BIN/docker" <<EOF
#!/usr/bin/env bash
set -eu
printf 'docker %s\n' "\$*" >>"$TEST_DIRECTORY/docker-actions"
case "\$*" in
  "info") ;;
  "compose --profile watch config --quiet") ;;
  "compose --profile watch ps -q invoice-worker-watch") printf 'watch-container-id\n' ;;
  "inspect --format {{.State.Status}} watch-container-id") printf 'running\n' ;;
  "compose --profile watch exec -T invoice-worker-watch /app/container-self-check.sh") ;;
  *) printf 'Unexpected docker arguments: %s\n' "\$*" >&2; exit 2 ;;
esac
EOF
chmod +x "$FAKE_BIN/docker" "$HOST_TEST_REPOSITORY/scripts/host-self-check.sh"

PATH="$FAKE_BIN:$PATH" "$HOST_TEST_REPOSITORY/scripts/host-self-check.sh"
grep -qx 'docker compose --profile watch exec -T invoice-worker-watch /app/container-self-check.sh' \
  "$TEST_DIRECTORY/docker-actions"

printf 'Container and host self-check tests passed.\n'
