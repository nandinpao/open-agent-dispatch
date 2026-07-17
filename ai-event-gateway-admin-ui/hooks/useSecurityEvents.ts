'use client';

import { useCallback } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { AgentSecurityEvent } from '@/lib/types/core';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useSecurityEvents() {
  const loader = useCallback(async (): Promise<AgentSecurityEvent[]> => {
    try {
      return await coreAdminApi.getSecurityEvents();
    } catch {
      return coreAdminApi.getAgentSecurityEvents();
    }
  }, []);

  return usePollingResource<AgentSecurityEvent[]>(loader);
}
