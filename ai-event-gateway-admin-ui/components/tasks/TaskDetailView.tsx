"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CommandMessage } from "@/components/common/CommandMessage";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorBox } from "@/components/common/ErrorBox";
import { LoadingBox } from "@/components/common/LoadingBox";
import { RefreshButton } from "@/components/common/RefreshButton";
import { TaskManualReviewWorkspace } from "@/components/phase7c/TaskManualReviewWorkspace";
import { TaskActionDialog, type TaskActionDialogValues } from "@/components/tasks/TaskActionDialog";
import { TaskCapabilityPrefetch } from "@/components/tasks/TaskCapabilityPrefetch";
import { TaskAdvancedDiagnostics } from "@/components/tasks/detail/TaskAdvancedDiagnostics";
import { TaskInvestigationOverview } from "@/components/tasks/detail/TaskInvestigationOverview";
import { TaskIssueOperationalSummary } from "@/components/tasks/detail/TaskIssueOperationalSummary";
import { TaskRemediationActionsPanel } from "@/components/tasks/detail/TaskDiagnosisPanels";
import { TaskVisibilitySummary } from "@/components/tasks/TaskVisibilitySummary";
import { useCapabilityMutation } from "@/components/ui-capability/UiPageBootstrapProvider";
import { useTaskDetail } from "@/hooks/useTaskDetail";
import { taskAdminApi } from "@/lib/api/domains/taskAdminApi";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import type { SelectOption } from "@/components/forms";
import { createIdempotencyKey } from "@/lib/utils/uuid";
import type { DispatchOperatorCommand } from "@/lib/dispatch-readiness/dispatchOperatorActions";
import {
  TASK_DETAIL_CONTEXT,
  TASK_UI_ACTIONS,
} from "@/lib/ui-capability/taskActions";
import {
  deriveTaskDispatchDiagnosis,
} from "@/lib/tasks/dispatchLifecycle";
import { buildTaskDiagnosisReadModel } from "@/lib/tasks/taskDiagnosisReadModel";
import {
  buildTaskRemediationCommandRequest,
  deriveAllowedTaskRemediationCommands,
  type TaskRemediationCommandDefinition,
} from "@/lib/tasks/taskRemediationCommands";
import type {
  CoreRecoveryGovernanceActionRequest,
  CoreTaskCommandAudit,
  CoreTaskRemediationCommandType,
} from "@/lib/types/domains/task";

const MODERATE_CONFIRMATION = "CONFIRM_RECOVERY_ACTION";
const HIGH_RISK_CONFIRMATION = "CONFIRM_HIGH_RISK_RECOVERY";

type TaskPendingAction =
  | "triggerRecovery"
  | "deadLetter"
  | "restoreDeadLetter"
  | "escalate";

function pendingActionCapability(action: TaskPendingAction): string {
  return ({
    triggerRecovery: TASK_UI_ACTIONS.runRecovery,
    deadLetter: TASK_UI_ACTIONS.moveDeadLetter,
    restoreDeadLetter: TASK_UI_ACTIONS.restoreDeadLetter,
    escalate: TASK_UI_ACTIONS.escalate,
  } satisfies Record<TaskPendingAction, string>)[action];
}

function actionFailureMessage(error: unknown): string {
  return error instanceof Error ? error.message : "The action could not be completed.";
}

