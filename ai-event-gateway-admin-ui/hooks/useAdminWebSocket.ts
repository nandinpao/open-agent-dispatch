'use client';

import { useAdminRealtime } from '@/hooks/useAdminRealtime';

export function useAdminWebSocket() {
  const realtime = useAdminRealtime();
  return {
    events: realtime.events,
    status: realtime.connection.status,
    connection: realtime.connection,
    clearEvents: realtime.clearEvents,
    pause: realtime.pause,
    resume: realtime.resume,
    reconnect: realtime.reconnect,
    gatewayMetrics: realtime.gatewayMetrics,
    nodeMetricsById: realtime.nodeMetricsById,
    lastMetricsAt: realtime.lastMetricsAt,
    sendJson: realtime.sendJson
  };
}
