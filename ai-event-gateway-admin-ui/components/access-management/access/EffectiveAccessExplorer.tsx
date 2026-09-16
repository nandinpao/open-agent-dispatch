'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { Department, EffectiveAccess, EffectiveUiAccess, Group, ResponsibilityTemplate, UiAccessGrantReason, User } from '@/lib/iam/types';
import type { UiNavigationItem } from '@/lib/navigation/uiEntitlements';
import { EmptyState, ErrorNotice } from '../ui';
import { SearchField, SearchSelectField, humanizePermission } from '../shared/beginnerUi';

export function EffectiveAccessExplorer({
  tenantId,
  tenantName,
  users,
  groups,
  departments,
  templates,
  initialUserId,
  onOpenAssignment,
}: Readonly<{
  tenantId: string;
  tenantName: string;
  users: User[];
  groups: Group[];
  departments: Department[];
  templates: ResponsibilityTemplate[];
  initialUserId?: string;
  onOpenAssignment: (bindingId: string) => void;
}>) {
  const [userId, setUserId] = useState(initialUserId ?? '');
  const [search, setSearch] = useState('');
  const [access, setAccess] = useState<EffectiveAccess | null>(null);
  const [uiAccess, setUiAccess] = useState<EffectiveUiAccess | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!userId) { setAccess(null); setUiAccess(null); return; }
    setLoading(true); setError('');
    try {
      const [effective, effectiveUi] = await Promise.all([
        accessManagementApi.effectiveAccess(tenantId, userId),
        accessManagementApi.effectiveUiAccess(tenantId, userId),
      ]);
      setAccess(effective);
      setUiAccess(effectiveUi);
    }
    catch (cause) { setError(formatIamError(cause, 'Unable to evaluate Effective Access for this person.')); }
    finally { setLoading(false); }
  }, [tenantId, userId]);
  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!access) return [];
    if (!term) return access.permissions;
    return access.permissions.filter((permission) => `${permission.permissionCode} ${humanizePermission(permission.permissionCode)}`.toLowerCase().includes(term));
  }, [access, search]);

  return <div className="space-y-5"><ErrorNotice message={error} />
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="grid gap-4 lg:grid-cols-2"><div><label htmlFor="effective-person" className="mb-1.5 block text-sm font-black text-slate-800">Person</label><SearchSelectField id="effective-person" value={userId} onChange={setUserId} options={users.filter((user) => user.status === 'ACTIVE').map((user) => ({ value: user.userId, label: user.displayName, description: `${user.username} · ${user.email}` }))} placeholder="Search people" /></div><div><label htmlFor="effective-capability" className="mb-1.5 block text-sm font-black text-slate-800">Capability</label><SearchField id="effective-capability" value={search} onChange={setSearch} placeholder="Search what this person can do" /></div></div>
      <p className="mt-3 text-sm leading-6 text-slate-600">This explorer explains the current grant sources. A resource-level runtime authorization decision can still apply additional resource and policy conditions.</p>
    </section>

    {!userId ? <EmptyState>Select a person to explain their Effective Access.</EmptyState> : null}
    {loading ? <div className="rounded-2xl bg-slate-50 p-5 text-sm text-slate-500">Evaluating Effective Access…</div> : null}
    {access?.conflicts.length ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900"><b>Access conflicts detected</b><ul className="mt-2 space-y-1">{access.conflicts.map((conflict) => <li key={conflict}>• {conflict}</li>)}</ul></div> : null}
    {userId && !loading && uiAccess ? <EffectiveUiAccessPanel access={uiAccess} tenantName={tenantName} users={users} departments={departments} groups={groups} /> : null}
    {userId && !loading && access && search && !filtered.length ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">No current grant source matches this capability search. This explains the evaluated grant snapshot; it is not a substitute for a resource-specific deny decision.</div> : null}
    <div className="space-y-4">{filtered.map((permission) => <article key={permission.permissionCode} className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div><p className="text-xs font-black uppercase tracking-[.16em] text-blue-700">Effective capability</p><h3 className="mt-1 text-lg font-black text-slate-950">{humanizePermission(permission.permissionCode)}</h3></div><div className="mt-4 space-y-3">{permission.sources.map((source) => {
      const template = templates.find((item) => item.roleId === source.roleId);
      const sourceLabel = grantSourceLabel(source.principalType, source.principalId, users, departments, groups, userId);
      return <div key={`${permission.permissionCode}-${source.bindingId}`} className="rounded-2xl border border-blue-200 bg-blue-50 p-4"><p className="font-black text-blue-950">{sourceLabel}</p><p className="mt-2 text-sm leading-6 text-blue-900">{template?.roleName ?? source.roleName} applies to {scopeName(source.scopeType, source.scopeId, tenantName, departments, groups)}{source.expiresAt ? ` until ${new Date(source.expiresAt).toLocaleDateString()}` : ' with no planned expiry'}.</p><button type="button" onClick={() => onOpenAssignment(source.bindingId)} className="mt-3 rounded-lg border border-blue-300 bg-white px-3 py-2 text-xs font-black text-blue-800">Open source assignment</button></div>;
    })}</div>{permission.observations.length ? <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600"><b>Evaluation observations</b><ul className="mt-2 space-y-1">{permission.observations.map((observation) => <li key={observation}>• {observation}</li>)}</ul></div> : null}<details className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600"><summary className="cursor-pointer font-black text-slate-800">Technical evidence</summary><p className="mt-2 break-all">Permission code: {permission.permissionCode}</p></details></article>)}</div>
  </div>;
}

function EffectiveUiAccessPanel({ access, tenantName, users, departments, groups }: Readonly<{ access: EffectiveUiAccess; tenantName: string; users: User[]; departments: Department[]; groups: Group[] }>) {
  const pages = Object.values(access.uiAccess.pages).filter((page) => page.displayMode !== 'HIDDEN');
  const labels = navigationLabels(access.uiAccess.navigation);
  const actions = Object.values(access.uiAccess.actionEntitlements).filter((action) => action.displayMode === 'ENABLED');
  return <section className="rounded-3xl border border-emerald-200 bg-emerald-50/40 p-5 shadow-sm">
    <div><p className="text-xs font-black uppercase tracking-[.16em] text-emerald-800">Effective UI Access</p><h3 className="mt-1 text-lg font-black text-slate-950">What this person actually sees and can use</h3><p className="mt-2 text-sm leading-6 text-slate-600">This projection uses the same backend entitlement registry as the signed-in Navigator. Each explanation below comes from the canonical Role Binding sources that made the page or action effective.</p></div>
    <div className="mt-5 grid gap-4 xl:grid-cols-2">
      <div className="rounded-2xl border border-emerald-200 bg-white p-4"><p className="text-sm font-black text-slate-900">Pages</p><div className="mt-3 space-y-3">{pages.map((page) => <UiAccessRow key={page.featureId} label={labels.get(page.featureId) ?? businessFeatureName(page.featureId)} mode={page.displayMode} reasons={access.pageReasons[page.featureId] ?? []} tenantName={tenantName} users={users} departments={departments} groups={groups} />)}{!pages.length ? <p className="text-sm text-slate-500">No Tenant workspace page is currently projected.</p> : null}</div></div>
      <div className="rounded-2xl border border-emerald-200 bg-white p-4"><p className="text-sm font-black text-slate-900">Enabled functions</p><div className="mt-3 space-y-3">{actions.map((action) => <UiAccessRow key={action.actionId} label={businessActionName(action.actionId)} mode="ENABLED" reasons={access.actionReasons[action.actionId] ?? []} tenantName={tenantName} users={users} departments={departments} groups={groups} />)}{!actions.length ? <p className="text-sm text-slate-500">This person currently has read-only UI access.</p> : null}</div></div>
    </div>
  </section>;
}

function UiAccessRow({ label, mode, reasons, tenantName, users, departments, groups }: Readonly<{ label: string; mode: string; reasons: UiAccessGrantReason[]; tenantName: string; users: User[]; departments: Department[]; groups: Group[] }>) {
  const roleNames = [...new Set(reasons.map((reason) => reason.roleName).filter(Boolean))];
  return <div className="rounded-xl border border-slate-200 bg-slate-50 p-3"><div className="flex flex-wrap items-center justify-between gap-2"><b className="text-sm text-slate-900">{label}</b><span className={`rounded-full px-2 py-0.5 text-xs font-black ${mode === 'ENABLED' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'}`}>{mode === 'ENABLED' ? 'Manage' : 'Read only'}</span></div>{roleNames.length ? <p className="mt-1 text-xs text-slate-600">Granted by {roleNames.join(', ')}.</p> : <p className="mt-1 text-xs text-slate-500">Available through the current effective permission set.</p>}{reasons.length ? <details className="mt-2 text-xs text-slate-600"><summary className="cursor-pointer font-bold">Why this access exists</summary><ul className="mt-2 space-y-2">{reasons.map((reason) => <li key={`${reason.permissionCode}-${reason.bindingId}`} className="rounded-lg bg-white p-2"><b>{reason.roleName}</b> · {grantSourceLabel(reason.principalType, reason.principalId, users, departments, groups, accessUserId(reasons))}<br/><span>{scopeName(reason.scopeType, reason.scopeId, tenantName, departments, groups)}{reason.expiresAt ? ` · expires ${new Date(reason.expiresAt).toLocaleDateString()}` : ''}</span><details className="mt-1 text-xs text-slate-500"><summary className="cursor-pointer font-bold">Technical evidence</summary><span className="break-all">Permission: {reason.permissionCode} · Binding: {reason.bindingId}</span></details></li>)}</ul></details> : null}</div>;
}

function navigationLabels(items: UiNavigationItem[]): Map<string, string> {
  const labels = new Map<string, string>();
  const walk = (values: UiNavigationItem[]) => values.forEach((item) => { labels.set(item.featureId, item.label); walk(item.children ?? []); });
  walk(items);
  return labels;
}
function businessFeatureName(value: string): string { return value.split('-').map((part) => part ? part[0].toUpperCase() + part.slice(1) : part).join(' '); }
function businessActionName(value: string): string { return value.split('.').slice(1).map((part) => part ? part[0].toUpperCase() + part.slice(1) : part).join(' › '); }
function accessUserId(reasons: UiAccessGrantReason[]): string { return reasons.find((reason) => reason.principalType === 'USER')?.principalId ?? ''; }
function scopeName(type: string, id: string, tenantName: string, departments: Department[], groups: Group[]): string {
  if (type === 'TENANT') return tenantName;
  if (type === 'DEPARTMENT') return departments.find((item) => item.departmentId === id)?.name ?? id;
  if (type === 'DEPARTMENT_SUBTREE') return `${departments.find((item) => item.departmentId === id)?.name ?? id} and child Departments`;
  if (type === 'GROUP') return groups.find((item) => item.groupId === id)?.name ?? id;
  return id;
}
function grantSourceLabel(type: string, id: string, users: User[], departments: Department[], groups: Group[], currentUserId: string): string {
  if (type === 'USER') return id === currentUserId ? 'Assigned directly to this person' : `Assigned through person ${users.find((item) => item.userId === id)?.displayName ?? id}`;
  if (type === 'DEPARTMENT') return `Inherited through Department — ${departments.find((item) => item.departmentId === id)?.name ?? id}`;
  if (type === 'GROUP') return `Inherited through Group — ${groups.find((item) => item.groupId === id)?.name ?? id}`;
  if (type === 'SERVICE_ACCOUNT') return 'Granted through a Service Account relationship';
  return `Granted through ${type}`;
}
