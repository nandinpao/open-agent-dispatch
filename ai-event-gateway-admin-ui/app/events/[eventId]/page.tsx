import { EventDetailView } from '@/components/events/EventDetailView';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';

export default async function EventDetailPage({ params }: { params: Promise<{ eventId: string }> }) {
  const { eventId } = await params;
  return (
    <main className="space-y-5">
      <LegacyRuntimePlaneNotice compact description="This legacy view shows Netty runtime event and trace evidence. Core remains authoritative for Agent governance, Tasks, and Dispatch." />
      <EventDetailView eventId={decodeURIComponent(eventId)} />
    </main>
  );
}
