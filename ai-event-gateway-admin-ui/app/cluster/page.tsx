import Link from 'next/link';
import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { AdminPerspectiveBanner } from '@/components/common/AdminPerspectiveBanner';
import { PageHeader } from '@/components/common/PageHeader';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';
import { ClusterNodeTable } from '@/components/cluster/ClusterNodeTable';
import { ClusterTopologyPanel } from '@/components/cluster/ClusterTopologyPanel';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function ClusterPage() {
  return (
    <main className="space-y-6">
      <PageHeader title="Cluster Topology" description="查看節點拓樸、Peer heartbeat、runtime metrics 與聚合統計。SELF 是目前處理 Admin API request 的入口節點，REMOTE 是由 SELF 彙整回來的其它節點。" />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <InformationArchitectureGuide activeLayer="runtime" compact />
      <AdminPerspectiveBanner compact />
      <LegacyRuntimePlaneNotice compact />
      <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-600 shadow-sm">
        需要確認 API 是否可用、是否 fallback local scope，請開啟 <Link href="/cluster/diagnostics" className="font-bold text-blue-600 hover:text-blue-800">Cluster Diagnostics</Link>。
      </div>
      <ClusterTopologyPanel />
      <ClusterNodeTable />
    </main>
  );
}
