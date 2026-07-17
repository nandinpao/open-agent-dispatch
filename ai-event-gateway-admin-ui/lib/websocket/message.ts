import type { AdminEventType, AdminWebSocketEvent } from '@/lib/types/admin';

export type ParsedAdminWebSocketMessage =
  | { kind: 'event'; event: AdminWebSocketEvent }
  | { kind: 'pong'; timestamp: string }
  | { kind: 'control'; controlType: string; timestamp: string; message?: string; raw: unknown; shouldReplyPong?: boolean }
  | { kind: 'unknown'; raw: unknown; reason: string; timestamp: string };

const allowedEventTypes = new Set<AdminEventType>([
  'NODE_JOINED',
  'NODE_LEFT',
  'NODE_HEARTBEAT_TIMEOUT',
  'NODE_DRAINING',
  'NODE_DRAINED',
  'NODE_RESUMED',
  'NODE_METRICS_UPDATED',
  'CLUSTER_METRICS_UPDATED',
  'GATEWAY_METRICS_UPDATED',
  'METRICS_UPDATED',
  'AGENT_CONNECTED',
  'AGENT_AUTHORIZED',
  'AGENT_AUTHORIZATION_DENIED',
  'AGENT_DISCONNECTED',
  'AGENT_STATUS_CHANGED',
  'DELIVERY_STARTED',
  'DELIVERY_SUCCEEDED',
  'DELIVERY_FAILED',
  'CALLBACK_RECEIVED',
  'CALLBACK_RELAYED',
  'CALLBACK_RELAY_FAILED',
  'SECURITY_CONNECTION_REJECTED',
  'SECURITY_CREDENTIAL_REVOKED_ATTEMPT',
  'TASK_CREATED',
  'TASK_ASSIGNED',
  'TASK_COMPLETED',
  'TASK_FAILED',
  'TASK_CANCELLED',
  'SYSTEM_MESSAGE'
]);

