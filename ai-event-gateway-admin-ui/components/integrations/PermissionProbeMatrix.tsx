'use client';

export type PermissionProbeResult = 'GRANTED' | 'DENIED' | 'UNSUPPORTED' | 'NOT_TESTED' | 'ERROR';
export interface PermissionProbeRow { capability: string; result: PermissionProbeResult; required: boolean; fallback?: string; evidence?: string }

const resultLabel: Record<PermissionProbeResult, string> = { GRANTED: 'PASS', DENIED: 'DENIED', UNSUPPORTED: 'UNSUPPORTED', NOT_TESTED: 'NOT TESTED', ERROR: 'ERROR' };

const tone: Record<PermissionProbeResult, string> = {
  GRANTED: 'bg-emerald-100 text-emerald-800',
  DENIED: 'bg-rose-100 text-rose-800',
  UNSUPPORTED: 'bg-amber-100 text-amber-900',
  NOT_TESTED: 'bg-slate-100 text-slate-700',
  ERROR: 'bg-rose-100 text-rose-800',
};

export function PermissionProbeMatrix({ rows, onRunProbe, running = false }: Readonly<{ rows: PermissionProbeRow[]; onRunProbe?: () => void; running?: boolean }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="permission-probe-matrix-title">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div><h3 id="permission-probe-matrix-title" className="font-black text-slate-950">Project Permission Probe</h3><p className="mt-1 text-sm text-slate-600">Verify the capabilities actually granted to the scoped Integration Principal.</p></div>
        {onRunProbe ? <button type="button" onClick={onRunProbe} disabled={running} className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-50">{running ? 'Running Probe…' : 'Run Permission Probe'}</button> : null}
      </div>
      <div className="mt-4 overflow-x-auto rounded-xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200 text-sm"><caption className="sr-only">Provider permission probe results for the selected scoped integration principal.</caption>
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Capability</th><th className="px-4 py-3">Result</th><th className="px-4 py-3">Required</th><th className="px-4 py-3">Fallback or evidence</th></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => <tr key={row.capability}><td className="px-4 py-3 font-bold text-slate-900">{row.capability}</td><td className="px-4 py-3"><span className={`rounded-full px-2.5 py-1 text-xs font-black ${tone[row.result]}`}>{resultLabel[row.result]}</span></td><td className="px-4 py-3">{row.required ? 'Required' : 'Optional'}</td><td className="px-4 py-3 text-slate-600">{row.evidence ?? row.fallback ?? '—'}</td></tr>)}
          </tbody>
        </table>
      </div>
    </section>
  );
}
