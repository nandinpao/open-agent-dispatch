'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import { ApiError } from '@/lib/api/client';
import type { UiCapability, UiPageBootstrap } from '@/lib/ui-capability/contracts';
import { UiCapabilityHydrator } from '@/lib/ui-capability/hydrator';
import {
  UiCapabilityStore,
  type UiCapabilityContextRecord,
  type UiCapabilityDecisionSnapshot,
} from '@/lib/ui-capability/store';

interface UiCapabilityRuntime {
  bootstrap: UiPageBootstrap;
  store: UiCapabilityStore;
  hydrator: UiCapabilityHydrator;
}

const Context = createContext<UiCapabilityRuntime | null>(null);

export function UiPageBootstrapProvider({
  bootstrap,
  resourceId,
  children,
}: Readonly<{ bootstrap: UiPageBootstrap; resourceId: string; children: ReactNode }>) {
  const [runtime] = useState<UiCapabilityRuntime>(() => {
    const store = new UiCapabilityStore(bootstrap, resourceId);
    return { bootstrap, store, hydrator: new UiCapabilityHydrator(store) };
  });

  useEffect(() => {
    runtime.store.seedBootstrap(bootstrap, resourceId);
  }, [bootstrap, resourceId, runtime.store]);

  useEffect(() => () => runtime.hydrator.dispose(), [runtime.hydrator]);

  return (
    <Context.Provider value={{ ...runtime, bootstrap }}>
      {children}
    </Context.Provider>
  );
}

export function useUiCapabilityRuntime(): UiCapabilityRuntime {
  const value = useContext(Context);
  if (!value) throw new Error('UiPageBootstrapProvider is required');
  return value;
}

export function useUiPageBootstrap(): UiPageBootstrap {
  return useUiCapabilityRuntime().bootstrap;
}

export function useUiCapabilityContext(contextId: string): UiCapabilityContextRecord | undefined {
  const { store } = useUiCapabilityRuntime();
  useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
  return store.getContext(contextId);
}

export function useUiCapability(contextId: string, uiActionId: string): UiCapabilityDecisionSnapshot {
  const { store, hydrator } = useUiCapabilityRuntime();
  useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
  const decision = store.getDecision(contextId, uiActionId);
  useEffect(() => {
    if (decision.needsHydration) hydrator.request(contextId, [uiActionId]);
  }, [contextId, decision.needsHydration, hydrator, uiActionId]);
  return decision;
}

export function useInitialUiCapability(uiActionId: string): UiCapability | undefined {
  const bootstrap = useUiPageBootstrap();
  return bootstrap.pageCapabilities.find((item) => item.uiActionId === uiActionId);
}

export function useCapabilityMutation(contextId: string) {
  const { store, hydrator } = useUiCapabilityRuntime();
  return useCallback(async <T,>(uiActionId: string, operation: () => Promise<T>): Promise<T> => {
    const decision = store.getDecision(contextId, uiActionId);
    if (decision.contextStatus !== 'READY' || decision.capability?.displayMode !== 'ENABLED') {
      throw new ApiError('This action is not currently available.', 403, undefined, 'UI_ACTION_NOT_AVAILABLE');
    }
    try {
      const result = await operation();
      hydrator.refreshContext(contextId, [uiActionId]);
      return result;
    } catch (error) {
      if (error instanceof ApiError) hydrator.invalidateForMutationStatus(contextId, error.status);
      throw error;
    }
  }, [contextId, hydrator, store]);
}

export function useRefreshUiCapabilityContext(contextId: string): () => void {
  const { hydrator } = useUiCapabilityRuntime();
  return useCallback(() => hydrator.refreshContext(contextId), [contextId, hydrator]);
}
