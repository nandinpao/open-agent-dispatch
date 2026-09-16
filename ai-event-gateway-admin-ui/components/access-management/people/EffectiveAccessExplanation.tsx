'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Department, EffectiveAccess, EffectiveUiAccess, Group, Role, UiAccessGrantReason, User } from '@/lib/iam/types';
import { EmptyState } from '../ui';
import { humanizePermission } from '../shared/beginnerUi';

interface EffectiveAccessExplanationProps {
  tenantId: string;
  person: User;
  access: EffectiveAccess | null;
  roles: Role[];
  groups: Group[];
  departments?: Department[];
}

function PersonUiAccessSummary({ access, person, groups, departments, tenantId }: Readonly<{ access: EffectiveUiAccess; person: User; groups: Group[]; departments: Department[]; tenantId: string }>) {
  const labels = new Map<string, string>();
  const visit = (items: typeof access.uiAccess.navigation) => items.forEach((item) => { labels.set(item.featureId, item.label); visit(item.children); });
  visit(access.uiAccess.navigation);
  const pages = Object.values(access.uiAccess.pages).filter((page) => page.displayMode !== 'HIDDEN');
  const actions = Object.values(access.uiAccess.actionEntitlements).filter((action) => action.displayMode === 'ENABLED');
  return <section className="rounded-2xl border border-emerald-200 bg-emerald-50/40 p-4"><p className="text-xs font-black uppercase tracking-[.14em] text-emerald-800">Effective UI Access</p><h3 className="mt-1 font-black text-slate-950">What {person.displayName} sees after sign-in</h3><p className="mt-1 text-xs leading-5 text-slate-600">Generated from the same backend entitlement projection as the Navigator. Expand a row to see which direct, Department or Group responsibility caused it.</p><div className="mt-4 grid gap-3 lg:grid-cols-2"><div className="space-y-2"><p className="text-xs font-black uppercase tracking-wide text-slate-500">Pages</p>{pages.map((page) => <PersonUiRow key={page.featureId} label={labels.get(page.featureId) ?? uiName(page.featureId)} mode={page.displayMode} reasons={access.pageReasons[page.featureId] ?? []} person={person} groups={groups} departments={departments} tenantId={tenantId} />)}</div><div className="space-y-2"><p className="text-xs font-black uppercase tracking-wide text-slate-500">Functions</p>{actions.map((action) => <PersonUiRow key={action.actionId} label={uiName(action.actionId)} mode="ENABLED" reasons={access.actionReasons[action.actionId] ?? []} person={person} groups={groups} departments={departments} tenantId={tenantId} />)}{!actions.length ? <p className="rounded-xl bg-white p-3 text-xs text-slate-500">No write function is enabled.</p> : null}</div></div></section>;
}

function PersonUiRow({ label, mode, reasons, person, groups, departments, tenantId }: Readonly<{ label: string; mode: string; reasons: UiAccessGrantReason[]; person: User; groups: Group[]; departments: Department[]; tenantId: string }>) {
  const roleNames = [...new Set(reasons.map((reason) => reason.roleName).filter(Boolean))];
  return <details className="rounded-xl border border-slate-200 bg-white p-3"><summary className="cursor-pointer list-none"><div className="flex items-center justify-between gap-2"><span className="text-sm font-black text-slate-900">{label}</span><span className={`rounded-full px-2 py-0.5 text-xs font-black ${mode === 'ENABLED' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'}`}>{mode === 'ENABLED' ? 'Manage' : 'Read only'}</span></div>{roleNames.length ? <p className="mt-1 text-xs text-slate-500">Granted by {roleNames.join(', ')}</p> : null}</summary>{reasons.length ? <ul className="mt-3 space-y-2 border-t border-slate-100 pt-3">{reasons.map((reason) => <li key={`${reason.permissionCode}-${reason.bindingId}`} className="text-xs leading-5 text-slate-600"><b className="text-slate-800">{reason.roleName}</b> · {reasonSource(reason, person, groups, departments)} · {scopeBusinessLabel(reason.scopeType, reason.scopeId, tenantId, groups, departments)}{reason.expiresAt ? ` · expires ${new Date(reason.expiresAt).toLocaleDateString()}` : ''}<details className="mt-1 text-xs text-slate-500"><summary className="cursor-pointer font-bold">Technical evidence</summary><span className="break-all">Permission: {reason.permissionCode} · Binding: {reason.bindingId}</span></details></li>)}</ul> : <p className="mt-2 text-xs text-slate-500">Available through the current effective permission set.</p>}</details>;
}

