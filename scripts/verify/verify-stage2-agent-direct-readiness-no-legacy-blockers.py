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

def assert_contains(rel: str, token: str) -> None:
    text = read(rel)
    if token not in text:
        errors.append(f"{rel} must contain {token!r}")

def assert_not_contains(rel: str, tokens: list[str]) -> None:
    text = read(rel)
    for token in tokens:
        if token in text:
            errors.append(f"{rel} must not contain retired standard-flow blocker token {token!r}")

def assert_absent(rel: str) -> None:
    if (ROOT / rel).exists():
        errors.append(f"Retired standard Agent UI component must be removed: {rel}")

retired_tokens = [
    "Service Scope",
    "service scope",
    "Source Coverage",
    "source coverage",
    "Task Scope",
    "task scope",
    "Assignment Profile",
    "assignment profile",
    "No ACTIVE and APPROVED Agent Source Coverage",
    "SERVICE_SCOPE_ACTIVE",
    "DISPATCH_RULE_ACTIVE",
    "CAPABILITIES_ASSIGNED",
    "Assign Task Scope",
    "Assign Source Coverage",
]

# Old Agent detail implementation physically removed from the standard code path.
for rel in [
    "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentRuntimeDiagnosticsPanel.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentSkillRegistryPanel.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentApprovedCapabilityPolicyPanel.tsx",
]:
    assert_absent(rel)

# Agent list and detail now use Dispatch Flow usage, not parallel scope/profile readiness.
assert_contains("ai-event-gateway-admin-ui/components/agents/AgentTable.tsx", "activeDispatchFlowCount")
assert_contains("ai-event-gateway-admin-ui/components/agents/AgentTable.tsx", "dispatchUsageLabel")
assert_not_contains("ai-event-gateway-admin-ui/components/agents/AgentTable.tsx", retired_tokens)

assert_contains("ai-event-gateway-admin-ui/hooks/useAgentGovernanceList.ts", "enrichRowsForDirectFlowUsage")
assert_contains("ai-event-gateway-admin-ui/hooks/useAgentGovernanceList.ts", "coreAdminApi.getDispatchFlows")
assert_not_contains("ai-event-gateway-admin-ui/hooks/useAgentGovernanceList.ts", [
    "dispatchGovernanceApi",
    "sourceAssignments(",
    "getAgentQualifications",
    "getAgentCertifications",
    "getAgentDispatchEligibility",
])

assert_contains("ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx", "TwoLayerReadinessPanel")
assert_contains("ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx", "Dispatch Flow")
assert_contains("ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx", "NON_DISPATCH_TASK_ALIAS_CAPABILITIES")
assert_not_contains("ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx", retired_tokens + ["Legacy profile", "setup-readiness"])

assert_not_contains("ai-event-gateway-admin-ui/hooks/useAgentDetail.ts", [
    "dispatchGovernanceApi",
    "getAssignmentProfiles",
    "getAgentQualifications",
    "getAgentCertifications",
    "assignAgentQualification",
    "runAgentCertification",
])

# Agent setup readiness no longer creates or requires backend service scopes or dispatch rule defaults.
assert_contains("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java", "OPTIONAL_CAPABILITIES_READY")
assert_not_contains("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java", retired_tokens + [
    "SupplyProfile",
    "findSupplyProfilesByAgent",
    "upsertSupplyProfile",
])

# Runtime eligibility standard path only uses Agent profile/credential/runtime binding/capacity and optional Flow capability.
eligibility = read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java")
for token in [
    "evaluateStandardFlowRequirementResolution",
    "DISPATCH_FLOW_DIRECT",
    "DISPATCH_REQUIRED_CAPABILITY_MISSING",
    "agent_direct_dispatch_pass",
]:
    if token not in eligibility:
        errors.append(f"DispatchEligibilityService.java must contain {token!r}")
legacy_runtime_tokens = retired_tokens + [
    "AgentAssignmentProfile",
    "DispatchTaskDefinition",
    "AgentQualification",
    "AgentQualificationStatus",
    "AssignmentProfileCapabilityBinding",
    "AgentAssignmentProfilePolicyBinding",
    "PolicyScopeCanonicalContract",
    "CapabilityTaskTypeCanonicalContract",
    "legacyCompatibility",
    "PROFILE_POLICY_BINDING_ACTIVE",
    "PROFILE_CAPABILITY_BINDING_ACTIVE",
]
for token in legacy_runtime_tokens:
    if token in eligibility:
        # result.setQualifications(List.of()) is a response compatibility field, not an eligibility dependency.
        if token == "Qualification" and "setQualifications" in eligibility:
            continue
        errors.append(f"DispatchEligibilityService.java must not contain retired runtime blocker token {token!r}")

# Task repair and task UI must point operators back to Dispatch Flow / Agent / Capability, not retired contract repair pages.
assert_contains("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/timeline/TaskDispatchEvidenceService.java", "REVIEW_DISPATCH_FLOW")
assert_not_contains("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/timeline/TaskDispatchEvidenceService.java", retired_tokens)
assert_not_contains("ai-event-gateway-admin-ui/hooks/useTaskDetail.ts", retired_tokens + ["approveAgentQualification"])

if errors:
    print("Stage 2 agent direct readiness verification failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Stage 2 agent direct readiness without legacy blockers contract verified.")
