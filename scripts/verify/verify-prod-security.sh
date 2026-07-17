#!/usr/bin/env bash
set -euo pipefail

# P0 production guardrail: fail if a production profile inherits permissive local defaults.
# Usage:
#   ENV_FILE=deploy/env/.env.prod ./scripts/verify/verify-prod-security.sh
#   make verify-prod-security ENV_FILE=deploy/env/.env.prod

ENV_FILE="${ENV_FILE:-}"
if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  set -a
  source "${ENV_FILE}"
  set +a
fi

failures=()

require_not_true() {
  local name="$1"
  local value="${!name:-}"
  if [[ "${value,,}" == "true" ]]; then
    failures+=("${name} must not be true in production")
  fi
}

require_true() {
  local name="$1"
  local value="${!name:-}"
  if [[ "${value,,}" != "true" ]]; then
    failures+=("${name} must be true in production")
  fi
}

require_false() {
  local name="$1"
  local value="${!name:-}"
  if [[ "${value,,}" != "false" ]]; then
    failures+=("${name} must be false in production")
  fi
}

require_secret() {
  local name="$1"
  local value="${!name:-}"
  local lower="${value,,}"
  if [[ -z "${value}" || "${lower}" == "default" || "${lower}" == "changeme" || "${lower}" == "change-me" || "${lower}" == "dev" || "${lower}" == *"example"* ]]; then
    failures+=("${name} must be set to a non-default secret in production")
  fi
}

require_not_true ADMIN_AUTH_DISABLED
require_true INTERNAL_SECURITY_ENABLED
require_false DEFAULT_ALLOW_WHEN_AUTH_DISABLED
require_secret JWT_SECRET

if [[ "${CORS_ALLOWED_ORIGINS:-}" == "*" ]]; then
  failures+=("CORS_ALLOWED_ORIGINS must not be '*' in production")
fi

if [[ "${REDMINE_EXECUTOR_ENABLED:-false}" == "true" ]]; then
  require_secret REDMINE_API_KEY
fi

if [[ "${GITLAB_EXECUTOR_ENABLED:-false}" == "true" ]]; then
  require_secret GITLAB_TOKEN
fi

if (( ${#failures[@]} > 0 )); then
  echo "[FAIL] Production security verification failed:" >&2
  for failure in "${failures[@]}"; do
    echo "  - ${failure}" >&2
  done
  exit 1
fi

echo "[OK] Production security verification passed."
