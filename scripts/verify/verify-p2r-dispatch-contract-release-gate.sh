#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADMIN_DIR="${ROOT_DIR}/ai-event-gateway-admin-ui"

echo "==> P2-R Admin UI static contract checks"
(
  cd "${ADMIN_DIR}"
  npm run verify:p2r
  npm run typecheck
  npm run lint
  NEXT_DIST_DIR="${NEXT_DIST_DIR:-.next-p2r}" npm run build
)

if [[ "${P2R_SKIP_DB_VERIFIERS:-false}" != "true" ]]; then
  echo "==> P2-R database contract verifiers"
  "${ROOT_DIR}/ai-event-gateway-core/scripts/verify/verify-p2m1-dispatch-contract-integrity.sh"
  "${ROOT_DIR}/ai-event-gateway-core/scripts/verify/verify-p2m2-dispatch-policy-contract-integrity.sh"
  "${ROOT_DIR}/ai-event-gateway-core/scripts/verify/verify-p2p-cms-content-review-contract.sh"
else
  echo "==> P2-R database contract verifiers skipped by P2R_SKIP_DB_VERIFIERS=true"
fi

if [[ "${P2R_SKIP_ROUTE_SMOKE:-false}" != "true" ]]; then
  echo "==> P2-R Admin UI route smoke"
  (
    cd "${ADMIN_DIR}"
    ADMIN_ROUTE_SMOKE_PATHS="/settings/dispatch-contract-builder,/settings/dispatch-task-definitions,/assignment-profiles,/settings/capabilities,/skills"       node scripts/route-smoke.mjs
  )
else
  echo "==> P2-R route smoke skipped by P2R_SKIP_ROUTE_SMOKE=true"
fi

if [[ "${P2R_SKIP_RUNTIME_E2E:-false}" != "true" ]]; then
  echo "==> P2-R Dispatch Contract Builder runtime smoke"
  (
    cd "${ADMIN_DIR}"
    node scripts/verify-p2r-dispatch-contract-builder.mjs
  )
else
  echo "==> P2-R runtime smoke skipped by P2R_SKIP_RUNTIME_E2E=true"
fi

echo "P2-R Dispatch Contract release gate passed."
