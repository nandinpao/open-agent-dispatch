#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Source-tree layout: scripts/release/release-preflight.sh -> repo root is ../..
if [[ -d "${ROOT_DIR}/scripts" && -d "${ROOT_DIR}/deploy" ]]; then
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi
PACKAGE_DIR="${PACKAGE_DIR:-${ROOT_DIR}}"
ENV_FILE="${ENV_FILE:-${PACKAGE_DIR}/deploy/env/.env.release}"
OFFLINE="false"
STRICT_SECRETS="true"
SKIP_DOCKER="false"
SKIP_PORT_CHECK="false"

usage() {
  cat <<USAGE
Usage: $0 [--package-dir <dir>] [--env-file <file>] [--offline] [--strict-secrets] [--allow-placeholder-secrets] [--skip-docker] [--skip-port-check]

Validate an OpenDispatch on-prem release package before starting it.

Modes:
  --offline         Require bundled Admin UI deps and locally available runtime images.
  --strict-secrets  Treat placeholder release secrets as fatal. This is the default for release packages.
  --allow-placeholder-secrets
                    Downgrade placeholder secret findings to warnings for non-production dry-runs only.
  --skip-docker     Skip docker/docker-compose and local image checks.
  --skip-port-check Skip host port availability checks. Use during guarded upgrades while the current stack is still running.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package-dir)
      PACKAGE_DIR="$2"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --offline)
      OFFLINE="true"
      shift
      ;;
    --strict-secrets)
      STRICT_SECRETS="true"
      shift
      ;;
    --allow-placeholder-secrets)
      STRICT_SECRETS="false"
      shift
      ;;
    --skip-docker)
      SKIP_DOCKER="true"
      shift
      ;;
    --skip-port-check)
      SKIP_PORT_CHECK="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

failures=0
warnings=0
info() { echo "[INFO] $*"; }
warn() { echo "[WARN] $*" >&2; warnings=$((warnings + 1)); }
fail() { echo "[FAIL] $*" >&2; failures=$((failures + 1)); }

require_file() { [[ -f "$1" ]] || fail "Missing file: $1"; }
require_dir() { [[ -d "$1" ]] || fail "Missing directory: $1"; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"; }

load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
  else
    warn "Env file not found: ${ENV_FILE}; falling back to process environment and compose defaults."
  fi
}

check_layout() {
  info "Checking release package layout."
  require_file "${PACKAGE_DIR}/deploy/docker-compose.release.yml"
  require_file "${PACKAGE_DIR}/deploy/env/.env.release.example"
  require_file "${PACKAGE_DIR}/runtime/core/ai-event-gateway-core.jar"
  require_file "${PACKAGE_DIR}/runtime/adapter-worker/ai-event-gateway-adapter-worker.jar"
  require_file "${PACKAGE_DIR}/runtime/netty/ai-event-gateway-netty.jar"
  require_file "${PACKAGE_DIR}/runtime/admin-ui/.next/BUILD_ID"
  require_file "${PACKAGE_DIR}/runtime/admin-ui/scripts/opendispatch-admin-ui-runtime-entrypoint.sh"
  require_dir "${PACKAGE_DIR}/db/migration"
  if [[ -x "${PACKAGE_DIR}/bin/verify-release-package.sh" ]]; then
    if [[ "${OFFLINE}" == "true" ]]; then
      "${PACKAGE_DIR}/bin/verify-release-package.sh" --package-dir "${PACKAGE_DIR}" --offline >/dev/null || fail "verify-release-package.sh --offline failed"
    else
      "${PACKAGE_DIR}/bin/verify-release-package.sh" --package-dir "${PACKAGE_DIR}" >/dev/null || fail "verify-release-package.sh failed"
    fi
  else
    warn "Package verifier is not executable: ${PACKAGE_DIR}/bin/verify-release-package.sh"
  fi
}

check_admin_offline() {
  if [[ "${OFFLINE}" == "true" ]]; then
    info "Checking offline Admin UI runtime dependencies."
    require_file "${PACKAGE_DIR}/runtime/admin-ui/node_modules-prod.tar.gz"
    if command -v tar >/dev/null 2>&1 && [[ -f "${PACKAGE_DIR}/runtime/admin-ui/node_modules-prod.tar.gz" ]]; then
      tar -tzf "${PACKAGE_DIR}/runtime/admin-ui/node_modules-prod.tar.gz" | grep -Eq '^node_modules/(\.bin/next|next/package\.json)' \
        || fail "Admin UI dependency bundle does not contain Next.js runtime dependencies."
    fi
  fi
}

is_truthy_value() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

is_falsey_value() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    0|false|no|off) return 0 ;;
    *) return 1 ;;
  esac
}

