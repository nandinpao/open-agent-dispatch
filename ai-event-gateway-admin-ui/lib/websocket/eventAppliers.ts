import type {
  AdminWebSocketEvent,
  AgentInfo,
  AgentStatus,
  AgentTransport,
  AgentType,
  ClusterNode,
  ClusterDrainStatus,
  ClusterNodeStatus,
  GatewayTaskRecord,
  GatewayTaskStatus
} from '@/lib/types/admin';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function payloadRecord(event: AdminWebSocketEvent): Record<string, unknown> {
  return isRecord(event.payload) ? event.payload : {};
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

const agentStatuses: AgentStatus[] = ['CONNECTED', 'AUTHORIZED', 'UNVERIFIED', 'DENIED', 'IDLE', 'BUSY', 'OFFLINE', 'ERROR'];
const agentTypes: AgentType[] = ['OPENCLAW', 'HERMES', 'TAO', 'CUSTOM'];
const transports: AgentTransport[] = ['WEBSOCKET', 'TCP', 'HTTP_CALLBACK'];
const taskStatuses: GatewayTaskStatus[] = ['CREATED', 'WAITING_APPROVAL', 'PENDING', 'ASSIGNED', 'DISPATCH_REQUESTED', 'DISPATCHED', 'ACKED', 'PROCESSING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT', 'REASSIGNING'];
const drainStatuses: ClusterDrainStatus[] = ['ACTIVE', 'DRAINING', 'DRAINED', 'MAINTENANCE'];

function normalizeAgentStatus(value: string | undefined, fallback: AgentStatus): AgentStatus {
  const upper = value?.toUpperCase() as AgentStatus | undefined;
  return upper && agentStatuses.includes(upper) ? upper : fallback;
}

function normalizeAgentType(value: unknown): AgentType {
  const upper = stringValue(value)?.toUpperCase() as AgentType | undefined;
  return upper && agentTypes.includes(upper) ? upper : 'CUSTOM';
}

function normalizeTransport(value: unknown): AgentTransport {
  const upper = stringValue(value)?.toUpperCase() as AgentTransport | undefined;
  return upper && transports.includes(upper) ? upper : 'WEBSOCKET';
}

function normalizeTaskStatus(value: string | undefined, fallback: GatewayTaskStatus): GatewayTaskStatus {
  const upper = value?.toUpperCase() as GatewayTaskStatus | undefined;
  return upper && taskStatuses.includes(upper) ? upper : fallback;
}

function sortOldestFirst(events: AdminWebSocketEvent[]): AdminWebSocketEvent[] {
  return [...events].reverse();
}

export function applyAgentRealtimeEvents(baseAgents: AgentInfo[] | null | undefined, events: AdminWebSocketEvent[]): AgentInfo[] | null {
  if (!baseAgents) return baseAgents ?? null;

  const byId = new Map(baseAgents.map((agent) => [agent.agentId, { ...agent }]));

  for (const event of sortOldestFirst(events)) {
    if (!event.agentId) continue;
    if (!event.eventType.startsWith('AGENT_') && !event.eventType.startsWith('TASK_')) continue;

    const payload = payloadRecord(event);
    const existing = byId.get(event.agentId);
    const next: AgentInfo = existing ?? {
      agentId: event.agentId,
      agentType: normalizeAgentType(payload.agentType),
      status: 'CONNECTED',
      nodeId: event.nodeId ?? stringValue(payload.nodeId) ?? '-',
      transport: normalizeTransport(payload.transport),
      connectedAt: event.timestamp,
      lastSeenAt: event.timestamp,
      currentTaskId: undefined,
      errorCount: 0
    };

    next.nodeId = event.nodeId ?? stringValue(payload.nodeId) ?? next.nodeId;
    next.lastSeenAt = event.timestamp;
    next.agentType = normalizeAgentType(payload.agentType ?? next.agentType);
    next.transport = normalizeTransport(payload.transport ?? next.transport);

    if (event.eventType === 'AGENT_CONNECTED' || event.eventType === 'AGENT_AUTHORIZED') {
      next.status = normalizeAgentStatus(event.status, event.eventType === 'AGENT_AUTHORIZED' ? 'AUTHORIZED' : 'CONNECTED');
      next.connectedAt = event.timestamp;
    }

    if (event.eventType === 'AGENT_AUTHORIZATION_DENIED') {
      next.status = 'DENIED';
    }

    if (event.eventType === 'AGENT_DISCONNECTED') {
      next.status = 'OFFLINE';
      next.currentTaskId = undefined;
    }

    if (event.eventType === 'AGENT_STATUS_CHANGED') {
      next.status = normalizeAgentStatus(event.status, next.status);
      next.currentTaskId = event.taskId ?? stringValue(payload.currentTaskId) ?? next.currentTaskId;
    }

    if (event.eventType === 'TASK_ASSIGNED' && event.taskId) {
      next.status = 'BUSY';
      next.currentTaskId = event.taskId;
    }

    if ((event.eventType === 'TASK_COMPLETED' || event.eventType === 'TASK_FAILED' || event.eventType === 'TASK_CANCELLED') && next.currentTaskId === event.taskId) {
      next.status = event.eventType === 'TASK_FAILED' ? 'ERROR' : 'IDLE';
      next.currentTaskId = undefined;
      if (event.eventType === 'TASK_FAILED') next.errorCount += 1;
    }

    byId.set(event.agentId, next);
  }

  return Array.from(byId.values());
}

export function applyClusterRealtimeEvents(baseNodes: ClusterNode[] | null | undefined, events: AdminWebSocketEvent[]): ClusterNode[] | null {
  if (!baseNodes) return baseNodes ?? null;

  const byId = new Map(baseNodes.map((node) => [node.nodeId, { ...node }]));

  for (const event of sortOldestFirst(events)) {
    if (!event.nodeId) continue;

    const payload = payloadRecord(event);
    const existing = byId.get(event.nodeId);
    const next: ClusterNode = existing ?? {
      nodeId: event.nodeId,
      host: stringValue(payload.host) ?? '-',
      tcpPort: numberValue(payload.tcpPort) ?? 0,
      websocketPort: numberValue(payload.websocketPort) ?? 0,
      adminPort: numberValue(payload.adminPort) ?? 0,
      status: 'ONLINE',
      agentCount: numberValue(payload.agentCount) ?? 0,
      lastHeartbeatAt: event.timestamp,
      discoveryMode: stringValue(payload.discoveryMode)?.toUpperCase() === 'STATIC' ? 'STATIC' : stringValue(payload.discoveryMode)?.toUpperCase() === 'DOCKER' ? 'DOCKER' : 'UDP'
    };

    next.lastHeartbeatAt = event.timestamp;
    next.agentCount = numberValue(payload.agentCount) ?? next.agentCount;
    if (stringValue(payload.drainStatus)) {
      const drainCandidate = stringValue(payload.drainStatus)?.toUpperCase() as ClusterDrainStatus | undefined;
      if (drainCandidate && drainStatuses.includes(drainCandidate)) next.drainStatus = drainCandidate;
    }
    if (typeof payload.acceptsNewTasks === 'boolean') next.acceptsNewTasks = payload.acceptsNewTasks;

    if (event.eventType === 'NODE_JOINED') next.status = 'ONLINE';
    if (event.eventType === 'NODE_LEFT' || event.eventType === 'NODE_HEARTBEAT_TIMEOUT') next.status = 'OFFLINE';
    if (event.eventType === 'NODE_DRAINING') {
      next.drainStatus = 'DRAINING';
      next.acceptsNewTasks = false;
    }
    if (event.eventType === 'NODE_DRAINED') {
      next.drainStatus = 'DRAINED';
      next.acceptsNewTasks = false;
    }
    if (event.eventType === 'NODE_RESUMED') {
      next.drainStatus = 'ACTIVE';
      next.acceptsNewTasks = true;
    }

    if (event.status) {
      const candidate = event.status.toUpperCase() as ClusterNodeStatus;
      if (['ONLINE', 'OFFLINE', 'STARTING', 'LEAVING'].includes(candidate)) next.status = candidate;
    }

    byId.set(event.nodeId, next);
  }

  return Array.from(byId.values());
}

export function applyTaskRealtimeEvents(baseTasks: GatewayTaskRecord[] | null | undefined, events: AdminWebSocketEvent[]): GatewayTaskRecord[] | null {
  if (!baseTasks) return baseTasks ?? null;

  const byId = new Map(baseTasks.map((task) => [task.taskId, { ...task }]));

  for (const event of sortOldestFirst(events)) {
    if (!event.taskId || (!event.eventType.startsWith('TASK_') && !event.eventType.startsWith('DELIVERY_') && !event.eventType.startsWith('CALLBACK_'))) continue;
    const payload = payloadRecord(event);

    const existing = byId.get(event.taskId);
    const next: GatewayTaskRecord = existing ?? {
      taskId: event.taskId,
      traceId: event.traceId ?? stringValue(payload.traceId) ?? '-',
      status: 'PENDING',
      assignedAgentId: undefined,
      assignedNodeId: undefined,
      createdAt: event.timestamp,
      retryCount: 0,
      requestPayload: payload.requestPayload,
      responsePayload: payload.responsePayload
    };

    next.traceId = event.traceId ?? stringValue(payload.traceId) ?? next.traceId;

    if (event.eventType === 'TASK_CREATED') {
      next.status = normalizeTaskStatus(event.status, 'PENDING');
      next.createdAt = next.createdAt || event.timestamp;
    }

    if (event.eventType === 'TASK_ASSIGNED' || event.eventType === 'DELIVERY_STARTED' || event.eventType === 'DELIVERY_SUCCEEDED') {
      next.status = normalizeTaskStatus(event.status, event.eventType === 'DELIVERY_STARTED' ? 'DISPATCHED' : 'ASSIGNED');
      next.assignedAgentId = event.agentId ?? stringValue(payload.assignedAgentId) ?? next.assignedAgentId;
      next.assignedNodeId = event.nodeId ?? stringValue(payload.assignedNodeId) ?? next.assignedNodeId;
      next.assignedAt = event.timestamp;
    }

    if (event.eventType === 'TASK_COMPLETED') {
      next.status = normalizeTaskStatus(event.status, 'COMPLETED');
      next.assignedAgentId = event.agentId ?? next.assignedAgentId;
      next.assignedNodeId = event.nodeId ?? next.assignedNodeId;
      next.completedAt = event.timestamp;
      next.durationMs = numberValue(payload.durationMs) ?? next.durationMs;
      next.responsePayload = payload.responsePayload ?? event.payload ?? next.responsePayload;
    }

    if (event.eventType === 'TASK_FAILED' || event.eventType === 'DELIVERY_FAILED' || event.eventType === 'CALLBACK_RELAY_FAILED') {
      next.status = normalizeTaskStatus(event.status, 'FAILED');
      next.assignedAgentId = event.agentId ?? next.assignedAgentId;
      next.assignedNodeId = event.nodeId ?? next.assignedNodeId;
      next.failedAt = event.timestamp;
      next.failureReason = event.message ?? stringValue(payload.failureReason) ?? next.failureReason;
      next.durationMs = numberValue(payload.durationMs) ?? next.durationMs;
      next.retryCount = numberValue(payload.retryCount) ?? next.retryCount;
    }

    if (event.eventType === 'TASK_CANCELLED') {
      next.status = normalizeTaskStatus(event.status, 'CANCELLED');
      next.assignedAgentId = event.agentId ?? next.assignedAgentId;
      next.assignedNodeId = event.nodeId ?? next.assignedNodeId;
      next.completedAt = event.timestamp;
      next.failureReason = event.message ?? stringValue(payload.failureReason) ?? next.failureReason;
    }

    byId.set(event.taskId, next);
  }

  return Array.from(byId.values()).sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
}
