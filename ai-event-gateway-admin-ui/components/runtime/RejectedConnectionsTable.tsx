'use client';

import { useEffect, useMemo, useState } from 'react';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { JsonViewer } from '@/components/common/JsonViewer';
import { ListFilterBar, type SelectFilterConfig } from '@/components/common/ListFilterBar';
import { LoadingBox } from '@/components/common/LoadingBox';
import { PaginationControls } from '@/components/common/PaginationControls';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { rejectedConnectionSemantics, rejectedConnectionsEmptyDescription, rejectedConnectionsEmptyTitle } from '@/lib/runtime/rejectedConnectionSemantics';
import { useRejectedConnections } from '@/hooks/useRejectedConnections';
import type { NettyRejectedConnection } from '@/lib/types/nettyRuntime';
import { formatDateTime } from '@/lib/utils/format';
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from '@/lib/utils/list';

const allValue = 'ALL';

function rejectedConnectionId(connection: NettyRejectedConnection, index: number): string {
  return connection.id ?? connection.eventId ?? `${connection.claimedAgentId ?? connection.agentId ?? 'unknown'}-${connection.occurredAt ?? connection.lastSeenAt ?? index}`;
}

function matchesConnection(connection: NettyRejectedConnection, query: string, reason: string, state: string): boolean {
  if (reason !== allValue && connection.reason !== reason) return false;
  if (state !== allValue && connection.authorizationState !== state) return false;

  return recordIncludesQuery([
    connection.id,
    connection.eventId,
    connection.claimedAgentId,
    connection.agentId,
    connection.gatewayNodeId,
    connection.connectionId,
    connection.sessionId,
    connection.remoteAddress,
    connection.reason,
    connection.authorizationState
  ], query);
}

export function RejectedConnectionsTable() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useRejectedConnections();
  const [search, setSearch] = useState('');
  const [reasonFilter, setReasonFilter] = useState(allValue);
  const [stateFilter, setStateFilter] = useState(allValue);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const connections = useMemo(() => data ?? [], [data]);
  const filtered = useMemo(
    () => connections.filter((connection) => matchesConnection(connection, search, reasonFilter, stateFilter)),
    [connections, reasonFilter, search, stateFilter]
  );
  const pagination = useMemo(() => paginateItems(filtered, { page, pageSize }), [filtered, page, pageSize]);

  useEffect(() => {
    setPage(1);
  }, [pageSize, reasonFilter, search, stateFilter]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const reasons = uniqueSortedValues(connections.map((connection) => connection.reason));
    const states = uniqueSortedValues(connections.map((connection) => connection.authorizationState));
    return [
      {
        id: 'reason',
        label: 'Reason',
        value: reasonFilter,
        onChange: setReasonFilter,
        options: [{ value: allValue, label: 'All Reasons' }, ...reasons.map((reason) => ({ value: reason, label: reason }))]
      },
      {
        id: 'state',
        label: 'Authorization',
        value: stateFilter,
        onChange: setStateFilter,
        options: [{ value: allValue, label: 'All States' }, ...states.map((state) => ({ value: state, label: state }))]
      }
    ];
  }, [connections, reasonFilter, stateFilter]);

  function clearFilters() {
    setSearch('');
    setReasonFilter(allValue);
    setStateFilter(allValue);
  }

  if (loading) return <LoadingBox label="讀取 Netty rejected connections..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data || data.length === 0) return <EmptyState title={rejectedConnectionsEmptyTitle()} description={rejectedConnectionsEmptyDescription()} />;

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>

      <div className="rounded-2xl border border-amber-100 bg-amber-50 p-4 text-sm text-amber-800">
        <div className="font-bold">{rejectedConnectionSemantics().title}</div>
        <p className="mt-1">{rejectedConnectionSemantics().description}</p>
        <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
          <div>
            <div className="font-semibold">會出現在這裡</div>
            <ul className="mt-1 list-disc space-y-1 pl-5 text-xs">
              {rejectedConnectionSemantics().examples.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
          <div>
            <div className="font-semibold">不會出現在這裡</div>
            <ul className="mt-1 list-disc space-y-1 pl-5 text-xs">
              {rejectedConnectionSemantics().nonExamples.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
        </div>
      </div>

      <ListFilterBar
        search={search}
        searchPlaceholder="搜尋 claimed Agent、remote IP、gateway、reason、session..."
        onSearchChange={setSearch}
        filters={filters}
        onClear={clearFilters}
      />

      {filtered.length === 0 ? (
        <EmptyState title="沒有符合條件的 rejected connection" description="請調整關鍵字、拒絕原因或授權狀態篩選條件。" />
      ) : (
        <>
          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="whitespace-nowrap px-4 py-3">Connection</th>
                    <th className="whitespace-nowrap px-4 py-3">Claimed Agent</th>
                    <th className="whitespace-nowrap px-4 py-3">Authorization</th>
                    <th className="whitespace-nowrap px-4 py-3">Reason</th>
                    <th className="whitespace-nowrap px-4 py-3">Gateway</th>
                    <th className="whitespace-nowrap px-4 py-3">Remote</th>
                    <th className="whitespace-nowrap px-4 py-3">First Seen</th>
                    <th className="whitespace-nowrap px-4 py-3">Last Seen</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {pagination.items.map((connection, index) => {
                    const id = rejectedConnectionId(connection, index);
                    const expanded = expandedId === id;
                    return (
                      <tr key={id} className="align-top hover:bg-slate-50">
                        <td className="px-4 py-3 font-semibold text-slate-900">
                          <button type="button" className="text-left text-blue-700 hover:underline" onClick={() => setExpandedId(expanded ? null : id)}>
                            {connection.connectionId ?? connection.sessionId ?? id}
                          </button>
                          {expanded ? <div className="mt-3 w-[36rem] max-w-[80vw]"><JsonViewer value={connection.payload ?? {}} /></div> : null}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{connection.claimedAgentId ?? connection.agentId ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3"><StatusBadge status={connection.authorizationState ?? 'DENIED'} /></td>
                        <td className="max-w-md px-4 py-3 text-slate-600">{connection.reason ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{connection.gatewayNodeId ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{connection.remoteAddress ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(connection.firstSeenAt ?? connection.occurredAt)}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(connection.lastSeenAt ?? connection.occurredAt)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <PaginationControls
            page={pagination.page}
            pageSize={pagination.pageSize}
            totalItems={pagination.totalItems}
            totalPages={pagination.totalPages}
            startItem={pagination.startItem}
            endItem={pagination.endItem}
            onPageChange={setPage}
            onPageSizeChange={setPageSize}
          />
        </>
      )}
    </div>
  );
}
