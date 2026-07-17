#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${PROJECT_NAME:-opendispatch-ci}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.ci.yml}"
ENV_FILE="${ENV_FILE:-deploy/env/.env.local.ci}"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
REPORT_FILE="${CI_OUTPUT_DIR}/report.md"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_NAME="${2:?}"; shift 2 ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift 2 ;;
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --output-dir) CI_OUTPUT_DIR="${2:?}"; REPORT_FILE="${CI_OUTPUT_DIR}/report.md"; shift 2 ;;
    --report-file) REPORT_FILE="${2:?}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

mkdir -p "$CI_OUTPUT_DIR" "$(dirname "$REPORT_FILE")"
source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"

compose_cmd=(docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

http_code() {
  curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "$1" 2>/dev/null || echo "000"
}

summarize_surefire() {
  local root="$1"
  local total=0 failures=0 errors=0 skipped=0 files=0
  while IFS= read -r -d '' file; do
    files=$((files+1))
    local tests f e s
    tests="$(grep -o 'tests="[0-9]*"' "$file" | head -1 | sed 's/[^0-9]//g')"
    f="$(grep -o 'failures="[0-9]*"' "$file" | head -1 | sed 's/[^0-9]//g')"
    e="$(grep -o 'errors="[0-9]*"' "$file" | head -1 | sed 's/[^0-9]//g')"
    s="$(grep -o 'skipped="[0-9]*"' "$file" | head -1 | sed 's/[^0-9]//g')"
    total=$((total + ${tests:-0}))
    failures=$((failures + ${f:-0}))
    errors=$((errors + ${e:-0}))
    skipped=$((skipped + ${s:-0}))
  done < <(find "$root" -name 'TEST-*.xml' -type f -print0 2>/dev/null || true)
  echo "files=${files}, tests=${total}, failures=${failures}, errors=${errors}, skipped=${skipped}"
}

ADMIN_UI_HTTP_PORT="${ADMIN_UI_HTTP_PORT:-3000}"
CORE_HTTP_PORT="${CORE_HTTP_PORT:-18080}"
NETTY_ADMIN_HTTP_PORT="${NETTY_ADMIN_HTTP_PORT:-18081}"
NETTY_TCP_PORT="${NETTY_TCP_PORT:-19090}"
ADAPTER_WORKER_HTTP_PORT="${ADAPTER_WORKER_HTTP_PORT:-18090}"
OTEL_COLLECTOR_HEALTH_PORT="${OTEL_COLLECTOR_HEALTH_PORT:-13133}"

{
  echo "# OpenDispatch Local CI Runtime Report"
  echo ""
  echo "Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo ""
  echo "## Configuration"
  echo ""
  echo "| Key | Value |"
  echo "|---|---|"
  echo "| Project | ${PROJECT_NAME} |"
  echo "| Compose file | ${COMPOSE_FILE} |"
  echo "| Env file | ${ENV_FILE} |"
  echo "| Output dir | ${CI_OUTPUT_DIR} |"
  echo "| Java runtime | ${JAVA25_RUNTIME_IMAGE:-eclipse-temurin:25-jre} |"
  echo "| Node runtime | ${NODE_RUNTIME_IMAGE:-node:22-bookworm-slim} |"
  echo "| PostgreSQL | ${POSTGRES_IMAGE:-postgres:18-alpine} |"
  echo "| Redis | ${REDIS_IMAGE:-redis:8-alpine} |"
  echo "| OpenTelemetry Collector | ${OTEL_COLLECTOR_IMAGE:-otel/opentelemetry-collector-contrib:0.156.0} |"
  echo ""
  echo "## Endpoints"
  echo ""
  echo "| Service | URL | HTTP |"
  echo "|---|---|---:|"
  echo "| Admin UI health | http://127.0.0.1:${ADMIN_UI_HTTP_PORT}/api/health | $(http_code "http://127.0.0.1:${ADMIN_UI_HTTP_PORT}/api/health") |"
  echo "| Core health | http://127.0.0.1:${CORE_HTTP_PORT}/actuator/health | $(http_code "http://127.0.0.1:${CORE_HTTP_PORT}/actuator/health") |"
  echo "| Core status | http://127.0.0.1:${CORE_HTTP_PORT}/api/core/status | $(http_code "http://127.0.0.1:${CORE_HTTP_PORT}/api/core/status") |"
  echo "| Netty admin health | http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT}/api/admin/health | $(http_code "http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT}/api/admin/health") |"
  echo "| Netty TCP | 127.0.0.1:${NETTY_TCP_PORT} | n/a |"
  echo "| OTel Collector health | http://127.0.0.1:${OTEL_COLLECTOR_HEALTH_PORT}/ | $(http_code "http://127.0.0.1:${OTEL_COLLECTOR_HEALTH_PORT}/") |"
  echo "| Adapter Worker health (smoke profile) | http://127.0.0.1:${ADAPTER_WORKER_HTTP_PORT}/actuator/health | $(http_code "http://127.0.0.1:${ADAPTER_WORKER_HTTP_PORT}/actuator/health") |"
  echo ""
  echo "## Compose Services"
  echo ""
  echo '```text'
  "${compose_cmd[@]}" ps 2>&1 || true
  echo '```'
  echo ""
  echo "## Runtime Artifacts"
  echo ""
  echo "| Artifact | Status |"
  echo "|---|---|"
  for artifact in core.jar netty.jar adapter-worker.jar admin-ui/.next/BUILD_ID; do
    path="${CI_OUTPUT_DIR}/runtime/${artifact}"
    if [[ -e "$path" ]]; then
      echo "| ${artifact} | present |"
    else
      echo "| ${artifact} | missing |"
    fi
  done
  echo ""
  echo "## Test Reports"
  echo ""
  echo "| Area | Summary |"
  echo "|---|---|"
  echo "| Core Surefire | $(summarize_surefire "${CI_OUTPUT_DIR}/reports/core-surefire") |"
  echo "| Netty Surefire | $(summarize_surefire "${CI_OUTPUT_DIR}/reports/netty-surefire") |"
  echo ""
  echo "## Recent Logs"
  echo ""
  for svc in core netty adapter-worker otel-collector admin-ui postgres redis core-db-migrate; do
    echo "### ${svc}"
    echo '```text'
    "${compose_cmd[@]}" logs --no-color --tail=60 "$svc" 2>&1 || true
    echo '```'
    echo ""
  done
} > "$REPORT_FILE"

echo "Local CI report written to ${REPORT_FILE}"
