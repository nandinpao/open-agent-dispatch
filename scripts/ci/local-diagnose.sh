#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${PROJECT_NAME:-opendispatch-ci}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.ci.yml}"
ENV_FILE="${ENV_FILE:-deploy/env/.env.local.ci}"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_NAME="${2:?}"; shift 2 ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift 2 ;;
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --output-dir) CI_OUTPUT_DIR="${2:?}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"
compose_cmd=(docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

mkdir -p "${CI_OUTPUT_DIR}/diagnostics" "${CI_OUTPUT_DIR}/logs" "${CI_OUTPUT_DIR}/compose"
DIAG_FILE="${CI_OUTPUT_DIR}/diagnostics/diagnosis.txt"
: > "$DIAG_FILE"

say() {
  echo "$*" | tee -a "$DIAG_FILE"
}

http_code() {
  curl -sS -o /tmp/opendispatch-diagnose-body.$$ -w '%{http_code}' --connect-timeout 2 --max-time 5 "$1" 2>/tmp/opendispatch-diagnose-curl.$$ || echo "000"
}

classify_logs() {
  local service="$1"
  local log_file="${CI_OUTPUT_DIR}/logs/${service}.diagnose.log"
  "${compose_cmd[@]}" logs --no-color --tail=300 "$service" > "$log_file" 2>&1 || true
  if grep -qiE 'Could not find a production build|no such file.*BUILD_ID' "$log_file"; then
    say "[FAIL] ${service}: Admin UI production build is missing. Run make build-admin or make ci-local again."
  elif grep -qiE 'ECONNREFUSED.*localhost:18081|Failed to proxy http://localhost:18081' "$log_file"; then
    say "[FAIL] ${service}: Admin UI is proxying to localhost:18081 from inside a container. NETTY_BACKEND_ORIGIN must be http://netty:18081 and build-time rewrites must stay disabled."
  elif grep -qiE 'Unable to connect to localhost/.+:6379|Connection refused.*localhost/.+:6379' "$log_file"; then
    say "[FAIL] ${service}: Redis is configured as localhost from inside a container. Use redis:6379."
  elif grep -qiE 'relation "module_outbox_events" does not exist' "$log_file"; then
    say "[FAIL] ${service}: database migration did not complete before Core started. Check core-db-migrate logs."
  elif grep -qiE 'Address already in use|EADDRINUSE' "$log_file"; then
    say "[FAIL] ${service}: host/container port conflict detected. Run make ci-port-check and make ci-down-v."
  elif grep -qiE 'ERROR|Exception|Failed to start|Application run failed' "$log_file"; then
    say "[WARN] ${service}: errors or exceptions found. See ${log_file}."
  else
    say "[OK]   ${service}: no known failure pattern in recent logs."
  fi
}

say "OpenDispatch local diagnostic"
say "Project: ${PROJECT_NAME}"
say "Compose: ${COMPOSE_FILE}"
say "Env: ${ENV_FILE}"
say "Output: ${CI_OUTPUT_DIR}"
say ""

say "== Compose state =="
"${compose_cmd[@]}" ps | tee -a "$DIAG_FILE" || true
say ""

ADMIN_UI_HTTP_PORT="${ADMIN_UI_HTTP_PORT:-3000}"
CORE_HTTP_PORT="${CORE_HTTP_PORT:-18080}"
NETTY_ADMIN_HTTP_PORT="${NETTY_ADMIN_HTTP_PORT:-18081}"
ADAPTER_WORKER_HTTP_PORT="${ADAPTER_WORKER_HTTP_PORT:-18090}"
OTEL_COLLECTOR_HEALTH_PORT="${OTEL_COLLECTOR_HEALTH_PORT:-13133}"

say "== Endpoint health =="
for pair in \
  "Admin UI|http://127.0.0.1:${ADMIN_UI_HTTP_PORT}/api/health" \
  "Core health|http://127.0.0.1:${CORE_HTTP_PORT}/actuator/health" \
  "Core status|http://127.0.0.1:${CORE_HTTP_PORT}/api/core/status" \
  "Netty health|http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT}/api/admin/health" \
  "OTel Collector health|http://127.0.0.1:${OTEL_COLLECTOR_HEALTH_PORT}/" \
  "Adapter Worker health (smoke profile)|http://127.0.0.1:${ADAPTER_WORKER_HTTP_PORT}/actuator/health"; do
  label="${pair%%|*}"
  url="${pair#*|}"
  code="$(http_code "$url")"
  say "${label}: ${code} ${url}"
done
say ""

say "== Known failure classification =="
for svc in admin-ui core netty adapter-worker otel-collector postgres redis core-db-migrate; do
  classify_logs "$svc"
done
say ""

bash scripts/ci/local-report.sh --project "$PROJECT_NAME" --compose-file "$COMPOSE_FILE" --env-file "$ENV_FILE" --output-dir "$CI_OUTPUT_DIR" >/dev/null 2>&1 || true
say "Diagnosis written to ${DIAG_FILE}"
say "Markdown report: ${CI_OUTPUT_DIR}/report.md"
