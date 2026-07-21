#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_FILE="${ROOT_DIR}/scripts/db/phase2-1-dispatch-integrity-report.sql"

if ! command -v psql >/dev/null 2>&1; then
  echo "ERROR: psql is required to run the Phase 2-1 dispatch integrity report." >&2
  exit 127
fi

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "ERROR: missing SQL report file: ${SQL_FILE}" >&2
  exit 2
fi

# Prefer DATABASE_URL when provided; otherwise rely on PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD.
if [[ -n "${DATABASE_URL:-}" ]]; then
  exec psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
fi

exec psql -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
