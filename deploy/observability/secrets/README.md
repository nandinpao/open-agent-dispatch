# Production observability secret directory

This directory is intentionally documentation-only. **Do not commit generated certificates, private keys, or bearer tokens.**

Run `scripts/observability/generate-otel-pki.sh <output-directory>` on a secured administration host, then mount that output directory as `/run/secrets/otel`.

Required files:

- `ca.crt`
- `ingest-server.crt`, `ingest-server.key`
- `sampler-server.crt`, `sampler-server.key`
- `core-client.crt`, `core-client.key`
- `netty-client.crt`, `netty-client.key`
- `adapter-worker-client.crt`, `adapter-worker-client.key`
- `gateway-client.crt`, `gateway-client.key`
- `backend-ca.crt`
- `backend-client.crt`, `backend-client.key`
- `ingest.tokens`
- `internal.token`
- `backend-traces.token`
- `backend-metrics.token`
- `backend-logs.token`

Token files contain the token only, without the `Bearer` prefix. File permissions should be `0600`; the directory should be `0700`.
