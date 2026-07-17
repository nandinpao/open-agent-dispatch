'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { mockClusterNodes } from '@/lib/mock/admin';
import type { ClusterNodeDetail } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';
import { useRealtimeClusterNodes } from '@/hooks/useRealtimeResources';

export function useClusterNodes() {
  const loader = useCallback(async (): Promise<ClusterNodeDetail[]> => {
    const env = getPublicEnv();
    return env.useMock ? (mockClusterNodes as unknown as ClusterNodeDetail[]) : adminApi.getClusterNodes();
  }, []);

  const resource = usePollingResource<ClusterNodeDetail[]>(loader);
  const realtimeData = useRealtimeClusterNodes(resource.data) as ClusterNodeDetail[] | null;

  return { ...resource, data: realtimeData };
}
