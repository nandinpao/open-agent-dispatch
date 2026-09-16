import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import { buildTaskWorkbenchDisplay } from '@/lib/tasks/taskWorkbench';
import { formatDateTime } from '@/lib/utils/format';

function InfoTile({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</div>
      <div className="mt-1 break-words text-sm font-semibold text-slate-800">{value || '-'}</div>
    </div>
  );
}


function severityBadgeClass(code: string): string {
  const normalized = String(code ?? '').toUpperCase();
  if (normalized === 'CRITICAL') return 'border-rose-300 bg-rose-50 text-rose-700';
  if (normalized === 'HIGH') return 'border-orange-300 bg-orange-50 text-orange-700';
  if (normalized === 'MIDDLE' || normalized === 'MEDIUM') return 'border-amber-300 bg-amber-50 text-amber-700';
  if (normalized === 'LOW') return 'border-emerald-300 bg-emerald-50 text-emerald-700';
  return 'border-slate-200 bg-slate-50 text-slate-600';
}

function IssueBridgeLink({ url, children }: Readonly<{ url?: string; children: string }>) {
  if (!url) return <span>{children}</span>;
  return <a href={url} target="_blank" rel="noreferrer" className="font-semibold text-blue-600 hover:text-blue-700">{children}</a>;
}

function issueStatusLabel(status: string): string {
  if (status === 'LINKED') return 'External Issue linked';
  if (status === 'SYNC_FAILED') return 'Issue operation failed';
  if (status === 'SYNC_PENDING') return 'Issue operation pending';
  if (status === 'PROVIDER_COMPLETED') return 'Provider completed · link pending';
  if (status === 'NOT_REQUIRED') return 'No Issue required by policy';
  if (status === 'MANUAL_REQUIRED') return 'Manual Issue decision required';
  return 'No Issue operation evidence';
}

function issueExecutionEvidence(status: string): string {
  if (status === 'LINKED') return 'Provider confirmed and Issue link recorded';
  if (status === 'PROVIDER_COMPLETED') return 'Provider completed; Issue link unavailable/pending';
  if (status === 'SYNC_PENDING') return 'Adapter/provider operation pending';
  if (status === 'SYNC_FAILED') return 'Operation failed';
  if (status === 'NOT_REQUIRED') return 'Not required by Issue Policy';
  if (status === 'MANUAL_REQUIRED') return 'Waiting for operator decision';
  return 'No execution evidence';
}

function issueResultCode(issue: ReturnType<typeof buildTaskWorkbenchDisplay>['issueBridge']): string {
  if (issue.failureCode) return issue.failureCode;
  if (issue.status === 'LINKED' && issue.providerStatusCode && issue.providerStatusCode >= 200 && issue.providerStatusCode < 300) return 'SUCCESS';
  if (issue.status === 'NOT_REQUIRED') return issue.decisionReason ?? 'NOT_REQUIRED';
  if (issue.status === 'MANUAL_REQUIRED') return issue.decisionReason ?? 'MANUAL_DECISION';
  return '-';
}

