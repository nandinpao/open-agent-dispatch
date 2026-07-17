'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { getMockEventDetail } from '@/lib/mock/admin';
import type { GatewayEventDetail } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useEventDetail(eventId: string) {
  const loader = useCallback(async (): Promise<GatewayEventDetail> => {
    const env = getPublicEnv();
    return env.useMock ? getMockEventDetail(eventId) : adminApi.getEventDetail(eventId);
  }, [eventId]);

  return usePollingResource<GatewayEventDetail>(loader);
}
