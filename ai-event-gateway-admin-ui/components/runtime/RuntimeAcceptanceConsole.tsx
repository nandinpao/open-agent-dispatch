'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreRuntimeAcceptanceSummary } from '@/lib/types/core';

function msg(v: unknown, fallback: string) { return v instanceof Error && v.message ? v.message : fallback; }

export function RuntimeAcceptanceConsole() {
  const { activeTenantId: tenantId } = useAuth();
  const [summary,setSummary]=useState<CoreRuntimeAcceptanceSummary>(); const [error,setError]=useState('');
  const load=useCallback(async()=>{if(!tenantId)return;try{setSummary(await coreAdminApi.getRuntimeAcceptanceSummary(tenantId));setError('');}catch(e){setError(msg(e,'Unable to load A0-R8 runtime acceptance evidence.'));}},[tenantId]);
  useEffect(()=>{void load();},[load]);
  const gate=summary?.gate;
  return <div className="space-y-5">
    <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5"><div className="text-xs font-black uppercase tracking-wide text-amber-800">A0-R8 · evidence / isolation / runtime acceptance</div><h2 className="mt-1 text-xl font-black text-slate-950">Source completion does not certify production runtime</h2><p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">This gate remains NOT_CERTIFIED until every required live isolation, crash/recovery, migration and scale scenario has append-only PASS evidence. Recording evidence here never changes Routing, Assignment, Dispatch or network authority.</p></section>
    {error&&<div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-bold text-red-800">{error}</div>}
    <section className="grid gap-4 md:grid-cols-4">{[['Gate',gate?.gate_status??'NOT_CERTIFIED'],['Required',gate?.required_count??'—'],['Passed',gate?.passed_count??0],['Blocking',gate?.blocking_count??'—']].map(([k,v])=><div key={String(k)} className="rounded-2xl border bg-white p-4 shadow-sm"><div className="text-xs font-bold uppercase text-slate-500">{k}</div><div className="mt-1 text-lg font-black">{String(v)}</div></div>)}</section>
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-center justify-between"><h3 className="font-black">Required runtime proof</h3><button onClick={()=>void load()} className="rounded-xl border px-3 py-2 text-xs font-black">Refresh</button></div><div className="mt-3 overflow-auto"><table className="min-w-full text-left text-xs"><thead><tr className="border-b"><th className="p-2">Category</th><th className="p-2">Scenario</th><th className="p-2">Result</th><th className="p-2">Evidence requirement</th><th className="p-2">Evidence</th></tr></thead><tbody>{(summary?.scenarios??[]).map(s=><tr key={s.scenario_code} className="border-b align-top"><td className="p-2 font-bold">{s.category}</td><td className="p-2 font-mono">{s.scenario_code}</td><td className="p-2 font-black">{s.result}</td><td className="max-w-xl p-2">{s.evidence_requirement}</td><td className="p-2 break-all">{s.evidence_ref??'—'}</td></tr>)}</tbody></table></div></section>
  </div>;
}
