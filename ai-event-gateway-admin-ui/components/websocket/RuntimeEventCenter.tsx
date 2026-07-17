'use client';

import { useMemo, useState } from 'react';
import { JsonViewer } from '@/components/common/JsonViewer';
import { ListFilterBar, type SelectFilterConfig } from '@/components/common/ListFilterBar';
import { PaginationControls } from '@/components/common/PaginationControls';
import { StatusBadge } from '@/components/common/StatusBadge';
import { RuntimeEventSummaryPanel } from '@/components/websocket/RuntimeEventSummaryPanel';
import { WebSocketConnectionPanel } from '@/components/websocket/WebSocketConnectionPanel';
import { useRuntimeEventCenter } from '@/hooks/useRuntimeEventCenter';
import { categoryLabel, getRuntimeEventCategory, getRuntimeEventDisplay, getRuntimeEventSeverity, isRuntimeRiskEvent, type RuntimeEventCategory } from '@/lib/realtime/runtimeEventCenter';
import type { AdminWebSocketEvent } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from '@/lib/utils/list';

const allValue = 'ALL';

const categoryOptions = [
  'ALL',
  'AGENT_AUTH',
  'AGENT_SESSION',
  'DELIVERY',
  'CALLBACK',
  'SECURITY',
  'TASK',
  'CLUSTER',
  'METRICS',
  'SYSTEM',
  'RISK'
] as const;

type CategoryOption = (typeof categoryOptions)[number];

function matchesCategory(event: AdminWebSocketEvent, category: CategoryOption): boolean {
  if (category === 'ALL') return true;
  if (category === 'RISK') return isRuntimeRiskEvent(event);
  return getRuntimeEventCategory(event) === category;
}

function matchesEvent(event: AdminWebSocketEvent, query: string, category: CategoryOption, severity: string, nodeId: string, agentId: string, taskId: string): boolean {
  if (!matchesCategory(event, category)) return false;
  if (severity !== allValue && getRuntimeEventSeverity(event) !== severity) return false;
  if (nodeId !== allValue && event.nodeId !== nodeId) return false;
  if (agentId !== allValue && event.agentId !== agentId) return false;
  if (taskId !== allValue && event.taskId !== taskId) return false;

  const display = getRuntimeEventDisplay(event);
  return recordIncludesQuery([
    event.eventType,
    display.title,
    display.description,
    display.category,
    display.severity,
    event.nodeId,
    event.agentId,
    event.taskId,
    event.traceId,
    event.status,
    event.message
  ], query);
}

function optionLabel(option: CategoryOption): string {
  if (option === 'ALL') return 'All';
  if (option === 'RISK') return 'Risk / Failed';
  return categoryLabel(option as RuntimeEventCategory);
}

