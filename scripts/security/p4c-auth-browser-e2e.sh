#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${P4C_PROJECT_NAME:-${PROJECT_NAME:-opendispatch}}"
ENV_FILE="${P4C_ENV_FILE:-${ENV_FILE:-deploy/env/.env.local.example}}"
COMPOSE_FILE="${P4C_COMPOSE_FILE:-${COMPOSE_FILE:-deploy/docker-compose.local.yml}}"
BASE_URL="${P4C_BASE_URL:-http://127.0.0.1:${ADMIN_UI_HTTP_PORT:-3000}}"
TEST_SESSION_TIMEOUT="${P4C_TEST_SESSION_TIMEOUT:-5s}"
RESTORE_SESSION_TIMEOUT="${P4C_RESTORE_SESSION_TIMEOUT:-${CORE_ADMIN_SESSION_TIMEOUT:-30m}}"
EXPIRY_WAIT_MS="${P4C_SESSION_EXPIRY_WAIT_MS:-6500}"
WAIT_SECONDS="${P4C_WAIT_SECONDS:-180}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }
command -v node >/dev/null 2>&1 || { echo "node is required" >&2; exit 2; }
docker compose version >/dev/null 2>&1 || { echo "docker compose is required" >&2; exit 2; }
[[ -f "$ENV_FILE" ]] || { echo "P4-C env file not found: $ENV_FILE" >&2; exit 2; }
[[ -f "$COMPOSE_FILE" ]] || { echo "P4-C compose file not found: $COMPOSE_FILE" >&2; exit 2; }

COMPOSE=(docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
restored=0
restore_stack() {
  local status=$?
  if [[ "$restored" -eq 0 ]]; then
    restored=1
    echo "==> Restore Core Admin session timeout to $RESTORE_SESSION_TIMEOUT"
    CORE_ADMIN_SESSION_TIMEOUT="$RESTORE_SESSION_TIMEOUT" "${COMPOSE[@]}" up -d --force-recreate core admin-ui >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap restore_stack EXIT INT TERM

echo "==> Start P4-C smoke stack with Core Admin session timeout $TEST_SESSION_TIMEOUT"
CORE_ADMIN_SESSION_TIMEOUT="$TEST_SESSION_TIMEOUT" "${COMPOSE[@]}" up -d --force-recreate core netty admin-ui

echo "==> Wait for Admin UI at $BASE_URL/api/health"
deadline=$((SECONDS + WAIT_SECONDS))
until curl --fail --silent --show-error "$BASE_URL/api/health" >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    echo "P4-C Admin UI health check timed out." >&2
    "${COMPOSE[@]}" ps >&2 || true
    "${COMPOSE[@]}" logs --tail=160 core netty admin-ui >&2 || true
    exit 1
  fi
  sleep 2
done

echo "==> Run P4-C Browser E2E"
P4C_BASE_URL="$BASE_URL" \
P4C_SESSION_EXPIRY_WAIT_MS="$EXPIRY_WAIT_MS" \
node ai-event-gateway-admin-ui/scripts/p4c-auth-browser-e2e.mjs

echo "P4-C Browser E2E completed successfully."
