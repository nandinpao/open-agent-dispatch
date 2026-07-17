'use client';

import { useMemo } from 'react';
import { useAdminWebSocket } from '@/hooks/useAdminWebSocket';
import { buildRuntimeEventCenterSummary, sortRuntimeEventsByNewest } from '@/lib/realtime/runtimeEventCenter';

export function useRuntimeEventCenter() {
  const realtime = useAdminWebSocket();

  const sortedEvents = useMemo(() => sortRuntimeEventsByNewest(realtime.events), [realtime.events]);
  const summary = useMemo(() => buildRuntimeEventCenterSummary(realtime.events), [realtime.events]);

  return {
    ...realtime,
    events: sortedEvents,
    summary
  };
}
