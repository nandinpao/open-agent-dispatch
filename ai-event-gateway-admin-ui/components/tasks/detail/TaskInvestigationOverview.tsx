'use client';

import Link from 'next/link';
import { KeyValue } from '@/components/tasks/detail/TaskDetailPrimitives';
import type { CoreTaskRuntimeView } from '@/lib/types/domains/task';
import type { TaskDiagnosisReadModel } from '@/lib/tasks/taskDiagnosisReadModel';
import { formatDateTime } from '@/lib/utils/format';

function stageClasses(status: TaskDiagnosisReadModel['timeline'][number]['status']): string {
  if (status === 'done') return 'border-emerald-200 bg-emerald-50 text-emerald-900';
  if (status === 'failed') return 'border-rose-200 bg-rose-50 text-rose-900';
  if (status === 'blocked') return 'border-amber-200 bg-amber-50 text-amber-900';
  if (status === 'current') return 'border-blue-200 bg-blue-50 text-blue-900';
  return 'border-slate-200 bg-white text-slate-500';
}

function blockerClasses(severity: TaskDiagnosisReadModel['primaryBlocker']['severity']): string {
  if (severity === 'success') return 'border-emerald-200 bg-emerald-50 text-emerald-950';
  if (severity === 'danger') return 'border-rose-200 bg-rose-50 text-rose-950';
  if (severity === 'warning') return 'border-amber-200 bg-amber-50 text-amber-950';
  return 'border-blue-200 bg-blue-50 text-blue-950';
}

function stageLabel(stage: TaskDiagnosisReadModel['timeline'][number]['stage']): string {
  return ({
    EVENT: 'Intake',
    TASK: 'Task created',
    ROUTING: 'Flow matched',
    FLOW: 'Work classified',
    POOL: 'Capability & pool',
    ASSIGNMENT: 'Agent selected',
    DELIVERY: 'Delivered',
    ACK: 'Agent accepted',
    RESULT: 'Agent result',
    RETRY: 'Retry',
    MANUAL_ACTION: 'Operator action',
    ISSUE: 'Issue tracking',
    COMPLETION: 'Completed',
  } satisfies Record<TaskDiagnosisReadModel['timeline'][number]['stage'], string>)[stage];
}

export function TaskInvestigationOverview({ task, model }: Readonly<{ task: CoreTaskRuntimeView; model: TaskDiagnosisReadModel }>) {
  const blocker = model.primaryBlocker;
  const primaryAction = blocker.recommendedActions.find((action) => action.enabled && action.href);
  return (
    <div className="space-y-5">
      <section className={`rounded-3xl border p-5 shadow-sm ${blockerClasses(blocker.severity)}`} aria-label="Task investigation summary">
        <div className="grid gap-5 xl:grid-cols-[1.25fr_0.75fr]">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.16em] opacity-70">What happened?</p>
            <h2 className="mt-1 text-xl font-black">{blocker.userTitle}</h2>
            <p className="mt-2 text-sm font-semibold leading-6">{blocker.userDescription}</p>
            <div className="mt-4 rounded-2xl border border-white/60 bg-white/70 p-4">
              <p className="text-xs font-black uppercase tracking-[0.14em] opacity-65">What should I do next?</p>
              <p className="mt-1 text-sm font-bold leading-6">{blocker.recommendedActions[0]?.description ?? 'Review the execution journey and wait for the next authoritative lifecycle event.'}</p>
              {primaryAction ? (
                <Link href={primaryAction.href!} className="mt-3 inline-flex rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white hover:bg-slate-800">
                  {primaryAction.label}
                </Link>
              ) : null}
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
            <KeyValue label="Where is it now?" value={model.currentStage.replaceAll('_', ' ')} />
            <KeyValue label="Task status" value={task.status ?? 'Unknown'} />
            <KeyValue label="Assigned Agent" value={model.routingEvidence.selectedAgentId ? <Link href={`/agents/${encodeURIComponent(model.routingEvidence.selectedAgentId)}`} className="text-blue-700 hover:underline">{model.routingEvidence.selectedAgentId}</Link> : 'Not assigned'} />
            <KeyValue label="Source / Task Type" value={`${task.sourceSystem ?? task.originSourceSystem ?? 'Unknown source'} · ${task.effectiveTaskTypeCode ?? task.taskTypeCode ?? task.taskType ?? 'Unknown type'}`} />
          </div>
        </div>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm" aria-label="Execution journey">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">Execution journey</p>
          <h2 className="mt-1 text-lg font-black text-slate-950">Where did the Task get to?</h2>
          <p className="mt-1 text-sm text-slate-600">Follow the business journey first. Open Advanced Diagnostics only when you need raw routing, callback, A2A, recovery, or integration evidence.</p>
        </div>
        <ol className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {model.timeline.map((step, index) => (
            <li key={`${step.stage}-${step.occurredAt ?? step.label}`} className={`rounded-2xl border p-3 ${stageClasses(step.status)}`}>
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-black uppercase tracking-wide">{index + 1}. {stageLabel(step.stage)}</span>
                <span className="text-xs font-black uppercase">{step.status}</span>
              </div>
              <p className="mt-1 text-sm font-black">{step.label}</p>
              <p className="mt-1 text-xs font-semibold leading-5 opacity-80">{step.detail}</p>
              {step.occurredAt ? <p className="mt-2 text-xs font-semibold opacity-65">{formatDateTime(step.occurredAt)}</p> : null}
            </li>
          ))}
        </ol>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6" aria-label="Task routing summary">
        <KeyValue label="Flow" value={model.routingEvidence.flowId ? <Link href={`/dispatch-flows?flowId=${encodeURIComponent(model.routingEvidence.flowId)}`} className="text-blue-700 hover:underline">{model.routingEvidence.flowId}</Link> : 'Not matched'} />
        <KeyValue label="Rule" value={model.routingEvidence.ruleId ?? 'Not matched'} />
        <KeyValue label="Pool" value={model.routingEvidence.targetPoolId ?? 'Not determined'} />
        <KeyValue label="Eligible Agents" value={`${model.poolEligibility.eligible} / ${model.poolEligibility.totalMembers}`} />
        <KeyValue label="Delivery" value={model.delivery.latestLedgerDeliveryState ?? model.delivery.dispatchDeliveryStatus ?? model.delivery.dispatchStatus ?? 'Unknown'} />
        <KeyValue label="Result" value={model.ackResult.callbackStatus ?? (model.ackResult.resultKnown ? 'Received' : 'Waiting')} />
      </section>
    </div>
  );
}
