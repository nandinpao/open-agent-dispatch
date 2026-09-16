import type { A2ABlockerDiagnosis } from '@/lib/api/domains/a2aOperationsApi';
import { A2AStatusBadge } from './A2AStatusBadge';

export function BlockerDiagnosisPanel({ blocker }: { blocker: A2ABlockerDiagnosis }) {
  const clear = blocker.code === 'NONE';
  return (
    <section className={`rounded-2xl border p-5 ${clear ? 'border-emerald-200 bg-emerald-50/50' : 'border-amber-200 bg-amber-50/60'}`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Blocker diagnosis</p>
          <h2 className="mt-1 text-lg font-black text-slate-900">{blocker.title}</h2>
        </div>
        <A2AStatusBadge value={blocker.code} />
      </div>
      <p className="mt-3 text-sm leading-6 text-slate-700">{blocker.explanation}</p>
      {blocker.evidence ? <p className="mt-3 break-all rounded-xl bg-white/80 px-3 py-2 text-xs text-slate-600"><strong>Evidence:</strong> {blocker.evidence}</p> : null}
      {blocker.recommendedAction ? <p className="mt-3 text-sm font-bold text-slate-800"><span className="text-slate-500">Recommended:</span> {blocker.recommendedAction}</p> : null}
      {blocker.humanDecisionRequired ? <p className="mt-3 text-xs font-black uppercase tracking-wide text-amber-800">Governed human decision required</p> : null}
    </section>
  );
}
