#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---dry-run}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

case "$MODE" in
  --dry-run)
    python3 scripts/verify/verify-stage7-legacy-isolation.py
    python3 scripts/characterization/stage7_legacy_isolation_retirement_contract.py
    python3 scripts/migration/stage7_legacy_dispatch_inventory.py --dry-run --output .ci-output/stage7-legacy-inventory/dry-run.json
    ;;
  --strict)
    python3 scripts/verify/verify-stage7-legacy-isolation.py
    python3 scripts/characterization/stage7_legacy_isolation_retirement_contract.py
    python3 scripts/migration/stage7_legacy_dispatch_inventory.py --dry-run --output .ci-output/stage7-legacy-inventory/dry-run.json
    make test-stage7-admin-ui
    make test-stage7-core
    ;;
  *)
    echo "Usage: $0 [--dry-run|--strict]" >&2
    exit 2
    ;;
esac
