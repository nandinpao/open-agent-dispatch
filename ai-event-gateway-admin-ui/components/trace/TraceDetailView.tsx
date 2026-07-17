'use client';

import Link from 'next/link';
import { DataScopeNotice } from '@/components/common/DataScopeNotice';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { TraceTimeline } from '@/components/trace/TraceTimeline';
import { useTraceDetail } from '@/hooks/useTraceDetail';

export function TraceDetailView({ traceId }: Readonly<{ traceId: string }>) {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useTraceDetail(traceId);

  if (loading) return <LoadingBox label={`讀取 ${traceId} Trace...`} />;
  if (error) return <ErrorBox message={error} />;
  if (!data) return <EmptyState title="找不到 Trace" description="後端沒有回傳此 Trace detail。" />;

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex flex-wrap gap-3 text-sm font-semibold">
            <Link href="/events" className="text-blue-600 hover:text-blue-700">← Events</Link>
            <Link href="/tasks" className="text-blue-600 hover:text-blue-700">Tasks</Link>
          </div>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">{data.traceId}</h1>
          <p className="mt-1 text-sm text-slate-500">Trace Detail / Cross Event & Task Timeline</p>
        </div>
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>
      <DataScopeNotice kind="traces" scope="LOCAL" />
      <TraceTimeline trace={data} />
    </div>
  );
}
