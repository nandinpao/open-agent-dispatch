#!/usr/bin/env bash
set -euo pipefail

ACTION="${1:-start}"
shift || true
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/deploy/env/.env.local.ci}"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --output-dir) CI_OUTPUT_DIR="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

# shellcheck source=scripts/ci/env-utils.sh
source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"

RUNTIME_DIR="${CI_OUTPUT_DIR}/runtime"
LOG_DIR="${CI_OUTPUT_DIR}/logs"
PID_FILE="${RUNTIME_DIR}/admin-ui.pid"
LOG_FILE="${LOG_DIR}/admin-ui-host.log"
START_TIMEOUT_SECONDS="${ADMIN_UI_START_TIMEOUT_SECONDS:-120}"

mkdir -p "$RUNTIME_DIR" "$LOG_DIR"

is_running() {
  [[ -f "$PID_FILE" ]] || return 1
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  [[ -n "$pid" ]] || return 1
  kill -0 "$pid" >/dev/null 2>&1
}

has_cmd() { command -v "$1" >/dev/null 2>&1; }

http_get() {
  local url="$1"
  if has_cmd curl; then
    curl -fsS --max-time 5 "$url" >/dev/null
  elif has_cmd wget; then
    wget -qO- "$url" >/dev/null
  else
    echo "Neither curl nor wget is available to check Admin UI readiness." >&2
    return 2
  fi
}

wait_admin_ready() {
  local url="$1"
  local deadline=$((SECONDS + START_TIMEOUT_SECONDS))
  printf 'Waiting for host Admin UI %s' "$url"
  until http_get "$url"; do
    if ! is_running; then
      echo
      echo "Admin UI process exited before it became ready. Recent log:" >&2
      tail -120 "$LOG_FILE" >&2 || true
      return 1
    fi
    if (( SECONDS >= deadline )); then
      echo
      echo "Timed out waiting for Admin UI at ${url}. Recent log:" >&2
      tail -120 "$LOG_FILE" >&2 || true
      return 1
    fi
    printf '.'
    sleep 2
  done
  echo " OK"
}

stop_admin() {
  if is_running; then
    local pid
    pid="$(cat "$PID_FILE")"
    echo "Stopping host Admin UI process ${pid}"
    kill "$pid" >/dev/null 2>&1 || true
    for _ in {1..20}; do
      if ! kill -0 "$pid" >/dev/null 2>&1; then
        break
      fi
      sleep 0.2
    done
    kill -9 "$pid" >/dev/null 2>&1 || true
  fi
  rm -f "$PID_FILE"
}

start_admin() {
  stop_admin

  local port host core_origin netty_origin
  port="${ADMIN_UI_HTTP_PORT:-3000}"
  host="${ADMIN_UI_HOSTNAME:-127.0.0.1}"
  core_origin="${CORE_BACKEND_ORIGIN:-http://127.0.0.1:${CORE_HTTP_PORT:-18080}}"
  netty_origin="${NETTY_BACKEND_ORIGIN:-http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT:-18081}}"

  echo "Starting host Admin UI on ${host}:${port}; log=${LOG_FILE}"
  if [[ ! -f "${ROOT_DIR}/ai-event-gateway-admin-ui/.next/BUILD_ID" ]]; then
    echo "Admin UI production build is missing: ai-event-gateway-admin-ui/.next/BUILD_ID" >&2
    echo "Run npm run build in ai-event-gateway-admin-ui before starting the host runtime." >&2
    exit 1
  fi
  (
    cd "${ROOT_DIR}/ai-event-gateway-admin-ui"
    export NODE_ENV=production
    export PORT="$port"
    export HOSTNAME="$host"
    export ADMIN_UI_SECURITY_PROFILE="${ADMIN_UI_SECURITY_PROFILE:-local}"
    export ADMIN_UI_FAIL_CLOSED="${ADMIN_UI_FAIL_CLOSED:-false}"
    export CORE_BACKEND_ORIGIN="$core_origin"
    export NETTY_BACKEND_ORIGIN="$netty_origin"
    export GATEWAY_BACKEND_ORIGIN="${GATEWAY_BACKEND_ORIGIN:-$netty_origin}"
    export NEXT_PUBLIC_APP_NAME="${NEXT_PUBLIC_APP_NAME:-AI Event Gateway Admin}"
    export NEXT_PUBLIC_ADMIN_BACKEND_MODE="${NEXT_PUBLIC_ADMIN_BACKEND_MODE:-dual}"
    export NEXT_PUBLIC_USE_MOCK="${NEXT_PUBLIC_USE_MOCK:-false}"
    export NEXT_PUBLIC_AUTH_ENABLED="${NEXT_PUBLIC_AUTH_ENABLED:-true}"
    export ADMIN_UI_COOKIE_SECURE="${ADMIN_UI_COOKIE_SECURE:-false}"
    export NEXT_PUBLIC_CORE_API_BASE_URL="${NEXT_PUBLIC_CORE_API_BASE_URL:-/core-api}"
    export NEXT_PUBLIC_NETTY_API_BASE_URL="${NEXT_PUBLIC_NETTY_API_BASE_URL:-/netty-api}"
    export NEXT_PUBLIC_GATEWAY_API_BASE_URL="${NEXT_PUBLIC_GATEWAY_API_BASE_URL:-/netty-api}"
    export NEXT_PUBLIC_NETTY_RUNTIME_WS_URL="${NEXT_PUBLIC_NETTY_RUNTIME_WS_URL:-/api/admin/runtime/stream}"
    export NEXT_PUBLIC_GATEWAY_WS_URL="${NEXT_PUBLIC_GATEWAY_WS_URL:-/api/admin/runtime/stream}"
    export NEXT_PUBLIC_REFRESH_INTERVAL_MS="${NEXT_PUBLIC_REFRESH_INTERVAL_MS:-5000}"
    export NEXT_PUBLIC_REQUEST_TIMEOUT_MS="${NEXT_PUBLIC_REQUEST_TIMEOUT_MS:-10000}"
    export NEXT_PUBLIC_API_CONTRACT_MODE="${NEXT_PUBLIC_API_CONTRACT_MODE:-warn}"
    exec npm run start -- -p "$port" -H "$host"
  ) >"$LOG_FILE" 2>&1 &

  echo $! > "$PID_FILE"
  sleep 1
  if ! is_running; then
    echo "Admin UI failed to start. Recent log:" >&2
    tail -120 "$LOG_FILE" >&2 || true
    exit 1
  fi
  wait_admin_ready "http://${host}:${port}/api/health"
}

case "$ACTION" in
  start) start_admin ;;
  stop) stop_admin ;;
  status)
    if is_running; then
      echo "Admin UI host process is running: $(cat "$PID_FILE")"
    else
      echo "Admin UI host process is not running"
      exit 1
    fi
    ;;
  *) echo "Unknown action: $ACTION" >&2; exit 2 ;;
esac
