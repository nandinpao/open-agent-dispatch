#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/deploy/env/.env.local}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/deploy/docker-compose.local.yml}"
PROJECT_NAME="${PROJECT_NAME:-opendispatch}"
ADMIN_UI_URL="${ADMIN_UI_URL:-http://127.0.0.1:3000}"

if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${ROOT_DIR}/deploy/env/.env.local.example"
fi

compose=(docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

echo "==> Admin UI dependency endpoint"
curl -sS -i "${ADMIN_UI_URL%/}/api/health/dependencies" || true

echo
echo "==> Admin UI runtime Core connectivity"
"${compose[@]}" exec -T \
  -e ADMIN_UI_CORE_STARTUP_TIMEOUT_MS=5000 \
  -e ADMIN_UI_BACKEND_CONNECT_TIMEOUT_MS=1000 \
  admin-ui node /workspace/admin-ui/scripts/wait-for-core-backend.mjs

echo
echo "==> Admin UI runtime environment"
"${compose[@]}" exec -T admin-ui sh -c 'printf "CORE_BACKEND_ORIGIN=%s\nCORE_BACKEND_FALLBACK_ORIGINS=%s\nOPENDISPATCH_PUBLIC_HOST=%s\n" "${CORE_BACKEND_ORIGIN:-}" "${CORE_BACKEND_FALLBACK_ORIGINS:-}" "${OPENDISPATCH_PUBLIC_HOST:-}"'
