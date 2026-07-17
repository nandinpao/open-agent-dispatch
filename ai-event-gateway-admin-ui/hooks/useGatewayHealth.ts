'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { mockGatewayHealth } from '@/lib/mock/admin';
import type { GatewayHealth } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useGatewayHealth() {
  const loader = useCallback(async (): Promise<GatewayHealth> => {
    const env = getPublicEnv();
    const result = env.useMock ? mockGatewayHealth : await adminApi.getHealth();
    return { ...result, timestamp: result.timestamp ?? new Date().toISOString() };
  }, []);

  return usePollingResource<GatewayHealth>(loader);
}
