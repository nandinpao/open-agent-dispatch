#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLAN_SQL="$ROOT_DIR/scripts/db/phase2-2-dispatch-integrity-repair-plan.sql"
DRY_RUN_SQL="$ROOT_DIR/scripts/db/phase2-2-dispatch-integrity-repair-dry-run.sql"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required to run the Phase 2-2 dispatch integrity repair dry run." >&2
  exit 1
fi

if [[ -n "${DATABASE_URL:-}" ]]; then
  echo "== Running Phase 2-2 repair plan =="
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$PLAN_SQL"
  echo "== Running Phase 2-2 repair dry run =="
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$DRY_RUN_SQL"
else
  echo "== Running Phase 2-2 repair plan =="
  psql -v ON_ERROR_STOP=1 -f "$PLAN_SQL"
  echo "== Running Phase 2-2 repair dry run =="
  psql -v ON_ERROR_STOP=1 -f "$DRY_RUN_SQL"
fi
