import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { TraceDetailView } from '@/components/trace/TraceDetailView';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';

export default async function TraceDetailPage({ params }: { params: Promise<{ traceId: string }> }) {
  const { traceId } = await params;
  return (
    <EntitlementPageGuard featureId="engineering-tools"><main className="space-y-5">
      <LegacyRuntimePlaneNotice compact description="This Trace view shows legacy Netty/runtime delivery evidence. Review Task/Dispatch status in the Core Tasks workspace before using low-level trace details for troubleshooting." />
      <TraceDetailView traceId={decodeURIComponent(traceId)} />
    </main></EntitlementPageGuard>
  );
}
