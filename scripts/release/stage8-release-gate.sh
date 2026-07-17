#!/usr/bin/env bash
set -uo pipefail
STAGE8_MODE="${1:---dry-run}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/.ci-output/stage8-release-gate"
LOG_DIR="$OUT/logs"
STEPS_FILE="$OUT/steps.jsonl"
COMMAND_LOG="$OUT/commands.log"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RELEASE_READY=false
MANAGED_LOCAL_STACK_STARTED=false

cd "$ROOT"

case "$STAGE8_MODE" in
  --dry-run|--strict) ;;
  *) echo "Usage: $0 [--dry-run|--strict]" >&2; exit 2 ;;
esac

rm -rf "$OUT"
mkdir -p "$LOG_DIR"
: > "$COMMAND_LOG"
: > "$STEPS_FILE"

# Stage 8 required release evidence matrix. These literal labels are checked by
# verify-stage8-release-gate.py and must not be renamed casually.
# Stage 8-F0a strict failure-capture artifacts are always generated:
# - release-ready.json
# - release-ready.md
# - failures.json
# - failure-map.md
# - steps.jsonl
# - logs/*.log

REQUIRED_EVIDENCE=(
  "Fresh DB Golden Path"
  "No-Capability Golden Path"
  "Explicit-Capability Golden Path"
  "Upgrade DB Golden Path"
  "Multi-Tenant Isolation"
  "Restart Recovery"
  "Browser E2E"
  "No Legacy Runtime Dependency"
  "No Source-Specific Hardcode"
)

slugify() {
  echo "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-//; s/-$//'
}

record_and_report() {
  local release_ready="$1"
  local failed_step="${2:-}"
  local exit_code="${3:-0}"
  local log_file="${4:-}"
  local completed_at
  completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  python3 scripts/release/stage8_release_report.py \
    --output-dir "$OUT" \
    --mode="$STAGE8_MODE" \
    --started-at "$STARTED_AT" \
    --completed-at "$completed_at" \
    --release-ready "$release_ready" \
    --steps-file "$STEPS_FILE" \
    --failed-step "$failed_step" \
    --exit-code "$exit_code" \
    --log-file "$log_file" \
    --command-log "$COMMAND_LOG"
}

run_gate() {
  local name="$1"
  shift
  local command="$*"
  local slug
  slug="$(slugify "$name")"
  local log_file="$LOG_DIR/${slug}.log"
  local started_at completed_at exit_code
  started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "[$started_at] $name :: $command" | tee -a "$COMMAND_LOG"

  bash -lc "$command" > "$log_file" 2>&1
  exit_code=$?
  completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  python3 scripts/release/stage8_record_step.py \
    --step "$name" \
    --command "$command" \
    --exit-code "$exit_code" \
    --started-at "$started_at" \
    --completed-at "$completed_at" \
    --log-file "$log_file" \
    --steps-file "$STEPS_FILE"

  if [[ "$exit_code" -ne 0 ]]; then
    record_and_report false "$name" "$exit_code" "$log_file"
    echo "Stage 8 release gate failed: step=$name exitCode=$exit_code log=$log_file" >&2
    return "$exit_code"
  fi

  return 0
}

if [[ "$STAGE8_MODE" == "--dry-run" ]]; then
  run_gate "Stage 0 feature freeze" "make verify-stage0-dispatch-feature-freeze" || exit $?
  run_gate "Stage 1 static Golden Path" "make verify-stage1-backend-golden-path" || exit $?
  run_gate "Stage 2 tenant/sql dry-run" "make characterize-stage2-dry-run" || exit $?
  run_gate "Stage 3 Flow aggregate dry-run" "make characterize-stage3-dry-run" || exit $?
  run_gate "Stage 4 beginner UI static" "make verify-stage4-beginner-ui-navigation" || exit $?
  run_gate "Stage 5 real Task diagnosis static" "make verify-stage5-real-event-task-diagnosis" || exit $?
  run_gate "Stage 6 recovery dry-run" "make characterize-stage6-dry-run" || exit $?
  run_gate "Stage 7 Legacy isolation dry-run" "make characterize-stage7-dry-run" || exit $?
  run_gate "Stage 8 contract report" "python3 scripts/characterization/stage8_release_gate_contract.py" || exit $?
  run_gate "No Source-Specific Hardcode dry-run" "python3 scripts/architecture/zero_special_case_guard.py" || exit $?
  record_and_report false "" 0 ""
else
  # Strict mode is the only mode that can produce release-grade evidence.
  # It intentionally requires Java 25, Maven, Docker/PostgreSQL, Admin UI deps,
  # live Core/Netty/Agent behavior, Browser E2E, and release lifecycle checks.
  run_gate "Toolchain" "make check-toolchain" || exit $?

  # Stage 8-F0g: the strict Stage 1 Golden Path is live, not static.  The
  # previous gate failed inside `make characterize-stage1-strict` when Core was
  # not listening on 127.0.0.1:18080.  Managed mode now creates an isolated
  # fresh local stack before Stage 1, while still allowing operators to opt out
  # and use an already-running stack.
  if [[ "${STAGE8_MANAGED_LOCAL_STACK:-true}" == "true" ]]; then
    run_gate "Bootstrap isolated fresh local runtime stack" "scripts/release/stage8-managed-local-stack.sh up" || exit $?
    MANAGED_LOCAL_STACK_STARTED=true
  fi
  run_gate "Local Runtime Readiness" "scripts/release/stage8-local-runtime-preflight.sh" || exit $?

  run_gate "Fresh DB Golden Path / No-Capability Golden Path / Explicit-Capability Golden Path" "make characterize-stage1-strict" || exit $?
  run_gate "Tenant and PostgreSQL contract" "make characterize-stage2-strict" || exit $?
  run_gate "Flow aggregate transaction and rollback" "make characterize-stage3-strict" || exit $?
  run_gate "Real Event to Task diagnosis" "make characterize-stage5-strict" || exit $?
  run_gate "Configuration recovery and Task actions" "make characterize-stage6-strict" || exit $?
  run_gate "No Legacy Runtime Dependency" "make characterize-stage7-strict" || exit $?
  if [[ "$MANAGED_LOCAL_STACK_STARTED" == "true" ]]; then
    run_gate "Stop isolated local runtime stack before CI release" "scripts/release/stage8-managed-local-stack.sh down" || exit $?
  fi
  run_gate "PostgreSQL optional filters" "make test-stage2-postgres-optional-filters" || exit $?
  run_gate "Flow aggregate PostgreSQL tests" "make test-stage3-dispatch-flow-aggregate" || exit $?
  run_gate "Stage 5 Core tests" "make test-stage5-core" || exit $?
  run_gate "Stage 6 Core restart/recovery tests" "make test-stage6-core" || exit $?
  run_gate "Stage 7 Legacy isolation Core tests" "make test-stage7-core" || exit $?
  run_gate "Upgrade DB Golden Path / Restart Recovery / Browser E2E" "make ci-release" || exit $?
  run_gate "Legacy inventory read-only report" "make report-stage7-legacy-inventory" || exit $?
  run_gate "No Source-Specific Hardcode" "python3 scripts/architecture/zero_special_case_guard.py" || exit $?
  record_and_report true "" 0 ""
fi

if [[ "$STAGE8_MODE" == "--strict" ]]; then
  echo "Stage 8 release gate completed: mode=$STAGE8_MODE releaseReady=true"
else
  echo "Stage 8 release gate completed: mode=$STAGE8_MODE releaseReady=false"
fi
