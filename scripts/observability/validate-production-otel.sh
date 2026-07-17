#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COLLECTOR_IMAGE="${OTEL_COLLECTOR_IMAGE:-otel/opentelemetry-collector-contrib:0.156.0}"
SECRET_DIR="${OTEL_SECRET_DIR:-}"
if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required for Collector runtime validation" >&2
  exit 69
fi
if [[ -z "$SECRET_DIR" || ! -d "$SECRET_DIR" ]]; then
  echo "OTEL_SECRET_DIR must point to the generated observability secret directory" >&2
  exit 64
fi
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT
for config in otel-collector-gateway.yml otel-collector-sampler.yml; do
  storage_dir="$TMP_ROOT/${config%.yml}"
  mkdir -p "$storage_dir"
  echo "Validating $config with $COLLECTOR_IMAGE"
  docker run --rm \
    -e OTEL_BACKEND_TRACES_ENDPOINT="${OTEL_BACKEND_TRACES_ENDPOINT:-localhost:4317}" \
    -e OTEL_BACKEND_METRICS_ENDPOINT="${OTEL_BACKEND_METRICS_ENDPOINT:-https://localhost/api/v1/write}" \
    -e OTEL_BACKEND_LOGS_ENDPOINT="${OTEL_BACKEND_LOGS_ENDPOINT:-localhost:4317}" \
    -v "$ROOT_DIR/deploy/observability/$config:/etc/otelcol-contrib/config.yaml:ro" \
    -v "$SECRET_DIR:/run/secrets/otel:ro" \
    -v "$storage_dir:/var/lib/otelcol" \
    "$COLLECTOR_IMAGE" validate --config=/etc/otelcol-contrib/config.yaml
done
