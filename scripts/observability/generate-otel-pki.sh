#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:-}"
if [[ -z "$OUT_DIR" ]]; then
  echo "Usage: $0 <secure-output-directory>" >&2
  exit 64
fi
if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required" >&2
  exit 69
fi
mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"
if find "$OUT_DIR" -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
  echo "Refusing to overwrite non-empty output directory: $OUT_DIR" >&2
  exit 73
fi

umask 077
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

openssl genrsa -out "$OUT_DIR/ca.key" 4096 >/dev/null 2>&1
openssl req -x509 -new -sha384 -days 3650 \
  -key "$OUT_DIR/ca.key" \
  -subj "/CN=OpenDispatch Observability CA/O=OpenSocket" \
  -out "$OUT_DIR/ca.crt"

issue_cert() {
  local name="$1"
  local usage="$2"
  local san="$3"
  local ext="$TMP_DIR/${name}.ext"
  openssl genrsa -out "$OUT_DIR/${name}.key" 3072 >/dev/null 2>&1
  openssl req -new -sha384 -key "$OUT_DIR/${name}.key" \
    -subj "/CN=${name}/O=OpenSocket" -out "$TMP_DIR/${name}.csr"
  {
    echo "basicConstraints=critical,CA:FALSE"
    echo "keyUsage=critical,digitalSignature,keyEncipherment"
    echo "extendedKeyUsage=${usage}"
    [[ -n "$san" ]] && echo "subjectAltName=${san}"
  } > "$ext"
  openssl x509 -req -sha384 -days 825 \
    -in "$TMP_DIR/${name}.csr" \
    -CA "$OUT_DIR/ca.crt" -CAkey "$OUT_DIR/ca.key" -CAcreateserial \
    -extfile "$ext" -out "$OUT_DIR/${name}.crt" >/dev/null 2>&1
}

issue_cert ingest-server serverAuth "DNS:otel-lb,DNS:otel-gateway-a,DNS:otel-gateway-b,DNS:localhost,IP:127.0.0.1"
issue_cert sampler-server serverAuth "DNS:otel-sampler-a,DNS:otel-sampler-b,DNS:localhost,IP:127.0.0.1"
issue_cert core-client clientAuth ""
issue_cert netty-client clientAuth ""
issue_cert adapter-worker-client clientAuth ""
issue_cert gateway-client clientAuth ""
issue_cert backend-client clientAuth ""
cp "$OUT_DIR/ca.crt" "$OUT_DIR/backend-ca.crt"

openssl rand -hex 48 > "$OUT_DIR/internal.token"
openssl rand -hex 48 > "$OUT_DIR/ingest.tokens"
# Replace these three values when the backend uses independently managed credentials.
openssl rand -hex 48 > "$OUT_DIR/backend-traces.token"
openssl rand -hex 48 > "$OUT_DIR/backend-metrics.token"
openssl rand -hex 48 > "$OUT_DIR/backend-logs.token"

chmod 600 "$OUT_DIR"/*
chmod 644 "$OUT_DIR"/*.crt
cat <<EOF
Generated OpenDispatch observability PKI and token files in:
  $OUT_DIR

Protect ca.key offline. For an externally managed observability backend, replace:
  backend-ca.crt
  backend-client.crt
  backend-client.key
  backend-*.token
with backend-issued material before deployment.
EOF
