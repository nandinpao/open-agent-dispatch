'use client';

import type { CoreTaskRuntimeView } from '@/lib/types/domains/task';
import { CapabilityGate } from '@/components/ui-capability/CapabilityGate';
import { DecisionReason } from '@/components/ui-capability/DecisionReason';
import { TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS } from '@/lib/ui-capability/taskActions';
import { normalizeTaskBlockingReason } from '@/lib/phase7c/taskA2aUx';

const decisions = [
  { id: 'retry', label: 'Retry the current dispatch path', action: TASK_UI_ACTIONS.retry },
  { id: 'reassign', label: 'Re-evaluate or reassign the Agent', action: TASK_UI_ACTIONS.reassign },
  { id: 'recovery', label: 'Run governed recovery', action: TASK_UI_ACTIONS.runRecovery },
  { id: 'cancel', label: 'Cancel the Task after impact review', action: TASK_UI_ACTIONS.cancel },
] as const;

export function TaskManualReviewWorkspace({ task }: Readonly<{ task: CoreTaskRuntimeView }>) {
  const blocker = normalizeTaskBlockingReason({
    blockedReason: task.blockedReason,
    blockerCode: task.blockerCode,
    blockerReason: task.blockerReason,
    dispatchWaitReason: task.dispatchWaitReason,
    failureReason: task.failureReason,
    reasonCategory: task.reasonCategory,
    status: task.status,
    dispatchStatus: task.dispatchStatus,
  });
  const needsReview = blocker?.code === 'MANUAL_REVIEW_REQUIRED' || blocker?.code === 'RESOURCE_LOCKED' || task.status === 'FAILED';
  if (!needsReview) return null;
  return (
    <section className="rounded-2xl border border-violet-200 bg-violet-50 p-5" aria-label="Task manual review workspace">
      <p className="text-xs font-black uppercase tracking-[0.18em] text-violet-700">Manual review</p>
      <h3 className="mt-1 text-lg font-black text-violet-950">Choose only a governed decision</h3>
      <p className="mt-2 text-sm leading-6 text-violet-900">
        Review the blocking evidence, current resource version, and impact before selecting a decision. Free text cannot directly change the Task state.
      </p>
      <dl className="mt-4 grid gap-3 sm:grid-cols-3">
        <div className="rounded-xl bg-white p-3"><dt className="text-xs font-bold uppercase text-slate-400">Status</dt><dd className="mt-1 font-black text-slate-900">{task.status}</dd></div>
        <div className="rounded-xl bg-white p-3"><dt className="text-xs font-bold uppercase text-slate-400">Dispatch</dt><dd className="mt-1 font-black text-slate-900">{task.dispatchStatus ?? 'Not reported'}</dd></div>
        <div className="rounded-xl bg-white p-3"><dt className="text-xs font-bold uppercase text-slate-400">Evidence</dt><dd className="mt-1 font-black text-slate-900">{blocker?.code ?? 'MANUAL_REVIEW_REQUIRED'}</dd></div>
      </dl>
      <div className="mt-4 grid gap-2 md:grid-cols-2">
        {decisions.map((decision) => (
          <CapabilityGate key={decision.id} contextId={TASK_DETAIL_CONTEXT} uiActionId={decision.action} unavailableFallback={
            <div className="rounded-xl border border-violet-200 bg-white p-3"><p className="text-sm font-black text-slate-700">{decision.label}</p><DecisionReason compact reason="NOT_ALLOWED" /></div>
          }>
            <div className="rounded-xl border border-violet-200 bg-white p-3">
              <p className="text-sm font-black text-violet-950">{decision.label}</p>
              <p className="mt-1 text-xs text-slate-500">Use the Recommended actions section; the Domain API will reauthorize and apply CAS/idempotency.</p>
            </div>
          </CapabilityGate>
        ))}
      </div>
    </section>
  );
}
