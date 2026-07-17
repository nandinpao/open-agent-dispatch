#!/usr/bin/env bash
set -euo pipefail

ACTION="${1:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

case "$ACTION" in
  up|down|down-v) ;;
  *) echo "Usage: $0 [up|down|down-v]" >&2; exit 2 ;;
esac

ENV_FILE="${STAGE8_MANAGED_ENV_FILE:-deploy/env/.env.local.example}"
COMPOSE_FILE="${STAGE8_MANAGED_COMPOSE_FILE:-deploy/docker-compose.local.yml}"
PROJECT_NAME="${STAGE8_MANAGED_PROJECT_NAME:-opendispatch-stage8}"
CI_OUTPUT_DIR="${STAGE8_MANAGED_OUTPUT_DIR:-$ROOT/.ci-output/stage8-managed-local}"
LOG_ROOT="${STAGE8_MANAGED_LOG_ROOT:-$CI_OUTPUT_DIR/logs}"
VOLUME_PREFIX="${STAGE8_MANAGED_VOLUME_PREFIX:-opendispatch-stage8}"

export ENV_FILE COMPOSE_FILE PROJECT_NAME CI_OUTPUT_DIR OPENDISPATCH_LOG_ROOT="$LOG_ROOT"
export LOCAL_DB_MIGRATION_VOLUME="${VOLUME_PREFIX}-db-migration-sql"
export LOCAL_CORE_RUNTIME_VOLUME="${VOLUME_PREFIX}-core-runtime"
export LOCAL_NETTY_RUNTIME_VOLUME="${VOLUME_PREFIX}-netty-runtime"
export LOCAL_ADAPTER_WORKER_RUNTIME_VOLUME="${VOLUME_PREFIX}-adapter-worker-runtime"
export LOCAL_ADMIN_UI_RUNTIME_VOLUME="${VOLUME_PREFIX}-admin-ui-runtime"
export LOCAL_MOCK_AGENT_E2E_VOLUME="${VOLUME_PREFIX}-mock-agent-e2e"

mkdir -p "$CI_OUTPUT_DIR" "$LOG_ROOT"

compose_down() {
  docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --profile agent down "$@" --remove-orphans
}

if [[ "$ACTION" == "down" ]]; then
  bash scripts/ci/local-admin-ui-host.sh stop --env-file "$ENV_FILE" --output-dir "$CI_OUTPUT_DIR" >/dev/null 2>&1 || true
  compose_down
  exit 0
fi

if [[ "$ACTION" == "down-v" ]]; then
  bash scripts/ci/local-admin-ui-host.sh stop --env-file "$ENV_FILE" --output-dir "$CI_OUTPUT_DIR" >/dev/null 2>&1 || true
  compose_down -v
  exit 0
fi

# A strict release gate needs fresh local evidence for Stage 1 before later
# Testcontainers/CI-release gates run. Use an isolated Compose project and
# named volumes so normal developer data under the default `opendispatch`
# project is not removed.
bash scripts/ci/local-admin-ui-host.sh stop --env-file "$ENV_FILE" --output-dir "$CI_OUTPUT_DIR" >/dev/null 2>&1 || true
compose_down -v >/dev/null 2>&1 || true
WITH_AGENT=true ./scripts/local-compose-up.sh
