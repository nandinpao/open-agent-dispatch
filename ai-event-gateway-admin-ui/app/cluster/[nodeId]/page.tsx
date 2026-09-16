import { ClusterNodeDetailView } from '@/components/cluster/ClusterNodeDetailView';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';

export default async function ClusterNodeDetailPage({ params }: { params: Promise<{ nodeId: string }> }) {
  const { nodeId } = await params;
  return (
    <main className="space-y-5">
      <LegacyRuntimePlaneNotice compact description="This legacy view shows a Netty gateway node, local Agents, local Tasks, and runtime metrics. Return to Core for authoritative Agent and Task status." />
      <ClusterNodeDetailView nodeId={decodeURIComponent(nodeId)} />
    </main>
  );
}
