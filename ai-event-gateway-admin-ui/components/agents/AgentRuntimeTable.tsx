'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { ListFilterBar, type SelectFilterConfig } from '@/components/common/ListFilterBar';
import { LoadingBox } from '@/components/common/LoadingBox';
import { PaginationControls } from '@/components/common/PaginationControls';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { AgentGovernanceWorkflowActions } from '@/components/agents/AgentGovernanceWorkflowActions';
import { useAgentRuntimeDashboard } from '@/hooks/useAgentRuntimeDashboard';
import { deriveAgentRuntimeWarning } from '@/lib/dashboard/agentMerge';
import { getAgentConnectionStatus, getAgentWorkloadStatus, getHeartbeatAgeMs, getHeartbeatStatus, getRowReviewTimestamp, getRuntimeBacklogLabel, getRuntimeCapacityLabel, getRuntimeLatencyLabel, getSuccessfulConnectedAt } from '@/lib/agents/agentRuntimeDisplay';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import { formatDateTime, formatDurationMs } from '@/lib/utils/format';
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from '@/lib/utils/list';

const allValue = 'ALL';

function rowRuntimeState(row: AgentDashboardRow): string {
  if (row.runtime?.authorizationState) return row.runtime.authorizationState;
  return getAgentConnectionStatus(row.runtime);
}

function rowAgentState(row: AgentDashboardRow): string {
  return getAgentWorkloadStatus(row.runtime);
}

function rowProfileState(row: AgentDashboardRow): string {
  return row.profile?.approvalStatus ?? 'NO_CORE_PROFILE';
}

function matchesRow(row: AgentDashboardRow, query: string, approvalStatus: string, runtimeStatus: string, source: string): boolean {
  if (approvalStatus !== allValue && rowProfileState(row) !== approvalStatus) return false;
  if (runtimeStatus !== allValue && ![rowRuntimeState(row), rowAgentState(row), getAgentConnectionStatus(row.runtime)].includes(runtimeStatus)) return false;
  if (source === 'MISSING_CORE' && row.source.profile !== 'MISSING') return false;
  if (source === 'MISSING_NETTY' && row.source.runtime !== 'MISSING') return false;
  if (source === 'DUAL' && (row.source.profile !== 'CORE' || row.source.runtime !== 'NETTY')) return false;

  return recordIncludesQuery([
    row.agentId,
    row.profile?.agentName,
    row.profile?.agentType,
    row.profile?.tenantId,
    row.profile?.ownerTeam,
    row.profile?.approvalStatus,
    row.profile?.riskStatus,
    row.enrollment?.status,
    row.enrollment?.enrollmentId,
    row.runtime?.gatewayNodeId,
    row.runtime?.nodeId,
    row.runtime?.sessionId,
    row.runtime?.transport,
    rowRuntimeState(row),
    rowAgentState(row),
    getHeartbeatStatus(row.runtime)
  ], query);
}

