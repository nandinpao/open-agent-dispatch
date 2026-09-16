'use client';

import { A2AResultReliabilityPanel } from '@/components/a2a/A2AResultReliabilityPanel';
import { HandoffSnapshotReliabilityPanel } from '@/components/handoff/HandoffSnapshotReliabilityPanel';
import { ResourceGovernancePanel } from '@/components/resource-access/ResourceGovernancePanel';
import { TaskA2ARequestDrawer } from '@/components/phase7c/TaskA2ARequestDrawer';
import { TaskChainExperiencePanel } from '@/components/phase7c/TaskChainExperiencePanel';
import { TaskLineageEvidencePanel } from '@/components/tasks/TaskLineageEvidencePanel';
import { TaskControlConsoleTabs } from '@/components/tasks/TaskControlConsoleTabs';
import { TaskFinalizationAuthorityPanel } from '@/components/tasks/TaskFinalizationAuthorityPanel';
import { TaskFlowMatchAuthorityPanel } from '@/components/tasks/TaskFlowMatchAuthorityPanel';
import { TaskA2AEvidenceChainPanel } from '@/components/tasks/detail/TaskA2AEvidencePanels';
import { buildTaskControlConsoleTabs } from '@/components/tasks/detail/TaskDetailTabBuilders';
import type { TaskDispatchDetailResource } from '@/hooks/useTaskDetail';
import type { DispatchOperatorCommand } from '@/lib/dispatch-readiness/dispatchOperatorActions';
import type { TaskDispatchDiagnosis } from '@/lib/tasks/dispatchLifecycle';
import type { CommandResult } from '@/lib/types/admin';
import type { HandoffSnapshotView } from '@/lib/handoffContextContract';
import type { A2AResultProcessingView } from '@/lib/a2aResultReliabilityContract';

export function TaskAdvancedDiagnostics({
  data,
  diagnosis,
  retrying,
  retryingIssueSyncActionId,
  retryingHandoffSnapshotId,
  reconcilingA2AResultId,
  onRetryIssueSync,
  onRetryHandoffRelease,
  onReconcileA2AResult,
  onDispatchOperatorCommand,
  onActivateOperationsSection,
}: Readonly<{
  data: TaskDispatchDetailResource;
  diagnosis: TaskDispatchDiagnosis;
  retrying: boolean;
  retryingIssueSyncActionId: string | null;
  retryingHandoffSnapshotId: string | null;
  reconcilingA2AResultId: string | null;
  onRetryIssueSync: (actionId: string) => Promise<CommandResult>;
  onRetryHandoffRelease: (snapshotId: string, reason: string) => Promise<HandoffSnapshotView>;
  onReconcileA2AResult: (resultId: string, reason: string) => Promise<A2AResultProcessingView>;
  onDispatchOperatorCommand: (command: DispatchOperatorCommand) => void;
  onActivateOperationsSection: (section: 'execution' | 'issue' | 'relationships') => void;
}>) {
  const { task, delivery, callbackRelay } = data.row;
  const attemptHistory = data.attemptHistory ?? [];
  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
        <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">Authority & ownership evidence</p>
        <div className="mt-4 space-y-4">
          <ResourceGovernancePanel resourceType="TASK" resourceId={task.taskId} permissionCode="task.read" requestedVisibility="STANDARD" />
          <TaskFlowMatchAuthorityPanel taskId={task.taskId} />
          <TaskFinalizationAuthorityPanel taskId={task.taskId} />
        </div>
      </section>

      <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.16em] text-indigo-700">A2A / relationship evidence</p>
            <p className="mt-1 text-sm text-indigo-900">Parent/child lineage, handoff reliability, and capability-delegation result evidence.</p>
          </div>
          <TaskA2ARequestDrawer taskId={task.taskId} />
        </div>
        <div className="mt-4 space-y-5">
          <TaskChainExperiencePanel taskId={task.taskId} />
          <TaskLineageEvidencePanel evidence={data.lineage} error={data.lineageError} />
          <TaskA2AEvidenceChainPanel task={task} parentTask={data.taskFamily?.parentTask} childTasks={data.taskFamily?.childTasks} caseTimeline={data.caseTimeline} familyError={data.taskFamilyError} />
          <HandoffSnapshotReliabilityPanel snapshots={data.handoffSnapshots ?? []} evidence={data.handoffReleaseEvidence ?? []} error={data.handoffReliabilityError} retryingSnapshotId={retryingHandoffSnapshotId} onRetry={onRetryHandoffRelease} />
          <A2AResultReliabilityPanel value={data.a2aResultReliability} error={data.a2aResultReliabilityError} reconcilingResultId={reconcilingA2AResultId} onReconcile={onReconcileA2AResult} />
        </div>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-4">
        <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">Raw execution / routing / callback / issue evidence</p>
        <div className="mt-4">
          <TaskControlConsoleTabs
            onTabChange={(tabId) => onActivateOperationsSection(tabId === 'issue-result' ? 'issue' : 'execution')}
            tabs={buildTaskControlConsoleTabs({
              row: data.row,
              task,
              diagnosis,
              dispatchRequests: data.dispatchRequests,
              attemptHistory,
              dispatchLedger: data.dispatchLedger ?? [],
              dispatchLedgerError: data.dispatchLedgerError,
              callbackInbox: data.callbackInbox ?? [],
              callbackInboxSummary: data.callbackInboxSummary,
              callbackInboxError: data.callbackInboxError,
              timeline: data.timeline,
              timelineError: data.timelineError,
              caseTimeline: data.caseTimeline,
              caseTimelineError: data.caseTimelineError,
              taskFamily: data.taskFamily,
              taskFamilyError: data.taskFamilyError,
              dispatchEvidence: data.dispatchEvidence,
              dispatchEvidenceError: data.dispatchEvidenceError,
              runtimeVerification: data.runtimeVerification,
              runtimeVerificationError: data.runtimeVerificationError,
              attemptHistoryError: data.attemptHistoryError,
              routingDecisions: data.routingDecisions,
              routingDecisionsError: data.routingDecisionsError,
              dispatchRequirements: data.dispatchRequirements,
              dispatchRequirementsError: data.dispatchRequirementsError,
              eligibleAgents: data.eligibleAgents,
              eligibleAgentsError: data.eligibleAgentsError,
              eligibleAgentsV2: data.eligibleAgentsV2,
              eligibleAgentsV2Error: data.eligibleAgentsV2Error,
              delivery,
              deliveryRuntimeError: data.deliveryRuntimeError,
              callbackRelay,
              callbackRelayRuntimeError: data.callbackRelayRuntimeError,
              retrying,
              retryingIssueSyncActionId: retryingIssueSyncActionId ?? undefined,
              onRetryIssueSync,
              onDispatchOperatorCommand,
            })}
          />
        </div>
      </section>
    </div>
  );
}
