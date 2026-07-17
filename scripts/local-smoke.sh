#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/deploy/env/.env.local" ]]; then
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local"
  else
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local.example"
  fi
fi

# shellcheck source=scripts/ci/env-utils.sh
source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "${ENV_FILE}"

PROJECT="${PROJECT_NAME:-opendispatch}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/deploy/docker-compose.local.yml}"
CORE_URL="${CORE_URL:-http://127.0.0.1:${CORE_HTTP_PORT:-18080}}"
NETTY_URL="${NETTY_URL:-http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT:-18081}}"
ADMIN_UI_URL="${ADMIN_UI_URL:-http://127.0.0.1:${ADMIN_UI_HTTP_PORT:-3000}}"
NETTY_TCP_HOST="${NETTY_TCP_HOST:-127.0.0.1}"
NETTY_TCP_PORT="${NETTY_TCP_PORT:-${NETTY_TCP_PORT_PUBLIC:-19090}}"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
REPORT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}/reports"
mkdir -p "${REPORT_DIR}"

has_cmd() { command -v "$1" >/dev/null 2>&1; }

http_status() {
  local url="$1"
  local body_file="$2"
  local error_file="${body_file}.err"
  local token_header_name="${CORE_INTERNAL_TOKEN_HEADER:-X-Cluster-Token}"
  local token_value="${CLUSTER_INTERNAL_TOKEN:-}"
  local status="000"
  : >"${body_file}"
  : >"${error_file}"

  if has_cmd curl; then
    local args=(-sS --max-time 5 -o "${body_file}" -w '%{http_code}')
    if [[ -n "${token_value}" ]]; then
      args+=(-H "${token_header_name}: ${token_value}")
    fi
    status="$(curl "${args[@]}" "${url}" 2>"${error_file}" || true)"
  elif has_cmd wget; then
    local args=(-qO "${body_file}")
    if [[ -n "${token_value}" ]]; then
      args+=(--header="${token_header_name}: ${token_value}")
    fi
    if wget "${args[@]}" "${url}" 2>"${error_file}"; then
      status="200"
    fi
  else
    echo "Neither curl nor wget is available." >&2
    return 2
  fi
  printf '%s' "${status:-000}"
}

print_http_diagnostics() {
  local label="$1"
  local status="$2"
  local body_file="$3"
  echo "${label} HTTP status: ${status:-unknown}" >&2
  if [[ -s "${body_file}" ]]; then
    echo "${label} response body:" >&2
    sed -n '1,240p' "${body_file}" >&2 || true
  fi
  if [[ -s "${body_file}.err" ]]; then
    echo "${label} HTTP client stderr:" >&2
    sed -n '1,80p' "${body_file}.err" >&2 || true
  fi
}

wait_core_health() {
  local health_url="${CORE_URL}/actuator/health"
  local liveness_url="${CORE_URL}/actuator/health/liveness"
  local status_url="${CORE_URL}/api/core/status"
  local health_body="${REPORT_DIR}/core-actuator-health.body"
  local liveness_body="${REPORT_DIR}/core-actuator-liveness.body"
  local status_body="${REPORT_DIR}/core-status.body"
  local health_status="000" liveness_status="000" core_status="000"
  local accept_converging="${SMOKE_ACCEPT_CORE_STATUS_WHEN_HEALTH_CONVERGING:-true}"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))

  printf 'Waiting for Core health at %s' "${health_url}"
  while true; do
    health_status="$(http_status "${health_url}" "${health_body}")"
    if [[ "${health_status}" =~ ^2[0-9][0-9]$ ]] && grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' "${health_body}" 2>/dev/null; then
      echo " OK"
      return 0
    fi

    liveness_status="$(http_status "${liveness_url}" "${liveness_body}")"
    core_status="$(http_status "${status_url}" "${status_body}")"
    if [[ "${accept_converging}" == "true" ]] \
      && [[ "${liveness_status}" =~ ^2[0-9][0-9]$ ]] \
      && [[ "${core_status}" =~ ^2[0-9][0-9]$ ]]; then
      echo " OK (Core API is ready; aggregate actuator health is ${health_status})"
      return 0
    fi

    if (( SECONDS >= deadline )); then
      echo
      echo "Timed out waiting for Core to become usable." >&2
      print_http_diagnostics "/actuator/health" "${health_status}" "${health_body}"
      print_http_diagnostics "/actuator/health/liveness" "${liveness_status}" "${liveness_body}"
      print_http_diagnostics "/api/core/status" "${core_status}" "${status_body}"
      echo "Recent Core/Redis/PostgreSQL/Flyway logs:" >&2
      docker compose -p "${PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" \
        logs --tail=300 core redis postgres core-db-migrate >&2 || true
      docker compose -p "${PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps >&2 || true
      return 1
    fi
    printf '.'
    sleep 2
  done
}

wait_http_2xx() {
  local name="$1"
  shift
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local body_file="${REPORT_DIR}/$(echo "${name}" | tr '[:upper:] ' '[:lower:]-' | tr -cd '[:alnum:]-').body"
  local candidate status last_candidate=""
  printf 'Waiting for %s' "${name}"
  while true; do
    for candidate in "$@"; do
      last_candidate="${candidate}"
      status="$(http_status "${candidate}" "${body_file}")"
      if [[ "${status}" =~ ^2[0-9][0-9]$ ]]; then
        echo " OK (${candidate})"
        return 0
      fi
    done
    if (( SECONDS >= deadline )); then
      echo
      echo "Timed out waiting for ${name}. Last URL: ${last_candidate}" >&2
      print_http_diagnostics "${name}" "${status:-unknown}" "${body_file}"
      docker compose -p "${PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=220 >&2 || true
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
  nc -z -G "$timeout_seconds" "$host" "$port" >/dev/null 2>&1
}

tcp_probe() {
  local host="$1"
  local port="$2"
  local mode="${SMOKE_TCP_CHECK_MODE:-auto}"
  case "$mode" in
    skip) return 0 ;;
    python) tcp_probe_python "$host" "$port" ;;
    nc) tcp_probe_nc "$host" "$port" ;;
    auto)
      if has_cmd python3; then
        tcp_probe_python "$host" "$port"
      elif has_cmd nc; then
        tcp_probe_nc "$host" "$port"
      else
        echo "No safe TCP probe is available. Install python3/nc or set SMOKE_TCP_CHECK_MODE=skip." >&2
        return 2
      fi
      ;;
    *) echo "Unsupported SMOKE_TCP_CHECK_MODE=${mode}" >&2; return 2 ;;
  esac
}

wait_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  printf 'Waiting for %s TCP %s:%s' "$name" "$host" "$port"
  if [[ "${SMOKE_TCP_CHECK_MODE:-auto}" == "skip" ]]; then
    echo " SKIPPED"
    return 0
  fi
  until tcp_probe "$host" "$port"; do
    if (( SECONDS >= deadline )); then
      echo
      echo "Timed out waiting for ${name} TCP ${host}:${port}" >&2
      docker compose -p "${PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=220 netty >&2 || true
      return 1
    fi
    printf '.'
    sleep 2
  done
  echo " OK"
}

wait_core_health
wait_http_2xx "Netty admin health" "${NETTY_URL}/api/admin/health" "${NETTY_URL}/actuator/health"
wait_http_2xx "Admin UI health" "${ADMIN_UI_URL}/api/health" "${ADMIN_UI_URL}/"
wait_tcp "Netty gateway" "${NETTY_TCP_HOST}" "${NETTY_TCP_PORT}"

echo "OpenDispatch local smoke passed."
