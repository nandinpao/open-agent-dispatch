#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_ROOT="${ROOT_DIR}/deploy/sql/postgresql"
COMMON_DIR="${SQL_ROOT}/common"
SQL_ENV="${SQL_ENV:-local}"
ENV_DIR="${SQL_ROOT}/${SQL_ENV}"
source "${SCRIPT_DIR}/_pg_env.sh"
source "${SCRIPT_DIR}/_pg_client.sh"
resolve_pg_env

case "${SQL_ENV}" in local|dev|prod) ;; *) echo "Unsupported SQL_ENV='${SQL_ENV}'" >&2; exit 1;; esac

run_sql() {
  local file="$1"
  if [[ -f "${file}" ]]; then
    echo "Verifying SQL: ${file#${ROOT_DIR}/}"
    run_psql_file "${file}"
  fi
}

# Flyway is the schema lifecycle owner. This verifier checks resulting objects only.
run_sql "${COMMON_DIR}/90_verify_schema.sql"
run_sql "${ENV_DIR}/90_verify_${SQL_ENV}.sql"
