import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { DataScopeNotice } from '@/components/common/DataScopeNotice';
import { PageHeader } from '@/components/common/PageHeader';
import { WebSocketEventStream } from '@/components/websocket/WebSocketEventStream';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function WebSocketPage() {
  return (
    <EntitlementPageGuard featureId="engineering-tools"><main className="space-y-4">
      <PageHeader title="Runtime Event Center" description="View Netty runtime events for Agent delivery, callback relay, security, and cluster operations. Core remains authoritative for Task and Dispatch status." />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <InformationArchitectureGuide activeLayer="runtime" compact />
      <DataScopeNotice kind="realtime" scope="LOCAL" />
      <WebSocketEventStream />
    </main></EntitlementPageGuard>
  );
}
