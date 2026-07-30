#!/usr/bin/env bash

set -Eeuo pipefail

readonly APPLICATION_JAR="${INVOICE_SELF_CHECK_APPLICATION_JAR:-/app/invoice-worker.jar}"
readonly CONFIGURATION_FILE="${INVOICE_SELF_CHECK_CONFIGURATION_FILE:-/config/application.properties}"
readonly DATA_DIRECTORY="${INVOICE_SELF_CHECK_DATA_DIRECTORY:-/data}"
readonly PROCESS_COMMAND_LINE_FILE="${INVOICE_SELF_CHECK_PROCESS_COMMAND_LINE_FILE:-/proc/1/cmdline}"
readonly REQUIRED_RUNTIME_DIRECTORIES=(
  input
  ocr
  work
  manual-review
  error
  archive
  database
  logs
)

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

check_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "Required container command is unavailable: $command_name"
}

check_runtime_directory() {
  local directory="$1"
  [ -d "$directory" ] || fail "Required runtime directory is missing: $directory"
  [ -r "$directory" ] || fail "Required runtime directory is not readable: $directory"
  [ -w "$directory" ] || fail "Required runtime directory is not writable: $directory"
}

check_command java
check_command grep
check_command ocrmypdf
check_command tesseract
check_command tr

[ -r "$APPLICATION_JAR" ] || fail "Application JAR is missing or unreadable: $APPLICATION_JAR"
[ -r "$CONFIGURATION_FILE" ] \
  || fail "Application configuration is missing or unreadable: $CONFIGURATION_FILE"
[ -r "$PROCESS_COMMAND_LINE_FILE" ] \
  || fail "Cannot inspect the main container process: $PROCESS_COMMAND_LINE_FILE"

process_command_line="$(tr '\000' ' ' <"$PROCESS_COMMAND_LINE_FILE")"
case "$process_command_line" in
  *"java -jar $APPLICATION_JAR"*)
    ;;
  *)
    fail "The main container process is not the invoice-worker Java process."
    ;;
esac
case "$process_command_line" in
  *"--config $CONFIGURATION_FILE"*)
    ;;
  *)
    fail "The invoice-worker process was not started with the expected configuration: $CONFIGURATION_FILE"
    ;;
esac

if ! tesseract --list-langs 2>/dev/null | grep -qx "deu"; then
  fail "Required Tesseract language is unavailable: deu"
fi

for relative_directory in "${REQUIRED_RUNTIME_DIRECTORIES[@]}"; do
  check_runtime_directory "$DATA_DIRECTORY/$relative_directory"
done

printf '[INFO] Container self-check passed for process: %s\n' "$process_command_line"