is_placeholder_secret() {
  local value lowered
  value="${1:-}"
  lowered="$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')"
  [[ -z "${value}" ]] && return 0
  [[ "${lowered}" == "change-me" ]] && return 0
  [[ "${lowered}" == "changeme" ]] && return 0
  [[ "${lowered}" == "password" ]] && return 0
  [[ "${lowered}" == "secret" ]] && return 0
  [[ "${lowered}" == "admin" ]] && return 0
  [[ "${lowered}" == "dev-token" ]] && return 0
  [[ "${lowered}" == "test-token" ]] && return 0
  [[ "${lowered}" == *change-me* ]] && return 0
  [[ "${lowered}" == *change_me* ]] && return 0
  [[ "${lowered}" == *replace-with* ]] && return 0
  [[ "${lowered}" == *replace_with* ]] && return 0
  [[ "${lowered}" == \<* ]] && return 0
  [[ "${lowered}" == *\> ]] && return 0
  return 1
}

secret_problem() {
  local message="$1"
  if [[ "${STRICT_SECRETS}" == "true" ]]; then
    fail "${message}"
  else
    warn "${message} Change it before production use."
  fi
}

require_secret() {
  local name="$1"
  local value="${!name:-}"
  if is_placeholder_secret "${value}"; then
    secret_problem "${name} is blank or still uses a placeholder value."
  fi
}

require_true_env() {
  local name="$1"
  local value="${!name:-}"
  if ! is_truthy_value "${value}"; then
    fail "${name} must be true in release production mode."
  fi
}

require_false_env() {
  local name="$1"
  local value="${!name:-}"
  if ! is_falsey_value "${value}"; then
    fail "${name} must be false in release production mode."
  fi
}

require_not_value() {
  local name="$1"
  local forbidden="$2"
  local value="$(printf '%s' "${!name:-}" | tr '[:upper:]' '[:lower:]')"
  if [[ "${value}" == "${forbidden}" ]]; then
    fail "${name}=${forbidden} is not allowed in release production mode."
  fi
}

require_profile_prod() {
  local value="$(printf '%s' "${ADMIN_UI_SECURITY_PROFILE:-}" | tr '[:upper:]' '[:lower:]')"
  if [[ "${value}" != "prod" && "${value}" != "production" ]]; then
    fail "ADMIN_UI_SECURITY_PROFILE must be prod or production in release packages."
  fi
}

require_distinct_secrets() {
  local label="$1"
  shift
  local -A seen=()
  local name value prior
  for name in "$@"; do
    value="${!name:-}"
    [[ -z "${value}" ]] && continue
    prior="${seen[${value}]:-}"
    if [[ -n "${prior}" ]]; then
      secret_problem "${label} secrets must be role-separated; ${prior} and ${name} resolve to the same value."
    fi
    seen[${value}]="${name}"
  done
}

check_secrets() {
  info "Checking fail-closed production security settings and secrets."

  local required_secrets=(
    POSTGRES_PASSWORD REDIS_PASSWORD
    CLUSTER_INTERNAL_TOKEN
    CORE_GATEWAY_INTERNAL_TOKEN CORE_ADAPTER_WORKER_INTERNAL_TOKEN CORE_OPERATOR_INTERNAL_TOKEN
    CORE_RECOVERY_OPERATOR_INTERNAL_TOKEN CORE_RECOVERY_ADMIN_INTERNAL_TOKEN CORE_RECOVERY_APPROVER_INTERNAL_TOKEN CORE_ACTUATOR_INTERNAL_TOKEN
    GATEWAY_CORE_FORWARD_AUTH_TOKEN GATEWAY_CORE_DIRECTORY_SYNC_AUTH_TOKEN GATEWAY_CORE_TASK_CALLBACK_RELAY_AUTH_TOKEN GATEWAY_AGENT_AUTHORIZATION_AUTH_TOKEN
    AGENT_ONBOARDING_TOKEN NETTY_MACHINE_ADMIN_TOKEN
  )
  local name
  for name in "${required_secrets[@]}"; do
    require_secret "${name}"
  done

  require_true_env CORE_INTERNAL_SECURITY_ENABLED
  require_true_env CORE_INTERNAL_PROTECT_API_MUTATIONS
  require_true_env CORE_INTERNAL_SECURITY_AUDIT_LOG_ENABLED
  require_false_env CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO
  require_false_env CORE_INTERNAL_ALLOW_LEGACY_TOKEN_HEADER

  require_true_env GATEWAY_AGENT_AUTHORIZATION_ENABLED
  require_true_env GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED
  require_false_env GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED
  require_true_env AGENT_AUTH_ENABLED
  require_true_env AGENT_WS_HANDSHAKE_AUTH_ENABLED
  require_true_env NETTY_MACHINE_AUTH_ENABLED
  require_true_env NETTY_MACHINE_WS_HANDSHAKE_AUTH_ENABLED
  require_true_env CORE_ADMIN_AUTH_ENABLED

  require_profile_prod
  require_true_env ADMIN_UI_FAIL_CLOSED
  require_true_env NEXT_PUBLIC_AUTH_ENABLED
  require_false_env NEXT_PUBLIC_USE_MOCK
  require_false_env CORE_FORWARD_BROWSER_AUTHORIZATION
  require_true_env ADMIN_UI_COOKIE_SECURE

  require_true_env CORE_RECOVERY_GOVERNANCE_ENABLED
  require_true_env CORE_RECOVERY_GOVERNANCE_REQUIRE_REASON
  require_true_env CORE_RECOVERY_GOVERNANCE_REQUIRE_CONFIRMATION
  require_false_env CORE_RECOVERY_GOVERNANCE_ALLOW_BODY_OPERATOR_ID_OVERRIDE
  require_true_env CORE_RECOVERY_GOVERNANCE_REQUIRE_DUAL_CONTROL_FOR_HIGH_RISK
  require_true_env CORE_RECOVERY_GOVERNANCE_FORBID_SELF_APPROVAL
  if [[ "${CORE_RECOVERY_GOVERNANCE_MIN_REASON_LENGTH:-0}" -lt 12 ]]; then
    fail "CORE_RECOVERY_GOVERNANCE_MIN_REASON_LENGTH must be at least 12 in release production mode."
  fi

  require_distinct_secrets "Core internal role" \
    CORE_GATEWAY_INTERNAL_TOKEN CORE_ADAPTER_WORKER_INTERNAL_TOKEN CORE_OPERATOR_INTERNAL_TOKEN \
    CORE_RECOVERY_OPERATOR_INTERNAL_TOKEN CORE_RECOVERY_ADMIN_INTERNAL_TOKEN CORE_RECOVERY_APPROVER_INTERNAL_TOKEN CORE_ACTUATOR_INTERNAL_TOKEN
  require_distinct_secrets "Netty machine" NETTY_MACHINE_ADMIN_TOKEN CLUSTER_INTERNAL_TOKEN
}

