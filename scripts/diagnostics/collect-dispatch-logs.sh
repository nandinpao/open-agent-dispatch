#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_ROOT="${OPENDISPATCH_LOG_ROOT:-${ROOT_DIR}/.local/opendispatch-logs}"
OUT_DIR="${1:-${ROOT_DIR}/.ci-output/diagnostics}"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE="${OUT_DIR}/opendispatch-dispatch-logs-${STAMP}.tar.gz"

mkdir -p "${OUT_DIR}"
mkdir -p "${LOG_ROOT}/core" "${LOG_ROOT}/netty" "${LOG_ROOT}/admin-ui" "${LOG_ROOT}/agents"

printf 'Collecting OpenDispatch logs from %s\n' "${LOG_ROOT}"
printf 'Writing archive to %s\n' "${ARCHIVE}"

LEGACY_AGENT_LOG_DIR="${ROOT_DIR}/ai-event-gateway-netty/.runtime/agents/logs"
LEGACY_AGENT_PENDING_DIR="${ROOT_DIR}/ai-event-gateway-netty/.runtime/agents/pending-callbacks"
if [[ -d "${LEGACY_AGENT_LOG_DIR}" ]]; then
  mkdir -p "${LOG_ROOT}/agents"
  find "${LEGACY_AGENT_LOG_DIR}" -maxdepth 1 -type f -name '*.log' -exec cp -f {} "${LOG_ROOT}/agents/" \;
fi
if [[ -d "${LEGACY_AGENT_PENDING_DIR}" ]]; then
  mkdir -p "${LOG_ROOT}/agents/pending-callbacks"
  find "${LEGACY_AGENT_PENDING_DIR}" -maxdepth 1 -type f -name '*.json' -exec cp -f {} "${LOG_ROOT}/agents/pending-callbacks/" \;
fi

# Since Phase 32-I local/CI compose uses named Docker volumes instead of host
# bind-mounted log directories, collect compose console logs directly into the
# diagnostic bundle. This prevents uploads that contain only standalone Agent
# logs while Core/Netty evidence remains inside Docker.
COMPOSE_PROJECT="${PROJECT_NAME:-opendispatch}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/deploy/docker-compose.local.yml}"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/deploy/env/.env.local" ]]; then
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local"
  else
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local.example"
  fi
fi
COMPOSE_LOG_DIR="${LOG_ROOT}/compose"
mkdir -p "${COMPOSE_LOG_DIR}"
if command -v docker >/dev/null 2>&1; then
  if docker compose -p "${COMPOSE_PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps >/dev/null 2>&1; then
    docker compose -p "${COMPOSE_PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --no-color --tail="${OPENDISPATCH_COLLECT_LOG_TAIL:-2000}" \
      > "${COMPOSE_LOG_DIR}/compose.log" 2>&1 || true
    for service in core netty postgres redis core-db-migrate admin-ui adapter-worker otel-collector; do
      docker compose -p "${COMPOSE_PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --no-color --tail="${OPENDISPATCH_COLLECT_LOG_TAIL:-2000}" "$service" \
        > "${COMPOSE_LOG_DIR}/${service}.log" 2>&1 || true
    done
  else
    printf 'docker compose stack not available for project=%s composeFile=%s envFile=%s\n' "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${ENV_FILE}" \
      > "${COMPOSE_LOG_DIR}/compose-unavailable.log"
  fi
fi

MANIFEST="${LOG_ROOT}/diagnostics-manifest.txt"
{
  printf 'generatedAt=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'logRoot=%s\n' "${LOG_ROOT}"
  printf 'composeProject=%s\n' "${COMPOSE_PROJECT}"
  printf 'composeFile=%s exists=%s\n' "${COMPOSE_FILE}" "$( [[ -f "${COMPOSE_FILE}" ]] && echo true || echo false )"
  printf 'composeEnvFile=%s exists=%s\n' "${ENV_FILE}" "$( [[ -f "${ENV_FILE}" ]] && echo true || echo false )"
  printf 'legacyAgentLogDir=%s exists=%s\n' "${LEGACY_AGENT_LOG_DIR}" "$( [[ -d "${LEGACY_AGENT_LOG_DIR}" ]] && echo true || echo false )"
  printf 'legacyAgentPendingDir=%s exists=%s\n' "${LEGACY_AGENT_PENDING_DIR}" "$( [[ -d "${LEGACY_AGENT_PENDING_DIR}" ]] && echo true || echo false )"
  printf '\n[agent log files]\n'
  find "${LOG_ROOT}/agents" -maxdepth 1 -type f -name '*.log' -print0 2>/dev/null | while IFS= read -r -d '' file; do
    printf '%s size=%s mtime=%s lastLine=' "${file}" "$(wc -c < "${file}" 2>/dev/null || echo 0)" "$(stat -f '%Sm' -t '%Y-%m-%dT%H:%M:%S%z' "${file}" 2>/dev/null || stat -c '%y' "${file}" 2>/dev/null || echo unknown)"
    tail -n 1 "${file}" 2>/dev/null || true
  done
  printf '\n[pending callback files]\n'
  find "${LOG_ROOT}/agents/pending-callbacks" -maxdepth 1 -type f -name '*.json' -print0 2>/dev/null | while IFS= read -r -d '' file; do
    printf '%s size=%s content=' "${file}" "$(wc -c < "${file}" 2>/dev/null || echo 0)"
    tr -d '\n' < "${file}" 2>/dev/null || true
    printf '\n'
  done
  printf '\n[compose log files]\n'
  find "${COMPOSE_LOG_DIR}" -maxdepth 1 -type f -name '*.log' -print0 2>/dev/null | while IFS= read -r -d '' file; do
    printf '%s size=%s lastLine=' "${file}" "$(wc -c < "${file}" 2>/dev/null || echo 0)"
    tail -n 1 "${file}" 2>/dev/null || true
  done
} > "${MANIFEST}"

tar -czf "${ARCHIVE}" -C "${LOG_ROOT}" .

printf '%s\n' "${ARCHIVE}"
