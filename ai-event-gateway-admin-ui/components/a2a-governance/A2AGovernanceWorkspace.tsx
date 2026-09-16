'use client';

import { useEffect, useMemo, useState } from 'react';
import { listLegacyA2APolicies } from '@/lib/api/domains/legacyA2AArchiveApi';
import type { A2APolicyView } from '@/lib/a2a/contracts';

export function A2AGovernanceWorkspace() {
  const [items, setItems] = useState<A2APolicyView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  useEffect(() => {
    let active = true;
    void listLegacyA2APolicies().then((value) => { if (active) setItems(value); })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : 'Unable to load legacy A2A policies.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((item) => [item.policyCode, item.policyName, item.sourceDomainId, item.targetDomainId, item.targetAgentPoolId]
      .some((value) => String(value ?? '').toLowerCase().includes(q)));
  }, [items, query]);

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
        <p className="text-xs font-black uppercase tracking-[.18em] text-amber-800">Retired directional model</p>
        <h2 className="mt-2 text-lg font-black text-slate-950">Directional A2A routing is retired</h2>
        <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">Existing Source Domain → Target Domain and Policy → Agent Pool records are retained only as historical governance evidence. This workspace cannot create or edit those relationships. Current cross-Agent execution is capability-first: an assigned Agent requests a required Capability and Core governs provider discovery, authorization, selection, execution binding, and the authoritative child Task.</p>
      </section>
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div><p className="text-xs font-black uppercase tracking-[.18em] text-slate-500">Historical evidence</p><h3 className="mt-1 text-base font-black text-slate-950">Legacy directional policies</h3></div>
          <label className="text-xs font-black text-slate-600">Search archive<input value={query} onChange={(event) => setQuery(event.target.value)} className="ml-2 rounded-lg border border-slate-300 px-3 py-2 font-normal" placeholder="Policy or legacy topology" /></label>
        </div>
        {loading ? <p className="mt-4 text-sm text-slate-500">Loading historical policies…</p> : null}
        {error ? <p className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p> : null}
        {!loading && !error ? (
          <div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-sm"><thead className="border-b text-xs uppercase text-slate-500"><tr><th className="px-3 py-2">Policy</th><th className="px-3 py-2">Legacy source</th><th className="px-3 py-2">Legacy target</th><th className="px-3 py-2">Legacy pool</th><th className="px-3 py-2">Status</th></tr></thead><tbody>{filtered.map((item) => <tr key={item.policyId} className="border-b border-slate-100"><td className="px-3 py-3 font-semibold">{item.policyName || item.policyCode || item.policyId}</td><td className="px-3 py-3 text-slate-600">{item.sourceDomainId || '—'}</td><td className="px-3 py-3 text-slate-600">{item.targetDomainId || '—'}</td><td className="px-3 py-3 text-slate-600">{item.targetAgentPoolId || '—'}</td><td className="px-3 py-3"><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-black text-slate-600">RETIRED</span></td></tr>)}</tbody></table>{filtered.length === 0 ? <p className="p-4 text-sm text-slate-500">No historical policies match the current search.</p> : null}</div>
        ) : null}
      </section>
    </div>
  );
}
