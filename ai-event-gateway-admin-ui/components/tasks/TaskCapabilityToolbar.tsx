'use client';

import Link from 'next/link';
import { ActionButton } from '@/components/ui-capability/ActionButton';
import { TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS } from '@/lib/ui-capability/taskActions';

export function TaskCapabilityToolbar({
  busy,
  recommendedActionLabel,
  onRecommendedAction,
}: Readonly<{
  busy: boolean;
  recommendedActionLabel?: string;
  onRecommendedAction: () => void;
}>) {
  const hasRecommendedAction = Boolean(recommendedActionLabel);
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm" aria-labelledby="task-capability-actions-title">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 id="task-capability-actions-title" className="text-sm font-bold text-slate-950">Task action focus</h2>
          <p className="mt-1 text-xs text-slate-600">Use the recommended governed command first. All other Task mutations are available from the Agent Assignment section and use the same command contract.</p>
        </div>
        <div className="flex flex-wrap items-start gap-2">
          <ActionButton
            contextId={TASK_DETAIL_CONTEXT}
            uiActionId={TASK_UI_ACTIONS.executeRemediation}
            disabled={busy || !hasRecommendedAction}
            hideReason={busy || !hasRecommendedAction}
            onClick={onRecommendedAction}
            className="bg-blue-700 text-white hover:bg-blue-800"
          >
            {recommendedActionLabel ? `Recommended: ${recommendedActionLabel}` : 'No remediation recommended'}
          </ActionButton>
          <Link href="#task-assignment" className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-800 hover:bg-slate-50">
            More Task actions
          </Link>
        </div>
      </div>
    </section>
  );
}
