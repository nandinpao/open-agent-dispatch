#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[2]
svc = root / "agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java"
text = svc.read_text()
required = [
    "DispatchPolicyScope",
    "DispatchPolicyRequiredCapability",
    "canonicalizePolicyScopeRequiredCapabilityToSourceContract",
    "POLICY_SCOPE_REQUIRED_CAPABILITY_CANONICAL_CONTRACT",
    "enrichRequirementProfileWithPolicyFallback",
    "findDispatchPolicyScopes",
    "findDispatchPolicyRequiredCapabilities",
]
missing = [x for x in required if x not in text]
if missing:
    raise SystemExit(f"Missing P33 markers: {missing}")
if text.count("private Optional<PolicyScopeCanonicalContract> canonicalizePolicyScopeRequiredCapabilityToSourceContract") != 1:
    raise SystemExit("P33 canonicalization method count mismatch")
if "usedPolicyScopeCapabilityFallback ? \"POLICY_SCOPE_REQUIRED_CAPABILITY_CANONICAL_CONTRACT\"" not in text:
    raise SystemExit("P33 requirementSource branch missing")

assignment_service = root / "agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
assignment_text = assignment_service.read_text()
service_required = [
    "public List<DispatchPolicyScope> findDispatchPolicyScopes",
    "repository.findDispatchPolicyScopes",
    "public List<DispatchPolicyRequiredCapability> findDispatchPolicyRequiredCapabilities",
    "repository.findDispatchPolicyRequiredCapabilities",
]
service_missing = [x for x in service_required if x not in assignment_text]
if service_missing:
    raise SystemExit(f"Missing P33 AgentAssignmentService pass-through markers: {service_missing}")

print("P33 policy-scope canonical requirements verification passed.")
