#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'MSG'
Direct SQL schema installation is no longer the default path.

ai-event-gateway-core uses Flyway as the schema lifecycle source of truth.
Use:
  scripts/db/flyway-migrate-postgres.sh
  scripts/db/flyway-validate-postgres.sh
  scripts/db/verify-postgres-schema.sh

For DBA/offline full installs only, set ALLOW_OFFLINE_SQL_INSTALL=true.
Host psql is not required; the script can use POSTGRES_CLIENT_MODE=docker-exec or docker-run.
MSG

if [[ "${ALLOW_OFFLINE_SQL_INSTALL:-false}" != "true" ]]; then
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_ROOT="${ROOT_DIR}/deploy/sql/postgresql"
COMMON_DIR="${SQL_ROOT}/common"
SQL_ENV="${SQL_ENV:-local}"
ENV_DIR="${SQL_ROOT}/${SQL_ENV}"
source "${SCRIPT_DIR}/_pg_env.sh"
source "${SCRIPT_DIR}/_pg_client.sh"
resolve_pg_env

run_sql() {
  local file="$1"
  if [[ -f "${file}" ]]; then
    echo "Running SQL: ${file#${ROOT_DIR}/}"
    run_psql_file "${file}"
  fi
}

case "${SQL_ENV}" in local|dev|prod) ;; *) echo "Unsupported SQL_ENV='${SQL_ENV}'" >&2; exit 1;; esac

echo "[WARN] Running DBA/offline SQL install. Runtime migrations must still be managed by Flyway."
run_sql "${COMMON_DIR}/01_schema.sql"
run_sql "${COMMON_DIR}/03_seed_common.sql"
run_sql "${ENV_DIR}/03_seed_${SQL_ENV}.sql"
run_sql "${COMMON_DIR}/90_verify_schema.sql"
run_sql "${ENV_DIR}/90_verify_${SQL_ENV}.sql"
