#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${PROJECT_NAME:-opendispatch-phase13}"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "$ENV_FILE" ]]; then
  if [[ -f "$ROOT_DIR/deploy/env/.env.local" ]]; then
    ENV_FILE="$ROOT_DIR/deploy/env/.env.local"
  else
    ENV_FILE="$ROOT_DIR/deploy/env/.env.local.example"
  fi
fi
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/deploy/docker-compose.local.yml}"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-$ROOT_DIR/.ci-output}"
PHASE13_OUTPUT_DIR="${PHASE13_OUTPUT_DIR:-$CI_OUTPUT_DIR/stage13-live-golden-path}"
PHASE13_SKIP_COMPOSE="${PHASE13_SKIP_COMPOSE:-false}"
PHASE13_KEEP_STACK="${PHASE13_KEEP_STACK:-false}"
PHASE13_TENANT_ID="${PHASE13_TENANT_ID:-${STAGE13_TENANT_ID:-tenant-a}}"
PHASE13_BASE_URL="${PHASE13_BASE_URL:-${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:3000}}"
PHASE13_ADMIN_USERNAME="${PHASE13_ADMIN_USERNAME:-${STAGE9_ADMIN_USERNAME:-admin}}"
PHASE13_ADMIN_PASSWORD="${PHASE13_ADMIN_PASSWORD:-${STAGE9_ADMIN_PASSWORD:-local-admin-change-me}}"

mkdir -p "$PHASE13_OUTPUT_DIR"

fail() {
  echo "[phase13] ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required for Phase 13 live stack golden path gate."
}

require_cmd java
require_cmd mvn
require_cmd docker
require_cmd node
require_cmd npm
require_cmd python3

JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -n 1)"
if ! echo "$JAVA_VERSION_OUTPUT" | grep -Eq '"25\.|version "25'; then
  fail "JDK 25 is required for Phase 13. Found: $JAVA_VERSION_OUTPUT"
fi

if [[ ! -f "$ENV_FILE" ]]; then
  fail "Env file not found: $ENV_FILE"
fi

cleanup_on_failure() {
  local exit_code="$?"
  if [[ "$exit_code" -ne 0 ]]; then
    echo "[phase13] failed; collecting diagnostics into $PHASE13_OUTPUT_DIR" >&2
    docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps >"$PHASE13_OUTPUT_DIR/docker-compose-ps.txt" 2>&1 || true
    docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=500 >"$PHASE13_OUTPUT_DIR/docker-compose-logs.txt" 2>&1 || true
  fi
  if [[ "$PHASE13_KEEP_STACK" != "true" && "$PHASE13_SKIP_COMPOSE" != "true" ]]; then
    docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down --remove-orphans >"$PHASE13_OUTPUT_DIR/docker-compose-down.log" 2>&1 || true
  fi
  exit "$exit_code"
}
trap cleanup_on_failure EXIT

if [[ "$PHASE13_SKIP_COMPOSE" != "true" ]]; then
  echo "[phase13] starting local live stack with mock Agent profile"
  WITH_AGENT=true PROJECT_NAME="$PROJECT_NAME" ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" CI_OUTPUT_DIR="$CI_OUTPUT_DIR" \
    "$ROOT_DIR/scripts/local-compose-up.sh" | tee "$PHASE13_OUTPUT_DIR/local-compose-up.log"
else
  echo "[phase13] PHASE13_SKIP_COMPOSE=true; using already-running live stack at $PHASE13_BASE_URL"
  "$ROOT_DIR/scripts/local-smoke.sh" | tee "$PHASE13_OUTPUT_DIR/local-smoke.log"
fi

# Run the browser proof against the live stack. This is intentionally not a
# mock-only test; the Playwright spec will fail if mock flags are enabled or
# ACK/RESULT evidence is not present.
cd "$ROOT_DIR/ai-event-gateway-admin-ui"
npm ci | tee "$PHASE13_OUTPUT_DIR/admin-ui-npm-ci.log"
npm run typecheck | tee "$PHASE13_OUTPUT_DIR/admin-ui-typecheck.log"
PLAYWRIGHT_BASE_URL="$PHASE13_BASE_URL" \
STAGE9_TENANT_ID="$PHASE13_TENANT_ID" \
STAGE9_ADMIN_USERNAME="$PHASE13_ADMIN_USERNAME" \
STAGE9_ADMIN_PASSWORD="$PHASE13_ADMIN_PASSWORD" \
NEXT_PUBLIC_USE_MOCK=false \
npm run stage13:live-golden-path | tee "$PHASE13_OUTPUT_DIR/playwright-stage13-live-golden-path.log"

echo "[phase13] live stack golden path passed"
