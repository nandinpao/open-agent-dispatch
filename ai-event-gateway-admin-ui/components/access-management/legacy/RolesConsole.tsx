'use client';

import type { FormEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Permission, RbacCriticalApproval, RbacHardeningPreview, Role } from '@/lib/iam/types';
import { formatIamError } from '@/lib/iam/errorPresentation';
import { useAccessManagement } from '../AccessManagementProvider';
import { AuditReasonInput, EmptyState, ErrorNotice, Panel, StatusPill, SuccessNotice, TenantRequired } from '../ui';

const field = (data: FormData, name: string) => String(data.get(name) ?? '').trim();
function domain(code: string): string { return code.split('.')[0] || 'other'; }

export function RolesConsole() {
  const { hasPermission } = useAuth();
  const { scopeTenantId } = useAccessManagement();
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [approvals, setApprovals] = useState<RbacCriticalApproval[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [selectedCodes, setSelectedCodes] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState('');
  const [hardening, setHardening] = useState<RbacHardeningPreview | null>(null);
  const [approvalId, setApprovalId] = useState<string | null>(null);
  const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false);
  const selected = roles.find(role => role.roleId === selectedId) ?? null;
  const groupedPermissions = useMemo(() => {
    const map = new Map<string, Permission[]>();
    permissions.filter(item => !search || `${item.permissionCode} ${item.displayName} ${item.description}`.toLowerCase().includes(search.toLowerCase())).forEach(item => {
      const key = domain(item.permissionCode); map.set(key, [...(map.get(key) ?? []), item]);
    }); return [...map.entries()].sort(([a],[b]) => a.localeCompare(b));
  }, [permissions, search]);

  const load = useCallback(async () => {
    if (!scopeTenantId) return;
    setError('');
    try {
      const [rolePage, permissionPage, approvalPage] = await Promise.all([accessManagementApi.roles(scopeTenantId), accessManagementApi.permissions(scopeTenantId), accessManagementApi.rbacApprovals(scopeTenantId)]);
      setRoles(rolePage.items); setPermissions(permissionPage.items); setApprovals(approvalPage);
      setSelectedId(current => current && rolePage.items.some(role => role.roleId === current) ? current : rolePage.items[0]?.roleId ?? '');
    } catch (cause) { setError(formatIamError(cause, 'Unable to load Roles and Permissions.')); }
  }, [scopeTenantId]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!scopeTenantId || !selectedId) { setSelectedCodes(new Set()); return; }
    void accessManagementApi.rolePermissions(scopeTenantId, selectedId).then(matrix => { setSelectedCodes(new Set(matrix.permissions.map(item => item.permissionCode))); setHardening(null); setApprovalId(null); }).catch(cause => setError(formatIamError(cause, 'Unable to load the Role permission matrix.')));
  }, [scopeTenantId, selectedId]);

  async function run(action: () => Promise<unknown>, success: string) { setBusy(true); setError(''); setNotice(''); try { await action(); setNotice(success); await load(); } catch (cause) { setError(formatIamError(cause, 'Role action failed.')); } finally { setBusy(false); } }
  async function create(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form=event.currentTarget; const data=new FormData(form); const reason=field(data,'auditReason'); await run(async()=>{const role=await accessManagementApi.createRole(scopeTenantId,{roleId:null,roleCode:field(data,'roleCode'),roleName:field(data,'roleName'),description:field(data,'description')},reason);setSelectedId(role.roleId);form.reset();},'Access Role created. No Principal was assigned automatically.'); }
  async function update(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if(!selected)return; const data=new FormData(event.currentTarget); const reason=field(data,'auditReason'); await run(()=>accessManagementApi.updateRole(scopeTenantId,selected.roleId,{roleName:field(data,'roleName'),description:field(data,'description')},selected.version,reason),'Role details updated.'); }
  async function previewPermissions(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if(!selected)return; setError(''); try { setHardening(await accessManagementApi.previewRolePermissionHardening(scopeTenantId,selected.roleId,[...selectedCodes],approvalId)); } catch(cause){ setError(formatIamError(cause,'Unable to evaluate R7 Role Permission hardening.')); } }
  async function requestPermissionApproval(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if(!selected)return; const data=new FormData(event.currentTarget); const reason=field(data,'auditReason'); await run(async()=>{const approval=await accessManagementApi.requestRolePermissionApproval(scopeTenantId,selected.roleId,[...selectedCodes],reason);setApprovalId(approval.approvalId);},'Critical Role Permission change approval requested.'); }
  async function savePermissions(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if(!selected)return; const data=new FormData(event.currentTarget); const reason=field(data,'auditReason'); await run(()=>accessManagementApi.replaceRolePermissions(scopeTenantId,selected.roleId,[...selectedCodes],selected.version,reason,approvalId),'Role Permission Matrix replaced with immutable R7 evidence.'); setHardening(null); setApprovalId(null); }

  const matchingApprovals=approvals.filter(item=>item.status==='APPROVED'&&item.requestHash===hardening?.requestHash);
  if (!scopeTenantId) return <TenantRequired />;
  return <div className="space-y-5"><ErrorNotice message={error}/><SuccessNotice message={notice}/><div className="grid gap-5 xl:grid-cols-[minmax(330px,.72fr)_minmax(0,1.28fr)]">
    <div className="space-y-5"><Panel title="Role catalog" description="Access Roles grant permissions only when assigned through a scoped Role Binding."><div className="space-y-2">{roles.length?roles.map(role=><button key={role.roleId} onClick={()=>setSelectedId(role.roleId)} className={`flex w-full items-center justify-between rounded-xl border p-3 text-left ${selectedId===role.roleId?'border-blue-300 bg-blue-50':'border-slate-200 hover:bg-slate-50'}`}><span><b className="block">{role.roleName}</b><span className="text-xs text-slate-500">{role.roleCode} · {role.roleType}</span></span><StatusPill value={role.status}/></button>):<EmptyState>No Roles created.</EmptyState>}</div></Panel>
    {hasPermission('identity.tenant_role.manage')?<Panel title="Create Access Role"><form onSubmit={create} className="space-y-3"><input name="roleCode" required placeholder="Stable role code" className="w-full rounded-xl border border-slate-300 px-3 py-2"/><input name="roleName" required placeholder="Role name" className="w-full rounded-xl border border-slate-300 px-3 py-2"/><textarea name="description" placeholder="Purpose and assignment boundary" className="w-full rounded-xl border border-slate-300 px-3 py-2"/><AuditReasonInput/><button disabled={busy} className="w-full rounded-xl bg-blue-700 px-4 py-2 font-black text-white">Create Role</button></form></Panel>:null}</div>
    <div>{!selected?<Panel title="Role detail"><EmptyState>Select a Role.</EmptyState></Panel>:<div className="space-y-5"><Panel title={selected.roleName} description={`${selected.roleCode} · ${selected.roleId}`} actions={<StatusPill value={selected.status}/>}><form onSubmit={update} className="space-y-3"><input name="roleName" defaultValue={selected.roleName} required className="w-full rounded-xl border border-slate-300 px-3 py-2"/><textarea name="description" defaultValue={selected.description} className="w-full rounded-xl border border-slate-300 px-3 py-2"/><AuditReasonInput/><button disabled={busy||selected.systemManaged||!hasPermission('identity.tenant_role.manage')} className="w-full rounded-xl bg-blue-700 px-4 py-2 font-black text-white disabled:bg-slate-300">Update Role</button></form><button disabled={busy||selected.systemManaged||!hasPermission('identity.tenant_role.manage')} onClick={()=>void run(()=>accessManagementApi.changeRoleStatus(scopeTenantId,selected.roleId,selected.status==='ACTIVE'?'DISABLED':'ACTIVE',selected.version,`Role ${selected.roleCode} lifecycle changed after access review.`),`Role changed to ${selected.status==='ACTIVE'?'DISABLED':'ACTIVE'}.`)} className="mt-4 rounded-xl border border-amber-300 px-4 py-2 text-sm font-black text-amber-900 disabled:opacity-50">{selected.status==='ACTIVE'?'Disable':'Activate'} Role</button></Panel>
    <Panel title="Permission Matrix" description="Atomic permissions are grouped by domain. Technical codes remain visible for auditability; operators select them rather than entering codes manually."><input value={search} onChange={event=>setSearch(event.target.value)} placeholder="Filter permissions" className="mb-4 w-full rounded-xl border border-slate-300 px-3 py-2"/><form onSubmit={previewPermissions} className="space-y-4">{groupedPermissions.map(([group,items])=><fieldset key={group} className="rounded-2xl border border-slate-200 p-4"><legend className="px-2 text-xs font-black uppercase tracking-wide text-blue-700">{group}</legend><div className="space-y-2">{items.map(permission=><label key={permission.permissionCode} className="flex gap-3 rounded-xl p-2 hover:bg-slate-50"><input type="checkbox" checked={selectedCodes.has(permission.permissionCode)} onChange={event=>setSelectedCodes(current=>{const next=new Set(current);if(event.target.checked)next.add(permission.permissionCode);else next.delete(permission.permissionCode);return next;})}/><span><b className="block text-sm">{permission.displayName||permission.permissionCode}</b><span className="block text-xs text-slate-500">{permission.permissionCode} · scopes {permission.allowedScopeTypes.join(', ')}</span>{permission.description?<span className="block text-xs text-slate-600">{permission.description}</span>:null}</span></label>)}</div></fieldset>)}{hardening?<div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs"><b>R7 hardening preview</b><p>Critical: {hardening.critical?'Yes':'No'} · Approval: {hardening.approvalRequired?(approvalId?`Attached ${approvalId}`:'Required'):'Not required'}</p>{hardening.conflicts.length?<p className="mt-2 font-black text-rose-800">Blocked: {hardening.conflicts.join(' · ')}</p>:null}<p className="mt-2 break-all text-xs text-slate-500">Payload {hardening.requestHash}</p></div>:null}{hardening?.approvalRequired&&matchingApprovals.length?<label className="block text-xs font-black text-slate-700">Attach approved request<select value={approvalId??''} onChange={event=>setApprovalId(event.target.value||null)} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2"><option value="">Select approved request</option>{matchingApprovals.map(item=><option key={item.approvalId} value={item.approvalId}>{item.approvalId} · approved by {item.approverId}</option>)}</select></label>:null}<button type="submit" disabled={busy||selected.systemManaged||!hasPermission('identity.role_permission.manage')} className="w-full rounded-xl bg-slate-950 px-4 py-3 font-black text-white disabled:bg-slate-300">Preview Permission Hardening</button></form>{hardening?.approvalRequired&&!approvalId?<form onSubmit={requestPermissionApproval} className="mt-4 space-y-3"><AuditReasonInput/><button disabled={busy||hardening.conflicts.length>0||!hasPermission('identity.role_approval.request')} className="w-full rounded-xl bg-amber-600 px-4 py-3 font-black text-white disabled:bg-slate-300">Request Two-Person Approval</button></form>:hardening?<form onSubmit={savePermissions} className="mt-4 space-y-3"><AuditReasonInput/><button disabled={busy||hardening.conflicts.length>0||!hasPermission('identity.role_permission.manage')} className="w-full rounded-xl bg-blue-700 px-4 py-3 font-black text-white disabled:bg-slate-300">Save Permission Matrix</button></form>:null}</Panel></div>}</div>
  </div></div>;
}
