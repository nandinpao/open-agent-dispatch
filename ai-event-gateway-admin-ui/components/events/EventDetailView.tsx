'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { DataScopeNotice } from '@/components/common/DataScopeNotice';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { JsonViewer } from '@/components/common/JsonViewer';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { TraceTimeline } from '@/components/trace/TraceTimeline';
import { useEventDetail } from '@/hooks/useEventDetail';
import type { GatewayEventDetail } from '@/lib/types/admin';
import { formatDateTime, toFiniteNumber } from '@/lib/utils/format';

function KeyValue({ label, value }: Readonly<{ label: string; value: ReactNode }>) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</div>
      <div className="mt-1 break-all text-sm font-semibold text-slate-800">{value}</div>
    </div>
  );
}

function EventSummary({ event }: Readonly<{ event: GatewayEventDetail }>) {
  const relatedTasks = Array.isArray(event.relatedTasks) ? event.relatedTasks : [];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Event Summary</h2>
          <p className="mt-1 text-sm text-slate-500">外部系統送入目前 SELF Gateway local event stream 的原始事件與 routing 結果；尚非完整 cluster-wide event aggregation。</p>
        </div>
        <StatusBadge status={event.status} />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <KeyValue label="Event ID" value={event.eventId} />
        <KeyValue label="Trace ID" value={<Link href={`/traces/${encodeURIComponent(event.traceId)}`} className="text-blue-600 hover:text-blue-700">{event.traceId}</Link>} />
        <KeyValue label="Source" value={event.sourceSystem} />
        <KeyValue label="Event Type" value={event.eventType} />
        <KeyValue label="Received" value={formatDateTime(event.receivedAt)} />
        <KeyValue label="Routed" value={event.routedAt ? formatDateTime(event.routedAt) : '-'} />
        <KeyValue label="Failed" value={event.failedAt ? formatDateTime(event.failedAt) : '-'} />
        <KeyValue label="Related Tasks" value={relatedTasks.length} />
      </div>
      {event.message ? <p className="mt-4 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">{event.message}</p> : null}
    </section>
  );
}

function RoutingDecisionPanel({ event }: Readonly<{ event: GatewayEventDetail }>) {
  const decision = event.routingDecision;
  const matchedSkills = decision?.matchedSkills ?? [];
  const missingSkillRequirements = decision?.missingSkillRequirements ?? [];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">Routing Decision</h2>
      <p className="mt-1 text-sm text-slate-500">用來確認 Gateway / Core 為什麼把事件派給某個 Agent / Node，並顯示 advanced policy compatibility diagnostics。</p>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <KeyValue label="Strategy" value={decision?.strategy ?? '-'} />
        <KeyValue label="Selected Agent" value={decision?.selectedAgentId ?? '-'} />
        <KeyValue label="Selected Node" value={decision?.selectedNodeId ?? '-'} />
        <KeyValue label="Score" value={toFiniteNumber(decision?.score as number | string | null | undefined)?.toFixed(2) ?? '-'} />
        <KeyValue label="Policy Compatibility" value={decision?.skillAware ? 'ENABLED' : 'LEGACY / UNKNOWN'} />
        <KeyValue label="Policy Contract" value={decision?.skillAware ? (decision?.skillEligible ? 'MATCHED' : 'MISSING') : '-'} />
        <KeyValue label="Matched Policies" value={matchedSkills.length ? matchedSkills.join(', ') : '-'} />
        <KeyValue label="Missing Policy Requirements" value={missingSkillRequirements.length ? missingSkillRequirements.join(', ') : '-'} />
      </div>
      <div className="mt-4 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
        {decision?.reason ?? '尚無 routing decision reason。'}
      </div>
    </section>
  );
}

function RelatedTaskPanel({ event }: Readonly<{ event: GatewayEventDetail }>) {
  const relatedTasks = Array.isArray(event.relatedTasks) ? event.relatedTasks : [];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">Related Tasks</h2>
      <p className="mt-1 text-sm text-slate-500">此事件被拆派或轉換出的 Task 清單。</p>
      {relatedTasks.length === 0 ? (
        <EmptyState title="尚未產生任務" description="事件可能還在 RECEIVED 狀態，或 routing 階段失敗。" />
      ) : (
        <div className="mt-4 overflow-hidden rounded-2xl border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Task ID</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Agent</th>
                <th className="px-4 py-3">Owner Node</th>
                <th className="px-4 py-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {relatedTasks.map((task) => (
                <tr key={task.taskId} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-semibold text-slate-900">{task.taskId}</td>
                  <td className="px-4 py-3"><StatusBadge status={task.status} /></td>
                  <td className="px-4 py-3 text-slate-600">{task.assignedAgentId ?? '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{task.ownerNodeId ?? task.assignedNodeId ?? '-'}</td>
                  <td className="px-4 py-3">
                    <Link href={`/tasks/${encodeURIComponent(task.taskId)}`} className="text-sm font-semibold text-blue-600 hover:text-blue-700">Detail</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function EventPayloadPanel({ event }: Readonly<{ event: GatewayEventDetail }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">Event Payload</h2>
      <p className="mt-1 text-sm text-slate-500">保留外部系統送入 Gateway 的原始 JSON，方便和 Task request payload 對照。</p>
      <div className="mt-4">
        {event.payload ? <JsonViewer value={event.payload} /> : <EmptyState title="沒有 Payload" description="後端未回傳 event payload。" />}
      </div>
    </section>
  );
}

export function EventDetailView({ eventId }: Readonly<{ eventId: string }>) {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useEventDetail(eventId);

  if (loading) return <LoadingBox label={`讀取 ${eventId} 事件明細...`} />;
  if (error) return <ErrorBox message={error} />;
  if (!data) return <EmptyState title="找不到事件" description="後端沒有回傳此 Event detail。" />;

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <Link href="/events" className="text-sm font-semibold text-blue-600 hover:text-blue-700">← Back to Events</Link>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">{data.eventType}</h1>
          <p className="mt-1 text-sm text-slate-500">Event Detail / Routing Decision / Trace Timeline</p>
        </div>
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>

      <DataScopeNotice kind="events" scope="LOCAL" />
      <EventSummary event={data} />
      <RoutingDecisionPanel event={data} />
      <RelatedTaskPanel event={data} />
      <EventPayloadPanel event={data} />
      <TraceTimeline trace={data.trace} />
    </div>
  );
}
