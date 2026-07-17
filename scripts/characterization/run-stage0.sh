#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:---dry-run}"
case "$MODE" in
  --dry-run|--live|--strict) ;;
  *) echo "Usage: $0 [--dry-run|--live|--strict]" >&2; exit 2 ;;
esac

python3 scripts/architecture/stage0_dispatch_feature_freeze.py
python3 scripts/characterization/stage0_static_characterization.py

status=0
case "$MODE" in
  --dry-run)
    node scripts/acceptance/stage0-dispatch-characterization.mjs --dry-run || status=$?
    ;;
  --live)
    node scripts/acceptance/stage0-dispatch-characterization.mjs || status=$?
    ;;
  --strict)
    node scripts/acceptance/stage0-dispatch-characterization.mjs --strict || status=$?
    ;;
esac

python3 scripts/characterization/stage0_failure_map.py || true
exit "$status"
