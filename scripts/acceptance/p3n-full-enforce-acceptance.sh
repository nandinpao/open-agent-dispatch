#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}/ai-event-gateway-admin-ui"

export P3N_RUNTIME_ACCEPTANCE_MODE="${P3N_RUNTIME_ACCEPTANCE_MODE:-${P3M_RUNTIME_ACCEPTANCE_MODE:-fixture}}"
export P3N_CORE_BASE_URL="${P3N_CORE_BASE_URL:-${P3M_CORE_BASE_URL:-http://127.0.0.1:18080}}"
export P3N_ADMIN_BASE_URL="${P3N_ADMIN_BASE_URL:-${P3M_ADMIN_BASE_URL:-http://127.0.0.1:3000}}"
export P3N_ACCEPTANCE_OUTPUT_DIR="${P3N_ACCEPTANCE_OUTPUT_DIR:-${ROOT_DIR}/.ci-output/reports/p3n-full-enforce}"

npm run acceptance:p3n-full-enforce
