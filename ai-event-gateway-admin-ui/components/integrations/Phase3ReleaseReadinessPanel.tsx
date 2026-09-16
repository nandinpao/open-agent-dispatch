'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiRequest } from '@/lib/api/client';

type Gate = { gateId:string; label:string; status:string; evidence:string; blocking:boolean };
type Readiness = { phase:string; status:string; productionReady:boolean; generatedAt:string; gates:Gate[]; limitations:string[] };

export function Phase3ReleaseReadinessPanel() {
  const [value,setValue]=useState<Readiness|null>(null);
  const [error,setError]=useState('');
  const [loading,setLoading]=useState(true);
  const load=useCallback(async()=>{setLoading(true);setError('');try{setValue(await apiRequest<Readiness>('/api/integrations/phase3-release-readiness'));}catch(e){setError(e instanceof Error?e.message:'Unable to load release readiness.');}finally{setLoading(false);}},[]);
  useEffect(()=>{void load();},[load]);
  return <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="phase3-readiness-title">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div><h3 id="phase3-readiness-title" className="font-black text-slate-950">Phase 3 Release Readiness</h3><p className="mt-1 text-sm text-slate-600">Source completion and runtime/live-provider certification are reported separately.</p></div>
      <button type="button" onClick={()=>void load()} className="rounded-xl border px-3 py-2 text-sm font-bold">Refresh</button>
    </div>
    {loading?<p className="mt-4 text-sm text-slate-500">Loading certification evidence…</p>:null}
    {error?<div role="alert" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900">{error}</div>:null}
    {value?<>
      <div className={`mt-4 rounded-xl border p-4 ${value.productionReady?'border-emerald-200 bg-emerald-50':'border-amber-200 bg-amber-50'}`}>
        <div className="text-xs font-black uppercase tracking-wide">{value.phase}</div><div className="mt-1 text-lg font-black">{value.status}</div>
      </div>
      <div className="mt-4 overflow-x-auto"><table className="min-w-full border-collapse text-sm"><caption className="sr-only">Phase 3 certification gates</caption><thead><tr className="bg-slate-100"><th scope="col" className="border px-3 py-2 text-left">Gate</th><th scope="col" className="border px-3 py-2 text-left">Status</th><th scope="col" className="border px-3 py-2 text-left">Evidence</th></tr></thead><tbody>{value.gates.map(g=><tr key={g.gateId}><td className="border px-3 py-2 font-bold">{g.label}{g.blocking?' · blocking':''}</td><td className="border px-3 py-2">{g.status}</td><td className="border px-3 py-2 text-slate-600">{g.evidence}</td></tr>)}</tbody></table></div>
      {value.limitations.length?<ul className="mt-4 list-disc space-y-1 pl-5 text-sm text-slate-600">{value.limitations.map(v=><li key={v}>{v}</li>)}</ul>:null}
    </>:null}
  </section>;
}
