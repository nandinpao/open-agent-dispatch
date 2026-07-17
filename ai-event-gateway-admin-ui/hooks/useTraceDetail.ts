'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { getMockTraceDetail } from '@/lib/mock/admin';
import type { TraceDetail } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useTraceDetail(traceId: string) {
  const loader = useCallback(async (): Promise<TraceDetail> => {
    const env = getPublicEnv();
    return env.useMock ? getMockTraceDetail(traceId) : adminApi.getTraceDetail(traceId);
  }, [traceId]);

  return usePollingResource<TraceDetail>(loader);
}
