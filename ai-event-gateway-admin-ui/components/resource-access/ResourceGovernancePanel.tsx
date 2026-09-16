'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ResourceAccessBadge } from './ResourceAccessBadge';
import { explainDecision, getResourceOverview, type AuthorizationDecision, type ResourceGovernanceOverview, type VisibilityLevel } from '@/lib/api/domains/resourceAccessApi';
import { useRuntimeCapabilities } from '@/components/providers/RuntimeCapabilityProvider';
import { runtimeSurfaceAvailable } from '@/lib/runtime-capability/contracts';
import { RightDrawer } from '@/components/ui/RightDrawer';

function ownerLabel(overview: ResourceGovernanceOverview): string {
  if (overview.ownerDepartmentId) return 'Department-owned resource';
  if (overview.ownerGroupId) return 'Group-owned resource';
  return overview.securityState === 'ORPHANED' ? 'Ownership needs remediation' : 'Tenant-owned resource';
}

export function ResourceGovernancePanel({resourceType,resourceId,permissionCode,requestedVisibility='STANDARD',compact=false}:Readonly<{resourceType:string;resourceId:string;permissionCode:string;requestedVisibility?:VisibilityLevel;compact?:boolean}>) {
  const {capabilities}=useRuntimeCapabilities();
  const enabled=runtimeSurfaceAvailable(capabilities.surfaces.resourceAccessAdministration);
  const [overview,setOverview]=useState<ResourceGovernanceOverview|null>(null);
  const [error,setError]=useState('');
  const [drawer,setDrawer]=useState(false);

  useEffect(()=>{
    if(!enabled||!resourceId)return;
    getResourceOverview(resourceType,resourceId).then(setOverview).catch(e=>setError(e instanceof Error?e.message:'Ownership information is unavailable.'));
  },[enabled,resourceId,resourceType]);

  if(!enabled||!resourceId)return null;
  if(error)return <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"><b>Ownership & Access:</b> {error}</section>;
  if(!overview)return <section className="rounded-2xl border border-slate-200 bg-white p-4 text-sm font-semibold text-slate-500">Loading ownership and access…</section>;

  return <>
    <section className={`rounded-2xl border border-violet-200 bg-violet-50 ${compact?'p-3':'p-4'}`}>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-violet-700">Ownership & Access</div>
          <div className="mt-1 flex flex-wrap items-center gap-2"><h3 className="font-black text-slate-950">{ownerLabel(overview)}</h3><ResourceAccessBadge value={overview.maximumVisibility}/>{overview.securityState!=='ACTIVE'?<ResourceAccessBadge value={overview.securityState}/>:null}</div>
          <p className="mt-2 text-sm leading-6 text-slate-600">OpenDispatch combines your Responsibility, Permission and this resource’s organizational scope. Being a Department or Group member by itself does not grant access.</p>
          {!compact ? <div className="mt-2 flex flex-wrap gap-2 text-xs font-bold text-violet-900"><span className="rounded-full bg-white px-2.5 py-1">{overview.participants.length} operational participants</span><span className="rounded-full bg-white px-2.5 py-1">{overview.activeGrantCount} explicit grants</span>{overview.activeDenyCount?<span className="rounded-full bg-white px-2.5 py-1">{overview.activeDenyCount} explicit denies</span>:null}</div> : null}
        </div>
        <div className="flex shrink-0 flex-wrap gap-2"><button type="button" onClick={()=>setDrawer(true)} className="rounded-xl border border-violet-200 bg-white px-3 py-2 text-xs font-black text-violet-800 hover:bg-violet-100">Why can I access this?</button><Link href="/access-management?workspace=access" className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">Review access</Link></div>
      </div>
    </section>
    {drawer?<AuthorizationExplanationDrawer resourceType={resourceType} resourceId={resourceId} permissionCode={permissionCode} requestedVisibility={requestedVisibility} overview={overview} onClose={()=>setDrawer(false)}/>:null}
  </>;
}

