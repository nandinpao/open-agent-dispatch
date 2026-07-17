'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { mockEvents } from '@/lib/mock/admin';
import type { GatewayEventRecord } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useEvents() {
  const loader = useCallback(async (): Promise<GatewayEventRecord[]> => {
    const env = getPublicEnv();
    return env.useMock ? mockEvents : adminApi.getEvents();
  }, []);

  return usePollingResource<GatewayEventRecord[]>(loader);
}
