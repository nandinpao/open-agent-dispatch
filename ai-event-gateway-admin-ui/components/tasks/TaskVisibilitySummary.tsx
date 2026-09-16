'use client';

import { VisibilityBadge } from '@/components/ui-capability/VisibilityBadge';
import { useUiCapability } from '@/components/ui-capability/UiPageBootstrapProvider';
import { TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS } from '@/lib/ui-capability/taskActions';

export function TaskVisibilitySummary() {
  const decision = useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.view);
  if (!decision.capability || decision.contextStatus !== 'READY') return null;
  return (
    <div className="flex items-center gap-2 text-xs text-slate-600">
      <span className="font-semibold">Visibility</span>
      <VisibilityBadge level={decision.capability.visibilityCeiling} />
    </div>
  );
}
