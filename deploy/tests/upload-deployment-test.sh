#!/usr/bin/env bash
set -Eeuo pipefail
SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
readonly SOURCE_ROOT
TEST_DIRECTORY="$(mktemp -d)"
readonly TEST_DIRECTORY
trap 'rm -rf -- "$TEST_DIRECTORY"' EXIT
mkdir -p "$TEST_DIRECTORY/repository/scripts" "$TEST_DIRECTORY/bin"
mkdir -p "$TEST_DIRECTORY/repository/scripts/lib"
cp "$SOURCE_ROOT/scripts/dev-start.sh" "$SOURCE_ROOT/scripts/dev-stop.sh" "$TEST_DIRECTORY/repository/scripts/"
cp "$SOURCE_ROOT/scripts/lib/dev-healthcheck.sh" "$TEST_DIRECTORY/repository/scripts/lib/"
cat > "$TEST_DIRECTORY/repository/scripts/prepare-runtime.sh" <<'SH'
#!/usr/bin/env bash
exit 0
SH
cat > "$TEST_DIRECTORY/bin/docker" <<'SH'
#!/usr/bin/env bash
set -eu
printf 'docker %s\n' "$*" >> "$TEST_ACTIONS"
if [[ "${1:-}" == compose && "${2:-}" == ps && "${3:-}" == -q ]]; then
    service="${4:-}"
    case "$service" in
        invoice-worker-watch) status="${WATCH_STATUS:-running|healthy}" ;;
        invoice-worker-api) status="${API_STATUS:-running|healthy}" ;;
        invoice-worker-ui) status="${UI_STATUS:-running|healthy}" ;;
        *) status=missing ;;
    esac
    [[ "$status" == missing ]] || printf '%s\n' "$service"
    exit 0
fi
if [[ "${1:-}" == inspect ]]; then
    service="${4:-}"
    case "$service" in
        invoice-worker-watch) status="${WATCH_STATUS:-running|healthy}" ;;
        invoice-worker-api) status="${API_STATUS:-running|healthy}" ;;
        invoice-worker-ui) status="${UI_STATUS:-running|healthy}" ;;
        *) status=missing ;;
    esac
    [[ "$status" == missing ]] && exit 1
    if [[ "$status" == *'|starting' ]]; then
        count_file="$TEST_DIRECTORY/$service.inspect-count"
        count=0
        [[ -f "$count_file" ]] && read -r count < "$count_file"
        count=$((count + 1))
        printf '%s\n' "$count" > "$count_file"
        if (( count > 1 )); then
            status="running|healthy"
        fi
    fi
    printf '%s\n' "$status"
    exit 0
fi
if [[ "$*" == *' compose logs '* ]]; then
    exit 0
fi
SH
cat > "$TEST_DIRECTORY/bin/curl" <<'SH'
#!/usr/bin/env bash
printf 'curl %s HTTP_PROXY=%s HTTPS_PROXY=%s\n' "$*" "${HTTP_PROXY:-}" "${HTTPS_PROXY:-}" >> "$TEST_ACTIONS"
SH
chmod +x "$TEST_DIRECTORY/repository/scripts/prepare-runtime.sh" "$TEST_DIRECTORY/bin/"*
export TEST_ACTIONS="$TEST_DIRECTORY/actions"
export TEST_DIRECTORY
export HTTP_PROXY="http://proxy.invalid:3128"
export HTTPS_PROXY="http://proxy.invalid:3128"
export DEV_START_TIMEOUT_SECONDS=2
export DEV_START_POLL_SECONDS=0
for mode in ui full; do
    PATH="$TEST_DIRECTORY/bin:$PATH" bash "$TEST_DIRECTORY/repository/scripts/dev-start.sh" "$mode"
