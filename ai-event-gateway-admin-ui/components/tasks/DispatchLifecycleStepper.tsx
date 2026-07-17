import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import {
  buildDispatchLifecycleSummary,
  lifecycleStateClass,
  lifecycleStateIcon,
  type DispatchLifecycleStep
} from '@/lib/tasks/dispatchLifecycle';

function stepTone(step: DispatchLifecycleStep): string {
  return lifecycleStateClass(step.status);
}

function stepIcon(step: DispatchLifecycleStep): string {
  return lifecycleStateIcon(step.status);
}

function StepReference({ value }: Readonly<{ value: string }>) {
  if (value.startsWith('agent-')) {
    return <Link href={`/agents/${encodeURIComponent(value)}`} className="text-blue-600 hover:text-blue-700">{value}</Link>;
  }
  return <span>{value}</span>;
}

function CompactStep({ step }: Readonly<{ step: DispatchLifecycleStep }>) {
  return (
    <div className="flex min-w-[8.5rem] flex-1 items-center gap-2 rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
      <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-bold ${stepTone(step)}`}>{stepIcon(step)}</span>
      <div className="min-w-0">
        <div className="truncate text-xs font-bold text-slate-700">{step.title}</div>
        <div className="mt-0.5 truncate text-[11px] text-slate-500">{step.badge ?? step.status}</div>
      </div>
    </div>
  );
}

function FullStep({ step, isLast }: Readonly<{ step: DispatchLifecycleStep; isLast: boolean }>) {
  return (
    <div className="relative flex gap-3">
      {!isLast ? <div className="absolute left-[15px] top-8 h-[calc(100%-1.5rem)] w-px bg-slate-200" aria-hidden="true" /> : null}
      <span className={`relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-sm font-bold ${stepTone(step)}`}>{stepIcon(step)}</span>
      <div className="min-w-0 flex-1 pb-5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="font-bold text-slate-900">{step.title}</div>
          <div className="flex flex-wrap gap-2">
            <StatusBadge status={step.badge ?? step.status.toUpperCase()} />
            <StatusBadge status={step.status.toUpperCase()} />
          </div>
        </div>
        {step.description ? <p className="mt-1 break-words text-sm text-slate-600">{step.description}</p> : null}
        <div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
          {step.timestamp ? <span className="rounded-full bg-slate-100 px-2.5 py-1 font-semibold">{step.timestamp}</span> : null}
          {step.references?.map((reference) => (
            <span key={reference} className="rounded-full bg-slate-100 px-2.5 py-1 font-semibold">
              <StepReference value={reference} />
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

export function DispatchLifecycleStepper({
  row,
  variant = 'full'
}: Readonly<{
  row: TaskDispatchDashboardRow;
  variant?: 'compact' | 'full';
}>) {
  const summary = buildDispatchLifecycleSummary(row);

  if (variant === 'compact') {
    return (
      <div className="mt-4 rounded-xl border border-slate-100 bg-white p-3">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="text-xs font-bold uppercase tracking-wide text-slate-400">Lifecycle</div>
            <div className="mt-1 text-sm font-semibold text-slate-800">{summary.headline}</div>
          </div>
          <div className="flex flex-wrap gap-2">
            <StatusBadge status={summary.overallStatus} />
            {summary.nextAction && summary.nextAction !== 'NONE' ? <StatusBadge status={summary.nextAction} /> : null}
          </div>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          {summary.steps.map((step) => <CompactStep key={step.id} step={step} />)}
        </div>
      </div>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Dispatch Lifecycle Detail</h2>
          <p className="mt-1 text-sm text-slate-500">工程師詳細視圖：Event / Incident → Task → Assignment → Dispatch request → Gateway delivery → Agent callback。</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={summary.overallStatus} />
          {summary.nextAction && summary.nextAction !== 'NONE' ? <StatusBadge status={summary.nextAction} /> : null}
        </div>
      </div>
      <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-4">
        <div className="text-sm font-bold text-slate-900">{summary.headline}</div>
        {summary.blockedReason ? <p className="mt-1 text-sm font-semibold text-amber-800">阻擋原因：{summary.blockedReason}</p> : null}
        {summary.nextAction && summary.nextAction !== 'NONE' ? <p className="mt-1 text-sm text-slate-600">下一步：{summary.nextAction}</p> : null}
      </div>
      <div className="mt-5">
        {summary.steps.map((step, index) => <FullStep key={step.id} step={step} isLast={index === summary.steps.length - 1} />)}
      </div>
    </section>
  );
}
