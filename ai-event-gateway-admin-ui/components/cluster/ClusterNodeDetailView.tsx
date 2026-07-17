'use client';

import Link from 'next/link';
import { useState, type ReactNode } from 'react';
import { CommandMessage } from '@/components/common/CommandMessage';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { JsonViewer } from '@/components/common/JsonViewer';
import { LoadingBox } from '@/components/common/LoadingBox';
import { MetricCard } from '@/components/common/MetricCard';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { gatewayTelemetryMissingEmptyText, nodeTaskCorrelationLabel } from '@/lib/cluster/nodeTaskCorrelation';
import { callbackTruthSummary, gatewayDiagnosticsDisclaimer } from '@/lib/runtime/callbackTruth';
import { useClusterNodeDetail } from '@/hooks/useClusterNodeDetail';
import { useAdminRealtime } from '@/hooks/useAdminRealtime';
import { formatDateTime, formatDuration, formatDurationMs, formatMemory, formatPercent } from '@/lib/utils/format';
import { getMemoryUsedPercent, selectFreshestClusterMetrics } from '@/lib/utils/runtimeMetrics';

export function ClusterNodeDetailView({ nodeId }: Readonly<{ nodeId: string }>) {
  const { detail, agents, tasks, taskCorrelation, commandMessage, commandRunning, drainNode, resumeNode } = useClusterNodeDetail(nodeId);
  const { nodeMetricsById, lastMetricsAt } = useAdminRealtime();
  const [showRaw, setShowRaw] = useState(false);

  if (detail.loading) return <LoadingBox label="讀取 Cluster Node 詳細資料..." />;
  if (detail.error) return <ErrorBox message={detail.error} />;
  if (!detail.data) return <EmptyState title="找不到 Cluster Node" description="請確認 nodeId 是否存在於目前 Cluster。" />;

  const restNode = detail.data;
  const node = {
    ...restNode,
    metrics: selectFreshestClusterMetrics(restNode.metrics, nodeMetricsById[restNode.nodeId])
  };
  const memoryPercent = getMemoryUsedPercent(node.metrics);
  const visibleAgents = agents.data ?? node.agents;
  const visibleTasks = tasks.data ?? node.recentTasks;

  async function handleDrain() {
    const confirmed = window.confirm('Drain Node 會停止接收新任務，既有任務會繼續完成。確定要執行嗎？');
    if (confirmed) await drainNode();
  }

  async function handleResume() {
    const confirmed = window.confirm('Resume Node 會讓節點重新接收新任務。確定要執行嗎？');
    if (confirmed) await resumeNode();
  }

  return (
    <main className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link href="/cluster" className="text-sm font-semibold text-blue-600 hover:text-blue-800">← Back to Cluster</Link>
          <h1 className="mt-2 text-3xl font-bold text-slate-950">{node.nodeId}</h1>
          <p className="mt-1 text-slate-500">{node.advertisedAddress} · {node.discoveryMode} discovery · {node.region ?? '-'} / {node.zone ?? '-'}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <StatusBadge status={node.status} />
            {node.isCurrentRequestNode ? <StatusBadge status="SELF" /> : null}
            <StatusBadge status={node.role} />
            <StatusBadge status={node.drainStatus} />
            <StatusBadge status={node.acceptsNewTasks ? 'ACCEPT_TASKS' : 'NO_NEW_TASKS'} />
          </div>
        </div>
        <RefreshButton refreshing={detail.refreshing} lastUpdatedAt={lastMetricsAt ?? detail.lastUpdatedAt} onRefresh={detail.refresh} />
      </div>

      <CommandMessage message={commandMessage} />

      <div className="grid gap-4 md:grid-cols-4">
        <MetricCard title="Agents" value={node.agentCount} subtitle="owner node 為此節點" />
        <MetricCard title="Runtime Active" value={node.metrics.activeTaskCount} subtitle="Node-local live workload observation" />
        <MetricCard title="Queue Size" value={node.metrics.queueSize} subtitle="等待被派發或處理" />
        <MetricCard title="Avg Latency" value={node.metrics.averageLatencyMs ? `${node.metrics.averageLatencyMs} ms` : '-'} subtitle="peer / admin heartbeat" />
      </div>

      <section className="grid gap-4 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm lg:col-span-2">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900">Node Runtime Diagnostics</h2>
              <p className="mt-1 text-sm text-slate-500">Netty worker、port、heartbeat、discovery 與啟動時間。此頁是 runtime diagnostics；Task / callback truth 以 Core Dispatch Ledger / Callback Inbox 為準。</p>
            </div>
            <button
              type="button"
              onClick={() => setShowRaw((value) => !value)}
              className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
            >
              {showRaw ? 'Hide JSON' : 'Show JSON'}
            </button>
          </div>

          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <InfoItem label="Host" value={node.host} />
            <InfoItem label="Ports" value={`TCP ${node.tcpPort} / WS ${node.websocketPort} / Admin ${node.adminPort}`} />
            <InfoItem label="Started At" value={node.startedAt ? formatDateTime(node.startedAt) : '-'} />
            <InfoItem label="Uptime" value={node.uptimeSeconds ? formatDuration(node.uptimeSeconds) : '-'} />
            <InfoItem label="Last Heartbeat" value={formatDateTime(node.lastHeartbeatAt)} />
            <InfoItem label="Last Discovery" value={node.lastDiscoveryAt ? formatDateTime(node.lastDiscoveryAt) : '-'} />
            <InfoItem label="Source Status" value={[node.sourceStatus, node.syncStatus ? `sync=${node.syncStatus}` : undefined].filter(Boolean).join(' / ') || '-'} />
          </div>

          {showRaw ? <div className="mt-5"><JsonViewer value={node} /></div> : null}
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-lg font-bold text-slate-900">維運操作</h2>
          <p className="mt-1 text-sm text-slate-500">Drain 用於維護前停止接新任務；Resume 用於恢復服務。</p>
          <div className="mt-5 space-y-3">
            <button
              type="button"
              disabled={commandRunning || node.drainStatus === 'DRAINING' || node.drainStatus === 'DRAINED'}
              onClick={handleDrain}
              className="w-full rounded-xl bg-amber-500 px-4 py-3 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {commandRunning ? 'Running...' : 'Drain Node'}
            </button>
            <button
              type="button"
              disabled={commandRunning || node.status === 'OFFLINE'}
              onClick={handleResume}
              className="w-full rounded-xl bg-blue-600 px-4 py-3 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {commandRunning ? 'Running...' : 'Resume Node'}
            </button>
          </div>
          <div className="mt-5 rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
            <div>Accepts new tasks：<strong>{node.acceptsNewTasks ? 'YES' : 'NO'}</strong></div>
            <div className="mt-1">Drain status：<strong>{node.drainStatus}</strong></div>
          </div>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        <MetricCard title="CPU" value={formatPercent(node.metrics.cpuUsagePercent)} subtitle={lastMetricsAt ? `WebSocket realtime · ${formatDateTime(lastMetricsAt)}` : '節點 CPU 使用率'} />
        <MetricCard title="Memory" value={formatMemory(node.metrics.memoryUsedMb, node.metrics.memoryMaxMb)} subtitle={`${formatPercent(memoryPercent)} used`} />
        <MetricCard title="Event Loop / Worker" value={`${node.metrics.nettyEventLoopThreads}/${node.metrics.workerThreads}`} subtitle="Netty event loop / worker threads" />
      </section>

      <section className="rounded-2xl border border-blue-100 bg-blue-50 p-5 text-sm text-blue-900 shadow-sm">
        <h2 className="text-base font-bold">Callback Truth Boundary</h2>
        <p className="mt-1">{callbackTruthSummary()}</p>
        <p className="mt-2 text-blue-800">{gatewayDiagnosticsDisclaimer()}</p>
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <TableCard title="Peer Relation / Heartbeat" description="Cluster peer relation、state sync 與 heartbeat 狀態。Heartbeat 來源為後端 P3.9 cluster state pull 成功時間。">
          {node.peers.length === 0 ? (
            <div className="p-4 text-sm text-slate-500">目前 API 尚未回傳此節點的 peer relation / heartbeat payload。</div>
          ) : (
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Peer</th>
                  <th className="px-4 py-3">Health</th>
                  <th className="px-4 py-3">Relation</th>
                  <th className="px-4 py-3">Sync</th>
                  <th className="px-4 py-3">Heartbeat</th>
                  <th className="px-4 py-3">Latency</th>
                  <th className="px-4 py-3">Missed</th>
                  <th className="px-4 py-3">Last heartbeat</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {node.peers.map((peer) => (
                  <tr key={peer.nodeId} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-semibold text-slate-900">{peer.nodeId}</td>
                    <td className="px-4 py-3"><StatusBadge status={peer.status} /></td>
                    <td className="px-4 py-3 text-slate-600">{peer.relation}</td>
                    <td className="px-4 py-3"><StatusBadge status={peer.syncStatus ?? 'UNKNOWN'} /></td>
                    <td className="px-4 py-3"><StatusBadge status={peer.heartbeatStatus ?? 'UNKNOWN'} /></td>
                    <td className="px-4 py-3 text-slate-600">{formatDurationMs(peer.heartbeatLatencyMs ?? peer.latencyMs)}</td>
                    <td className="px-4 py-3 text-slate-600">{peer.missedHeartbeatCount ?? 0}</td>
                    <td className="px-4 py-3 text-slate-600">{formatDateTime(peer.lastHeartbeatAt ?? peer.lastSeenAt ?? peer.lastSyncAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </TableCard>

        <TableCard title="Agents on Node" description="優先使用 /api/cluster/agents by-node 聚合資料；此清單代表 owner node 為本節點的 Agent，不代表共享分散式 AgentRegistry。">
          {agents.loading ? <LoadingBox label="讀取 Agent..." /> : agents.error ? <ErrorBox message={agents.error} /> : visibleAgents.length === 0 ? (
            <div className="p-4 text-sm text-slate-500">目前沒有回傳此節點的 Agent 明細。若 overview 只有 summary，請確認 /api/cluster/agents 或 /api/cluster/agents/by-node 是否已啟用。</div>
          ) : (
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr><th className="px-4 py-3">Agent</th><th className="px-4 py-3">Type</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Owner Node</th><th className="px-4 py-3">Task</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {visibleAgents.map((agent) => (
                  <tr key={agent.agentId} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-semibold text-slate-900"><Link href={`/agents/${encodeURIComponent(agent.agentId)}`} className="text-blue-600 hover:text-blue-800">{agent.agentId}</Link></td>
                    <td className="px-4 py-3 text-slate-600">{agent.agentType}</td>
                    <td className="px-4 py-3"><StatusBadge status={agent.status} /></td>
                    <td className="px-4 py-3 text-slate-600">{agent.ownerNodeId ?? agent.nodeId}</td>
                    <td className="px-4 py-3 text-slate-600">{agent.currentTaskId ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </TableCard>
      </section>

      <section className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900 shadow-sm">
        <h2 className="text-base font-bold">Gateway diagnostics 不是 Task truth，但應能看到 delivery history</h2>
        <p className="mt-1 leading-6">如果 Dashboard 的 Recent Core Tasks 有資料，但下方 Recent Gateway Relay Diagnostics 沒資料，代表 Netty 目前沒有回報本 node 的 command delivery history。請確認 Core dispatch 是透過 Netty internal delivery API 投遞，且 Agent worker 不是 observe / idle。</p>
        <pre className="mt-3 overflow-x-auto rounded-xl bg-slate-950 p-3 text-xs text-slate-100">{`CORE_BOOTSTRAP_AGENTS=true \
AGENT_WORKER_MODE=process-result \
AGENT_WORKER_PROCESSING_MS=8000 \
AGENT_MAX_CONCURRENT_TASKS=3 \
./scripts/cluster-run-many-agents.sh restart`}</pre>
      </section>

      <TableCard title="Recent Gateway Relay Diagnostics" description="只顯示 Netty command delivery tracker / runtime delivery history 回報的 delivery diagnostics；不混入 Core recent tasks。Task 與 callback 權威狀態請到 Task Detail / Dispatch Ledger / Callback Inbox 查看。">
        {taskCorrelation ? (
          <div className="border-b border-slate-100 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold text-slate-800">資料來源：</span>
              <StatusBadge status={taskCorrelation.source === 'NETTY_NODE' ? 'OK' : 'WARNING'} label={nodeTaskCorrelationLabel(taskCorrelation.source)} />
              {taskCorrelation.telemetryMissing ? <StatusBadge status="WARNING" label="Runtime telemetry missing" /> : null}
            </div>
            <p className="mt-2">{taskCorrelation.reason}</p>
            {taskCorrelation.coreRecentTasksCount > 0 ? <p className="mt-1 text-xs text-slate-500">Core recent tasks detected: {taskCorrelation.coreRecentTasksCount}. These are intentionally not rendered as node relay diagnostics.</p> : null}
          </div>
        ) : null}
        {tasks.loading ? <LoadingBox label="讀取 Task..." /> : tasks.error ? <ErrorBox message={tasks.error} /> : visibleTasks.length === 0 ? (
          <div className="space-y-2 p-4 text-sm text-slate-500"><div className="font-semibold text-slate-700">No gateway delivery / callback relay telemetry for this node.</div><div>{gatewayTelemetryMissingEmptyText(taskCorrelation ?? undefined)}</div><div>這不代表 Core 沒有 Task；代表此 Gateway node 沒有可用的 runtime relay diagnostics。請到 Tasks / Task Detail 查看權威狀態。</div></div>
        ) : (
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr><th className="px-4 py-3">Task</th><th className="px-4 py-3">Relay Status</th><th className="px-4 py-3">Gateway Node</th><th className="px-4 py-3">Agent</th><th className="px-4 py-3">Retry</th><th className="px-4 py-3">Created</th><th className="px-4 py-3">Truth Boundary</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {visibleTasks.map((task) => (
                <tr key={task.taskId} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-semibold"><Link href={`/tasks/${encodeURIComponent(task.taskId)}`} className="text-blue-600 hover:text-blue-800">{task.taskId}</Link></td>
                  <td className="px-4 py-3"><StatusBadge status={task.status} /></td>
                  <td className="px-4 py-3 text-slate-600">{task.ownerNodeId ?? task.assignedNodeId ?? '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{task.assignedAgentId ?? '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{task.retryCount}</td>
                  <td className="px-4 py-3 text-slate-600">{formatDateTime(task.createdAt)}</td>
                  <td className="px-4 py-3 text-slate-600">{taskCorrelation ? `${nodeTaskCorrelationLabel(taskCorrelation.source)} · diagnostics only` : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </TableCard>
    </main>
  );
}

function InfoItem({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-xl bg-slate-50 p-4">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</div>
      <div className="mt-1 break-words text-sm font-semibold text-slate-800">{value}</div>
    </div>
  );
}

function TableCard({ title, description, children }: Readonly<{ title: string; description: string; children: ReactNode }>) {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-100 px-4 py-3">
        <h2 className="font-bold text-slate-900">{title}</h2>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      </div>
      <div className="overflow-x-auto">{children}</div>
    </section>
  );
}
