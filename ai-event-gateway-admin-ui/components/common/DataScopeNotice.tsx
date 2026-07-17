import { StatusBadge } from '@/components/common/StatusBadge';
import type { ClusterDataScope } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';

type DataScopeKind = 'agents' | 'tasks' | 'events' | 'realtime' | 'traces';

const copyByKind: Record<DataScopeKind, { clusterTitle: string; localTitle: string; clusterDescription: string; localDescription: string }> = {
  agents: {
    clusterTitle: '目前顯示叢集聚合 Agent 檢視。',
    localTitle: '目前只顯示 SELF Gateway 的 local Agent 檢視。',
    clusterDescription: '資料由目前處理 request 的 SELF Gateway 彙整各節點回報；Agent 仍由 owner node 擁有，不代表所有 Gateway 共用同一份 AgentRegistry。',
    localDescription: 'Cluster aggregated Agent API 無法使用時才會 fallback 到 local API；此時資料不代表整個 cluster。'
  },
  tasks: {
    clusterTitle: '目前顯示叢集聚合 Task 檢視。',
    localTitle: '目前只顯示 SELF Gateway 的 local Task 檢視。',
    clusterDescription: '資料由目前處理 request 的 SELF Gateway 彙整各節點回報；Task 仍由 owner node 擁有。查詢可聚合，但目前 dispatch 仍是 local dispatch，並非自動跨節點派工。',
    localDescription: 'Cluster aggregated Task API 無法使用時才會 fallback 到 local API；此時資料不代表整個 cluster。'
  },
  events: {
    clusterTitle: '目前顯示叢集聚合 Event 檢視。',
    localTitle: '目前顯示 SELF Gateway 的 local Event 檢視。',
    clusterDescription: '如果後端提供 cluster-wide event aggregation，這裡才可視為跨節點事件檢視。',
    localDescription: '目前 Events 仍來自 /api/admin/events local scope；尚非完整 cluster-wide events。若要跨節點事件檢視，後端需提供 /api/cluster/events 或 /api/cluster/events/by-node。'
  },
  realtime: {
    clusterTitle: '目前顯示 cluster-wide realtime stream。',
    localTitle: '目前顯示 SELF Gateway 的 Admin realtime stream。',
    clusterDescription: '後端若已廣播 cluster-wide realtime events，此 stream 可視為叢集聚合事件流。',
    localDescription: '這條 WebSocket 是 Admin UI 連到目前 Gateway endpoint 的 realtime connection；除非後端主動廣播 cluster-wide events，否則不應視為完整 cluster event stream。'
  },
  traces: {
    clusterTitle: '目前顯示叢集聚合 Trace 檢視。',
    localTitle: '目前顯示 SELF Gateway 的 local Trace 檢視。',
    clusterDescription: '後端若提供 cluster-wide trace aggregation，這裡才代表跨節點 trace。',
    localDescription: '目前 Trace 由 local admin trace endpoint 查詢；若 Task/Event 發生在其它 owner node，需後端提供 cluster trace aggregation 才能完整呈現。'
  }
};

export function DataScopeNotice({
  kind,
  scope,
  localNodeId,
  generatedAt,
  fallbackReason
}: Readonly<{
  kind: DataScopeKind;
  scope: ClusterDataScope;
  localNodeId?: string;
  generatedAt?: string;
  fallbackReason?: string;
}>) {
  const copy = copyByKind[kind];
  const isClusterAggregated = scope === 'CLUSTER';
  const status = isClusterAggregated ? 'CLUSTER_AGGREGATED' : kind === 'events' ? 'LOCAL_EVENT_SCOPE' : kind === 'realtime' ? 'LOCAL_REALTIME_SCOPE' : kind === 'traces' ? 'LOCAL_TRACE_SCOPE' : 'LOCAL_SCOPE';

  return (
    <div className={`rounded-2xl border p-4 text-sm ${isClusterAggregated ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge status={status} />
        <span className="font-semibold">{isClusterAggregated ? copy.clusterTitle : copy.localTitle}</span>
      </div>
      <p className="mt-1 leading-6">{isClusterAggregated ? copy.clusterDescription : copy.localDescription}</p>
      <p className="mt-1 text-xs opacity-90">
        {localNodeId ? `SELF / Admin Aggregation Node：${localNodeId}。` : ''}
        {generatedAt ? `資料時間：${formatDateTime(generatedAt)}。` : ''}
        {fallbackReason ? `Fallback：${fallbackReason}` : ''}
      </p>
    </div>
  );
}
