#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="${CURRENT_FULL_LIVE_RELEASE_GATE_MODE:-live}"
if [[ "${1:-}" == "--dry-run" ]]; then
  MODE="dry-run"
elif [[ "${1:-}" == "--live" ]]; then
  MODE="live"
fi
RUN_ID="${CURRENT_FULL_LIVE_RUN_ID:-current-full-live-$(date -u +%Y%m%dT%H%M%SZ)}"
OUTPUT_DIR="${CURRENT_FULL_LIVE_OUTPUT_DIR:-${ROOT_DIR}/.ci-output/current-full-live-release-gate}"
RUN_DIR="${OUTPUT_DIR}/${RUN_ID}"
CI_OUTPUT_DIR_FOR_STACK="${RUN_DIR}/stack"
PROJECT_NAME="${CURRENT_FULL_LIVE_PROJECT_NAME:-opendispatch-phase7b}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.local.yml}"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/deploy/env/.env.local" ]]; then
    ENV_FILE="deploy/env/.env.local"
  else
    ENV_FILE="deploy/env/.env.local.example"
  fi
fi
CORE_URL="${CORE_URL:-${CORE_BASE_URL:-http://127.0.0.1:${CORE_HTTP_PORT:-18080}}}"
AGENT_ID="${CURRENT_FULL_LIVE_AGENT_ID:-agent-local-001}"
SOURCE_SYSTEM="${CURRENT_FULL_LIVE_SOURCE_SYSTEM:-SOURCE_X}"
TENANT_ID="${CURRENT_FULL_LIVE_TENANT_ID:-tenant-phase7b-current-live}"

mkdir -p "${RUN_DIR}/logs" "${RUN_DIR}/compose" "${RUN_DIR}/db" "${RUN_DIR}/http" "${RUN_DIR}/reports"

