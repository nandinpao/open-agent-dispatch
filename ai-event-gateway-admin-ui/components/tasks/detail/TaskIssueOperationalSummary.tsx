import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import { buildTaskWorkbenchDisplay } from '@/lib/tasks/taskWorkbench';
import type { CoreIssueRuntimeJourneyView, CoreIssueRuntimeStage } from '@/lib/types/domains/task';

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

const STAGE_LABELS: Record<string, string> = {
  POLICY: 'Policy',
  BINDING: 'Binding',
  ACTION: 'Action',
  EXECUTOR: 'Executor',
  PROVIDER: 'Provider',
  RESULT: 'Result',
  LINK: 'Link',
};

function stageTone(stage: CoreIssueRuntimeStage): string {
  if (stage.status === 'SUCCEEDED' || stage.status === 'NOT_REQUIRED') return 'border-emerald-200 bg-emerald-50';
  if (stage.status === 'BLOCKED' || stage.status === 'FAILED_FINAL') return 'border-rose-200 bg-rose-50';
  if (stage.status === 'FAILED_RETRYABLE') return 'border-amber-200 bg-amber-50';
  if (stage.status === 'IN_PROGRESS' || stage.status === 'PENDING') return 'border-sky-200 bg-sky-50';
  return 'border-slate-200 bg-slate-50';
}

function evidenceSummary(stage: CoreIssueRuntimeStage): string {
  const refs = stage.evidence ?? [];
  if (!refs.length) return 'No durable evidence yet';
  return refs.map((ref) => `${ref.evidenceType}:${ref.evidenceId}`).join(' · ');
}

export function TaskIssueOperationalSummary({
  row,
  journey,
  error,
  onReconcileLink,
  reconcilingLink = false,
}: Readonly<{
  row: TaskDispatchDashboardRow;
  journey?: CoreIssueRuntimeJourneyView;
  error?: string;
  onReconcileLink?: () => void;
  reconcilingLink?: boolean;
}>) {
  const issue = buildTaskWorkbenchDisplay(row).issueBridge;
  const firstProblem = journey?.firstFailedStage
    ? journey.stages.find((stage) => stage.stage === journey.firstFailedStage)
    : undefined;
  const currentStage = journey?.currentStage
    ? journey.stages.find((stage) => stage.stage === journey.currentStage)
    : undefined;
  const diagnosisStage = firstProblem ?? currentStage;
  const remediationRoute = diagnosisStage?.stage === 'BINDING'
    ? '/source-systems'
    : diagnosisStage?.stage === 'EXECUTOR'
      ? '/settings/issue-tracking'
      : diagnosisStage?.stage === 'PROVIDER' || diagnosisStage?.stage === 'RESULT'
        ? '/source-systems'
        : diagnosisStage?.stage === 'POLICY' || diagnosisStage?.stage === 'ACTION'
          ? '/dispatch'
          : '/operations/integration-sync';
  const remediationLabel = diagnosisStage?.stage === 'BINDING'
    ? 'Open Source System setup'
    : diagnosisStage?.stage === 'EXECUTOR'
      ? 'Open Issue Tracking runtime settings'
      : diagnosisStage?.stage === 'PROVIDER' || diagnosisStage?.stage === 'RESULT'
        ? 'Review Issue Tracking connection'
        : diagnosisStage?.stage === 'POLICY' || diagnosisStage?.stage === 'ACTION'
          ? 'Open Dispatch configuration'
          : 'Open integration operations';
  const canReconcileLink = diagnosisStage?.stage === 'LINK'
    && diagnosisStage.retryable
    && diagnosisStage.status === 'FAILED_RETRYABLE'
    && Boolean(onReconcileLink);

  return (
    <section id="task-external-issue" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="task-external-issue-heading">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">Route B · External Issue</p>
          <h2 id="task-external-issue-heading" className="mt-1 text-lg font-black text-slate-950">Issue Runtime Journey</h2>
          <p className="mt-1 text-sm text-slate-600">Canonical server-side evidence: Policy → Binding → Action → Executor → Provider → Result → Link.</p>
        </div>
        <StatusBadge status={journey?.overallStatus ?? issue.status} label={journey?.overallStatus ?? label(issue.status)} />
      </div>

      {error ? (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">
          Issue evidence could not be refreshed: {error}
        </div>
      ) : null}

      {journey ? (
        <>
          <div className={`mt-4 rounded-xl border p-4 ${diagnosisStage ? stageTone(diagnosisStage) : 'border-slate-200 bg-slate-50'}`}>
            <p className="text-xs font-black uppercase tracking-wide text-slate-600">{firstProblem ? 'First failed stage' : 'Current stage'}</p>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <span className="text-base font-black text-slate-950">{diagnosisStage ? STAGE_LABELS[diagnosisStage.stage] : 'None'}</span>
              {diagnosisStage ? <StatusBadge status={diagnosisStage.status} label={diagnosisStage.status} /> : null}
            </div>
            <p className="mt-2 text-sm text-slate-800">{diagnosisStage?.summary ?? 'No Issue Runtime evidence is currently available.'}</p>
            <p className="mt-1 break-all font-mono text-xs text-slate-600">Reason: {show(diagnosisStage?.reasonCode ?? journey.reasonCode)}</p>
            {!firstProblem ? <p className="mt-2 text-xs font-semibold text-slate-600">First failed stage: none proven.</p> : null}
            {diagnosisStage && (firstProblem || diagnosisStage.status === 'BLOCKED' || diagnosisStage.status === 'FAILED_RETRYABLE' || diagnosisStage.status === 'FAILED_FINAL') ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {canReconcileLink ? (
                  <button type="button" disabled={reconcilingLink} onClick={onReconcileLink} className="rounded-xl bg-slate-950 px-3 py-2 text-xs font-black text-white disabled:opacity-50">
                    {reconcilingLink ? 'Retrying local link projection…' : 'Retry link projection'}
                  </button>
                ) : null}
                {!canReconcileLink ? (
                  <Link href={remediationRoute} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-800 hover:bg-slate-50">
                    {remediationLabel}
                  </Link>
                ) : null}
              </div>
            ) : null}
          </div>

          <ol className="mt-4 grid gap-3 lg:grid-cols-7" aria-label="Issue Runtime Journey">
            {journey.stages.map((stage) => (
              <li key={stage.stage} className={`min-w-0 rounded-xl border p-3 ${stageTone(stage)}`}>
                <div className="flex items-start justify-between gap-2 lg:block">
                  <p className="text-xs font-black uppercase tracking-wide text-slate-600">{STAGE_LABELS[stage.stage] ?? stage.stage}</p>
                  <p className="mt-1 break-words text-xs font-bold text-slate-900">{stage.status}</p>
                </div>
                <p className="mt-2 break-words text-xs leading-5 text-slate-700">{stage.summary ?? stage.reasonCode ?? 'No summary'}</p>
                <p className="mt-2 break-all font-mono text-[11px] text-slate-500">{show(stage.reasonCode)}</p>
                <p className="mt-2 break-all text-[11px] text-slate-500" title={evidenceSummary(stage)}>{evidenceSummary(stage)}</p>
              </li>
            ))}
          </ol>
        </>
      ) : (
        <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
          Canonical Issue Runtime Journey is not available from this Core runtime. The compatibility summary below remains read-only evidence.
        </div>
      )}

      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Task policy</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.policy)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Binding</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.bindingStatus)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">AdapterAction</p><p className="mt-1 break-all text-sm font-semibold text-slate-900">{show(issue.actionId)}</p></div>
        <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Provider HTTP</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(issue.providerStatusCode)}</p></div>
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