function reasonSource(reason: UiAccessGrantReason, person: User, groups: Group[], departments: Department[]): string {
  if (reason.principalType === 'GROUP') return `Group ${groups.find((item) => item.groupId === reason.principalId)?.name ?? reason.principalId}`;
  if (reason.principalType === 'DEPARTMENT') return `Department ${departments.find((item) => item.departmentId === reason.principalId)?.name ?? reason.principalId}`;
  if (reason.principalType === 'USER' && reason.principalId === person.userId) return 'Direct assignment';
  return reason.principalType.replaceAll('_', ' ');
}
function scopeBusinessLabel(type: string, id: string, tenantId: string, groups: Group[], departments: Department[]): string {
  if (type === 'TENANT') return id === tenantId ? 'this Tenant' : 'Tenant scope';
  if (type === 'GROUP') return groups.find((item) => item.groupId === id)?.name ?? 'Group scope';
  if (type === 'DEPARTMENT') return departments.find((item) => item.departmentId === id)?.name ?? 'Department scope';
  if (type === 'DEPARTMENT_SUBTREE') return `${departments.find((item) => item.departmentId === id)?.name ?? 'Department'} and children`;
  return type.replaceAll('_', ' ');
}
function uiName(value: string): string { return value.split(/[.-]/).filter(Boolean).map((part) => part[0].toUpperCase() + part.slice(1)).join(' › '); }

export function EffectiveAccessExplanation({
  tenantId,
  person,
  access,
  roles,
  groups,
  departments = [],
}: Readonly<EffectiveAccessExplanationProps>) {
  const [uiAccess, setUiAccess] = useState<EffectiveUiAccess | null>(null);
  useEffect(() => {
    let active = true;
    void accessManagementApi.effectiveUiAccess(tenantId, person.userId)
      .then((value) => { if (active) setUiAccess(value); })
      .catch(() => { if (active) setUiAccess(null); });
    return () => { active = false; };
  }, [person.userId, tenantId]);
  if (!access?.permissions.length) {
    return (
      <EmptyState>
        <p className="font-black text-slate-700">No effective access was found.</p>
        <p className="mt-2">Assign a business responsibility to this person or to one of their Groups.</p>
        <Link href={`/admin/tenants/${encodeURIComponent(tenantId)}/access?principalType=USER&principalId=${encodeURIComponent(person.userId)}`} className="mt-3 inline-flex rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white">
          Assign access
        </Link>
      </EmptyState>
    );
  }

  return (
    <div className="space-y-3">
      {uiAccess ? <PersonUiAccessSummary access={uiAccess} person={person} groups={groups} departments={departments} tenantId={tenantId} /> : null}
      {access.conflicts.length ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-900">
          Access review required: {access.conflicts.join(' · ')}
        </div>
      ) : null}
      {access.permissions.map((permission) => (
        <details key={permission.permissionCode} className="rounded-2xl border border-slate-200 bg-white p-4" open={access.permissions.length <= 5}>
          <summary className="cursor-pointer list-none">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="font-black text-slate-950">{humanizePermission(permission.permissionCode)}</p>
                <p className="mt-1 text-xs text-slate-500">Technical permission details are available when expanded.</p>
              </div>
              <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-black text-emerald-800">Allowed</span>
            </div>
          </summary>
          <div className="mt-4 space-y-3 border-t border-slate-100 pt-4">
            {permission.sources.map((source) => {
              const role = roles.find((item) => item.roleId === source.roleId);
              const group = source.principalType === 'GROUP'
                ? groups.find((item) => item.groupId === source.principalId)
                : null;
              const department = source.principalType === 'DEPARTMENT'
                ? departments.find((item) => item.departmentId === source.principalId)
                : null;
              const roleName = role?.roleName ?? source.roleName ?? source.roleId;
              const sourceText = source.principalType === 'GROUP'
                ? `${person.displayName} is a member of ${group?.name ?? source.principalId}, and that Group has the ${roleName} responsibility.`
                : source.principalType === 'DEPARTMENT'
                  ? `${person.displayName} belongs to ${department?.name ?? source.principalId}, and that Department has the ${roleName} responsibility.`
                  : `${person.displayName} has the ${roleName} responsibility directly.`;
              return (
                <div key={`${permission.permissionCode}-${source.bindingId}`} className="rounded-2xl bg-slate-50 p-4">
                  <p className="text-sm font-black text-slate-900">Why this access exists</p>
                  <p className="mt-2 text-sm leading-6 text-slate-700">
                    {sourceText} It applies to {scopeBusinessLabel(source.scopeType, source.scopeId, tenantId, groups, departments)}.
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Link href={`/admin/tenants/${encodeURIComponent(tenantId)}/access?bindingId=${encodeURIComponent(source.bindingId)}`} className="rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-black text-blue-800">
                      Open assignment
                    </Link>
                    {group ? (
                      <Link href={`/admin/tenants/${encodeURIComponent(tenantId)}/organization?type=GROUP&id=${encodeURIComponent(group.groupId)}&tab=MEMBERS`} className="rounded-lg bg-white px-2.5 py-1 text-xs font-black text-slate-700 ring-1 ring-slate-200">
                        Open Group
                      </Link>
                    ) : null}
                  </div>
                  <details className="mt-3">
                    <summary className="cursor-pointer text-xs font-black text-slate-500">Technical evidence</summary>
                    <dl className="mt-2 grid gap-2 text-xs text-slate-600 sm:grid-cols-2">
                      <div><dt className="font-black">Permission</dt><dd className="break-all">{permission.permissionCode}</dd></div>
                      <div><dt className="font-black">Binding</dt><dd className="break-all">{source.bindingId}</dd></div>
                    </dl>
                  </details>
                </div>
              );
            })}
          </div>
        </details>
      ))}
    </div>
  );
}