check_ports() {
  info "Checking host port availability."
  if ! command -v python3 >/dev/null 2>&1; then
    warn "python3 is not available; skipping port availability check."
    return
  fi
  python3 - "${CORE_HTTP_PORT:-18080}" "${NETTY_ADMIN_HTTP_PORT:-18081}" "${NETTY_TCP_PORT:-19090}" "${NETTY_WS_PORT:-19091}" "${ADMIN_UI_HTTP_PORT:-3000}" <<'PY'
import socket
import sys
ports = [int(p) for p in sys.argv[1:] if p]
failed = False
for port in ports:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        s.bind(('127.0.0.1', port))
    except OSError as exc:
        print(f'[FAIL] Host port is not available: {port} ({exc})', file=sys.stderr)
        failed = True
    finally:
        s.close()
if failed:
    sys.exit(1)
PY
  local rc=$?
  if [[ ${rc} -ne 0 ]]; then
    failures=$((failures + 1))
  fi
}

check_docker() {
  if [[ "${SKIP_DOCKER}" == "true" ]]; then
    info "Skipping docker checks."
    return
  fi
  info "Checking Docker and Compose."
  require_cmd docker
  if command -v docker >/dev/null 2>&1; then
    docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is not available."
  fi
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose --env-file "${ENV_FILE}" -f "${PACKAGE_DIR}/deploy/docker-compose.release.yml" config >/dev/null \
      || fail "docker compose config failed."
  fi
  if [[ "${OFFLINE}" == "true" ]] && command -v docker >/dev/null 2>&1; then
    info "Checking locally available runtime images for offline start."
    local images=(
      "${JAVA25_RUNTIME_IMAGE:-eclipse-temurin:25-jre}"
      "${NODE_RUNTIME_IMAGE:-node:22-bookworm-slim}"
      "${POSTGRES_IMAGE:-postgres:18-alpine}"
      "${REDIS_IMAGE:-redis:8-alpine}"
      "${FLYWAY_IMAGE:-flyway/flyway:11-alpine}"
    )
    local image
    for image in "${images[@]}"; do
      docker image inspect "${image}" >/dev/null 2>&1 || fail "Offline mode requires local Docker image: ${image}"
    done
  fi
}

PACKAGE_DIR="$(cd "${PACKAGE_DIR}" && pwd)"
if [[ ! -f "${ENV_FILE}" && -f "${PACKAGE_DIR}/deploy/env/.env.release.example" ]]; then
  ENV_FILE="${PACKAGE_DIR}/deploy/env/.env.release.example"
fi
if [[ -f "${ENV_FILE}" ]]; then
  ENV_FILE="$(cd "$(dirname "${ENV_FILE}")" && pwd)/$(basename "${ENV_FILE}")"
fi

load_env
check_layout
check_admin_offline
check_secrets
if [[ "${SKIP_PORT_CHECK}" == "true" ]]; then
  info "Skipping host port availability check."
else
  check_ports
fi
check_docker

if [[ ${failures} -gt 0 ]]; then
  echo "OpenDispatch release preflight failed: ${failures} failure(s), ${warnings} warning(s)." >&2
  exit 1
fi

echo "OpenDispatch release preflight passed: ${warnings} warning(s)."
