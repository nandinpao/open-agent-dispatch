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


def forbid_executable_statement(path: str, keyword: str) -> None:
    text = read(path)
    for line_no, line in enumerate(text.splitlines(), start=1):
        stripped = line.strip().lower()
        if not stripped or stripped.startswith("--") or stripped.startswith("\\"):
            continue
        if stripped.startswith(keyword.lower()):
            print(f"Forbidden executable statement in {path}:{line_no}: {line}", file=sys.stderr)
            sys.exit(1)


def main() -> None:
    sql = "scripts/db/phase2-7-1-dispatch-constraint-validate-readiness-report.sql"
    runner = "scripts/diagnostics/run-dispatch-constraint-validate-readiness-report.sh"
    doc = "docs/current/development/phase2-7-1-constraint-validate-readiness-report.md"
    changelog = "docs/current/phase2-7-1-change-log.md"
    files = "docs/current/phase2-7-1-modified-files.md"
    readme = "docs/current/README.md"
    er = "docs/current/data/dispatch-er-baseline.md"
    makefile = "Makefile"

    # SQL report shape.
    require(sql, "Phase 2-7-1 Dispatch Constraint Validate Readiness Report")
    require(sql, "READY_TO_VALIDATE")
    require(sql, "ALREADY_VALIDATED")
    require(sql, "BLOCKED")
    require(sql, "MISSING")
    require(sql, "pg_constraint")
    require(sql, "convalidated")
    require(sql, "violation_counts")
    require(sql, "blocker_details")
    require(sql, "fk_dispatch_flows_source_system")
    require(sql, "fk_dispatch_flows_default_pool")
    require(sql, "fk_dispatch_policies_flow")
    require(sql, "fk_dispatch_policies_source_system")
    require(sql, "fk_dispatch_policies_target_pool")
    require(sql, "fk_agent_pool_members_pool")
    require(sql, "fk_agent_pool_members_agent_profile")
    require(sql, "fk_task_assignments_task")
    require(sql, "fk_dispatch_requests_assignment")
    require(sql, "fk_dispatch_requests_task")
    require(sql, "ck_agent_pools_selection_strategy_supported")
    require(sql, "ck_agent_pools_status_supported")
    require(sql, "ck_agent_pool_members_status_supported")
    require(sql, "ck_agent_pool_members_weight_positive")
    require(sql, "ck_agent_pool_members_priority_non_negative")
    require(sql, "ck_dispatch_flows_status_supported")
    require(sql, "ck_dispatch_policies_priority_non_negative")
    require(sql, "ck_dispatch_policies_status_supported")
    require(sql, "ck_dispatch_policies_routing_strategy_supported")

    # Phase 2-7-1 must remain read-only. Meta/SELECT/report only.
    # Comments and report messages may mention later migration commands, but the
    # SQL file itself must not execute mutating DDL/DML statements.
    for keyword in ("alter table", "update", "delete", "insert", "create", "drop", "truncate"):
        forbid_executable_statement(sql, keyword)

    # Runner is a psql wrapper for the read-only report.
    require(runner, "phase2-7-1-dispatch-constraint-validate-readiness-report.sql")
    require(runner, "DATABASE_URL")
    require(runner, "psql")

    # Documentation and make target.
    require(doc, "Phase 2-7-1: Constraint Validate Readiness Report")
    require(doc, "does **not** validate constraints")
    require(doc, "READY_TO_VALIDATE")
    require(doc, "BLOCKED")
    require(doc, "Phase 2-7-2")
    require(changelog, "No `VALIDATE CONSTRAINT` was executed")
    require(files, "scripts/db/phase2-7-1-dispatch-constraint-validate-readiness-report.sql")
    require(readme, "development/phase2-7-1-constraint-validate-readiness-report.md")
    require(readme, "make verify-phase2-7-1-constraint-validate-readiness-report")
    require(er, "Phase 2-7-1 constraint validate readiness update")
    require(makefile, "verify-phase2-7-1-constraint-validate-readiness-report")
    require(makefile, "verify-phase2-6-api-ui-optimistic-lock-conflict-handling")

    print("Phase 2-7-1 constraint validate readiness report contract verified.")


if __name__ == "__main__":
    main()
