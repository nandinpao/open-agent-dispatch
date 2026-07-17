'use client';

import { useCallback } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import type { ApiDiagnosticsReport } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useClusterDiagnostics() {
  const loader = useCallback(async (): Promise<ApiDiagnosticsReport> => adminApi.getApiDiagnostics(), []);
  return usePollingResource<ApiDiagnosticsReport>(loader);
}
