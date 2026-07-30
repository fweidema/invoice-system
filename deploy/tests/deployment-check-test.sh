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
REAL_GIT="$(command -v git)"
readonly REAL_GIT
readonly TEST_REPOSITORY="$TEST_DIRECTORY/repository"
readonly FAKE_BIN="$TEST_DIRECTORY/bin"

mkdir -p "$TEST_REPOSITORY/deploy/lib" "$TEST_REPOSITORY/deploy/tests" \
    "$TEST_REPOSITORY/scripts" "$TEST_REPOSITORY/docker" "$TEST_REPOSITORY/runtime/database" \
    "$TEST_REPOSITORY/runtime/archive" "$TEST_REPOSITORY/runtime/manual-review" "$FAKE_BIN"

cp "$SOURCE_ROOT/deploy/lib/common.sh" "$TEST_REPOSITORY/deploy/lib/common.sh"
cp "$SOURCE_ROOT/deploy/backup-staging.sh" "$TEST_REPOSITORY/deploy/backup-staging.sh"
cp "$SOURCE_ROOT/deploy/check-staging.sh" "$TEST_REPOSITORY/deploy/check-staging.sh"
cp "$SOURCE_ROOT/deploy/deploy-staging.sh" "$TEST_REPOSITORY/deploy/deploy-staging.sh"
printf 'ai.provider=mock\n' >"$TEST_REPOSITORY/docker/application.properties"
printf 'database\n' >"$TEST_REPOSITORY/runtime/database/invoice-system.db"

cat >"$TEST_REPOSITORY/scripts/prepare-runtime.sh" <<EOF
#!/usr/bin/env bash
set -eu
printf '%s\n' prepare-runtime >>"$TEST_DIRECTORY/actions"
EOF
cat >"$TEST_REPOSITORY/deploy/fix-runtime-permissions.sh" <<EOF
#!/usr/bin/env bash
set -eu
printf '%s\n' fix-runtime-permissions >>"$TEST_DIRECTORY/actions"
EOF
cat >"$TEST_REPOSITORY/mvnw" <<EOF
#!/usr/bin/env bash
set -eu
printf 'mvnw %s\n' "\$*" >>"$TEST_DIRECTORY/actions"
EOF

cat >"$FAKE_BIN/git" <<EOF
#!/usr/bin/env bash
set -eu
printf 'git %s\n' "\$*" >>"$TEST_DIRECTORY/actions"
case "\$*" in
    "branch --show-current") printf 'main\n' ;;
    "status --porcelain --untracked-files=no") ;;
    "pull --ff-only origin main") ;;
    "rev-parse --verify HEAD"|"-C $TEST_REPOSITORY rev-parse --verify HEAD") printf 'abcdef123456\n' ;;
    "describe --always --dirty --tags") printf 'v0.2.0-test\n' ;;
    *) printf 'Unexpected git arguments: %s\n' "\$*" >&2; exit 2 ;;
esac
EOF

cat >"$FAKE_BIN/sqlite3" <<'EOF'
#!/usr/bin/env bash
set -eu
database="$1"
statement="$2"
case "$statement" in
    ".backup '"*)
        destination="${statement#".backup '"}"
        destination="${destination%\'}"
        cp -- "$database" "$destination"
        ;;
    "PRAGMA quick_check;")
        printf 'ok\n'
        ;;
    *)
        exit 2
        ;;
esac
EOF

cat >"$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
set -eu
url="${!#}"
case "$url" in
    */api/health) printf '{"status":"UP"}\n' ;;
    */health) printf 'OK\n' ;;
    *) exit 22 ;;
esac
EOF

cat >"$FAKE_BIN/docker" <<EOF
#!/usr/bin/env bash
set -eu
printf 'docker %s\n' "\$*" >>"$TEST_DIRECTORY/actions"
case "\$*" in
    "info") ;;
    "compose --profile watch --profile ui config --quiet") ;;
    "compose --profile watch --profile ui ps -q "*) printf 'container-id\n' ;;
    "inspect --format {{.State.Status}} container-id") printf 'running\n' ;;
    "inspect --format {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} container-id") printf 'healthy\n' ;;
    "inspect --format {{index .Config.Labels \"org.opencontainers.image.version\"}} container-id") printf 'v0.2.0-test\n' ;;
    "inspect --format {{index .Config.Labels \"org.opencontainers.image.revision\"}} container-id") printf 'abcdef123456\n' ;;
    "compose --profile watch --profile ui build") ;;
    "compose --profile watch --profile ui up -d --remove-orphans invoice-worker-watch invoice-worker-api invoice-worker-ui") ;;
    *) printf 'Unexpected docker arguments: %s\n' "\$*" >&2; exit 2 ;;
esac
EOF

chmod +x "$TEST_REPOSITORY/deploy/backup-staging.sh" "$TEST_REPOSITORY/deploy/check-staging.sh" \
    "$TEST_REPOSITORY/deploy/deploy-staging.sh" "$TEST_REPOSITORY/deploy/fix-runtime-permissions.sh" \
    "$TEST_REPOSITORY/scripts/prepare-runtime.sh" \
    "$TEST_REPOSITORY/mvnw" "$FAKE_BIN/git" "$FAKE_BIN/sqlite3" "$FAKE_BIN/curl" "$FAKE_BIN/docker"

printf 'runtime/\nbackup/\n' >"$TEST_REPOSITORY/.gitignore"
"$REAL_GIT" -C "$TEST_REPOSITORY" init -q
"$REAL_GIT" -C "$TEST_REPOSITORY" add .
"$REAL_GIT" -C "$TEST_REPOSITORY" \
    -c user.name='Deployment Test' \
    -c user.email='deployment-test@example.invalid' \
    commit -q -m 'test fixture'

export PATH="$FAKE_BIN:$PATH"
export STAGING_TIMESTAMP="20260730T120000Z"
export STAGING_CHECK_ATTEMPTS=1
export STAGING_CHECK_INTERVAL_SECONDS=0

"$TEST_REPOSITORY/deploy/deploy-staging.sh"

grep -qx 'prepare-runtime' "$TEST_DIRECTORY/actions"
if grep -qx 'fix-runtime-permissions' "$TEST_DIRECTORY/actions"; then
    printf 'Deployment unexpectedly invoked runtime permission repair.\n' >&2
    exit 1
fi
grep -qx 'git pull --ff-only origin main' "$TEST_DIRECTORY/actions"
grep -qx 'mvnw clean verify' "$TEST_DIRECTORY/actions"
grep -qx 'docker compose --profile watch --profile ui build' "$TEST_DIRECTORY/actions"
grep -qx 'docker compose --profile watch --profile ui up -d --remove-orphans invoice-worker-watch invoice-worker-api invoice-worker-ui' \
    "$TEST_DIRECTORY/actions"
test -f "$TEST_REPOSITORY/backup/staging/20260730T120000Z/database/invoice-system.db"
[ -z "$("$REAL_GIT" -C "$TEST_REPOSITORY" status --porcelain)" ] || {
    printf 'Deployment changed the temporary Git checkout.\n' >&2
    "$REAL_GIT" -C "$TEST_REPOSITORY" status --short >&2
    exit 1
}

printf 'Deployment and staging check tests passed.\n'
