import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
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
    <EntitlementPageGuard featureId="engineering-tools"><main className="space-y-6">
      <PageHeader title="Cluster Topology" description="Inspect peer heartbeats and runtime metrics. SELF is the current Admin API process; REMOTE identifies peer nodes observed by this process." />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <InformationArchitectureGuide activeLayer="runtime" compact />
      <AdminPerspectiveBanner compact />
      <LegacyRuntimePlaneNotice compact />
      <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-600 shadow-sm">
        To verify API fallback and local-scope behavior, open <Link href="/cluster/diagnostics" className="font-bold text-blue-600 hover:text-blue-800">Cluster Diagnostics</Link>.
      </div>
      <ClusterTopologyPanel />
      <ClusterNodeTable />
    </main></EntitlementPageGuard>
  );
}
