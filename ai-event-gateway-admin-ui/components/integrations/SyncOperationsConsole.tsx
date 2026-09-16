'use client';

export interface SyncOperationsSummary {
  pending: number;
  retryWaiting: number;
  failed: number;
  completed: number;
  circuitState: string;
  lastProviderError?: string;
  nextRetryAt?: string;
}

export function SyncOperationsConsole({ summary }: Readonly<{ summary: SyncOperationsSummary }>) {
  const metrics = [
    ['Pending', summary.pending],
    ['Retry waiting', summary.retryWaiting],
    ['Failed', summary.failed],
    ['Completed', summary.completed],
  ] as const;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="sync-operations-title">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div><h3 id="sync-operations-title" className="font-black text-slate-950">Issue Connector Operations</h3><p className="mt-1 text-sm text-slate-600">Current ISSUE_TRACKING AdapterAction execution and Redmine Provider-health evidence. Legacy projection, relay and conflict authority are not part of this operational view.</p></div>
        <span className={`rounded-full px-3 py-1 text-xs font-black ${['OPEN','OPEN_CIRCUIT','SATURATED','DISABLED'].includes(summary.circuitState) ? 'bg-rose-100 text-rose-800' : ['HALF_OPEN','THROTTLED','RECOVERING'].includes(summary.circuitState) ? 'bg-amber-100 text-amber-900' : summary.circuitState === 'HEALTHY' || summary.circuitState === 'CLOSED' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-700'}`}>Provider {summary.circuitState}</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{metrics.map(([label, value]) => <div key={label} className="rounded-xl border border-slate-200 bg-slate-50 p-3"><div className="text-xs font-bold text-slate-500">{label}</div><div className="mt-1 text-xl font-black text-slate-950">{value}</div></div>)}</div>
      {summary.lastProviderError ? <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900" role="alert"><strong>Latest Provider failure:</strong> {summary.lastProviderError}</div> : null}
      {summary.nextRetryAt ? <p className="mt-3 text-xs font-bold text-slate-500">Next retry: {summary.nextRetryAt}</p> : null}
    </section>
  );
}
