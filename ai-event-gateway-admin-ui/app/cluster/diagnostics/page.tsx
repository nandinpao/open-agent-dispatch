import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { ClusterDiagnosticsPanel } from '@/components/cluster/ClusterDiagnosticsPanel';
import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { PageHeader } from '@/components/common/PageHeader';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function ClusterDiagnosticsPage() {
  return (
    <EntitlementPageGuard featureId="engineering-tools"><main className="space-y-6">
      <PageHeader title="Cluster Diagnostics" description="Inspect Netty cluster aggregation and local fallback behavior across SELF, REMOTE, cluster, and local scopes." />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <LegacyRuntimePlaneNotice compact description="This legacy view shows Netty gateway, cluster, and local runtime evidence. Core remains authoritative for Agent and Task status." />
      <InformationArchitectureGuide activeLayer="runtime" compact />
      <ClusterDiagnosticsPanel />
    </main></EntitlementPageGuard>
  );
}
