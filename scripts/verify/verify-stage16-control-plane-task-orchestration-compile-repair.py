#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors = []

def require(path, text=None):
    p = ROOT / path
    if not p.exists():
        errors.append(f"missing file: {path}")
        return ""
    data = p.read_text(errors="ignore")
    if text and text not in data:
        errors.append(f"{path} must contain {text!r}")
    return data

trace = require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/contract/DispatchContractTraceService.java")
if "mergeCapabilities(body.getRequiredCapabilities(),\n                task == null ? List.of() : task.getRequiredCapabilities(),\n                List.of()," not in trace:
    errors.append("DispatchContractTraceService must pass an explicit third List to mergeCapabilities")

flow = require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java")
if "RowMapper<DispatchFlowAgentOptionView> AGENT_OPTION_ROW_MAPPER" not in flow:
    errors.append("DispatchFlowManagementService must define AGENT_OPTION_ROW_MAPPER")
if "option.setSelectable(enabled && approved && runtimeConnected && heartbeatHealthy && capacityAvailable)" not in flow:
    errors.append("Dispatch Flow agent options must be backend-authoritative and include selectability")

evidence = require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/timeline/TaskDispatchEvidenceService.java")
for retired in ["task.getAssignedAgentId()", "No Service Scope", "Assignment Profile", "Source Coverage", "Task Scope"]:
    if retired in evidence:
        errors.append(f"TaskDispatchEvidenceService still contains retired token: {retired}")
if "String assignedAgentId = taskAssignedAgent(task);" not in evidence:
    errors.append("TaskDispatchEvidenceService must resolve assigned Agent from routing decision evidence")

cutover = require("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/cutover/DispatchCutoverService.java")
for retired in ["setOfferFirstRequired", "setDirectAssignmentRecoveryOnly"]:
    if retired in cutover:
        errors.append(f"DispatchCutoverService still references retired retired two-step dispatch setter: {retired}")

task_decision = require("ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java")
for expected in ["(FlowRuleRoutingService) null", "(RoutingProperties) null"]:
    if expected not in task_decision:
        errors.append(f"TaskDecisionService constructor calls must disambiguate nulls with {expected}")

setup_test = require("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/AgentSetupControllerMockMvcTest.java")
if "setTaskScope" in setup_test:
    errors.append("AgentSetupControllerMockMvcTest must not set taskScope")

obsolete_test = ROOT / "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/assignment/DispatchEligibilityPolicyFlowRuleTest.java"
if obsolete_test.exists():
    errors.append("DispatchEligibilityPolicyFlowRuleTest must be removed with retired DispatchEligibilityPolicy")

for path in ["ai-event-gateway-core", "ai-event-gateway-admin-ui"]:
    for p in (ROOT / path).rglob("*"):
        if p.is_file() and p.suffix in {".java", ".xml", ".ts", ".tsx"}:
            data = p.read_text(errors="ignore")
            if "DispatchEligibilityPolicy" in data:
                errors.append(f"retired DispatchEligibilityPolicy reference remains in {p.relative_to(ROOT)}")

if errors:
    print("Phase 16 control-plane/task-orchestration compile repair verification failed:")
    for e in errors:
        print(f" - {e}")
    sys.exit(1)
print("Phase 16 control-plane/task-orchestration compile repair contract verified.")
