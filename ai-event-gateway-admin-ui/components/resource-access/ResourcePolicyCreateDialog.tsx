'use client';
import { useState } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { createExplicitDeny,createScopeGrant,type CreateExplicitDenyInput,type CreateScopeGrantInput,type ScopePrincipalType,type ScopeType,type VisibilityLevel } from '@/lib/api/domains/resourceAccessApi';
import { ScopeGrantSafetyPanel } from '@/components/phase7e/ScopeGrantSafetyPanel';
import { ExplicitDenySecurityPanel } from '@/components/phase7e/ExplicitDenySecurityPanel';
import { validateScopeGrantDraft } from '@/lib/phase7e/iamResourceAccessUx';
import { createUuid } from '@/lib/utils/uuid';

type Mode='grants'|'denies';
const resourceTypes=['TASK','TASK_CHAIN','A2A_REQUEST','A2A_APPROVAL','TASK_CONTEXT_SNAPSHOT','TASK_RESULT','TASK_ATTACHMENT','ISSUE_CONNECTION','ISSUE_PRINCIPAL','ISSUE_CREDENTIAL_METADATA','ISSUE_PROJECT_MAPPING','TASK_ISSUE_LINK','ISSUE_CONTEXT_SNAPSHOT','ISSUE_ATTACHMENT','ISSUE_CONFLICT','ISSUE_DEAD_LETTER','ISSUE_TOPOLOGY','AGENT','AGENT_POOL','SERVICE_ACCOUNT'] as const;
const scopeTypes:ScopeType[]=['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP','RESOURCE','RESOURCE_TREE','TASK_CHAIN','PARTICIPANT','OWNER','CREATED_BY_ME','ASSIGNED_TO_ME','AUDIT_WINDOW','EXPLICIT_SET'];
const implicitScope=new Set<ScopeType>(['TENANT','OWNER','PARTICIPANT','CREATED_BY_ME','ASSIGNED_TO_ME']);
function id(prefix:string){return `${prefix}-${createUuid()}`}
function localNow(){const d=new Date();d.setMinutes(d.getMinutes()-d.getTimezoneOffset());return d.toISOString().slice(0,16)}

