#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_FILE="${ROOT_DIR}/deploy/sql/postgresql/common/00_create_database.sql"
source "${SCRIPT_DIR}/_pg_env.sh"
source "${SCRIPT_DIR}/_pg_client.sh"

: "${PGDATABASE:=postgres}"
resolve_pg_env

test -f "${SQL_FILE}" || { echo "Missing SQL file: ${SQL_FILE}" >&2; exit 1; }

echo "Running optional database creation helper against ${PGHOST}:${PGPORT}/${PGDATABASE} as ${PGUSER}."
echo "Host psql is optional. Set POSTGRES_CLIENT_MODE=docker-exec or docker-run when PostgreSQL is Docker-hosted."
echo "The SQL file is intentionally mostly commented. Edit it according to the customer's DBA policy before use."
run_psql_file "${SQL_FILE}"
