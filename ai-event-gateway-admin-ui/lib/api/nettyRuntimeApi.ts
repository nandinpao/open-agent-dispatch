import { nettyApiGet, nettyApiPost } from '@/lib/api/nettyClient';
import { nettyRuntimeEndpoints } from '@/lib/api/endpoints';
import type {
  NettyAgentRuntime,
  NettyCallbackRelayRuntime,
  NettyDeliveryRuntime,
  NettyRejectedConnection,
  NettyRuntimeSnapshot,
  NettyRuntimeSloSnapshot
} from '@/lib/types/nettyRuntime';
import type { CommandResult } from '@/lib/types/admin';

type PageLike<T> = T[] | {
  content?: T[];
  items?: T[];
  records?: T[];
  rows?: T[];
  data?: T[];
  agents?: T[];
};

function toList<T>(value: PageLike<T>): T[] {
  if (Array.isArray(value)) return value;
  return value.content ?? value.items ?? value.records ?? value.rows ?? value.data ?? value.agents ?? [];
}

async function nettyGetList<T>(path: string): Promise<T[]> {
  return toList(await nettyApiGet<PageLike<T>>(path));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function pickString(record: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && value !== '') return String(value);
  }
  return undefined;
}

function safeRuntimeAgentId(agent: NettyAgentRuntime): string {
  return String(agent.agentId ?? '');
}

function safeRuntimeNodeId(agent: NettyAgentRuntime): string {
  return String(agent.gatewayNodeId ?? agent.nodeId ?? '');
}

function pickNumber(record: Record<string, unknown>, keys: string[]): number | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string' && value.trim() !== '') {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return undefined;
}


function pickRuntimeLoad(record: Record<string, unknown>): Record<string, unknown> {
  const metadata = isRecord(record.metadata) ? record.metadata : {};
  const payload = isRecord(record.payload) ? record.payload : {};
  const direct = isRecord(record.runtimeLoad) ? record.runtimeLoad : undefined;
  const fromMetadata = isRecord(metadata.runtimeLoad) ? metadata.runtimeLoad : undefined;
  const fromPayload = isRecord(payload.runtimeLoad) ? payload.runtimeLoad : undefined;
  return direct ?? fromMetadata ?? fromPayload ?? {};
}

function pickMetadataText(record: Record<string, unknown>, key: string): string | undefined {
  const metadata = isRecord(record.metadata) ? record.metadata : {};
  const value = record[key] ?? metadata[key];
  return value === undefined || value === null || value === '' ? undefined : String(value);
}

function pickBoolean(record: Record<string, unknown>, keys: string[]): boolean | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'boolean') return value;
    if (typeof value === 'string' && value.trim() !== '') return value.toLowerCase() === 'true';
  }
  return undefined;
}

function normalizeConnected(record: Record<string, unknown>): boolean {
  if (typeof record.connected === 'boolean') return record.connected;
  const transportStatus = pickString(record, ['transportStatus', 'connectionStatus', 'authorizationState']);
  if (transportStatus) return ['CONNECTED', 'AUTHORIZED', 'ONLINE', 'IDLE', 'BUSY', 'BUSY_ACCEPTING', 'DRAINING', 'DEGRADED'].includes(transportStatus.toUpperCase());
  const status = pickString(record, ['reportedStatus', 'status', 'agentStatus']);
  if (status) return ['ONLINE', 'IDLE', 'BUSY', 'BUSY_ACCEPTING', 'DRAINING', 'DEGRADED'].includes(status.toUpperCase());
  return false;
}

function normalizeNettyAgentRuntime(raw: unknown, fallbackNodeId?: string): NettyAgentRuntime | null {
  if (!isRecord(raw)) return null;
  const agentId = pickString(raw, ['agentId', 'id']);
  if (!agentId) return null;
  const transport = pickString(raw, ['transport', 'connectionType']) as NettyAgentRuntime['transport'];
  const runtimeLoad = pickRuntimeLoad(raw);
  return {
    agentId,
    connected: normalizeConnected(raw),
    authorizationState: pickString(raw, ['authorizationState', 'transportStatus']) as NettyAgentRuntime['authorizationState'],
    gatewayNodeId: pickString(raw, ['gatewayNodeId', 'nodeId', 'ownerNodeId']) ?? fallbackNodeId,
    nodeId: pickString(raw, ['nodeId', 'gatewayNodeId', 'ownerNodeId']) ?? fallbackNodeId,
    sessionId: pickString(raw, ['sessionId']),
    connectionId: pickString(raw, ['connectionId']),
    transport,
    remoteAddress: pickString(raw, ['remoteAddress']),
    connectedAt: pickString(raw, ['connectedAt', 'registeredAt']),
    lastHeartbeatAt: pickString(raw, ['lastHeartbeatAt']),
    lastSeenAt: pickString(raw, ['lastSeenAt', 'statusUpdatedAt', 'lastHeartbeatAt']),
    latencyMs: pickNumber(raw, ['latencyMs', 'heartbeatLatencyMs']),
    activeTaskCount: pickNumber(raw, ['activeTaskCount', 'currentTaskCount']) ?? pickNumber(runtimeLoad, ['activeTasks']),
    maxConcurrentTasks: pickNumber(raw, ['maxConcurrentTasks']) ?? pickNumber(runtimeLoad, ['maxConcurrentTasks']),
    availableSlots: pickNumber(raw, ['availableSlots']) ?? pickNumber(runtimeLoad, ['availableSlots']),
    capacityUtilization: pickNumber(raw, ['capacityUtilization']) ?? pickNumber(runtimeLoad, ['capacityUtilization']),
    outboxPending: pickNumber(raw, ['outboxPending']) ?? pickNumber(runtimeLoad, ['outboxPending']),
    outboxInFlight: pickNumber(raw, ['outboxInFlight']) ?? pickNumber(runtimeLoad, ['outboxInFlight']),
    recoveryPendingAssignments: pickNumber(raw, ['recoveryPendingAssignments']) ?? pickNumber(runtimeLoad, ['recoveryPendingAssignments']),
    draining: pickBoolean(raw, ['draining']) ?? pickBoolean(runtimeLoad, ['draining']),
    capabilityRevision: pickMetadataText(raw, 'capabilityRevision'),
    pluginName: pickMetadataText(raw, 'pluginName'),
    pluginVersion: pickMetadataText(raw, 'pluginVersion'),
    inflightCommandCount: pickNumber(raw, ['inflightCommandCount']),
    currentTaskId: pickString(raw, ['currentTaskId']),
    payload: raw
  };
}

