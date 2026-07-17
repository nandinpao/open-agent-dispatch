'use client';

import type { CoreDispatchUserFacingError } from '@/lib/types/core';
import { parseDispatchUserFacingError } from '@/lib/dispatch-readiness/dispatchUserFacingError';
import { buildDispatchOperatorActions, type DispatchOperatorActionContext, type DispatchOperatorCommand, type DispatchOperatorAction } from '@/lib/dispatch-readiness/dispatchOperatorActions';
import { DispatchOperatorActions } from '@/components/common/DispatchOperatorActions';

export function DispatchUserFacingReason({
  value,
  error,
  fallback = '目前沒有明確阻擋原因。',
  technicalLabel = '工程診斷細節',
  codeClassName = 'inline-flex rounded-full bg-slate-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white',
  detailsClassName = 'rounded-xl border border-white/70 bg-white/60 px-3 py-2 text-xs font-semibold opacity-80',
  technicalClassName = 'mt-2 break-words whitespace-pre-wrap font-mono leading-5',
  showOperatorActions = false,
  actionContext,
  actionCompact = true,
  onOperatorCommand,
}: Readonly<{
  value?: string | null;
  error?: CoreDispatchUserFacingError | null;
  fallback?: string;
  technicalLabel?: string;
  codeClassName?: string;
  detailsClassName?: string;
  technicalClassName?: string;
  showOperatorActions?: boolean;
  actionContext?: DispatchOperatorActionContext;
  actionCompact?: boolean;
  onOperatorCommand?: (command: DispatchOperatorCommand, action: DispatchOperatorAction) => void;
}>) {
  const parsed = parseDispatchUserFacingError(value, error);
  const actions = showOperatorActions
    ? buildDispatchOperatorActions(parsed, {
        ...actionContext,
        runbookRef: actionContext?.runbookRef ?? parsed.runbookRef,
      })
    : [];
  return (
    <div className="space-y-2">
      {parsed.code ? <span className={codeClassName}>{parsed.code}</span> : null}
      <div>{parsed.message || fallback}</div>
      {parsed.nextAction ? <div><span className="font-black opacity-70">下一步：</span>{parsed.nextAction}</div> : null}
      {actions.length > 0 ? (
        <div className="rounded-xl border border-slate-100 bg-white/60 p-2">
          <div className="mb-2 text-[11px] font-black uppercase tracking-wide opacity-60">Operator actions</div>
          <DispatchOperatorActions actions={actions} compact={actionCompact} onCommand={onOperatorCommand} />
        </div>
      ) : null}
      {parsed.technicalDetails ? (
        <details className={detailsClassName}>
          <summary className="cursor-pointer">{technicalLabel}</summary>
          <div className={technicalClassName}>{parsed.technicalDetails}</div>
        </details>
      ) : null}
    </div>
  );
}
