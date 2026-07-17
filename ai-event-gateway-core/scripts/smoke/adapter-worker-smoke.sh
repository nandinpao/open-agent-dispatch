#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:18080}"
ADAPTER_TYPE="${1:-MCP}"
WORKER_ID="${2:-worker-${ADAPTER_TYPE,,}-001}"
LEASE_SECONDS="${3:-120}"
TOKEN_HEADER="${TOKEN_HEADER:-X-Internal-Token}"
TOKEN="${TOKEN:-}"

AUTH_ARGS=()
if [[ -n "${TOKEN}" ]]; then
  AUTH_ARGS=(-H "${TOKEN_HEADER}: ${TOKEN}")
fi

need_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required for this smoke script" >&2
    exit 1
  fi
}

need_jq

echo "[1/5] Core status"
curl -fsS "${BASE_URL}/api/core/status" | jq '(.data // .) | {version, adapterExecutorMode, adapterExecutorExternalMode}'

echo "[2/5] Adapter action metadata"
curl -fsS "${BASE_URL}/api/adapter-actions/metadata" | jq '(.data // .) | {executorMode, executorExternalMode, store}'

echo "[3/5] Claim next ${ADAPTER_TYPE} action for ${WORKER_ID}"
CLAIM_RESPONSE=$(curl -fsS -X POST "${BASE_URL}/internal/adapter-actions/claim?adapterType=${ADAPTER_TYPE}&workerId=${WORKER_ID}&leaseSeconds=${LEASE_SECONDS}" "${AUTH_ARGS[@]}" || true)
if [[ -z "${CLAIM_RESPONSE}" ]]; then
  echo "No claimable ${ADAPTER_TYPE} adapter action found. Create a PENDING adapter action first, then rerun this script."
  exit 0
fi

echo "${CLAIM_RESPONSE}" | jq '(.data // .) | {actionId, adapterType, actionType, status, claimedBy, leaseExpiresAt}'
ACTION_ID=$(echo "${CLAIM_RESPONSE}" | jq -r '(.data // .).actionId')

echo "[4/5] Heartbeat claimed action"
curl -fsS -X POST "${BASE_URL}/internal/adapter-actions/${ACTION_ID}/heartbeat" \
  "${AUTH_ARGS[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"workerId\":\"${WORKER_ID}\",\"leaseSeconds\":${LEASE_SECONDS}}" \
  | jq '(.data // .) | {actionId, status, claimedBy, workerHeartbeatAt, leaseExpiresAt}'

echo "[5/5] Complete claimed action"
curl -fsS -X POST "${BASE_URL}/internal/adapter-actions/${ACTION_ID}/complete" \
  "${AUTH_ARGS[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"workerId\":\"${WORKER_ID}\",\"responseRef\":\"smoke-${ACTION_ID}\"}" \
  | jq '(.data // .) | {actionId, status, responseRef, claimedBy, leaseExpiresAt}'

echo "Adapter external worker smoke completed."
