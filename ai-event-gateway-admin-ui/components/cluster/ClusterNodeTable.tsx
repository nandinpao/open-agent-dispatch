'use client';

import Link from 'next/link';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { LoadingBox } from '@/components/common/LoadingBox';
import { MetricTimestampBadge } from '@/components/common/MetricTimestampBadge';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useClusterNodes } from '@/hooks/useClusterNodes';
import { formatDateTime, formatNumber, formatPercent } from '@/lib/utils/format';
import { getMemoryUsedPercent } from '@/lib/utils/runtimeMetrics';

export function ClusterNodeTable() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useClusterNodes();

  if (loading) return <LoadingBox label="讀取 Cluster 節點..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data || data.length === 0) return <EmptyState title="目前沒有 Cluster 節點" description="請確認 ai-event-gateway-netty Admin API 是否已啟動。" />;

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold text-slate-900">Node List</h2>
          <p className="mt-1 text-sm text-slate-500">SELF 是目前處理 Admin API request 的入口節點；REMOTE 是由 SELF 透過 cluster aggregation 彙整回來的其它節點。</p>
        </div>
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Node</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Host</th>
                <th className="px-4 py-3">Ports</th>
                <th className="px-4 py-3">CPU</th>
                <th className="px-4 py-3">Memory</th>
                <th className="px-4 py-3">Agents</th>
                <th className="px-4 py-3">Tasks</th>
                <th className="px-4 py-3">Last Heartbeat</th>
                <th className="px-4 py-3">Metrics</th>
                <th className="px-4 py-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((node) => (
                <tr key={node.nodeId} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-semibold text-slate-900">{node.nodeId}</td>
                  <td className="px-4 py-3"><div className="flex flex-wrap gap-1"><StatusBadge status={node.status} />{node.isCurrentRequestNode ? <StatusBadge status="SELF" /> : <StatusBadge status="REMOTE" />}</div></td>
                  <td className="px-4 py-3"><div className="flex flex-wrap gap-1">{node.role ? <StatusBadge status={node.role} /> : null}{node.drainStatus ? <StatusBadge status={node.drainStatus} /> : null}</div></td>
                  <td className="px-4 py-3 text-slate-600">{node.host}</td>
                  <td className="px-4 py-3 text-slate-600">TCP {node.tcpPort} / WS {node.websocketPort} / Admin {node.adminPort}</td>
                  <td className="px-4 py-3 text-slate-600">{formatPercent(node.metrics.cpuUsagePercent)}</td>
                  <td className="px-4 py-3 text-slate-600">{formatNumber(node.metrics.memoryUsedMb)} MB ({formatPercent(getMemoryUsedPercent(node.metrics))})</td>
                  <td className="px-4 py-3 text-slate-600">{node.agentCount}</td>
                  <td className="px-4 py-3 text-slate-600">{node.metrics.activeTaskCount}</td>
                  <td className="px-4 py-3 text-slate-600">{formatDateTime(node.lastHeartbeatAt)}</td>
                  <td className="px-4 py-3 text-slate-600"><MetricTimestampBadge timestamp={node.metrics.timestamp} label="Updated" /></td>
                  <td className="px-4 py-3">
                    <Link href={`/cluster/${encodeURIComponent(node.nodeId)}`} className="font-semibold text-blue-600 hover:text-blue-800">
                      Detail
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
