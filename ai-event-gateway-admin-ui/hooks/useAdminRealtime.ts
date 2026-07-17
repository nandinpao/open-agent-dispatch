'use client';

import { createContext, useContext } from 'react';
import type { AdminRealtimeContextValue } from '@/lib/types/realtime';

export const AdminRealtimeContext = createContext<AdminRealtimeContextValue | null>(null);

export function useAdminRealtime(): AdminRealtimeContextValue {
  const context = useContext(AdminRealtimeContext);
  if (!context) {
    throw new Error('useAdminRealtime must be used inside AdminRealtimeProvider.');
  }
  return context;
}
