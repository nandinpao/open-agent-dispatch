'use client';

import Link from 'next/link';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { humanizeA2ACode } from '@/lib/a2a/contracts';
import {
  getCapabilityDelegationDetail,
  searchCapabilityDelegations,
  type CapabilityDelegationDetail,
  type CapabilityDelegationListItem,
} from '@/lib/api/domains/a2aOperationsApi';
import { A2AStatusBadge } from './A2AStatusBadge';
import { CapabilityDelegationDetailView } from './CapabilityDelegationDetailView';

const STATUSES = ['', 'RECEIVED', 'WAITING_APPROVAL', 'NO_CANDIDATE', 'ROUTING_UNAVAILABLE', 'HOW_UNAVAILABLE', 'AUTHORIZED', 'CHILD_CREATED', 'DISPATCH_QUEUED', 'RESULT_SUCCEEDED', 'RESULT_FAILED', 'FAILED'];
const PROVIDER_TYPES = ['', 'MANAGED_AGENT', 'MCP_TOOL', 'REMOTE_A2A_AGENT', 'INTERNAL_SERVICE'];

export function CapabilityDelegationWorkspace({ initialDelegationId, initialParentTaskId }: { initialDelegationId?: string; initialParentTaskId?: string }) {
  const { status: authStatus, activeTenantId } = useAuth();
  const tenantReady = authStatus === 'AUTHENTICATED' && activeTenantId.trim().length > 0;
  const [items, setItems] = useState<CapabilityDelegationListItem[]>([]);
  const [selectedId, setSelectedId] = useState(initialDelegationId ?? '');
  const [detail, setDetail] = useState<CapabilityDelegationDetail | null>(null);
  const [status, setStatus] = useState('');
  const [providerType, setProviderType] = useState('');
  const [parentTaskId, setParentTaskId] = useState(initialParentTaskId ?? '');
  const [capabilityCode, setCapabilityCode] = useState('');
  const [query, setQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [offset, setOffset] = useState(0);
  const [total, setTotal] = useState(0);
  const [sortDirection, setSortDirection] = useState<'ASC' | 'DESC'>('DESC');
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sequence = useRef(0);
  const limit = 50;

  const loadList = useCallback(async () => {
    if (!tenantReady) { setItems([]); setTotal(0); setLoading(false); return; }
    setLoading(true); setError(null);
    try {
      const page = await searchCapabilityDelegations({ status: status || undefined, parentTaskId: parentTaskId.trim() || undefined, capabilityCode: capabilityCode.trim() || undefined, providerType: providerType || undefined, q: appliedQuery || undefined, offset, limit, sortDirection });
      setItems(page.items); setTotal(page.total);
      setSelectedId((current) => {
        if (initialDelegationId) return initialDelegationId;
        if (current && page.items.some((item) => item.delegationId === current)) return current;
        return page.items[0]?.delegationId ?? '';
      });
    } catch (cause) {
      setItems([]); setTotal(0); setError(cause instanceof Error ? cause.message : 'Unable to load capability delegations.');
    } finally { setLoading(false); }
  }, [appliedQuery, capabilityCode, initialDelegationId, offset, parentTaskId, providerType, sortDirection, status, tenantReady]);

  const loadDetail = useCallback(async () => {
    const current = ++sequence.current;
    if (!tenantReady || !selectedId) { setDetail(null); setDetailLoading(false); return; }
    setDetailLoading(true); setError(null);
    try {
      const next = await getCapabilityDelegationDetail(selectedId);
      if (current === sequence.current) setDetail(next);
    } catch (cause) {
      if (current === sequence.current) { setDetail(null); setError(cause instanceof Error ? cause.message : 'Unable to load capability delegation evidence.'); }
    } finally { if (current === sequence.current) setDetailLoading(false); }
  }, [selectedId, tenantReady]);

  useEffect(() => { void loadList(); }, [loadList]);
  useEffect(() => { void loadDetail(); }, [loadDetail]);
  useEffect(() => { sequence.current += 1; setItems([]); setDetail(null); setOffset(0); setSelectedId(initialDelegationId ?? ''); setParentTaskId(initialParentTaskId ?? ''); }, [activeTenantId, initialDelegationId, initialParentTaskId]);

  if (authStatus === 'CHECKING') return <div className="rounded-2xl border bg-white p-8 text-sm font-semibold text-slate-600">Loading the active administration workspace…</div>;
  if (!tenantReady) return <div role="status" className="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-amber-950"><h2 className="font-black">Select a company workspace</h2><p className="mt-2 text-sm">Capability delegations are scoped to the active authenticated Tenant.</p></div>;

  const rangeStart = total === 0 ? 0 : offset + 1;
  const rangeEnd = Math.min(offset + limit, total);
  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-4 text-sm text-indigo-950">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div><p className="font-black">Capability-first operations</p><p className="mt-1 text-xs leading-5">This workspace shows provider-neutral delegation evidence. A delegation is not complete merely because a Child Task was queued; inspect provider result and Parent notification evidence. Legacy directional A2A remains archive-only.</p></div>
          <Link href="/a2a-governance" className="rounded-lg border border-indigo-300 bg-white px-3 py-2 text-xs font-black text-indigo-800">Open Legacy A2A Archive</Link>
        </div>
      </section>

      <div className="grid gap-5 xl:grid-cols-[410px_minmax(0,1fr)]">
        <aside className="self-start rounded-2xl border border-slate-200 bg-white p-4 shadow-sm xl:sticky xl:top-20">
          <div className="flex items-center justify-between gap-3"><div><p className="text-xs font-black uppercase tracking-[.18em] text-slate-500">Delegation queue</p><h2 className="mt-1 text-lg font-black">Capability delegations</h2></div><button type="button" onClick={() => void loadList()} className="rounded-lg border px-3 py-2 text-xs font-black">Refresh</button></div>
          <form className="mt-4 space-y-3" onSubmit={(event) => { event.preventDefault(); setOffset(0); setAppliedQuery(query.trim()); }}>
            <label className="block text-xs font-black uppercase text-slate-500">Search<input value={query} onChange={(event) => setQuery(event.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2 text-sm normal-case" placeholder="Capability, task, provider, delegation" /></label>
            <label className="block text-xs font-black uppercase text-slate-500">Operational status<select value={status} onChange={(event) => { setStatus(event.target.value); setOffset(0); }} className="mt-1 w-full rounded-xl border px-3 py-2 text-sm normal-case">{STATUSES.map((value) => <option key={value || 'ALL'} value={value}>{value ? humanizeA2ACode(value) : 'All statuses'}</option>)}</select></label>
            <label className="block text-xs font-black uppercase text-slate-500">Parent Task ID<input value={parentTaskId} onChange={(event) => { setParentTaskId(event.target.value); setOffset(0); }} className="mt-1 w-full rounded-xl border px-3 py-2 text-sm font-mono normal-case" placeholder="Exact Parent Task ID from lab:a2a" /></label>
            <label className="block text-xs font-black uppercase text-slate-500">Capability code<input value={capabilityCode} onChange={(event) => { setCapabilityCode(event.target.value); setOffset(0); }} className="mt-1 w-full rounded-xl border px-3 py-2 text-sm normal-case" placeholder="Optional exact capability" /></label>
            <label className="block text-xs font-black uppercase text-slate-500">Provider type<select value={providerType} onChange={(event) => { setProviderType(event.target.value); setOffset(0); }} className="mt-1 w-full rounded-xl border px-3 py-2 text-sm normal-case">{PROVIDER_TYPES.map((value) => <option key={value || 'ALL'} value={value}>{value ? humanizeA2ACode(value) : 'All provider types'}</option>)}</select></label>
            <div className="flex flex-wrap gap-2"><button type="submit" className="rounded-xl bg-indigo-700 px-4 py-2 text-sm font-black text-white">Search</button><button type="button" onClick={() => { setQuery(''); setAppliedQuery(''); setStatus(''); setParentTaskId(''); setCapabilityCode(''); setProviderType(''); setOffset(0); }} className="rounded-xl border px-4 py-2 text-sm font-black">Reset</button><button type="button" onClick={() => setSortDirection((value) => value === 'DESC' ? 'ASC' : 'DESC')} className="rounded-xl border px-3 py-2 text-xs font-black">{sortDirection === 'DESC' ? 'Newest' : 'Oldest'}</button></div>
          </form>
          <div className="mt-4 border-t pt-3 text-xs text-slate-500">Showing {rangeStart}–{rangeEnd} of {total}</div>
          <div className="mt-3 max-h-[54vh] space-y-2 overflow-y-auto pr-1">
            {loading ? <p className="p-3 text-sm text-slate-500">Loading delegations…</p> : null}
            {!loading && items.length === 0 ? <p className="p-3 text-sm text-slate-500">No capability delegations match this view.</p> : null}
            {items.map((item) => (
              <button key={item.delegationId} type="button" onClick={() => setSelectedId(item.delegationId)} className={`w-full rounded-xl border p-3 text-left ${selectedId === item.delegationId ? 'border-indigo-400 bg-indigo-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                <div className="flex items-start justify-between gap-2"><span className="font-black text-slate-900">{humanizeA2ACode(item.capabilityCode)}</span><A2AStatusBadge value={item.status} /></div>
                <p className="mt-1 text-xs font-bold text-slate-600">{humanizeA2ACode(item.operation)}{item.selectedProviderName ? ` · ${item.selectedProviderName}` : ''}</p>
                <p className="mt-2 truncate font-mono text-[11px] text-slate-400">{item.delegationId}</p>
              </button>
            ))}
          </div>
          <div className="mt-3 flex justify-between gap-2"><button type="button" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - limit))} className="rounded-lg border px-3 py-2 text-xs font-black disabled:opacity-40">Previous</button><button type="button" disabled={offset + limit >= total} onClick={() => setOffset(offset + limit)} className="rounded-lg border px-3 py-2 text-xs font-black disabled:opacity-40">Next</button></div>
        </aside>

        <section className="min-w-0">
          {error ? <div role="alert" className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-bold text-red-900">{error}</div> : null}
          {detailLoading ? <div className="rounded-2xl border bg-white p-8 text-sm text-slate-500">Loading authority evidence…</div> : null}
          {!detailLoading && detail ? <CapabilityDelegationDetailView detail={detail} /> : null}
          {!detailLoading && !detail && !error ? <div className="rounded-2xl border bg-white p-8 text-sm text-slate-500">Select a capability delegation to inspect the authority decision chain.</div> : null}
        </section>
      </div>
    </div>
  );
}
