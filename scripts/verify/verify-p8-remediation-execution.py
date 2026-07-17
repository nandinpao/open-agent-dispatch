#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def require(path, needle=None, label=None, invert=False):
    p = ROOT / path
    ok = p.exists()
    if ok and needle is not None:
        text = p.read_text(encoding="utf-8")
        ok = needle in text
    if invert:
        ok = not ok
    checks.append((label or str(path), ok))

controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java"
policy = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowExecutionPolicy.java"
status = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java"
security_enum = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentSecurityEventType.java"
ui_panel = "ai-event-gateway-admin-ui/components/agents/AgentRemediationPanel.tsx"
ui_types = "ai-event-gateway-admin-ui/lib/types/core.ts"

require(policy, "EXECUTION_MODE_LEASED_ACTION_LEVEL", "Execution policy owns current guarded execution mode")
require(policy, "P10_WORKFLOW_LEASED_ACTION_LEVEL_EXECUTION", "Execution policy preserves persisted execution mode token")
require(controller, "executeWorkflowActions", "Controller has workflow action execution dispatcher")
require(controller, "agentDirectoryService.clearRuntimeBackoff", "Controller executes clear runtime backoff")
require(controller, "agentGovernanceService.suspendAgent", "Controller executes suspend agent")
require(controller, "skillRegistryService.replaceApprovedSkills", "Controller executes approved skill sync")
require(controller, "runtimeDisconnectClient.disconnectAgent", "Controller executes runtime disconnect through Core runtime client")
require(controller, "EXECUTION_FAILED", "Controller records execution failure history")
require(controller, "return failedAttempt", "Execution failure keeps workflow APPROVED for retry")
require(security_enum, "AGENT_REMEDIATION_WORKFLOW_EXECUTION_FAILED", "Security event enum has execution failed event")
require(status, "agentRemediationWorkflowExecutionIntegrationEnabled", "Core status exposes execution integration")
require(status, "agentRemediationExecutableActions", "Core status lists executable remediation actions")
require(ui_types, "dryRun?: boolean", "Admin UI decision request supports dryRun")
require(ui_panel, "Execute with Lease", "Admin UI exposes guarded execution button")
require(ui_panel, "dry-run", "Admin UI prompts dry-run vs real execution")

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
if failed:
    print("\nRemediation workflow execution verification failed:")
    for name in failed:
        print(" - " + name)
    sys.exit(1)
print("\nRemediation workflow execution verification passed.")