export function AgentRuntimeTable() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useAgentRuntimeDashboard();
  const [search, setSearch] = useState('');
  const [approvalFilter, setApprovalFilter] = useState(allValue);
  const [runtimeFilter, setRuntimeFilter] = useState(allValue);
  const [sourceFilter, setSourceFilter] = useState(allValue);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const rows = useMemo(() => data?.rows ?? [], [data]);
  const filtered = useMemo(
    () => rows.filter((row) => matchesRow(row, search, approvalFilter, runtimeFilter, sourceFilter)),
    [approvalFilter, rows, runtimeFilter, search, sourceFilter]
  );
  const pagination = useMemo(() => paginateItems(filtered, { page, pageSize }), [filtered, page, pageSize]);

  useEffect(() => {
    setPage(1);
  }, [approvalFilter, runtimeFilter, search, sourceFilter, pageSize]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const approvalStatuses = uniqueSortedValues(rows.map(rowProfileState));
    const runtimeStatuses = uniqueSortedValues(rows.flatMap((row) => [rowRuntimeState(row), rowAgentState(row), getAgentConnectionStatus(row.runtime)]));
    return [
      {
        id: 'approvalStatus',
        label: 'Core Status',
        value: approvalFilter,
        onChange: setApprovalFilter,
        options: [{ value: allValue, label: 'All Core Statuses' }, ...approvalStatuses.map((status) => ({ value: status, label: status }))]
      },
      {
        id: 'runtimeStatus',
        label: 'Runtime Status',
        value: runtimeFilter,
        onChange: setRuntimeFilter,
        options: [{ value: allValue, label: 'All Runtime Statuses' }, ...runtimeStatuses.map((status) => ({ value: status, label: status }))]
      },
      {
        id: 'source',
        label: 'Source',
        value: sourceFilter,
        onChange: setSourceFilter,
        options: [
          { value: allValue, label: 'All Sources' },
          { value: 'DUAL', label: 'Core + Netty' },
          { value: 'MISSING_CORE', label: 'Netty only' },
          { value: 'MISSING_NETTY', label: 'Core only' }
        ]
      }
    ];
  }, [approvalFilter, rows, runtimeFilter, sourceFilter]);

  function clearFilters() {
    setSearch('');
    setApprovalFilter(allValue);
    setRuntimeFilter(allValue);
    setSourceFilter(allValue);
  }

  if (loading) return <LoadingBox label="讀取 Agent Core profile 與 Netty runtime..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data || data.rows.length === 0) return <EmptyState title="目前沒有 Agent runtime 資料" description="Core profile 與 Netty runtime 都沒有回傳 Agent。" />;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <div className="grid gap-3 text-sm sm:grid-cols-3">
          <div><span className="text-slate-500">Core profiles：</span><span className="font-semibold text-slate-900">{data.profiles.length}</span></div>
          <div><span className="text-slate-500">Netty runtimes：</span><span className="font-semibold text-slate-900">{data.runtimes.length}</span></div>
          <div><span className="text-slate-500">Merged rows：</span><span className="font-semibold text-slate-900">{data.rows.length}</span></div>
        </div>
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>

      <div className="rounded-2xl border border-amber-100 bg-amber-50 p-4 text-sm text-amber-800">
        此頁是 runtime merge view：顯示 Core profile + Netty cluster runtime。Netty-only row 代表 Agent 已連線但尚未完成 Core enrollment / approval，可從 Actions 建立 enrollment。
      </div>

      <ListFilterBar
        search={search}
        searchPlaceholder="搜尋 Agent、tenant、node、session、transport、狀態..."
        onSearchChange={setSearch}
        filters={filters}
        onClear={clearFilters}
      />

      {filtered.length === 0 ? (
        <EmptyState title="沒有符合條件的 Agent" description="請調整關鍵字、Core 狀態、runtime 狀態或來源篩選條件。" />
      ) : (
        <>
          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="whitespace-nowrap px-4 py-3">Agent</th>
                    <th className="whitespace-nowrap px-4 py-3">Core Trust</th>
                    <th className="whitespace-nowrap px-4 py-3">Connection / Agent Status</th>
                    <th className="whitespace-nowrap px-4 py-3">Gateway / Session</th>
                    <th className="whitespace-nowrap px-4 py-3">Heartbeat</th>
                    <th className="whitespace-nowrap px-4 py-3">Connected / Latency</th>
                    <th className="whitespace-nowrap px-4 py-3">Source</th>
                    <th className="whitespace-nowrap px-4 py-3">Warning</th>
                    <th className="whitespace-nowrap px-4 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {pagination.items.map((row) => {
                    const warning = deriveAgentRuntimeWarning(row);
                    return (
                      <tr key={row.agentId} className="align-top hover:bg-slate-50">
                        <td className="px-4 py-3">
                          <Link href={`/agents/${encodeURIComponent(row.agentId)}`} className="font-semibold text-blue-700 hover:underline">
                            {row.agentId}
                          </Link>
                          <div className="mt-1 text-xs text-slate-500">{row.profile?.agentName ?? row.profile?.agentType ?? '-'}</div>
                          <div className="text-xs text-slate-400">tenant: {row.profile?.tenantId ?? '-'}</div>
                          {row.runtimeSummary?.duplicateRuntimeDetected ? (
                            <div className="mt-2 rounded-lg border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-700">
                              duplicate runtime: {row.runtimeSummary.connectedCount} sessions
                            </div>
                          ) : null}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <div className="space-y-1">
                            <StatusBadge status={rowProfileState(row)} />
                            <div className="text-xs text-slate-500">enabled: {row.profile ? String(row.profile.enabled) : '-'}</div>
                            <div className="text-xs text-slate-500">risk: {row.profile?.riskStatus ?? '-'}</div>
                            {row.enrollment ? <div className="text-xs text-amber-700">enrollment: {row.enrollment.status}</div> : null}
                            {(() => {
                              const review = getRowReviewTimestamp(row);
                              return review.value ? <div className="text-xs text-slate-500">{review.label}: {formatDateTime(review.value)}</div> : null;
                            })()}
                          </div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <div className="space-y-1">
                            <div className="flex flex-wrap gap-1">
                              <StatusBadge status={getAgentConnectionStatus(row.runtime)} />
                              <StatusBadge status={rowAgentState(row)} />
                            </div>
                            <div className="text-xs text-slate-500">authorization: {row.runtime?.authorizationState ?? '-'}</div>
                            <div className="text-xs text-slate-500">transport: {row.runtime?.transport ?? '-'}</div>
                            <div className="text-xs text-slate-500">capacity: {getRuntimeCapacityLabel(row.runtime)}</div>
                            <div className="text-xs text-slate-500">backlog: {getRuntimeBacklogLabel(row.runtime)}</div>
                          </div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                          <div>{row.runtime?.gatewayNodeId ?? row.runtime?.nodeId ?? '-'}</div>
                          <div className="text-xs text-slate-400">{row.runtime?.sessionId ?? row.runtime?.connectionId ?? '-'}</div>
                          {row.runtimeSummary?.gatewayNodeIds && row.runtimeSummary.gatewayNodeIds.length > 1 ? (
                            <div className="mt-1 max-w-48 whitespace-normal text-xs text-rose-700">all nodes: {row.runtimeSummary.gatewayNodeIds.join(', ')}</div>
                          ) : null}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                          <div className="mb-1"><StatusBadge status={getHeartbeatStatus(row.runtime)} /></div>
                          <div>{formatDateTime(row.runtime?.lastHeartbeatAt ?? row.runtime?.lastSeenAt)}</div>
                          <div className="text-xs text-slate-400">age: {formatDurationMs(getHeartbeatAgeMs(row.runtime))}</div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                          <div>connected: {formatDateTime(getSuccessfulConnectedAt(row.runtime))}</div>
                          <div className="text-xs text-slate-400">latency: {getRuntimeLatencyLabel(row.runtime)}</div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                          <div>profile: {row.source.profile}</div>
                          <div>runtime: {row.source.runtime}</div>
                        </td>
                        <td className="max-w-sm px-4 py-3 text-xs text-slate-600">
                          {warning ? <span className="font-semibold text-amber-700">{warning}</span> : '-'}
                        </td>
                        <td className="px-4 py-3">
                          <AgentGovernanceWorkflowActions row={row} onChanged={refresh} />
                        </td>
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
