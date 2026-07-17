#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/deploy/env/.env.local.ci}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
SCENARIOS="${P26_RUNTIME_E2E_SCENARIOS:-happy,duplicate,stale}"
SKIP_ADMIN_UI_SMOKE="${SKIP_ADMIN_UI_SMOKE:-false}"
SKIP_PREFLIGHT_SMOKE="${P26_RUNTIME_E2E_SKIP_PREFLIGHT_SMOKE:-false}"

usage() {
  cat <<USAGE
Usage: $0 [--env-file <path>] [--scenarios happy,duplicate,stale,owner] [--dry-run]

Run OpenDispatch P26 runtime lifecycle E2E against an already-started stack.
The default scenarios are happy,duplicate,stale. The owner scenario requires a
second Netty node and is opt-in.
USAGE
}

DRY_RUN="false"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --scenarios) SCENARIOS="$2"; shift 2 ;;
    --dry-run) DRY_RUN="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ "${ENV_FILE}" != /* ]]; then
  ENV_FILE="${ROOT_DIR}/${ENV_FILE}"
fi

if [[ -f "${ROOT_DIR}/scripts/ci/env-utils.sh" && -f "${ENV_FILE}" ]]; then
  # shellcheck source=scripts/ci/env-utils.sh
  source "${ROOT_DIR}/scripts/ci/env-utils.sh"
  load_dotenv_file "${ENV_FILE}"
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
  if [[ -z "${GATEWAY_TCP_HOST:-}" ]] || is_loopback_endpoint "${GATEWAY_TCP_HOST}"; then
    GATEWAY_TCP_HOST="${NETTY_TCP_HOST:-${PUBLIC_HOST}}"
    if is_loopback_endpoint "${GATEWAY_TCP_HOST}"; then
      GATEWAY_TCP_HOST="${PUBLIC_HOST}"
    fi
  fi
else
  CORE_URL="${CORE_URL:-http://127.0.0.1:${CORE_HTTP_PORT:-18080}}"
  NETTY_URL="${NETTY_URL:-http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT:-18081}}"
  ADMIN_UI_URL="${ADMIN_UI_URL:-http://127.0.0.1:${ADMIN_UI_HTTP_PORT:-3000}}"
  GATEWAY_TCP_HOST="${GATEWAY_TCP_HOST:-${NETTY_TCP_HOST:-127.0.0.1}}"
fi
GATEWAY_TCP_PORT="${GATEWAY_TCP_PORT:-${NETTY_TCP_PORT:-19090}}"
AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN:-local-ci-agent-onboarding-token-change-me}"
CLUSTER_INTERNAL_TOKEN="${CLUSTER_INTERNAL_TOKEN:-local-ci-cluster-token-change-me}"

export CORE_URL NETTY_URL ADMIN_UI_URL GATEWAY_TCP_HOST GATEWAY_TCP_PORT AGENT_ONBOARDING_TOKEN CLUSTER_INTERNAL_TOKEN
export I6_CORE_BASE_URL="${I6_CORE_BASE_URL:-${CORE_URL}}"
export I6_NETTY_ADMIN_BASE_URL="${I6_NETTY_ADMIN_BASE_URL:-${NETTY_URL}}"
export I6_GATEWAY_TCP_HOST="${I6_GATEWAY_TCP_HOST:-${GATEWAY_TCP_HOST}}"
export I6_GATEWAY_TCP_PORT="${I6_GATEWAY_TCP_PORT:-${GATEWAY_TCP_PORT}}"
export I6_GATEWAY_NODE_ID="${I6_GATEWAY_NODE_ID:-gateway-node-001}"
export I6_CLUSTER_INTERNAL_TOKEN="${I6_CLUSTER_INTERNAL_TOKEN:-${CLUSTER_INTERNAL_TOKEN}}"
export I6_AGENT_ID="${I6_AGENT_ID:-agent-p26-lifecycle-$(date +%s)-$$}"
export I6_AGENT_TENANT_ID="${I6_AGENT_TENANT_ID:-tenant-a}"
export I6_EVENT_TENANT_ID="${I6_EVENT_TENANT_ID:-${I6_AGENT_TENANT_ID}}"
export I6_AGENT_SITE_ID="${I6_AGENT_SITE_ID:-LOCAL}"
export I6_EVENT_SITE_ID="${I6_EVENT_SITE_ID:-${I6_AGENT_SITE_ID}}"
export I6_AGENT_CAPABILITIES="${I6_AGENT_CAPABILITIES:-INCIDENT_ANALYSIS,TASK_EXECUTION,GENERAL_AGENT}"
export I6_REQUIRED_CAPABILITIES="${I6_REQUIRED_CAPABILITIES:-INCIDENT_ANALYSIS}"
export I6_AGENT_HEARTBEAT_INTERVAL_SECONDS="${I6_AGENT_HEARTBEAT_INTERVAL_SECONDS:-1}"
export I7_RUNTIME_SCENARIOS="${SCENARIOS}"

