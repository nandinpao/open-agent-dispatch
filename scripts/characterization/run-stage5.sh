#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---dry-run}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

case "$MODE" in
  --dry-run)
    python3 scripts/verify/verify-stage5-real-event-task-diagnosis.py
    python3 scripts/characterization/stage5_real_event_task_diagnosis_contract.py
    ;;
  --strict)
    python3 scripts/verify/verify-stage5-real-event-task-diagnosis.py
    python3 scripts/characterization/stage5_real_event_task_diagnosis_contract.py
    make test-stage5-admin-ui
    make test-stage5-core
    ;;
  *)
    echo "Usage: $0 [--dry-run|--strict]" >&2
    exit 2
    ;;
esac
