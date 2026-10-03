'use client';

import Link from 'next/link';
import { integrationConfigurationHref, recoveryCategoryLabel, recoveryGuidance, type IntegrationRecoverySnapshot } from '@/lib/integrations/integrationRecovery';

function stateTone(state: string) {
  if (['OPEN_CIRCUIT','DEGRADED','DISABLED'].includes(state)) return 'bg-rose-100 text-rose-800';
  if (['THROTTLED','RECOVERING'].includes(state)) return 'bg-amber-100 text-amber-900';
  if (state === 'HEALTHY') return 'bg-emerald-100 text-emerald-800';
  return 'bg-slate-100 text-slate-700';
}

export function SyncOperationsConsole({ snapshot }: Readonly<{ snapshot: IntegrationRecoverySnapshot }>) {
  const metrics = [
    ['Pending', snapshot.pending],
    ['Retry waiting', snapshot.retryWaiting],
    ['Needs attention', snapshot.needsAttention],
    ['Completed', snapshot.completed],
  ] as const;
  const failure = snapshot.latestFailure ?? null;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="sync-operations-title">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div><h3 id="sync-operations-title" className="font-black text-slate-950">Issue Connector Operations</h3><p className="mt-1 text-sm text-slate-600">Canonical ISSUE_TRACKING AdapterAction state plus timestamp-ordered executor-audit evidence. A provider request validation error does not by itself mean that Redmine/Jira is unhealthy.</p></div>
        <span className={`rounded-full px-3 py-1 text-xs font-black ${stateTone(snapshot.providerState)}`}>Provider {snapshot.providerState}</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{metrics.map(([label, value]) => <div key={label} className="rounded-xl border border-slate-200 bg-slate-50 p-3"><div className="text-xs font-bold text-slate-500">{label}</div><div className="mt-1 text-xl font-black text-slate-950">{value}</div></div>)}</div>
      {snapshot.uncertain > 0 ? <div className="mt-4 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm font-semibold text-amber-950">{snapshot.uncertain} operation(s) have uncertain provider outcome. Reconcile them before any mutating retry.</div> : null}
      {failure ? <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-950" role="alert">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><strong>Latest integration failure</strong><div className="mt-1 text-base font-black">{recoveryCategoryLabel(failure.category)}</div></div><span className="rounded-full bg-white px-2 py-1 text-xs font-black">{failure.occurredAt ?? 'time unavailable'}</span></div>
        <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-4"><Fact label="Operation" value={failure.actionId}/><Fact label="HTTP" value={failure.providerStatusCode == null ? '—' : String(failure.providerStatusCode)}/><Fact label="Failure code" value={failure.providerFailureCode}/><Fact label="Provider health impact" value={failure.providerHealthImpact}/></div>
        <p className="mt-3 leading-6">{recoveryGuidance(failure.category)}</p>
        {failure.message ? <details className="mt-3 rounded-lg border border-rose-200 bg-white/70 p-3"><summary className="cursor-pointer font-black">Technical details</summary><p className="mt-2 break-words font-mono text-xs leading-5">{failure.message}</p>{failure.correlationId ? <p className="mt-2 text-xs"><b>Correlation:</b> {failure.correlationId}</p> : null}</details> : null}
        <div className="mt-3 flex flex-wrap gap-2"><Link href={integrationConfigurationHref(failure)} className="rounded-lg bg-slate-950 px-3 py-2 text-xs font-black text-white">Open Integration Configuration</Link><span className="rounded-lg border border-rose-200 bg-white px-3 py-2 text-xs font-bold">Recovery preflight required before retry</span></div>
      </div> : <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-900">No canonical Issue Tracking execution failure is present in the current audit window.</div>}
      {snapshot.nextRetryAt ? <p className="mt-3 text-xs font-bold text-slate-500">Next scheduled retry window: {snapshot.nextRetryAt}</p> : null}
    </section>
  );
}

function Fact({label,value}:{label:string;value?:string|null}) { return <div className="rounded-lg border border-rose-100 bg-white/70 p-2"><div className="text-[11px] font-black uppercase tracking-wide text-rose-700">{label}</div><div className="mt-1 break-words font-semibold text-slate-900">{value || '—'}</div></div>; }
