#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

if [ ! -x "./mvnw" ] && [ ! -f "./mvnw.cmd" ]; then
  echo "Maven Wrapper not found in repository root." >&2
  exit 2
fi

mvn_command=()
if [ -x "./mvnw" ] && ./mvnw -version >/dev/null 2>&1; then
  mvn_command=("./mvnw")
elif [ -f "./mvnw.cmd" ] && ./mvnw.cmd -version >/dev/null 2>&1; then
  mvn_command=("./mvnw.cmd")
else
  echo "No runnable Maven Wrapper found in this shell. Ensure Java 21 is available in PATH or JAVA_HOME." >&2
  exit 2
fi

echo "Running full Maven build with tests"
"${mvn_command[@]}" clean verify