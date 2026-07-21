#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_FILE="$ROOT_DIR/scripts/db/phase2-7-1-dispatch-constraint-validate-readiness-report.sql"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required to run the dispatch constraint validate readiness report." >&2
  exit 127
fi

if [[ -n "${DATABASE_URL:-}" ]]; then
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$SQL_FILE"
else
  psql -v ON_ERROR_STOP=1 -f "$SQL_FILE"
fi
