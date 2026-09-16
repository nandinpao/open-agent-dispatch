'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  deprecateProjectMapping,
  diffProjectMapping,
  forkProjectMapping,
  listProjectMappings,
  listProjectMappingVersions,
  listProviderMetadataSnapshots,
  previewProjectMapping,
  probeProviderMetadata,
  publishProjectMapping,
  rollbackProjectMapping,
  saveProjectMapping,
  validateProjectMapping,
  type IntegrationProjectMapping,
  type IntegrationProjectMappingVersion,
  type ProjectMappingDiff,
  type ProjectMappingPreview,
  type ProjectMappingValidationResult,
  type ProviderMetadataSnapshot,
} from '@/lib/api/domains/integrationIdentityApi';
import { listPrincipals, type IntegrationPrincipal } from '@/lib/api/domains/integrationIdentityApi';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { taskContractAdminApi } from '@/lib/api/domains/taskContractAdminApi';
import type { CoreDispatchTaskDefinition, CoreSourceSystem } from '@/lib/types/core';

const emptyMapping = (connectionId=''):IntegrationProjectMapping => ({
  mappingId:'', connectionId, externalProjectId:'', externalProjectKey:'', externalIssueType:'', mappingStatus:'DRAFT',
  lifecycleStatus:'DRAFT', mappingVersion:1, summaryTemplate:'{{task.title}}', descriptionTemplate:'{{task.description}}',
  requiredFields:[], customFieldMappings:{}, transitionMappings:{}, commentPolicy:'APPEND_ONLY', linkPolicy:'CANONICAL_ONLY',
  resolutionPriority:1000, defaultMapping:false, enabled:false,
});
function split(v:string){return v.split(',').map(x=>x.trim()).filter(Boolean);}
function jsonMap(v:string){if(!v.trim())return {};const parsed=JSON.parse(v);if(!parsed||Array.isArray(parsed)||typeof parsed!=='object')throw new Error('Expected a JSON object.');return parsed as Record<string,string>;}
function errorText(error:unknown){return error instanceof Error?error.message:'The operation failed.';}

