#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

if [ ! -x "./mvnw" ]; then
  echo "A runnable Maven Wrapper is required." >&2
  exit 2
fi

echo "Installing the Chromium version required by Playwright"
./mvnw -pl invoice-worker test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"

echo "Running Playwright cockpit tests"
./mvnw -pl invoice-worker -Pui-e2e verify
