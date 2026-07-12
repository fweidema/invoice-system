#!/usr/bin/env bash
set -euo pipefail

uid=10001
gid=10001
runtime_dirs=(
  "runtime/input"
  "runtime/ocr"
  "runtime/archive"
  "runtime/database"
  "runtime/logs"
)

echo "Preparing invoice-system runtime directories"
for dir in "${runtime_dirs[@]}"; do
  mkdir -p "$dir"
  echo "Ensured $dir"
done

if command -v chown >/dev/null 2>&1; then
  if [ "$(id -u)" -eq 0 ]; then
    chown -R "$uid:$gid" runtime
    echo "Set owner of runtime/ to $uid:$gid"
  elif command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
    sudo chown -R "$uid:$gid" runtime
    echo "Set owner of runtime/ to $uid:$gid via sudo"
  else
    echo "Skipping chown because root or passwordless sudo is unavailable"
  fi
fi

chmod -R u+rwX,g+rwX runtime
echo "Runtime preparation complete"