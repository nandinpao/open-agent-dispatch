#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

def must_exist(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        errors.append(f"Missing required file: {path}")
        return ""
    return p.read_text(errors="ignore")

def must_not_exist(path: str):
    if (ROOT / path).exists():
        errors.append(f"Legacy standard UI artifact still exists: {path}")

def require(path: str, tokens: list[str]):
    text = must_exist(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path} missing token: {token}")

def forbid(path: str, tokens: list[str]):
    p = ROOT / path
    if not p.exists():
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token in text:
            errors.append(f"{path} still contains forbidden token: {token}")

# Removed legacy standard UI / workflow components.
for path in [
    "ai-event-gateway-admin-ui/app/settings/capabilities",
    "ai-event-gateway-admin-ui/components/capabilities",
    "ai-event-gateway-admin-ui/components/skills",
    "ai-event-gateway-admin-ui/components/assignment-profiles",
    "ai-event-gateway-admin-ui/components/dispatch-readiness",
    "ai-event-gateway-admin-ui/components/dispatch-recipes",
    "ai-event-gateway-admin-ui/components/dispatch-simulator",
    "ai-event-gateway-admin-ui/components/dispatch-task-definitions",
    "ai-event-gateway-admin-ui/components/migration-readiness",
    "ai-event-gateway-admin-ui/components/supply-profiles",
    "ai-event-gateway-admin-ui/components/tasks/DispatchTroubleshootingWizard.tsx",
]:
    must_not_exist(path)

require("docs/PHASE5_CAPABILITY_TASK_SCOPE_CLEANUP.md", [
    "Capability = an optional special technical ability owned by an Agent",
    "Dispatch Flow Rule has no Required Capability",
    "do not check Agent Capability",
])

require("ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx", [
    "Required Capability 只由本頁",
    "requiredCapabilities",
    "requiredSkills",
])

require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java", [
    "requestedSkill is no longer an operator-owned dispatch selector",
    "Compatibility projection only: operators cannot set requestedSkill",
    "setCapabilityCount",
])

require("ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java", [
    "incoming task.requestedSkill is no longer a selector",
    "String requestedSkill = null;",
    "boolean hasRequestedSkill = false;",
])

require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessService.java", [
    "REQUIRED_CAPABILITY_OPTIONAL",
    "No Required Capability is configured; Agent capability grant is not required.",
    "requiresCapability",
])

# Standard UI should not direct users back to removed Task Scope/readiness/legacy policy pages.
for path in [
    "ai-event-gateway-admin-ui/components/agents/AgentOnboardingPanel.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx",
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "ai-event-gateway-admin-ui/app/tasks/page.tsx",
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/dispatchOperatorActions.ts",
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/dispatchUserFacingError.ts",
]:
    forbid(path, [
        "Task Scope",
        "task scope",
        "taskScope",
        "/settings/dispatch-task-definitions",
        "/testing/dispatch-readiness",
        "/dispatch-policies",
        "/supply-profiles",
        "NO_REQUESTED_SKILL",
        "AGENT_SKILL_GRANT_MISSING",
        "requestedSkill grant",
    ])

# Agent setup response must no longer expose Task Scope as a standard contract.
forbid("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupResponse.java", [
    "taskScope",
    "setTaskScope",
])
forbid("ai-event-gateway-admin-ui/lib/types/core.ts", [
    "taskScope?:",
])

if errors:
    for error in errors:
        print(f"ERROR: {error}")
    raise SystemExit(1)

print("Stage 5 capability/task-scope cleanup contract verified.")
