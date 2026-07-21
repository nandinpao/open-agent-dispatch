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
    migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V5__dispatch_validate_current_configuration_constraints.sql"
    doc = "docs/current/development/phase2-7-2-batch-validate-current-configuration-constraints.md"
    changelog = "docs/current/phase2-7-2-change-log.md"
    files = "docs/current/phase2-7-2-modified-files.md"
    readme = "docs/current/README.md"
    er = "docs/current/data/dispatch-er-baseline.md"
    makefile = "Makefile"

    require(migration, "Phase 2-7-2 Batch Validate Current Configuration Constraints")
    require(migration, "Preflight 1: all selected constraints must exist")
    require(migration, "Preflight 2: selected CHECK constraints must have zero violating rows")
    require(migration, "Preflight 3: selected Current configuration foreign keys must have zero")
    require(migration, "Batch A: validate Current configuration CHECK constraints first")
    require(migration, "Batch B: validate Source Flow / Agent Pool / Dispatch Policy foreign keys")
    require(migration, "Batch C: validate Agent Pool Member foreign keys")

    # Check constraints validated in this phase.
    for token in (
        "VALIDATE CONSTRAINT ck_agent_pools_selection_strategy_supported",
        "VALIDATE CONSTRAINT ck_agent_pools_status_supported",
        "VALIDATE CONSTRAINT ck_agent_pool_members_status_supported",
        "VALIDATE CONSTRAINT ck_agent_pool_members_weight_positive",
        "VALIDATE CONSTRAINT ck_agent_pool_members_priority_non_negative",
        "VALIDATE CONSTRAINT ck_dispatch_flows_status_supported",
        "VALIDATE CONSTRAINT ck_dispatch_policies_priority_non_negative",
        "VALIDATE CONSTRAINT ck_dispatch_policies_status_supported",
        "VALIDATE CONSTRAINT ck_dispatch_policies_routing_strategy_supported",
        "VALIDATE CONSTRAINT fk_dispatch_flows_source_system",
        "VALIDATE CONSTRAINT fk_dispatch_flows_default_pool",
        "VALIDATE CONSTRAINT fk_dispatch_policies_flow",
        "VALIDATE CONSTRAINT fk_dispatch_policies_source_system",
        "VALIDATE CONSTRAINT fk_dispatch_policies_target_pool",
        "VALIDATE CONSTRAINT fk_agent_pool_members_pool",
        "VALIDATE CONSTRAINT fk_agent_pool_members_agent_profile",
    ):
        require(migration, token)

    # Active lifecycle constraints must not be validated in Phase 2-7-2.
    for token in (
        "VALIDATE CONSTRAINT fk_task_assignments_task",
        "VALIDATE CONSTRAINT fk_dispatch_requests_assignment",
        "VALIDATE CONSTRAINT fk_dispatch_requests_task",
    ):
        forbid(migration, token)

    # Migration should not add new constraints or mutate data.
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

    require(doc, "Phase 2-7-2: Batch Validate Current Configuration Constraints")
    require(doc, "Current configuration")
    require(doc, "Batch A")
    require(doc, "Batch B")
    require(doc, "Batch C")
    require(doc, "fk_task_assignments_task")
    require(doc, "Phase 2-7-3")
    require(changelog, "V5__dispatch_validate_current_configuration_constraints.sql")
    require(changelog, "ALTER TABLE ... VALIDATE CONSTRAINT")
    require(files, "V5__dispatch_validate_current_configuration_constraints.sql")
    require(readme, "development/phase2-7-2-batch-validate-current-configuration-constraints.md")
    require(readme, "make verify-phase2-7-2-batch-validate-current-configuration-constraints")
    require(er, "Phase 2-7-2 current configuration constraint validation update")
    require(makefile, "verify-phase2-7-2-batch-validate-current-configuration-constraints")
    require(makefile, "verify-phase2-7-1-constraint-validate-readiness-report")

    print("Phase 2-7-2 batch validate current configuration constraints contract verified.")


if __name__ == "__main__":
    main()
