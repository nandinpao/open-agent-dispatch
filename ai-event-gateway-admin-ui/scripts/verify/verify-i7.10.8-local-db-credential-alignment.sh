#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() { echo "[I7.10.9][CORE][FAIL] $1" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || fail "Missing required file: $1"; }

LOCAL_YML="ai-event-gateway-core-app/src/main/resources/application-local.yml"
DEV_YML="ai-event-gateway-core-app/src/main/resources/application-dev.yml"
PROD_YML="ai-event-gateway-core-app/src/main/resources/application-prod.yml"
LOCAL_ENV="deploy/env/.env.core-local.example"
DEV_ENV="deploy/env/.env.core-dev.example"
LOCAL_COMPOSE="deploy/docker/docker-compose.core-local.yml"
DEV_COMPOSE="deploy/docker/docker-compose.core-dev.yml"
DB_HELPER="deploy/sql/postgresql/common/00_create_database.sql"
DOC="docs/i7.10.9-maven-reactor-parent-layout-fix.md"
POLICY="release/i7.10/maven-reactor-parent-layout-fix-policy.md"

for f in "$LOCAL_YML" "$DEV_YML" "$PROD_YML" "$LOCAL_ENV" "$DEV_ENV" "$LOCAL_COMPOSE" "$DEV_COMPOSE" "$DB_HELPER" "$DOC" "$POLICY"; do
  require_file "$f"
done

for f in "$LOCAL_YML" "$DEV_YML"; do
  grep -q 'username: ${PG_SINGLE_USERNAME:admin}' "$f" || fail "$f must default PG_SINGLE_USERNAME to admin"
  grep -q 'password: ${PG_SINGLE_PASSWORD:123456}' "$f" || fail "$f must default PG_SINGLE_PASSWORD to 123456"
done

for f in "$LOCAL_ENV" "$DEV_ENV"; do
  grep -q '^PG_SINGLE_USERNAME=admin$' "$f" || fail "$f must set PG_SINGLE_USERNAME=admin"
  grep -q '^PG_SINGLE_PASSWORD=123456$' "$f" || fail "$f must set PG_SINGLE_PASSWORD=123456"
done

for f in "$LOCAL_COMPOSE" "$DEV_COMPOSE"; do
  grep -q 'POSTGRES_USER: ${POSTGRES_USER:-admin}' "$f" || fail "$f must default POSTGRES_USER to admin"
  grep -q 'POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-123456}' "$f" || fail "$f must default POSTGRES_PASSWORD to 123456"
  grep -q 'PGPASSWORD: ${POSTGRES_PASSWORD:-123456}' "$f" || fail "$f schema installer must use POSTGRES_PASSWORD default 123456"
  grep -q 'pg_isready -U $${POSTGRES_USER:-admin}' "$f" || fail "$f healthcheck must use POSTGRES_USER default admin"
  grep -q 'psql -v ON_ERROR_STOP=1 -h postgres -p 5432 -U "$${POSTGRES_USER:-admin}"' "$f" || fail "$f schema installer must use POSTGRES_USER default admin"
done

grep -q "create user admin with password '123456'" "$DB_HELPER" || fail "DB helper must document admin / 123456 local account"

# Production must stay explicit-only and must not inherit local defaults.
grep -q 'username: ${PG_SINGLE_USERNAME}$' "$PROD_YML" || fail "prod username must remain explicit-only"
grep -q 'password: ${PG_SINGLE_PASSWORD}$' "$PROD_YML" || fail "prod password must remain explicit-only"
if grep -Eq 'PG_SINGLE_USERNAME:admin|PG_SINGLE_PASSWORD:123456|PG_SINGLE_USERNAME=admin|PG_SINGLE_PASSWORD=123456' "$PROD_YML" deploy/env/.env.core-prod.example; then
  fail "prod config/env must not include local DB credential defaults"
fi

grep -q 'Local Test DB account' "$DOC" || fail "I7.10.9 doc must mention Local Test DB account"
grep -q 'Production profiles must not provide local DB credential defaults' "$POLICY" || fail "policy must protect production credential boundary"

echo "I7.10.9 Core local DB credential alignment verification passed."