const eventTypeAliases: Record<string, AdminEventType> = {
  CLUSTER_NODE_JOINED: 'NODE_JOINED',
  CLUSTER_NODE_ONLINE: 'NODE_JOINED',
  NODE_ONLINE: 'NODE_JOINED',
  NODE_UP: 'NODE_JOINED',

  CLUSTER_NODE_LEFT: 'NODE_LEFT',
  CLUSTER_NODE_OFFLINE: 'NODE_LEFT',
  NODE_OFFLINE: 'NODE_LEFT',
  NODE_DOWN: 'NODE_LEFT',
  NODE_DISCONNECTED: 'NODE_LEFT',

  NODE_TIMEOUT: 'NODE_HEARTBEAT_TIMEOUT',
  NODE_HEARTBEAT_MISSED: 'NODE_HEARTBEAT_TIMEOUT',
  NODE_HEARTBEAT_FAILED: 'NODE_HEARTBEAT_TIMEOUT',

  CLUSTER_NODE_DRAINING: 'NODE_DRAINING',
  CLUSTER_NODE_DRAINED: 'NODE_DRAINED',
  CLUSTER_NODE_RESUMED: 'NODE_RESUMED',
  NODE_RESUME: 'NODE_RESUMED',

  NODE_METRICS: 'NODE_METRICS_UPDATED',
  NODE_METRICS_UPDATE: 'NODE_METRICS_UPDATED',
  NODE_STATS: 'NODE_METRICS_UPDATED',
  NODE_STATUS_METRICS: 'NODE_METRICS_UPDATED',
  CLUSTER_NODE_METRICS: 'NODE_METRICS_UPDATED',
  CLUSTER_NODE_METRICS_UPDATED: 'NODE_METRICS_UPDATED',

  CLUSTER_METRICS: 'CLUSTER_METRICS_UPDATED',
  CLUSTER_STATS: 'CLUSTER_METRICS_UPDATED',
  CLUSTER_METRICS_UPDATE: 'CLUSTER_METRICS_UPDATED',

  GATEWAY_METRICS: 'GATEWAY_METRICS_UPDATED',
  GATEWAY_STATS: 'GATEWAY_METRICS_UPDATED',
  GATEWAY_METRICS_UPDATE: 'GATEWAY_METRICS_UPDATED',

  METRICS: 'METRICS_UPDATED',
  METRIC: 'METRICS_UPDATED',
  STATS: 'METRICS_UPDATED',
  RUNTIME_METRICS: 'METRICS_UPDATED',
  RUNTIME_STATS: 'METRICS_UPDATED',

  AGENT_REGISTERED: 'AGENT_CONNECTED',
  AGENT_ONLINE: 'AGENT_CONNECTED',
  AGENT_READY: 'AGENT_CONNECTED',
  AGENT_UP: 'AGENT_CONNECTED',

  AGENT_AUTHORIZED: 'AGENT_AUTHORIZED',
  AGENT_AUTHORIZATION_ALLOWED: 'AGENT_AUTHORIZED',
  AGENT_CONNECTION_AUTHORIZED: 'AGENT_AUTHORIZED',

  AGENT_AUTHORIZATION_DENIED: 'AGENT_AUTHORIZATION_DENIED',
  AGENT_DENIED: 'AGENT_AUTHORIZATION_DENIED',
  AUTHORIZATION_DENIED: 'AGENT_AUTHORIZATION_DENIED',
  AGENT_CONNECTION_DENIED: 'AGENT_AUTHORIZATION_DENIED',

  AGENT_UNREGISTERED: 'AGENT_DISCONNECTED',
  AGENT_OFFLINE: 'AGENT_DISCONNECTED',
  AGENT_DOWN: 'AGENT_DISCONNECTED',
  AGENT_LOST: 'AGENT_DISCONNECTED',

  AGENT_STATUS: 'AGENT_STATUS_CHANGED',
  AGENT_STATE_CHANGED: 'AGENT_STATUS_CHANGED',
  AGENT_STATE_UPDATED: 'AGENT_STATUS_CHANGED',
  AGENT_UPDATED: 'AGENT_STATUS_CHANGED',

  DELIVERY_STARTED: 'DELIVERY_STARTED',
  COMMAND_DELIVERY_STARTED: 'DELIVERY_STARTED',
  DISPATCH_DELIVERY_STARTED: 'DELIVERY_STARTED',

  DELIVERY_SUCCEEDED: 'DELIVERY_SUCCEEDED',
  DELIVERY_SUCCESS: 'DELIVERY_SUCCEEDED',
  COMMAND_DELIVERY_SUCCEEDED: 'DELIVERY_SUCCEEDED',
  COMMAND_DELIVERED: 'DELIVERY_SUCCEEDED',

  DELIVERY_FAILED: 'DELIVERY_FAILED',
  COMMAND_DELIVERY_FAILED: 'DELIVERY_FAILED',
  DISPATCH_DELIVERY_FAILED: 'DELIVERY_FAILED',

  CALLBACK_RECEIVED: 'CALLBACK_RECEIVED',
  TASK_CALLBACK_RECEIVED: 'CALLBACK_RECEIVED',

  CALLBACK_RELAYED: 'CALLBACK_RELAYED',
  CALLBACK_RELAY_SUCCEEDED: 'CALLBACK_RELAYED',
  TASK_CALLBACK_RELAYED: 'CALLBACK_RELAYED',

  CALLBACK_RELAY_FAILED: 'CALLBACK_RELAY_FAILED',
  TASK_CALLBACK_RELAY_FAILED: 'CALLBACK_RELAY_FAILED',

  SECURITY_CONNECTION_REJECTED: 'SECURITY_CONNECTION_REJECTED',
  CONNECTION_REJECTED: 'SECURITY_CONNECTION_REJECTED',
  AGENT_CONNECTION_REJECTED: 'SECURITY_CONNECTION_REJECTED',

  SECURITY_CREDENTIAL_REVOKED_ATTEMPT: 'SECURITY_CREDENTIAL_REVOKED_ATTEMPT',
  CREDENTIAL_REVOKED_ATTEMPT: 'SECURITY_CREDENTIAL_REVOKED_ATTEMPT',
  REVOKED_CREDENTIAL_ATTEMPT: 'SECURITY_CREDENTIAL_REVOKED_ATTEMPT',

  TASK_NEW: 'TASK_CREATED',
  TASK_QUEUED: 'TASK_CREATED',
  TASK_SUBMITTED: 'TASK_CREATED',

  TASK_DISPATCHED: 'TASK_ASSIGNED',
  TASK_ROUTED: 'TASK_ASSIGNED',

  TASK_DONE: 'TASK_COMPLETED',
  TASK_SUCCESS: 'TASK_COMPLETED',
  TASK_SUCCEEDED: 'TASK_COMPLETED',
  TASK_FINISHED: 'TASK_COMPLETED',

  TASK_ERROR: 'TASK_FAILED',
  TASK_TIMEOUT: 'TASK_FAILED',
  TASK_CANCELLED: 'TASK_CANCELLED',
  TASK_CANCELED: 'TASK_CANCELLED',

  INFO: 'SYSTEM_MESSAGE',
  NOTICE: 'SYSTEM_MESSAGE',
  SYSTEM: 'SYSTEM_MESSAGE',
  SYSTEM_EVENT: 'SYSTEM_MESSAGE',
  SYSTEM_MESSAGE: 'SYSTEM_MESSAGE'
};