if [[ "${DRY_RUN}" == "true" ]]; then
  export I7_DRY_RUN=true
  export I6_DRY_RUN=true
fi

cd "${ROOT_DIR}"

echo "[p26-runtime] core=${CORE_URL} netty=${NETTY_URL} admin=${ADMIN_UI_URL} tcp=${GATEWAY_TCP_HOST}:${GATEWAY_TCP_PORT} scenarios=${SCENARIOS}"

if [[ "${DRY_RUN}" != "true" && "${SKIP_PREFLIGHT_SMOKE}" != "true" ]] && command -v node >/dev/null 2>&1; then
  echo "[p26-runtime] running API envelope/runtime smoke before lifecycle scenarios"
  CORE_URL="${CORE_URL}" NETTY_URL="${NETTY_URL}" ADMIN_UI_URL="${ADMIN_UI_URL}" SKIP_ADMIN_UI_SMOKE="${SKIP_ADMIN_UI_SMOKE}" \
    node "${ROOT_DIR}/scripts/acceptance/api-envelope-runtime-acceptance.mjs"
  CORE_URL="${CORE_URL}" NETTY_URL="${NETTY_URL}" ADMIN_UI_URL="${ADMIN_UI_URL}" SKIP_ADMIN_UI_SMOKE="${SKIP_ADMIN_UI_SMOKE}" \
    node "${ROOT_DIR}/scripts/acceptance/api-runtime-smoke.mjs"
fi

if [[ "${DRY_RUN}" != "true" && "${SKIP_PREFLIGHT_SMOKE}" != "true" && "${SKIP_ADMIN_UI_SMOKE}" != "true" && -f "${ROOT_DIR}/ai-event-gateway-admin-ui/scripts/route-smoke.mjs" ]] && command -v node >/dev/null 2>&1; then
  echo "[p26-runtime] running Admin UI route smoke before lifecycle scenarios"
  ADMIN_UI_ORIGIN="${ADMIN_UI_URL}" node "${ROOT_DIR}/ai-event-gateway-admin-ui/scripts/route-smoke.mjs"
fi

if [[ "${DRY_RUN}" == "true" ]]; then
  export P26_DRY_RUN=true
fi

if [[ "${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-false}" == "true" ]]; then
  if command -v node >/dev/null 2>&1; then
    echo "[p26-runtime] running Core governance disable -> Netty disconnect lifecycle scenario"
    node "${ROOT_DIR}/scripts/acceptance/agent-governance-lifecycle-e2e.mjs"
  else
    echo "Skipping P26 governance lifecycle scenario because node is unavailable." >&2
  fi
else
  echo "Skipping P26 governance lifecycle scenario because GATEWAY_AGENT_AUTHORIZATION_ENABLED=${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-false}"
fi

echo "[p26-runtime] running Core + Netty + Agent dispatch/callback lifecycle scenarios"
bash "${ROOT_DIR}/ai-event-gateway-core/scripts/e2e/run-i7-runtime-gate.sh"

echo "[p26-runtime] OpenDispatch runtime lifecycle E2E passed."
