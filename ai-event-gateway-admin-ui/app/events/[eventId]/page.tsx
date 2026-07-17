import { EventDetailView } from '@/components/events/EventDetailView';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';

export default async function EventDetailPage({ params }: { params: Promise<{ eventId: string }> }) {
  const { eventId } = await params;
  return (
    <main className="space-y-5">
      <LegacyRuntimePlaneNotice compact description="此事件明細仍是 Netty runtime event/trace 視角，不代表 Core 的 Agent governance、Task 或 Dispatch 權威狀態。" />
      <EventDetailView eventId={decodeURIComponent(eventId)} />
    </main>
  );
}
