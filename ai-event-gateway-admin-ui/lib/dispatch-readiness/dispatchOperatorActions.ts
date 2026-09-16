import type { ParsedDispatchUserFacingError } from "@/lib/dispatch-readiness/dispatchUserFacingError";

export type DispatchOperatorActionTone =
  "primary" | "secondary" | "danger" | "safe" | "warning";
export type DispatchOperatorCommand =
  "triggerRecoveryNow" | "manualRetry" | "escalate" | "deadLetter";

export interface DispatchOperatorAction {
  id: string;
  label: string;
  description?: string;
  href?: string;
  command?: DispatchOperatorCommand;
  tone?: DispatchOperatorActionTone;
  requiresReason?: boolean;
}

export interface DispatchOperatorActionContext {
  taskId?: string;
  agentId?: string;
  reasonCategory?: string;
  runbookRef?: string;
  includeTaskCommands?: boolean;
  includeRunbook?: boolean;
  canManualRetry?: boolean;
  canEscalate?: boolean;
  canDeadLetter?: boolean;
  canTriggerRecoveryNow?: boolean;
}

const DEFAULT_RUNBOOKS: Record<string, string> = {
  MISSING_FLOW_RULE: "runbooks/dispatch/flow-rule-missing",
  MISSING_REQUIRED_CAPABILITY: "runbooks/dispatch/required-capability",
  NO_FLOW_AGENT_ASSIGNMENT: "runbooks/dispatch/flow-agent-assignment",
  AGENT_REQUIRED_CAPABILITY_MISSING: "runbooks/dispatch/required-capability",
  AGENT_OFFLINE: "runbooks/dispatch/agent-runtime",
  DISPATCH_PROFILE_NOT_CONFIGURED: "runbooks/dispatch/flow-rule-missing",
  DISPATCH_TASK_DEFINITION_NOT_FOUND:
    "runbooks/dispatch/flow-rule-missing",
  DISPATCH_PROFILE_POLICY_MISSING: "runbooks/dispatch/flow-rule-missing",
  DISPATCH_PROFILE_CAPABILITY_MISSING: "runbooks/dispatch/required-capability",
  DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL:
    "runbooks/dispatch/capability-contract",
  DISPATCH_AGENT_CAPABILITY_REVOKED: "runbooks/dispatch/capability-contract",
  DISPATCH_RUNTIME_FEATURE_MISSING: "runbooks/dispatch/runtime-feature-trust",
  DISPATCH_RUNTIME_FEATURE_UNTRUSTED: "runbooks/dispatch/runtime-feature-trust",
  DISPATCH_RUNTIME_FEATURE_REVOKED: "runbooks/dispatch/runtime-feature-trust",
  DISPATCH_AGENT_PROFILE_MISSING: "runbooks/dispatch/agent-profile-missing",
  DISPATCH_AGENT_NO_CAPACITY: "runbooks/dispatch/agent-no-capacity",
  DISPATCH_AGENT_NOT_ASSIGNABLE: "runbooks/dispatch/agent-not-assignable",
  DISPATCH_DELAYED_NO_ELIGIBLE_AGENT:
    "runbooks/dispatch/delayed-no-eligible-agent",
  DISPATCH_RECOVERY_EXHAUSTED: "runbooks/dispatch/recovery-exhausted",
  DISPATCH_SCORE_BELOW_THRESHOLD: "runbooks/dispatch/score-below-threshold",
  DISPATCH_NO_AGENT_ONLINE: "runbooks/dispatch/no-agent-online",
  DISPATCH_RECOVERY_SCANNER_FAILED: "runbooks/dispatch/recovery-scanner-failed",
  NO_CANDIDATE: "runbooks/dispatch/no-matching-agent",
    RUNTIME_CAPABILITY_MISSING: "runbooks/dispatch/runtime-capability-missing",
  RUNTIME_BINDING_MISSING: "runbooks/dispatch/runtime-binding-missing",
  RUNTIME_BINDING_ACTIVE: "runbooks/dispatch/runtime-binding-missing",
  P3H_ACTIVE_RUNTIME_BINDING_REQUIRED: "runbooks/dispatch/runtime-binding-missing",
  P3H_RUNTIME_BINDING_NOT_ACTIVE: "runbooks/dispatch/runtime-binding-missing",
  DISPATCH_RULE_MISSING: "runbooks/dispatch/dispatch-rule-missing",
  ASSIGNMENT_NOT_CREATED: "runbooks/dispatch/assignment-not-created",
  DISPATCH_REQUEST_NOT_CREATED:
    "runbooks/dispatch/dispatch-request-not-created",
  EFFECTIVE_CAPABILITY_NOT_RESOLVED:
    "runbooks/dispatch/effective-capability-contract",
  DISPATCH_ELIGIBILITY_WAITING: "runbooks/dispatch/eligibility-waiting",
};

