'use client';

import type { FormEvent } from 'react';
import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Department, Group, Membership, User } from '@/lib/iam/types';
import { formatIamError } from '@/lib/iam/errorPresentation';
import { useAccessManagement } from '../AccessManagementProvider';
import {
  AuditReasonInput,
  EmptyState,
  ErrorNotice,
  Panel,
  StatusPill,
  SuccessNotice,
  TenantRequired,
} from '../ui';

const field = (data: FormData, name: string) => String(data.get(name) ?? '').trim();
const dateValue = (value: string) => value ? new Date(value).toISOString() : null;

export function GroupsConsole() {
  const { hasPermission } = useAuth();
  const { scopeTenantId } = useAccessManagement();
  const [groups, setGroups] = useState<Group[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [members, setMembers] = useState<Membership[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [membersLoading, setMembersLoading] = useState(false);
  const selected = groups.find(item => item.groupId === selectedId) ?? null;
  const memberIds = useMemo(() => new Set(members.map(item => item.userId)), [members]);
  const availableUsers = users.filter(user => user.status === 'ACTIVE' && !memberIds.has(user.userId));

  const load = useCallback(async () => {
    if (!scopeTenantId) return;
    setError('');
    try {
      const [groupPage, departmentPage, userPage] = await Promise.all([
        accessManagementApi.groups(scopeTenantId),
        accessManagementApi.departments(scopeTenantId),
        accessManagementApi.tenantUsers(scopeTenantId, 250),
      ]);
      setGroups(groupPage.items);
      setDepartments(departmentPage.items);
      setUsers(userPage.items);
      setSelectedId(current => current && groupPage.items.some(item => item.groupId === current)
        ? current
        : groupPage.items[0]?.groupId ?? '');
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load Groups.'));
    }
  }, [scopeTenantId]);

  const loadMembers = useCallback(async () => {
    if (!scopeTenantId || !selectedId) {
      setMembers([]);
      return;
    }
    setMembersLoading(true);
    try {
      const page = await accessManagementApi.groupMembers(scopeTenantId, selectedId, 200);
      setMembers(page.items);
    } catch (cause) {
      setMembers([]);
      setError(formatIamError(cause, 'Unable to load Group members.'));
    } finally {
      setMembersLoading(false);
    }
  }, [scopeTenantId, selectedId]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void loadMembers(); }, [loadMembers]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(success);
      await Promise.all([load(), loadMembers()]);
    } catch (cause) {
      setError(formatIamError(cause, 'Group action failed.'));
    } finally {
      setBusy(false);
    }
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const reason = field(data, 'auditReason');
    await run(async () => {
      const created = await accessManagementApi.createGroup(scopeTenantId, {
        groupId: null,
        code: field(data, 'code'),
        name: field(data, 'name'),
        type: field(data, 'type') || 'FUNCTIONAL',
        parentGroupId: field(data, 'parentGroupId') || null,
        ownerDepartmentId: field(data, 'ownerDepartmentId') || null,
        description: field(data, 'description'),
      }, reason);
      setSelectedId(created.groupId);
      form.reset();
    }, 'Group created. Add people without granting an Access Role.');
  }

  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const data = new FormData(event.currentTarget);
    const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.updateGroup(scopeTenantId, selected.groupId, {
      code: field(data, 'code'),
      name: field(data, 'name'),
      type: field(data, 'type'),
      parentGroupId: field(data, 'parentGroupId') || null,
      ownerDepartmentId: field(data, 'ownerDepartmentId') || null,
      description: field(data, 'description'),
      reason,
    }, selected.version, reason), 'Group updated.');
  }


  async function changeStatus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const data = new FormData(event.currentTarget);
    const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.changeGroupStatus(
      scopeTenantId,
      selected.groupId,
      field(data, 'status'),
      selected.version,
      reason,
    ), 'Group lifecycle status updated.');
  }

  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.addGroupMember(scopeTenantId, selected.groupId, {
      membershipId: null,
      userId: field(data, 'userId'),
      membershipRole: field(data, 'membershipRole'),
      expiresAt: dateValue(field(data, 'expiresAt')),
    }, reason), 'Person added to the Group. No Access Role was assigned.');
    form.reset();
  }

  async function removeMember(member: Membership) {
    const person = users.find(user => user.userId === member.userId);
    const reason = `Removed ${person?.displayName || member.userId} from Group ${selected?.name || member.resourceId}.`;
    await run(() => accessManagementApi.removeGroupMembership(scopeTenantId, member.membershipId, member.version, reason), 'Person removed from the Group.');
  }

  if (!scopeTenantId) return <TenantRequired />;

  const relatedHref = '/access-management/organization/departments?tenantId=' + encodeURIComponent(scopeTenantId);

  return <div className="space-y-5">
    <div className="flex flex-col gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-slate-600">Departments and reporting relationships are managed in the same Tenant workspace.</p>
      <Link href={relatedHref} className="rounded-xl bg-white px-3 py-2 text-center text-sm font-black text-blue-700 ring-1 ring-slate-200 hover:ring-blue-300">Open Departments</Link>
    </div>
    <ErrorNotice message={error} />
    <SuccessNotice message={notice} />
    <div className="grid gap-5 xl:grid-cols-[minmax(340px,.8fr)_minmax(0,1.2fr)]">
      <div className="space-y-5">
        <Panel title="Groups" description="Select a Group to manage its details and people in one place.">
          <div className="space-y-2">{groups.length ? groups.map(item => <button type="button" key={item.groupId} onClick={() => setSelectedId(item.groupId)} className={`flex w-full items-center justify-between rounded-xl border p-3 text-left ${selectedId === item.groupId ? 'border-blue-300 bg-blue-50' : 'border-slate-200 hover:bg-slate-50'}`}><span><b className="block">{item.name}</b><span className="text-xs text-slate-500">{item.code} · {item.type}</span></span><StatusPill value={item.status} /></button>) : <EmptyState>No Groups created.</EmptyState>}</div>
        </Panel>
        {hasPermission('identity.group.manage') ? <Panel title="Create Group">
          <form onSubmit={create} className="space-y-3">
            <label className="block text-sm font-bold text-slate-700">Group code<input name="code" required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="block text-sm font-bold text-slate-700">Group name<input name="name" required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="block text-sm font-bold text-slate-700">Type<select name="type" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="FUNCTIONAL">Functional</option><option value="GOVERNANCE">Governance</option><option value="PROJECT">Project</option><option value="SECURITY">Security</option></select></label>
            <label className="block text-sm font-bold text-slate-700">Owner Department<select name="ownerDepartmentId" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">No owner Department</option>{departments.map(item => <option key={item.departmentId} value={item.departmentId}>{item.name}</option>)}</select></label>
            <label className="block text-sm font-bold text-slate-700">Description<textarea name="description" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <AuditReasonInput />
            <button disabled={busy} className="w-full rounded-xl bg-blue-700 px-4 py-2 font-black text-white disabled:bg-slate-300">Create Group</button>
          </form>
        </Panel> : null}
      </div>

      <div>{!selected ? <Panel title="Group detail"><EmptyState>Select a Group.</EmptyState></Panel> : <div className="space-y-5">
        <Panel title={selected.name} description={selected.code} actions={<StatusPill value={selected.status} />}>
          <form onSubmit={update} className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm font-bold text-slate-700">Code<input name="code" defaultValue={selected.code} required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="text-sm font-bold text-slate-700">Name<input name="name" defaultValue={selected.name} required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="text-sm font-bold text-slate-700">Type<select name="type" defaultValue={selected.type} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="FUNCTIONAL">Functional</option><option value="GOVERNANCE">Governance</option><option value="PROJECT">Project</option><option value="SECURITY">Security</option></select></label>
            <label className="text-sm font-bold text-slate-700">Owner Department<select name="ownerDepartmentId" defaultValue={selected.ownerDepartmentId} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">No owner Department</option>{departments.map(item => <option key={item.departmentId} value={item.departmentId}>{item.name}</option>)}</select></label>
            <label className="text-sm font-bold text-slate-700 sm:col-span-2">Parent Group<select name="parentGroupId" defaultValue={selected.parentGroupId} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">No parent Group</option>{groups.filter(item => item.groupId !== selected.groupId).map(item => <option key={item.groupId} value={item.groupId}>{item.name}</option>)}</select></label>
            <label className="text-sm font-bold text-slate-700 sm:col-span-2">Description<textarea name="description" defaultValue={selected.description} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <div className="sm:col-span-2"><AuditReasonInput /></div>
            <button disabled={busy || !hasPermission('identity.group.manage')} className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white sm:col-span-2 disabled:bg-slate-300">Save Group</button>
          </form>
          {hasPermission('identity.group.manage') ? <form onSubmit={changeStatus} className="mt-4 grid gap-3 border-t border-slate-200 pt-4 sm:grid-cols-2">
            <label className="text-sm font-bold text-slate-700">Lifecycle status<select name="status" defaultValue={selected.status} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="ARCHIVED">Archived</option></select></label>
            <div><AuditReasonInput /></div>
            <button disabled={busy} className="rounded-xl border border-slate-300 bg-white px-4 py-2 font-black text-slate-800 sm:col-span-2">Update lifecycle</button>
          </form> : null}
        </Panel>

        <Panel title="People" description="MEMBER and LEAD are organization relationships. Access is assigned separately.">
          {membersLoading ? <p className="text-sm text-slate-500">Loading people…</p> : members.length ? <div className="space-y-2">{members.map(member => {
            const person = users.find(user => user.userId === member.userId);
            return <div key={member.membershipId} className="flex items-center justify-between rounded-xl border border-slate-200 p-3">
              <div><b className="block">{person?.displayName || member.userId}</b><span className="text-xs text-slate-500">{member.role}</span></div>
              <div className="flex items-center gap-2"><StatusPill value={member.status} />{hasPermission('identity.membership.manage') ? <button type="button" onClick={() => void removeMember(member)} disabled={busy} className="rounded-lg border border-rose-200 px-2 py-1 text-xs font-black text-rose-700 disabled:opacity-40">Remove</button> : null}</div>
            </div>;
          })}</div> : <EmptyState>No people in this Group.</EmptyState>}

          {hasPermission('identity.membership.manage') ? <form onSubmit={addMember} className="mt-4 grid gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2">
            <label className="text-sm font-bold text-slate-700 sm:col-span-2">Person<select name="userId" required className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2"><option value="">Select a Tenant member</option>{availableUsers.map(user => <option key={user.userId} value={user.userId}>{user.displayName} ({user.username})</option>)}</select></label>
            <label className="text-sm font-bold text-slate-700">Relationship<select name="membershipRole" defaultValue="MEMBER" className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2"><option value="MEMBER">Member</option><option value="LEAD">Lead</option></select></label>
            <label className="text-sm font-bold text-slate-700">Valid until<input name="expiresAt" type="datetime-local" className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2" /></label>
            <div className="sm:col-span-2"><AuditReasonInput /></div>
            <button disabled={busy || availableUsers.length === 0} className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white sm:col-span-2 disabled:bg-slate-300">Add person</button>
          </form> : null}
        </Panel>
      </div>}</div>
    </div>
  </div>;
}