function AuthorizationExplanationDrawer({resourceType,resourceId,permissionCode,requestedVisibility,overview,onClose}:Readonly<{resourceType:string;resourceId:string;permissionCode:string;requestedVisibility:VisibilityLevel;overview:ResourceGovernanceOverview;onClose:()=>void}>) {
  const [decision,setDecision]=useState<AuthorizationDecision|null>(null);
  const [error,setError]=useState('');
  useEffect(()=>{explainDecision({permissionCode,resourceType,resourceId,actionKind:'READ',sideEffecting:false,requestedVisibility,purpose:'WHY_CAN_I_ACCESS'}).then(setDecision).catch(e=>setError(e instanceof Error?e.message:'Access explanation is unavailable.'))},[permissionCode,requestedVisibility,resourceId,resourceType]);
  return <RightDrawer open onClose={onClose} title="Why can I access this?" description="This explanation uses the same Resource Access policy model as the business operation. IDs stay hidden unless support details are opened." widthClassName="max-w-2xl">
    <div className="space-y-5">
      <section className="grid gap-3 sm:grid-cols-2"><Fact label="Primary ownership" value={ownerLabel(overview)}/><Fact label="Maximum visibility" value={overview.maximumVisibility}/><Fact label="Sensitivity" value={overview.sensitivityLevel}/><Fact label="Security state" value={overview.securityState}/></section>
      {error?<div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">{error}</div>:!decision?<div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">Evaluating your current Responsibility and resource scope…</div>:<>
        <section className={`rounded-2xl border p-4 ${decision.effect==='ALLOW'?'border-emerald-200 bg-emerald-50':'border-rose-200 bg-rose-50'}`}><div className="flex flex-wrap items-center gap-2"><ResourceAccessBadge value={decision.effect}/><ResourceAccessBadge value={decision.grantedVisibility}/></div><p className="mt-2 text-sm leading-6">OpenDispatch evaluated <b>{decision.permissionCode}</b> for your current session and this resource.</p></section>
        <section><h3 className="text-sm font-black text-slate-900">What matched</h3><div className="mt-2 grid gap-2 sm:grid-cols-2"><FriendlyEvidence label="Responsibility / Role Binding" count={decision.matchedRoleBindingIds.length}/><FriendlyEvidence label="Organizational scope" count={decision.matchedScopeSources.length}/><FriendlyEvidence label="Operational participant" count={decision.matchedParticipantIds.length}/><FriendlyEvidence label="Explicit deny" count={decision.matchedDenyIds.length}/></div></section>
        {decision.reasons.length?<section><h3 className="text-sm font-black text-slate-900">Decision explanation</h3><div className="mt-2 space-y-2">{decision.reasons.map(r=><div key={`${r.code}-${r.safeMessage}`} className="rounded-xl border border-slate-200 bg-slate-50 p-3"><p className="text-sm text-slate-700">{r.safeMessage}</p><div className="mt-1 text-xs font-semibold text-slate-500">{r.category}</div></div>)}</div></section>:null}
        <details className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600"><summary className="cursor-pointer font-black text-slate-800">Technical details for support</summary><div className="mt-3 space-y-3"><div><b>Resource:</b> {resourceType} / {resourceId}</div><Evidence label="Matched role binding IDs" values={decision.matchedRoleBindingIds}/><Evidence label="Matched scope sources" values={decision.matchedScopeSources}/><Evidence label="Matched participants" values={decision.matchedParticipantIds}/><Evidence label="Ownership evidence" values={decision.matchedOwnershipEvidence}/><Evidence label="Matched explicit denies" values={decision.matchedDenyIds}/><div><b>Decision ID:</b> {decision.decisionId}</div></div></details>
      </>}
      <p className="rounded-xl border border-blue-200 bg-blue-50 p-3 text-xs font-semibold leading-5 text-blue-900">This panel explains access; it does not grant access. Any mutation performs a fresh authorization check.</p>
    </div>
  </RightDrawer>;
}

function Fact({label,value}:{label:string;value:string}){return <div className="rounded-xl border border-slate-200 bg-slate-50 p-3"><div className="text-xs font-black text-slate-500">{label}</div><div className="mt-1 break-all text-sm font-black text-slate-900">{value}</div></div>}
function FriendlyEvidence({label,count}:{label:string;count:number}){return <div className="rounded-xl border border-slate-200 bg-white p-3"><div className="text-sm font-black text-slate-800">{label}</div><div className="mt-1 text-xs text-slate-500">{count>0?`${count} matching evidence record${count===1?'':'s'}`:'No matching evidence'}</div></div>}
function Evidence({label,values}:{label:string;values:string[]}){return <section><h3 className="text-xs font-black text-slate-700">{label}</h3><div className="mt-1 flex flex-wrap gap-1.5">{values.length?values.map(v=><span key={v} className="rounded-full border border-slate-200 bg-white px-2 py-1 text-xs font-semibold text-slate-600">{v}</span>):<span className="text-xs text-slate-500">None</span>}</div></section>}
