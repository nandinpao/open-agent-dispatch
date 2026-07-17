import { PageHeader } from '@/components/common/PageHeader';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';
import { EventTable } from '@/components/events/EventTable';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function EventsPage() {
  return (
    <main className="space-y-5">
      <PageHeader title="Events" description="查看 SELF Gateway 的 local event stream、Routing Decision、Trace Timeline 與 Payload；目前尚非完整 cluster-wide event aggregation。" />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <LegacyRuntimePlaneNotice compact description="此頁仍是 Netty local event/runtime trace 視角，適合排查 transport pipeline。若要確認 Agent 安全事件，請使用 Security Events；若要確認 runtime stream，請使用 Runtime Events。" />
      <EventTable />
    </main>
  );
}
