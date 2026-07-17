'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { getPublicEnv } from '@/lib/constants/env';

export interface PollingResourceState<T> {
  data: T | null;
  loading: boolean;
  refreshing: boolean;
  error: string | null;
  lastUpdatedAt: string | null;
  refresh: () => Promise<void>;
}

type PollingLoader<T> = (signal?: AbortSignal) => Promise<T>;

export function usePollingResource<T>(loader: PollingLoader<T>, enabled = true): PollingResourceState<T> {
  const env = getPublicEnv();
  const mountedRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const inFlightRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!enabled || inFlightRef.current) return;

    inFlightRef.current = true;
    const controller = new AbortController();
    abortControllerRef.current = controller;

    if (!hasLoadedRef.current) setLoading(true);
    if (hasLoadedRef.current) setRefreshing(true);
    setError(null);

    try {
      const result = await loader(controller.signal);
      if (!mountedRef.current || controller.signal.aborted) return;
      setData(result);
      hasLoadedRef.current = true;
      setLastUpdatedAt(new Date().toISOString());
    } catch (err) {
      if (!mountedRef.current || controller.signal.aborted) return;
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null;
      }
      inFlightRef.current = false;
      if (!mountedRef.current || controller.signal.aborted) return;
      setLoading(false);
      setRefreshing(false);
    }
  }, [enabled, loader]);

  useEffect(() => {
    mountedRef.current = true;
    void refresh();

    const timer = window.setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      void refresh();
    }, env.refreshIntervalMs);

    const onVisible = () => {
      if (document.visibilityState === 'visible') void refresh();
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      mountedRef.current = false;
      abortControllerRef.current?.abort();
      abortControllerRef.current = null;
      inFlightRef.current = false;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [env.refreshIntervalMs, refresh]);

  return { data, loading, refreshing, error, lastUpdatedAt, refresh };
}
