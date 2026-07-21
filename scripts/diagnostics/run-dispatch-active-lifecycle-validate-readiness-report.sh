#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_FILE="${REPO_ROOT}/scripts/db/phase2-7-3-active-lifecycle-validate-readiness-report.sql"

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "Missing SQL file: ${SQL_FILE}" >&2
  exit 1
fi

if [[ -n "${DATABASE_URL:-}" ]]; then
  psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
else
  psql -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
fi
