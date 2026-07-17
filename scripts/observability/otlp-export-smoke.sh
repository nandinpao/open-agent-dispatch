#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${PROJECT_NAME:-opendispatch}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.local.yml}"
ENV_FILE="${ENV_FILE:-deploy/env/.env.local.example}"
OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
TIMEOUT_SECONDS="${OTLP_SMOKE_TIMEOUT_SECONDS:-90}"
KEEP_ADAPTER_WORKER="${KEEP_OTLP_SMOKE_ADAPTER_WORKER:-false}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_NAME="${2:?}"; shift 2 ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift 2 ;;
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --output-dir) OUTPUT_DIR="${2:?}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:?}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -f "$COMPOSE_FILE" ]] || { echo "Missing Compose file: $COMPOSE_FILE" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { echo "Missing env file: $ENV_FILE" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 1; }

# Load only simple KEY=VALUE entries used by this smoke test.
source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"

CORE_HTTP_PORT="${CORE_HTTP_PORT:-18080}"
NETTY_ADMIN_HTTP_PORT="${NETTY_ADMIN_HTTP_PORT:-18081}"
ADAPTER_WORKER_HTTP_PORT="${ADAPTER_WORKER_HTTP_PORT:-18090}"
OTEL_COLLECTOR_HEALTH_PORT="${OTEL_COLLECTOR_HEALTH_PORT:-13133}"

CORE_URL="${OTLP_SMOKE_CORE_URL:-http://127.0.0.1:${CORE_HTTP_PORT}}"
NETTY_URL="${OTLP_SMOKE_NETTY_URL:-http://127.0.0.1:${NETTY_ADMIN_HTTP_PORT}}"
ADAPTER_URL="${OTLP_SMOKE_ADAPTER_URL:-http://127.0.0.1:${ADAPTER_WORKER_HTTP_PORT}}"
COLLECTOR_HEALTH_URL="${OTLP_SMOKE_COLLECTOR_HEALTH_URL:-http://127.0.0.1:${OTEL_COLLECTOR_HEALTH_PORT}/}"

mkdir -p "$OUTPUT_DIR/observability"
COLLECTOR_LOG="$OUTPUT_DIR/observability/otel-collector-smoke.log"
REPORT_FILE="$OUTPUT_DIR/observability/otlp-export-smoke.txt"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

compose=(docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --profile observability-smoke)

cleanup() {
  local code=$?
  "${compose[@]}" logs --no-color --since "$STARTED_AT" otel-collector > "$COLLECTOR_LOG" 2>&1 || true
  if [[ "$KEEP_ADAPTER_WORKER" != "true" ]]; then
    "${compose[@]}" stop adapter-worker >/dev/null 2>&1 || true
    "${compose[@]}" rm -f adapter-worker >/dev/null 2>&1 || true
  fi
  if [[ $code -ne 0 ]]; then
    echo "OTLP smoke failed. Collector evidence: $COLLECTOR_LOG" >&2
    tail -160 "$COLLECTOR_LOG" >&2 || true
  fi
  exit "$code"
}
trap cleanup EXIT

wait_http() {
  local label="$1" url="$2" deadline=$((SECONDS + TIMEOUT_SECONDS)) code
  while (( SECONDS < deadline )); do
    code="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "$url" 2>/dev/null || true)"
    if [[ "$code" =~ ^[234] ]]; then
      echo "[OK] $label: HTTP $code"
      return 0
    fi
    sleep 2
  done
  echo "[FAIL] $label did not respond: $url" >&2
  return 1
}

traceparent() {
  python3 - <<'PY'
import secrets
print(f"00-{secrets.token_hex(16)}-{secrets.token_hex(8)}-01")
PY
}

emit_request() {
  local url="$1" tp
  tp="$(traceparent)"
  curl -sS -o /dev/null --connect-timeout 2 --max-time 8 \
    -H "traceparent: ${tp}" \
    -H "baggage: otlp.smoke=true" \
    "$url" || true
}

# The main local/CI stack should already be running. Starting the profile service
# also starts missing dependencies without recreating healthy containers.
"${compose[@]}" up -d --no-recreate otel-collector adapter-worker

wait_http "OpenTelemetry Collector" "$COLLECTOR_HEALTH_URL"
wait_http "Core" "$CORE_URL/actuator/health"
wait_http "Netty" "$NETTY_URL/actuator/health"
wait_http "Adapter Worker" "$ADAPTER_URL/actuator/health"

# Generate explicit HTTP server observations. 404 responses are intentional and
# avoid mutating business state while still producing spans and HTTP metrics.
for _ in 1 2 3; do
  emit_request "$CORE_URL/__otel_smoke__/core"
  emit_request "$NETTY_URL/__otel_smoke__/netty"
  emit_request "$ADAPTER_URL/__otel_smoke__/adapter-worker"
done

EXPECTED_SERVICES=(
  ai-event-gateway-core
  ai-event-gateway-netty
  ai-event-gateway-adapter-worker
)

deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  "${compose[@]}" logs --no-color --since "$STARTED_AT" otel-collector > "$COLLECTOR_LOG" 2>&1 || true
  missing=0
  for service in "${EXPECTED_SERVICES[@]}"; do
    if ! grep -Fq "$service" "$COLLECTOR_LOG"; then
      missing=1
      break
    fi
  done
  trace_ok=0
  metric_ok=0
  grep -Eq 'TracesExporter.*"spans": [1-9][0-9]*' "$COLLECTOR_LOG" && trace_ok=1 || true
  grep -Eq 'MetricsExporter.*"metrics": [1-9][0-9]*' "$COLLECTOR_LOG" && metric_ok=1 || true
  if [[ $missing -eq 0 && $trace_ok -eq 1 && $metric_ok -eq 1 ]]; then
    {
      echo "OpenDispatch P1-B OTLP export smoke passed"
      echo "timestamp=$STARTED_AT"
      echo "collector=$COLLECTOR_HEALTH_URL"
      echo "trace_export=true"
      echo "metric_export=true"
      printf 'service=%s\n' "${EXPECTED_SERVICES[@]}"
      echo "collector_log=$COLLECTOR_LOG"
    } > "$REPORT_FILE"
    cat "$REPORT_FILE"
    exit 0
  fi
  sleep 3
done

echo "Collector did not expose trace and metric evidence for all executable applications within ${TIMEOUT_SECONDS}s." >&2
for service in "${EXPECTED_SERVICES[@]}"; do
  grep -Fq "$service" "$COLLECTOR_LOG" || echo "Missing service.name evidence: $service" >&2
done
grep -Eq 'TracesExporter.*"spans": [1-9][0-9]*' "$COLLECTOR_LOG" || echo "Missing non-empty trace export evidence" >&2
grep -Eq 'MetricsExporter.*"metrics": [1-9][0-9]*' "$COLLECTOR_LOG" || echo "Missing non-empty metric export evidence" >&2
exit 1
