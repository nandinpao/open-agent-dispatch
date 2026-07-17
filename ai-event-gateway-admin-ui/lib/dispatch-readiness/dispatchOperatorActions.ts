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
          description: "Preview effective capabilities and candidate blockers.",
        },
        {
          id: "open-agents",
          label: "Open Agents",
          href: "/agents",
          tone: "secondary",
          description:
            "Compare Core approval, runtime report, Dispatch Flow Agent selection, and capacity.",
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
          href: "/settings/runtime-resources",
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
            "Verify Admin UI/Core capability approval. Runtime capability observations are diagnostic only.",
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
          label: "Create / Fix Dispatch Flow Rule",
          href: "/dispatch-flows",
          tone: "primary",
          description:
            "建立或啟用 matched Dispatch Flow 的 Flow-owned Rule。",
        },
        {
          id: "open-flow-agents",
          label: "Open Flow Agents",
          href: "/dispatch-flows?panel=agents",
          tone: "secondary",
          description:
            "確認 Agent 已在 matched Dispatch Flow 內被指派與核准。",
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
            "用 Flow Rule 的 Required Capability 取代舊 capability fallback。",
        },
        {
          id: "open-dispatch-readiness",
          label: "Run Flow Dry-run",
          href: "/dispatch-flows?panel=dry-run",
          tone: "secondary",
          description:
            "使用 Flow Rule dry-run 檢查 Required Capability、Flow Agent assignment 與 Capability assignment。",
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
          label: "Create / Fix Dispatch Flow Rule",
          href: "/dispatch-flows",
          tone: "primary",
          description: "建立或修復 Flow-owned Rule，讓 task 產生 matchedFlowId / matchedRuleId / routingPath=FLOW_RULE。",
        },
        {
          id: "open-task-detail",
          label: "Open Flow Repair Center",
          href: taskLink(context.taskId),
          tone: "secondary",
          description: "回到 Task Detail 的 Flow 修復中心查看第一個 blocking gate。",
        },
      );
      break;
    case "MISSING_REQUIRED_CAPABILITY":
      actions.push(
        {
          id: "open-flow-capabilities",
          label: "Configure Optional Capability",
          href: "/dispatch-flows?panel=capabilities",
          tone: "primary",
          description: "若此 Flow 需要特殊能力，請在「特殊能力（選填）」設定 Required Capability；一般派工保持空白。",
        },
      );
      break;
    case "NO_FLOW_AGENT_ASSIGNMENT":
      actions.push(
        {
          id: "open-flow-agents",
          label: "Assign Agent on Flow",
          href: "/dispatch-flows?panel=agents",
          tone: "primary",
          description: "在 matched Dispatch Flow 指派並核准至少一個 Agent。",
        },
      );
      break;
    case "AGENT_REQUIRED_CAPABILITY_MISSING":
      actions.push(
        {
          id: "assign-required-capability",
          label: "Assign Required Capability",
          href: "/dispatch-flows?panel=agents&focus=required-capability",
          tone: "primary",
          description: "將 Required Capability 指派給已選 Agent，並完成 approve。",
        },
        {
          id: "open-agent",
          label: context.agentId ? "Open Agent" : "Open Agents",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: "檢查 Agent 的 approved Capability assignment。",
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
          description: "正式派工以 Flow-owned Rule / optional Required Capability / Agent assignment 為準。",
        },
        {
          id: "open-flow-dry-run",
          label: "Run Flow Dry-run",
          href: "/dispatch-flows?panel=dry-run",
          tone: "secondary",
          description: "直接檢查 Flow Rule 是否可派工。",
        },
      );
      break;
    case "DISPATCH_PROFILE_CAPABILITY_MISSING":
    case "DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL":
    case "DISPATCH_AGENT_CAPABILITY_REVOKED":
      actions.push(
        {
          id: "open-capabilities",
          label: "Assign Required Capability",
          href: "/dispatch-flows?panel=agents&focus=required-capability",
          tone: "primary",
          description:
            "確認 Flow Agent 是否取得 Required Capability。",
        },
        {
          id: "open-agent-capabilities",
          label: context.agentId ? "Open Agent Capabilities" : "Open Agents",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: "核准或重新指派 Agent Required Capability。",
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
          description: "確認必要 Runtime Feature 已定義並可用於 dispatch。",
        },
        {
          id: "open-agent-runtime-trust",
          label: context.agentId ? "Open Agent Runtime Trust" : "Open Agents",
          href: agentLink(context.agentId),
          tone: "secondary",
          description:
            "將 observation 驗證並提升為 TRUSTED，或處理被 revoke/suspend 的 feature。",
        },
      );
      break;
    case "DISPATCH_AGENT_PROFILE_MISSING":
      actions.push(
        {
          id: "open-agent-Flow Agent approval",
          label: "Open Flow Agent Assignment",
          href: agentLink(context.agentId),
          tone: "primary",
          description:
            "檢查 Flow Agent assignment、approval 與 optional Required Capability。",
        },
        {
          id: "open-dispatch-flows",
          label: "Open Dispatch Flows",
          href: "/dispatch-flows?panel=agents",
          tone: "secondary",
          description: "確認 Agent 是否在 matched Dispatch Flow 內被指派與核准。",
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
            "檢查 currentTaskCount、reservedTaskCount、maxConcurrentTasks 與 runtime load。",
        },
        {
          id: "open-agent-detail",
          label: context.agentId
            ? "Open Agent Detail"
            : "Open Agent Operations",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: "調整 capacity 或確認是否需要啟動更多 Agent。",
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
            "檢查 offline、draining、backoff、credential、enabled 狀態。",
        },
        {
          id: "open-agent-governance",
          label: context.agentId
            ? "Open Agent Governance"
            : "Open Agent Operations",
          href: agentLink(context.agentId),
          tone: "secondary",
          description: "確認 Agent 是否被治理規則或安全規則封鎖。",
        },
      );
      break;
    case "DISPATCH_DELAYED_NO_ELIGIBLE_AGENT":
      actions.push({
        id: "open-failure-queue",
        label: "Open Failure Queue",
        href: "/tasks/failure-queue",
        tone: "primary",
        description: "查看同類 delayed dispatch / recovery wait 任務。",
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
          description: "在修正 Flow Agent / Capability assignment / capacity 後立即觸發 recovery。",
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
          description: "在 Failure Queue 直接要求 Core 重新嘗試派工。",
        });
      }
      break;
    case "DISPATCH_RECOVERY_EXHAUSTED":
      actions.push({
        id: "open-task-timeline",
        label: "Open Task Timeline",
        href: taskLink(context.taskId),
        tone: "primary",
        description: "檢查已耗盡的 retry history 與 routing decision。",
      });
      if (context.includeTaskCommands && (context.canEscalate ?? true)) {
        actions.push({
          id: "escalate",
          label: "Escalate",
          command: "escalate",
          tone: "warning",
          requiresReason: true,
          description: "轉交人工處理，避免任務停在無人負責狀態。",
        });
      }
      if (context.includeTaskCommands && (context.canDeadLetter ?? true)) {
        actions.push({
          id: "dead-letter",
          label: "Move to DLQ",
          command: "deadLetter",
          tone: "danger",
          requiresReason: true,
          description: "確認無法自動恢復後移入 dead-letter。",
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
          description: "查看 candidate score breakdown、門檻與扣分原因。",
        },
        {
          id: "open-dispatch-readiness",
          label: "Review Dispatch Flow",
          href: "/dispatch-flows",
          tone: "secondary",
          description: "用測試工具重現派工條件與候選 Agent 評分。",
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
          description: "確認是否有 Agent online 且可被 Core 看到。",
        },
        {
          id: "open-agent-enrollments",
          label: "Open Agent Enrollments",
          href: "/agent-enrollments",
          tone: "secondary",
          description: "確認是否需要核准或重新註冊 Agent。",
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
          description: "確認 Core recovery scanner 與叢集狀態。",
        },
        {
          id: "open-failure-queue",
          label: "Open Failure Queue",
          href: "/tasks/failure-queue",
          tone: "secondary",
          description: "查看是否有大量 recovery 異常堆積。",
        },
      );
      break;
    default:
      actions.push({
        id: "open-task",
        label: "Open Task",
        href: taskLink(context.taskId),
        tone: "secondary",
        description: "查看 Task detail 與 dispatch diagnostics。",
      });
      break;
  }

  if (context.includeRunbook !== false && runbookRef) {
    actions.push({
      id: "runbook-ref",
      label: `Runbook: ${runbookRef}`,
      tone: "secondary",
      description:
        "此版本先顯示 runbook reference；若日後新增 Runbook page，可改為內部連結。",
    });
  }

  return uniqueActions(actions);
}
