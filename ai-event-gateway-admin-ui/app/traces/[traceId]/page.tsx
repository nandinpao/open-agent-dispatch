import { TraceDetailView } from '@/components/trace/TraceDetailView';
import { LegacyRuntimePlaneNotice } from '@/components/common/LegacyRuntimePlaneNotice';

export default async function TraceDetailPage({ params }: { params: Promise<{ traceId: string }> }) {
  const { traceId } = await params;
  return (
    <main className="space-y-5">
      <LegacyRuntimePlaneNotice compact description="此 Trace 明細保留 Netty runtime/local trace 語意，適合排查 delivery pipeline。Task/Dispatch 最終狀態請以 Core Tasks 頁與 Core detail 為準。" />
      <TraceDetailView traceId={decodeURIComponent(traceId)} />
    </main>
  );
}
