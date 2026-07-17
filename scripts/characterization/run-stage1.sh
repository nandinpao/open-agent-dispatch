#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:---dry-run}"
case "$MODE" in
  --dry-run|--live|--strict) ;;
  *) echo "Usage: $0 [--dry-run|--live|--strict]" >&2; exit 2 ;;
esac

python3 scripts/maintenance/repair_nested_project_layout.py || exit $?
python3 scripts/architecture/stage0_dispatch_feature_freeze.py || exit $?
python3 scripts/verify/verify-stage1-backend-golden-path.py || exit $?
node scripts/acceptance/stage1-characterization-auth-self-test.mjs || exit $?

export DISPATCH_CHARACTERIZATION_STAGE=STAGE_1_BACKEND_GOLDEN_PATH
export STAGE1_REPORT_DIR="${STAGE1_REPORT_DIR:-$ROOT_DIR/.ci-output/stage1-characterization}"
mkdir -p "$STAGE1_REPORT_DIR"

stage1_exit=0
case "$MODE" in
  --dry-run)
    node scripts/acceptance/stage0-dispatch-characterization.mjs --dry-run --strict
    stage1_exit=$?
    ;;
  --live)
    node scripts/acceptance/stage0-dispatch-characterization.mjs
    stage1_exit=$?
    ;;
  --strict)
    node scripts/acceptance/stage0-dispatch-characterization.mjs --strict
    stage1_exit=$?
    ;;
esac

# Stage 8-F0c: always build drilldown evidence, even when strict mode fails.
python3 scripts/characterization/stage1_golden_path_drilldown_report.py || true

exit "$stage1_exit"
