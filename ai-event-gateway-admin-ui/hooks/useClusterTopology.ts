'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { mockClusterTopology } from '@/lib/mock/admin';
import type { ClusterTopology } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useClusterTopology() {
  const loader = useCallback(async (): Promise<ClusterTopology> => {
    const env = getPublicEnv();
    return env.useMock ? mockClusterTopology : adminApi.getClusterTopology();
  }, []);

  return usePollingResource<ClusterTopology>(loader);
}
