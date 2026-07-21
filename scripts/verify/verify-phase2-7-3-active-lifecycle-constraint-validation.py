#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        print(f"Missing token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        print(f"Forbidden token in {path}: {token}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    report = "scripts/db/phase2-7-3-active-lifecycle-validate-readiness-report.sql"
    runner = "scripts/diagnostics/run-dispatch-active-lifecycle-validate-readiness-report.sh"
    migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V6__dispatch_validate_active_lifecycle_constraints.sql"
    doc = "docs/current/development/phase2-7-3-active-lifecycle-constraint-validation.md"
    changelog = "docs/current/phase2-7-3-change-log.md"
    files = "docs/current/phase2-7-3-modified-files.md"
    readme = "docs/current/README.md"
    er = "docs/current/data/dispatch-er-baseline.md"
    makefile = "Makefile"

    require(report, "Phase 2-7-3 Active Lifecycle Constraint Validate Readiness Report")
    require(report, "READY_TO_VALIDATE")
    require(report, "ALREADY_VALIDATED")
    require(report, "BLOCKED")
    require(report, "MISSING")
    require(report, "Active Lifecycle Blocker Details")
    require(report, "Active Lifecycle Advisory Findings")
    require(report, "DISPATCH_REQUEST_ASSIGNMENT_TASK_MISMATCH")
    for token in (
        "fk_task_assignments_task",
        "fk_dispatch_requests_assignment",
        "fk_dispatch_requests_task",
        "task_assignments ta",
        "dispatch_requests dr",
        "tasks t",
    ):
        require(report, token)

    # Readiness report must remain read-only.
    for token in (
        "ALTER TABLE",
        "VALIDATE CONSTRAINT",
        "UPDATE ",
        "DELETE ",
        "INSERT ",
        "CREATE ",
        "DROP ",
        "TRUNCATE",
    ):
        forbid(report, token)

    require(runner, "phase2-7-3-active-lifecycle-validate-readiness-report.sql")
    require(runner, "DATABASE_URL")
    require(runner, "psql")

    require(migration, "Phase 2-7-3 Validate Active Lifecycle Constraints")
    require(migration, "Preflight 1: all selected active lifecycle constraints must exist")
    require(migration, "Preflight 2: active lifecycle foreign keys must have zero violating rows")
    require(migration, "Preflight 3: advisory task consistency must not hide a tenant mismatch")
    require(migration, "Batch D: validate active Task -> Assignment -> Dispatch Request lifecycle FKs")
    for token in (
        "VALIDATE CONSTRAINT fk_task_assignments_task",
        "VALIDATE CONSTRAINT fk_dispatch_requests_assignment",
        "VALIDATE CONSTRAINT fk_dispatch_requests_task",
    ):
        require(migration, token)

    # V6 should validate existing V2 constraints only.
    for token in (
        "ADD CONSTRAINT",
        "CREATE TRIGGER",
        "CREATE TABLE",
        "DROP TABLE",
        "UPDATE ",
        "DELETE ",
        "INSERT ",
    ):
        forbid(migration, token)

    # V6 must not validate callback/history/legacy reference-only data.
    for token in (
        "task_callbacks",
        "routing_decisions",
        "flow_required_capabilities",
        "flow_agent_assignments",
        "dispatch_attempt_history",
    ):
        forbid(migration, token)

    require(doc, "Phase 2-7-3: Active Lifecycle Constraint Validation Readiness and Validation")
    require(doc, "run-dispatch-active-lifecycle-validate-readiness-report.sh")
    require(doc, "V6__dispatch_validate_active_lifecycle_constraints.sql")
    require(doc, "fk_task_assignments_task")
    require(doc, "DISPATCH_REQUEST_ASSIGNMENT_TASK_MISMATCH")
    require(changelog, "V6__dispatch_validate_active_lifecycle_constraints.sql")
    require(changelog, "phase2-7-3-active-lifecycle-validate-readiness-report.sql")
    require(files, "V6__dispatch_validate_active_lifecycle_constraints.sql")
    require(files, "make verify-phase2-7-3-active-lifecycle-constraint-validation")
    require(readme, "development/phase2-7-3-active-lifecycle-constraint-validation.md")
    require(readme, "make verify-phase2-7-3-active-lifecycle-constraint-validation")
    require(er, "Phase 2-7-3 active lifecycle constraint validation update")
    require(makefile, "verify-phase2-7-3-active-lifecycle-constraint-validation")
    require(makefile, "verify-phase2-7-2-batch-validate-current-configuration-constraints")

    print("Phase 2-7-3 active lifecycle constraint validation contract verified.")


if __name__ == "__main__":
    main()
