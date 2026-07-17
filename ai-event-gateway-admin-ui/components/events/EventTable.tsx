'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { JsonViewer } from '@/components/common/JsonViewer';
import { ListFilterBar, type SelectFilterConfig } from '@/components/common/ListFilterBar';
import { LoadingBox } from '@/components/common/LoadingBox';
import { PaginationControls } from '@/components/common/PaginationControls';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { DataScopeNotice } from '@/components/common/DataScopeNotice';
import { useEvents } from '@/hooks/useEvents';
import type { GatewayEventRecord } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from '@/lib/utils/list';

const allValue = 'ALL';

function matchesEvent(event: GatewayEventRecord, query: string, status: string, source: string, eventType: string): boolean {
  if (status !== allValue && event.status !== status) return false;
  if (source !== allValue && event.sourceSystem !== source) return false;
  if (eventType !== allValue && event.eventType !== eventType) return false;

  return recordIncludesQuery([
    event.eventId,
    event.traceId,
    event.sourceSystem,
    event.eventType,
    event.status,
    event.message
  ], query);
}

export function EventTable() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useEvents();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState(allValue);
  const [sourceFilter, setSourceFilter] = useState(allValue);
  const [typeFilter, setTypeFilter] = useState(allValue);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const events = useMemo(() => data ?? [], [data]);

  const filteredEvents = useMemo(
    () => events.filter((event) => matchesEvent(event, search, statusFilter, sourceFilter, typeFilter)),
    [events, search, sourceFilter, statusFilter, typeFilter]
  );

  const pagination = useMemo(() => paginateItems(filteredEvents, { page, pageSize }), [filteredEvents, page, pageSize]);

  useEffect(() => {
    setPage(1);
  }, [search, statusFilter, sourceFilter, typeFilter, pageSize]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const statuses = uniqueSortedValues(events.map((event) => event.status));
    const sources = uniqueSortedValues(events.map((event) => event.sourceSystem));
    const eventTypes = uniqueSortedValues(events.map((event) => event.eventType));

    return [
      {
        id: 'status',
        label: 'Status',
        value: statusFilter,
        onChange: setStatusFilter,
        options: [{ value: allValue, label: 'All Statuses' }, ...statuses.map((status) => ({ value: status, label: status }))]
      },
      {
        id: 'source',
        label: 'Source',
        value: sourceFilter,
        onChange: setSourceFilter,
        options: [{ value: allValue, label: 'All Sources' }, ...sources.map((source) => ({ value: source, label: source }))]
      },
      {
        id: 'eventType',
        label: 'Event Type',
        value: typeFilter,
        onChange: setTypeFilter,
        options: [{ value: allValue, label: 'All Event Types' }, ...eventTypes.map((type) => ({ value: type, label: type }))]
      }
    ];
  }, [events, sourceFilter, statusFilter, typeFilter]);

  function clearFilters() {
    setSearch('');
    setStatusFilter(allValue);
    setSourceFilter(allValue);
    setTypeFilter(allValue);
  }

  if (loading) return <LoadingBox label="讀取 Gateway Events..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data || data.length === 0) return <EmptyState title="目前沒有事件資料" description="請確認 /api/admin/events 是否有回傳資料。" />;

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>

      <DataScopeNotice kind="events" scope="LOCAL" />

      <ListFilterBar
        search={search}
        searchPlaceholder="搜尋 Event ID、Trace ID、來源系統、事件類型、訊息..."
        onSearchChange={setSearch}
        filters={filters}
        onClear={clearFilters}
      />

      {filteredEvents.length === 0 ? (
        <EmptyState title="沒有符合條件的事件" description="請調整關鍵字、狀態、來源系統或事件類型篩選條件。" />
      ) : (
        <>
          <div className="space-y-3">
            {pagination.items.map((event) => (
              <div key={event.eventId} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <div className="text-sm font-bold text-slate-950">{event.eventType}</div>
                    <div className="mt-1 break-all text-xs text-slate-500">
                      {event.eventId} / Trace：
                      {event.traceId ? (
                        <Link href={`/traces/${encodeURIComponent(event.traceId)}`} className="font-semibold text-blue-600 hover:text-blue-700">{event.traceId}</Link>
                      ) : '-'}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusBadge status={event.status} />
                    <Link
                      href={`/events/${encodeURIComponent(event.eventId)}`}
                      className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                    >
                      Detail
                    </Link>
                  </div>
                </div>
                <div className="mt-4 grid grid-cols-1 gap-2 text-sm text-slate-600 md:grid-cols-4">
                  <div>Source：{event.sourceSystem}</div>
                  <div>Received：{formatDateTime(event.receivedAt)}</div>
                  <div>Routed：{event.routedAt ? formatDateTime(event.routedAt) : '-'}</div>
                  <div>Failed：{event.failedAt ? formatDateTime(event.failedAt) : '-'}</div>
                </div>
                {event.message ? <p className="mt-3 text-sm text-slate-700">{event.message}</p> : null}
                {event.payload ? <div className="mt-4"><JsonViewer value={event.payload} /></div> : null}
              </div>
            ))}
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
