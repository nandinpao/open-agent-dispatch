'use client';

import { useCallback, useEffect, useState } from 'react';
import type { PermissionAwareNavigationResponse } from '@/lib/navigation/permissionAwareNavigation';

interface NavigationState {
  loading: boolean;
  value?: PermissionAwareNavigationResponse;
  error?: string;
}

interface NavigationErrorBody {
  code?: string;
  message?: string;
}

async function navigationError(response: Response): Promise<Error> {
  let body: NavigationErrorBody = {};
  try {
    body = await response.json() as NavigationErrorBody;
  } catch {
    // The status-specific fallback below remains safe when the body is unreadable.
  }

  if (body.message?.trim()) return new Error(body.message);
  if (response.status === 401) return new Error('Sign in again to load navigation.');
  if (response.status === 403) return new Error('Your current account cannot open this administration workspace.');
  if (body.code === 'AUTH_MODE_MISMATCH') {
    return new Error('Admin UI authentication mode does not match the Core runtime configuration.');
  }
  return new Error('Navigation is temporarily unavailable.');
}

export function usePermissionAwareNavigation(): NavigationState & { refresh: () => void } {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<NavigationState>({ loading: true });
  const refresh = useCallback(() => setRevision((value) => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setState({ loading: true });

    void fetch('/api/ui/navigation', {
      method: 'GET',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw await navigationError(response);
        return response.json() as Promise<PermissionAwareNavigationResponse>;
      })
      .then((value) => {
        if (!controller.signal.aborted) setState({ loading: false, value });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({
            loading: false,
            error: error instanceof Error ? error.message : 'Navigation is unavailable.',
          });
        }
      });

    return () => controller.abort();
  }, [revision]);

  return { ...state, refresh };
}
