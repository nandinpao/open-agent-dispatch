'use client';

import type { ReactNode } from 'react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import type { UiEntitlementResponse } from '@/lib/navigation/uiEntitlements';

interface EntitlementState {
  loading: boolean;
  value?: UiEntitlementResponse;
  error?: string;
}
interface EntitlementContextValue extends EntitlementState { refresh: () => void; }
const UiEntitlementContext = createContext<EntitlementContextValue | null>(null);

async function entitlementError(response: Response): Promise<Error> {
  let body: { message?: string } = {};
  try { body = await response.json() as { message?: string }; } catch { /* safe fallback */ }
  if (body.message?.trim()) return new Error(body.message);
  if (response.status === 401) return new Error('Sign in again to load your workspace access.');
  if (response.status === 403) return new Error('Your current account cannot open this workspace.');
  return new Error('Workspace access is temporarily unavailable.');
}

export function UiEntitlementProvider({ children }: Readonly<{ children: ReactNode }>) {
  const { selectedTenantId, user } = useAuth();
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<EntitlementState>({ loading: true });
  const refresh = useCallback(() => setRevision(value => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setState({ loading: true });
    void fetch('/api/ui/entitlements', {
      method: 'GET', credentials: 'same-origin', cache: 'no-store',
      headers: { Accept: 'application/json' }, signal: controller.signal,
    }).then(async response => {
      if (!response.ok) throw await entitlementError(response);
      return response.json() as Promise<UiEntitlementResponse>;
    }).then(value => {
      if (!controller.signal.aborted) setState({ loading: false, value });
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setState({ loading: false, error: error instanceof Error ? error.message : 'Workspace access is unavailable.' });
    });
    return () => controller.abort();
  }, [revision, selectedTenantId, user?.credentialVersion]);

  const value = useMemo<EntitlementContextValue>(() => ({ ...state, refresh }), [state, refresh]);
  return <UiEntitlementContext.Provider value={value}>{children}</UiEntitlementContext.Provider>;
}

export function useUiEntitlements(): EntitlementContextValue {
  const value = useContext(UiEntitlementContext);
  if (!value) throw new Error('useUiEntitlements must be used inside UiEntitlementProvider');
  return value;
}
