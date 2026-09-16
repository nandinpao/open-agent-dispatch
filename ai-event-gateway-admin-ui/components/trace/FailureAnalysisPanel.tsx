import { StatusBadge } from '@/components/common/StatusBadge';
import type { GatewayTaskDetail } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';

function buildSuggestion(task: GatewayTaskDetail): string {
  if (!task.failureReason) {
    return 'Review the dispatch evidence timeline to identify the first blocked stage before retrying.';
  }
  const reason = task.failureReason.toLowerCase();
  if (reason.includes('capability') || reason.includes('skill')) {
    return 'Check the Task Required Capability and confirm that at least one member of the selected Agent Pool has the matching Core APPROVED capability assignment. Runtime-reported capability values are diagnostic only.';
  }
  if (reason.includes('timeout')) {
    return 'Check Agent heartbeat, runtime capacity, delivery/ACK evidence, and MCP or tool-call timeout details before retrying.';
  }
  if (reason.includes('disconnect') || reason.includes('offline') || reason.includes('session')) {
    return 'Check the selected Agent runtime session, credential, gateway connection, heartbeat, and capacity before retrying.';
  }
  return 'Review the Task dispatch evidence, logs, request payload, and Agent runtime error. Repair the first canonical blocker before retrying.';
}

export function FailureAnalysisPanel({ task }: Readonly<{ task: GatewayTaskDetail }>) {
  const failed = task.status === 'FAILED';
  return (
    <section className={`rounded-2xl border p-5 shadow-sm ${failed ? 'border-rose-200 bg-rose-50/70' : 'border-slate-200 bg-white'}`}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className={`text-base font-bold ${failed ? 'text-rose-900' : 'text-slate-900'}`}>Failure Analysis</h2>
          <p className={`mt-1 text-sm ${failed ? 'text-rose-700' : 'text-slate-500'}`}>Find the first blocked dispatch stage, repair that authority, then retry.</p>
        </div>
        <StatusBadge status={task.status} />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <div className="rounded-xl bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Failed At</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{task.failedAt ? formatDateTime(task.failedAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Retry Count</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{task.retryCount}</div>
        </div>
        <div className="rounded-xl bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Agent</div>
          <div className="mt-1 break-all text-sm font-bold text-slate-800">{task.assignedAgentId ?? '-'}</div>
        </div>
      </div>
      <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 p-4 text-sm text-indigo-900">
        <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Canonical dispatch authority</div>
        <p className="mt-1 leading-6">Check Source Flow → Agent Pool → Pool membership → Core APPROVED Required Capability qualification → runtime eligibility/capacity → routing selection. Runtime-reported capability observations are troubleshooting diagnostics only.</p>
      </div>
      <div className="mt-4 rounded-xl bg-white p-4">
        <div className="text-xs font-semibold text-slate-400">Failure Reason</div>
        <p className="mt-1 text-sm font-semibold text-slate-800">{task.failureReason ?? '-'}</p>
      </div>
      <div className="mt-3 rounded-xl bg-white p-4">
        <div className="text-xs font-semibold text-slate-400">Suggested Action</div>
        <p className="mt-1 text-sm text-slate-700">{buildSuggestion(task)}</p>
      </div>
    </section>
  );
}
