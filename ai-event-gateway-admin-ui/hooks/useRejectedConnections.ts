'use client';

import { useCallback } from 'react';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import type { NettyRejectedConnection } from '@/lib/types/nettyRuntime';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useRejectedConnections() {
  const loader = useCallback((): Promise<NettyRejectedConnection[]> => nettyRuntimeApi.getRejectedConnections(), []);
  return usePollingResource<NettyRejectedConnection[]>(loader);
}
