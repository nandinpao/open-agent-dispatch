import { StatusBadge } from '@/components/common/StatusBadge';
import type { ClusterDataScope } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';

type DataScopeKind = 'agents' | 'tasks' | 'events' | 'realtime' | 'traces';

const copyByKind: Record<DataScopeKind, { clusterTitle: string; localTitle: string; clusterDescription: string; localDescription: string }> = {
  agents: {
    clusterTitle: ' Agent review.',
    localTitle: 'currentonlydisplay SELF Gateway  local Agent review.',
    clusterDescription: 'Data is requested through the current process. SELF gateway Agents remain owned by their runtime node and are reconciled through the shared Agent registry.',
    localDescription: 'Cluster aggregated Agent API  fallback to local API cluster.'
  },
  tasks: {
    clusterTitle: ' Task review.',
    localTitle: 'currentonlydisplay SELF Gateway  local Task review.',
    clusterDescription: 'databycurrentprocess request  SELF Gateway Task still by owner node  dispatch still is local dispatchDispatch information',
    localDescription: 'Cluster aggregated Task API  fallback to local API cluster.'
  },
  events: {
    clusterTitle: ' Event review.',
    localTitle: 'currentdisplay SELF Gateway  local Event review.',
    clusterDescription: ' cluster-wide event aggregationView details',
    localDescription: 'current Events  /api/admin/events local scope cluster-wide eventsView details /api/cluster/events or /api/cluster/events/by-node.'
  },
  realtime: {
    clusterTitle: 'currentdisplay cluster-wide realtime stream.',
    localTitle: 'currentdisplay SELF Gateway  Admin realtime stream.',
    clusterDescription: ' cluster-wide realtime events, this stream Event details',
    localDescription: ' WebSocket is Admin UI  Gateway endpoint  realtime connection cluster-wide events cluster event stream.'
  },
  traces: {
    clusterTitle: ' Trace review.',
    localTitle: 'currentdisplay SELF Gateway  local Trace review.',
    clusterDescription: ' cluster-wide trace aggregation trace.',
    localDescription: 'current Trace by local admin trace endpoint  Task/Event  owner node cluster trace aggregation '
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
        {localNodeId ? `SELF / Admin Aggregation Node:${localNodeId}.` : ''}
        {generatedAt ? `Data timestamp: ${formatDateTime(generatedAt)}.` : ''}
        {fallbackReason ? `Fallback:${fallbackReason}` : ''}
      </p>
    </div>
  );
}
