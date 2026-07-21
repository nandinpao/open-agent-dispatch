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


def require_file(path: str) -> None:
    if not (ROOT / path).is_file():
        print(f"Missing required file: {path}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    # Verify that each Phase 2 local verifier remains present. The consolidated
    # gate performs its own cross-phase contract checks instead of recursively
    # invoking the full Make dependency chain, which keeps it usable in CI and
    # local environments with short command timeouts.
    for verifier in (
        "scripts/verify/verify-phase2-1-dispatch-integrity-report.py",
        "scripts/verify/verify-phase2-2-dispatch-integrity-repair-plan.py",
        "scripts/verify/verify-phase2-3-dispatch-composite-fk.py",
        "scripts/verify/verify-phase2-4-dispatch-enum-checks.py",
        "scripts/verify/verify-phase2-5-dispatch-optimistic-locking.py",
        "scripts/verify/verify-phase2-6-api-ui-optimistic-lock-conflict-handling.py",
        "scripts/verify/verify-phase2-7-1-constraint-validate-readiness-report.py",
        "scripts/verify/verify-phase2-7-2-batch-validate-current-configuration-constraints.py",
        "scripts/verify/verify-phase2-7-3-active-lifecycle-constraint-validation.py",
    ):
        require_file(verifier)

    migrations = {
        "v2": "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V2__dispatch_referential_integrity.sql",
        "v3": "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V3__dispatch_enum_checks.sql",
        "v4": "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V4__dispatch_optimistic_locking.sql",
        "v5": "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V5__dispatch_validate_current_configuration_constraints.sql",
        "v6": "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V6__dispatch_validate_active_lifecycle_constraints.sql",
    }
    for path in migrations.values():
        require_file(path)

    # V2: tenant-aware Current path and active lifecycle referential integrity.
    for token in (
        "V2__dispatch_referential_integrity",
        "fk_dispatch_flows_source_system",
        "fk_dispatch_flows_default_pool",
        "fk_dispatch_policies_flow",
        "fk_dispatch_policies_source_system",
        "fk_dispatch_policies_target_pool",
        "fk_agent_pool_members_pool",
        "fk_agent_pool_members_agent_profile",
        "fk_task_assignments_task",
        "fk_dispatch_requests_assignment",
        "fk_dispatch_requests_task",
        "tenant_id",
        "NOT VALID",
    ):
        require(migrations["v2"], token)

    # V3: Current dispatch enum/value protection.
    for token in (
        "V3__dispatch_enum_checks",
        "ck_agent_pools_selection_strategy_supported",
        "LOWEST_LOAD",
        "WEIGHTED_SCORE",
        "MANUAL_ONLY",
        "ck_agent_pool_members_weight_positive",
        "ck_agent_pool_members_priority_non_negative",
        "ck_dispatch_policies_routing_strategy_supported",
        "NOT VALID",
    ):
        require(migrations["v3"], token)

    # V4: optimistic locking schema support.
    for token in (
        "V4__dispatch_optimistic_locking",
        "dispatch_admin_optimistic_lock_touch",
        "source_systems",
        "dispatch_flows",
        "dispatch_policies",
        "agent_pools",
        "agent_pool_members",
        "version",
        "updated_by",
    ):
        require(migrations["v4"], token)

    # V5: validated Current configuration constraints, not active lifecycle.
    for token in (
        "V5__dispatch_validate_current_configuration_constraints",
        "Batch A: validate Current configuration CHECK constraints first",
        "Batch B: validate Source Flow / Agent Pool / Dispatch Policy foreign keys",
        "Batch C: validate Agent Pool Member foreign keys",
        "VALIDATE CONSTRAINT ck_agent_pools_selection_strategy_supported",
        "VALIDATE CONSTRAINT fk_dispatch_flows_source_system",
        "VALIDATE CONSTRAINT fk_agent_pool_members_agent_profile",
    ):
        require(migrations["v5"], token)
    for token in (
        "VALIDATE CONSTRAINT fk_task_assignments_task",
        "VALIDATE CONSTRAINT fk_dispatch_requests_assignment",
        "VALIDATE CONSTRAINT fk_dispatch_requests_task",
    ):
        forbid(migrations["v5"], token)

    # V6: active lifecycle validation only.
    for token in (
        "V6__dispatch_validate_active_lifecycle_constraints",
        "Batch D: validate active Task -> Assignment -> Dispatch Request lifecycle FKs",
        "VALIDATE CONSTRAINT fk_task_assignments_task",
        "VALIDATE CONSTRAINT fk_dispatch_requests_assignment",
        "VALIDATE CONSTRAINT fk_dispatch_requests_task",
        "assignment/task mismatch",
    ):
        require(migrations["v6"], token)
    for token in (
        "task_callbacks",
        "routing_decisions",
        "flow_required_capabilities",
        "flow_agent_assignments",
        "dispatch_attempt_history",
    ):
        forbid(migrations["v6"], token)

    # Diagnostic and readiness assets.
    required_assets = (
        "scripts/db/phase2-1-dispatch-integrity-report.sql",
        "scripts/diagnostics/run-dispatch-integrity-report.sh",
        "scripts/db/phase2-2-dispatch-integrity-repair-plan.sql",
        "scripts/db/phase2-2-dispatch-integrity-repair-dry-run.sql",
        "scripts/diagnostics/run-dispatch-integrity-repair-dry-run.sh",
        "scripts/db/phase2-7-1-dispatch-constraint-validate-readiness-report.sql",
        "scripts/diagnostics/run-dispatch-constraint-validate-readiness-report.sh",
        "scripts/db/phase2-7-3-active-lifecycle-validate-readiness-report.sql",
        "scripts/diagnostics/run-dispatch-active-lifecycle-validate-readiness-report.sh",
    )
    for path in required_assets:
        require_file(path)

    # Read-only behavior for the individual diagnostic SQL assets is enforced by
    # their dedicated Phase 2 verifiers. The consolidated gate verifies that the
    # assets exist and are wired into the release-readiness contract without
    # over-matching explanatory comments such as "does not DROP data".

    # API/UI optimistic lock conflict handling contract for Agent Pool and Source Flow.
    controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java"
    service = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
    handler = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java"
    api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    pool_ui = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx"
    flow_ui = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"
    types = "ai-event-gateway-admin-ui/lib/types/core.ts"

    for token in ("If-Match", "expectedVersion", "Agent Pool", "Source Flow"):
        require(controller, token)
    for token in ("RESOURCE_VERSION_CONFLICT", "where agent_pools.version = :expectedVersion", "where dispatch_flows.version = :expectedVersion"):
        require(service, token)
    require(handler, "RESOURCE_VERSION_CONFLICT")
    require(api, "If-Match")
    for token in ("version", "updatedBy", "updatedAt", "RESOURCE_VERSION_CONFLICT"):
        require(pool_ui, token)
        require(flow_ui, token)
    for token in ("version?", "updatedBy?", "updatedAt?"):
        require(types, token)

    # Documentation and Make target.
    doc = "docs/current/development/phase2-8-release-gate-consolidation.md"
    changelog = "docs/current/phase2-8-change-log.md"
    files = "docs/current/phase2-8-modified-files.md"
    readme = "docs/current/README.md"
    er = "docs/current/data/dispatch-er-baseline.md"
    makefile = "Makefile"
    for path in (doc, changelog, files):
        require_file(path)
    require(doc, "Phase 2-8: Phase 2 Release Gate Consolidation")
    require(doc, "make verify-phase2-release-gate")
    require(doc, "callback/history/evidence")
    require(doc, "legacy reference-only")
    require(changelog, "verify-phase2-release-gate.py")
    require(files, "scripts/verify/verify-phase2-release-gate.py")
    require(readme, "development/phase2-8-release-gate-consolidation.md")
    require(readme, "make verify-phase2-release-gate")
    require(er, "Phase 2-8 release gate consolidation update")
    require(makefile, "verify-phase2-release-gate")
    require(makefile, "verify-phase2-release-gate.py")

    print("Phase 2 release gate contract verified.")


if __name__ == "__main__":
    main()
