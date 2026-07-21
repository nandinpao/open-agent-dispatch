#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${CURRENT_GOLDEN_PATH_OUTPUT_DIR:-${ROOT_DIR}/.ci-output/current-minimum-live-golden-path}"
RUN_ID="${CURRENT_GOLDEN_PATH_RUN_ID:-current-golden-path-$(date -u +%Y%m%dT%H%M%SZ)}"
RUN_DIR="${OUTPUT_DIR}/${RUN_ID}"
LIVE_MODE="${CURRENT_GOLDEN_PATH_LIVE:-false}"
DRY_RUN_ARGS=()

mkdir -p "${RUN_DIR}"

if [[ "${LIVE_MODE}" != "true" && "${LIVE_MODE}" != "1" ]]; then
  DRY_RUN_ARGS=(--dry-run)
fi

log_step() {
  local step="$1"
  local logfile="$2"
  echo "==> ${step}"
  echo "${step}" >> "${RUN_DIR}/steps.log"
  shift 2
  (cd "${ROOT_DIR}" && "$@") > "${logfile}" 2>&1
}

SOURCE_SYSTEM="${CURRENT_GOLDEN_PATH_SOURCE_SYSTEM:-SOURCE_X}"
TENANT_ID="${CURRENT_GOLDEN_PATH_TENANT_ID:-tenant-current-golden-path}"
AGENT_ID="${CURRENT_GOLDEN_PATH_AGENT_ID:-agent-current-golden-path-001}"
POOL_ID="${CURRENT_GOLDEN_PATH_POOL_ID:-pool-current-golden-path-default}"
POOL_CODE="${CURRENT_GOLDEN_PATH_POOL_CODE:-DEFAULT_PROCESSING_POOL}"
FLOW_ID="${CURRENT_GOLDEN_PATH_FLOW_ID:-flow-current-golden-path-default}"

export SOURCE_SYSTEM_ONLY_SOURCE_SYSTEM="${SOURCE_SYSTEM}"
export SOURCE_SYSTEM_ONLY_TENANT_ID="${TENANT_ID}"
export SOURCE_SYSTEM_ONLY_AGENT_ID="${AGENT_ID}"
export SOURCE_SYSTEM_ONLY_POOL_ID="${POOL_ID}"
export SOURCE_SYSTEM_ONLY_POOL_CODE="${POOL_CODE}"
export SOURCE_SYSTEM_ONLY_FLOW_ID="${FLOW_ID}"
export SOURCE_SYSTEM_ONLY_OPERATOR_ID="current-golden-path-release-gate"
export STAGE23_TENANT_ID="${TENANT_ID}"
export STAGE23_AGENT_ID="${AGENT_ID}"
export STAGE23_OPERATOR_ID="current-golden-path-release-gate"

log_step \
  "SourceSystem-only default Flow/Pool/LOWEST_LOAD routing" \
  "${RUN_DIR}/01-source-system-only-routing.log" \
  node scripts/acceptance/source-system-only-golden-path.mjs "${DRY_RUN_ARGS[@]}"

# Phase 7A keeps the current delivery-confirmation gate as a transport proof wrapper.
# Phase 7B will collapse routing + runtime delivery into a single live scenario.
log_step \
  "Delivery ACK/Result confirmation contract" \
  "${RUN_DIR}/02-delivery-ack-result-contract.log" \
  node scripts/acceptance/dispatch-delivery-confirmation-e2e.mjs "${DRY_RUN_ARGS[@]}"

cat > "${RUN_DIR}/manifest.json" <<JSON
{
  "runId": "${RUN_ID}",
  "phase": "7A",
  "gate": "current-minimum-live-golden-path",
  "mode": "$( [[ "${#DRY_RUN_ARGS[@]}" -gt 0 ]] && echo dry-run || echo live )",
  "tenantId": "${TENANT_ID}",
  "sourceSystem": "${SOURCE_SYSTEM}",
  "flowId": "${FLOW_ID}",
  "targetPoolId": "${POOL_ID}",
  "selectionStrategy": "LOWEST_LOAD",
  "selectedAgentId": "${AGENT_ID}",
  "requiredPath": [
    "SourceSystem-only Event",
    "Default Flow",
    "Default Pool",
    "LOWEST_LOAD",
    "Mock/Runtime Agent",
    "Delivery",
    "ACK",
    "Result",
    "COMPLETED"
  ],
  "evidenceFiles": [
    "01-source-system-only-routing.log",
    "02-delivery-ack-result-contract.log",
    "steps.log"
  ],
  "notes": [
    "Phase 7A establishes the canonical entrypoint and evidence archive.",
    "Dry-run mode validates the contract without writing Task, Assignment, Delivery, ACK, or Result records.",
    "Live mode requires the local/CI stack, Java 25, Maven, Docker, and a connected runtime agent."
  ]
}
JSON

cat > "${RUN_DIR}/failure-map.md" <<'MD'
# Current Minimum Golden Path Failure Map

| Stage | Owner | Common failure | Next action |
|---|---|---|---|
| SourceSystem-only Event | Core intake | Intake rejected or sourceSystem missing | Check `/api/events/intake` envelope and source system config. |
| Default Flow | Core routing | Source Flow not found | Create active Source Flow for the source system. |
| Default Pool | Core routing | Default Pool missing | Assign `defaultPoolId` on the Source Flow. |
| LOWEST_LOAD | Core selection | No eligible candidate | Check Pool members and runtime eligibility. |
| Delivery | Netty transport | Dispatch request not delivered | Check Netty connection and gateway logs. |
| ACK | Agent runtime | ACK callback missing | Check agent process and callback route. |
| Result | Agent runtime/Core callback | terminal result missing | Check callback inbox and ledger. |
| COMPLETED | Core lifecycle | Task not terminal | Check Task lifecycle transition and callback summary. |
MD

echo "Current minimum golden path evidence written to ${RUN_DIR}"
