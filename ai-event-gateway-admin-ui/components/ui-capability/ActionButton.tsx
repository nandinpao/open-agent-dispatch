'use client';

import { useId, type ButtonHTMLAttributes } from 'react';
import { DecisionReason } from '@/components/ui-capability/DecisionReason';
import { useUiCapability } from '@/components/ui-capability/UiPageBootstrapProvider';

export function ActionButton({
  contextId,
  uiActionId,
  children,
  className = '',
  hideReason = false,
  ...buttonProps
}: Readonly<{
  contextId: string;
  uiActionId: string;
  hideReason?: boolean;
} & ButtonHTMLAttributes<HTMLButtonElement>>) {
  const decision = useUiCapability(contextId, uiActionId);
  const reasonId = useId();
  const capability = decision.capability;
  if (capability?.displayMode === 'HIDE') return null;
  const loading = decision.contextStatus === 'HYDRATING' || (!capability && decision.contextStatus === 'READY');
  const enabled = decision.contextStatus === 'READY' && capability?.displayMode === 'ENABLED' && !buttonProps.disabled;
  const reason = capability?.reasonCategory ?? decision.staleReason;
  return (
    <div className="inline-flex flex-col items-start gap-1">
      <button
        {...buttonProps}
        type={buttonProps.type ?? 'button'}
        disabled={!enabled || loading}
        aria-describedby={!enabled && !hideReason ? reasonId : undefined}
        data-ui-action-id={uiActionId}
        data-display-mode={capability?.displayMode ?? (loading ? 'HYDRATING' : 'UNAVAILABLE')}
        className={`rounded-lg px-3 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-55 ${className}`}
      >
        {loading ? 'Checking access…' : children}
      </button>
      {!enabled && !loading && !hideReason ? (
        <span id={reasonId}><DecisionReason reason={reason} displayMode={capability?.displayMode} compact /></span>
      ) : null}
    </div>
  );
}
