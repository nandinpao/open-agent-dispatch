'use client';

import type { FormEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Department, Group, Membership, PlatformTenantMembership, User, UserAccessOverview } from '@/lib/iam/types';
import { formatIamError } from '@/lib/iam/errorPresentation';
import { useAccessManagement } from '../AccessManagementProvider';
import { AuditReasonInput, EmptyState, ErrorNotice, Panel, StatusPill, SuccessNotice } from '../ui';

const PAGE_SIZE = 50;
const value = (data: FormData, key: string) => String(data.get(key) ?? '').trim();
const dateOrNull = (data: FormData, key: string) => value(data, key) ? new Date(value(data, key)).toISOString() : null;

export function UsersConsole() {
  const { hasPermission } = useAuth();
  const { allTenants, scopeTenantId, tenants } = useAccessManagement();
  const [users, setUsers] = useState<User[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [selected, setSelected] = useState<User | null>(null);
  const [overview, setOverview] = useState<UserAccessOverview | null>(null);
  const [platformMemberships, setPlatformMemberships] = useState<PlatformTenantMembership[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [detailTab, setDetailTab] = useState<'PROFILE'|'TENANTS'|'ORGANIZATION'|'ACCESS'|'SECURITY'>('PROFILE');

  const loadUsers = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const page = allTenants
        ? await accessManagementApi.users(PAGE_SIZE, '', search)
        : await accessManagementApi.tenantUsers(scopeTenantId, PAGE_SIZE, '', search);
      setUsers(page.items);
      setSelectedId(current => current && page.items.some(item => item.userId === current) ? current : page.items[0]?.userId ?? '');
    } catch (cause) { setError(formatIamError(cause, 'Unable to load users.')); }
    finally { setLoading(false); }
  }, [allTenants, scopeTenantId, search]);

  const loadDetail = useCallback(async () => {
    if (!selectedId) { setSelected(null); setOverview(null); setPlatformMemberships([]); return; }
    setError('');
    try {
      if (allTenants) {
        const [user, memberships] = await Promise.all([
          accessManagementApi.user(selectedId),
          accessManagementApi.userTenantMemberships(selectedId),
        ]);
        setSelected(user); setPlatformMemberships(memberships.items); setOverview(null);
      } else {
        const [detail, departmentPage, groupPage] = await Promise.all([
          accessManagementApi.userOverview(scopeTenantId, selectedId),
          accessManagementApi.departments(scopeTenantId),
          accessManagementApi.groups(scopeTenantId),
        ]);
        setSelected(detail.user); setOverview(detail); setPlatformMemberships([]);
        setDepartments(departmentPage.items); setGroups(groupPage.items);
      }
    } catch (cause) { setError(formatIamError(cause, 'Unable to load user details.')); }
  }, [allTenants, scopeTenantId, selectedId]);

  useEffect(() => { void loadUsers(); }, [loadUsers]);
  useEffect(() => { void loadDetail(); }, [loadDetail]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setError(''); setNotice('');
    try { await action(); setNotice(success); await loadUsers(); await loadDetail(); }
    catch (cause) { setError(formatIamError(cause, 'Access Management action failed.')); }
    finally { setBusy(false); }
  }

  async function createUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget; const data = new FormData(form); const reason = value(data, 'auditReason');
    const payload = { userId: null, username: value(data, 'username'), email: value(data, 'email') || null, displayName: value(data, 'displayName'), creationMode: value(data, 'creationMode') || 'INVITATION' };
    await run(async () => {
      const created = allTenants
        ? await accessManagementApi.createUser(payload, reason)
        : await accessManagementApi.createTenantUser(scopeTenantId, payload, reason);
      setSelectedId(created.userId); form.reset();
    }, 'User identity created. Tenant admission and permissions remain separate assignments.');
  }

  async function updateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const data = new FormData(event.currentTarget); const reason = value(data, 'auditReason');
    const payload = { displayName: value(data, 'displayName'), email: value(data, 'email') || null };
    await run(() => allTenants
      ? accessManagementApi.updateUser(selected.userId, payload, selected.version, reason)
      : accessManagementApi.updateTenantUser(scopeTenantId, selected.userId, payload, selected.version, reason), 'User profile updated.');
  }

  async function createTenantMembership(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const form = event.currentTarget; const data = new FormData(form); const targetTenantId = value(data, 'tenantId'); const reason = value(data, 'auditReason');
    await run(() => accessManagementApi.createTenantMembership(targetTenantId, {
      membershipId: null,
      userId: selected.userId,
      initialStatus: 'ACTIVE',
      employeeId: value(data, 'employeeId') || null,
      expiresAt: dateOrNull(data, 'expiresAt'),
      defaultTenant: data.get('defaultTenant') === 'on',
      membershipSource: 'ADMIN_CREATED',
      reason,
    }, reason), 'Tenant Membership created without assigning an Access Role.');
    form.reset();
  }

  async function addDepartment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const form = event.currentTarget; const data = new FormData(form); const reason = value(data, 'auditReason');
    await run(() => accessManagementApi.addDepartmentMembership(scopeTenantId, selected.userId, {
      membershipId: null,
      departmentId: value(data, 'departmentId'),
      membershipType: 'MEMBER',
      primary: data.get('primary') === 'on',
      expiresAt: dateOrNull(data, 'expiresAt'),
    }, reason), 'Department Membership added. This organizational relationship grants no permission.');
    form.reset();
  }

  async function addGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const form = event.currentTarget; const data = new FormData(form); const reason = value(data, 'auditReason');
    await run(() => accessManagementApi.addGroupMembership(scopeTenantId, selected.userId, {
      membershipId: null,
      groupId: value(data, 'groupId'),
      membershipRole: value(data, 'membershipRole') || 'MEMBER',
      expiresAt: dateOrNull(data, 'expiresAt'),
    }, reason), 'Group Membership added. Permissions are inherited only from a Group Role Binding.');
    form.reset();
  }

  async function changeGlobalUserStatus() {
    if (!selected || !allTenants) return;
    const nextStatus = selected.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    const reason = `Root changed global User status to ${nextStatus} after identity governance review.`;
    await run(() => accessManagementApi.changeUserStatus(selected.userId, nextStatus, selected.version, reason), `Global User status changed to ${nextStatus}.`);
  }

  async function makePrimaryDepartment(target: Membership) {
    const currentPrimary = departmentMemberships.find(item => item.primary);
    if (!currentPrimary || currentPrimary.membershipId === target.membershipId) return;
    const reason = 'Primary Department changed from unified Access Management after organization review.';
    await run(() => accessManagementApi.updateDepartmentMembership(scopeTenantId, currentPrimary.membershipId, {
      membershipType: currentPrimary.role || 'MEMBER',
      primary: false,
      expiresAt: currentPrimary.expiresAt || null,
      replacementMembershipId: target.membershipId,
      replacementExpectedVersion: target.version,
      reason,
    }, currentPrimary.version, reason), 'Primary Department changed. No Access Role was modified.');
  }

  async function removeDepartmentMembership(item: Membership) {
    const replacement = item.primary ? departmentMemberships.find(candidate => candidate.membershipId !== item.membershipId && candidate.status === 'ACTIVE') : undefined;
    if (item.primary && !replacement) {
      setError('Add another active Department Membership before removing the current Primary Department.');
      return;
    }
    const reason = 'Department Membership removed from unified Access Management after organization review.';
    await run(() => accessManagementApi.removeDepartmentMembership(scopeTenantId, item.membershipId, item.version, reason, replacement ? { id: replacement.membershipId, version: replacement.version } : undefined), 'Department Membership removed.');
  }

  async function updateGroupMembershipRole(item: Membership) {
    const nextRole = item.role === 'LEAD' ? 'MEMBER' : 'LEAD';
    const reason = `Group organizational role changed to ${nextRole}; RBAC authority remains controlled by Group Role Bindings.`;
    await run(() => accessManagementApi.updateGroupMembership(scopeTenantId, item.membershipId, { membershipRole: nextRole, expiresAt: item.expiresAt || null, reason }, item.version, reason), `Group Membership changed to ${nextRole}.`);
  }

  async function removeGroupMembership(item: Membership) {
    const reason = 'Group Membership removed from unified Access Management after organization review.';
    await run(() => accessManagementApi.removeGroupMembership(scopeTenantId, item.membershipId, item.version, reason), 'Group Membership removed. Inherited Group access will be recalculated.');
  }

  const tenantMembership = useMemo(() => overview?.memberships.find(item => item.membershipType === 'TENANT'), [overview]);
  const departmentMemberships = useMemo(() => overview?.memberships.filter(item => item.membershipType === 'DEPARTMENT') ?? [], [overview]);
  const groupMemberships = useMemo(() => overview?.memberships.filter(item => item.membershipType === 'GROUP') ?? [], [overview]);

  return <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(560px,1.45fr)]">
    <div className="space-y-5">
      <Panel title={allTenants ? 'Global user directory' : 'Tenant user directory'} description={allTenants ? 'Root sees global identities across all Tenants.' : 'Users admitted to the selected Tenant.'}>
        <form onSubmit={event => { event.preventDefault(); setSearch(value(new FormData(event.currentTarget), 'search')); }} className="flex gap-2">
          <input name="search" defaultValue={search} placeholder="Search username, email or display name" className="min-w-0 flex-1 rounded-xl border border-slate-300 px-3 py-2 text-sm" />
          <button className="rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white">Search</button>
        </form>
        <div className="mt-4 divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200">
          {loading ? <p className="p-5 text-sm text-slate-500">Loading users…</p> : users.length ? users.map(item => <button key={item.userId} onClick={() => setSelectedId(item.userId)} className={`flex w-full items-center justify-between gap-3 p-4 text-left ${selectedId === item.userId ? 'bg-blue-50' : 'bg-white hover:bg-slate-50'}`}><span><span className="block font-black text-slate-950">{item.displayName}</span><span className="block text-xs text-slate-500">{item.username} · {item.email || 'No email'}</span></span><StatusPill value={item.status} /></button>) : <EmptyState>No users match the current scope.</EmptyState>}
        </div>
      </Panel>

      {hasPermission(allTenants ? 'identity.platform_user.create' : 'identity.user.create') ? <Panel title="Create user" description="Creates a global identity. Tenant admission, organization placement and permissions are separate operations.">
        <form onSubmit={createUser} className="grid gap-3 sm:grid-cols-2">
          <input name="username" required placeholder="Username" className="rounded-xl border border-slate-300 px-3 py-2" />
          <input name="displayName" required placeholder="Display name" className="rounded-xl border border-slate-300 px-3 py-2" />
          <input name="email" type="email" placeholder="Email" className="rounded-xl border border-slate-300 px-3 py-2" />
          <select name="creationMode" className="rounded-xl border border-slate-300 px-3 py-2"><option value="INVITATION">Invitation</option><option value="ADMINISTRATIVE">Administrative</option><option value="MIGRATED">Migrated</option></select>
          <div className="sm:col-span-2"><AuditReasonInput /></div>
          <button disabled={busy} className="rounded-xl bg-blue-700 px-4 py-3 font-black text-white sm:col-span-2 disabled:bg-slate-300">Create user</button>
        </form>
      </Panel> : null}
    </div>

    <div className="space-y-4">
      <ErrorNotice message={error} /><SuccessNotice message={notice} />
      {!selected ? <Panel title="User details"><EmptyState>Select a user.</EmptyState></Panel> : <Panel title={selected.displayName} description={`${selected.username} · ${selected.userId}`} actions={<StatusPill value={selected.status} />}>
        <div className="flex gap-2 overflow-x-auto border-b border-slate-200 pb-3">
          {(['PROFILE','TENANTS','ORGANIZATION','ACCESS','SECURITY'] as const).map(tab => <button key={tab} onClick={() => setDetailTab(tab)} className={`rounded-xl px-3 py-2 text-xs font-black ${detailTab === tab ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-700'}`}>{tab.replace('_',' ')}</button>)}
        </div>

        {detailTab === 'PROFILE' ? <form onSubmit={updateProfile} className="mt-4 grid gap-3 sm:grid-cols-2">
          <label className="text-xs font-black text-slate-600">Display name<input name="displayName" defaultValue={selected.displayName} required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" /></label>
          <label className="text-xs font-black text-slate-600">Email<input name="email" type="email" defaultValue={selected.email} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" /></label>
          <div className="sm:col-span-2"><AuditReasonInput /></div>
          <button disabled={busy} className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white sm:col-span-2">Update profile</button>
          {allTenants && hasPermission('identity.platform_user.update') ? <button type="button" disabled={busy} onClick={() => void changeGlobalUserStatus()} className="rounded-xl border border-amber-300 px-4 py-2 font-black text-amber-900 sm:col-span-2">{selected.status === 'ACTIVE' ? 'Suspend global User' : 'Activate global User'}</button> : null}
        </form> : null}

        {detailTab === 'TENANTS' ? <div className="mt-4 space-y-4">
          <div className="overflow-x-auto rounded-2xl border border-slate-200"><table className="min-w-full text-left text-sm"><thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="p-3">Tenant</th><th className="p-3">Status</th><th className="p-3">Default</th><th className="p-3">Expires</th></tr></thead><tbody>
            {allTenants ? platformMemberships.map(item => <tr key={`${item.tenantId}-${item.userId}`} className="border-t"><td className="p-3 font-bold">{item.tenantName}<div className="text-xs text-slate-500">{item.tenantId}</div></td><td className="p-3"><StatusPill value={item.membershipStatus} /></td><td className="p-3">{item.defaultTenant ? 'Yes' : 'No'}</td><td className="p-3">{item.expiresAt || 'Never'}</td></tr>) : tenantMembership ? <tr className="border-t"><td className="p-3 font-bold">{scopeTenantId}</td><td className="p-3"><StatusPill value={tenantMembership.status} /></td><td className="p-3">{tenantMembership.primary ? 'Yes' : 'No'}</td><td className="p-3">{tenantMembership.expiresAt || 'Never'}</td></tr> : null}
          </tbody></table></div>
          {allTenants && hasPermission('identity.tenant_membership.manage') ? <form onSubmit={createTenantMembership} className="grid gap-3 rounded-2xl border border-blue-200 bg-blue-50 p-4 sm:grid-cols-2">
            <select name="tenantId" required className="rounded-xl border border-slate-300 px-3 py-2"><option value="">Select Tenant</option>{tenants.map(item => <option key={item.tenantId} value={item.tenantId}>{item.tenantName}</option>)}</select>
            <input name="employeeId" placeholder="Employee ID" className="rounded-xl border border-slate-300 px-3 py-2" />
            <input name="expiresAt" type="datetime-local" className="rounded-xl border border-slate-300 px-3 py-2" />
            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2 text-sm font-bold"><input name="defaultTenant" type="checkbox" /> Default Tenant</label>
            <div className="sm:col-span-2"><AuditReasonInput /></div><button className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white sm:col-span-2">Add Tenant Membership</button>
          </form> : null}
        </div> : null}

        {detailTab === 'ORGANIZATION' ? allTenants ? <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Select a Tenant scope to manage Department and Group memberships.</div> : <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <div className="space-y-3"><h3 className="font-black">Departments</h3>{departmentMemberships.map(item => <div key={item.membershipId} className="rounded-xl border border-slate-200 p-3 text-sm"><b>{departments.find(d => d.departmentId === item.resourceId)?.name ?? item.resourceId}</b><div className="text-xs text-slate-500">{item.primary ? 'Primary Department' : 'Additional Department'} · {item.status}</div>{hasPermission('identity.membership.manage') ? <div className="mt-2 flex flex-wrap gap-2">{!item.primary ? <button type="button" disabled={busy} onClick={() => void makePrimaryDepartment(item)} className="rounded-lg border border-blue-300 px-2 py-1 text-xs font-black text-blue-800">Make primary</button> : null}<button type="button" disabled={busy} onClick={() => void removeDepartmentMembership(item)} className="rounded-lg border border-rose-300 px-2 py-1 text-xs font-black text-rose-800">Remove</button></div> : null}</div>)}
            <form onSubmit={addDepartment} className="space-y-2 rounded-2xl bg-slate-50 p-3"><select name="departmentId" required className="w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">Select Department</option>{departments.map(item => <option key={item.departmentId} value={item.departmentId}>{item.name}</option>)}</select><label className="flex gap-2 text-sm"><input name="primary" type="checkbox" /> Primary Department</label><input name="expiresAt" type="datetime-local" className="w-full rounded-xl border border-slate-300 px-3 py-2" /><AuditReasonInput /><button className="w-full rounded-xl bg-blue-700 px-3 py-2 font-black text-white">Add Department</button></form>
          </div>
          <div className="space-y-3"><h3 className="font-black">Groups</h3>{groupMemberships.map(item => <div key={item.membershipId} className="rounded-xl border border-slate-200 p-3 text-sm"><b>{groups.find(g => g.groupId === item.resourceId)?.name ?? item.resourceId}</b><div className="text-xs text-slate-500">{item.role} · {item.status}</div>{hasPermission('identity.membership.manage') ? <div className="mt-2 flex flex-wrap gap-2"><button type="button" disabled={busy} onClick={() => void updateGroupMembershipRole(item)} className="rounded-lg border border-blue-300 px-2 py-1 text-xs font-black text-blue-800">Change to {item.role === 'LEAD' ? 'Member' : 'Lead'}</button><button type="button" disabled={busy} onClick={() => void removeGroupMembership(item)} className="rounded-lg border border-rose-300 px-2 py-1 text-xs font-black text-rose-800">Remove</button></div> : null}</div>)}
            <form onSubmit={addGroup} className="space-y-2 rounded-2xl bg-slate-50 p-3"><select name="groupId" required className="w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">Select Group</option>{groups.map(item => <option key={item.groupId} value={item.groupId}>{item.name}</option>)}</select><select name="membershipRole" className="w-full rounded-xl border border-slate-300 px-3 py-2"><option value="MEMBER">Member</option><option value="LEAD">Lead</option></select><input name="expiresAt" type="datetime-local" className="w-full rounded-xl border border-slate-300 px-3 py-2" /><AuditReasonInput /><button className="w-full rounded-xl bg-blue-700 px-3 py-2 font-black text-white">Add Group</button></form>
          </div>
        </div> : null}

        {detailTab === 'ACCESS' ? allTenants ? <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Select a Tenant to inspect scoped Role Bindings and Effective Access.</div> : <div className="mt-4 space-y-3">
          {overview?.effectiveAccess.conflicts?.length ? <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{overview.effectiveAccess.conflicts.join(' · ')}</div> : null}
          {overview?.effectiveAccess.permissions.map(permission => <details key={permission.permissionCode} className="rounded-xl border border-slate-200 p-3"><summary className="cursor-pointer font-black text-slate-900">{permission.permissionCode} <span className="text-xs font-normal text-slate-500">{permission.sources.some(source => source.inheritedFromGroup) ? 'Direct / inherited sources' : 'Direct source'}</span></summary><div className="mt-2 space-y-2 text-xs text-slate-600">{permission.sources.map(source => <div key={`${permission.permissionCode}-${source.bindingId}`} className="rounded-lg bg-slate-50 p-2">{source.roleName} · {source.scopeType}:{source.scopeId} · {source.inheritedFromGroup ? `Inherited from Group ${source.principalId}` : `Direct ${source.principalType}`}</div>)}</div></details>)}
          {!overview?.effectiveAccess.permissions.length ? <EmptyState>No effective permissions.</EmptyState> : null}
        </div> : null}

        {detailTab === 'SECURITY' ? <div className="mt-4 space-y-4">
          <div className="grid gap-2 sm:grid-cols-3">
            <button disabled={busy} onClick={() => void run(() => allTenants ? accessManagementApi.requirePasswordChange(selected.userId, 'Root required password rotation from unified Access Management.') : accessManagementApi.resetPassword(scopeTenantId, selected.userId, 'Administrator initiated password reset from unified Access Management.'), 'Password change/reset action recorded.')} className="rounded-xl border border-amber-300 px-3 py-2 text-sm font-black text-amber-900">Reset password</button>
            {!allTenants ? <button disabled={busy} onClick={() => void run(() => accessManagementApi.resetMfa(scopeTenantId, selected.userId, 'Administrator reset MFA after identity verification.'), 'MFA reset completed.')} className="rounded-xl border border-amber-300 px-3 py-2 text-sm font-black text-amber-900">Reset MFA</button> : null}
            <button disabled={busy} onClick={() => void run(() => allTenants ? accessManagementApi.revokeAllUserSessions(selected.userId, 'Root revoked all sessions from unified Access Management.') : accessManagementApi.revokeUserSessions(scopeTenantId, selected.userId, 'Administrator revoked all sessions after security review.'), 'All user sessions revoked.')} className="rounded-xl border border-rose-300 px-3 py-2 text-sm font-black text-rose-900">Revoke sessions</button>
          </div>
          {!allTenants ? <div className="space-y-2">{overview?.sessions.map(session => <div key={session.sessionId} className="rounded-xl border border-slate-200 p-3 text-sm"><div className="flex justify-between gap-3"><b>{session.userAgent || session.methods.join(', ')}</b><StatusPill value={session.status} /></div><div className="mt-1 text-xs text-slate-500">{session.ipAddress} · last seen {session.lastSeenAt}</div></div>)}</div> : null}
        </div> : null}
      </Panel>}
    </div>
  </div>;
}
