'use client';

import type { UiDisplayMode, UiReasonCategory } from '@/lib/ui-capability/contracts';
import { displayModeLabel, reasonCopy } from '@/lib/ui-capability/reasons';

export function DecisionReason({
  reason,
  displayMode,
  compact = false,
}: Readonly<{ reason?: UiReasonCategory; displayMode?: UiDisplayMode; compact?: boolean }>) {
  const copy = reasonCopy(reason);
  if (compact) {
    return (
      <span className="text-xs text-slate-600" role="status">
        {displayMode ? `${displayModeLabel(displayMode)}: ` : ''}{copy.message}
      </span>
    );
  }
  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950" role="status">
      <div className="font-bold">{copy.title}</div>
      <p className="mt-1">{copy.message}</p>
      {copy.action ? <p className="mt-1 font-semibold">{copy.action}</p> : null}
    </div>
  );
}