export function ResourcePolicyCreateDialog({mode,open,onClose,onCreated}:Readonly<{mode:Mode;open:boolean;onClose:()=>void;onCreated:()=>void}>){
 const dialogRef=useDialogAccessibility<HTMLElement>(open,onClose);
 const [principalType,setPrincipalType]=useState<ScopePrincipalType>('USER');
 const [principalId,setPrincipalId]=useState('');
 const [permissionCode,setPermissionCode]=useState(mode==='grants'?'task.read':'');
 const [resourceType,setResourceType]=useState('TASK');
 const [scopeType,setScopeType]=useState<ScopeType>('RESOURCE');
 const [scopeRefId,setScopeRefId]=useState('');
 const [visibility,setVisibility]=useState<VisibilityLevel>('STANDARD');
 const [severity,setSeverity]=useState<CreateExplicitDenyInput['severity']>('HIGH');
 const [reason,setReason]=useState('');
 const [validFrom,setValidFrom]=useState(localNow());
 const [validTo,setValidTo]=useState('');
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 if(!open)return null;
 async function submit(){
  setBusy(true);setError('');
  try{
   const common={principalType,principalId:principalId.trim(),permissionCode:permissionCode.trim(),resourceType,scopeType,scopeRefId:implicitScope.has(scopeType)?'':scopeRefId.trim(),validFrom:new Date(validFrom).toISOString(),validTo:validTo?new Date(validTo).toISOString():null};
   if(mode==='grants')await createScopeGrant({...common,grantId:id('grant'),visibilityLevel:visibility,grantSource:'MANUAL',grantReason:reason.trim()} satisfies CreateScopeGrantInput);
   else await createExplicitDeny({...common,denyId:id('deny'),denyReason:reason.trim(),severity} satisfies CreateExplicitDenyInput);
   onCreated();onClose();
  }catch(e){setError(e instanceof Error?e.message:'Unable to create policy draft.')}finally{setBusy(false)}
 }
 const grantDraft={permissionCode,resourceType,scopeType,scopeRefId:implicitScope.has(scopeType)?'':scopeRefId,visibilityLevel:visibility,validFrom,validTo:validTo||null,grantReason:reason};
 const grantValidation=validateScopeGrantDraft(grantDraft);
 const valid=Boolean(principalId.trim()&&reason.trim().length>=12&&validFrom&&(implicitScope.has(scopeType)||scopeRefId.trim())&&(mode==='denies'||grantValidation.status!=='BLOCKED'));
 return <div className="fixed inset-0 z-[90] grid place-items-center bg-slate-950/60 p-4" role="dialog" aria-modal="true" aria-labelledby="resource-policy-create-title"><section ref={dialogRef} tabIndex={-1} className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl"><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-black uppercase tracking-[.16em] text-indigo-600">Draft governance policy</p><h2 id="resource-policy-create-title" className="mt-1 text-2xl font-black text-slate-950">Create {mode==='grants'?'Scope Grant':'Explicit Deny'}</h2><p className="mt-2 text-sm leading-6 text-slate-600">The record is created as <b>DRAFT</b>. It cannot become active until a different authenticated approver completes the approval step.</p></div><button type="button" onClick={onClose} className="rounded-xl border border-slate-300 px-3 py-2 font-black" aria-label="Close create policy dialog">×</button></div>{error?<div role="alert" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</div>:null}<div className="mt-5 grid gap-4 sm:grid-cols-2"><Select label="Principal type" value={principalType} values={['USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT']} onChange={v=>setPrincipalType(v as ScopePrincipalType)}/><Field label="Principal ID" value={principalId} onChange={setPrincipalId}/><Field label={mode==='denies'?'Permission (blank means all)':'Permission'} value={permissionCode} onChange={setPermissionCode}/><Select label="Resource type" value={resourceType} values={[...resourceTypes]} onChange={setResourceType}/><Select label="Scope type" value={scopeType} values={scopeTypes} onChange={v=>{setScopeType(v as ScopeType);setScopeRefId('')}}/><Field label={implicitScope.has(scopeType)?'Scope reference resolved by policy':'Scope reference ID'} value={scopeRefId} onChange={setScopeRefId} disabled={implicitScope.has(scopeType)}/>{mode==='grants'?<Select label="Visibility cap" value={visibility} values={['METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA']} onChange={v=>setVisibility(v as VisibilityLevel)}/>:<Select label="Severity" value={severity} values={['LOW','MEDIUM','HIGH','CRITICAL']} onChange={v=>setSeverity(v as CreateExplicitDenyInput['severity'])}/>}<label className="block text-sm font-black text-slate-700">Valid from<input type="datetime-local" value={validFrom} onChange={e=>setValidFrom(e.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal"/></label><label className="block text-sm font-black text-slate-700">Valid to (optional)<input type="datetime-local" value={validTo} onChange={e=>setValidTo(e.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal"/></label></div><label className="mt-4 block text-sm font-black text-slate-700">Governance reason<textarea value={reason} onChange={e=>setReason(e.target.value)} rows={4} placeholder="Explain the business need and risk controls (minimum 12 characters)." className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal"/></label>{mode==='grants'?<div className="mt-4"><ScopeGrantSafetyPanel draft={grantDraft}/></div>:<div className="mt-4"><ExplicitDenySecurityPanel severity={severity}/></div>}<div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b>High-risk control:</b> activation requires independent approval, current Tenant context, optimistic version matching, and any backend-required recent MFA assurance. The UI cannot assert or bypass MFA.</div><div className="mt-6 flex justify-end gap-3"><button type="button" onClick={onClose} className="rounded-xl border border-slate-300 px-4 py-2.5 font-black">Cancel</button><button type="button" disabled={busy||!valid} onClick={()=>void submit()} className="rounded-xl bg-indigo-700 px-5 py-2.5 font-black text-white disabled:opacity-40">{busy?'Creating…':'Create draft'}</button></div></section></div>;
}
function Field({label,value,onChange,disabled=false}:{label:string;value:string;onChange:(v:string)=>void;disabled?:boolean}){return <label className="block text-sm font-black text-slate-700">{label}<input value={value} disabled={disabled} onChange={e=>onChange(e.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal disabled:bg-slate-100 disabled:text-slate-500"/></label>}
function Select({label,value,values,onChange}:{label:string;value:string;values:readonly string[];onChange:(v:string)=>void}){return <label className="block text-sm font-black text-slate-700">{label}<select value={value} onChange={e=>onChange(e.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal">{values.map(v=><option key={v}>{v}</option>)}</select></label>}
