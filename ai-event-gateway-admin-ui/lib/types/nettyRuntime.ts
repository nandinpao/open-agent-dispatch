export type NettyAuthorizationState = 'UNVERIFIED' | 'AUTHORIZED' | 'DENIED' | 'REVOKED' | 'DISCONNECTED_BY_POLICY' | string;
export type NettyTransportType = 'WEBSOCKET' | 'TCP' | 'HTTP_CALLBACK' | string;

export interface NettyAgentRuntime {
  agentId: string;
  connected: boolean;
  authorizationState?: NettyAuthorizationState;
  agentStatus?: string;
  heartbeatStatus?: string;
  gatewayNodeId?: string;
  nodeId?: string;
  sessionId?: string;
  connectionId?: string;
  transport?: NettyTransportType;
  remoteAddress?: string;
  connectedAt?: string;
  lastHeartbeatAt?: string;
  lastSeenAt?: string;
  latencyMs?: number;
  activeTaskCount?: number;
  maxConcurrentTasks?: number;
  availableSlots?: number;
  capacityUtilization?: number;
  outboxPending?: number;
  outboxInFlight?: number;
  recoveryPendingAssignments?: number;
  draining?: boolean;
  capabilityRevision?: string;
  pluginName?: string;
  pluginVersion?: string;
  inflightCommandCount?: number;
  currentTaskId?: string;
  payload?: unknown;
}

export interface NettyRejectedConnection {
  id?: string;
  eventId?: string;
  claimedAgentId?: string;
  agentId?: string;
  gatewayNodeId?: string;
  connectionId?: string;
  sessionId?: string;
  remoteAddress?: string;
  reason?: string;
  authorizationState?: NettyAuthorizationState;
  agentStatus?: string;
  heartbeatStatus?: string;
  firstSeenAt?: string;
  lastSeenAt?: string;
  occurredAt?: string;
  payload?: unknown;
}

export interface NettyDeliveryRuntime {
  generatedAt?: string;
  inFlightCount?: number;
  successCount?: number;
  failedCount?: number;
  averageLatencyMs?: number;
  recentDeliveries?: unknown[];
  [key: string]: unknown;
}

export interface NettyCallbackRelayRuntime {
  generatedAt?: string;
  successCount?: number;
  failedCount?: number;
  averageLatencyMs?: number;
  recentAttempts?: unknown[];
  [key: string]: unknown;
}

export interface NettyRuntimeSloSnapshot {
  status?: string;
  deliveryBacklog?: Record<string, unknown>;
  gatewayRelayBacklog?: Record<string, unknown>;
  callbackRelay?: Record<string, unknown>;
  alerts?: unknown[];
  generatedAt?: string;
  [key: string]: unknown;
}

export interface NettyRuntimeSnapshot {
  generatedAt?: string;
  localNodeId?: string;
  gatewayNodeId?: string;
  agents?: NettyAgentRuntime[];
  rejectedConnections?: NettyRejectedConnection[];
  delivery?: NettyDeliveryRuntime;
  callbackRelay?: NettyCallbackRelayRuntime;
  inbound?: Record<string, unknown>;
  cluster?: Record<string, unknown>;
  metrics?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface NettyRuntimeEvent {
  eventId?: string;
  eventType: string;
  gatewayNodeId?: string;
  nodeId?: string;
  agentId?: string;
  taskId?: string;
  traceId?: string;
  occurredAt?: string;
  timestamp?: string;
  sequence?: number;
  payload?: unknown;
}
