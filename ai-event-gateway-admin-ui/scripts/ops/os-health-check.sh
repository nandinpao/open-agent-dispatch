#!/usr/bin/env bash
set -euo pipefail
CORE_BASE_URL="${CORE_BASE_URL:-${I7_CORE_BASE_URL:-http://127.0.0.1:18080}}"
NETTY_ADMIN_BASE_URL="${NETTY_ADMIN_BASE_URL:-${I7_NETTY_ADMIN_BASE_URL:-http://127.0.0.1:18081}}"
TOKEN_HEADER="${CLUSTER_INTERNAL_TOKEN_HEADER:-X-Cluster-Token}"
TOKEN="${CLUSTER_INTERNAL_TOKEN:-}"
CURL=(curl -fsS --max-time "${CURL_TIMEOUT_SECONDS:-5}")

echo "[health] Core: ${CORE_BASE_URL}"
"${CURL[@]}" "${CORE_BASE_URL}/actuator/health" || true
printf '\n[health] Netty: %s\n' "${NETTY_ADMIN_BASE_URL}"
"${CURL[@]}" "${NETTY_ADMIN_BASE_URL}/actuator/health" || true
printf '\n[health] Core metrics probe\n'
"${CURL[@]}" ${TOKEN:+-H "${TOKEN_HEADER}: ${TOKEN}"} "${CORE_BASE_URL}/actuator/prometheus" | head -n 40 || true
printf '\n[health] Netty metrics probe\n'
"${CURL[@]}" ${TOKEN:+-H "${TOKEN_HEADER}: ${TOKEN}"} "${NETTY_ADMIN_BASE_URL}/actuator/prometheus" | head -n 40 || true
