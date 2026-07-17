#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}/ai-event-gateway-admin-ui"

: "${P3M_RUNTIME_ACCEPTANCE_MODE:=live}"
: "${P3M_CORE_BASE_URL:=http://127.0.0.1:18080}"
: "${P3M_ADMIN_BASE_URL:=http://127.0.0.1:3000}"
: "${P3M_ACCEPTANCE_OUTPUT:=../.ci-output/reports/p3m-enforce-runtime-acceptance.json}"
export P3M_RUNTIME_ACCEPTANCE_MODE P3M_CORE_BASE_URL P3M_ADMIN_BASE_URL P3M_ACCEPTANCE_OUTPUT

npm run acceptance:p3m-enforce-runtime