function uniqueAgents(agents: NettyAgentRuntime[]): NettyAgentRuntime[] {
  const byKey = new Map<string, NettyAgentRuntime>();
  for (const agent of agents) {
    const key = `${agent.gatewayNodeId ?? agent.nodeId ?? 'unknown'}::${agent.agentId}`;
    byKey.set(key, agent);
  }
  return Array.from(byKey.values()).sort((left, right) => `${safeRuntimeNodeId(left)}:${safeRuntimeAgentId(left)}`.localeCompare(`${safeRuntimeNodeId(right)}:${safeRuntimeAgentId(right)}`));
}

function flattenClusterAgents(raw: unknown): NettyAgentRuntime[] {
  if (!isRecord(raw)) return [];
  const agents: NettyAgentRuntime[] = [];
  const nodes = Array.isArray(raw.nodes) ? raw.nodes : [];
  for (const node of nodes) {
    if (!isRecord(node)) continue;
    const nodeId = pickString(node, ['nodeId']);
    const nodeAgents = Array.isArray(node.agents) ? node.agents : [];
    for (const agent of nodeAgents) {
      const normalized = normalizeNettyAgentRuntime(agent, nodeId);
      if (normalized) agents.push(normalized);
    }
  }
  if (agents.length > 0) return uniqueAgents(agents);

  const byNode = isRecord(raw.agentsByNode) ? raw.agentsByNode : {};
  for (const [nodeId, nodeAgents] of Object.entries(byNode)) {
    if (!Array.isArray(nodeAgents)) continue;
    for (const agent of nodeAgents) {
      const normalized = normalizeNettyAgentRuntime(agent, nodeId);
      if (normalized) agents.push(normalized);
    }
  }
  return uniqueAgents(agents);
}

export const nettyRuntimeApi = {
  getHealth(): Promise<Record<string, unknown>> {
    return nettyApiGet<Record<string, unknown>>(nettyRuntimeEndpoints.health);
  },

  getRuntimeSnapshot(): Promise<NettyRuntimeSnapshot> {
    return nettyApiGet<NettyRuntimeSnapshot>(nettyRuntimeEndpoints.runtimeSnapshot);
  },

  getRuntimeLocal(): Promise<NettyRuntimeSnapshot> {
    return nettyApiGet<NettyRuntimeSnapshot>(nettyRuntimeEndpoints.runtimeLocal);
  },

  async getRuntimeAgents(): Promise<NettyAgentRuntime[]> {
    const rawAgents = await nettyGetList<unknown>(nettyRuntimeEndpoints.runtimeAgents);
    return uniqueAgents(rawAgents.map((agent) => normalizeNettyAgentRuntime(agent)).filter((agent): agent is NettyAgentRuntime => agent !== null));
  },

  async getClusterRuntimeAgents(): Promise<NettyAgentRuntime[]> {
    try {
      const rawAgents = await nettyGetList<unknown>(nettyRuntimeEndpoints.runtimeAgentsCluster);
      const normalized = uniqueAgents(rawAgents.map((agent) => normalizeNettyAgentRuntime(agent)).filter((agent): agent is NettyAgentRuntime => agent !== null));
      if (normalized.length > 0) return normalized;
    } catch {
      // Older Netty builds do not support /api/admin/runtime/agents?scope=cluster.
      // Fall back to the cluster aggregation endpoint below.
    }
    const raw = await nettyApiGet<unknown>(nettyRuntimeEndpoints.clusterAgentsV2);
    return flattenClusterAgents(raw);
  },

  getRejectedConnections(): Promise<NettyRejectedConnection[]> {
    return nettyGetList<NettyRejectedConnection>(nettyRuntimeEndpoints.runtimeRejectedConnections);
  },

  getDeliveryRuntime(): Promise<NettyDeliveryRuntime> {
    return nettyApiGet<NettyDeliveryRuntime>(nettyRuntimeEndpoints.runtimeDelivery);
  },

  getCallbackRelayRuntime(): Promise<NettyCallbackRelayRuntime> {
    return nettyApiGet<NettyCallbackRelayRuntime>(nettyRuntimeEndpoints.runtimeCallbackRelay);
  },

  getRuntimeSlo(): Promise<NettyRuntimeSloSnapshot> {
    return nettyApiGet<NettyRuntimeSloSnapshot>(nettyRuntimeEndpoints.runtimeSlo);
  },

  getInboundRuntime(): Promise<Record<string, unknown>> {
    return nettyApiGet<Record<string, unknown>>(nettyRuntimeEndpoints.runtimeInbound);
  },

  pingAgent(agentId: string): Promise<CommandResult> {
    return nettyApiPost<CommandResult>(nettyRuntimeEndpoints.agentPing(agentId), {});
  },

  disconnectAgent(agentId: string): Promise<CommandResult> {
    return nettyApiPost<CommandResult>(nettyRuntimeEndpoints.agentDisconnect(agentId), {});
  }
} as const;
