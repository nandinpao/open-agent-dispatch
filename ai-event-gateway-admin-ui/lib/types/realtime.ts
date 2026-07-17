import type { AdminWebSocketEvent, ClusterNodeMetrics, GatewayMetrics } from '@/lib/types/admin';

export type WebSocketConnectionStatus =
  | 'MOCK'
  | 'CONNECTING'
  | 'OPEN'
  | 'RECONNECTING'
  | 'CLOSED'
  | 'ERROR'
  | 'DISABLED';

export interface WebSocketConnectionState {
  status: WebSocketConnectionStatus;
  connectedAt: string | null;
  disconnectedAt: string | null;
  lastMessageAt: string | null;
  lastHeartbeatSentAt: string | null;
  lastPongAt: string | null;
  reconnectAttempts: number;
  nextReconnectAt: string | null;
  lastError: string | null;
  lastControlMessageAt: string | null;
  lastControlMessageType: string | null;
  unsupportedMessageCount: number;
  lastUnsupportedMessageAt: string | null;
  lastUnsupportedMessageReason: string | null;
  lastUnsupportedMessageSample: string | null;
  paused: boolean;
}

export interface AdminToastMessage {
  id: string;
  level: 'info' | 'success' | 'warning' | 'error';
  title: string;
  message?: string;
  timestamp: string;
}

export interface AdminRealtimeContextValue {
  events: AdminWebSocketEvent[];
  connection: WebSocketConnectionState;
  toasts: AdminToastMessage[];
  clearEvents: () => void;
  pause: () => void;
  resume: () => void;
  reconnect: () => void;
  dismissToast: (id: string) => void;
  gatewayMetrics: GatewayMetrics | null;
  nodeMetricsById: Record<string, ClusterNodeMetrics>;
  lastMetricsAt: string | null;
  sendJson: (payload: unknown) => boolean;
}

