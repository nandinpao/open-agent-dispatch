#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"Missing required file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")

def assert_absent(rel: str) -> None:
    if (ROOT / rel).exists():
        errors.append(f"Retired standard governance artifact must be removed: {rel}")

def assert_contains(rel: str, token: str) -> None:
    text = read(rel)
    if token not in text:
        errors.append(f"{rel} must contain {token!r}")

def assert_not_contains(rel: str, tokens: list[str]) -> None:
    text = read(rel)
    for token in tokens:
        if token in text:
            errors.append(f"{rel} must not contain retired dispatch-governance standard-path token {token!r}")

# Standard operator/API entry points are deleted, not hidden.
for rel in [
    "ai-event-gateway-admin-ui/lib/api/dispatchGovernanceApi.ts",
    "ai-event-gateway-admin-ui/lib/types/dispatchGovernance.ts",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchGovernanceController.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ActionGovernanceController.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/SourceCoverageEligibilityEvaluator.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/OperationEligibilityEvaluator.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/ActionAuthorizationEvaluator.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/GovernanceEligibilityEvaluator.java",

    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchGovernanceConfigurationService.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/AgentSourceAssignmentRepository.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchOperationProfileRepository.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/SourceSystemDispatchDefaultRepository.java",
    "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcAgentSourceAssignmentRepository.java",
    "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcDispatchOperationProfileRepository.java",
    "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcSourceSystemDispatchDefaultRepository.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/AgentSourceAssignment.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchOperationProfile.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/SourceSystemDispatchDefault.java",
]:
    assert_absent(rel)

resolver = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/GenericDispatchRequirementResolver.java"
assert_contains(resolver, "public GenericDispatchRequirementResolver()")
assert_contains(resolver, "DISPATCH_FLOW_DIRECT")
assert_contains(resolver, "FLOW_AGENT_ASSIGNMENT")
assert_contains(resolver, "parallelDispatchModelsRemoved")
assert_not_contains(resolver, [
    "SourceSystemDispatchDefaultRepository",
    "DispatchOperationProfileRepository",
    "resolveSourceDefault",
    "resolveLegacy",
    "SOURCE_BASELINE",
    "SOURCE_DEFAULT",
    "OperationProfile",
    "ActionGrant",
    "AgentSourceAssignment",
])

candidate_provider = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericCandidateAgentProvider.java"
assert_contains(candidate_provider, "Flow Agent Assignments")
assert_contains(candidate_provider, "CandidatePoolMode.EXPLICIT_FLOW_AGENTS")
assert_contains(candidate_provider, "CandidatePoolOrigin.EXPLICIT_FLOW_ASSIGNMENT")
assert_not_contains(candidate_provider, [
    "AgentSourceAssignmentRepository",
    "sourceAssignments",
    "SOURCE_SYSTEM_POOL",
    "CAPABILITY_MATCHED_POOL",
    "SOURCE_ASSIGNMENT",
    "CAPABILITY_GRANT",
    "LEGACY_CANDIDATE",
])

candidate_repository = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/routing/GenericCandidateAgentRepository.java"
assert_contains(candidate_repository, "findExplicitFlowAgentIds")
assert_not_contains(candidate_repository, ["findCapabilityMatchedAgentIds"])

jdbc_candidate_repository = "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcGenericCandidateAgentRepository.java"
assert_contains(jdbc_candidate_repository, "from flow_agent_assignments")
assert_not_contains(jdbc_candidate_repository, [
    "agent_source_assignments",
    "agent_action_grants",
    "dispatch_operation_profiles",
    "source_system_dispatch_defaults",
    "findCapabilityMatchedAgentIds",
])

eligibility = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/GenericDispatchEligibilityService.java"
assert_contains(eligibility, "Stage 8 direct-dispatch Agent eligibility evaluator")
assert_contains(eligibility, "AgentProfileEligibilityEvaluator")
assert_contains(eligibility, "CapabilityEligibilityEvaluator")
assert_contains(eligibility, "RuntimeEligibilityEvaluator")
assert_contains(eligibility, "CapacityEligibilityEvaluator")
assert_not_contains(eligibility, [
    "AgentSourceAssignmentRepository",
    "DispatchOperationProfileRepository",
    "ActionGovernanceRepository",
    "SourceCoverageEligibilityEvaluator",
    "OperationEligibilityEvaluator",
    "ActionAuthorizationEvaluator",
    "GovernanceEligibilityEvaluator",
    "sourceAssignment",
    "operationProfile",
    "actionGrant",
])

context = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/DispatchEligibilityShadowContext.java"
assert_contains(context, "getAgentProfile")
assert_not_contains(context, [
    "AgentSourceAssignment",
    "DispatchOperationProfile",
    "AgentActionGrant",
    "sourceAssignment",
    "operationProfile",
    "actionGrant",
])

agent_profile = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/AgentProfileEligibilityEvaluator.java"
assert_contains(agent_profile, "AGENT_PROFILE")
assert_contains(agent_profile, "AGENT_PROFILE_APPROVED")

ui_dashboard = "ai-event-gateway-admin-ui/lib/types/dashboard.ts"
assert_not_contains(ui_dashboard, ["AgentSourceAssignment", "sourceAssignments"])

if errors:
    print("Stage 3 dispatch governance removal verification failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Stage 3 dispatch governance removed from standard path contract verified.")
