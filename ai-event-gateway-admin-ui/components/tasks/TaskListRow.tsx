'use client';

import { StatusBadge } from '@/components/common/StatusBadge';
import { ListCapabilityButton, ListCapabilityLink, ListCapabilityReason } from '@/components/ui-capability/ListCapabilityControls';
import { listCapability, useUiListCapabilityRow } from '@/components/ui-capability/UiListCapabilityProvider';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import { buildTaskWorkbenchDisplay } from '@/lib/tasks/taskWorkbench';
import { TASK_UI_ACTIONS } from '@/lib/ui-capability/taskActions';
import { formatDateTime } from '@/lib/utils/format';

const ROW_ACTIONS = [TASK_UI_ACTIONS.view, TASK_UI_ACTIONS.retry, TASK_UI_ACTIONS.cancel] as const;

export function TaskListRow({
  row,
  selected,
  onSelect,
  onRetry,
  onCancel,
  allowRetry,
  allowCancel,
}: Readonly<{
  row: TaskDispatchDashboardRow;
  selected: boolean;
  onSelect: () => void;
  onRetry: () => void;
  onCancel: () => void;
  allowRetry: boolean;
  allowCancel: boolean;
}>) {
  const task = row.task;
  const display = buildTaskWorkbenchDisplay(row);
  const contextId = `task.list.${task.taskId}`;
  const capabilityRecord = useUiListCapabilityRow({
    contextId,
    resourceId: task.taskId,
    uiActionIds: ROW_ACTIONS,
  });
  const view = listCapability(capabilityRecord, TASK_UI_ACTIONS.view);
  const retry = listCapability(capabilityRecord, TASK_UI_ACTIONS.retry);
  const cancel = listCapability(capabilityRecord, TASK_UI_ACTIONS.cancel);

  return (
    <article className={`h-full rounded-2xl border bg-white p-4 shadow-sm transition ${selected ? 'border-blue-400 ring-2 ring-blue-100' : 'border-slate-200 hover:border-slate-300'}`}>
      <button type="button" onClick={onSelect} className="block w-full text-left" aria-pressed={selected}>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={display.businessStatusCode} label={display.businessStatus} />
          <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-bold text-slate-700">{display.severity.label}</span>
          <span className="ml-auto text-xs text-slate-500">{task.updatedAt ? formatDateTime(task.updatedAt) : 'No update time'}</span>
        </div>
        <h3 className="mt-2 line-clamp-2 font-black text-slate-950">{display.title}</h3>
        <p className="mt-1 line-clamp-1 text-xs text-slate-500">{display.subtitle}</p>
        <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-600">
          <span><strong className="text-slate-400">Source:</strong> {display.sourceLabel}</span>
          <span><strong className="text-slate-400">Agent:</strong> {task.assignedAgentId ?? 'Not assigned'}</span>
          <span className="col-span-2 line-clamp-1"><strong className="text-slate-400">Next:</strong> {display.nextStep}</span>
        </div>
      </button>
      <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-3">
        <ListCapabilityLink record={capabilityRecord} capability={view} serverGuarded href={`/tasks/${encodeURIComponent(task.taskId)}`} className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-bold text-slate-700 hover:bg-slate-50">
          Open details
        </ListCapabilityLink>
        {allowRetry ? <ListCapabilityButton record={capabilityRecord} capability={retry} onClick={onRetry} className="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-bold text-blue-700 hover:bg-blue-50">Retry</ListCapabilityButton> : null}
        {allowCancel ? <ListCapabilityButton record={capabilityRecord} capability={cancel} onClick={onCancel} className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-bold text-rose-700 hover:bg-rose-50">Cancel</ListCapabilityButton> : null}
      </div>
      <div className="mt-2"><ListCapabilityReason record={capabilityRecord} capability={view} serverGuarded /></div>
    </article>
  );
}
