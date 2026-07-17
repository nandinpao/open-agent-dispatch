#!/usr/bin/env bash
set -euo pipefail

PROJECT="opendispatch-ci"
COMPOSE_FILE="deploy/docker-compose.ci.yml"
ENV_FILE="deploy/env/.env.local.ci"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
SELF_CHECK="false"

usage() {
  cat <<USAGE
Usage: $0 [--project <name>] [--compose-file <path>] [--env-file <path>] [--timeout-seconds <seconds>] [--self-check]

Run OpenDispatch smoke checks against an already-started compose stack.
--self-check validates script/package paths only and does not contact Docker or HTTP endpoints.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --timeout-seconds) TIMEOUT_SECONDS="$2"; shift 2 ;;
    --self-check) SELF_CHECK="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "${SCRIPT_DIR}/env-utils.sh" && -f "${SCRIPT_DIR}/../deploy/docker-compose.release.yml" ]]; then
  # Release package layout: bin/local-smoke.sh + bin/env-utils.sh.
  ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
  ENV_UTILS_FILE="${SCRIPT_DIR}/env-utils.sh"
else
  # Source repository layout: scripts/ci/local-smoke.sh + scripts/ci/env-utils.sh.
  ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
  ENV_UTILS_FILE="${ROOT_DIR}/scripts/ci/env-utils.sh"
fi

