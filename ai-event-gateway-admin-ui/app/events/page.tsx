import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PageHeader } from '@/components/common/PageHeader';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';
import { EventTable } from '@/components/events/EventTable';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function EventsPage() {
  return (
    <EntitlementPageGuard featureId="engineering-tools"><main className="space-y-5">
      <PageHeader title="Events" description="View the SELF gateway event stream, routing decisions, trace timeline, and payload. Cluster-wide event aggregation is not available here." />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <LegacyRuntimePlaneNotice compact description="This legacy page shows the Netty-local event and trace transport pipeline. Use Security Events for security records and Runtime Events for the live runtime stream." />
      <EventTable />
    </main></EntitlementPageGuard>
  );
}
