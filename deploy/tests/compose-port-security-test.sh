#!/usr/bin/env bash
set -Eeuo pipefail
SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
readonly SOURCE_ROOT
python3 "$SOURCE_ROOT/deploy/tests/compose-port-security-test.py"
