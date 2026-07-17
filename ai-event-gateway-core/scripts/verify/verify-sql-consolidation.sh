#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_ROOT="${ROOT_DIR}/deploy/sql/postgresql"
RUNTIME_DB_DIR="${ROOT_DIR}/database-platform/src/main/resources/db"

required_sql=(
  "common/00_create_database.sql"
  "common/01_schema.sql"
  "common/03_seed_common.sql"
  "common/90_verify_schema.sql"
  "local/03_seed_local.sql"
  "local/90_verify_local.sql"
  "local/99_drop.sql"
  "dev/03_seed_dev.sql"
  "dev/90_verify_dev.sql"
  "dev/99_drop.sql"
  "prod/90_verify_prod.sql"
  "README.md"
)

for file in "${required_sql[@]}"; do
  test -f "${SQL_ROOT}/${file}"
done

# Runtime deploy SQL folder should no longer contain flat phase-style SQL files.
if find "${SQL_ROOT}" -maxdepth 1 -type f -name '*.sql' | grep -q .; then
  echo "Flat SQL files still exist directly under deploy/sql/postgresql." >&2
  exit 1
fi

# Production must not contain destructive SQL.
if find "${SQL_ROOT}/prod" -type f -name '*drop*.sql' | grep -q .; then
  echo "Production SQL folder contains destructive drop SQL." >&2
  exit 1
fi

# Previously broken table name must not remain.
if grep -R "incident_occurrence_summaries" -n "${SQL_ROOT}"; then
  echo "Old incorrect table name incident_occurrence_summaries still exists." >&2
  exit 1
fi

grep -q "incident_occurrence_summary" "${SQL_ROOT}/local/99_drop.sql"
grep -q "incident_occurrence_summary" "${SQL_ROOT}/dev/99_drop.sql"

test -f "${RUNTIME_DB_DIR}/README.md"
test -d "${RUNTIME_DB_DIR}/migration"

bash -n "${ROOT_DIR}/scripts/db/install-postgres-schema.sh"
bash -n "${ROOT_DIR}/scripts/db/verify-postgres-schema.sh"
bash -n "${ROOT_DIR}/scripts/db/drop-postgres-schema.sh"
bash -n "${ROOT_DIR}/scripts/db/create-postgres-database.sh"

echo "P12.6 SQL consolidation verification passed."
