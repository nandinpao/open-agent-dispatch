import { JsonViewer } from '@/components/common/JsonViewer';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { TraceDetail, TraceStep } from '@/lib/types/admin';
import { formatDateTime, formatDurationMs } from '@/lib/utils/format';

function TraceStepCard({ step, index }: Readonly<{ step: TraceStep; index: number }>) {
  return (
    <div className="relative rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="absolute -left-3 top-5 flex h-6 w-6 items-center justify-center rounded-full border border-slate-200 bg-white text-xs font-bold text-slate-600">
        {index + 1}
      </div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-bold text-slate-900">{step.stage}</h3>
            <StatusBadge status={step.status} />
          </div>
          <div className="mt-1 text-xs text-slate-500">
            {step.actorType}{step.actorId ? `：${step.actorId}` : ''} ・ {formatDateTime(step.timestamp)}
          </div>
        </div>
        <div className="text-xs font-semibold text-slate-500">{formatDurationMs(step.durationMs)}</div>
      </div>
      {step.message ? <p className="mt-3 text-sm text-slate-700">{step.message}</p> : null}
      {step.payload ? <div className="mt-4"><JsonViewer value={step.payload} /></div> : null}
    </div>
  );
}

export function TraceTimeline({ trace }: Readonly<{ trace?: TraceDetail | null }>) {
  const safeTrace: TraceDetail = trace ?? {
    traceId: '-',
    status: 'PROCESSING',
    startedAt: '-',
    steps: []
  };
  const steps = Array.isArray(safeTrace.steps) ? safeTrace.steps : [];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white/60 p-5 shadow-sm">
      <div className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Trace Timeline</h2>
          <p className="mt-1 text-sm text-slate-500">依 traceId 串起 Event → Route → Task → Agent 的完整生命週期。</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={safeTrace.status} />
          <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-600">
            {formatDurationMs(safeTrace.durationMs)}
          </span>
        </div>
      </div>
      <div className="mb-4 grid gap-3 md:grid-cols-4">
        <div className="rounded-xl border border-slate-100 bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Trace ID</div>
          <div className="mt-1 break-all text-sm font-bold text-slate-800">{safeTrace.traceId}</div>
        </div>
        <div className="rounded-xl border border-slate-100 bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Event</div>
          <div className="mt-1 break-all text-sm font-bold text-slate-800">{safeTrace.eventId ?? '-'}</div>
        </div>
        <div className="rounded-xl border border-slate-100 bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Task</div>
          <div className="mt-1 break-all text-sm font-bold text-slate-800">{safeTrace.taskId ?? '-'}</div>
        </div>
        <div className="rounded-xl border border-slate-100 bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Source</div>
          <div className="mt-1 break-all text-sm font-bold text-slate-800">{safeTrace.sourceSystem ?? '-'}</div>
        </div>
      </div>
      <div className="border-l-2 border-slate-200 pl-6">
        <div className="space-y-4">
          {steps.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-white p-4 text-sm text-slate-500">目前沒有 Trace steps。</div>
          ) : steps.map((step, index) => <TraceStepCard key={step.stepId} step={step} index={index} />)}
        </div>
      </div>
    </section>
  );
}
