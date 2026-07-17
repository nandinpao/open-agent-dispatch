#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"
fail() { echo "[ERROR] $*" >&2; exit 1; }

for f in \
  scripts/db/_pg_env.sh \
  scripts/db/flyway-migrate-postgres.sh \
  scripts/db/flyway-validate-postgres.sh \
  scripts/db/flyway-info-postgres.sh \
  scripts/db/flyway-repair-postgres.sh \
  scripts/db/install-postgres-schema.sh \
  scripts/dev/run-core-local.sh; do
  [[ -f "$f" ]] || fail "Missing $f"
  bash -n "$f" || fail "Shell syntax failed: $f"
done

grep -q 'flyway-maven-plugin' control-plane-app/pom.xml || fail "Core app POM must configure flyway-maven-plugin"
grep -q 'flyway-database-postgresql' control-plane-app/pom.xml || fail "Flyway Maven plugin must include PostgreSQL database support"
grep -q 'FLYWAY_BASELINE_ON_MIGRATE:=false' scripts/db/flyway-migrate-postgres.sh || fail "Flyway migrate script must default baselineOnMigrate=false"
grep -q 'FLYWAY_VALIDATE_ON_MIGRATE:=true' scripts/db/flyway-migrate-postgres.sh || fail "Flyway migrate script must default validateOnMigrate=true"
grep -q 'scripts/db/flyway-migrate-postgres.sh' scripts/dev/run-core-local.sh || fail "run-core-local must use Flyway migrate"
! grep -q 'scripts/db/install-postgres-schema.sh' scripts/dev/run-core-local.sh || fail "run-core-local must not use direct SQL installer"
grep -q 'ALLOW_OFFLINE_SQL_INSTALL' scripts/db/install-postgres-schema.sh || fail "Direct SQL installer must require explicit offline opt-in"

grep -q 'baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}' control-plane-app/src/main/resources/application-local.yml || fail "local Flyway baseline-on-migrate must default false"
grep -q 'baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}' control-plane-app/src/main/resources/application-dev.yml || fail "dev Flyway baseline-on-migrate must default false"
grep -q 'baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}' control-plane-app/src/main/resources/application-prod.yml || fail "prod Flyway baseline-on-migrate must default false"

for f in deploy/docker/docker-compose.core-local.yml deploy/docker/docker-compose.core-dev.yml; do
  ! grep -qE '^  postgres:' "$f" || fail "$f must not create PostgreSQL"
  ! grep -q 'postgres-schema:' "$f" || fail "$f must not include postgres-schema direct SQL installer"
  grep -q 'FLYWAY_ENABLED' "$f" || fail "$f must pass Flyway controls"
done

grep -q 'PG_SINGLE_URL=jdbc:postgresql://host.docker.internal:5432/aeg_core' deploy/env/.env.core-local.example || fail "Docker local env must point to existing host PostgreSQL by default"
grep -q 'PG_SINGLE_URL=jdbc:postgresql://127.0.0.1:5432/aeg_core' deploy/env/.env.core-host-local.example || fail "Host local env must point to existing local PostgreSQL by default"

grep -q 'Flyway is the source of truth' docs/i7.10.13-flyway-existing-postgres-alignment.md || fail "I7.10.13 doc must describe Flyway source-of-truth policy"

echo "I7.10.13 Core Flyway existing PostgreSQL alignment verification passed."