export function ProjectMappingGovernancePanel({connectionId}:{connectionId:string}){
  const { activeTenantId } = useAuth();
  const [mappings,setMappings]=useState<IntegrationProjectMapping[]>([]);
  const [selectedId,setSelectedId]=useState('');
  const [draft,setDraft]=useState<IntegrationProjectMapping>(emptyMapping(connectionId));
  const [snapshots,setSnapshots]=useState<ProviderMetadataSnapshot[]>([]);
  const [versions,setVersions]=useState<IntegrationProjectMappingVersion[]>([]);
  const [validation,setValidation]=useState<ProjectMappingValidationResult|null>(null);
  const [preview,setPreview]=useState<ProjectMappingPreview|null>(null);
  const [diff,setDiff]=useState<ProjectMappingDiff|null>(null);
  const [contextText,setContextText]=useState('{"task.title":"Example incident","task.description":"Generated preview context"}');
  const [customText,setCustomText]=useState('{}');
  const [transitionText,setTransitionText]=useState('{}');
  const [newMappingId,setNewMappingId]=useState('');
  const [fromVersion,setFromVersion]=useState(1);
  const [toVersion,setToVersion]=useState(1);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');
  const [principals,setPrincipals]=useState<IntegrationPrincipal[]>([]);
  const [sourceSystems,setSourceSystems]=useState<CoreSourceSystem[]>([]);
  const [taskDefinitions,setTaskDefinitions]=useState<CoreDispatchTaskDefinition[]>([]);

  const activeSnapshot=snapshots[0];
  const serviceAccounts=useMemo(()=>principals.filter((value)=>value.principalType==='SERVICE_ACCOUNT'),[principals]);
  const taskOptions=useMemo(()=>taskDefinitions.filter((value)=>!draft.sourceSystemId||String(value.sourceSystem??'').toUpperCase()===String(draft.sourceSystemId).toUpperCase()),[taskDefinitions,draft.sourceSystemId]);
  const selectedPrincipalId=draft.readPrincipalId??draft.createPrincipalId??draft.commentPrincipalId??draft.updatePrincipalId??'';
  const refresh=useCallback(async (preferred?:string)=>{
    if(!connectionId){setMappings([]);setSelectedId('');return;}
    const values=await listProjectMappings(connectionId);setMappings(values);const next=preferred??values[0]?.mappingId??'';setSelectedId(next);
  },[connectionId]);
  const refreshDetail=useCallback(async (mappingId:string)=>{
    if(!mappingId){setSnapshots([]);setVersions([]);return;}
    const [s,v]=await Promise.all([listProviderMetadataSnapshots(mappingId),listProjectMappingVersions(mappingId)]);setSnapshots(s);setVersions(v);
    if(v.length){setFromVersion(v.at(-1)?.mappingVersion??1);setToVersion(v[0].mappingVersion);}
  },[]);
  async function perform(action:()=>Promise<void>,success:string){setBusy(true);setMessage('');try{await action();setMessage(success);}catch(e){setMessage(errorText(e));}finally{setBusy(false);}}

  useEffect(()=>{setDraft(emptyMapping(connectionId));setSelectedId('');setValidation(null);setPreview(null);setDiff(null);refresh().catch(e=>setMessage(errorText(e)));},[connectionId,refresh]);
  useEffect(()=>{if(!connectionId){setPrincipals([]);return;}listPrincipals(connectionId).then(setPrincipals).catch(e=>setMessage(errorText(e)));},[connectionId]);
  useEffect(()=>{if(!activeTenantId){setSourceSystems([]);setTaskDefinitions([]);return;}Promise.all([coreAdminApi.getSourceSystems(activeTenantId),taskContractAdminApi.getDispatchTaskDefinitions('ACTIVE',activeTenantId)]).then(([sources,definitions])=>{setSourceSystems(sources);setTaskDefinitions(definitions);}).catch(e=>setMessage(errorText(e)));},[activeTenantId]);
  useEffect(()=>{if(!selectedId)return;const value=mappings.find(v=>v.mappingId===selectedId);if(value){setDraft(value);setCustomText(JSON.stringify(value.customFieldMappings??{},null,2));setTransitionText(JSON.stringify(value.transitionMappings??{},null,2));}refreshDetail(selectedId).catch(e=>setMessage(errorText(e)));},[selectedId,mappings,refreshDetail]);

  const immutable=draft.lifecycleStatus==='ACTIVE'||draft.lifecycleStatus==='DEPRECATED';
  return <section className="rounded-2xl border border-violet-200 bg-violet-50 p-5">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="text-lg font-black text-violet-950">Source System → Redmine Project Routing</h3><p className="mt-1 text-sm leading-6 text-violet-900">Route OpenDispatch Task context to a Redmine project. Mapping selects the destination; Redmine decides project roles, workflow and issue permissions.</p></div><button type="button" disabled={!connectionId} onClick={()=>{setSelectedId('');setDraft(emptyMapping(connectionId));setCustomText('{}');setTransitionText('{}');}} className="rounded-lg border border-violet-300 bg-white px-3 py-2 text-sm font-bold disabled:opacity-50">New Draft</button></div>
    {message?<div role="status" className="mt-4 rounded-xl border border-violet-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700">{message}</div>:null}
    <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(18rem,0.8fr)_minmax(0,1.2fr)]">
      <div className="space-y-4">
        <label className="block text-sm font-bold text-violet-950">Mapping<select value={selectedId} onChange={e=>setSelectedId(e.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2"><option value="">New mapping</option>{mappings.map(v=><option key={v.mappingId} value={v.mappingId}>{v.mappingId} · v{v.mappingVersion} · {v.lifecycleStatus}</option>)}</select></label>
        <div className="rounded-xl border border-violet-200 bg-white p-4 text-sm"><div className="grid grid-cols-2 gap-3"><Metric label="Lifecycle" value={draft.lifecycleStatus??'DRAFT'}/><Metric label="Mapping version" value={`v${draft.mappingVersion??1}`}/><Metric label="Metadata" value={draft.metadataSnapshotId?'Bound':'Not bound'}/><Metric label="Schema" value={draft.metadataSchemaHash?draft.metadataSchemaHash.slice(0,12):'—'}/></div></div>
        {activeSnapshot?<div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm"><div className="font-black text-emerald-950">Latest Metadata Snapshot</div><div className="mt-2 grid grid-cols-2 gap-2 text-emerald-900"><span>v{activeSnapshot.metadataVersion}</span><span>{activeSnapshot.cacheStatus}</span><span>{activeSnapshot.projects.length} projects</span><span>{activeSnapshot.fields.length} fields</span><span>{activeSnapshot.issueTypes.length} issue types</span><span>{activeSnapshot.transitions.length} transitions</span></div><p className="mt-2 text-xs">Expires {activeSnapshot.expiresAt}</p></div>:null}
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="text-sm font-bold text-slate-700">Source System<select disabled={immutable} value={draft.sourceSystemId??''} onChange={e=>setDraft({...draft,sourceSystemId:e.target.value||null,taskType:null})} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Any Source System</option>{sourceSystems.map((value)=><option key={value.sourceSystemId} value={value.sourceSystemId}>{value.displayName||value.sourceSystemId}</option>)}</select></label>
        <label className="text-sm font-bold text-slate-700">Task Type<select disabled={immutable} value={draft.taskType??''} onChange={e=>setDraft({...draft,taskType:e.target.value||null})} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">All task types</option>{taskOptions.map((value)=><option key={value.taskType} value={value.taskType}>{value.displayName||value.taskType}</option>)}</select></label>
        <label className="md:col-span-2 text-sm font-bold text-slate-700">Technical Service Account<select disabled={immutable||!connectionId} value={selectedPrincipalId} onChange={e=>setDraft({...draft,readPrincipalId:e.target.value||null,createPrincipalId:null,commentPrincipalId:null,updatePrincipalId:null,relationPrincipalId:null,webhookPrincipalId:null})} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Resolve the only active Service Account on this connection</option>{serviceAccounts.map((value)=><option key={value.principalId} value={value.principalId}>{value.principalName} · {value.status}</option>)}</select><span className="mt-1 block text-xs font-medium leading-5 text-slate-500">Canonical connector runtime uses one technical Service Account. Legacy per-operation Principal slots are not configured by this UI; Redmine decides READ / CREATE / COMMENT / UPDATE permissions.</span></label>
        <Field label="Mapping ID" value={draft.mappingId} disabled={Boolean(selectedId)} onChange={v=>setDraft({...draft,mappingId:v,connectionId})}/>
        {activeSnapshot?.projects?.length ? <label className="text-sm font-bold text-slate-700">Redmine Project<select disabled={immutable} value={draft.externalProjectId} onChange={e=>{const project=activeSnapshot.projects.find((value)=>value.projectId===e.target.value);setDraft({...draft,externalProjectId:e.target.value,externalProjectKey:project?.projectKey??null});}} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Select project</option>{activeSnapshot.projects.filter((value)=>value.accessible).map((value)=><option key={value.projectId} value={value.projectId}>{value.displayName||value.projectKey||value.projectId}</option>)}</select></label>:<Field label="Provider Project ID" value={draft.externalProjectId} disabled={immutable} onChange={v=>setDraft({...draft,externalProjectId:v})}/>} 
        <Field label="Provider Project Key" value={draft.externalProjectKey??''} disabled={immutable} onChange={v=>setDraft({...draft,externalProjectKey:v})}/>
        {activeSnapshot?.issueTypes?.length ? <label className="text-sm font-bold text-slate-700">Issue Type / Tracker<select disabled={immutable} value={draft.externalIssueType??''} onChange={e=>setDraft({...draft,externalIssueType:e.target.value,externalTrackerId:e.target.value})} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Select tracker</option>{activeSnapshot.issueTypes.map((value)=><option key={value.issueTypeId} value={value.issueTypeId}>{value.displayName||value.issueTypeKey||value.issueTypeId}</option>)}</select></label>:<Field label="Issue Type / Tracker" value={draft.externalIssueType??''} disabled={immutable} onChange={v=>setDraft({...draft,externalIssueType:v,externalTrackerId:v})}/>} 
        <Field label="Required fields" value={(draft.requiredFields??[]).join(', ')} disabled={immutable} onChange={v=>setDraft({...draft,requiredFields:split(v)})}/>
        <label className="md:col-span-2 text-sm font-bold text-slate-700">Summary template<textarea disabled={immutable} value={draft.summaryTemplate??''} onChange={e=>setDraft({...draft,summaryTemplate:e.target.value})} className="mt-1 min-h-20 w-full rounded-xl border px-3 py-2 font-mono text-sm font-normal"/></label>
        <label className="md:col-span-2 text-sm font-bold text-slate-700">Description template<textarea disabled={immutable} value={draft.descriptionTemplate??''} onChange={e=>setDraft({...draft,descriptionTemplate:e.target.value})} className="mt-1 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-sm font-normal"/></label>
        <label className="text-sm font-bold text-slate-700">Custom field mapping JSON<textarea disabled={immutable} value={customText} onChange={e=>setCustomText(e.target.value)} className="mt-1 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-xs font-normal"/></label>
        <label className="text-sm font-bold text-slate-700">Transition mapping JSON<textarea disabled={immutable} value={transitionText} onChange={e=>setTransitionText(e.target.value)} className="mt-1 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-xs font-normal"/></label>
        <label className="text-sm font-bold text-slate-700">Comment policy<select disabled={immutable} value={draft.commentPolicy??'APPEND_ONLY'} onChange={e=>setDraft({...draft,commentPolicy:e.target.value})} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option>APPEND_ONLY</option><option>DISABLED</option><option>STATUS_CHANGES_ONLY</option></select></label>
        <label className="text-sm font-bold text-slate-700">Link policy<select disabled={immutable} value={draft.linkPolicy??'CANONICAL_ONLY'} onChange={e=>setDraft({...draft,linkPolicy:e.target.value})} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option>CANONICAL_ONLY</option><option>PROVIDER_NATIVE_OPTIONAL</option><option>PROVIDER_NATIVE_REQUIRED</option></select></label>
      </div>
    </div>
    <div className="mt-5 flex flex-wrap gap-2">
      <button disabled={busy||immutable||!draft.mappingId||!draft.externalProjectId} onClick={()=>perform(async()=>{const saved=await saveProjectMapping({...draft,connectionId,customFieldMappings:jsonMap(customText),transitionMappings:jsonMap(transitionText)});await refresh(saved.mappingId);},'Draft saved.')} className="rounded-lg bg-violet-800 px-3 py-2 text-sm font-black text-white disabled:opacity-50">Save Draft</button>
      <button disabled={busy||!selectedId||immutable} onClick={()=>perform(async()=>{await probeProviderMetadata(selectedId,undefined,true);await refreshDetail(selectedId);},'Redmine project metadata refreshed using the configured technical Service Account.')} className="rounded-lg border bg-white px-3 py-2 text-sm font-bold disabled:opacity-50">Test Project Visibility</button>
      <button disabled={busy||!selectedId||immutable} onClick={()=>perform(async()=>{setValidation(await validateProjectMapping(selectedId));await refresh(selectedId);},'Mapping validation completed.')} className="rounded-lg border bg-white px-3 py-2 text-sm font-bold disabled:opacity-50">Validate</button>
      <button disabled={busy||draft.lifecycleStatus!=='VALID'} onClick={()=>perform(async()=>{await publishProjectMapping(selectedId);await refresh(selectedId);await refreshDetail(selectedId);},'Mapping published as immutable version.')} className="rounded-lg bg-emerald-700 px-3 py-2 text-sm font-black text-white disabled:opacity-50">Publish</button>
      <button disabled={busy||draft.lifecycleStatus!=='ACTIVE'} onClick={()=>perform(async()=>{await deprecateProjectMapping(selectedId);await refresh(selectedId);},'Mapping deprecated.')} className="rounded-lg border border-amber-400 bg-amber-50 px-3 py-2 text-sm font-bold disabled:opacity-50">Deprecate</button>
    </div>
    {validation?<div className={`mt-4 rounded-xl border p-4 text-sm ${validation.valid?'border-emerald-200 bg-emerald-50':'border-red-200 bg-red-50'}`}><div className="font-black">{validation.valid?'Validation passed':'Validation failed'}</div>{validation.errors.map(v=><div key={v} className="mt-1">{v}</div>)}{validation.warnings.map(v=><div key={v} className="mt-1 text-amber-800">{v}</div>)}</div>:null}
    <div className="mt-5 grid gap-5 xl:grid-cols-2">
      <div className="rounded-xl border border-violet-200 bg-white p-4"><h4 className="font-black">Preview</h4><textarea value={contextText} onChange={e=>setContextText(e.target.value)} className="mt-3 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-xs"/><button disabled={busy||!selectedId} onClick={()=>perform(async()=>setPreview(await previewProjectMapping(selectedId,JSON.parse(contextText))), 'Preview rendered.')} className="mt-3 rounded-lg border px-3 py-2 text-sm font-bold disabled:opacity-50">Render Preview</button>{preview?<div className="mt-3 space-y-2 text-sm"><div><b>Summary:</b> {preview.renderedSummary}</div><div><b>Description:</b> {preview.renderedDescription}</div><div><b>Missing:</b> {preview.missingRequiredFields.join(', ')||'None'}</div></div>:null}</div>
      <div className="rounded-xl border border-violet-200 bg-white p-4"><h4 className="font-black">Version Diff / Rollback</h4><div className="mt-3 flex gap-2"><input type="number" min={1} value={fromVersion} onChange={e=>setFromVersion(Number(e.target.value))} className="w-24 rounded-lg border px-2 py-1"/><input type="number" min={1} value={toVersion} onChange={e=>setToVersion(Number(e.target.value))} className="w-24 rounded-lg border px-2 py-1"/><button disabled={busy||!selectedId||versions.length<2} onClick={()=>perform(async()=>setDiff(await diffProjectMapping(selectedId,fromVersion,toVersion)),'Version diff loaded.')} className="rounded-lg border px-3 py-2 text-sm font-bold disabled:opacity-50">Compare</button></div>{diff?<pre className="mt-3 overflow-auto rounded-lg bg-slate-950 p-3 text-xs text-white">{JSON.stringify(diff.changes,null,2)}</pre>:null}<Field label="New draft Mapping ID" value={newMappingId} onChange={setNewMappingId}/><div className="mt-3 flex gap-2"><button disabled={busy||!selectedId||!newMappingId||draft.lifecycleStatus!=='ACTIVE'} onClick={()=>perform(async()=>{const created=await forkProjectMapping(selectedId,newMappingId);await refresh(created.mappingId);},'New Mapping version draft created.')} className="rounded-lg border px-3 py-2 text-sm font-bold disabled:opacity-50">Create Next Version</button><button disabled={busy||!selectedId||!newMappingId||versions.length===0} onClick={()=>perform(async()=>{const created=await rollbackProjectMapping(selectedId,fromVersion,newMappingId);await refresh(created.mappingId);},'Rollback draft created from immutable version.')} className="rounded-lg border px-3 py-2 text-sm font-bold disabled:opacity-50">Create Rollback Draft</button></div></div>
    </div>
  </section>;
}
function Field({label,value,onChange,disabled=false}:{label:string;value:string;onChange:(v:string)=>void;disabled?:boolean}){return <label className="text-sm font-bold text-slate-700">{label}<input disabled={disabled} value={value} onChange={e=>onChange(e.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal disabled:bg-slate-100"/></label>}
function Metric({label,value}:{label:string;value:string}){return <div><div className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</div><div className="mt-1 font-black text-slate-900">{value}</div></div>}
