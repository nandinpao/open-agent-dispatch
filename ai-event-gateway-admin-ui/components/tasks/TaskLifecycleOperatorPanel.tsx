import Link from 'next/link';
import { HumanizedCode } from '@/components/common/HumanizedCode';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import { buildDispatchLifecycleSummary, buildOperatorLifecycleStages, lifecycleOperatorDecision, lifecycleStateClass, lifecycleStateIcon } from '@/lib/tasks/dispatchLifecycle';

function connectorClass(status: string): string {
  if (status === 'done') return 'bg-emerald-200';
  if (status === 'failed') return 'bg-rose-200';
  if (status === 'blocked') return 'bg-amber-200';
  if (status === 'current') return 'bg-blue-200';
  return 'bg-slate-200';
}

function referenceNode(value: string) {
  if (value.startsWith('agent-')) return <Link href={`/agents/${encodeURIComponent(value)}`} className="text-blue-700 hover:text-blue-800">{value}</Link>;
  if (value.startsWith('task-')) return <span>{value}</span>;
  return <span>{value}</span>;
}

export function TaskLifecycleOperatorPanel({ row }: Readonly<{ row: TaskDispatchDashboardRow }>) {
  const summary = buildDispatchLifecycleSummary(row);
  const stages = buildOperatorLifecycleStages(summary);
  const decision = lifecycleOperatorDecision(summary);

  return (
    <section className="rounded-2xl border border-blue-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-blue-500">Lifecycle Stepper · Operator View</div>
          <h2 className="mt-1 text-xl font-black text-slate-950">{decision.title}</h2>
          <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">{decision.description}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={summary.overallStatus} />
          {summary.nextAction && summary.nextAction !== 'NONE' ? <HumanizedCode code={summary.nextAction} type="status" compact /> : null}
        </div>
      </div>

      <div className="mt-4 rounded-2xl border border-slate-100 bg-slate-50 p-4">
        <div className="grid gap-3 md:grid-cols-3">
          <div>
            <div className="text-xs font-bold uppercase tracking-wide text-slate-400">目前卡在哪</div>
            <div className="mt-1 text-sm font-black text-slate-900">{decision.currentStage}</div>
          </div>
          <div className="md:col-span-2">
            <div className="text-xs font-bold uppercase tracking-wide text-slate-400">下一步</div>
            <div className="mt-1 text-sm font-semibold text-slate-700">{decision.nextAction}</div>
          </div>
        </div>
      </div>

      <div className="mt-5 overflow-x-auto pb-2">
        <ol className="flex min-w-[900px] items-stretch gap-0">
          {stages.map((stage, index) => (
            <li key={stage.id} className="flex flex-1 items-stretch">
              <div className="flex min-w-0 flex-1 flex-col rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-start gap-3">
                  <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full border text-sm font-black ${lifecycleStateClass(stage.status)}`}>{lifecycleStateIcon(stage.status)}</span>
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-black text-slate-900">{stage.title}</div>
                    <div className="mt-1"><HumanizedCode code={stage.code} type="status" compact /></div>
                  </div>
                </div>
                <p className="mt-3 text-xs leading-5 text-slate-600">{stage.operatorHint}</p>
                {stage.summary && stage.summary !== stage.operatorHint ? <p className="mt-2 text-xs leading-5 text-slate-500">{stage.summary}</p> : null}
                <div className="mt-auto pt-3 text-[11px] text-slate-400">
                  {stage.timestamp ? <div>{stage.timestamp}</div> : null}
                  {stage.references?.length ? (
                    <div className="mt-1 flex flex-wrap gap-1">
                      {stage.references.map((reference) => <span key={reference} className="rounded-full bg-slate-100 px-2 py-0.5 font-semibold text-slate-500">{referenceNode(reference)}</span>)}
                    </div>
                  ) : null}
                </div>
              </div>
              {index < stages.length - 1 ? <div className="flex w-6 items-center"><div className={`h-1 w-full rounded ${connectorClass(stage.status)}`} /></div> : null}
            </li>
          ))}
        </ol>
      </div>

      <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs leading-5 text-slate-500">
        上方是一般操作人員看的流程定位；完整 event、payload、correlation id 與 attempt history 請看下方「Timeline Event Log」。
      </div>
    </section>
  );
}
