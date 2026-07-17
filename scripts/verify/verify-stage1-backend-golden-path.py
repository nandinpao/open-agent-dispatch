#!/usr/bin/env python3
"""Static contract gate for Stage 1 Backend Golden Path convergence."""
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"missing Stage 1 artifact: {relative}")
    return path.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(text: str, marker: str, source: str) -> None:
    if marker not in text:
        fail(f"{source} missing required Stage 1 contract marker: {marker}")


def forbid(text: str, marker: str, source: str) -> None:
    if marker in text:
        fail(f"{source} contains forbidden Stage 1 marker: {marker}")


def main() -> int:
    operation_path = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/OperationEligibilityEvaluator.java"
    generic_path = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/GenericDispatchEligibilityService.java"
    offer_path = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/DispatchEligibilityPolicy.java"
    runner_path = "scripts/acceptance/stage0-dispatch-characterization.mjs"

    operation = read(operation_path)
    for marker in [
        "CandidatePoolMode.EXPLICIT_FLOW_AGENTS",
        "RequirementResolutionMode.NONE",
        "RequirementResolutionMode.EXPLICIT_CAPABILITY",
        "FLOW_RULE_OPERATION_CONTRACT_ACCEPTED",
        "EFFECTFUL_FLOW_REQUIRES_GOVERNED_OPERATION_PROFILE",
    ]:
        require(operation, marker, operation_path)

    generic = read(generic_path)
    for marker in [
        "boolean standardFlowContract = isStandardFlowContract(requirement)",
        "standardFlowContract ? null : sourceAssignmentRepository",
        "standardFlowContract || blank(profileId)",
        "CandidatePoolMode.EXPLICIT_FLOW_AGENTS",
    ]:
        require(generic, marker, generic_path)

    offer = read(offer_path)
    for marker in [
        "FLOW_RULE_AGENT_CAPABILITY_RUNTIME",
        'facts.put("legacyEligibility", "DECOMMISSIONED")',
        "Agent runtime target is eligible for Task offer",
    ]:
        require(offer, marker, offer_path)
    for legacy_marker in [
        "backendAssignmentProfileGate",
        "SKIPPED_FLOW_RULE",
        "isFormalFlowRuleTask",
    ]:
        forbid(offer, legacy_marker, offer_path)

    runner = read(runner_path)
    for marker in [
        "characterizationStage",
        "agent-local-001",
        "/admin/capabilities/",
        "/admin/agents/${encodeURIComponent(agentId)}/capabilities",
        "taskCompleted === true",
        "Stage 1 requires a real connected Agent",
        "/api/events/intake",
        "ensureAdminAuthentication",
        "cookieJar",
        "csrfHeaderName",
        "rememberResponseCookies",
        "STAGE1_ADMIN_USERNAME",
        "STAGE1_ENV_FILE",
        "validateAgentSelection",
        "agent-local-ci-001 belongs to docker-compose.ci.yml",
        "inspectFlowAggregate",
        "flowDiagnostics",
        "ruleConditionMatchesEvent",
        "flowAgentAssignmentCount",
        "legacyLeakInStandardEvidence",
        "RUNTIME_LOOKUP_OR_ASSIGNMENT_DID_NOT_USE_FLOW_AGGREGATE",
        "LEGACY_BLOCKER_LEAKED_IN_STANDARD_EVIDENCE",
        "ensureAgentProfile",
        "FLOW_CREATE_REJECTED_AGENT_NOT_FOUND",
        "allowApiError",
        "apiEnvelopeOk",
        "apiCodeOf",
        "Agent profile precondition failed",
    ]:
        require(runner, marker, runner_path)
    for marker in ["sourceSystem: 'CMS'", 'sourceSystem: "CMS"', "createDefaultCapabilities: true"]:
        forbid(runner, marker, runner_path)


    flow_routing = read("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java")
    require(flow_routing, "Assign at least one approved Agent to the Flow", "FlowRuleRoutingService.java")
    forbid(flow_routing, "use SOURCE_DEFAULT with an active Source Default", "FlowRuleRoutingService.java")
    forbid(flow_routing, "approved Agent Source Coverage", "FlowRuleRoutingService.java")

    auth_self_test = read("scripts/acceptance/stage1-characterization-auth-self-test.mjs")
    for marker in [
        "csrf-before-login",
        "OPENDISPATCH_ADMIN_SESSION=session-1",
        "csrf-after-login",
        "mutationCsrf",
    ]:
        require(auth_self_test, marker, "stage1-characterization-auth-self-test.mjs")

    required_tests = [
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/governance/eligibility/GenericDispatchEligibilityServiceStandardFlowTest.java",
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/assignment/DispatchEligibilityPolicyFlowRuleTest.java",
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/governance/routing/GenericCandidateAgentProviderStandardFlowTest.java",
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/task/InMemoryTaskRepositoryFlowRecoveryTest.java",
    ]
    for test in required_tests:
        text = read(test)
        forbid(text, '"CMS"', test)
        forbid(text, '"MES"', test)
        forbid(text, '"ERP"', test)


    in_memory_recovery = read("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/InMemoryTaskRepository.java")
    forbid(in_memory_recovery, "getRequestedSkill", "InMemoryTaskRepository.java recovery claim")
    require(in_memory_recovery, "getNextDispatchAttemptAt() != null", "InMemoryTaskRepository.java due-time recovery")
    task_dao = read("ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml")
    forbid(task_dao, "upper(coalesce(nullif(t.requested_skill, ''), 'NO_REQUESTED_SKILL')) = 'NO_REQUESTED_SKILL'", "TaskDao.xml")
    require(task_dao, "t.next_dispatch_attempt_at is not null", "TaskDao.xml due-time recovery")

    makefile = read("Makefile")
    for target in [
        "verify-stage1-backend-golden-path:",
        "test-stage1-backend-golden-path:",
        "test-stage1-characterization-auth:",
        "characterize-stage1-dry-run:",
        "characterize-stage1-live:",
        "characterize-stage1-strict:",
    ]:
        require(makefile, target, "Makefile")

    stage1_runner = read("scripts/characterization/run-stage1.sh")
    require(stage1_runner, "STAGE_1_BACKEND_GOLDEN_PATH", "scripts/characterization/run-stage1.sh")
    require(stage1_runner, "repair_nested_project_layout.py", "scripts/characterization/run-stage1.sh")
    require(stage1_runner, "stage1-characterization-auth-self-test.mjs", "scripts/characterization/run-stage1.sh")
    require(stage1_runner, "stage1_golden_path_drilldown_report.py", "scripts/characterization/run-stage1.sh")

    drilldown = read("scripts/characterization/stage1_golden_path_drilldown_report.py")
    for marker in [
        "Stage 8-F0c",
        "FLOW_RULE_NOT_PERSISTED",
        "RULE_EVENT_CONDITION_MISMATCH",
        "FLOW_AGENT_ASSIGNMENT_NOT_PERSISTED",
        "RUNTIME_LOOKUP_OR_ASSIGNMENT_DID_NOT_USE_FLOW_AGGREGATE",
        "LEGACY_BLOCKER_LEAKED_IN_STANDARD_EVIDENCE",
        "stage1-golden-path-drilldown.md",
    ]:
        require(drilldown, marker, "scripts/characterization/stage1_golden_path_drilldown_report.py")

    freeze = read("scripts/architecture/stage0_dispatch_feature_freeze.py")
    require(freeze, "nested OpenDispatch module copies", "scripts/architecture/stage0_dispatch_feature_freeze.py")
    require(freeze, "repair_nested_project_layout.py --apply", "scripts/architecture/stage0_dispatch_feature_freeze.py")

    repair = read("scripts/maintenance/repair_nested_project_layout.py")
    require(repair, "byte-for-byte identical", "scripts/maintenance/repair_nested_project_layout.py")
    require(repair, "Conflicting nested modules are never removed automatically", "scripts/maintenance/repair_nested_project_layout.py")

    smoke = read("scripts/local-smoke.sh")
    for smoke_marker in [
        "SMOKE_ACCEPT_CORE_STATUS_WHEN_HEALTH_CONVERGING",
        "/actuator/health/liveness",
        "/api/core/status",
        "Recent Core/Redis/PostgreSQL/Flyway logs",
    ]:
        require(smoke, smoke_marker, "scripts/local-smoke.sh")

    read("docs/STAGE1_BACKEND_GOLDEN_PATH/README.md")
    print("Stage 1 Backend Golden Path static contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
