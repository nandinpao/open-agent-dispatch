#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
fail() { echo "[ERROR] $*" >&2; exit 1; }

for f in "$ROOT_DIR/deploy/docker/docker-compose.core-local.yml" "$ROOT_DIR/deploy/docker/docker-compose.core-dev.yml"; do
  [[ -f "$f" ]] || fail "Missing compose file: ${f#$ROOT_DIR/}"
  if grep -qE '^  postgres:' "$f"; then
    fail "${f#$ROOT_DIR/} must not create PostgreSQL; local/dev use an existing PostgreSQL server"
  fi
  if grep -q 'postgres-schema:' "$f"; then
    fail "${f#$ROOT_DIR/} must not use postgres-schema SQL installer; Flyway owns schema lifecycle"
  fi
  grep -q 'FLYWAY_ENABLED' "$f" || fail "${f#$ROOT_DIR/} must expose Flyway settings"
  grep -q 'ai-event-gateway-core:' "$f" || fail "${f#$ROOT_DIR/} must define core service"
done

echo "Local/dev Docker existing-PostgreSQL Flyway boundary verification passed."