log() { echo "==> $*" | tee -a "${RUN_DIR}/steps.log"; }
warn() { echo "[WARN] $*" | tee -a "${RUN_DIR}/steps.log" >&2; }
fail() { echo "[ERROR] $*" | tee -a "${RUN_DIR}/steps.log" >&2; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"; }
compose() { docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"; }

write_manifest() {
  local result="$1"
  local notes="$2"
  cat > "${RUN_DIR}/manifest.json" <<JSON
{
  "runId": "${RUN_ID}",
  "phase": "7B",
  "gate": "current-full-live-release-gate",
  "mode": "${MODE}",
  "result": "${result}",
  "tenantId": "${TENANT_ID}",
  "sourceSystem": "${SOURCE_SYSTEM}",
  "agentId": "${AGENT_ID}",
  "projectName": "${PROJECT_NAME}",
  "coreUrl": "${CORE_URL}",
  "evidenceFiles": [
    "manifest.json",
    "steps.log",
    "http/http-transcript.json",
    "reports/acceptance-evidence.json",
    "logs/core.log",
    "logs/netty.log",
    "logs/mock-agent.log",
    "logs/admin-ui.log",
    "compose/ps.txt",
    "db/postgres-dump.sql"
  ],
  "requiredPath": [
    "Clean Stack",
    "Fresh DB Migration",
    "Upgrade DB Migration",
    "Source System",
    "Source Flow",
    "Default Pool",
    "Pool Member Agent",
    "Runtime Agent",
    "SourceSystem-only Event",
    "Task",
    "Assignment",
    "Delivery",
    "ACK",
    "Result",
    "COMPLETED"
  ],
  "notes": [${notes}]
}
JSON
}

collect_evidence() {
  log "Collect release evidence archive"
  if command -v docker >/dev/null 2>&1; then
    compose ps > "${RUN_DIR}/compose/ps.txt" 2>/dev/null || true
    compose logs --no-color --tail=1000 core > "${RUN_DIR}/logs/core.log" 2>/dev/null || true
    compose logs --no-color --tail=1000 netty > "${RUN_DIR}/logs/netty.log" 2>/dev/null || true
    compose logs --no-color --tail=1000 mock-agent > "${RUN_DIR}/logs/mock-agent.log" 2>/dev/null || true
    compose logs --no-color --tail=1000 admin-ui > "${RUN_DIR}/logs/admin-ui.log" 2>/dev/null || true
    compose logs --no-color --tail=1000 core-db-migrate postgres > "${RUN_DIR}/logs/migration-postgres.log" 2>/dev/null || true
    compose exec -T postgres sh -lc 'pg_dump -U "${POSTGRES_USER:-ai_event}" "${POSTGRES_DB:-ai_event_gateway}"' > "${RUN_DIR}/db/postgres-dump.sql" 2>"${RUN_DIR}/db/postgres-dump.err" || true
  fi
  if [[ -d "${CI_OUTPUT_DIR_FOR_STACK}/logs" ]]; then
    cp -R "${CI_OUTPUT_DIR_FOR_STACK}/logs/." "${RUN_DIR}/logs/" 2>/dev/null || true
  fi
  if [[ -d "${CI_OUTPUT_DIR_FOR_STACK}/compose" ]]; then
    cp -R "${CI_OUTPUT_DIR_FOR_STACK}/compose/." "${RUN_DIR}/compose/" 2>/dev/null || true
  fi
}

on_error() {
  local code=$?
  warn "Phase 7B full live release gate failed with exit code ${code}."
  collect_evidence || true
  write_manifest "FAIL" '"Failure diagnostics were collected. Inspect logs, HTTP transcript, compose state, and DB snapshot."'
  exit "${code}"
}
trap on_error ERR

cd "${ROOT_DIR}"
: > "${RUN_DIR}/steps.log"

if [[ "${MODE}" == "dry-run" ]]; then
  log "Run Phase 7B full live gate in dry-run contract mode"
  require_cmd node
  CURRENT_FULL_LIVE_DRY_RUN=true \
  CURRENT_FULL_LIVE_TENANT_ID="${TENANT_ID}" \
  CURRENT_FULL_LIVE_SOURCE_SYSTEM="${SOURCE_SYSTEM}" \
  CURRENT_FULL_LIVE_AGENT_ID="${AGENT_ID}" \
  CURRENT_FULL_LIVE_EVIDENCE_FILE="${RUN_DIR}/reports/acceptance-evidence.json" \
  CURRENT_FULL_LIVE_HTTP_TRANSCRIPT_FILE="${RUN_DIR}/http/http-transcript.json" \
    node scripts/acceptance/current-full-live-release-gate.mjs --dry-run | tee "${RUN_DIR}/logs/current-full-live-dry-run.log"
  write_manifest "PASS" '"Dry-run contract validation only; no live stack was started.", "Use CURRENT_FULL_LIVE_RELEASE_GATE_MODE=live make verify-e2e for the release-blocking live gate."'
  echo "Phase 7B dry-run evidence written to ${RUN_DIR}"
  exit 0
fi

log "Preflight live release-gate toolchain"
require_cmd java
require_cmd mvn
require_cmd node
require_cmd npm
require_cmd python3
require_cmd docker
docker compose version > "${RUN_DIR}/reports/docker-compose-version.txt"
java -version > "${RUN_DIR}/reports/java-version.txt" 2>&1 || true
mvn -version > "${RUN_DIR}/reports/maven-version.txt" 2>&1 || true
node -v > "${RUN_DIR}/reports/node-version.txt"
npm -v > "${RUN_DIR}/reports/npm-version.txt"
if ! grep -E 'version "25\.|openjdk 25\.' "${RUN_DIR}/reports/java-version.txt" >/dev/null 2>&1; then
  fail "Phase 7B live gate requires Java 25. See ${RUN_DIR}/reports/java-version.txt"
fi

log "Clean stack and remove volumes before fresh migration"
PROJECT_NAME="${PROJECT_NAME}" COMPOSE_FILE="${COMPOSE_FILE}" ENV_FILE="${ENV_FILE}" CI_OUTPUT_DIR="${CI_OUTPUT_DIR_FOR_STACK}" make down-v >/dev/null 2>&1 || true

log "Start clean local stack with mock/runtime Agent; this performs fresh DB migration"
PROJECT_NAME="${PROJECT_NAME}" \
COMPOSE_FILE="${COMPOSE_FILE}" \
ENV_FILE="${ENV_FILE}" \
CI_OUTPUT_DIR="${CI_OUTPUT_DIR_FOR_STACK}" \
WITH_AGENT=true \
CURRENT_FULL_LIVE_AGENT_ID="${AGENT_ID}" \
SOURCE_SYSTEM_ONLY_AGENT_ID="${AGENT_ID}" \
  ./scripts/ci/local-cd.sh | tee "${RUN_DIR}/logs/local-cd.log"

log "Run upgrade migration validation against already-migrated database"
compose run --rm core-db-migrate > "${RUN_DIR}/logs/upgrade-migration.log" 2>&1

log "Run Current full live acceptance scenario"
CORE_URL="${CORE_URL}" \
CURRENT_FULL_LIVE_TENANT_ID="${TENANT_ID}" \
CURRENT_FULL_LIVE_SOURCE_SYSTEM="${SOURCE_SYSTEM}" \
CURRENT_FULL_LIVE_AGENT_ID="${AGENT_ID}" \
CURRENT_FULL_LIVE_EVIDENCE_FILE="${RUN_DIR}/reports/acceptance-evidence.json" \
CURRENT_FULL_LIVE_HTTP_TRANSCRIPT_FILE="${RUN_DIR}/http/http-transcript.json" \
  node scripts/acceptance/current-full-live-release-gate.mjs | tee "${RUN_DIR}/logs/current-full-live-acceptance.log"

collect_evidence
write_manifest "PASS" '"Full live release gate completed on a clean stack.", "Evidence archive contains logs, HTTP transcript, compose state, and DB snapshot."'
log "Phase 7B full live release evidence written to ${RUN_DIR}"
