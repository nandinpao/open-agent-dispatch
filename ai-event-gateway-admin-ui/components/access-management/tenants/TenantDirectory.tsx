'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/components/auth/AuthProvider';
import { useAccessManagement } from '../AccessManagementProvider';
import { HumanStatus, SearchField, SelectField } from '../shared/beginnerUi';
import { CreateTenantDialog } from './CreateTenantDialog';
import { WorkspaceEmpty, WorkspaceLoading } from '../shared/workspaceUi';

export function TenantDirectory() {
  const router = useRouter();
  const { hasPermission } = useAuth();
  const { instanceRoot, tenants, scopeTenantId, loadingTenants, setScopeTenantId } = useAccessManagement();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [creating, setCreating] = useState(false);
  const canCreate = instanceRoot && hasPermission('instance.tenant.manage');

  useEffect(() => {
    if (!instanceRoot && scopeTenantId) router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}`);
  }, [instanceRoot, router, scopeTenantId]);

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    return tenants.filter((tenant) => (!status || tenant.status === status) && (!term || `${tenant.tenantName} ${tenant.tenantCode} ${tenant.legalName} ${tenant.dataRegion}`.toLowerCase().includes(term)));
  }, [query, status, tenants]);

  async function openTenant(tenantId: string) {
    await setScopeTenantId(tenantId);
  }

  if (loadingTenants && tenants.length === 0) return <WorkspaceLoading title="Loading Companies / business units" description="Retrieving the companies and business units you can administer." />;

  return (
    <div className="space-y-5">
      <section className="rounded-3xl border border-blue-200 bg-gradient-to-br from-blue-50 via-white to-cyan-50 p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div><p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Companies / business units</p><h2 className="mt-2 text-2xl font-black text-slate-950">Choose the company or business unit to manage</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-700">Each company or business unit opens one workspace with Overview, People, Organization, Access, and Security & Audit. Most daily administration stays inside that workspace.</p></div>
          {canCreate ? <button type="button" onClick={() => setCreating(true)} className="rounded-xl bg-blue-700 px-5 py-3 text-sm font-black text-white shadow-sm">Create workspace</button> : null}
        </div>
      </section>

      {tenants.length ? <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px]"><SearchField id="tenant-tenant-search" value={query} onChange={setQuery} placeholder="Search by company / business-unit name, code, legal name or region" /><SelectField id="tenant-tenant-status" value={status} onChange={setStatus} options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'SUSPENDED', label: 'Suspended' }, { value: 'DISABLED', label: 'Disabled' }]} placeholder="All statuses" /></div></section> : null}

      {!tenants.length ? <WorkspaceEmpty title="No workspace exists yet" description="Create the first company or business-unit workspace, then continue directly to its guided setup." action={canCreate ? <button type="button" onClick={() => setCreating(true)} className="rounded-xl bg-blue-700 px-5 py-3 text-sm font-black text-white">Create the first workspace</button> : undefined} /> : filtered.length ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((tenant) => <button key={tenant.tenantId} type="button" onClick={() => { void openTenant(tenant.tenantId); }} className="group rounded-3xl border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-blue-400 hover:shadow-md">
            <div className="flex items-start justify-between gap-3"><div><h3 className="text-lg font-black text-slate-950 group-hover:text-blue-800">{tenant.tenantName}</h3><p className="mt-1 text-sm font-bold text-slate-500">{tenant.tenantCode}</p></div><HumanStatus value={tenant.status} /></div>
            <p className="mt-4 min-h-10 text-sm leading-5 text-slate-600">{tenant.legalName || 'No legal name has been recorded.'}</p>
            <dl className="mt-4 grid grid-cols-2 gap-3 rounded-2xl bg-slate-50 p-3 text-xs"><div><dt className="font-black text-slate-500">Region</dt><dd className="mt-1 font-bold text-slate-900">{tenant.dataRegion}</dd></div><div><dt className="font-black text-slate-500">Time zone</dt><dd className="mt-1 font-bold text-slate-900">{tenant.timezone}</dd></div><div><dt className="font-black text-slate-500">Language</dt><dd className="mt-1 font-bold text-slate-900">{tenant.locale}</dd></div><div><dt className="font-black text-slate-500">Updated</dt><dd className="mt-1 font-bold text-slate-900">{tenant.updatedAt ? new Date(tenant.updatedAt).toLocaleDateString() : '—'}</dd></div></dl>
            <span className="mt-5 inline-flex items-center gap-2 text-sm font-black text-blue-700">Open workspace <span aria-hidden="true">→</span></span>
          </button>)}
        </div>
      ) : <WorkspaceEmpty title="No workspace matches these filters" description="No company or business unit matches both the current search and status filter." nextAction="Clear the filters, then search by business name or code." action={<button type="button" onClick={() => { setQuery(''); setStatus(''); }} className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-black text-white">Clear filters</button>} />}

      <CreateTenantDialog open={creating} onClose={() => setCreating(false)} />
    </div>
  );
}
