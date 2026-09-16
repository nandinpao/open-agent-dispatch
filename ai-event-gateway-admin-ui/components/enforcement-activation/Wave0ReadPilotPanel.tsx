'use client';

import { FormEvent,useCallback,useEffect,useMemo,useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { HighRiskActionDialog } from '@/components/iam/HighRiskActionDialog';
import { TaskReadCertificationPanel } from '@/components/enforcement-activation/TaskReadCertificationPanel';
import { enforcementActivationApi } from '@/lib/api/enforcementActivationApi';
import type {
  ReadinessEvidenceType,
  Wave0AdminSummaryView,
  Wave0PermissionCatalogView,
  Wave0ReadPilotEntryPoint,
  Wave0ReadPilotObservation,
  Wave0ReadPilotOverview,
  Wave0ReadPilotResponse,
  Wave0ReadinessEvidenceView,
  Wave0RuntimeStatusView
} from '@/lib/types/enforcementActivation';

const input='w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm';
const entryPointLabels:Record<Wave0ReadPilotEntryPoint,string>={
  ENFORCEMENT_RUNTIME_STATUS:'Enforcement Runtime Status',
  READINESS_EVIDENCE:'Readiness Evidence',
  PERMISSION_CATALOG:'Permission Catalog',
  NON_SENSITIVE_ADMIN:'Non-sensitive Admin Summary',
  TASK_LIST_SEARCH:'Task List / Search'
};
function date(value?:string|null){return value?new Date(value).toLocaleString():'—';}
function basisPoints(value:number){return `${(value/100).toFixed(2)}%`;}
function tone(state:string){
  if(state==='OPEN'||state==='APPLIED'||state==='MATCH')return 'border-emerald-200 bg-emerald-50 text-emerald-800';
  if(state==='PAUSED'||state==='OBSERVING'||state==='NOT_COMPARED')return 'border-amber-200 bg-amber-50 text-amber-800';
  if(state==='DISABLED')return 'border-slate-200 bg-slate-50 text-slate-700';
  return 'border-rose-200 bg-rose-50 text-rose-800';
}

type PilotPayload=Wave0RuntimeStatusView|Wave0ReadinessEvidenceView|Wave0PermissionCatalogView|Wave0AdminSummaryView;
type PilotResult=Wave0ReadPilotResponse<PilotPayload>;
type GateAction={kind:'PAUSE'|'RESUME';overview:Wave0ReadPilotOverview}|null;

export function Wave0ReadPilotPanel(){
  const {hasPermission}=useAuth();
  const canOperate=hasPermission('permission.enforcement_wave0.operate');
  const [overview,setOverview]=useState<Wave0ReadPilotOverview[]>([]);
  const [selected,setSelected]=useState<Wave0ReadPilotEntryPoint>('ENFORCEMENT_RUNTIME_STATUS');
  const [observations,setObservations]=useState<Wave0ReadPilotObservation[]>([]);
  const [result,setResult]=useState<PilotResult|null>(null);
  const [evidenceType,setEvidenceType]=useState<ReadinessEvidenceType>('PHASE6_ELIGIBILITY');
  const [evidenceId,setEvidenceId]=useState('');
  const [auditReason,setAuditReason]=useState('Review Wave 0 read-only evidence before changing the pilot gate.');
  const [gateAction,setGateAction]=useState<GateAction>(null);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const current=useMemo(()=>overview.find(item=>item.policy.entryPoint===selected)??null,[overview,selected]);

  const load=useCallback(async(entryPoint:Wave0ReadPilotEntryPoint=selected)=>{
    try{
      setError('');
      const [items,history]=await Promise.all([
        enforcementActivationApi.wave0Overview(),
        enforcementActivationApi.wave0Observations(entryPoint,100)
      ]);
      setOverview(items);
      setObservations(history);
      if(!items.some(item=>item.policy.entryPoint===entryPoint)&&items[0])setSelected(items[0].policy.entryPoint);
    }catch(cause){setError(cause instanceof Error?cause.message:'Unable to load Wave 0 read pilot.');}
  },[selected]);
  useEffect(()=>{void load(selected);},[load,selected]);

  async function evaluate(){
    if(!current)return;
    if(auditReason.trim().length<12){setError('Audit reason must contain at least 12 characters.');return;}
    setBusy(true);
    try{await enforcementActivationApi.wave0Evaluate(selected,auditReason.trim());await load(selected);}
    catch(cause){setError(cause instanceof Error?cause.message:'Gate evaluation failed.');}
    finally{setBusy(false);}
  }
  async function executeGate(reason:string){
    if(!gateAction)return;
    setBusy(true);
    try{
      if(gateAction.kind==='PAUSE')await enforcementActivationApi.wave0Pause(gateAction.overview.policy.entryPoint,gateAction.overview.gate.version,reason);
      else await enforcementActivationApi.wave0Resume(gateAction.overview.policy.entryPoint,gateAction.overview.gate.version,reason);
      setGateAction(null);await load(gateAction.overview.policy.entryPoint);
    }catch(cause){setError(cause instanceof Error?cause.message:'Gate transition failed.');}
    finally{setBusy(false);}
  }
  async function runRead(operation:()=>Promise<PilotResult>){
    setBusy(true);setResult(null);
    try{setResult(await operation());await load(selected);}
    catch(cause){setError(cause instanceof Error?cause.message:'Pilot read failed.');}
    finally{setBusy(false);}
  }
  function readiness(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    if(!evidenceId.trim()){setError('Evidence UUID is required.');return;}
    setSelected('READINESS_EVIDENCE');
    void runRead(()=>enforcementActivationApi.wave0ReadinessRead(evidenceType,evidenceId.trim()) as Promise<PilotResult>);
  }

  return <section className="space-y-5 rounded-3xl border border-indigo-200 bg-indigo-50/50 p-5">
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div><p className="text-xs font-black uppercase tracking-[.18em] text-indigo-700">Phase 6C-2 · Wave 0</p><h2 className="mt-1 text-2xl font-black text-slate-950">Read-only Pilot Control</h2><p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Shadow and canary reads are independently observed. Legacy remains authoritative unless an eligible TARGET_CANARY route, an OPEN gate and the runtime execution property all agree.</p></div>
      <button type="button" onClick={()=>void load(selected)} className="rounded-xl border border-indigo-200 bg-white px-3 py-2 text-sm font-bold text-indigo-800">Refresh</button>
    </header>
    {error?<div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-bold text-rose-800">{error}</div>:null}
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">{overview.map(item=>{
      const active=item.policy.entryPoint===selected;
      return <button type="button" key={item.policy.entryPoint} onClick={()=>{setSelected(item.policy.entryPoint);setResult(null);}} className={`rounded-2xl border p-4 text-left ${active?'border-indigo-500 bg-white ring-2 ring-indigo-100':'border-slate-200 bg-white/80'}`}>
        <div className="flex items-start justify-between gap-2"><span className="text-sm font-black text-slate-950">{entryPointLabels[item.policy.entryPoint]}</span><span className={`rounded-full border px-2 py-1 text-xs font-black ${tone(item.gate.state)}`}>{item.gate.state}</span></div>
        <div className="mt-3 text-xs text-slate-600">Samples {item.metrics.sampleCount} · Mismatch {basisPoints(item.metrics.mismatchBasisPoints)}</div>
        <div className="mt-1 text-xs text-slate-600">Target errors {basisPoints(item.metrics.targetErrorBasisPoints)} · P95 {item.metrics.targetP95Millis} ms</div>
        <div className={`mt-3 rounded-lg p-2 text-xs font-semibold ${item.executable?'bg-emerald-50 text-emerald-800':'bg-slate-100 text-slate-700'}`}>{item.executable?'Executable':'Blocked'}{item.blockingReason?` · ${item.blockingReason}`:''}</div>
      </button>})}</div>
    {current?<div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
      <div className="rounded-2xl border border-slate-200 bg-white p-5">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-black uppercase text-slate-500">Selected entry point</p><h3 className="mt-1 text-lg font-black">{entryPointLabels[selected]}</h3><p className="mt-1 font-mono text-xs text-slate-500">{selected}</p></div><span className={`rounded-full border px-3 py-1 text-xs font-black ${tone(current.gate.state)}`}>{current.gate.state} · v{current.gate.version}</span></div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><Metric label="Samples" value={String(current.metrics.sampleCount)}/><Metric label="Mismatch" value={basisPoints(current.metrics.mismatchBasisPoints)}/><Metric label="Target errors" value={basisPoints(current.metrics.targetErrorBasisPoints)}/><Metric label="Target P95" value={`${current.metrics.targetP95Millis} ms`}/></div>
        <dl className="mt-4 grid gap-2 text-xs text-slate-600 md:grid-cols-2"><div><dt className="font-black uppercase text-slate-500">Policy limits</dt><dd>Minimum {current.policy.minimumSamples} · mismatch ≤ {basisPoints(current.policy.maximumMismatchBasisPoints)} · target errors ≤ {basisPoints(current.policy.maximumTargetErrorBasisPoints)} · P95 ≤ {current.policy.maximumTargetP95Millis} ms</dd></div><div><dt className="font-black uppercase text-slate-500">Execution boundary</dt><dd>Property {current.executionPropertyEnabled?'enabled':'disabled'} · route {current.routeEligible?'eligible':'not eligible'} · policy {current.policy.enabled?'enabled':'disabled'} · auto-pause {current.policy.autoPause?'enabled':'disabled'}</dd></div></dl>
        <p className={`mt-4 rounded-xl border p-3 text-sm font-semibold ${current.executable?'border-emerald-200 bg-emerald-50 text-emerald-800':'border-amber-200 bg-amber-50 text-amber-800'}`}>{current.executable?'This entry point may execute a governed read pilot.':`Pilot execution remains blocked${current.blockingReason?`: ${current.blockingReason}`:'.'}`}</p>
      </div>
      <div className="rounded-2xl border border-slate-200 bg-white p-5"><h3 className="font-black">Gate operations</h3><textarea value={auditReason} onChange={event=>setAuditReason(event.target.value)} className={`${input} mt-3 min-h-24`} aria-label="Wave 0 audit reason"/><div className="mt-3 grid grid-cols-2 gap-2"><button type="button" disabled={busy||!canOperate} onClick={()=>void evaluate()} className="rounded-xl border border-indigo-200 px-3 py-2 text-sm font-bold text-indigo-800 disabled:opacity-40">Evaluate</button>{current.gate.state==='PAUSED'||current.gate.state==='DISABLED'||current.gate.state==='BLOCKED'?<button type="button" disabled={busy||!canOperate||!current.policy.enabled} onClick={()=>setGateAction({kind:'RESUME',overview:current})} className="rounded-xl bg-emerald-700 px-3 py-2 text-sm font-bold text-white disabled:opacity-40">Resume</button>:<button type="button" disabled={busy||!canOperate} onClick={()=>setGateAction({kind:'PAUSE',overview:current})} className="rounded-xl bg-rose-700 px-3 py-2 text-sm font-bold text-white disabled:opacity-40">Pause</button>}</div>{!canOperate?<p className="mt-3 text-xs font-semibold text-slate-500">Read-only access. Gate operations require permission.enforcement_wave0.operate.</p>:null}</div>
    </div>:null}
    <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]"><div className="rounded-2xl border border-slate-200 bg-white p-5"><h3 className="font-black">Execute governed read</h3><p className="mt-1 text-xs leading-5 text-slate-500">The response declares the selected authority plane, actual serving source, fallback state and immutable observation ID.</p><div className="mt-4 grid gap-2"><button type="button" disabled={busy} onClick={()=>{setSelected('ENFORCEMENT_RUNTIME_STATUS');void runRead(()=>enforcementActivationApi.wave0RuntimeRead() as Promise<PilotResult>);}} className="rounded-xl border px-3 py-2 text-left text-sm font-bold">Read Runtime Status</button><button type="button" disabled={busy} onClick={()=>{setSelected('PERMISSION_CATALOG');void runRead(()=>enforcementActivationApi.wave0PermissionCatalogRead() as Promise<PilotResult>);}} className="rounded-xl border px-3 py-2 text-left text-sm font-bold">Read Permission Catalog</button><button type="button" disabled={busy} onClick={()=>{setSelected('NON_SENSITIVE_ADMIN');void runRead(()=>enforcementActivationApi.wave0AdminSummaryRead() as Promise<PilotResult>);}} className="rounded-xl border px-3 py-2 text-left text-sm font-bold">Read Non-sensitive Admin Summary</button></div><form onSubmit={readiness} className="mt-4 space-y-2 border-t pt-4"><select value={evidenceType} onChange={event=>setEvidenceType(event.target.value as ReadinessEvidenceType)} className={input}><option>PHASE6_ELIGIBILITY</option><option>DOMAIN_READINESS</option><option>RUNTIME_CERTIFICATION</option></select><input value={evidenceId} onChange={event=>setEvidenceId(event.target.value)} placeholder="Immutable evidence UUID" className={input}/><button disabled={busy} className="w-full rounded-xl bg-slate-950 px-3 py-2 text-sm font-bold text-white">Read Readiness Evidence</button></form><div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600"><b>Task List / Search:</b> Phase 6C-2 connects the governed adapter to <code>/api/tasks</code>. It remains inactive until both runtime properties are enabled, the Gate is resumed to OBSERVING, and a SHADOW or TARGET_CANARY Authority Route is published.</div></div>
      <div className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5"><h3 className="font-black">Pilot response evidence</h3>{result?<><div className="mt-3 grid gap-3 md:grid-cols-3"><Metric label="Authority" value={`${result.authorityDecision.mode} / ${result.authorityDecision.plane}`}/><Metric label="Served by" value={`${result.servedBy}${result.fallbackUsed?' · FALLBACK':''}`}/><Metric label="Observation" value={result.observationId??'Not recorded'}/></div><div className="mt-3 rounded-xl bg-slate-950 p-3 text-xs text-slate-100"><div>Revision {result.authorityDecision.revision} · Gate {result.gateState} · Shadow {result.shadowCompared?'yes':'no'}</div><div className="mt-1 break-all">Reason {result.authorityDecision.reasonCode} · Cohort bucket {result.authorityDecision.cohortBucketBasisPoints}</div></div><pre className="mt-3 max-h-96 overflow-auto rounded-xl bg-slate-950 p-4 text-xs text-emerald-200">{JSON.stringify(result.payload,null,2)}</pre></>:<p className="mt-4 rounded-xl bg-slate-50 p-5 text-sm text-slate-500">Run one of the governed reads to inspect authority and observation evidence.</p>}</div></div>
    <div className="rounded-2xl border border-slate-200 bg-white p-5"><div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-black">Recent immutable observations · {entryPointLabels[selected]}</h3><span className="text-xs text-slate-500">Newest 100</span></div><div className="mt-3 overflow-x-auto"><table className="min-w-full text-xs"><thead><tr className="text-left uppercase text-slate-500"><th className="p-2">Observed</th><th>Mode / plane</th><th>Served</th><th>Comparison</th><th>Latency µs</th><th>Revision</th><th>Observation ID</th></tr></thead><tbody>{observations.map(item=><tr key={item.observationId} className="border-t"><td className="p-2 whitespace-nowrap">{date(item.observedAt)}</td><td>{item.authorityMode} / {item.selectedPlane}</td><td>{item.servedBy}{item.fallbackUsed?' · fallback':''}</td><td><span className={`rounded-full border px-2 py-1 font-bold ${tone(item.mismatchCategory)}`}>{item.mismatchCategory}</span></td><td>{item.legacyDurationMicros} / {item.targetDurationMicros}</td><td>{item.authorityRevision}</td><td className="max-w-56 truncate font-mono" title={item.observationId}>{item.observationId}</td></tr>)}</tbody></table>{observations.length===0?<p className="p-5 text-center text-sm text-slate-500">No observations recorded for this entry point.</p>:null}</div></div>
    <TaskReadCertificationPanel/>
    <HighRiskActionDialog open={gateAction!==null} title={`${gateAction?.kind??''} Wave 0 Gate`} description="This changes whether the selected read entry point may execute Shadow or TARGET_CANARY traffic. Legacy fallback and Hard Security Guards remain active." confirmation={gateAction?.kind==='PAUSE'?'PAUSE_WAVE0_GATE':'RESUME_WAVE0_GATE'} running={busy} onCancel={()=>setGateAction(null)} onConfirm={reason=>void executeGate(reason)}/>
  </section>;
}

function Metric({label,value}:{label:string;value:string}){return <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div><div className="mt-1 break-all text-sm font-black text-slate-900">{value}</div></div>;}
