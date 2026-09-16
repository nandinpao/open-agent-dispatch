'use client';

import type { FormEvent } from 'react';
import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Department, Membership, User } from '@/lib/iam/types';
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

interface TreeNode { department: Department; children: TreeNode[]; }

function departmentTree(items: Department[]): TreeNode[] {
  const nodes = new Map(items.map(item => [item.departmentId, { department: item, children: [] as TreeNode[] }]));
  const roots: TreeNode[] = [];
  nodes.forEach(node => {
    const parent = node.department.parentDepartmentId ? nodes.get(node.department.parentDepartmentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  const sort = (list: TreeNode[]) => {
    list.sort((a, b) => a.department.displayOrder - b.department.displayOrder || a.department.name.localeCompare(b.department.name));
    list.forEach(item => sort(item.children));
  };
  sort(roots);
  return roots;
}

function Tree({ nodes, selectedId, onSelect, depth = 0 }: Readonly<{
  nodes: TreeNode[];
  selectedId: string;
  onSelect: (id: string) => void;
  depth?: number;
}>) {
  return <>{nodes.map(node => <div key={node.department.departmentId}>
    <button
      type="button"
      onClick={() => onSelect(node.department.departmentId)}
      style={{ paddingLeft: `${12 + depth * 18}px` }}
      className={`flex w-full items-center justify-between rounded-xl py-2 pr-3 text-left text-sm ${selectedId === node.department.departmentId ? 'bg-blue-50 text-blue-950' : 'hover:bg-slate-50'}`}
    >
      <span><b>{node.department.name}</b><span className="ml-2 text-xs text-slate-400">{node.department.code}</span></span>
      <StatusPill value={node.department.status} />
    </button>
    <Tree nodes={node.children} selectedId={selectedId} onSelect={onSelect} depth={depth + 1} />
  </div>)}</>;
}

export function DepartmentsConsole() {
  const { hasPermission } = useAuth();
  const { scopeTenantId } = useAccessManagement();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [members, setMembers] = useState<Membership[]>([]);
  const [eligibleManagers, setEligibleManagers] = useState<User[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const selected = departments.find(item => item.departmentId === selectedId) ?? null;
  const tree = useMemo(() => departmentTree(departments), [departments]);
  const memberUserIds = useMemo(() => new Set(members.map(item => item.userId)), [members]);
  const availableUsers = users.filter(user => user.status === 'ACTIVE' && !memberUserIds.has(user.userId));

  const load = useCallback(async () => {
    if (!scopeTenantId) return;
    setError('');
    try {
      const [departmentPage, userPage] = await Promise.all([
        accessManagementApi.departments(scopeTenantId),
        accessManagementApi.tenantUsers(scopeTenantId, 250),
      ]);
      setDepartments(departmentPage.items);
      setUsers(userPage.items);
      setSelectedId(current => current && departmentPage.items.some(item => item.departmentId === current)
        ? current
        : departmentPage.items[0]?.departmentId ?? '');
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load Department hierarchy.'));
    }
  }, [scopeTenantId]);

  const loadSelectedDetails = useCallback(async () => {
    if (!scopeTenantId || !selectedId) {
      setMembers([]);
      setEligibleManagers([]);
      return;
    }
    setDetailLoading(true);
    try {
      const [memberPage, managerPage] = await Promise.all([
        accessManagementApi.departmentMembers(scopeTenantId, selectedId, 200),
        accessManagementApi.eligibleDepartmentManagers(scopeTenantId, selectedId, '', 100),
      ]);
      setMembers(memberPage.items);
      setEligibleManagers(managerPage.items);
    } catch (cause) {
      setMembers([]);
      setEligibleManagers([]);
      setError(formatIamError(cause, 'Unable to load Department members.'));
    } finally {
      setDetailLoading(false);
    }
  }, [scopeTenantId, selectedId]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void loadSelectedDetails(); }, [loadSelectedDetails]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(success);
      await Promise.all([load(), loadSelectedDetails()]);
    } catch (cause) {
      setError(formatIamError(cause, 'Department action failed.'));
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
      const created = await accessManagementApi.createDepartment(scopeTenantId, {
        departmentId: null,
        code: field(data, 'code'),
        name: field(data, 'name'),
        parentDepartmentId: field(data, 'parentDepartmentId') || null,
        managerUserId: null,
        displayOrder: Number(field(data, 'displayOrder') || 0),
        reason,
      }, reason);
      setSelectedId(created.departmentId);
      form.reset();
    }, 'Department created. Add members before selecting its official Manager.');
  }

  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const data = new FormData(event.currentTarget);
    const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.updateDepartment(scopeTenantId, selected.departmentId, {
      code: field(data, 'code'),
      name: field(data, 'name'),
      parentDepartmentId: field(data, 'parentDepartmentId') || null,
      managerUserId: selected.managerUserId || null,
      displayOrder: Number(field(data, 'displayOrder') || 0),
      reason,
    }, selected.version, reason), 'Department updated.');
  }

  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.addDepartmentMember(scopeTenantId, selected.departmentId, {
      membershipId: null,
      userId: field(data, 'userId'),
      membershipType: field(data, 'membershipType'),
      primary: data.get('primary') === 'on',
      expiresAt: dateValue(field(data, 'expiresAt')),
    }, reason), 'Person added to the Department. No Access Role was assigned.');
    form.reset();
  }


  async function removeMember(member: Membership) {
    const person = users.find(user => user.userId === member.userId);
    const reason = `Removed ${person?.displayName || member.userId} from Department ${selected?.name || member.resourceId}.`;
    await run(() => accessManagementApi.removeDepartmentMembership(
      scopeTenantId,
      member.membershipId,
      member.version,
      reason,
    ), 'Person removed from the Department.');
  }

  async function changeStatus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const data = new FormData(event.currentTarget);
    const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.changeDepartmentStatus(
      scopeTenantId,
      selected.departmentId,
      field(data, 'status'),
      selected.version,
      reason,
    ), 'Department lifecycle status updated.');
  }

  async function assignManager(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const data = new FormData(event.currentTarget);
    const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.assignOfficialManager(
      scopeTenantId,
      selected.departmentId,
      field(data, 'managerUserId'),
      selected.version,
      reason,
    ), 'Official Department Manager updated. No RBAC Role was assigned.');
  }

  if (!scopeTenantId) return <TenantRequired />;

  const relatedHref = '/access-management/organization/groups?tenantId=' + encodeURIComponent(scopeTenantId);

  return <div className="space-y-5">
    <div className="flex flex-col gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-slate-600">Groups are managed in the same Tenant workspace.</p>
      <Link href={relatedHref} className="rounded-xl bg-white px-3 py-2 text-center text-sm font-black text-blue-700 ring-1 ring-slate-200 hover:ring-blue-300">Open Groups</Link>
    </div>
    <ErrorNotice message={error} />
    <SuccessNotice message={notice} />
    <div className="grid gap-5 xl:grid-cols-[minmax(320px,.75fr)_minmax(0,1.25fr)]">
      <div className="space-y-5">
        <Panel title="Organization" description="Select a Department to manage its details, people and Manager in one place.">
          {tree.length ? <Tree nodes={tree} selectedId={selectedId} onSelect={setSelectedId} /> : <EmptyState>No Departments created.</EmptyState>}
        </Panel>
        {hasPermission('identity.department.manage') ? <Panel title="Create Department">
          <form onSubmit={create} className="space-y-3">
            <label className="block text-sm font-bold text-slate-700">Department code<input name="code" required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="block text-sm font-bold text-slate-700">Department name<input name="name" required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="block text-sm font-bold text-slate-700">Parent Department<select name="parentDepartmentId" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">Top level</option>{departments.map(item => <option key={item.departmentId} value={item.departmentId}>{item.name}</option>)}</select></label>
            <label className="block text-sm font-bold text-slate-700">Display order<input name="displayOrder" type="number" min="0" defaultValue="0" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <AuditReasonInput />
            <button disabled={busy} className="w-full rounded-xl bg-blue-700 px-4 py-2 font-black text-white disabled:bg-slate-300">Create Department</button>
          </form>
        </Panel> : null}
      </div>

      <div>{!selected ? <Panel title="Department detail"><EmptyState>Select a Department.</EmptyState></Panel> : <div className="space-y-5">
        <Panel title={selected.name} description={selected.code} actions={<StatusPill value={selected.status} />}>
          <form onSubmit={update} className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm font-bold text-slate-700">Code<input name="code" defaultValue={selected.code} required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="text-sm font-bold text-slate-700">Name<input name="name" defaultValue={selected.name} required className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="text-sm font-bold text-slate-700">Parent<select name="parentDepartmentId" defaultValue={selected.parentDepartmentId} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">Top level</option>{departments.filter(item => item.departmentId !== selected.departmentId).map(item => <option key={item.departmentId} value={item.departmentId}>{item.name}</option>)}</select></label>
            <label className="text-sm font-bold text-slate-700">Display order<input name="displayOrder" type="number" min="0" defaultValue={selected.displayOrder} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <div className="sm:col-span-2"><AuditReasonInput /></div>
            <button disabled={busy || !hasPermission('identity.department.manage')} className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white sm:col-span-2 disabled:bg-slate-300">Save Department</button>
          </form>
          {hasPermission('identity.department.manage') ? <form onSubmit={changeStatus} className="mt-4 grid gap-3 border-t border-slate-200 pt-4 sm:grid-cols-2">
            <label className="text-sm font-bold text-slate-700">Lifecycle status<select name="status" defaultValue={selected.status} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="ARCHIVED">Archived</option></select></label>
            <div><AuditReasonInput /></div>
            <button disabled={busy} className="rounded-xl border border-slate-300 bg-white px-4 py-2 font-black text-slate-800 sm:col-span-2">Update lifecycle</button>
          </form> : null}
        </Panel>

        <Panel title="People" description="Department membership is an organization relationship. It does not grant permissions.">
          {detailLoading ? <p className="text-sm text-slate-500">Loading people…</p> : members.length ? <div className="space-y-2">{members.map(member => {
            const person = users.find(user => user.userId === member.userId);
            return <div key={member.membershipId} className="flex items-center justify-between rounded-xl border border-slate-200 p-3">
              <div><b className="block">{person?.displayName || member.userId}</b><span className="text-xs text-slate-500">{member.role}{member.primary ? ' · Primary Department' : ''}</span></div>
              <div className="flex items-center gap-2"><StatusPill value={member.status} />{hasPermission('identity.membership.manage') ? <button type="button" onClick={() => void removeMember(member)} disabled={busy} className="rounded-lg border border-rose-200 px-2 py-1 text-xs font-black text-rose-700 disabled:opacity-40">Remove</button> : null}</div>
            </div>;
          })}</div> : <EmptyState>No people in this Department.</EmptyState>}

          {hasPermission('identity.membership.manage') ? <form onSubmit={addMember} className="mt-4 grid gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2">
            <label className="text-sm font-bold text-slate-700 sm:col-span-2">Person<select name="userId" required className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2"><option value="">Select a Tenant member</option>{availableUsers.map(user => <option key={user.userId} value={user.userId}>{user.displayName} ({user.username})</option>)}</select></label>
            <label className="text-sm font-bold text-slate-700">Relationship<select name="membershipType" defaultValue="MEMBER" className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2"><option value="MEMBER">Member</option><option value="DELEGATE">Delegate</option></select></label>
            <label className="text-sm font-bold text-slate-700">Valid until<input name="expiresAt" type="datetime-local" className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2" /></label>
            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2 text-sm font-bold sm:col-span-2"><input name="primary" type="checkbox" />Set as primary Department</label>
            <div className="sm:col-span-2"><AuditReasonInput /></div>
            <button disabled={busy || availableUsers.length === 0} className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white sm:col-span-2 disabled:bg-slate-300">Add person</button>
          </form> : null}
        </Panel>

        <Panel title="Official Manager" description="Select from active Department members. Manager remains separate from Access Roles.">
          <div className="mb-3 rounded-xl bg-slate-50 p-3 text-sm"><b>Current:</b> {users.find(user => user.userId === selected.managerUserId)?.displayName || selected.managerUserId || 'Not assigned'}</div>
          <form onSubmit={assignManager} className="space-y-3">
            <label className="block text-sm font-bold text-slate-700">Manager<select name="managerUserId" key={`${selected.departmentId}:${selected.managerUserId ?? ''}`} defaultValue={selected.managerUserId} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="">No official Manager</option>{eligibleManagers.map(user => <option key={user.userId} value={user.userId}>{user.displayName} ({user.username})</option>)}</select></label>
            <AuditReasonInput />
            <button disabled={busy || detailLoading || !hasPermission('identity.department.manage')} className="w-full rounded-xl bg-blue-700 px-4 py-2 font-black text-white disabled:bg-slate-300">Save Manager</button>
          </form>
        </Panel>
      </div>}</div>
    </div>
  </div>;
}
