import { ClusterNodeDetailView } from '@/components/cluster/ClusterNodeDetailView';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';

export default async function ClusterNodeDetailPage({ params }: { params: Promise<{ nodeId: string }> }) {
  const { nodeId } = await params;
  return (
    <main className="space-y-5">
      <LegacyRuntimePlaneNotice compact description="此節點明細仍是 Netty cluster/runtime 視角，適合觀察 Gateway node、local agents、local tasks 與 runtime metrics；Agent 審核與 Task 權威狀態請回到 Core/dual-plane 頁面確認。" />
      <ClusterNodeDetailView nodeId={decodeURIComponent(nodeId)} />
    </main>
  );
}
