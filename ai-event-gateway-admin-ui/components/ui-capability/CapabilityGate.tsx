'use client';

import type { ReactNode } from 'react';
import { DecisionReason } from '@/components/ui-capability/DecisionReason';
import { useUiCapability } from '@/components/ui-capability/UiPageBootstrapProvider';

export interface CapabilityGateState {
  enabled: boolean;
  readOnly: boolean;
  loading: boolean;
  displayMode?: import('@/lib/ui-capability/contracts').UiDisplayMode;
  reasonCategory?: import('@/lib/ui-capability/contracts').UiReasonCategory;
}

export function CapabilityGate({
  contextId,
  uiActionId,
  children,
  loadingFallback = null,
  unavailableFallback,
}: Readonly<{
  contextId: string;
  uiActionId: string;
  children: ReactNode | ((state: CapabilityGateState) => ReactNode);
  loadingFallback?: ReactNode;
  unavailableFallback?: ReactNode;
}>) {
  const decision = useUiCapability(contextId, uiActionId);
  const capability = decision.capability;
  const loading = decision.contextStatus === 'HYDRATING' || (!capability && decision.contextStatus === 'READY');
  const state: CapabilityGateState = {
    enabled: decision.contextStatus === 'READY' && capability?.displayMode === 'ENABLED',
    readOnly: decision.contextStatus === 'READY' && capability?.displayMode === 'READ_ONLY',
    loading,
    displayMode: capability?.displayMode,
    reasonCategory: capability?.reasonCategory ?? decision.staleReason,
  };
  if (loading) return loadingFallback;
  if (!capability || capability.displayMode === 'HIDE') return null;
  if (typeof children === 'function') return children(state);
  if (decision.contextStatus !== 'READY') {
    return unavailableFallback ?? <DecisionReason reason={state.reasonCategory} displayMode={state.displayMode} />;
  }
  if (capability.displayMode === 'ENABLED' || capability.displayMode === 'READ_ONLY') return children;
  return unavailableFallback ?? <DecisionReason reason={state.reasonCategory} displayMode={state.displayMode} />;
}
