#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fail(){ echo "[ERROR] $*" >&2; exit 1; }

[[ -f "$ROOT_DIR/scripts/db/_pg_client.sh" ]] || fail "Missing scripts/db/_pg_client.sh"
grep -q "POSTGRES_CLIENT_MODE" "$ROOT_DIR/scripts/db/_pg_client.sh" || fail "_pg_client.sh must support POSTGRES_CLIENT_MODE."
grep -q "docker-exec" "$ROOT_DIR/scripts/db/_pg_client.sh" || fail "_pg_client.sh must support docker-exec."
grep -q "docker-run" "$ROOT_DIR/scripts/db/_pg_client.sh" || fail "_pg_client.sh must support docker-run."
if grep -q "Missing required command: psql" "$ROOT_DIR/scripts/db/verify-postgres-schema.sh"; then
  fail "verify-postgres-schema.sh must not require host psql."
fi
grep -q "run_psql_file" "$ROOT_DIR/scripts/db/verify-postgres-schema.sh" || fail "verify-postgres-schema.sh must use _pg_client.sh run_psql_file."
grep -q "flyway-migrate-postgres.sh" "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "run-core-local.sh must keep Flyway migrate prestart."
grep -q "flyway-validate-postgres.sh" "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "run-core-local.sh must keep Flyway validate prestart."
grep -q "verify-postgres-schema.sh" "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "run-core-local.sh must verify schema after Flyway."
grep -q "POSTGRES_CLIENT_MODE=auto" "$ROOT_DIR/deploy/env/.env.core-host-local.example" || fail "host local env must document POSTGRES_CLIENT_MODE."

for f in scripts/db/create-postgres-database.sh scripts/db/install-postgres-schema.sh scripts/db/verify-local-docker-schema.sh; do
  bash -n "$ROOT_DIR/$f" || fail "Shell syntax failed: $f"
  if grep -q "Missing required command: psql" "$ROOT_DIR/$f"; then
    fail "$f must not require host psql."
  fi
done
grep -q "run_psql_file" "$ROOT_DIR/scripts/db/install-postgres-schema.sh" || fail "offline SQL installer must use Docker-capable PostgreSQL client helper."
grep -q "run_psql_file" "$ROOT_DIR/scripts/db/create-postgres-database.sh" || fail "database creation helper must use Docker-capable PostgreSQL client helper."
grep -q "verify-postgres-schema.sh" "$ROOT_DIR/scripts/db/verify-local-docker-schema.sh" || fail "local docker schema verifier must delegate to Docker-capable verifier."

grep -q "I7.10.14 Docker PostgreSQL Client" "$ROOT_DIR/docs/i7.10.14-docker-postgres-client-flyway-alignment.md" || fail "Missing I7.10.14 docs."

echo "I7.10.14 Docker PostgreSQL client / Flyway alignment verification passed."
