import { ClusterDiagnosticsPanel } from '@/components/cluster/ClusterDiagnosticsPanel';
import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { PageHeader } from '@/components/common/PageHeader';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function ClusterDiagnosticsPage() {
  return (
    <main className="space-y-6">
      <PageHeader title="Cluster Diagnostics" description="檢查 Netty Admin UI 實際使用的 cluster aggregation / local fallback API，釐清 SELF、REMOTE、cluster aggregated 與 local scope 的資料邊界。" />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <LegacyRuntimePlaneNotice compact description="此診斷頁保留 Netty cluster/runtime 檢查語意，用來確認 Gateway SELF/REMOTE、aggregation 與 local fallback。Agent 信任與 Task 權威狀態請以 Core 頁面為準。" />
      <InformationArchitectureGuide activeLayer="runtime" compact />
      <ClusterDiagnosticsPanel />
    </main>
  );
}
