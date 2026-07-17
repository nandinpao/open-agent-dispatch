#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

ENV_FILE="${ENV_FILE:-deploy/env/.env.local}"
if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="deploy/env/.env.local.example"
fi

load_env_if_unset() {
  local file="$1"
  while IFS= read -r raw || [[ -n "${raw}" ]]; do
    local line="${raw#export }"
    [[ -z "${line//[[:space:]]/}" || "${line}" == \#* || "${line}" != *=* ]] && continue
    local key="${line%%=*}"
    local value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && continue
    if [[ -z "${!key+x}" ]]; then
      value="${value%$'\r'}"
      if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then value="${value:1:${#value}-2}"; fi
      if [[ "${value}" == \'*\' && "${value}" == *\' ]]; then value="${value:1:${#value}-2}"; fi
      while [[ "${value}" =~ \$\{([A-Za-z_][A-Za-z0-9_]*)\} ]]; do
        local reference_name="${BASH_REMATCH[1]}"
        local reference_value="${!reference_name-}"
        value="${value//\$\{${reference_name}\}/${reference_value}}"
      done
      export "${key}=${value}"
    fi
  done < "${file}"
}

load_env_if_unset "${ENV_FILE}"

export CORE_BOOTSTRAP_ENV_FILE="${CORE_BOOTSTRAP_ENV_FILE:-${ENV_FILE}}"
export CORE_BOOTSTRAP_ADMIN_AUTH_MODE="${CORE_BOOTSTRAP_ADMIN_AUTH_MODE:-SESSION}"
export CORE_BOOTSTRAP_AGENTS=true
export CORE_BOOTSTRAP_RESET_EXISTING="${CORE_BOOTSTRAP_RESET_EXISTING:-true}"
export CORE_BOOTSTRAP_RESET_BLOCKED="${CORE_BOOTSTRAP_RESET_BLOCKED:-true}"
export CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL="${CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL:-true}"
export CORE_BOOTSTRAP_VERIFY_AUTHORIZATION="${CORE_BOOTSTRAP_VERIFY_AUTHORIZATION:-true}"
export GATEWAY_CLUSTER_NODE_COUNT="${GATEWAY_CLUSTER_NODE_COUNT:-1}"
export AGENTS_PER_NODE="${AGENTS_PER_NODE:-1}"
export AGENT_TENANT_ID="${AGENT_TENANT_ID:-tenant-a}"
export AGENT_WORKER_MODE="${AGENT_WORKER_MODE:-process-result}"
export AGENT_WORKER_PROCESSING_MS="${AGENT_WORKER_PROCESSING_MS:-800}"
export AGENT_MAX_CONCURRENT_TASKS="${AGENT_MAX_CONCURRENT_TASKS:-3}"

# Explicitly unset obsolete bootstrap authority variables.
unset CORE_BOOTSTRAP_ASSIGNMENT_PROFILE CORE_BOOTSTRAP_AUTO_APPROVE_QUALIFICATION \
  CORE_BOOTSTRAP_QUALIFICATION_EVIDENCE CORE_BOOTSTRAP_RUN_CERTIFICATION \
  CORE_BOOTSTRAP_LEGACY_CAPABILITIES_ENABLED AGENT_CLUSTER_CAPABILITY_PRESETS \
  AGENT_CAPABILITY_PRESET AGENT_SCOPE_SYSTEM_CODE AGENT_SCOPE_TASK_TYPE AGENT_SCOPE_SITE_CODE || true

./scripts/cluster-run-many-agents.sh restart

export STAGE1_ENV_FILE="${ENV_FILE}"
export STAGE1_AGENT_ID="${STAGE1_AGENT_ID:-agent-cluster-node-001-001}"
export STAGE1_REPORT_DIR="${STAGE7_FIX2_REPORT_DIR:-.ci-output/stage7-fix2-e2e}"
export CORE_URL="${CORE_URL:-${CORE_BASE_URL:-http://127.0.0.1:18080}}"

./scripts/characterization/run-stage1.sh --strict
