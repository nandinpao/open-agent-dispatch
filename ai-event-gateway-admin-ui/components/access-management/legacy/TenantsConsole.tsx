'use client';

import type { FormEvent } from 'react';
import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Membership, Tenant, User } from '@/lib/iam/types';
import { formatIamError } from '@/lib/iam/errorPresentation';
import { useAccessManagement } from '../AccessManagementProvider';
import { AuditReasonInput, EmptyState, ErrorNotice, Panel, StatusPill, SuccessNotice, TenantRequired } from '../ui';

const field = (data: FormData, name: string) => String(data.get(name) ?? '').trim();

export function TenantsConsole() {
  const { hasPermission } = useAuth();
  const canReadInstanceTenants = hasPermission('instance.tenant.read');
  const { scopeTenantId, tenants, refreshTenants } = useAccessManagement();
  const [selected, setSelected] = useState<Tenant | null>(null);
  const [tenantUsers, setTenantUsers] = useState<User[]>([]);
  const [availableUsers, setAvailableUsers] = useState<User[]>([]);
  const [identitySearch, setIdentitySearch] = useState('');
  const [membershipUserId, setMembershipUserId] = useState('');
  const [memberships, setMemberships] = useState<Membership[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  const currentTenantId = scopeTenantId || selected?.tenantId || '';
  const selectedTenantMembership = memberships.find(item => item.membershipType === 'TENANT' && item.userId === membershipUserId) ?? null;

  const loadTenantDetails = useCallback(async () => {
    if (!currentTenantId) { setTenantUsers([]); setSelected(null); return; }
    setError('');
    try {
      const [tenant, users, memberPage] = await Promise.all([
        accessManagementApi.tenant(currentTenantId),
        accessManagementApi.tenantUsers(currentTenantId, 200),
        accessManagementApi.tenantMembers(currentTenantId, 250),
      ]);
      setSelected(tenant);
      setTenantUsers(users.items);
      setMemberships(memberPage.items);
      setMembershipUserId(current => current && memberPage.items.some(item => item.userId === current)
        ? current
        : memberPage.items[0]?.userId ?? '');
    } catch (cause) { setError(formatIamError(cause, 'Unable to load Tenant details.')); }
  }, [currentTenantId]);

  useEffect(() => { void loadTenantDetails(); }, [loadTenantDetails]);

  async function searchAvailableUsers() {
    if (!currentTenantId || identitySearch.trim().length < 2) {
      setAvailableUsers([]);
      setError('Enter at least two characters to search global identities that are not yet members of this Tenant.');
      return;
    }
    setError('');
    try {
      const page = await accessManagementApi.availableTenantUsers(currentTenantId, identitySearch.trim(), 100);
      setAvailableUsers(page.items);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to search global identities.'));
    }
  }

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setError(''); setNotice('');
    try { await action(); setNotice(success); await refreshTenants(); await loadTenantDetails(); }
    catch (cause) { setError(formatIamError(cause, 'Tenant action failed.')); }
    finally { setBusy(false); }
  }

  async function createTenant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const reason = field(data, 'auditReason');
    setBusy(true); setError(''); setNotice('');
    try {
      const created = await accessManagementApi.createTenant({
        tenantId: null, tenantCode: field(data, 'tenantCode'), tenantName: field(data, 'tenantName'), legalName: field(data, 'legalName'),
        timezone: field(data, 'timezone'), locale: field(data, 'locale'), dataRegion: field(data, 'dataRegion'),
      }, reason);
      await refreshTenants();
      setSelected(created);
      setNotice('Tenant created. No user or Access Role was assigned automatically.');
      form.reset();
    } catch (cause) { setError(formatIamError(cause, 'Tenant creation failed.')); }
    finally { setBusy(false); }
  }

  async function updateMembership(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!currentTenantId || !selectedTenantMembership) return;
    const data = new FormData(event.currentTarget); const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.updateTenantMembership(currentTenantId, selectedTenantMembership.membershipId, {
      employeeId: field(data, 'employeeId') || null,
      expiresAt: field(data, 'expiresAt') ? new Date(field(data, 'expiresAt')).toISOString() : null,
      defaultTenant: data.get('defaultTenant') === 'on',
      reason,
    }, selectedTenantMembership.version, reason), 'Tenant Membership updated. No Access Role changed.');
  }

  async function changeMembershipStatus(status: 'ACTIVE'|'SUSPENDED'|'REMOVED') {
    if (!currentTenantId || !selectedTenantMembership) return;
    const reason = `Tenant Membership changed to ${status} from unified Access Management after membership review.`;
    await run(() => accessManagementApi.changeTenantMembershipStatus(currentTenantId, selectedTenantMembership.membershipId, status, selectedTenantMembership.version, reason), `Tenant Membership changed to ${status}.`);
  }

  async function createMembership(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!currentTenantId) return;
    const form = event.currentTarget; const data = new FormData(form); const reason = field(data, 'auditReason');
    await run(() => accessManagementApi.addTenantMember(currentTenantId, {
      membershipId: null,
      userId: field(data, 'userId'),
      initialStatus: 'ACTIVE',
      employeeId: field(data, 'employeeId') || null,
      expiresAt: field(data, 'expiresAt') ? new Date(field(data, 'expiresAt')).toISOString() : null,
      defaultTenant: data.get('defaultTenant') === 'on',
      membershipSource: 'ADMIN_CREATED',
      reason,
    }, reason), 'Tenant Membership created. Membership grants admission only, not administrative authority.');
    form.reset();
    setAvailableUsers([]);
    setIdentitySearch('');
  }

  if (!canReadInstanceTenants && !scopeTenantId) return <TenantRequired />;

  return <div className="space-y-5">
    <ErrorNotice message={error} /><SuccessNotice message={notice} />
    <div className="grid gap-5 xl:grid-cols-[minmax(380px,.8fr)_minmax(0,1.2fr)]">
      <div className="space-y-5">
        <Panel title="Tenant directory" description="Hard-isolated administration boundaries. Root may create and review all Tenants.">
          <div className="space-y-2">{tenants.length ? tenants.map(item => <button key={item.tenantId} onClick={() => setSelected(item)} className={`flex w-full items-center justify-between rounded-xl border p-3 text-left ${currentTenantId === item.tenantId ? 'border-blue-300 bg-blue-50' : 'border-slate-200 hover:bg-slate-50'}`}><span><b className="block">{item.tenantName}</b><span className="text-xs text-slate-500">{item.tenantCode} · {item.tenantId}</span></span><StatusPill value={item.status} /></button>) : <EmptyState>No Tenant records are visible.</EmptyState>}</div>
        </Panel>
        {canReadInstanceTenants && hasPermission('instance.tenant.manage') ? <Panel title="Create Tenant" description="Creates the boundary only. The first administrator is assigned separately through Membership and Role Binding.">
          <form onSubmit={createTenant} className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm font-bold text-slate-700">Tenant code<input name="tenantCode" required placeholder="Example: VN01" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="text-sm font-bold text-slate-700">Tenant name<input name="tenantName" required placeholder="Example: Vietnam Company" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="text-sm font-bold text-slate-700 sm:col-span-2">Legal name<input name="legalName" placeholder="Registered legal entity name" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2" /></label>
            <label className="text-sm font-bold text-slate-700">Time zone<select name="timezone" required defaultValue="Asia/Taipei" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="Asia/Taipei">Asia/Taipei</option><option value="Asia/Ho_Chi_Minh">Asia/Ho Chi Minh</option><option value="Asia/Shanghai">Asia/Shanghai</option><option value="Asia/Tokyo">Asia/Tokyo</option><option value="UTC">UTC</option></select></label>
            <label className="text-sm font-bold text-slate-700">Default language<select name="locale" required defaultValue="en-US" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="en-US">English (United States)</option><option value="zh-TW">Traditional Chinese</option><option value="zh-CN">Simplified Chinese</option><option value="vi-VN">Vietnamese</option><option value="ja-JP">Japanese</option></select></label>
            <label className="text-sm font-bold text-slate-700 sm:col-span-2">Data region<select name="dataRegion" required defaultValue="TW" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2"><option value="TW">Taiwan</option><option value="VN">Vietnam</option><option value="CN">China</option><option value="JP">Japan</option><option value="GLOBAL">Global</option></select></label>
            <p className="text-xs leading-5 text-slate-500 sm:col-span-2">The internal Tenant ID is generated by the server. Administrators do not enter database identifiers.</p>
            <div className="sm:col-span-2"><AuditReasonInput /></div>
            <button disabled={busy} className="rounded-xl bg-blue-700 px-4 py-3 font-black text-white sm:col-span-2">Create Tenant</button>
          </form>
        </Panel> : null}
      </div>

      <div className="space-y-5">
        {!currentTenantId || !selected ? <Panel title="Tenant detail"><EmptyState>Select a Tenant.</EmptyState></Panel> : <>
          <Panel title={selected.tenantName} description={`${selected.tenantCode} · ${selected.tenantId}`} actions={<StatusPill value={selected.status} />}>
            <dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="font-black text-slate-500">Legal name</dt><dd>{selected.legalName || '—'}</dd></div><div><dt className="font-black text-slate-500">Region</dt><dd>{selected.dataRegion}</dd></div><div><dt className="font-black text-slate-500">Time zone</dt><dd>{selected.timezone}</dd></div><div><dt className="font-black text-slate-500">Locale</dt><dd>{selected.locale}</dd></div></dl>
            {canReadInstanceTenants && hasPermission('instance.tenant.manage') ? <button disabled={busy} onClick={() => void run(() => accessManagementApi.changeTenantStatus(selected.tenantId, selected.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE', selected.version, `Root changed Tenant ${selected.tenantCode} status after governance review.`), `Tenant changed to ${selected.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'}.`)} className="mt-4 rounded-xl border border-amber-300 px-4 py-2 text-sm font-black text-amber-900">{selected.status === 'ACTIVE' ? 'Suspend Tenant' : 'Activate Tenant'}</button> : null}
          </Panel>

          <Panel title="Tenant Memberships" description="Membership admits a global identity to this Tenant. It does not assign an Access Role.">
            <div className="overflow-x-auto rounded-2xl border border-slate-200"><table className="min-w-full text-left text-sm"><thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="p-3">User</th><th className="p-3">Status</th><th className="p-3">Membership</th></tr></thead><tbody>{memberships.filter(item => item.membershipType === 'TENANT').map(membership => {
              const user = tenantUsers.find(item => item.userId === membership.userId);
              return <tr key={membership.membershipId} className="border-t"><td className="p-3"><button onClick={() => setMembershipUserId(membership.userId)} className="font-black text-blue-700">{user?.displayName ?? membership.userId}</button><div className="text-xs text-slate-500">{user?.username ?? 'Identity details unavailable'}</div></td><td className="p-3"><StatusPill value={membership.status} /></td><td className="p-3 text-xs">{membership.employeeId || 'No employee ID'}{membership.primary ? ' · Default Tenant' : ''}</td></tr>;
            })}</tbody></table></div>
            {selectedTenantMembership && hasPermission('identity.tenant_membership.manage') ? <form key={`${selectedTenantMembership.membershipId}:${selectedTenantMembership.version}`} onSubmit={updateMembership} className="mt-4 grid gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2">
              <div className="sm:col-span-2"><b>Manage selected Membership</b><p className="text-xs text-slate-500">{tenantUsers.find(user => user.userId === membershipUserId)?.displayName} · {selectedTenantMembership.status}</p></div>
              <input name="employeeId" defaultValue={selectedTenantMembership.employeeId} placeholder="Employee ID" className="rounded-xl border border-slate-300 px-3 py-2" />
              <input name="expiresAt" type="datetime-local" defaultValue={selectedTenantMembership.expiresAt ? selectedTenantMembership.expiresAt.slice(0,16) : ''} className="rounded-xl border border-slate-300 px-3 py-2" />
              <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2 text-sm font-bold sm:col-span-2"><input name="defaultTenant" type="checkbox" defaultChecked={selectedTenantMembership.primary} /> Default Tenant</label>
              <div className="sm:col-span-2"><AuditReasonInput /></div>
              <button disabled={busy} className="rounded-xl bg-slate-950 px-4 py-2 font-black text-white sm:col-span-2">Update Membership</button>
              <div className="flex flex-wrap gap-2 sm:col-span-2"><button type="button" disabled={busy || selectedTenantMembership.status === 'ACTIVE'} onClick={() => void changeMembershipStatus('ACTIVE')} className="rounded-xl border border-emerald-300 px-3 py-2 text-xs font-black text-emerald-800 disabled:opacity-40">Activate</button><button type="button" disabled={busy || selectedTenantMembership.status === 'SUSPENDED'} onClick={() => void changeMembershipStatus('SUSPENDED')} className="rounded-xl border border-amber-300 px-3 py-2 text-xs font-black text-amber-900 disabled:opacity-40">Suspend</button><button type="button" disabled={busy || selectedTenantMembership.status === 'REMOVED'} onClick={() => void changeMembershipStatus('REMOVED')} className="rounded-xl border border-rose-300 px-3 py-2 text-xs font-black text-rose-900 disabled:opacity-40">Remove admission</button></div>
            </form> : null}
            {hasPermission('identity.tenant_membership.manage') ? <form onSubmit={createMembership} className="mt-4 grid gap-3 rounded-2xl border border-blue-200 bg-blue-50 p-4 sm:grid-cols-2">
              <div className="flex gap-2 sm:col-span-2"><input value={identitySearch} onChange={event => setIdentitySearch(event.target.value)} placeholder="Search global identity by username, name or email" className="min-w-0 flex-1 rounded-xl border border-slate-300 px-3 py-2" /><button type="button" onClick={() => void searchAvailableUsers()} disabled={busy || identitySearch.trim().length < 2} className="rounded-xl border border-blue-300 bg-white px-4 py-2 text-sm font-black text-blue-800 disabled:opacity-50">Search identities</button></div>
              <select name="userId" required className="rounded-xl border border-slate-300 px-3 py-2 sm:col-span-2"><option value="">Select identity not yet admitted to this Tenant</option>{availableUsers.map(user => <option key={user.userId} value={user.userId}>{user.displayName} ({user.username})</option>)}</select>
              {!availableUsers.length ? <p className="text-xs text-slate-600 sm:col-span-2">Search first. Existing Tenant members are intentionally excluded from this selector.</p> : null}
              <input name="employeeId" placeholder="Employee ID" className="rounded-xl border border-slate-300 px-3 py-2" />
              <input name="expiresAt" type="datetime-local" className="rounded-xl border border-slate-300 px-3 py-2" />
              <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2 text-sm font-bold"><input name="defaultTenant" type="checkbox" /> Default Tenant</label>
              <div className="sm:col-span-2"><AuditReasonInput /></div><button disabled={busy} className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white sm:col-span-2">Create Membership</button>
            </form> : null}
          </Panel>
        </>}
      </div>
    </div>
  </div>;
}
