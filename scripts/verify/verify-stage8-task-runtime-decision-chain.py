#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")

def require(rel: str, token: str) -> None:
    if token not in read(rel):
        errors.append(f"{rel}: missing {token!r}")

def forbid(rel: str, token: str) -> None:
    if token in read(rel):
        errors.append(f"{rel}: forbidden {token!r}")

service = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/timeline/TaskDispatchEvidenceService.java"
controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskDispatchEvidenceController.java"
evidence_panel = "ai-event-gateway-admin-ui/components/tasks/TaskDispatchEvidenceTimelinePanel.tsx"
trace_panel = "ai-event-gateway-admin-ui/components/tasks/TaskDispatchContractTracePanel.tsx"
lifecycle = "ai-event-gateway-admin-ui/lib/tasks/dispatchLifecycle.ts"

def forbid_content(rel: str, tokens: list[str]) -> None:
    text = read(rel)
    for token in tokens:
        if token in text:
            errors.append(f"{rel}: forbidden content token {token!r}")

# Backend evidence must expose the single Runtime Decision Chain and the eight standard blockers.
for token in [
    "FLOW_RULE_TO_DISPATCH_REQUEST_TO_NETTY_ACK_RESULT",
    "NO_MATCHING_FLOW",
    "NO_MATCHING_RULE",
    "NO_FLOW_AGENT",
    "MISSING_REQUIRED_CAPABILITY",
    "AGENT_OFFLINE",
    "AGENT_CAPACITY_FULL",
    "DISPATCH_DELIVERY_FAILED",
    "RESULT_TIMEOUT",
    "EVENT_ACCEPTED",
    "TASK_CREATED",
    "FLOW_MATCHED",
    "RULE_MATCHED",
    "CANDIDATES_FOUND",
    "ASSIGNMENT_CREATED",
    "DISPATCH_REQUEST_CREATED",
]:
    require(service, token)

# Task evidence controller must not offer task-level readiness or repair-contract endpoints.
for token in ["dispatch-contract-readiness", "repair-dispatch-contract", "runDispatchContractReadiness", "repairDispatchContract"]:
    forbid(controller, token)

# Standard Task panels must not display old-model blockers or independent readiness repair buttons.
for rel in [evidence_panel, trace_panel]:
    for token in [
        "Run Task-level Readiness",
        "Run Readiness",
        "Repair Dispatch Contract",
        "Service Scope",
        "Assignment Profile",
        "Source Coverage",
        "Task Scope",
        "Operation Profile",
        "Qualification",
        "dispatch-contract-readiness",
        "repair-dispatch-contract",
    ]:
        forbid(rel, token)
    for token in ["Runtime Decision Chain", "NO_MATCHING_FLOW", "NO_MATCHING_RULE", "NO_FLOW_AGENT", "MISSING_REQUIRED_CAPABILITY", "AGENT_OFFLINE", "AGENT_CAPACITY_FULL", "DISPATCH_DELIVERY_FAILED", "RESULT_TIMEOUT"]:
        require(rel, token)

# Diagnosis code union should use Phase 8 standard blocker codes.
for token in ["NO_MATCHING_FLOW", "NO_MATCHING_RULE", "NO_FLOW_AGENT", "MISSING_REQUIRED_CAPABILITY", "AGENT_OFFLINE", "AGENT_CAPACITY_FULL", "DISPATCH_DELIVERY_FAILED", "RESULT_TIMEOUT"]:
    require(lifecycle, token)
for token in ["NO_ACTIVE_FLOW_RULE", "REQUIRED_CAPABILITY_MISSING", "AGENT_NO_CAPACITY", "NO_ELIGIBLE_AGENT", "CALLBACK_FAILED"]:
    forbid(lifecycle, token)

# Standard action panel must be present and not the old recovery governance panel.
task_detail = "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
require(task_detail, "Standard Dispatch Actions")
for token in ["Recovery Governance Actions", "Run Task-level Readiness", "Repair Dispatch Contract", "Service Scope", "Assignment Profile", "Source Coverage", "Task Scope", "Operation Profile", "Qualification"]:
    forbid(task_detail, token)

if errors:
    print("Phase 8 Task Runtime Decision Chain verification failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print("Phase 8 Task Runtime Decision Chain contract verified.")
