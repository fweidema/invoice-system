#!/usr/bin/env bash
set -Eeuo pipefail
SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
readonly SOURCE_ROOT
TEST_DIRECTORY="$(mktemp -d)"
readonly TEST_DIRECTORY
trap 'rm -rf -- "$TEST_DIRECTORY"' EXIT
mkdir -p "$TEST_DIRECTORY/repository/scripts" "$TEST_DIRECTORY/bin"
cp "$SOURCE_ROOT/scripts/dev-start.sh" "$SOURCE_ROOT/scripts/dev-stop.sh" "$TEST_DIRECTORY/repository/scripts/"
cat > "$TEST_DIRECTORY/repository/scripts/prepare-runtime.sh" <<'SH'
#!/usr/bin/env bash
exit 0
SH
cat > "$TEST_DIRECTORY/bin/docker" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$TEST_ACTIONS"
SH
cat > "$TEST_DIRECTORY/bin/curl" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$TEST_ACTIONS"
SH
chmod +x "$TEST_DIRECTORY/repository/scripts/prepare-runtime.sh" "$TEST_DIRECTORY/bin/"*
export TEST_ACTIONS="$TEST_DIRECTORY/actions"
for mode in ui full; do
    PATH="$TEST_DIRECTORY/bin:$PATH" bash "$TEST_DIRECTORY/repository/scripts/dev-start.sh" "$mode"
done
PATH="$TEST_DIRECTORY/bin:$PATH" bash "$TEST_DIRECTORY/repository/scripts/dev-stop.sh"
grep -Fxq 'compose --profile watch --profile ui up -d invoice-worker-watch invoice-worker-api invoice-worker-ui' "$TEST_ACTIONS"
grep -Fxq 'compose --profile api --profile watch --profile ui stop invoice-worker invoice-worker-api invoice-worker-watch invoice-worker-ui' "$TEST_ACTIONS"
grep -Fq '127.0.0.1:8081/health' "$TEST_ACTIONS"
cd "$SOURCE_ROOT"
docker compose --env-file /dev/null --profile watch --profile ui config --format json | python3 -c '
import json, sys
config = json.load(sys.stdin)
services = config["services"]
ui = services["invoice-worker-ui"]
watch = services["invoice-worker-watch"]
ui_mount = next(volume for volume in ui["volumes"] if volume["target"] == "/data/input")
watch_mount = next(volume for volume in watch["volumes"] if volume["target"] == "/data/input")
assert ui_mount["source"] == watch_mount["source"]
assert not ui_mount.get("read_only", False)
assert ui["environment"]["INVOICE_UI_MANUAL_REVIEW_API_BASE_URI"] == "http://invoice-worker-api:8080/"
for name in ("invoice-worker-ui", "invoice-worker-api"):
    assert all(port["host_ip"] == "127.0.0.1" for port in services[name]["ports"])
print("Upload deployment tests passed.")
'
