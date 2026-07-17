'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { mockGatewayMetrics } from '@/lib/mock/admin';
import type { GatewayMetrics } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useGatewayMetrics() {
  const loader = useCallback(async (): Promise<GatewayMetrics> => {
    const env = getPublicEnv();
    const result = env.useMock ? mockGatewayMetrics : await adminApi.getMetrics();
    return { ...result, timestamp: result.timestamp ?? new Date().toISOString() };
  }, []);

  return usePollingResource<GatewayMetrics>(loader);
}
