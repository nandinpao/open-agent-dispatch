'use client';

import { useCallback, useEffect, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { EffectiveAccess, User } from '@/lib/iam/types';
import { formatIamError } from '@/lib/iam/errorPresentation';
import { useAccessManagement } from '../AccessManagementProvider';
import { EmptyState, ErrorNotice, Panel, TenantRequired } from '../ui';

export function EffectiveAccessConsole() {
  const { scopeTenantId } = useAccessManagement();
  const [users, setUsers] = useState<User[]>([]);
  const [userId, setUserId] = useState('');
  const [access, setAccess] = useState<EffectiveAccess | null>(null);
  const [error, setError] = useState('');

  const loadUsers = useCallback(async () => {
    if (!scopeTenantId) return;
    try {
      const page = await accessManagementApi.tenantUsers(scopeTenantId, 250);
      setUsers(page.items);
      setUserId(current => current && page.items.some(user => user.userId === current) ? current : page.items[0]?.userId ?? '');
    } catch (cause) { setError(formatIamError(cause, 'Unable to load users.')); }
  }, [scopeTenantId]);
  useEffect(() => { void loadUsers(); }, [loadUsers]);
  useEffect(() => {
    if (!scopeTenantId || !userId) { setAccess(null); return; }
    setError('');
    void accessManagementApi.effectiveAccess(scopeTenantId, userId).then(setAccess).catch(cause => setError(formatIamError(cause, 'Unable to calculate Effective Access.')));
  }, [scopeTenantId, userId]);

  if (!scopeTenantId) return <TenantRequired />;
  const selected = users.find(user => user.userId === userId);
  return <div className="space-y-5"><ErrorNotice message={error}/><Panel title="Effective Access Explorer" description="Explains every permission from direct User bindings and inherited Group bindings. Organization membership alone does not grant access." actions={<select value={userId} onChange={event=>setUserId(event.target.value)} className="min-w-64 rounded-xl border border-slate-300 px-3 py-2 text-sm"><option value="">Select user</option>{users.map(user=><option key={user.userId} value={user.userId}>{user.displayName} ({user.username})</option>)}</select>}>
    {selected?<div className="mb-4 rounded-2xl bg-slate-50 p-4 text-sm"><b>{selected.displayName}</b><span className="ml-2 text-slate-500">{selected.userId}</span></div>:null}
    {access?.conflicts.length?<div className="mb-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-800"><p className="font-black">Access observations / conflicts</p><ul className="mt-2 list-disc pl-5">{access.conflicts.map(item=><li key={item}>{item}</li>)}</ul></div>:null}
    {access?.permissions.length?<div className="space-y-3">{access.permissions.map(permission=><details key={permission.permissionCode} open className="rounded-2xl border border-slate-200 bg-white p-4"><summary className="cursor-pointer font-black text-slate-950">{permission.permissionCode}</summary><div className="mt-3 grid gap-3 lg:grid-cols-2">{permission.sources.map(source=><div key={`${permission.permissionCode}-${source.bindingId}`} className={`rounded-xl border p-3 text-sm ${source.inheritedFromGroup?'border-violet-200 bg-violet-50':'border-blue-200 bg-blue-50'}`}><div className="flex justify-between gap-2"><b>{source.roleName}</b><span className="text-xs font-black uppercase">{source.inheritedFromGroup?'Inherited':'Direct'}</span></div><dl className="mt-2 grid gap-1 text-xs"><div><dt className="inline font-bold">Principal: </dt><dd className="inline">{source.principalType} {source.principalId}</dd></div><div><dt className="inline font-bold">Scope: </dt><dd className="inline">{source.scopeType} {source.scopeId}</dd></div><div><dt className="inline font-bold">Binding: </dt><dd className="inline">{source.bindingId}</dd></div><div><dt className="inline font-bold">Expires: </dt><dd className="inline">{source.expiresAt||'Never'}</dd></div></dl></div>)}</div>{permission.observations.length?<p className="mt-3 text-xs text-amber-800">{permission.observations.join(' · ')}</p>:null}</details>)}</div>:<EmptyState>{userId?'No effective permissions were found.':'Select a user.'}</EmptyState>}
  </Panel></div>;
}