export function TaskDetailView({ taskId }: Readonly<{ taskId: string }>) {
  const {
    data,
    loading,
    refreshing,
    error,
    lastUpdatedAt,
    refresh,
    commandMessage,
    retrying,
    cancelling,
    reassigning,
    triggeringRecovery,
    movingDeadLetter,
    restoringDeadLetter,
    retryingIssueSyncActionId,
    remediatingCommand,
    retryingHandoffSnapshotId,
    reconcilingA2AResultId,
    runTaskRemediationCommand,
    triggerRecoveryNow,
    moveToDeadLetter,
    restoreFromDeadLetter,
    retryIssueSync,
    retryHandoffRelease,
    reconcileA2AResult,
    activateOperationsSection,
  } = useTaskDetail(taskId);
  const runCapabilityMutation = useCapabilityMutation(TASK_DETAIL_CONTEXT);
  const [pendingAction, setPendingActionValue] = useState<TaskPendingAction | null>(null);
  const [pendingActionRequestId, setPendingActionRequestId] = useState<string | null>(null);
  const [pendingRemediationCommand, setPendingRemediationCommand] = useState<TaskRemediationCommandDefinition | null>(null);
  const [pendingRemediationIdempotencyKey, setPendingRemediationIdempotencyKey] = useState<string | null>(null);
  const setPendingAction = (action: TaskPendingAction | null) => {
    setPendingActionValue(action);
    setPendingActionRequestId(action ? createIdempotencyKey(`task-${action}`) : null);
  };
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [reconcilingIssueLink, setReconcilingIssueLink] = useState(false);
  const [remediationCommandAudits, setRemediationCommandAudits] = useState<CoreTaskCommandAudit[]>([]);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [targetAgentOptions, setTargetAgentOptions] = useState<SelectOption[]>([]);
  const [targetPoolOptions, setTargetPoolOptions] = useState<SelectOption[]>([]);
  const [targetOptionsLoading, setTargetOptionsLoading] = useState(false);

  useEffect(() => {
    const requiredPayload = pendingRemediationCommand?.requiredPayload;
    const tenantId = data?.row.task.tenantId ?? '';
    if (!tenantId || (requiredPayload !== 'targetAgentId' && requiredPayload !== 'targetPoolId')) {
      setTargetAgentOptions([]); setTargetPoolOptions([]); setTargetOptionsLoading(false); return;
    }
    let cancelled = false;
    setTargetOptionsLoading(true);
    const sourceSystem = data?.row.task.sourceSystem ?? data?.row.task.originSourceSystem ?? undefined;
    const request = requiredPayload === 'targetAgentId'
      ? coreAdminApi.getDispatchFlowAgentOptions(tenantId).then((items) => ({ agents: items, pools: [] }))
      : coreAdminApi.getAgentPools(tenantId, sourceSystem).then((items) => ({ agents: [], pools: items }));
    void request.then(({ agents, pools }) => {
      if (cancelled) return;
      setTargetAgentOptions(agents.filter((item) => item.selectable !== false).map((item) => ({ value: item.agentId, label: item.agentName || item.agentId, description: [item.runtimeConnected ? 'Connected' : item.runtimeStatus, item.approvalStatus, item.disabledReason].filter(Boolean).join(' · ') })));
      setTargetPoolOptions(pools.filter((item) => String(item.status ?? 'ACTIVE').toUpperCase() !== 'RETIRED').map((item) => ({ value: item.poolId, label: item.poolName || item.poolCode || item.poolId, description: [item.sourceSystem, item.poolType, item.status].filter(Boolean).join(' · ') })));
    }).catch(() => { if (!cancelled) { setTargetAgentOptions([]); setTargetPoolOptions([]); } }).finally(() => { if (!cancelled) setTargetOptionsLoading(false); });
    return () => { cancelled = true; };
  }, [data?.row.task.originSourceSystem, data?.row.task.sourceSystem, data?.row.task.tenantId, pendingRemediationCommand?.requiredPayload]);

  if (loading)
    return (
      <LoadingBox label={`load ${taskId} Core Task / Dispatch runtime...`} />
    );
  if (error) return <ErrorBox message={error} />;
  if (!data)
    return (
      <EmptyState
        title="Task not found"
        description="Core did not return a runtime view for this Task."
      />
    );

  const { task } = data.row;
  const diagnosis = deriveTaskDispatchDiagnosis({
    task,
    evidence: data.dispatchEvidence,
    runtimeVerification: data.runtimeVerification,
  });
  const taskDiagnosisReadModel = buildTaskDiagnosisReadModel({
    task,
    diagnosis,
    dispatchEvidence: data.dispatchEvidence,
    runtimeVerification: data.runtimeVerification,
    dispatchRequests: data.dispatchRequests,
    dispatchLedger: data.dispatchLedger,
    callbackInboxSummary: data.callbackInboxSummary,
    timeline: data.timeline,
    eligibleAgents: data.eligibleAgents,
    eligibleAgentsV2: data.eligibleAgentsV2,
    issueTracking: task.issueTracking ?? data.row.task.issueTracking,
    externalIssueDedup: data.issueDedup,
    remediationCommandAudits,
  });
  const allowedRemediationCommands = deriveAllowedTaskRemediationCommands(task, taskDiagnosisReadModel);
  const openRemediationCommand = (commandType: CoreTaskRemediationCommandType) => {
    const command = allowedRemediationCommands.find((candidate) => candidate.commandType === commandType);
    if (command) {
      setPendingRemediationCommand(command);
      setPendingRemediationIdempotencyKey(createIdempotencyKey(`task-remediation-${command.commandType.toLowerCase()}`));
    }
  };

  function runDispatchOperatorCommand(command: DispatchOperatorCommand) {
    if (command === "triggerRecoveryNow") setPendingAction("triggerRecovery");
    else if (command === "manualRetry") openRemediationCommand("RETRY_TASK");
    else if (command === "escalate") openRemediationCommand("MOVE_TO_MANUAL_QUEUE");
    else if (command === "deadLetter") openRemediationCommand("IGNORE_TASK");
  }

  async function executePendingAction(values: TaskActionDialogValues) {
    if (pendingRemediationCommand) {
      try {
        const result = await runCapabilityMutation(TASK_UI_ACTIONS.executeRemediation, () =>
          runTaskRemediationCommand(buildTaskRemediationCommandRequest({
            task,
            commandType: pendingRemediationCommand.commandType as CoreTaskRemediationCommandType,
            reason: values.reason,
            idempotencyKey: pendingRemediationIdempotencyKey ?? createIdempotencyKey(`task-remediation-${pendingRemediationCommand.commandType.toLowerCase()}`),
            targetAgentId: values.targetAgentId,
            targetPoolId: values.targetPoolId,
          })),
        );
        setActionMessage(result.message);
        setRemediationCommandAudits((existing) => [result.audit, ...existing].slice(0, 12));
        setPendingRemediationCommand(null);
        setPendingRemediationIdempotencyKey(null);
      } catch (actionError) {
        setActionMessage(actionFailureMessage(actionError));
      }
      return;
    }
    if (!pendingAction) return;
    const controlRequest = (risk: "MODERATE" | "HIGH"): CoreRecoveryGovernanceActionRequest => ({
      reason: values.reason,
      riskAcknowledged: true,
      confirmationPhrase: values.confirmationPhrase ?? (risk === "HIGH" ? HIGH_RISK_CONFIRMATION : MODERATE_CONFIRMATION),
      requestId: pendingActionRequestId ?? createIdempotencyKey(`task-${pendingAction}`),
    });

    try {
      await runCapabilityMutation(pendingActionCapability(pendingAction), async () => {
        if (pendingAction === "triggerRecovery") return triggerRecoveryNow(controlRequest("MODERATE"));
        if (pendingAction === "deadLetter") return moveToDeadLetter(controlRequest("HIGH"));
        if (pendingAction === "restoreDeadLetter") return restoreFromDeadLetter({ ...controlRequest("HIGH"), resetAttempts: true, immediate: true });
        const result = await taskAdminApi.escalateTask(task.taskId, { reason: values.reason });
        setActionMessage(result.message ?? `Task ${task.taskId} was escalated.`);
        await refresh();
        return result;
      });
      setPendingAction(null);
    } catch (actionError) {
      setActionMessage(actionFailureMessage(actionError));
    }
  }

  const reconcileIssueLinkProjection = async () => {
    if (!task?.taskId) return;
    setReconcilingIssueLink(true);
    setActionMessage(null);
    try {
      const result = await taskAdminApi.reconcileTaskIssueLink(task.taskId);
      setActionMessage(result.message ?? 'TaskIssueLink projection reconciliation completed.');
      await refresh();
    } catch (actionError) {
      setActionMessage(actionFailureMessage(actionError));
    } finally {
      setReconcilingIssueLink(false);
    }
  };

  const guardedRetryIssueSync = async (actionId: string) => {
    try {
      return await runCapabilityMutation(TASK_UI_ACTIONS.retryIssueSync, () => retryIssueSync(actionId));
    } catch (actionError) {
      setActionMessage(actionFailureMessage(actionError));
      throw actionError;
    }
  };
  const guardedRetryHandoffRelease = async (snapshotId: string, reason: string) => {
    try {
      return await runCapabilityMutation(TASK_UI_ACTIONS.retryHandoff, () => retryHandoffRelease(snapshotId, reason));
    } catch (actionError) {
      setActionMessage(actionFailureMessage(actionError));
      throw actionError;
    }
  };
  const guardedReconcileA2AResult = async (resultId: string, reason: string) => {
    try {
      return await runCapabilityMutation(TASK_UI_ACTIONS.reconcileA2AResult, () => reconcileA2AResult(resultId, reason));
    } catch (actionError) {
      setActionMessage(actionFailureMessage(actionError));
      throw actionError;
    }
  };


  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <Link
            href="/tasks"
            className="text-sm font-semibold text-blue-600 hover:text-blue-700"
          >
            ← Back to Tasks
          </Link>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">
            Task Details
          </h1>
          <p className="mt-1 break-all text-sm text-slate-500">{task.taskId}</p>
          <div className="mt-2"><TaskVisibilitySummary /></div>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-black uppercase tracking-wide text-slate-500">
            Actions follow the current Task state
          </div>
          <RefreshButton
            refreshing={refreshing}
            lastUpdatedAt={lastUpdatedAt}
            onRefresh={refresh}
          />
        </div>
      </div>

      <CommandMessage message={actionMessage ?? commandMessage} />
      <TaskCapabilityPrefetch />
      <TaskInvestigationOverview task={task} model={taskDiagnosisReadModel} />
      <TaskIssueOperationalSummary row={data.row} journey={data.issueRuntimeJourney} error={data.issueOperationsError} onReconcileLink={()=>void reconcileIssueLinkProjection()} reconcilingLink={reconcilingIssueLink} />

      <section id="task-actions" className="scroll-mt-24 space-y-3" aria-labelledby="task-actions-heading">
        <div>
          <h2 id="task-actions-heading" className="text-lg font-black text-slate-950">Recommended actions</h2>
          <p className="mt-1 text-sm text-slate-600">Use governed remediation only when the investigation summary recommends operator action.</p>
        </div>
        <TaskManualReviewWorkspace task={task} />
        <TaskRemediationActionsPanel
          commands={allowedRemediationCommands}
          runningCommand={remediatingCommand}
          onCommand={(command) => openRemediationCommand(command.commandType)}
        />
      </section>

      <details
        className="rounded-3xl border border-slate-200 bg-white shadow-sm"
        open={advancedOpen}
        onToggle={(event: { currentTarget: HTMLDetailsElement }) => {
          const open = event.currentTarget.open;
          setAdvancedOpen(open);
          if (open) {
            activateOperationsSection("relationships");
            activateOperationsSection("issue");
          }
        }}
      >
        <summary className="cursor-pointer list-none px-5 py-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">Advanced Diagnostics</p>
              <h2 className="mt-1 text-lg font-black text-slate-950">Routing, callback, A2A, recovery, issue, and raw authority evidence</h2>
              <p className="mt-1 text-sm text-slate-600">Open this only when the investigation summary does not provide enough evidence.</p>
            </div>
            <span className="rounded-full border border-slate-300 bg-slate-50 px-3 py-1 text-xs font-black text-slate-700">{advancedOpen ? "Hide diagnostics" : "Open diagnostics"}</span>
          </div>
        </summary>
        {advancedOpen ? (
          <div className="border-t border-slate-200 p-5">
            <TaskAdvancedDiagnostics
              data={data}
              diagnosis={diagnosis}
              retrying={retrying}
              retryingIssueSyncActionId={retryingIssueSyncActionId}
              retryingHandoffSnapshotId={retryingHandoffSnapshotId}
              reconcilingA2AResultId={reconcilingA2AResultId}
              onRetryIssueSync={guardedRetryIssueSync}
              onRetryHandoffRelease={guardedRetryHandoffRelease}
              onReconcileA2AResult={guardedReconcileA2AResult}
              onDispatchOperatorCommand={runDispatchOperatorCommand}
              onActivateOperationsSection={activateOperationsSection}
            />
          </div>
        ) : null}
      </details>
      <TaskActionDialog
        open={pendingAction !== null || pendingRemediationCommand !== null}
        title={
          pendingRemediationCommand ? pendingRemediationCommand.label :
          pendingAction === "triggerRecovery" ? "Run Recovery Now" :
          pendingAction === "deadLetter" ? "Move to Dead Letter" :
          pendingAction === "restoreDeadLetter" ? "Restore from Dead Letter" :
          pendingAction === "escalate" ? "Escalate to Human Review" : "Retry Dispatch"
        }
        target={task.taskId}
        description={pendingRemediationCommand ? `${pendingRemediationCommand.description}. Core validates state prerequisites, the idempotency key, and expectedTaskVersion, then records before/after audit evidence.` : "Core executes every action through the authoritative state machine and records the reason and result in the Task timeline."}
        confirmLabel={pendingRemediationCommand ? "Confirm Command" : pendingAction === "deadLetter" ? "Confirm Dead Letter" : pendingAction === "restoreDeadLetter" ? "Confirm Restore" : pendingAction === "escalate" ? "Confirm Escalation" : "Confirm Action"}
        tone={pendingRemediationCommand?.tone === "danger" || pendingAction === "deadLetter" ? "danger" : "warning"}
        isRunning={Boolean(remediatingCommand) || retrying || cancelling || reassigning || triggeringRecovery || movingDeadLetter || restoringDeadLetter}
        allowTargetAgent={pendingRemediationCommand?.requiredPayload === "targetAgentId"}
        allowTargetPool={pendingRemediationCommand?.requiredPayload === "targetPoolId"}
        targetAgentOptions={targetAgentOptions}
        targetPoolOptions={targetPoolOptions}
        targetOptionsLoading={targetOptionsLoading}
        requiredPhrase={pendingRemediationCommand?.requiredPhrase ?? (pendingAction === "deadLetter" || pendingAction === "restoreDeadLetter" ? HIGH_RISK_CONFIRMATION : pendingAction === "triggerRecovery" || pendingAction === "escalate" ? MODERATE_CONFIRMATION : undefined)}
        onCancel={() => { setPendingAction(null); setPendingRemediationCommand(null); setPendingRemediationIdempotencyKey(null); }}
        onConfirm={executePendingAction}
      />
    </div>
  );
}
