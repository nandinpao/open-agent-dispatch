#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_ROOT="${ROOT_DIR}/deploy/sql/postgresql"
SQL_ENV="${SQL_ENV:-local}"
ENV_DIR="${SQL_ROOT}/${SQL_ENV}"

: "${PGHOST:=localhost}"
: "${PGPORT:=5432}"
: "${PGDATABASE:=aeg_core}"
: "${PGUSER:=aeg}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

case "${SQL_ENV}" in
  local|dev) ;;
  prod)
    echo "Refusing to run destructive drop script with SQL_ENV=prod." >&2
    exit 1
    ;;
  *)
    echo "Unsupported SQL_ENV='${SQL_ENV}'. Expected local or dev for drop." >&2
    exit 1
    ;;
esac

DROP_SQL="${ENV_DIR}/99_drop.sql"
test -f "${DROP_SQL}" || { echo "Missing drop SQL file: ${DROP_SQL}" >&2; exit 1; }

require_cmd psql

echo "WARNING: this will drop ai-event-gateway-core tables from ${PGHOST}:${PGPORT}/${PGDATABASE} using SQL_ENV=${SQL_ENV}."
read -r -p "Type DROP to continue: " confirm
if [[ "${confirm}" != "DROP" ]]; then
  echo "Cancelled."
  exit 0
fi

psql -v ON_ERROR_STOP=1 -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -f "${DROP_SQL}"
