#!/usr/bin/env python3
"""Static Stage 3 gate for atomic Dispatch Flow aggregate persistence."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
CONTROLLER = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java"
API = ROOT / "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
FLOW_VIEW = ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowView.java"
RULE_VIEW = ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRuleView.java"
RUNTIME_CANDIDATES = ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcGenericCandidateAgentRepository.java"
RUNTIME_RULES = ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java"
TESTS = [
    ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage3DispatchFlowAggregateTransactionContainerTest.java",
    ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/DispatchFlowControllerAggregateMutationTest.java",
]


def require(text: str, needles: list[str], label: str) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"ERROR: {label} missing Stage 3 contract markers: {missing}")


def reject(text: str, needles: list[str], label: str) -> None:
    found = [needle for needle in needles if needle in text]
    if found:
        raise SystemExit(f"ERROR: {label} still exposes forbidden partial mutation: {found}")


def main() -> None:
    service = SERVICE.read_text()
    controller = CONTROLLER.read_text()
    api = API.read_text()
    flow_view = FLOW_VIEW.read_text()
    rule_view = RULE_VIEW.read_text()
    candidates = RUNTIME_CANDIDATES.read_text()
    rules = RUNTIME_RULES.read_text()

    require(service, [
        "@Transactional\n    public DispatchFlowView createOrUpdateFlow",
        "transactionMode=FULL_REPLACEMENT",
        "delete from flow_agent_assignments",
        "delete from flow_required_capabilities",
        "delete from dispatch_policies",
        "pg_advisory_xact_lock",
        "validateAggregate(normalized)",
        "verifyPersistedAggregate(normalized, saved)",
        "CandidatePoolMode.EXPLICIT_FLOW_AGENTS.name()",
        "Agent does not exist in the selected tenant",
        "Required Capabilities are not active in this tenant's Capability Catalog",
        "validateChildRowOwnership(flow)",
        "requestedSkill is a Runtime compatibility projection",
        "ID is already owned by another Dispatch Flow",
        "dispatch_policies.flow_id = excluded.flow_id",
        "flow_required_capabilities.flow_id = excluded.flow_id",
        "flow_agent_assignments.flow_id = excluded.flow_id",
    ], "DispatchFlowManagementService")


    require(flow_view, [
        'defaultCapabilityRequirementMode = "NONE"',
        'defaultCandidatePoolMode = "EXPLICIT_FLOW_AGENTS"',
    ], "DispatchFlowView")
    require(rule_view, [
        "private String capabilityRequirementMode;",
        'candidatePoolMode = "EXPLICIT_FLOW_AGENTS"',
    ], "DispatchFlowRuleView")
    reject(flow_view + rule_view, [
        'defaultCapabilityRequirementMode = "SOURCE_DEFAULT"',
        'defaultCandidatePoolMode = "SOURCE_SYSTEM_POOL"',
        'capabilityRequirementMode = "SOURCE_DEFAULT"',
        'candidatePoolMode = "SOURCE_SYSTEM_POOL"',
    ], "Dispatch Flow DTO defaults")

    require(controller, [
        "Partial Dispatch Flow mutation is disabled",
        "HttpStatus.CONFLICT",
        "requireTenantMatch",
    ], "DispatchFlowController")

    reject(api, [
        "upsertDispatchFlowRule(",
        "upsertDispatchFlowSkill(",
        "upsertDispatchFlowAgent(",
    ], "coreAdminApi")

    require(candidates, [
        "from flow_agent_assignments",
        "assignment_status",
        "approval_status",
    ], "Runtime candidate repository")
    require(rules, [
        "from dispatch_policies p",
        "flow_required_capabilities",
        "candidate_pool_mode",
    ], "Runtime Flow Rule repository")

    for test in TESTS:
        if not test.exists():
            raise SystemExit(f"ERROR: missing Stage 3 TDD test: {test.relative_to(ROOT)}")

    # Stage 3 may modify existing production code but must not add a new dispatch model or migration.
    migration_hits = list((ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration").glob("*stage3*"))
    if migration_hits:
        raise SystemExit(f"ERROR: Stage 3 must not add a new migration/model: {migration_hits}")

    print("Stage 3 Dispatch Flow aggregate static verification passed.")


if __name__ == "__main__":
    main()
