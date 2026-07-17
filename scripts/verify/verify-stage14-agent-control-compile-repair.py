#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"missing required file: {path}")
    return p.read_text(encoding="utf-8")

checks = []

agent_directory = read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/AgentDirectoryService.java")
checks.append(("runtime binding observation uses current signature", "ensureActiveRuntimeBindingForRuntimeObservation(\n                    null,\n                    agentId," in agent_directory))
checks.append(("runtime observation no longer passes capacity int into String parameter", "Math.max(1, agent.getMaxConcurrentTasks())" not in agent_directory))

assignment = read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java")
for forbidden in ["getCapabilityCategory(", "setCapabilityCategory(", "setCapabilityCode(request", "setTouched("]:
    checks.append((f"AgentAssignmentService removed stale {forbidden}", forbidden not in assignment))
checks.append(("AgentAssignmentService maps catalog category", "setCategory(firstNonBlank(request.getCategory(), \"GENERAL\"))" in assignment))
checks.append(("bootstrap readiness uses createdOrUpdated", "setCreatedOrUpdated(List.of(\"DISPATCH_FLOW_DIRECT_ONLY\"))" in assignment))

eligibility = read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java")
checks.append(("Agent eligibility no longer sets deleted evidence field", "setEligibilityEvidence" not in eligibility))
checks.append(("metadata capability helper present", "private boolean isMetadataCapability" in eligibility))
checks.append(("skill version helper present", "private boolean isSkillVersionHint" in eligibility))

v2 = ROOT / "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityServiceV2.java"
checks.append(("legacy DispatchEligibilityServiceV2 removed", not v2.exists()))

controller = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchEligibilityController.java")
checks.append(("eligible-agents-v2 endpoint removed", "eligible-agents-v2" not in controller and "DispatchEligibilityServiceV2" not in controller))

setup = read("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java")
for forbidden in ["setTaskScope", "supplyProfiles.size()", "dispatchPolicies.size()"]:
    checks.append((f"AgentSetupService removed stale {forbidden}", forbidden not in setup))

assignment_controller = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java")
legacy_calls = [
    "searchSupplyProfiles", "getSupplyProfileQualitySnapshot", "findAgentQualifications", "searchProfiles",
    "upsertProfile", "AssignmentProfile", "SupplyProfile", "AgentQualification"
]
for forbidden in legacy_calls:
    checks.append((f"AgentAssignmentController no longer calls/imports {forbidden}", forbidden not in assignment_controller))

# Verify every service.<method>(...) call from the clean controller exists in AgentAssignmentService.
service_methods = set(re.findall(r"\bservice\.(\w+)\s*\(", assignment_controller))
public_methods = set(re.findall(r"\n\s*(?:@Transactional\s*)?public\s+[^;{=]+\s+(\w+)\s*\(", assignment))
missing = sorted(service_methods - public_methods)
checks.append(("AgentAssignmentController only calls implemented AgentAssignmentService methods", not missing))

failed = [name for name, ok in checks if not ok]
if failed:
    print("Stage 14 agent-control compile repair verification failed:")
    for name in failed:
        print(f" - {name}")
    if missing:
        print("Missing service methods:", ", ".join(missing))
    sys.exit(1)

print("Stage 14 agent-control compile repair contract verified.")
