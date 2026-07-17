#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${PROJECT_NAME:-opendispatch-ci}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.ci.yml}"
ENV_FILE="${ENV_FILE:-deploy/env/.env.local.ci}"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
MODE="status"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_NAME="${2:?}"; shift 2 ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift 2 ;;
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --output-dir) CI_OUTPUT_DIR="${2:?}"; shift 2 ;;
    --urls) MODE="urls"; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"

CORE_HTTP_PORT="${CORE_HTTP_PORT:-18080}"
NETTY_ADMIN_HTTP_PORT="${NETTY_ADMIN_HTTP_PORT:-18081}"
ADMIN_UI_HTTP_PORT="${ADMIN_UI_HTTP_PORT:-3000}"
NETTY_TCP_PORT="${NETTY_TCP_PORT:-19090}"
POSTGRES_PUBLIC_PORT="${POSTGRES_PUBLIC_PORT:-15432}"
REDIS_PUBLIC_PORT="${REDIS_PUBLIC_PORT:-16379}"
ADAPTER_WORKER_HTTP_PORT="${ADAPTER_WORKER_HTTP_PORT:-18090}"
OTEL_COLLECTOR_HEALTH_PORT="${OTEL_COLLECTOR_HEALTH_PORT:-13133}"

compose_cmd=(docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

endpoint_status() {
  local label="$1"
  local url="$2"
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "$url" 2>/dev/null || true)"
  [[ -n "$code" ]] || code="000"
  printf '  %-22s %-45s %s\n' "$label" "$url" "$code"
}

print_urls() {
  echo "OpenDispatch local endpoints (${PROJECT_NAME})"
  echo "  Admin UI:       http://127.0.0.1:${ADMIN_UI_HTTP_PORT}"
  echo "  Core health:    http://127.0.0.1:${CORE_HTTP_PORT}/actuator/health"
  echo "  Core status:    http://127.0.0.1:${CORE_HTTP_PORT}/api/core/status"
  echo "  Netty health:   http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT}/api/admin/health"
  echo "  Netty TCP:      127.0.0.1:${NETTY_TCP_PORT}"
  echo "  PostgreSQL:     127.0.0.1:${POSTGRES_PUBLIC_PORT}"
  echo "  Redis:          127.0.0.1:${REDIS_PUBLIC_PORT}"
  echo "  OTel Collector: http://127.0.0.1:${OTEL_COLLECTOR_HEALTH_PORT}/"
  echo "  Adapter Worker: http://127.0.0.1:${ADAPTER_WORKER_HTTP_PORT} (observability-smoke profile)"
}

if [[ "$MODE" == "urls" ]]; then
  print_urls
  exit 0
fi

echo "OpenDispatch local stack status"
echo "Project:      ${PROJECT_NAME}"
echo "Compose file: ${COMPOSE_FILE}"
echo "Env file:     ${ENV_FILE}"
echo "Output dir:   ${CI_OUTPUT_DIR}"
echo ""
print_urls

echo ""
echo "Compose services:"
if ! "${compose_cmd[@]}" ps; then
  echo "  Compose stack is not running or Docker is unavailable."
fi

echo ""
echo "Endpoint checks:"
endpoint_status "Admin UI health" "http://127.0.0.1:${ADMIN_UI_HTTP_PORT}/api/health"
endpoint_status "Core actuator" "http://127.0.0.1:${CORE_HTTP_PORT}/actuator/health"
endpoint_status "Core status" "http://127.0.0.1:${CORE_HTTP_PORT}/api/core/status"
endpoint_status "Netty admin" "http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT}/api/admin/health"
endpoint_status "OTel Collector" "http://127.0.0.1:${OTEL_COLLECTOR_HEALTH_PORT}/"
endpoint_status "Adapter Worker" "http://127.0.0.1:${ADAPTER_WORKER_HTTP_PORT}/actuator/health"

echo ""
echo "Runtime artifacts:"
for artifact in core.jar netty.jar adapter-worker.jar admin-ui/.next/BUILD_ID; do
  path="${CI_OUTPUT_DIR}/runtime/${artifact}"
  if [[ -e "$path" ]]; then
    if [[ -f "$path" ]]; then
      printf '  %-32s present (%s bytes)\n' "$artifact" "$(wc -c < "$path" | tr -d ' ')"
    else
      printf '  %-32s present\n' "$artifact"
    fi
  else
    printf '  %-32s missing\n' "$artifact"
  fi
done

if [[ -f "${CI_OUTPUT_DIR}/images/runtime.env" ]]; then
  echo ""
  echo "Runtime image policy:"
  sed 's/^/  /' "${CI_OUTPUT_DIR}/images/runtime.env" || true
fi
