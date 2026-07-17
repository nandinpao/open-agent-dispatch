'use client';

import Link from 'next/link';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { LoadingBox } from '@/components/common/LoadingBox';
import { MetricCard } from '@/components/common/MetricCard';
import { MetricTimestampBadge } from '@/components/common/MetricTimestampBadge';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useClusterTopology } from '@/hooks/useClusterTopology';
import { useAdminRealtime } from '@/hooks/useAdminRealtime';
import { formatDateTime, formatDurationMs, formatNumber, formatPercent } from '@/lib/utils/format';
import { selectFreshestClusterMetrics } from '@/lib/utils/runtimeMetrics';

export function ClusterTopologyPanel() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useClusterTopology();
  const { nodeMetricsById, lastMetricsAt } = useAdminRealtime();

  if (loading) return <LoadingBox label="讀取 Cluster Topology..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data) return <EmptyState title="沒有 Cluster Topology" description="請確認後端是否提供 /api/admin/cluster/topology。" />;

  const summary = data.summary ?? { totalNodes: 0, onlineNodes: 0, drainingNodes: 0, totalAgents: 0, queueSize: 0, activeTasks: 0 };
  const nodes = (data.nodes ?? []).map((node) => ({
    ...node,
    metrics: selectFreshestClusterMetrics(node.metrics, nodeMetricsById[node.nodeId])
  }));
  const links = data.links ?? [];

  return (
    <section className="space-y-4">
      <div className="grid gap-4 md:grid-cols-4">
        <MetricCard title="Online Nodes" value={`${formatNumber(summary.onlineNodes)}/${formatNumber(summary.totalNodes)}`} subtitle="目前可服務節點" />
        <MetricCard title="Draining Nodes" value={formatNumber(summary.drainingNodes)} subtitle="維護 / Drain 中節點" />
        <MetricCard title="Total Agents" value={formatNumber(summary.totalAgents)} subtitle="Cluster aggregated agents" />
        <MetricCard title="Queue / Active" value={`${formatNumber(summary.queueSize)}/${formatNumber(summary.activeTasks)}`} subtitle="Aggregated queue / active" />
      </div>

      <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div>
          <h2 className="text-lg font-bold text-slate-900">Cluster Topology</h2>
          <p className="mt-1 text-sm text-slate-500">節點角色、Drain 狀態、Agent / Task 聚合統計與 peer relation / heartbeat 狀態。SELF 是目前處理 Admin API request 的入口節點；REMOTE 是由 SELF 節點透過 cluster sync 聚合回來的其它節點。</p>
        </div>
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastMetricsAt ?? lastUpdatedAt ?? data.generatedAt} onRefresh={refresh} />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {nodes.map((node) => (
          <Link
            key={node.nodeId}
            href={`/cluster/${encodeURIComponent(node.nodeId)}`}
            className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-xs font-semibold uppercase tracking-widest text-slate-400">{node.role}</div>
                <h3 className="mt-1 font-bold text-slate-900">{node.nodeId}</h3>
                <p className="mt-1 text-sm text-slate-500">{node.advertisedAddress}</p>
              </div>
              <div className="flex flex-col items-end gap-2">
                <StatusBadge status={node.status} />
                {node.isCurrentRequestNode ? <StatusBadge status="SELF" /> : null}
                <StatusBadge status={node.drainStatus} />
              </div>
            </div>

            <div className="mt-5 grid grid-cols-3 gap-3 text-sm">
              <div className="rounded-xl bg-slate-50 p-3">
                <div className="text-xs text-slate-500">Agents</div>
                <div className="mt-1 text-lg font-bold text-slate-900">{node.agentCount}</div>
              </div>
              <div className="rounded-xl bg-slate-50 p-3">
                <div className="text-xs text-slate-500">CPU</div>
                <div className="mt-1 text-lg font-bold text-slate-900">{formatPercent(node.metrics?.cpuUsagePercent)}</div>
              </div>
              <div className="rounded-xl bg-slate-50 p-3">
                <div className="text-xs text-slate-500">Memory</div>
                <div className="mt-1 text-lg font-bold text-slate-900">{formatNumber(node.metrics?.memoryUsedMb)} MB</div>
              </div>
            </div>

            <div className="mt-4 text-xs text-slate-500">
              Last heartbeat：{formatDateTime(node.lastHeartbeatAt)}
            </div>
            <div className="mt-2"><MetricTimestampBadge timestamp={node.metrics.timestamp} label="Metrics" /></div>
          </Link>
        ))}
      </div>

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 px-4 py-3 text-sm font-bold text-slate-900">Peer Relation / Heartbeat</div>
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">From</th>
              <th className="px-4 py-3">To</th>
              <th className="px-4 py-3">Relation</th>
              <th className="px-4 py-3">Health</th>
              <th className="px-4 py-3">Sync</th>
              <th className="px-4 py-3">Heartbeat</th>
              <th className="px-4 py-3">Latency</th>
              <th className="px-4 py-3">Missed</th>
              <th className="px-4 py-3">Last heartbeat</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {links.map((link) => (
              <tr key={`${link.fromNodeId}-${link.toNodeId}`} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-semibold text-slate-900">{link.fromNodeId}</td>
                <td className="px-4 py-3 text-slate-600">{link.toNodeId}</td>
                <td className="px-4 py-3 text-slate-600">{link.relation}</td>
                <td className="px-4 py-3"><StatusBadge status={link.status} /></td>
                <td className="px-4 py-3"><StatusBadge status={link.syncStatus ?? 'UNKNOWN'} /></td>
                <td className="px-4 py-3"><StatusBadge status={link.heartbeatStatus ?? 'UNKNOWN'} /></td>
                <td className="px-4 py-3 text-slate-600">{formatDurationMs(link.latencyMs)}</td>
                <td className="px-4 py-3 text-slate-600">{link.missedHeartbeatCount ?? 0}</td>
                <td className="px-4 py-3 text-slate-600">{link.lastSeenAt ? formatDateTime(link.lastSeenAt) : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
