'use client';

import Link from 'next/link';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { humanizeA2ACode } from '@/lib/a2a/contracts';
import {
  getA2AOperationsDetail,
  searchA2AOperations,
  type A2AOperationsDetail,
  type A2AOperationsListItem,
} from '@/lib/api/domains/a2aOperationsApi';
import { A2AOperationsDetailView } from './A2AOperationsDetailView';
import { A2AStatusBadge } from './A2AStatusBadge';

const REQUEST_STATUSES = [
  '',
  'REQUESTED',
  'VALIDATING',
  'WAITING_APPROVAL',
  'APPROVED',
  'CHILD_TASK_CREATED',
  'DISPATCHING',
  'RUNNING',
  'WAIT_HUMAN',
  'COMPLETED',
  'FAILED',
  'REJECTED',
  'EXPIRED',
  'CANCEL_REQUESTED',
  'CANCELLED_CONFIRMED',
  'CANCELLED_UNCONFIRMED',
];

const OPERATIONAL_STAGES = [
  '',
  'REQUESTED',
  'VALIDATING',
  'WAITING_APPROVAL',
  'APPROVED',
  'CHILD_TASK_CREATING',
  'CHILD_TASK_CREATED',
  'DISPATCH_REQUESTED',
  'DISPATCHING',
  'BLOCKED',
  'RUNNING',
  'WAITING_RESULT',
  'CANCEL_REQUESTED',
  'WAIT_HUMAN',
  'TERMINAL',
];

