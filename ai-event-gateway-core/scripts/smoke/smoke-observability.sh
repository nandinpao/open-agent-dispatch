#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CORE_BASE_URL:-http://localhost:18080}"

echo "[1/4] health"
curl -fsS "${BASE_URL}/actuator/health" >/dev/null

echo "[2/4] metrics endpoint"
curl -fsS "${BASE_URL}/actuator/metrics" >/dev/null

echo "[3/4] prometheus endpoint"
curl -fsS "${BASE_URL}/actuator/prometheus" >/dev/null

echo "[4/4] operational summary"
curl -fsS "${BASE_URL}/api/ops/summary" >/dev/null

echo "Observability smoke OK"