export function RuntimeEventCenter() {
  const { events, summary } = useRuntimeEventCenter();
  const [categoryFilter, setCategoryFilter] = useState<CategoryOption>('ALL');
  const [severityFilter, setSeverityFilter] = useState(allValue);
  const [search, setSearch] = useState('');
  const [nodeFilter, setNodeFilter] = useState(allValue);
  const [agentFilter, setAgentFilter] = useState(allValue);
  const [taskFilter, setTaskFilter] = useState(allValue);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const filteredEvents = useMemo(
    () => events.filter((event) => matchesEvent(event, search, categoryFilter, severityFilter, nodeFilter, agentFilter, taskFilter)),
    [agentFilter, categoryFilter, events, nodeFilter, search, severityFilter, taskFilter]
  );

  const pagination = useMemo(() => paginateItems(filteredEvents, { page, pageSize }), [filteredEvents, page, pageSize]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const nodes = uniqueSortedValues(events.map((event) => event.nodeId));
    const agents = uniqueSortedValues(events.map((event) => event.agentId));
    const tasks = uniqueSortedValues(events.map((event) => event.taskId));

    return [
      {
        id: 'severity',
        label: 'Severity',
        value: severityFilter,
        onChange: (value) => {
          setSeverityFilter(value);
          setPage(1);
        },
        options: [
          { value: allValue, label: 'All Severities' },
          { value: 'SUCCESS', label: 'Success' },
          { value: 'INFO', label: 'Info' },
          { value: 'WARNING', label: 'Warning' },
          { value: 'ERROR', label: 'Error' }
        ]
      },
      {
        id: 'node',
        label: 'Node',
        value: nodeFilter,
        onChange: (value) => {
          setNodeFilter(value);
          setPage(1);
        },
        options: [{ value: allValue, label: 'All Nodes' }, ...nodes.map((nodeId) => ({ value: nodeId, label: nodeId }))]
      },
      {
        id: 'agent',
        label: 'Agent',
        value: agentFilter,
        onChange: (value) => {
          setAgentFilter(value);
          setPage(1);
        },
        options: [{ value: allValue, label: 'All Agents' }, ...agents.map((agentId) => ({ value: agentId, label: agentId }))]
      },
      {
        id: 'task',
        label: 'Task',
        value: taskFilter,
        onChange: (value) => {
          setTaskFilter(value);
          setPage(1);
        },
        options: [{ value: allValue, label: 'All Tasks' }, ...tasks.map((taskId) => ({ value: taskId, label: taskId }))]
      }
    ];
  }, [agentFilter, events, nodeFilter, severityFilter, taskFilter]);

  function clearFilters() {
    setSearch('');
    setCategoryFilter('ALL');
    setSeverityFilter(allValue);
    setNodeFilter(allValue);
    setAgentFilter(allValue);
    setTaskFilter(allValue);
    setPage(1);
  }

  return (
    <div className="space-y-4">
      <WebSocketConnectionPanel />
      <RuntimeEventSummaryPanel summary={summary} />

      <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex flex-wrap gap-2">
          {categoryOptions.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => {
                setCategoryFilter(option);
                setPage(1);
              }}
              className={`rounded-xl px-3 py-2 text-xs font-semibold ${categoryFilter === option ? 'bg-blue-600 text-white' : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'}`}
            >
              {optionLabel(option)}
            </button>
          ))}
        </div>
      </section>

      <ListFilterBar
        search={search}
        searchPlaceholder="搜尋 event type、category、severity、node、agent、task、trace、message..."
        onSearchChange={(value) => {
          setSearch(value);
          setPage(1);
        }}
        filters={filters}
        onClear={clearFilters}
      />

      <div className="space-y-3">
        {filteredEvents.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
            目前沒有符合條件的 runtime event。
          </div>
        ) : pagination.items.map((event, index) => {
          const display = getRuntimeEventDisplay(event);
          return (
            <article key={`${event.timestamp}-${event.eventType}-${index}`} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="text-sm font-bold text-slate-950">{display.title}</div>
                    <StatusBadge status={display.severity} />
                    <StatusBadge status={display.category} />
                  </div>
                  <div className="mt-1 text-xs text-slate-500">{event.eventType} · {formatDateTime(event.timestamp)}</div>
                </div>
                {event.status ? <StatusBadge status={event.status} /> : null}
              </div>

              <div className="mt-3 rounded-xl border border-slate-100 bg-slate-50 p-3 text-sm text-slate-600">
                <div className="font-medium text-slate-700">{display.description}</div>
                <div className="mt-2 grid grid-cols-1 gap-2 text-xs md:grid-cols-4">
                  <div>Node：{event.nodeId ?? '-'}</div>
                  <div>Agent：{event.agentId ?? '-'}</div>
                  <div>Task：{event.taskId ?? '-'}</div>
                  <div>Trace：{event.traceId ?? '-'}</div>
                </div>
              </div>

              {event.payload ? <div className="mt-4"><JsonViewer value={event.payload} /></div> : null}
            </article>
          );
        })}
      </div>

      {filteredEvents.length > 0 ? (
        <PaginationControls
          page={pagination.page}
          pageSize={pagination.pageSize}
          totalItems={pagination.totalItems}
          totalPages={pagination.totalPages}
          startItem={pagination.startItem}
          endItem={pagination.endItem}
          onPageChange={setPage}
          onPageSizeChange={(value) => {
            setPageSize(value);
            setPage(1);
          }}
        />
      ) : null}
    </div>
  );
}
