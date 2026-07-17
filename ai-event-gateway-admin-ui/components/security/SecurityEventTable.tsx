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
import { useSecurityEvents } from '@/hooks/useSecurityEvents';
import type { AgentSecurityEvent } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';
import { isDuplicateRuntimeSecurityEvent } from '@/lib/agents/duplicateRuntimeSecurityEvents';
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from '@/lib/utils/list';

const allValue = 'ALL';

function matchesEvent(event: AgentSecurityEvent, query: string, severity: string, eventType: string): boolean {
  if (severity !== allValue && event.severity !== severity) return false;
  if (eventType !== allValue && event.eventType !== eventType) return false;

  return recordIncludesQuery([
    event.eventId,
    event.agentId,
    event.claimedAgentId,
    event.eventType,
    event.severity,
    event.reason,
    event.remoteAddress,
    event.gatewayNodeId
  ], query);
}

export function SecurityEventTable() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useSecurityEvents();
  const [search, setSearch] = useState('');
  const [severityFilter, setSeverityFilter] = useState(allValue);
  const [eventTypeFilter, setEventTypeFilter] = useState(allValue);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const events = useMemo(() => data ?? [], [data]);
  const filtered = useMemo(
    () => events.filter((event) => matchesEvent(event, search, severityFilter, eventTypeFilter)),
    [eventTypeFilter, events, search, severityFilter]
  );
  const pagination = useMemo(() => paginateItems(filtered, { page, pageSize }), [filtered, page, pageSize]);

  useEffect(() => {
    setPage(1);
  }, [eventTypeFilter, pageSize, search, severityFilter]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const severities = uniqueSortedValues(events.map((event) => event.severity));
    const eventTypes = uniqueSortedValues(events.map((event) => event.eventType));
    return [
      {
        id: 'severity',
        label: 'Severity',
        value: severityFilter,
        onChange: setSeverityFilter,
        options: [{ value: allValue, label: 'All Severities' }, ...severities.map((severity) => ({ value: severity, label: severity }))]
      },
      {
        id: 'eventType',
        label: 'Event Type',
        value: eventTypeFilter,
        onChange: setEventTypeFilter,
        options: [{ value: allValue, label: 'All Event Types' }, ...eventTypes.map((eventType) => ({ value: eventType, label: eventType }))]
      }
    ];
  }, [eventTypeFilter, events, severityFilter]);

  function clearFilters() {
    setSearch('');
    setSeverityFilter(allValue);
    setEventTypeFilter(allValue);
  }

  if (loading) return <LoadingBox label="讀取 Core security events..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data || data.length === 0) return <EmptyState title="目前沒有 Security Event" description="Core 會記錄 Agent 授權失敗、憑證撤銷後重連、未知 Agent 嘗試連線等安全事件。" />;

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>

      <div className="rounded-2xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-800">
        此頁資料來源為 Core。它是安全稽核與治理資料，不應由 Netty runtime 取代。
      </div>

      <ListFilterBar
        search={search}
        searchPlaceholder="搜尋 event、Agent、reason、remote IP、gateway node..."
        onSearchChange={setSearch}
        filters={filters}
        onClear={clearFilters}
      />

      {filtered.length === 0 ? (
        <EmptyState title="沒有符合條件的安全事件" description="請調整關鍵字、severity 或 event type 篩選條件。" />
      ) : (
        <>
          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="whitespace-nowrap px-4 py-3">Event</th>
                    <th className="whitespace-nowrap px-4 py-3">Type</th>
                    <th className="whitespace-nowrap px-4 py-3">Severity</th>
                    <th className="whitespace-nowrap px-4 py-3">Agent</th>
                    <th className="whitespace-nowrap px-4 py-3">Remote</th>
                    <th className="whitespace-nowrap px-4 py-3">Gateway</th>
                    <th className="whitespace-nowrap px-4 py-3">Reason</th>
                    <th className="whitespace-nowrap px-4 py-3">Occurred</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {pagination.items.map((event) => {
                    const expanded = expandedId === event.eventId;
                    return (
                      <tr key={event.eventId} className={`align-top hover:bg-slate-50 ${isDuplicateRuntimeSecurityEvent(event) ? 'bg-rose-50/60' : ''}`}>
                        <td className="px-4 py-3 font-semibold text-slate-900">
                          <button
                            type="button"
                            className="text-left text-blue-700 hover:underline"
                            onClick={() => setExpandedId(expanded ? null : event.eventId)}
                          >
                            {event.eventId}
                          </button>
                          {expanded ? <div className="mt-3 w-[36rem] max-w-[80vw]"><JsonViewer value={event.payload ?? {}} /></div> : null}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{event.eventType}</td>
                        <td className="whitespace-nowrap px-4 py-3"><StatusBadge status={event.severity ?? 'INFO'} /></td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{event.agentId ?? event.claimedAgentId ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{event.remoteAddress ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{event.gatewayNodeId ?? '-'}</td>
                        <td className="max-w-md px-4 py-3 text-slate-600">{event.reason ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(event.occurredAt)}</td>
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
