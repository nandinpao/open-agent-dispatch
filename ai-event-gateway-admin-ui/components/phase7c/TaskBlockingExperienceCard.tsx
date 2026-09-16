'use client';

import Link from 'next/link';
import type { CoreTaskRuntimeView } from '@/lib/types/domains/task';
import { normalizeTaskBlockingReason } from '@/lib/phase7c/taskA2aUx';

export function TaskBlockingExperienceCard({ task, compact = false }: Readonly<{ task: CoreTaskRuntimeView; compact?: boolean }>) {
  const experience = normalizeTaskBlockingReason({
    blockedReason: task.blockedReason,
    blockerCode: task.blockerCode,
    blockerReason: task.blockerReason,
    dispatchWaitReason: task.dispatchWaitReason,
    failureReason: task.failureReason,
    reasonCategory: task.reasonCategory,
    status: task.status,
    dispatchStatus: task.dispatchStatus,
  });
  if (!experience) {
    return (
      <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4" aria-label="Task blocking reason">
        <p className="text-xs font-black uppercase tracking-[0.16em] text-emerald-700">Blocking reason</p>
        <h3 className="mt-1 font-black text-emerald-950">No active blocker</h3>
        {!compact ? <p className="mt-2 text-sm text-emerald-900">The current Task evidence does not report a blocking condition.</p> : null}
      </section>
    );
  }
  return (
    <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4" aria-label="Task blocking reason" data-blocking-reason={experience.code}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em] text-amber-700">{experience.code.replaceAll('_', ' ')}</p>
          <h3 className="mt-1 text-base font-black text-amber-950">{experience.title}</h3>
        </div>
        <span className="rounded-full border border-amber-300 bg-white px-2.5 py-1 text-xs font-black text-amber-800">
          {experience.userCanFix ? 'Operator can act' : 'Requires another authority'}
        </span>
      </div>
      {!compact ? <p className="mt-3 text-sm leading-6 text-amber-950">{experience.explanation}</p> : null}
      <div className="mt-3 rounded-xl border border-amber-200 bg-white/80 px-3 py-2 text-sm text-amber-950">
        <span className="font-black">Safest next action:</span> {experience.recommendedAction}
      </div>
      {experience.settingsHref ? (
        <Link href={experience.settingsHref} className="mt-3 inline-flex rounded-lg border border-amber-300 bg-white px-3 py-2 text-xs font-black text-amber-900 hover:bg-amber-100">
          Open related workspace
        </Link>
      ) : null}
    </section>
  );
}