done
PATH="$TEST_DIRECTORY/bin:$PATH" bash "$TEST_DIRECTORY/repository/scripts/dev-stop.sh"
grep -Fq 'compose --profile watch --profile ui up -d invoice-worker-watch invoice-worker-api invoice-worker-ui' "$TEST_ACTIONS"
grep -Fq 'compose ps -q invoice-worker-watch' "$TEST_ACTIONS"
grep -Fq 'compose ps -q invoice-worker-api' "$TEST_ACTIONS"
grep -Fq 'compose ps -q invoice-worker-ui' "$TEST_ACTIONS"
grep -Fq -- '--noproxy 127.0.0.1,localhost,::1 -fsS http://127.0.0.1:8081/health' "$TEST_ACTIONS"
grep -Fq -- '--noproxy 127.0.0.1,localhost,::1 -fsS http://127.0.0.1:8080/api/health' "$TEST_ACTIONS"
grep -Fq 'HTTP_PROXY=http://proxy.invalid:3128 HTTPS_PROXY=http://proxy.invalid:3128' "$TEST_ACTIONS"
grep -Fq 'compose --profile api --profile watch --profile ui stop invoice-worker invoice-worker-api invoice-worker-watch invoice-worker-ui' "$TEST_ACTIONS"

: > "$TEST_ACTIONS"
API_STATUS='running|starting' PATH="$TEST_DIRECTORY/bin:$PATH" \
    bash "$TEST_DIRECTORY/repository/scripts/dev-start.sh" full >"$TEST_DIRECTORY/wait-for-api.log" 2>&1
api_inspect_line="$(grep -n 'inspect .* invoice-worker-api$' "$TEST_ACTIONS" | head -n 1 | cut -d: -f1)"
first_curl_line="$(grep -n '^curl ' "$TEST_ACTIONS" | head -n 1 | cut -d: -f1)"
test "$(grep -c 'inspect .* invoice-worker-api$' "$TEST_ACTIONS")" -ge 2
test "$api_inspect_line" -lt "$first_curl_line"

if WATCH_STATUS='exited|unhealthy' PATH="$TEST_DIRECTORY/bin:$PATH" \
    bash "$TEST_DIRECTORY/repository/scripts/dev-start.sh" full >"$TEST_DIRECTORY/watch-failed.log" 2>&1; then
    printf 'Full start accepted a failed watch service.\n' >&2
    exit 1
fi
grep -Fq 'Service invoice-worker-watch failed (state=exited, health=unhealthy)' "$TEST_DIRECTORY/watch-failed.log"
grep -Fq 'docker compose logs --tail=100 invoice-worker-watch' "$TEST_DIRECTORY/watch-failed.log"

if WATCH_STATUS=missing PATH="$TEST_DIRECTORY/bin:$PATH" \
    bash "$TEST_DIRECTORY/repository/scripts/dev-start.sh" ui >"$TEST_DIRECTORY/watch-missing.log" 2>&1; then
    printf 'UI start accepted a missing watch service.\n' >&2
    exit 1
fi
grep -Fq 'Service invoice-worker-watch failed (state=not running, health=unknown)' "$TEST_DIRECTORY/watch-missing.log"
cd "$SOURCE_ROOT"
docker compose --env-file /dev/null --profile watch --profile ui config --format json >"$TEST_DIRECTORY/compose.json"
python3 - "$SOURCE_ROOT" "$TEST_DIRECTORY/compose.json" <<'PY'
import json, sys
from pathlib import Path
ROOT = Path(sys.argv[1])
with open(sys.argv[2], encoding="utf-8") as compose_file:
    config = json.load(compose_file)
services = config["services"]
ui = services["invoice-worker-ui"]
watch = services["invoice-worker-watch"]
ui_mount = next(volume for volume in ui["volumes"] if volume["target"] == "/data/input")
watch_mount = next(volume for volume in watch["volumes"] if volume["target"] == "/data/input")
assert ui_mount["source"] == watch_mount["source"]
assert not ui_mount.get("read_only", False)
assert ui["environment"]["INVOICE_UI_MANUAL_REVIEW_API_BASE_URI"] == "http://invoice-worker-api:8080/"
properties = (ROOT / "docker/application.properties").read_text().splitlines()
assert "ai.provider=openai" in properties
for name in ("invoice-worker-ui", "invoice-worker-api"):
    assert all(port["host_ip"] == "127.0.0.1" for port in services[name]["ports"])
print("Upload deployment tests passed.")
PY
