import { StatusBadge } from '@/components/common/StatusBadge';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import { buildTaskWorkbenchDisplay } from '@/lib/tasks/taskWorkbench';

function label(status: string): string {
  if (status === 'LINKED') return 'External Issue linked';
  if (status === 'SYNC_FAILED') return 'Issue operation failed';
  if (status === 'SYNC_PENDING') return 'Issue operation pending';
  if (status === 'PROVIDER_COMPLETED') return 'Provider completed · Issue link pending';
  if (status === 'NOT_REQUIRED') return 'No external Issue required';
  if (status === 'MANUAL_REQUIRED') return 'Manual Issue decision required';
  return 'Issue evidence not available';
}

function show(value?: string | number): string {
  return value === undefined || value === null || String(value).trim() === '' ? '—' : String(value);
}

export function TaskIssueOperationalSummary({
  row,
  error,
}: Readonly<{
  row: TaskDispatchDashboardRow;
  error?: string;
}>) {
  const issue = buildTaskWorkbenchDisplay(row).issueBridge;
  return (
    <section id="task-external-issue" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="task-external-issue-heading">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">Route B · External Issue</p>
          <h2 id="task-external-issue-heading" className="mt-1 text-lg font-black text-slate-950">Issue Policy & provider status</h2>
          <p className="mt-1 text-sm text-slate-600">This is the canonical Task view of Issue Policy, AdapterAction/provider execution, and TaskIssueLink evidence.</p>
        </div>
        <StatusBadge status={issue.status} label={label(issue.status)} />
      </div>

      {error ? (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">
          Issue evidence could not be refreshed: {error}
        </div>
      ) : null}

      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Task policy</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.policy)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Policy source</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.policySource)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Decision</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.decision)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Automation</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.automationStatus)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Binding</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.bindingStatus)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">AdapterAction</p><p className="mt-1 break-all text-sm font-semibold text-slate-900">{show(issue.actionId)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Provider</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.vendor)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Provider HTTP</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.providerStatusCode)}</p></div>
      </div>

      {issue.policySource === 'A2A_PARENT_INHERITED' || issue.policyInheritanceMode === 'A2A_PARENT' ? (
        <div className="mt-3 rounded-xl border border-indigo-200 bg-indigo-50 p-3 text-sm text-indigo-950">
          <strong>A2A Child policy inheritance:</strong> effective Issue Policy was inherited from parent Task <span className="font-mono">{show(issue.policyInheritedFromTaskId)}</span>. Parent Flow/Rule is not presented as this Child Task&apos;s routing authority.
        </div>
      ) : null}

      <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-3">
        <p className="text-xs font-black uppercase tracking-wide text-slate-500">Why</p>
        <p className="mt-1 text-sm leading-6 text-slate-800">{issue.message}</p>
        {issue.decisionReason ? <p className="mt-1 text-xs text-slate-500">Decision reason: <span className="font-semibold">{issue.decisionReason}</span></p> : null}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-500">
        <span>TaskIssueLink: <strong className="text-slate-700">{show(issue.issueId)}</strong></span>
        <span>Provider issue: <strong className="text-slate-700">{show(issue.providerExternalIssueId)}</strong></span>
        <span>Outcome certainty: <strong className="text-slate-700">{show(issue.providerOutcomeCertainty ?? 'UNKNOWN')}</strong></span>
        <span>Mapping: <strong className="text-slate-700">{show(issue.projectMappingId)}</strong></span>
        <span>Connection: <strong className="text-slate-700">{show(issue.connectionId)}</strong></span>
      </div>
    </section>
  );
}
