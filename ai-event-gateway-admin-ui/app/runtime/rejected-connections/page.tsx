import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { PageHeader } from '@/components/common/PageHeader';
import { RejectedConnectionsTable } from '@/components/runtime/RejectedConnectionsTable';
import { rejectedConnectionSemantics } from '@/lib/runtime/rejectedConnectionSemantics';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function RejectedConnectionsPage() {
  return (
    <main className="space-y-6">
      <PageHeader title="Rejected Connections" description={rejectedConnectionSemantics().description} />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <InformationArchitectureGuide activeLayer="runtime" compact />
      <RejectedConnectionsTable />
    </main>
  );
}