const controlTypes = new Set([
  'PING',
  'PONG',
  'HEARTBEAT',
  'HEARTBEAT_ACK',
  'ACK',
  'CONNECTED',
  'CONNECTION_ESTABLISHED',
  'WELCOME',
  'READY',
  'SUBSCRIBED',
  'SUBSCRIBE_ACK',
  'AUTH',
  'AUTH_OK',
  'AUTH_SUCCESS',
  'AUTHENTICATED',
  'AUTH_FAILED',
  'ERROR'
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function numberOrStringValue(value: unknown): string | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return stringValue(value);
}

function normalizeToken(value: unknown): string | undefined {
  const raw = stringValue(value);
  if (!raw) return undefined;

  return raw
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/_{2,}/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase();
}

function normalizeEventType(value: unknown): AdminEventType | undefined {
  const raw = normalizeToken(value);
  if (!raw) return undefined;
  if (allowedEventTypes.has(raw as AdminEventType)) return raw as AdminEventType;
  return eventTypeAliases[raw];
}

function extractEnvelope(raw: unknown): unknown {
  if (!isRecord(raw)) return raw;

  // If the root object already carries the event/control type, keep it as the message.
  // Example: { eventType: 'TASK_COMPLETED', data: {...} }
  if (normalizeToken(readEventTypeCandidate(raw)) !== undefined) return raw;

  // Supports API-like envelopes accidentally pushed through WebSocket:
  // { success: true, data: { eventType: '...' } }
  if (isRecord(raw.data)) return raw.data;

  // Supports common nested event shapes:
  // { event: { eventType: '...' } } / { message: { type: '...' } }
  if (isRecord(raw.event)) return raw.event;
  if (isRecord(raw.message)) return raw.message;

  return raw;
}

function readEventTypeCandidate(raw: Record<string, unknown>): unknown {
  return raw.eventType
    ?? raw.type
    ?? raw.messageType
    ?? raw.event
    ?? raw.name
    ?? raw.action
    ?? raw.kind;
}

function readTimestamp(raw: Record<string, unknown> | null): string {
  if (!raw) return new Date().toISOString();
  return stringValue(raw.timestamp)
    ?? stringValue(raw.time)
    ?? stringValue(raw.occurredAt)
    ?? stringValue(raw.createdAt)
    ?? new Date().toISOString();
}

function readMessage(raw: Record<string, unknown>): string | undefined {
  return stringValue(raw.message)
    ?? stringValue(raw.msg)
    ?? stringValue(raw.reason)
    ?? stringValue(raw.error)
    ?? stringValue(raw.description);
}

function readPayload(raw: Record<string, unknown>): unknown {
  if (raw.payload !== undefined) return raw.payload;
  if (raw.data !== undefined && !isRecord(raw.data)) return raw.data;
  return raw;
}

function toRecordPayload(raw: Record<string, unknown>, payload: unknown): Record<string, unknown> {
  return isRecord(payload) ? payload : raw;
}

export function parseAdminWebSocketMessage(data: string): ParsedAdminWebSocketMessage {
  const now = new Date().toISOString();
  const trimmed = data.trim();
  let raw: unknown;

  try {
    raw = JSON.parse(trimmed);
  } catch {
    const controlType = normalizeToken(trimmed);
    if (controlType === 'PONG' || controlType === 'HEARTBEAT_ACK') {
      return { kind: 'pong', timestamp: now };
    }
    if (controlType && controlTypes.has(controlType)) {
      return {
        kind: 'control',
        controlType,
        timestamp: now,
        raw: trimmed,
        shouldReplyPong: controlType === 'PING' || controlType === 'HEARTBEAT'
      };
    }
    return { kind: 'unknown', raw: data, reason: 'Message is not valid JSON and is not a supported control frame.', timestamp: now };
  }

  const unwrapped = extractEnvelope(raw);

  if (!isRecord(unwrapped)) {
    return { kind: 'unknown', raw, reason: 'Message root is not an object.', timestamp: now };
  }

  const timestamp = readTimestamp(unwrapped);
  const controlType = normalizeToken(readEventTypeCandidate(unwrapped));

  if (controlType === 'PONG' || controlType === 'HEARTBEAT_ACK') {
    return { kind: 'pong', timestamp };
  }

  if (controlType && controlTypes.has(controlType)) {
    return {
      kind: 'control',
      controlType,
      timestamp,
      message: readMessage(unwrapped),
      raw,
      shouldReplyPong: controlType === 'PING' || controlType === 'HEARTBEAT'
    };
  }

  const eventType = normalizeEventType(readEventTypeCandidate(unwrapped));
  if (!eventType) {
    return {
      kind: 'unknown',
      raw,
      reason: `eventType is missing or unsupported. Candidate=${controlType ?? '-'}`,
      timestamp
    };
  }

  const payload = readPayload(unwrapped);
  const payloadRecord = toRecordPayload(unwrapped, payload);

  return {
    kind: 'event',
    event: {
      eventType,
      timestamp,
      nodeId: numberOrStringValue(unwrapped.nodeId ?? unwrapped.gatewayNodeId ?? unwrapped.node ?? payloadRecord.nodeId),
      agentId: numberOrStringValue(unwrapped.agentId ?? unwrapped.agent ?? payloadRecord.agentId),
      taskId: numberOrStringValue(unwrapped.taskId ?? unwrapped.task ?? payloadRecord.taskId),
      traceId: numberOrStringValue(unwrapped.traceId ?? unwrapped.trace ?? payloadRecord.traceId),
      status: stringValue(unwrapped.status ?? unwrapped.state ?? payloadRecord.status),
      message: readMessage(unwrapped),
      payload
    }
  };
}
