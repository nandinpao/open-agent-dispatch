'use client';

import { ChangeEvent,useCallback,useEffect,useMemo,useState } from 'react';
import { platformGovernanceApi } from '@/lib/api/platformGovernanceApi';
import type { ApplicationPermissionManifest,ApplicationPermissionManifestEntry,PermissionCoverageEvidence,PermissionManifestDriftSummary } from '@/lib/iam/types';

export function PermissionManifestReadinessPanel({auditReason}:{auditReason:string}){
  const [manifests,setManifests]=useState<ApplicationPermissionManifest[]>([]);
  const [entries,setEntries]=useState<ApplicationPermissionManifestEntry[]>([]);
  const [evidence,setEvidence]=useState<PermissionCoverageEvidence[]>([]);
  const [drift,setDrift]=useState<PermissionManifestDriftSummary|null>(null);
  const [selectedId,setSelectedId]=useState('');
  const [driftStatus,setDriftStatus]=useState('');
  const [error,setError]=useState('');
  const [registering,setRegistering]=useState(false);
  const selected=useMemo(()=>manifests.find(x=>x.manifestId===selectedId)??manifests[0],[manifests,selectedId]);

  const load=useCallback(async()=>{
    try{
      const [m,e]=await Promise.all([platformGovernanceApi.applicationPermissionManifests(50),platformGovernanceApi.permissionCoverageEvidence(100)]);
      setManifests(m);setEvidence(e);setSelectedId(id=>id&&m.some(x=>x.manifestId===id)?id:m[0]?.manifestId??'');setError('');
    }catch(c){setError(c instanceof Error?c.message:'Unable to load Application Permission Manifests.');}
  },[]);
  useEffect(()=>{void load();},[load]);
  useEffect(()=>{if(!selected)return;void Promise.all([
    platformGovernanceApi.applicationPermissionManifestEntries(selected.manifestId,driftStatus,500),
    platformGovernanceApi.applicationPermissionManifestDrift(selected.manifestId)
  ]).then(([items,summary])=>{setEntries(items);setDrift(summary);}).catch(c=>setError(c instanceof Error?c.message:'Unable to load runtime drift.'));},[selected,driftStatus]);

  async function registerManifest(event:ChangeEvent<HTMLInputElement>){
    const file=event.target.files?.[0];event.target.value='';if(!file)return;
    if(auditReason.trim().length<12){setError('Provide an audit reason of at least 12 characters before registration.');return;}
    setRegistering(true);
    try{
      const payload=JSON.parse(await file.text()) as Record<string,unknown>;
      await platformGovernanceApi.registerApplicationPermissionManifest({...payload,environment:'default'},auditReason);
      await load();
    }catch(c){setError(c instanceof Error?c.message:'Manifest registration failed.');}
    finally{setRegistering(false);}
  }

  return <section className="space-y-4">
    {error?<div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-700">{error}</div>:null}
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border bg-white p-4">
      <div><h2 className="text-lg font-black">Application Permission Manifests</h2><p className="text-sm text-slate-600">Build descriptors are bound to one published PostgreSQL Catalog revision. Registration fails unless coverage is 100% and runtime drift is zero.</p></div>
      <label className="cursor-pointer rounded-xl bg-slate-950 px-4 py-2 text-sm font-bold text-white">{registering?'Registering…':'Register manifest JSON'}<input type="file" accept="application/json,.json" className="hidden" disabled={registering} onChange={registerManifest}/></label>
    </div>
    {selected?<div className="grid gap-3 md:grid-cols-4 xl:grid-cols-8">
      {[
        ['Coverage',`${selected.coveragePercent}%`],['Entries',selected.entryCount],['Target',selected.targetPermissionCount],['Legacy',selected.legacyAuthorityCount],
        ['Delegated',selected.delegatedCount],['Exempt',selected.exemptCount],['Drift',selected.runtimeDriftBlockers],['Status',selected.status]
      ].map(([label,value])=><div key={String(label)} className="rounded-2xl border bg-white p-4"><div className="text-xs uppercase text-slate-500">{label}</div><div className="mt-1 truncate text-xl font-black">{value}</div></div>)}
    </div>:null}
    <div className="grid gap-5 xl:grid-cols-[380px_minmax(0,1fr)]">
      <aside className="rounded-2xl border bg-white p-4"><div className="text-xs font-bold uppercase text-slate-500">Registered deployments</div><div className="mt-3 max-h-[640px] space-y-2 overflow-y-auto">{manifests.map(m=><button key={m.manifestId} onClick={()=>setSelectedId(m.manifestId)} className={`w-full rounded-xl border p-3 text-left ${selected?.manifestId===m.manifestId?'border-blue-500 bg-blue-50':'border-slate-200'}`}><div className="flex items-center justify-between gap-2"><b className="truncate">{m.buildVersion}</b><span className="text-xs font-bold">{m.status}</span></div><div className="truncate text-xs text-slate-500">{m.applicationId} · {m.environment}</div><div className="mt-1 truncate font-mono text-xs text-slate-500">{m.manifestHash}</div>{m.runtimeDriftBlockers>0?<div className="mt-1 text-xs font-bold text-rose-700">{m.runtimeDriftBlockers} runtime blockers</div>:<div className="mt-1 text-xs font-bold text-emerald-700">Runtime aligned</div>}</button>)}</div></aside>
      <div className="space-y-4">
        {selected?<section className="rounded-2xl border bg-white p-5"><div className="grid gap-3 md:grid-cols-2"><Info label="Manifest revision" value={selected.manifestRevision}/><Info label="Catalog revision" value={`${selected.catalogRevisionCode} · ${selected.catalogRevisionId}`}/><Info label="Manifest SHA-256" value={selected.manifestHash}/><Info label="Catalog SHA-256" value={selected.catalogContentHash}/><Info label="Source inventory" value={selected.sourceInventoryRevision}/><Info label="Registered" value={`${selected.registeredAt} by ${selected.registeredBy}`}/></div></section>:null}
        {drift?<section className="rounded-2xl border bg-white p-4"><div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-black">Runtime Drift</h3><select value={driftStatus} onChange={e=>setDriftStatus(e.target.value)} className="rounded-xl border p-2 text-sm"><option value="">All descriptors</option>{['MATCH','SOURCE_CHANGED','DESCRIPTOR_CHANGED','UNREGISTERED_SOURCE','STALE_MANIFEST','UNKNOWN_PERMISSION','RETIRED_PERMISSION','MISSING_RESOLVER'].map(x=><option key={x}>{x}</option>)}</select></div><div className="mt-3 grid gap-2 sm:grid-cols-3 lg:grid-cols-5">{[['Matching',drift.matching],['Source changed',drift.sourceChanged],['Descriptor changed',drift.descriptorChanged],['Unregistered',drift.unregisteredSource],['Stale',drift.staleManifest],['Blockers',drift.blockers]].map(([k,v])=><div key={String(k)} className="rounded-xl bg-slate-50 p-3"><div className="text-xs uppercase text-slate-500">{k}</div><b>{v}</b></div>)}</div></section>:null}
        <section className="overflow-hidden rounded-2xl border bg-white"><div className="max-h-[520px] overflow-auto"><table className="min-w-full text-sm"><thead className="sticky top-0 bg-slate-100 text-left text-xs uppercase text-slate-500"><tr><th className="p-3">Entry Point</th><th>Protection</th><th>Permission／Resolver</th><th>Drift</th></tr></thead><tbody>{entries.map(e=><tr key={e.entryPointId} className="border-t align-top"><td className="max-w-xl p-3"><b>{e.displayName}</b><div className="break-all font-mono text-xs text-slate-500">{e.entryPointId}</div><div className="text-xs text-slate-500">{e.ownerModule} · {e.entryPointType}</div></td><td className="p-3"><b>{e.protectionMode}</b><div className="text-xs text-slate-500">{e.coverageStatus}</div></td><td className="p-3"><div className="break-all">{e.permissionCode??'—'}</div><div className="break-all text-xs text-slate-500">{e.resourceResolverId}</div></td><td className={`p-3 font-bold ${e.blocker?'text-rose-700':'text-emerald-700'}`}>{e.driftStatus}</td></tr>)}</tbody></table></div></section>
      </div>
    </div>
    <section className="rounded-2xl border bg-white p-4"><h3 className="font-black">Immutable Coverage Evidence</h3><div className="mt-3 overflow-x-auto"><table className="min-w-full text-sm"><thead><tr className="text-left text-xs uppercase text-slate-500"><th className="p-2">Event</th><th>Coverage</th><th>Manifest</th><th>Catalog</th><th>Actor／Time</th></tr></thead><tbody>{evidence.map(x=><tr key={x.evidenceId} className="border-t"><td className="p-2 font-bold">{x.evidenceType}</td><td>{x.coveredEntryCount}/{x.entryCount} · {x.coveragePercent}%</td><td className="max-w-xs truncate font-mono text-xs">{x.manifestHash}</td><td className="max-w-xs truncate font-mono text-xs">{x.catalogContentHash}</td><td>{x.actorId}<div className="text-xs text-slate-500">{x.occurredAt}</div></td></tr>)}</tbody></table></div></section>
  </section>;
}
function Info({label,value}:{label:string;value:string}){return <div><div className="text-xs font-bold uppercase text-slate-500">{label}</div><div className="mt-1 break-all font-mono text-xs">{value}</div></div>}
