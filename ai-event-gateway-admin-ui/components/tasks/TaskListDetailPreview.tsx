'use client';

import { StatusBadge } from '@/components/common/StatusBadge';
import { ListCapabilityLink, ListCapabilityReason } from '@/components/ui-capability/ListCapabilityControls';
import { listCapability, useUiListCapabilityRow } from '@/components/ui-capability/UiListCapabilityProvider';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import { buildTaskWorkbenchDisplay } from '@/lib/tasks/taskWorkbench';
import { TASK_UI_ACTIONS } from '@/lib/ui-capability/taskActions';
import { TaskBlockingExperienceCard } from '@/components/phase7c/TaskBlockingExperienceCard';

export function TaskListDetailPreview({ row }: Readonly<{ row?: TaskDispatchDashboardRow }>) {
  if (!row) {
    return <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-8 text-sm text-slate-500">Select a Task to review its current state and safest next action.</div>;
  }
  return <SelectedTaskPreview row={row} />;
}

function SelectedTaskPreview({ row }: Readonly<{ row: TaskDispatchDashboardRow }>) {
  const task = row.task;
  const display = buildTaskWorkbenchDisplay(row);
  const record = useUiListCapabilityRow({
    contextId: `task.list.${task.taskId}`,
    resourceId: task.taskId,
    uiActionIds: [TASK_UI_ACTIONS.view],
  });
  const view = listCapability(record, TASK_UI_ACTIONS.view);
  const waitingRetry = task.status?.toUpperCase() === 'RETRY_WAIT' || task.reasonCategory === 'WAITING_RETRY' || Boolean(task.nextDispatchAttemptAt) || Boolean(task.dispatchWaitReason);
  const historicalExecutionFailed = task.dispatchExecutionStatus?.toUpperCase() === 'FAILED';
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap gap-2"><StatusBadge status={display.businessStatusCode} label={display.businessStatus} /></div>
      <h2 className="mt-3 text-lg font-black text-slate-950">{display.title}</h2>
      <p className="mt-1 break-all text-xs text-slate-500">{task.taskId}</p>
      <div className="mt-4">
        {waitingRetry ? (
          <section className="rounded-2xl border border-blue-200 bg-blue-50 p-4" aria-label="Task waiting for recovery">
            <p className="text-xs font-black uppercase tracking-[0.16em] text-blue-700">Current state · Waiting for recovery</p>
            <h3 className="mt-1 font-black text-blue-950">Task remains active and is scheduled for retry</h3>
            <p className="mt-2 text-sm text-blue-900">{task.dispatchWaitReason ?? task.dispatchRetryReason ?? task.lifecycleReason ?? 'OpenDispatch is waiting for the next governed dispatch attempt.'}</p>
            {historicalExecutionFailed ? <p className="mt-3 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600">Previous execution evidence contains a failed attempt. That historical attempt does not mean the current Task is terminally failed.</p> : null}
          </section>
        ) : historicalExecutionFailed || task.nextAction === 'RETRY_OR_MOVE_TO_DEAD_LETTER' ? (
          <section className="rounded-2xl border border-rose-200 bg-rose-50 p-4" aria-label="Task execution failure">
            <p className="text-xs font-black uppercase tracking-[0.16em] text-rose-700">Current execution failure</p>
            <h3 className="mt-1 font-black text-rose-950">Dispatch did not complete</h3>
            <p className="mt-2 text-sm text-rose-900">{task.failureReason ?? task.lifecycleReason ?? 'The current Task reached a failure state. Open the full Task console for callback, attempt, and recovery evidence.'}</p>
          </section>
        ) : <TaskBlockingExperienceCard task={task} compact />}
      </div>
      <dl className="mt-5 space-y-3 text-sm">
        <div><dt className="text-xs font-bold uppercase tracking-wide text-slate-400">Recommended next step</dt><dd className="mt-1 font-semibold text-slate-900">{display.nextStep}</dd></div>
        <div><dt className="text-xs font-bold uppercase tracking-wide text-slate-400">Agent</dt><dd className="mt-1 text-slate-800">{task.assignedAgentId ?? 'Not assigned'}</dd></div>
        <div><dt className="text-xs font-bold uppercase tracking-wide text-slate-400">External issue</dt><dd className="mt-1 text-slate-800">{display.issueBridge.issueId ?? display.issueBridge.status}</dd></div>
      </dl>
      <ListCapabilityLink record={record} capability={view} serverGuarded href={`/tasks/${encodeURIComponent(task.taskId)}`} className="mt-6 block rounded-xl bg-slate-950 px-4 py-3 text-center text-sm font-black text-white hover:bg-slate-800">
        Open full Task console
      </ListCapabilityLink>
      <div className="mt-2"><ListCapabilityReason record={record} capability={view} serverGuarded /></div>
    </div>
  );
}
