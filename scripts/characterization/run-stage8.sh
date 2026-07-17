#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---dry-run}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

case "$MODE" in
  --dry-run)
    python3 scripts/verify/verify-stage8-release-gate.py
    python3 scripts/characterization/stage8_release_gate_contract.py
    scripts/release/stage8-release-gate.sh --dry-run
    ;;
  --strict)
    python3 scripts/verify/verify-stage8-release-gate.py
    python3 scripts/characterization/stage8_release_gate_contract.py
    scripts/release/stage8-release-gate.sh --strict
    ;;
  *)
    echo "Usage: $0 [--dry-run|--strict]" >&2
    exit 2
    ;;
esac