export function dispatchRunbookRefForCode(
  code?: string,
  explicitRef?: string,
): string | undefined {
  return explicitRef || (code ? DEFAULT_RUNBOOKS[code] : undefined);
}

function taskLink(taskId?: string): string {
  return taskId ? `/tasks/${encodeURIComponent(taskId)}` : "/tasks";
}

function agentLink(agentId?: string): string {
  return agentId ? `/agents/${encodeURIComponent(agentId)}` : "/agents";
}

function uniqueActions(
  actions: DispatchOperatorAction[],
): DispatchOperatorAction[] {
  const seen = new Set<string>();
  return actions.filter((action) => {
    const key = `${action.id}:${action.href ?? ""}:${action.command ?? ""}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function buildDispatchOperatorActions(
  parsed?: Pick<ParsedDispatchUserFacingError, "code" | "runbookRef"> | null,
  context: DispatchOperatorActionContext = {},
): DispatchOperatorAction[] {
  const code = parsed?.code;
  if (!code) return [];

  const runbookRef = dispatchRunbookRefForCode(
    code,
    parsed?.runbookRef ?? context.runbookRef,
  );
  const actions: DispatchOperatorAction[] = [];

  switch (code) {
    case "NO_CANDIDATE":
      actions.push(
        {
          id: "open-dispatch-readiness",
          label: "Open Dispatch Flows",
          href: "/dispatch-flows",
          tone: "primary",
          description: "Review the matched Source Flow, target Agent Pool, Pool members, and candidate blockers.",
        },
        {
          id: "open-agents",
          label: "Open Agents",
          href: "/agents",
          tone: "secondary",
          description:
            "Compare Agent approval, Pool membership, runtime connection, capacity, credential, and backoff.",
        },
      );
      break;

    case "RUNTIME_BINDING_MISSING":
    case "RUNTIME_BINDING_ACTIVE":
    case "P3H_ACTIVE_RUNTIME_BINDING_REQUIRED":
    case "P3H_RUNTIME_BINDING_NOT_ACTIVE":
      actions.push(
        {
          id: "open-agent-runtime-binding",
          label: "Open Agent Runtime Binding",
          href: context.agentId ? `${agentLink(context.agentId)}#connection` : "/agents",
          tone: "primary",
          description: "Create or activate the runtime binding from Agent Detail > Connection.",
        },
        {
          id: "open-runtime-resources",
          label: "Open Runtime Resources",
          href: "/agents/runtime",
          tone: "secondary",
          description: "Review runtime resources and binding status for this gateway runtime.",
        },
      );
      break;
    case "RUNTIME_CAPABILITY_MISSING":
      actions.push(
        {
          id: "open-agent-capabilities",
          label: "Open Agent Capabilities",
          href: context.agentId
            ? `${agentLink(context.agentId)}#capabilities`
            : "/agents",
          tone: "primary",
          description:
            "Verify that this Agent has an approved Canonical Capability matching the Task requirement.",
        },
        {
          id: "open-agent-connection",
          label: "Open Agent Connection",
          href: context.agentId
            ? `${agentLink(context.agentId)}#connection`
            : "/agents",
          tone: "secondary",
          description:
            "Confirm Agent ID, credential, gateway URL, heartbeat, and capacity.",
        },
      );
      break;
    case "DISPATCH_RULE_MISSING":
      actions.push(
        {
          id: "open-dispatch-flows",
          label: "Open Dispatch Setup",
          href: "/dispatch-flows",
          tone: "primary",
          description:
            "Create or activate the Source Flow, then confirm its default or rule-target Agent Pool.",
        },
        {
          id: "open-flow-agents",
          label: "Open Flow Agents",
          href: "/dispatch-flows?panel=agents",
          tone: "secondary",
          description:
            "Confirm that the matched Flow targets a Pool containing an approved and eligible Agent.",
        },
      );
      break;
    case "ASSIGNMENT_NOT_CREATED":
    case "DISPATCH_REQUEST_NOT_CREATED":
      actions.push(
        {
          id: "open-task-detail",
          label: "Open Task Detail",
          href: taskLink(context.taskId),
          tone: "primary",
          description:
            "Review routing decision, assignment evidence, and dispatch request creation.",
        },
        {
          id: "open-dispatch-monitoring",
          label: "Open Dispatch Monitoring",
          href: "/enforce-observability",
          tone: "secondary",
          description:
            "Check routing audit and production dispatch monitoring.",
        },
      );
      break;
    case "EFFECTIVE_CAPABILITY_NOT_RESOLVED":
      actions.push(
        {
          id: "open-dispatch-flows",
          label: "Open Dispatch Flows",
          href: "/dispatch-flows",
          tone: "primary",
          description:
            " Flow Rule  Required Capability  capability fallback.",
        },
        {
          id: "open-dispatch-readiness",
          label: "Run Flow Dry-run",
          href: "/dispatch-flows?panel=dry-run",
          tone: "secondary",
          description:
            "use Flow Rule dry-run  Required Capability,Flow Agent assignment and Capability assignment.",
        },
      );
      break;
    case "DISPATCH_ELIGIBILITY_WAITING":
      actions.push(
        {
          id: "open-task-detail",
          label: "Open Task Detail",
          href: taskLink(context.taskId),
          tone: "primary",
          description: "Use the evidence panel to fix the first missing gate.",
        },
        {
          id: "open-dispatch-readiness",
          label: "Open Dispatch Flows",
          href: "/dispatch-flows",
          tone: "secondary",
          description: "Run an isolated readiness preview.",
        },
      );
      break;
    case "MISSING_FLOW_RULE":
    case "DISPATCH_PROFILE_NOT_CONFIGURED":
    case "DISPATCH_TASK_DEFINITION_NOT_FOUND":
      actions.push(
        {
          id: "open-dispatch-flows",
          label: "openDispatch",
          href: "/dispatch-flows",
          tone: "primary",
          description: "Create Source Flow, and confirmdefault or ruleTarget Agent Pool.",
        },
        {
          id: "open-task-detail",
          label: "Open Flow Repair Center",
          href: taskLink(context.taskId),
          tone: "secondary",
          description: "return to Task Detail  Flow View details blocking gate.",
        },
      );
      break;
    case "MISSING_REQUIRED_CAPABILITY":
      actions.push(
        {
          id: "open-flow-capabilities",
          label: "Review Current Dispatch Setup",
          href: "/dispatch-flows?panel=capabilities",
          tone: "primary",
          description: " Capability blocker;Current dispatchVerify Source Flow,Target Agent Pool,Pool Member Agent and Runtime Eligibility.",
        },
      );
      break;
    case "NO_FLOW_AGENT_ASSIGNMENT":
      actions.push(
        {
          id: "open-flow-agents",
          label: "Configure Agent Pool Members",
          href: "/dispatch-flows?panel=agents",
          tone: "primary",
          description: "in matched Source Flow Target Agent Pool Enable Agent.",
        },
      );
      break;
    case "AGENT_REQUIRED_CAPABILITY_MISSING":
      actions.push(
        {
          id: "review-current-pool-members",
          label: "Review Agent Pool Members",
          href: "/dispatch-flows?panel=pools",
          tone: "primary",
          description: " Capability blocker; VerifyTarget Agent Pool hasEnableMembers.",
        },
        {
          id: "open-agent",
          label: context.agentId ? "Open Agent" : "Open Agents",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: "Review Agent governance status, Agent Pool membership, and runtime eligibility.",
        },
      );
      break;
    case "DISPATCH_PROFILE_POLICY_MISSING":
      actions.push(
        {
          id: "open-dispatch-flows",
          label: "Open Dispatch Flows",
          href: "/dispatch-flows",
          tone: "primary",
          description: "Dispatch information Source Flow,Rule override/Default Agent Pool and Pool Member Agent ",
        },
        {
          id: "open-flow-dry-run",
          label: "Run Flow Dry-run",
          href: "/dispatch-flows?panel=dry-run",
          tone: "secondary",
          description: " Flow Rule Dispatch information",
        },
      );
      break;
    case "DISPATCH_PROFILE_CAPABILITY_MISSING":
    case "DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL":
    case "DISPATCH_AGENT_CAPABILITY_REVOKED":
      actions.push(
        {
          id: "review-current-dispatch-setup",
          label: "Review Current Dispatch Setup",
          href: "/dispatch-flows",
          tone: "primary",
          description:
            "Capability Review the configuration and try again. Source Flow,Agent Pool,Pool Member Agent and Runtime Eligibility.",
        },
        {
          id: "open-agent-capabilities",
          label: context.agentId ? "Open Agent Detail" : "Open Agents",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: " Agent managementStatus,Agent Pool membership and Runtime.",
        },
      );
      break;
    case "DISPATCH_RUNTIME_FEATURE_MISSING":
    case "DISPATCH_RUNTIME_FEATURE_UNTRUSTED":
    case "DISPATCH_RUNTIME_FEATURE_REVOKED":
      actions.push(
        {
          id: "open-runtime-features",
          label: "Open Runtime Features",
          href: "/settings/runtime-features",
          tone: "primary",
          description: " Runtime Feature  dispatch.",
        },
        {
          id: "open-agent-runtime-trust",
          label: context.agentId ? "Open Agent Runtime Trust" : "Open Agents",
          href: agentLink(context.agentId),
          tone: "secondary",
          description:
            " observation  TRUSTED revoke/suspend  feature.",
        },
      );
      break;
    case "DISPATCH_AGENT_PROFILE_MISSING":
      actions.push(
        {
          id: "open-agent-management",
          label: "Open Agent Detail",
          href: agentLink(context.agentId),
          tone: "primary",
          description:
            " Agent enabled, and Credential and Runtime StatusHealthy.",
        },
        {
          id: "open-dispatch-flows",
          label: "Open Dispatch Flows",
          href: "/dispatch-flows?panel=agents",
          tone: "secondary",
          description: "confirm Agent  matched Source Flow Target Agent Pool.",
        },
      );
      break;
    case "DISPATCH_AGENT_NO_CAPACITY":
      actions.push(
        {
          id: "open-agent-runtime-load",
          label: "Open Agent Runtime Load",
          href: "/agents/runtime",
          tone: "primary",
          description:
            " currentTaskCount,reservedTaskCount,maxConcurrentTasks and runtime load.",
        },
        {
          id: "open-agent-detail",
          label: context.agentId
            ? "Open Agent Detail"
            : "Open Agent Operations",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: " capacity  Agent.",
        },
      );
      break;
    case "DISPATCH_AGENT_NOT_ASSIGNABLE":
      actions.push(
        {
          id: "open-agent-runtime-status",
          label: "Open Agent Runtime Status",
          href: "/agents/runtime",
          tone: "primary",
          description:
            " offline,draining,backoff,credential,enabled Status.",
        },
        {
          id: "open-agent-governance",
          label: context.agentId
            ? "Open Agent Governance"
            : "Open Agent Operations",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: "confirm Agent ",
        },
      );
      break;
    case "DISPATCH_DELAYED_NO_ELIGIBLE_AGENT":
      actions.push({
        id: "open-failure-queue",
        label: "Open Failure Queue",
        href: "/tasks/failure-queue",
        tone: "primary",
        description: "View details delayed dispatch / recovery wait Task.",
      });
      if (
        context.includeTaskCommands &&
        (context.canTriggerRecoveryNow ?? true)
      ) {
        actions.push({
          id: "trigger-recovery-now",
          label: "Trigger Recovery Now",
          command: "triggerRecoveryNow",
          tone: "safe",
          requiresReason: true,
          description: " Flow Agent / Capability assignment / capacity  recovery.",
        });
      } else if (
        context.includeTaskCommands &&
        (context.canManualRetry ?? true)
      ) {
        actions.push({
          id: "manual-retry",
          label: "Manual Retry",
          command: "manualRetry",
          tone: "safe",
          requiresReason: true,
          description: "in Failure Queue  Core Dispatch information",
        });
      }
      break;
    case "DISPATCH_RECOVERY_EXHAUSTED":
      actions.push({
        id: "open-task-timeline",
        label: "Open Task Timeline",
        href: taskLink(context.taskId),
        tone: "primary",
        description: " retry history and routing decision.",
      });
      if (context.includeTaskCommands && (context.canEscalate ?? true)) {
        actions.push({
          id: "escalate",
          label: "Escalate",
          command: "escalate",
          tone: "warning",
          requiresReason: true,
          description: "Task detailsStatus.",
        });
      }
      if (context.includeTaskCommands && (context.canDeadLetter ?? true)) {
        actions.push({
          id: "dead-letter",
          label: "Move to DLQ",
          command: "deadLetter",
          tone: "danger",
          requiresReason: true,
          description: " dead-letter.",
        });
      }
      break;
    case "DISPATCH_SCORE_BELOW_THRESHOLD":
      actions.push(
        {
          id: "open-task-routing",
          label: "Open Routing Explainability",
          href: taskLink(context.taskId),
          tone: "primary",
          description: "view candidate score breakdown",
        },
        {
          id: "open-dispatch-readiness",
          label: "Review Dispatch Flow",
          href: "/dispatch-flows",
          tone: "secondary",
          description: "Dispatch information Agent ",
        },
      );
      break;
    case "DISPATCH_NO_AGENT_ONLINE":
      actions.push(
        {
          id: "open-agent-runtime",
          label: "Open Agent Runtime",
          href: "/agents/runtime",
          tone: "primary",
          description: " Agent online  Core ",
        },
        {
          id: "open-agent-enrollments",
          label: "Open Agent Enrollments",
          href: "/agent-enrollments",
          tone: "secondary",
          description: " Agent.",
        },
      );
      break;
    case "DISPATCH_RECOVERY_SCANNER_FAILED":
      actions.push(
        {
          id: "open-cluster-diagnostics",
          label: "Open Cluster Diagnostics",
          href: "/cluster/diagnostics",
          tone: "primary",
          description: "confirm Core recovery scanner Status.",
        },
        {
          id: "open-failure-queue",
          label: "Open Failure Queue",
          href: "/tasks/failure-queue",
          tone: "secondary",
          description: "View details recovery ",
        },
      );
      break;
    default:
      actions.push({
        id: "open-task",
        label: "Open Task",
        href: taskLink(context.taskId),
        tone: "secondary",
        description: "view Task detail and dispatch diagnostics.",
      });
      break;
  }

  if (context.includeRunbook !== false && runbookRef) {
    actions.push({
      id: "runbook-ref",
      label: `Runbook: ${runbookRef}`,
      tone: "secondary",
      description:
        "thisVersion runbook reference Runbook page",
    });
  }

  return uniqueActions(actions);
}
