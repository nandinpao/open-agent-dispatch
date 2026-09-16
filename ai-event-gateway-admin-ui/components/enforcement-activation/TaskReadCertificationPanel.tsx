'use client';

import { FormEvent,useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { enforcementActivationApi } from '@/lib/api/enforcementActivationApi';
import type { TaskReadCertificationEvidence,TaskReadCertificationRequest,TaskReadCertificationStatus } from '@/lib/types/enforcementActivation';

const input='w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm';
const checks:[keyof TaskReadCertificationRequest,string][]=[
  ['deterministicOrderStatus','Deterministic order'],
  ['cursorPaginationStatus','Keyset cursor pagination'],
  ['nPlusOneStatus','N+1 prevention'],
  ['forceRlsStatus','FORCE RLS'],
  ['sensitiveFieldMaskingStatus','Sensitive-field masking'],
  ['fallbackPauseStatus','Fallback / Pause drill'],
  ['loadTestStatus','Load test']
];
function date(value:string){return new Date(value).toLocaleString();}
function tone(status:TaskReadCertificationStatus){return status==='PASS'?'border-emerald-200 bg-emerald-50 text-emerald-800':status==='BLOCKED'?'border-amber-200 bg-amber-50 text-amber-800':'border-rose-200 bg-rose-50 text-rose-800';}

export function TaskReadCertificationPanel(){
  const {hasPermission}=useAuth();
  const canOperate=hasPermission('permission.enforcement_wave0.operate');
  const [tenantId,setTenantId]=useState('');
  const [authorityRevision,setAuthorityRevision]=useState('');
  const [routeMode,setRouteMode]=useState<'SHADOW'|'TARGET_CANARY'>('SHADOW');
  const [statuses,setStatuses]=useState<Record<string,TaskReadCertificationStatus>>(Object.fromEntries(checks.map(([key])=>[key,'BLOCKED'])));
  const [evidenceJson,setEvidenceJson]=useState('{}');
  const [auditReason,setAuditReason]=useState('Record Phase 6C-2 Task read certification evidence.');
  const [history,setHistory]=useState<TaskReadCertificationEvidence[]>([]);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');

  async function load(){
    if(!tenantId.trim()){setError('Tenant ID is required.');return;}
    setBusy(true);setError('');
    try{setHistory(await enforcementActivationApi.taskReadCertifications(tenantId.trim(),20));}
    catch(cause){setError(cause instanceof Error?cause.message:'Unable to load Task read certifications.');}
    finally{setBusy(false);}
  }
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    const revision=Number(authorityRevision);
    if(!tenantId.trim()||!Number.isSafeInteger(revision)||revision<1){setError('Tenant ID and a positive Authority Revision are required.');return;}
    if(auditReason.trim().length<12){setError('Audit reason must contain at least 12 characters.');return;}
    try{JSON.parse(evidenceJson);}catch{setError('Evidence JSON must be valid JSON.');return;}
    const request:TaskReadCertificationRequest={tenantId:tenantId.trim(),authorityRevision:revision,routeMode,evidenceJson,
      deterministicOrderStatus:statuses.deterministicOrderStatus,
      cursorPaginationStatus:statuses.cursorPaginationStatus,
      nPlusOneStatus:statuses.nPlusOneStatus,
      forceRlsStatus:statuses.forceRlsStatus,
      sensitiveFieldMaskingStatus:statuses.sensitiveFieldMaskingStatus,
      fallbackPauseStatus:statuses.fallbackPauseStatus,
      loadTestStatus:statuses.loadTestStatus};
    setBusy(true);setError('');
    try{await enforcementActivationApi.taskReadCertify(request,auditReason.trim());await load();}
    catch(cause){setError(cause instanceof Error?cause.message:'Task read certification failed.');}
    finally{setBusy(false);}
  }

  return <section className="rounded-2xl border border-slate-200 bg-white p-5">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-black uppercase tracking-[.16em] text-indigo-700">Phase 6C-2 Certification</p><h3 className="mt-1 text-lg font-black">Task List / Search evidence</h3><p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">The server derives the overall result from the current Task pilot metrics and all seven evidence attestations. Overall PASS cannot be selected manually.</p></div><button type="button" disabled={busy} onClick={()=>void load()} className="rounded-xl border px-3 py-2 text-sm font-bold disabled:opacity-40">Load history</button></div>
    {error?<div role="alert" className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-bold text-rose-800">{error}</div>:null}
    <form onSubmit={submit} className="mt-4 space-y-4">
      <div className="grid gap-3 md:grid-cols-3"><input value={tenantId} onChange={event=>setTenantId(event.target.value)} placeholder="Tenant ID" className={input}/><input value={authorityRevision} onChange={event=>setAuthorityRevision(event.target.value)} inputMode="numeric" placeholder="Authority Revision" className={input}/><select value={routeMode} onChange={event=>setRouteMode(event.target.value as 'SHADOW'|'TARGET_CANARY')} className={input}><option value="SHADOW">SHADOW</option><option value="TARGET_CANARY">TARGET_CANARY</option></select></div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{checks.map(([key,label])=><label key={key} className="rounded-xl border border-slate-200 p-3 text-xs font-bold text-slate-700"><span className="mb-2 block">{label}</span><select value={statuses[key]??'BLOCKED'} onChange={event=>setStatuses(current=>({...current,[key]:event.target.value as TaskReadCertificationStatus}))} className={input}><option value="BLOCKED">BLOCKED</option><option value="PASS">PASS</option><option value="FAIL">FAIL</option></select></label>)}</div>
      <textarea value={evidenceJson} onChange={event=>setEvidenceJson(event.target.value)} className={`${input} min-h-28 font-mono`} aria-label="Task read certification evidence JSON"/>
      <textarea value={auditReason} onChange={event=>setAuditReason(event.target.value)} className={`${input} min-h-20`} aria-label="Task read certification audit reason"/>
      <button disabled={busy||!canOperate} className="rounded-xl bg-indigo-700 px-4 py-2.5 text-sm font-black text-white disabled:opacity-40">Derive and record certification</button>{!canOperate?<span className="ml-3 text-xs font-semibold text-slate-500">Requires permission.enforcement_wave0.operate.</span>:null}
    </form>
    <div className="mt-5 overflow-x-auto"><table className="min-w-full text-xs"><thead><tr className="text-left uppercase text-slate-500"><th className="p-2">Certified</th><th>Mode / revision</th><th>Samples</th><th>Overall</th><th>Seven checks</th><th>Certification ID</th></tr></thead><tbody>{history.map(item=><tr key={item.certificationId} className="border-t"><td className="p-2 whitespace-nowrap">{date(item.certifiedAt)}</td><td>{item.routeMode} / {item.authorityRevision}</td><td>{item.sampleCount}</td><td><span className={`rounded-full border px-2 py-1 font-black ${tone(item.overallStatus)}`}>{item.overallStatus}</span></td><td>{[item.deterministicOrderStatus,item.cursorPaginationStatus,item.nPlusOneStatus,item.forceRlsStatus,item.sensitiveFieldMaskingStatus,item.fallbackPauseStatus,item.loadTestStatus].join(' · ')}</td><td className="max-w-52 truncate font-mono" title={item.certificationId}>{item.certificationId}</td></tr>)}</tbody></table>{history.length===0?<p className="p-4 text-center text-sm text-slate-500">No certification evidence loaded.</p>:null}</div>
  </section>;
}