export function A2AOperationsWorkspace({ initialRequestId }: { initialRequestId?: string }) {
  const { status: authStatus, activeTenantId: selectedTenantId } = useAuth();
  const tenantReady = authStatus === 'AUTHENTICATED' && selectedTenantId.trim().length > 0;
  const [items, setItems] = useState<A2AOperationsListItem[]>([]);
  const [selectedId, setSelectedId] = useState(initialRequestId ?? '');
  const [detail, setDetail] = useState<A2AOperationsDetail | null>(null);
  const [requestStatus, setRequestStatus] = useState('');
  const [stage, setStage] = useState('');
  const [blockerOnly, setBlockerOnly] = useState(false);
  const [query, setQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [offset, setOffset] = useState(0);
  const [total, setTotal] = useState(0);
  const [sortDirection, setSortDirection] = useState<'ASC' | 'DESC'>('DESC');
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [domainNames, setDomainNames] = useState<Record<string, string>>({});
  const detailSequence = useRef(0);
  const limit = 50;

  const loadList = useCallback(async () => {
    if (!tenantReady) {
      setLoading(false);
      setItems([]);
      setTotal(0);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const page = await searchA2AOperations({
        requestStatus: requestStatus || undefined,
        operationalStage: stage || undefined,
        q: appliedQuery || undefined,
        blockerOnly,
        offset,
        limit,
        sortBy: 'updatedAt',
        sortDirection,
      });
      setItems(page.items);
      setTotal(page.total);
      setSelectedId((current) => {
        if (initialRequestId) return initialRequestId;
        if (current && page.items.some((item) => item.requestId === current)) return current;
        return page.items[0]?.requestId ?? '';
      });
    } catch (cause) {
      setItems([]);
      setTotal(0);
      setError(cause instanceof Error ? cause.message : 'Unable to load delegations.');
    } finally {
      setLoading(false);
    }
  }, [
    appliedQuery,
    blockerOnly,
    initialRequestId,
    offset,
    requestStatus,
    sortDirection,
    stage,
    tenantReady,
  ]);

  const loadDetail = useCallback(async () => {
    const sequence = ++detailSequence.current;
    if (!tenantReady || !selectedId) {
      setDetail(null);
      setDetailLoading(false);
      return;
    }

    setDetailLoading(true);
    setError(null);
    try {
      const next = await getA2AOperationsDetail(selectedId);
      if (sequence === detailSequence.current) setDetail(next);
    } catch (cause) {
      if (sequence === detailSequence.current) {
        setDetail(null);
        setError(cause instanceof Error ? cause.message : 'Unable to load delegation details.');
      }
    } finally {
      if (sequence === detailSequence.current) setDetailLoading(false);
    }
  }, [selectedId, tenantReady]);

  useEffect(() => {
    let active = true;
    if (!tenantReady) { setDomainNames({}); return () => { active = false; }; }
    void coreAdminApi.getSourceSystems(selectedTenantId)
      .then((rows) => {
        if (!active) return;
        setDomainNames(Object.fromEntries(rows.map((row) => [row.sourceSystemId, row.displayName || row.sourceSystemId])));
      })
      .catch(() => { if (active) setDomainNames({}); });
    return () => { active = false; };
  }, [selectedTenantId, tenantReady]);

  useEffect(() => {
    detailSequence.current += 1;
    setItems([]);
    setDetail(null);
    setTotal(0);
    setOffset(0);
    setError(null);
    setSelectedId(initialRequestId ?? '');
  }, [initialRequestId, selectedTenantId]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  async function refreshAll() {
    await loadList();
    await loadDetail();
  }

  function applySearch() {
    setOffset(0);
    setAppliedQuery(query.trim());
  }

  function saveView() {
    localStorage.setItem('a2a-operations:last-view', JSON.stringify({
      requestStatus,
      stage,
      blockerOnly,
      query,
      sortDirection,
    }));
  }

  function restoreView() {
    try {
      const value = JSON.parse(localStorage.getItem('a2a-operations:last-view') ?? '{}') as Record<string, unknown>;
      const restoredQuery = typeof value.query === 'string' ? value.query : '';
      setRequestStatus(typeof value.requestStatus === 'string' ? value.requestStatus : '');
      setStage(typeof value.stage === 'string' ? value.stage : '');
      setBlockerOnly(Boolean(value.blockerOnly));
      setQuery(restoredQuery);
      setAppliedQuery(restoredQuery.trim());
      setSortDirection(value.sortDirection === 'ASC' ? 'ASC' : 'DESC');
      setOffset(0);
    } catch {
      // Ignore invalid local display preferences. No authority data is stored here.
    }
  }

  if (authStatus === 'CHECKING') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-8 text-sm font-semibold text-slate-600">
        Loading the active administration workspace…
      </div>
    );
  }

  if (!tenantReady) {
    return (
      <div role="status" className="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-amber-950">
        <h2 className="font-black">Select a company workspace</h2>
        <p className="mt-2 text-sm leading-6">
          Delegations are scoped to the currently selected company workspace. Select a workspace before loading delegated work, evidence, or governed actions.
        </p>
      </div>
    );
  }

  const rangeStart = total === 0 ? 0 : offset + 1;
  const rangeEnd = Math.min(offset + limit, total);

  return (
    <div className="grid gap-5 xl:grid-cols-[400px_minmax(0,1fr)]">
      <aside className="self-start rounded-2xl border border-slate-200 bg-white p-4 shadow-sm xl:sticky xl:top-20">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs font-black uppercase tracking-[.18em] text-slate-500">Delegation queue</p>
            <h2 className="mt-1 text-lg font-black text-slate-900">Delegated work</h2>
            <p className="mt-1 text-xs text-slate-500">Governed delegated work that may require operational attention.</p>
          </div>
          <button type="button" onClick={() => void loadList()} className="rounded-lg border px-3 py-2 text-xs font-black">
            Refresh
          </button>
        </div>

        <form className="mt-4 space-y-3" onSubmit={(event) => { event.preventDefault(); applySearch(); }}>
          <label className="block text-xs font-black uppercase text-slate-500">
            Search
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              className="mt-1 w-full rounded-xl border px-3 py-2 text-sm"
              placeholder="Delegation, task, domain, or blocker"
            />
          </label>

          <label className="block text-xs font-black uppercase text-slate-500">
            Operational stage
            <select
              value={stage}
              onChange={(event) => { setStage(event.target.value); setOffset(0); }}
              className="mt-1 w-full rounded-xl border px-2 py-2 text-sm"
            >
              {OPERATIONAL_STAGES.map((value) => (
                <option key={value || 'ALL'} value={value}>{value ? humanizeA2ACode(value) : 'All operational stages'}</option>
              ))}
            </select>
            <span className="mt-1 block normal-case font-normal text-slate-400">Primary execution progress for legacy records.</span>
          </label>

          <details className="rounded-xl border border-slate-200 bg-slate-50 p-3">
            <summary className="cursor-pointer text-xs font-black uppercase text-slate-600">Advanced governance filter</summary>
            <label className="mt-3 block text-xs font-black uppercase text-slate-500">
              Governance request state
              <select
                value={requestStatus}
                onChange={(event) => { setRequestStatus(event.target.value); setOffset(0); }}
                className="mt-1 w-full rounded-xl border px-2 py-2 text-sm"
              >
                {REQUEST_STATUSES.map((value) => (
                  <option key={value || 'ALL'} value={value}>{value ? humanizeA2ACode(value) : 'All governance states'}</option>
                ))}
              </select>
              <span className="mt-1 block normal-case font-normal text-slate-400">Legacy governance lifecycle; separate from execution progress.</span>
            </label>
          </details>

          <label className="flex items-center gap-2 text-sm font-bold">
            <input
              type="checkbox"
              checked={blockerOnly}
              onChange={(event) => { setBlockerOnly(event.target.checked); setOffset(0); }}
            />
            Active blockers only
          </label>

          <div className="flex flex-wrap gap-2">
            <button type="submit" className="rounded-xl bg-indigo-700 px-4 py-2 text-sm font-black text-white">Search</button>
            <button
              type="button"
              onClick={() => {
                setQuery('');
                setAppliedQuery('');
                setRequestStatus('');
                setStage('');
                setBlockerOnly(false);
                setOffset(0);
              }}
              className="rounded-xl border px-4 py-2 text-sm font-black"
            >
              Reset
            </button>
            <button type="button" onClick={saveView} className="rounded-xl border px-3 py-2 text-xs font-black">Save view</button>
            <button type="button" onClick={restoreView} className="rounded-xl border px-3 py-2 text-xs font-black">Restore</button>
            <button
              type="button"
              onClick={() => setSortDirection((value) => value === 'DESC' ? 'ASC' : 'DESC')}
              className="rounded-xl border px-3 py-2 text-xs font-black"
            >
              {sortDirection === 'DESC' ? 'Newest' : 'Oldest'}
            </button>
          </div>
        </form>

        <div className="mt-4 flex items-center justify-between text-xs font-bold text-slate-500">
          <span>{total} total · {rangeStart}-{rangeEnd}</span>
          <span>Page {Math.floor(offset / limit) + 1}</span>
        </div>

        <div className="mt-3 max-h-[60vh] space-y-2 overflow-y-auto pr-1">
          {loading ? <p className="rounded-xl bg-slate-50 p-4 text-sm">Loading…</p> : null}
          {!loading && items.length === 0 ? <p className="rounded-xl bg-slate-50 p-4 text-sm">No matching delegations.</p> : null}
          {items.map((item) => (
            <button
              key={item.requestId}
              type="button"
              onClick={() => setSelectedId(item.requestId)}
              className={`w-full rounded-xl border p-3 text-left ${selectedId === item.requestId ? 'border-indigo-400 bg-indigo-50' : 'border-slate-200 hover:bg-slate-50'}`}
            >
              <div className="flex items-start justify-between gap-2">
                <p className="min-w-0 truncate text-sm font-black">{humanizeA2ACode(item.requestedTaskType || 'A2A_REQUEST')}</p>
                <A2AStatusBadge value={item.requestStatus} />
              </div>
              <p className="mt-2 truncate text-xs font-semibold text-slate-700">{domainNames[item.sourceDomainId || ''] || 'Unknown source'} → {domainNames[item.targetDomainId || ''] || 'Unknown target'}</p>
              <p className="mt-1 truncate text-xs text-slate-500">{item.childTaskId ? 'Child task available' : 'Delegation in progress'}</p>
              <div className="mt-2 flex flex-wrap gap-1">
                <A2AStatusBadge value={item.operationalStage} />
                {item.blockerCode !== 'NONE' ? <A2AStatusBadge value={item.blockerCode} /> : null}
              </div>
            </button>
          ))}
        </div>

        <div className="mt-3 flex justify-between">
          <button
            type="button"
            disabled={offset === 0}
            onClick={() => setOffset(Math.max(0, offset - limit))}
            className="rounded-lg border px-3 py-2 text-xs font-black disabled:opacity-40"
          >
            Previous
          </button>
          <button
            type="button"
            disabled={offset + limit >= total}
            onClick={() => setOffset(offset + limit)}
            className="rounded-lg border px-3 py-2 text-xs font-black disabled:opacity-40"
          >
            Next
          </button>
        </div>
      </aside>

      <section className="min-w-0">
        {error ? <div className="mb-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-700">{error}</div> : null}
        {detailLoading ? <div className="rounded-2xl border bg-white p-8 text-sm">Loading canonical evidence…</div> : null}
        {!detailLoading && detail ? <A2AOperationsDetailView detail={detail} domainNames={domainNames} onRefresh={refreshAll} /> : null}
        {!detailLoading && !detail ? (
          <div className="rounded-2xl border border-dashed bg-white p-10 text-center">
            <h2 className="text-lg font-black">Select delegated work</h2>
            <p className="mt-2 text-sm">Start with current state and blockers; open technical evidence only when deeper investigation is needed.</p>
            <Link href="/tasks" className="mt-4 inline-flex rounded-xl border px-4 py-2 text-sm font-black">Open Tasks</Link>
          </div>
        ) : null}
      </section>
    </div>
  );
}
