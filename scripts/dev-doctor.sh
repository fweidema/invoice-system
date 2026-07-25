#!/usr/bin/env bash
set -u

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root" || exit 2

failures=0
warnings=0
compose_mode="none"

ok() {
  echo "OK: $*"
}

warn() {
  warnings=$((warnings + 1))
  echo "WARN: $*" >&2
}

fail() {
  failures=$((failures + 1))
  echo "FAIL: $*" >&2
}

check_command() {
  if command -v "$1" >/dev/null 2>&1; then
    ok "$1 found"
  else
    fail "$1 not found"
  fi
}

powershell_available() {
  command -v powershell.exe >/dev/null 2>&1 || command -v pwsh.exe >/dev/null 2>&1
}

run_powershell() {
  if command -v pwsh.exe >/dev/null 2>&1; then
    pwsh.exe -NoProfile -Command "$1"
  else
    powershell.exe -NoProfile -Command "$1"
  fi
}

docker_compose_available() {
  if docker compose version >/dev/null 2>&1; then
    compose_mode="bash"
    return 0
  fi
  if powershell_available && run_powershell "docker compose version" >/dev/null 2>&1; then
    compose_mode="powershell"
    return 0
  fi
  return 1
}

docker_compose_ps() {
  if [ "$compose_mode" = "bash" ]; then
    docker compose ps
  elif [ "$compose_mode" = "powershell" ]; then
    run_powershell "Set-Location -LiteralPath '$repo_root'; docker compose ps"
  fi
}

docker_compose_watch_running() {
  if [ "$compose_mode" = "bash" ]; then
    docker compose ps --status running 2>/dev/null | grep -q "invoice-worker-watch"
  elif [ "$compose_mode" = "powershell" ]; then
    run_powershell "Set-Location -LiteralPath '$repo_root'; docker compose ps --status running" 2>/dev/null | grep -q "invoice-worker-watch"
  else
    return 1
  fi
}

echo "invoice-system development doctor"

java_version=""
if command -v java >/dev/null 2>&1; then
  java_version="$(java -version 2>&1 | head -n 1)"
elif [ -f "./mvnw.cmd" ]; then
  java_version="$(./mvnw.cmd -version 2>/dev/null | grep -m 1 "Java version" || true)"
fi

if [ -n "$java_version" ]; then
  case "$java_version" in
    *"21"*|*"21."*) ok "Java 21 detected: $java_version" ;;
    *) fail "Java 21 required, detected: $java_version" ;;
  esac
else
  fail "java not found"
fi

if [ -x "./mvnw" ] || [ -f "./mvnw.cmd" ]; then
  ok "Maven Wrapper found"
else
  fail "Maven Wrapper missing"
fi

check_command git

if command -v docker >/dev/null 2>&1 || powershell_available; then
  ok "Docker command path available or PowerShell fallback available"
  if docker_compose_available; then
    ok "Docker Compose found ($compose_mode)"
  else
    fail "docker compose is unavailable"
  fi
else
  warn "Docker not found; Docker/VPS checks skipped"
fi

runtime_dirs=(runtime/input runtime/ocr runtime/archive runtime/database runtime/logs)
for dir in "${runtime_dirs[@]}"; do
  if [ ! -d "$dir" ]; then
    mkdir -p "$dir" 2>/dev/null && ok "Created $dir" || fail "Could not create $dir"
  fi
  if [ -d "$dir" ] && [ -w "$dir" ]; then
    ok "$dir writable"
  elif [ -d "$dir" ]; then
    fail "$dir is not writable"
  fi
done

database="runtime/database/invoice-system.db"
if [ -e "$database" ]; then
  if [ -w "$database" ]; then
    ok "$database writable"
  else
    fail "$database is not writable"
  fi
else
  ok "$database does not exist yet; parent directory is checked"
fi

if command -v sqlite3 >/dev/null 2>&1; then
  sqlite3 ":memory:" "select sqlite_version();" >/dev/null 2>&1 && ok "sqlite3 smoke test passed" || fail "sqlite3 smoke test failed"
else
  warn "sqlite3 CLI not found; application uses sqlite-jdbc as fallback"
fi

api_port="${INVOICE_API_PORT:-8080}"
if command -v lsof >/dev/null 2>&1; then
  lsof -iTCP:"$api_port" -sTCP:LISTEN >/dev/null 2>&1 && warn "Port $api_port is already in use" || ok "Port $api_port is free"
elif command -v netstat >/dev/null 2>&1; then
  netstat -an | grep -E "[.:]${api_port}[[:space:]].*LISTEN" >/dev/null 2>&1 && warn "Port $api_port is already in use" || ok "Port $api_port is free"
else
  warn "No lsof/netstat available for port check"
fi

if [ "$compose_mode" != "none" ]; then
  docker_compose_ps
fi

health_url="http://127.0.0.1:${api_port}/api/health"
if command -v curl >/dev/null 2>&1; then
  curl -fsS "$health_url" >/dev/null 2>&1 && ok "API healthcheck passed" || warn "API healthcheck unavailable at $health_url"
else
  warn "curl not found; API healthcheck skipped"
fi

if docker_compose_watch_running; then
  ok "Watch service container is running"
else
  warn "Watch service container is not running"
fi

if [ -n "${OPENAI_API_KEY:-}" ]; then
  ok "OPENAI_API_KEY is set"
else
  ok "OPENAI_API_KEY is not set; mock provider works without it"
fi

current_branch="$(git branch --show-current 2>/dev/null)"
[ -n "$current_branch" ] && ok "Git branch: $current_branch" || warn "Could not determine Git branch"

if git diff --quiet --ignore-submodules -- 2>/dev/null && git diff --cached --quiet --ignore-submodules -- 2>/dev/null; then
  ok "Git working tree has no tracked modifications"
else
  warn "Git working tree has tracked modifications"
fi

if [ "$failures" -gt 0 ]; then
  echo "Doctor finished with $failures failure(s) and $warnings warning(s)." >&2
  exit 1
fi

echo "Doctor finished with $warnings warning(s)."