export function TaskWorkbenchPanel({ row, onRetryIssueSync, retryingIssueSyncActionId }: Readonly<{ row: TaskDispatchDashboardRow; onRetryIssueSync?: (actionId: string) => void; retryingIssueSyncActionId?: string | null }>) {
  const task = row.task;
  const display = buildTaskWorkbenchDisplay(row);
  const issue = display.issueBridge;

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={display.businessStatusCode} label={display.businessStatus} />
            <span className={`rounded-full border px-2.5 py-1 text-xs font-black ${severityBadgeClass(display.severity.code)}`}>Severity {display.severity.label}</span>
            <StatusBadge status={issue.status} label={issueStatusLabel(issue.status)} />
          </div>
          <h1 className="mt-3 break-words text-2xl font-bold text-slate-950">{display.title}</h1>
          <p className="mt-1 break-all text-sm text-slate-500">{display.subtitle}</p>
        </div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm text-slate-700 lg:max-w-md">
          <div className="text-xs font-bold uppercase tracking-wide text-slate-400">Next step</div>
          <div className="mt-1 font-semibold">{display.nextStep}</div>
        </div>
      </div>

      <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <InfoTile label="source" value={display.sourceLabel} />
        <InfoTile label="Target" value={display.targetLabel} />
        <InfoTile label="event / Error" value={display.eventLabel} />
        <InfoTile label="Severity" value={display.severity.label} />
        <InfoTile label="createreason" value={display.triggerReason} />
      </div>

      <div className="mt-5 grid gap-4 lg:grid-cols-3">
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
          <h2 className="text-sm font-bold text-slate-900">Agent runsummary</h2>
          <div className="mt-3 space-y-2 text-sm text-slate-700">
            <div><span className="font-semibold text-slate-500">process Agent:</span>{task.assignedAgentId ? <Link href={`/agents/${encodeURIComponent(task.assignedAgentId)}`} className="font-semibold text-blue-600 hover:text-blue-700">{display.assignedAgentLabel}</Link> : display.assignedAgentLabel}</div>
            <div><span className="font-semibold text-slate-500">Capability </span>{display.requiredCapabilityLabel}</div>
            <div><span className="font-semibold text-slate-500">Current status:</span>{display.businessStatus}</div>
          </div>
          <div className="mt-4 rounded-lg bg-white p-3 text-sm text-slate-700">
            <div className="text-xs font-bold uppercase tracking-wide text-slate-400">latestsummary</div>
            <p className="mt-1 leading-6">{display.latestAgentSummary}</p>
          </div>
        </div>

        <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
          <h2 className="text-sm font-bold text-slate-900">Expected outputs</h2>
          <ul className="mt-3 space-y-2 text-sm text-slate-700">
            {display.expectedOutputs.map((item) => (
              <li key={item} className="flex gap-2"><span className="mt-0.5 text-emerald-600">•</span><span>{item}</span></li>
            ))}
          </ul>
          <p className="mt-4 rounded-lg bg-white p-3 text-xs leading-5 text-slate-500">
            Issue operations shown here are canonical AdapterAction / provider evidence. Legacy Issue Projection synchronization is retired.
          </p>
        </div>

        <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
          <h2 className="text-sm font-bold text-slate-900">External Issue Operation Evidence</h2>
          <div className="mt-3 space-y-2 text-sm text-slate-700">
            <div><span className="font-semibold text-slate-500">Issue Policy:</span>{issue.policy ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Policy source:</span>{issue.policySource ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Decision:</span>{issue.decision ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Decision reason:</span>{issue.decisionReason ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Binding:</span>{issue.bindingStatus ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Automation:</span>{issue.automationStatus ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Provider:</span>{issue.vendor ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Issue:</span><IssueBridgeLink url={issue.issueUrl}>{issue.issueId ?? '-'}</IssueBridgeLink></div>
            <div><span className="font-semibold text-slate-500">Result:</span>{issue.issueStatus ?? issue.status}</div>
            <div><span className="font-semibold text-slate-500">Operation:</span>{issue.actionType ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Provider HTTP:</span>{issue.providerStatusCode ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Result code:</span>{issueResultCode(issue)}</div>
            <div><span className="font-semibold text-slate-500">Connection impact:</span>{issue.providerHealthImpact ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Outcome certainty:</span>{issue.providerOutcomeCertainty ?? 'UNKNOWN'}</div>
            <div><span className="font-semibold text-slate-500">Source system:</span>{issue.sourceSystemId ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Credential version:</span>{issue.credentialVersion ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Correlation:</span>{issue.correlationId ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">A2A request:</span>{issue.a2aRequestId ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Operation fingerprint:</span>{issue.operationFingerprint ? `${issue.operationFingerprint.slice(0, 16)}…` : '-'}</div>
            <div><span className="font-semibold text-slate-500">Last provider evidence:</span>{issue.lastSyncedAt ? formatDateTime(issue.lastSyncedAt) : '-'}</div>
            <div><span className="font-semibold text-slate-500">Action ID:</span>{issue.actionId ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Comment mode:</span>{issue.commentMode ?? '-'}</div>
            <div><span className="font-semibold text-slate-500">Execution evidence:</span>{issueExecutionEvidence(issue.status)}</div>
          </div>
          <p className="mt-4 rounded-lg bg-white p-3 text-sm leading-6 text-slate-700">{issue.message}</p>
          {issue.latestCommentPreview ? (
            <div className="mt-3 rounded-lg bg-white p-3 text-xs leading-5 text-slate-500">
              <div className="font-bold uppercase tracking-wide text-slate-400">Comment preview</div>
              <p className="mt-1 whitespace-pre-wrap">{issue.latestCommentPreview}</p>
            </div>
          ) : null}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {issue.nextAction ? <p className="text-xs font-semibold text-slate-500">Next:{issue.nextAction}</p> : null}
            {issue.retryable && issue.actionId && onRetryIssueSync ? (
              <button
                type="button"
                onClick={() => onRetryIssueSync(issue.actionId!)}
                disabled={retryingIssueSyncActionId === issue.actionId}
                className="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-bold text-blue-700 hover:bg-blue-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
              >
                {retryingIssueSyncActionId === issue.actionId ? 'Retrying...' : 'Retry provider operation'}
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}