if [[ "${COMPOSE_FILE}" != /* ]]; then
  COMPOSE_FILE="${ROOT_DIR}/${COMPOSE_FILE}"
fi
if [[ "${ENV_FILE}" != /* ]]; then
  ENV_FILE="${ROOT_DIR}/${ENV_FILE}"
fi

cd "$ROOT_DIR"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
mkdir -p "${CI_OUTPUT_DIR}/logs" "${CI_OUTPUT_DIR}/reports" "${CI_OUTPUT_DIR}/compose"

[[ -f "${ENV_UTILS_FILE}" ]] || { echo "Missing env utils: ${ENV_UTILS_FILE}" >&2; exit 1; }
[[ -f "${COMPOSE_FILE}" ]] || { echo "Missing compose file: ${COMPOSE_FILE}" >&2; exit 1; }
[[ -f "${ENV_FILE}" ]] || { echo "Missing env file: ${ENV_FILE}" >&2; exit 1; }

# shellcheck source=scripts/ci/env-utils.sh
source "${ENV_UTILS_FILE}"
load_dotenv_file "$ENV_FILE"

if [[ "${SELF_CHECK}" == "true" ]]; then
  echo "OpenDispatch smoke self-check passed."
  echo "ROOT_DIR=${ROOT_DIR}"
  echo "COMPOSE_FILE=${COMPOSE_FILE}"
  echo "ENV_FILE=${ENV_FILE}"
  exit 0
fi

PUBLIC_HOST="${OPENDISPATCH_PUBLIC_HOST:-${CI_PUBLIC_HOST:-}}"
PUBLIC_SCHEME="${OPENDISPATCH_PUBLIC_SCHEME:-http}"
is_loopback_endpoint() {
  case "${1:-}" in
    localhost|127.0.0.1|0.0.0.0|http://localhost:*|http://127.0.0.1:*|http://0.0.0.0:*|https://localhost:*|https://127.0.0.1:*|https://0.0.0.0:*) return 0 ;;
    *) return 1 ;;
  esac
}
if [[ -n "${PUBLIC_HOST}" ]]; then
  if [[ -z "${CORE_URL:-}" ]] || is_loopback_endpoint "${CORE_URL}"; then
    CORE_URL="${PUBLIC_SCHEME}://${PUBLIC_HOST}:${CORE_HTTP_PORT:-18080}"
  fi
  if [[ -z "${NETTY_URL:-}" ]] || is_loopback_endpoint "${NETTY_URL}"; then
    NETTY_URL="${PUBLIC_SCHEME}://${PUBLIC_HOST}:${NETTY_ADMIN_HTTP_PORT:-18081}"
  fi
  if [[ -z "${ADMIN_UI_URL:-}" ]] || is_loopback_endpoint "${ADMIN_UI_URL}"; then
    ADMIN_UI_URL="${PUBLIC_SCHEME}://${PUBLIC_HOST}:${ADMIN_UI_HTTP_PORT:-3000}"
  fi
  if [[ -z "${NETTY_TCP_HOST:-}" ]] || is_loopback_endpoint "${NETTY_TCP_HOST}"; then
    NETTY_TCP_HOST="${PUBLIC_HOST}"
  fi
else
  CORE_URL="${CORE_URL:-http://127.0.0.1:${CORE_HTTP_PORT:-18080}}"
  NETTY_URL="${NETTY_URL:-http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT:-18081}}"
  ADMIN_UI_URL="${ADMIN_UI_URL:-http://127.0.0.1:${ADMIN_UI_HTTP_PORT:-3000}}"
  NETTY_TCP_HOST="${NETTY_TCP_HOST:-127.0.0.1}"
fi
NETTY_TCP_PORT="${NETTY_TCP_PORT:-19090}"
SKIP_ADMIN_UI_SMOKE="${SKIP_ADMIN_UI_SMOKE:-false}"
RUN_RUNTIME_LIFECYCLE_E2E="${RUN_RUNTIME_LIFECYCLE_E2E:-false}"

has_cmd() { command -v "$1" >/dev/null 2>&1; }

http_status() {
  local url="$1"
  local body_file="$2"
  local status
  local token_header_name="${CORE_INTERNAL_TOKEN_HEADER:-X-Cluster-Token}"
  local token_value="${CLUSTER_INTERNAL_TOKEN:-}"
  if has_cmd curl; then
    if [[ -n "${token_value}" ]]; then
      status="$(curl -sS --max-time 5 -H "${token_header_name}: ${token_value}" -o "$body_file" -w '%{http_code}' "$url" 2>"${body_file}.err" || true)"
    else
      status="$(curl -sS --max-time 5 -o "$body_file" -w '%{http_code}' "$url" 2>"${body_file}.err" || true)"
    fi
  elif has_cmd wget; then
    local wget_args=(-qO "$body_file")
    if [[ -n "${token_value}" ]]; then
      wget_args+=(--header="${token_header_name}: ${token_value}")
    fi
    if wget "${wget_args[@]}" "$url" 2>"${body_file}.err"; then
      status="200"
    else
      status="000"
    fi
  else
    echo "Neither curl nor wget is available." >&2
    return 2
  fi
  printf '%s' "${status:-000}"
}

http_success() {
  local url="$1"
  local body_file="$2"
  local status
  status="$(http_status "$url" "$body_file")"
  [[ "$status" =~ ^2[0-9][0-9]$ ]]
}

wait_http_any() {
  local name="$1"; shift
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local body_file="${CI_OUTPUT_DIR}/reports/$(echo "$name" | tr '[:upper:] /:' '[:lower:]___').body"
  local err_file="${body_file}.err"
  local candidate status last_candidate=""
  printf 'Waiting for %s' "$name"
  while true; do
    for candidate in "$@"; do
      last_candidate="$candidate"
      status="$(http_status "$candidate" "$body_file")"
      if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
        echo " OK"
        return 0
      fi
    done
    if (( SECONDS >= deadline )); then
      echo
      echo "Timed out waiting for ${name}. Tried: $*" >&2
      echo "Last URL: ${last_candidate}" >&2
      echo "Last HTTP status: ${status:-unknown}" >&2
      if [[ -s "$body_file" ]]; then
        echo "Last response body:" >&2
        sed -n '1,200p' "$body_file" >&2 || true
      fi
      if [[ -s "$err_file" ]]; then
        echo "Last HTTP client stderr:" >&2
        sed -n '1,80p' "$err_file" >&2 || true
      fi
      if [[ "$name" == *"Core"* ]]; then
        echo "Recent Core logs:" >&2
        docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=240 core redis postgres core-db-migrate >&2 || true
      fi
      if [[ "$name" == *"Admin UI"* ]]; then
        echo "Recent Admin UI container log:" >&2
        docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=240 admin-ui >&2 || true
      fi
      docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps >&2 || true
      docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=200 >&2 || true
      return 1
    fi
    printf '.'
    sleep 2
  done
}

wait_core_health_up() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local health_url="${CORE_URL}/actuator/health"
  local liveness_url="${CORE_URL}/actuator/health/liveness"
  local readiness_url="${CORE_URL}/api/core/status"
  local body_file="${CI_OUTPUT_DIR}/reports/core-actuator-health.body"
  local liveness_body_file="${CI_OUTPUT_DIR}/reports/core-actuator-liveness.body"
  local readiness_body_file="${CI_OUTPUT_DIR}/reports/core-status-readiness.body"
  local status liveness_status readiness_status
  local accept_core_status_when_health_converging="${SMOKE_ACCEPT_CORE_STATUS_WHEN_HEALTH_CONVERGING:-true}"
  printf 'Waiting for Core actuator health'
  while true; do
    # Liveness is a better signal that the Spring process is serving HTTP; the
    # aggregate health can legitimately stay 503 while dependency indicators or
    # custom health contributors are converging. For local developer smoke, do
    # not block for minutes when the Core API is already serving successfully.
    # Set SMOKE_ACCEPT_CORE_STATUS_WHEN_HEALTH_CONVERGING=false for strict CI.
    liveness_status="$(http_status "$liveness_url" "$liveness_body_file")"
    status="$(http_status "$health_url" "$body_file")"
    if [[ "$status" =~ ^2[0-9][0-9]$ ]] && grep -q '"status":"UP"' "$body_file" 2>/dev/null; then
      echo " OK"
      return 0
    fi
    if [[ "$accept_core_status_when_health_converging" == "true" ]]; then
      readiness_status="$(http_status "$readiness_url" "$readiness_body_file")"
      if [[ "$liveness_status" =~ ^2[0-9][0-9]$ ]] && [[ "$readiness_status" =~ ^2[0-9][0-9]$ ]]; then
        echo " OK (Core API ready; aggregate actuator health still converging)"
        return 0
      fi
    fi
    if (( SECONDS >= deadline )); then
      echo
      echo "Timed out waiting for Core actuator health to become UP." >&2
      echo "Last /actuator/health/liveness HTTP status: ${liveness_status:-unknown}" >&2
      [[ -s "$liveness_body_file" ]] && { echo "Last liveness body:" >&2; sed -n '1,200p' "$liveness_body_file" >&2 || true; }
      echo "Last /actuator/health HTTP status: ${status:-unknown}" >&2
      [[ -s "$body_file" ]] && { echo "Last health body:" >&2; sed -n '1,260p' "$body_file" >&2 || true; }
      echo "Last /api/core/status HTTP status: ${readiness_status:-unknown}" >&2
      [[ -s "$readiness_body_file" ]] && { echo "Last core status body:" >&2; sed -n '1,220p' "$readiness_body_file" >&2 || true; }
      echo "Recent Core/Redis/PostgreSQL/Flyway logs:" >&2
      docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=260 core redis postgres core-db-migrate >&2 || true
      return 1
    fi
    printf '.'
    sleep 2
  done
}

tcp_probe_python() {
  local host="$1"
  local port="$2"
  local timeout_seconds="${SMOKE_TCP_CONNECT_TIMEOUT_SECONDS:-2}"
  python3 - "$host" "$port" "$timeout_seconds" <<'PYTCP'
import socket
import sys

host = sys.argv[1]
port = int(sys.argv[2])
timeout_seconds = float(sys.argv[3])

with socket.create_connection((host, port), timeout=timeout_seconds):
    pass
PYTCP
}

tcp_probe_nc() {
  local host="$1"
  local port="$2"
  local timeout_seconds="${SMOKE_TCP_CONNECT_TIMEOUT_SECONDS:-2}"
  if nc -z -w "$timeout_seconds" "$host" "$port" >/dev/null 2>&1; then
    return 0
  fi
  # macOS/BSD nc uses -G for connect timeout. Keep this as a fallback.
  nc -z -G "$timeout_seconds" "$host" "$port" >/dev/null 2>&1
}

tcp_probe_bash_dev_tcp() {
  local host="$1"
  local port="$2"
  local timeout_seconds="${SMOKE_TCP_CONNECT_TIMEOUT_SECONDS:-2}"
  if has_cmd timeout; then
    timeout "$timeout_seconds" bash -c 'exec 3<>"/dev/tcp/$1/$2"' _ "$host" "$port" >/dev/null 2>&1
  else
    bash -c 'exec 3<>"/dev/tcp/$1/$2"' _ "$host" "$port" >/dev/null 2>&1
  fi
}

tcp_probe() {
  local host="$1"
  local port="$2"
  local mode="${SMOKE_TCP_CHECK_MODE:-auto}"
  case "$mode" in
    skip)
      return 0
      ;;
    python)
      has_cmd python3 || { echo "python3 is required for SMOKE_TCP_CHECK_MODE=python" >&2; return 2; }
      tcp_probe_python "$host" "$port"
      ;;
    nc)
      has_cmd nc || { echo "nc is required for SMOKE_TCP_CHECK_MODE=nc" >&2; return 2; }
      tcp_probe_nc "$host" "$port"
      ;;
    bash|dev-tcp)
      tcp_probe_bash_dev_tcp "$host" "$port"
      ;;
    auto)
      if has_cmd python3; then
        tcp_probe_python "$host" "$port"
      elif has_cmd nc; then
        tcp_probe_nc "$host" "$port"
      elif [[ "${SMOKE_ENABLE_DEV_TCP_FALLBACK:-false}" == "true" ]]; then
        tcp_probe_bash_dev_tcp "$host" "$port"
      else
        echo "No safe TCP probe is available. Install python3/nc or set SMOKE_TCP_CHECK_MODE=skip for this smoke check." >&2
        return 2
      fi
      ;;
    *)
      echo "Unsupported SMOKE_TCP_CHECK_MODE=${mode}; expected auto, python, nc, bash, or skip." >&2
      return 2
      ;;
  esac
}

wait_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local mode="${SMOKE_TCP_CHECK_MODE:-auto}"
  printf 'Waiting for %s TCP %s:%s' "$name" "$host" "$port"
  if [[ "$mode" == "skip" ]]; then
    echo " SKIPPED (SMOKE_TCP_CHECK_MODE=skip)"
    return 0
  fi
  until tcp_probe "$host" "$port"; do
    if (( SECONDS >= deadline )); then
      echo
      echo "Timed out waiting for ${name} TCP ${host}:${port}" >&2
      echo "TCP probe mode: ${mode}; set SMOKE_TCP_CHECK_MODE=python or nc to avoid shell /dev/tcp, or skip to bypass this smoke-only check." >&2
      return 1
    fi
    printf '.'
    sleep 2
  done
  echo " OK"
}

body_contains() {
  local url="$1"
  local pattern="$2"
  local body_file="${CI_OUTPUT_DIR}/reports/body-contains.body"
  if http_success "$url" "$body_file"; then
    grep -q "$pattern" "$body_file"
  else
    return 1
  fi
}

check_postgres_table() {
  local table_name="$1"
  local expected="$2"
  printf 'Checking PostgreSQL table %s' "$table_name"
  local value
  value="$(docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
    exec -T postgres psql -U "${POSTGRES_USER:-ai_event_ci}" -d "${POSTGRES_DB:-ai_event_gateway_ci}" \
    -tAc "select coalesce(to_regclass('public.${table_name}')::text, '')" 2>/dev/null | tr -d '[:space:]')"
  if [[ "$value" != "$expected" ]]; then
    echo
    echo "PostgreSQL table ${table_name} is missing after CI migration. Got: ${value:-<empty>}" >&2
    docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=200 core-db-migrate postgres || true
    return 1
  fi
  echo " OK"
}

check_redis_ping() {
  printf 'Checking Redis ping'
  local value
  value="$(docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T redis redis-cli ping 2>/dev/null | tr -d '[:space:]')"
  if [[ "$value" != "PONG" ]]; then
    echo
    echo "Redis ping failed. Got: ${value:-<empty>}" >&2
    docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=160 redis core || true
    return 1
  fi
  echo " OK"
}

wait_core_health_up
check_postgres_table "module_outbox_events" "module_outbox_events"
check_postgres_table "flyway_schema_history" "flyway_schema_history"
check_redis_ping
wait_http_any "Core status" "${CORE_URL}/api/core/status"
wait_http_any "Netty admin health" "${NETTY_URL}/api/admin/health" "${NETTY_URL}/actuator/health"
if [[ "$SKIP_ADMIN_UI_SMOKE" != "true" ]]; then
  wait_http_any "Admin UI health" "${ADMIN_UI_URL}/api/health" "${ADMIN_UI_URL}/"
  if [[ "${CORE_ADMIN_AUTH_ENABLED:-false}" == "true" ]]; then
    if [[ -z "${SMOKE_ADMIN_AUTH_USERNAME:-}" || -z "${SMOKE_ADMIN_AUTH_PASSWORD:-}" ]]; then
      echo "CORE_ADMIN_AUTH_ENABLED=true requires SMOKE_ADMIN_AUTH_USERNAME and SMOKE_ADMIN_AUTH_PASSWORD for login smoke." >&2
      exit 1
    fi
    echo "Running Core Admin credential smoke through Admin UI proxy"
    python3 "${ROOT_DIR}/scripts/security/core-admin-login-smoke.py"       --base-url "${ADMIN_UI_URL}"       --username "${SMOKE_ADMIN_AUTH_USERNAME}"       --password "${SMOKE_ADMIN_AUTH_PASSWORD}"
  fi
else
  echo "Skipping Admin UI smoke check by SKIP_ADMIN_UI_SMOKE=true"
fi
wait_tcp "Netty gateway" "$NETTY_TCP_HOST" "$NETTY_TCP_PORT"

if ! body_contains "${CORE_URL}/api/core/status" "agentRemediationWorkflowMetricsEnabled"; then
  echo "Core status does not expose P12 remediation metrics metadata." >&2
  exit 1
fi

if ! body_contains "${CORE_URL}/internal/control-plane/tasks/callbacks/metadata" "idempotencyEnabled"; then
  echo "Callback metadata does not expose idempotencyEnabled." >&2
  exit 1
fi

if has_cmd node; then
  echo "Running P21 API envelope runtime acceptance"
  CORE_URL="${CORE_URL}" NETTY_URL="${NETTY_URL}" ADMIN_UI_URL="${ADMIN_UI_URL}" \
    SKIP_ADMIN_UI_SMOKE="${SKIP_ADMIN_UI_SMOKE}" \
    node "${ROOT_DIR}/scripts/acceptance/api-envelope-runtime-acceptance.mjs"
  echo "Running high-risk Core/Netty/Admin API runtime smoke acceptance"
  CORE_URL="${CORE_URL}" NETTY_URL="${NETTY_URL}" ADMIN_UI_URL="${ADMIN_UI_URL}" \
    SKIP_ADMIN_UI_SMOKE="${SKIP_ADMIN_UI_SMOKE}" \
    node "${ROOT_DIR}/scripts/acceptance/api-runtime-smoke.mjs"
  if [[ "${SKIP_ADMIN_UI_SMOKE}" != "true" ]]; then
    echo "Running Admin UI proxy E2E smoke"
    ADMIN_UI_ORIGIN="${ADMIN_UI_URL}" CORE_BACKEND_ORIGIN="${CORE_URL}" NETTY_BACKEND_ORIGIN="${NETTY_URL}" \
      node "${ROOT_DIR}/ai-event-gateway-admin-ui/scripts/e2e-smoke.mjs"
    echo "Running Admin UI route-level smoke"
    ADMIN_UI_ORIGIN="${ADMIN_UI_URL}" \
      node "${ROOT_DIR}/ai-event-gateway-admin-ui/scripts/route-smoke.mjs"
  fi
else
  echo "Skipping P21 API envelope runtime acceptance because node is unavailable." >&2
fi

if [[ "${RUN_RUNTIME_LIFECYCLE_E2E}" == "true" ]]; then
  if has_cmd python3; then
    echo "Running P26 Core/Netty/Admin UI/Agent runtime lifecycle E2E"
    P26_RUNTIME_E2E_SKIP_PREFLIGHT_SMOKE=true \
      CORE_URL="${CORE_URL}" NETTY_URL="${NETTY_URL}" ADMIN_UI_URL="${ADMIN_UI_URL}" \
      GATEWAY_TCP_HOST="${NETTY_TCP_HOST}" GATEWAY_TCP_PORT="${NETTY_TCP_PORT}" \
      SKIP_ADMIN_UI_SMOKE="${SKIP_ADMIN_UI_SMOKE}" \
      "${ROOT_DIR}/scripts/acceptance/runtime-lifecycle-e2e.sh" --env-file "${ENV_FILE}" --scenarios "${P26_RUNTIME_E2E_SCENARIOS:-happy,duplicate,stale}"
  else
    echo "P26 runtime lifecycle E2E requested but python3 is unavailable." >&2
    exit 1
  fi
else
  echo "Skipping P26 runtime lifecycle E2E by RUN_RUNTIME_LIFECYCLE_E2E=${RUN_RUNTIME_LIFECYCLE_E2E}"
fi

echo "OpenDispatch local smoke passed."
