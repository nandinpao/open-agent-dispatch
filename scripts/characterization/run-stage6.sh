#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---dry-run}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

case "$MODE" in
  --dry-run)
    python3 scripts/verify/verify-stage6-recovery-task-actions.py
    python3 scripts/characterization/stage6_recovery_task_actions_contract.py
    ;;
  --strict)
    python3 scripts/verify/verify-stage6-recovery-task-actions.py
    python3 scripts/characterization/stage6_recovery_task_actions_contract.py
    make test-stage6-admin-ui
    make test-stage6-core
    ;;
  *)
    echo "Usage: $0 [--dry-run|--strict]" >&2
    exit 2
    ;;
esac
