'use client';

import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { DataTableShell, TableEmptyRow } from '@/components/ui/DataTable';
import { HelpText } from '@/components/common/HelpText';
import { StatusBadge } from '@/components/common/StatusBadge';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreRuntimeFeatureCatalog } from '@/lib/types/core';

const DEFAULT_FEATURE: CoreRuntimeFeatureCatalog = {
  featureCode: '',
  featureName: '',
  category: 'PROTOCOL',
  status: 'ACTIVE',
  requiresProbe: true,
  requiresTrustApproval: true,
  dispatchEligible: true,
};

export function RuntimeFeatureCatalogConsole() {
  const [items, setItems] = useState<CoreRuntimeFeatureCatalog[]>([]);
  const [status, setStatus] = useState('ACTIVE');
  const [query, setQuery] = useState('');
  const [draft, setDraft] = useState<CoreRuntimeFeatureCatalog>(DEFAULT_FEATURE);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await coreAdminApi.getRuntimeFeatures(status || undefined));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => { void refresh(); }, [refresh]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return items;
    return items.filter((item) => [item.featureCode, item.featureName, item.category, item.status].some((value) => String(value ?? '').toLowerCase().includes(needle)));
  }, [items, query]);

  async function saveFeature() {
    const featureCode = draft.featureCode.trim().toUpperCase();
    if (!featureCode) {
      setMessage('featureCode is required.');
      return;
    }
    const saved = await coreAdminApi.upsertRuntimeFeature(featureCode, { ...draft, featureCode, featureName: draft.featureName || featureCode });
    setMessage(`Runtime feature ${saved.featureCode} saved.`);
    setDraft(DEFAULT_FEATURE);
    await refresh();
  }

  return (
    <div className="space-y-6">
      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-blue-600">P2-I · Runtime Feature Catalog + Trust</div>
            <h1 className="mt-2 text-2xl font-black text-slate-950">Runtime Feature Catalog</h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Runtime features are protocol/tool facts reported by Agent runtime. Heartbeat creates observations only; routing should later use TRUSTED feature records.
            </p>
            <div className="mt-3"><HelpText term="runtimeEligibility" label="Runtime feature trust 說明" /></div>
          </div>
          <Button tone="secondary" onClick={() => void refresh()} disabled={loading}>{loading ? 'Refreshing...' : 'Refresh'}</Button>
        </div>
      </div>

      {message ? <div className="rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm font-semibold text-blue-800">{message}</div> : null}

      <div className="grid gap-5 xl:grid-cols-[420px_1fr]">
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-black text-slate-900">Create / Edit Runtime Feature</h2>
          <p className="mt-1 text-sm text-slate-500">Use catalog records instead of free-form requiredRuntimeFeatures CSV.</p>
          <div className="mt-4 space-y-3">
            <Field label="Feature Code"><input value={draft.featureCode} onChange={(event) => setDraft((prev) => ({ ...prev, featureCode: event.target.value.toUpperCase() }))} className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm" placeholder="TASK_RESULT" /></Field>
            <Field label="Feature Name"><input value={draft.featureName ?? ''} onChange={(event) => setDraft((prev) => ({ ...prev, featureName: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm" placeholder="Task Result" /></Field>
            <Field label="Category"><input value={draft.category ?? ''} onChange={(event) => setDraft((prev) => ({ ...prev, category: event.target.value.toUpperCase() }))} className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm" placeholder="PROTOCOL" /></Field>
            <Field label="Status"><select value={draft.status ?? 'ACTIVE'} onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"><option>ACTIVE</option><option>DRAFT</option><option>DISABLED</option><option>RETIRED</option><option>NEEDS_REVIEW</option></select></Field>
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-700"><input type="checkbox" checked={draft.requiresProbe !== false} onChange={(event) => setDraft((prev) => ({ ...prev, requiresProbe: event.target.checked }))} /> Requires probe</label>
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-700"><input type="checkbox" checked={draft.requiresTrustApproval !== false} onChange={(event) => setDraft((prev) => ({ ...prev, requiresTrustApproval: event.target.checked }))} /> Requires trust approval</label>
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-700"><input type="checkbox" checked={draft.dispatchEligible !== false} onChange={(event) => setDraft((prev) => ({ ...prev, dispatchEligible: event.target.checked }))} /> Dispatch eligible</label>
            <div className="flex gap-2"><Button tone="primary" onClick={() => void saveFeature()}>Save Runtime Feature</Button><Button tone="ghost" onClick={() => setDraft(DEFAULT_FEATURE)}>Reset</Button></div>
          </div>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-base font-black text-slate-900">Catalog</h2>
              <p className="mt-1 text-sm text-slate-500">Only ACTIVE catalog entries should be selectable for trust workflows.</p>
            </div>
            <div className="flex gap-2"><input value={query} onChange={(event) => setQuery(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2 text-sm" placeholder="Search feature" /><select value={status} onChange={(event) => setStatus(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2 text-sm"><option value="ACTIVE">ACTIVE</option><option value="NEEDS_REVIEW">NEEDS_REVIEW</option><option value="DRAFT">DRAFT</option><option value="DISABLED">DISABLED</option><option value="RETIRED">RETIRED</option><option value="">ALL</option></select></div>
          </div>
          <DataTableShell>
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Feature</th><th className="px-4 py-3">Category</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Trust</th><th className="px-4 py-3">Dispatch</th></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.length ? filtered.map((item) => <tr key={item.featureCode} className="hover:bg-slate-50"><td className="px-4 py-3"><div className="font-black text-slate-900">{item.featureCode}</div><div className="text-xs text-slate-500">{item.featureName ?? '-'}</div></td><td className="px-4 py-3 text-slate-600">{item.category ?? '-'}</td><td className="px-4 py-3"><StatusBadge status={item.status ?? 'ACTIVE'} /></td><td className="px-4 py-3 text-xs text-slate-500">Probe {item.requiresProbe === false ? 'optional' : 'required'} · Trust {item.requiresTrustApproval === false ? 'optional' : 'required'}</td><td className="px-4 py-3"><StatusBadge status={item.dispatchEligible === false ? 'NOT_DISPATCH_ELIGIBLE' : 'DISPATCH_ELIGIBLE'} /></td></tr>) : <TableEmptyRow colSpan={5} title="No runtime features" description="Create catalog entries or backfill from required runtime features." />}
              </tbody>
            </table>
          </DataTableShell>
        </section>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block text-sm font-semibold text-slate-700"><span className="mb-1 block text-xs font-black uppercase tracking-wide text-slate-400">{label}</span>{children}</label>;
}
