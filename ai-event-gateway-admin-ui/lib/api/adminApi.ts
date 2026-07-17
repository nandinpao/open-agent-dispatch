import { ApiError, apiGet, apiPost, isNotFoundOrUnsupportedApiError } from '@/lib/api/client';
import { authApi } from '@/lib/api/authApi';
import {
  validateArrayContract,
  validateClusterNodeDetail,
  validateContract,
  validateGatewayEventRecord,
  validateGatewayTaskDetail,
  validateGatewayTaskRecord,
  validateTraceDetail,
  validateAgentInfo
} from '@/lib/api/contracts';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { adminEndpoints } from '@/lib/api/endpoints';
import { getPublicEnv } from '@/lib/constants/env';
import type {
  AgentErrorLog,
  AgentInfo,
  AgentDetail,
  ApiDiagnosticsReport,
  ApiDiagnosticProbe,
  ClusterNodeDetail,
  ClusterNodePeer,
  ClusterAgentsResponse,
  ClusterPeersResponse,
  ClusterTasksResponse,
  ClusterTopology,
  ClusterTopologyLink,
  CommandResult,
  GatewayEventDetail,
  GatewayEventRecord,
  GatewayHealth,
  GatewayMetrics,
  GatewayTaskDetail,
  GatewayTaskRecord,
  RetryAttempt,
  TaskLogRecord,
  TraceDetail,
  LoginRequest
} from '@/lib/types/admin';
import type { CoreTaskRuntimeView } from '@/lib/types/core';
import { toFiniteNumber } from '@/lib/utils/format';

type PageLike<T> = T[] | {
  content?: T[];
  items?: T[];
  records?: T[];
  rows?: T[];
  data?: T[];
};

function toList<T>(value: PageLike<T>): T[] {
  if (Array.isArray(value)) return value;
  return value.content ?? value.items ?? value.records ?? value.rows ?? value.data ?? [];
}

async function apiGetList<T>(path: string): Promise<T[]> {
  return toList(await apiGet<PageLike<T>>(path));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function pickValue(record: Record<string, unknown>, keys: string[]): unknown {
  for (const key of keys) {
    if (record[key] !== undefined && record[key] !== null) return record[key];
  }
  return undefined;
}

function pickNestedValue(record: Record<string, unknown>, path: string[]): unknown {
  let current: unknown = record;
  for (const segment of path) {
    if (!isRecord(current)) return undefined;
    current = current[segment];
  }
  return current === null ? undefined : current;
}

function pickValueDeep(record: Record<string, unknown>, directKeys: string[], nestedPaths: string[][] = []): unknown {
  const directValue = pickValue(record, directKeys);
  if (directValue !== undefined && directValue !== null) return directValue;

  for (const path of nestedPaths) {
    const nestedValue = pickNestedValue(record, path);
    if (nestedValue !== undefined && nestedValue !== null) return nestedValue;
  }

  return undefined;
}

function nestedMetricValue(record: Record<string, unknown>, objectKey: string, keys: string[]): unknown {
  const nested = record[objectKey];
  if (!isRecord(nested)) return undefined;
  return pickValue(nested, keys);
}

function normalizeMemoryMb(value: unknown): number {
  const numberValue = toFiniteNumber(value as number | string | null | undefined);
  if (numberValue === undefined) return 0;
  if (numberValue > 1_000_000) return Math.round((numberValue / 1024 / 1024) * 10) / 10;
  return numberValue;
}

function pickNumber(record: Record<string, unknown>, keys: string[], fallback = 0): number {
  const numberValue = toFiniteNumber(pickValue(record, keys) as number | string | null | undefined);
  return numberValue ?? fallback;
}

function pickNumberDeep(record: Record<string, unknown>, keys: string[], nestedPaths: string[][] = [], fallback = 0): number {
  const numberValue = toFiniteNumber(pickValueDeep(record, keys, nestedPaths) as number | string | null | undefined);
  return numberValue ?? fallback;
}

function normalizePercentValue(value: unknown): number {
  const numberValue = toFiniteNumber(value as number | string | null | undefined);
  if (numberValue === undefined) return 0;

  // 後端可能回 0.35，也可能回 35 或 '35%'; 統一轉成百分比數字。
  if (numberValue >= 0 && numberValue <= 1) return numberValue * 100;
  return numberValue;
}

function normalizeCpuUsagePercent(record: Record<string, unknown>): number {
  const explicitPercent = toFiniteNumber(pickValue(record, ['cpuUsagePercent', 'cpuPercent']) as number | string | null | undefined);
  if (explicitPercent !== undefined) return explicitPercent;

  return normalizePercentValue(pickValue(record, ['cpuUsage', 'cpu', 'processCpuLoad']));
}

function normalizeGatewayMetrics(raw: GatewayMetrics | unknown): GatewayMetrics {
  if (!isRecord(raw)) {
    return {
      nodeId: '-',
      cpuUsagePercent: 0,
      memoryUsedMb: 0,
      memoryMaxMb: 0,
      nettyEventLoopThreads: 0,
      workerThreads: 0,
      queueSize: 0,
      inboundEventsPerMinute: 0,
      routedEventsPerMinute: 0,
      failedEventsPerMinute: 0,
      timestamp: new Date().toISOString()
    };
  }

  return {
    nodeId: String(pickValue(raw, ['nodeId', 'node', 'gatewayNodeId']) ?? '-'),
    cpuUsagePercent: normalizeCpuUsagePercent(raw),
    memoryUsedMb: normalizeMemoryMb(pickValue(raw, ['memoryUsedMb', 'memoryUsedMB', 'usedMemoryMb', 'usedMemoryMB', 'usedMb', 'memoryUsed', 'usedMemory', 'heapUsedMb', 'heapUsed', 'memoryUsedBytes']) ?? nestedMetricValue(raw, 'memory', ['usedMb', 'used', 'usedBytes']) ?? nestedMetricValue(raw, 'heap', ['usedMb', 'used', 'usedBytes'])),
    memoryMaxMb: normalizeMemoryMb(pickValue(raw, ['memoryMaxMb', 'memoryMaxMB', 'maxMemoryMb', 'maxMemoryMB', 'maxMb', 'memoryMax', 'maxMemory', 'heapMaxMb', 'heapMax', 'memoryMaxBytes']) ?? nestedMetricValue(raw, 'memory', ['maxMb', 'max', 'maxBytes']) ?? nestedMetricValue(raw, 'heap', ['maxMb', 'max', 'maxBytes'])),
    nettyEventLoopThreads: pickNumber(raw, ['nettyEventLoopThreads', 'eventLoopThreads', 'eventLoopThreadCount']),
    workerThreads: pickNumber(raw, ['workerThreads', 'workerThreadCount']),
    queueSize: pickNumber(raw, ['queueSize', 'pendingQueueSize', 'taskQueueSize']),
    inboundEventsPerMinute: pickNumber(raw, ['inboundEventsPerMinute', 'receivedEventsPerMinute', 'eventsInPerMinute']),
    routedEventsPerMinute: pickNumber(raw, ['routedEventsPerMinute', 'eventsRoutedPerMinute']),
    failedEventsPerMinute: pickNumber(raw, ['failedEventsPerMinute', 'eventsFailedPerMinute']),
    timestamp: String(pickValue(raw, ['timestamp', 'time', 'generatedAt']) ?? new Date().toISOString())
  };
}


function pickArray(record: Record<string, unknown>, keys: string[]): unknown[] {
  const value = pickValue(record, keys);
  if (Array.isArray(value)) return value;
  if (isRecord(value)) return toList(value as PageLike<unknown>);
  return [];
}

function pickString(record: Record<string, unknown>, keys: string[], fallback = '-'): string {
  const value = pickValue(record, keys);
  if (value === undefined || value === null || value === '') return fallback;
  return String(value);
}

function pickStringDeep(record: Record<string, unknown>, keys: string[], nestedPaths: string[][] = [], fallback = '-'): string {
  const value = pickValueDeep(record, keys, nestedPaths);
  if (value === undefined || value === null || value === '') return fallback;
  return String(value);
}

function pickBoolean(record: Record<string, unknown>, keys: string[], fallback = false): boolean {
  const value = pickValue(record, keys);
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['true', 'yes', 'y', '1'].includes(normalized)) return true;
    if (['false', 'no', 'n', '0'].includes(normalized)) return false;
  }
  if (typeof value === 'number') return value !== 0;
  return fallback;
}

function pickBooleanDeep(record: Record<string, unknown>, keys: string[], nestedPaths: string[][] = [], fallback = false): boolean {
  const value = pickValueDeep(record, keys, nestedPaths);
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['true', 'yes', 'y', '1'].includes(normalized)) return true;
    if (['false', 'no', 'n', '0'].includes(normalized)) return false;
  }
  if (typeof value === 'number') return value !== 0;
  return fallback;
}

function normalizeEnum<T extends string>(value: unknown, allowed: readonly T[], fallback: T): T {
  if (typeof value === 'string') {
    const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, '_');
    if ((allowed as readonly string[]).includes(normalized)) return normalized as T;
  }
  return fallback;
}

function normalizeClusterNodeStatus(value: unknown): ClusterNodeDetail['status'] {
  const normalized = typeof value === 'string' ? value.trim().toUpperCase().replace(/[\s-]+/g, '_') : '';

  // Netty P3.8 semantics:
  // SELF means the node currently handling this Admin API request, not an offline state.
  // SYNCED/DISCOVERED also represent reachable cluster members from the Admin UI perspective.
  if (['SELF', 'SYNCED', 'DISCOVERED'].includes(normalized)) return 'ONLINE';
  if (['STALE', 'DEGRADED'].includes(normalized)) return 'DEGRADED';
  if (['FAILED', 'ERROR', 'DOWN', 'UNREACHABLE', 'TIMEOUT'].includes(normalized)) return 'OFFLINE';

  return normalizeEnum(
    normalized,
    ['ONLINE', 'OFFLINE', 'STARTING', 'LEAVING', 'DISCOVERED', 'DEGRADED'] as const,
    'OFFLINE'
  );
}

function normalizeStatusSource(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, '_');
  return normalized || undefined;
}

function normalizeDiscoveryMode(value: unknown): ClusterNodeDetail['discoveryMode'] {
  const normalized = typeof value === 'string' ? value.trim().toUpperCase().replace(/[\s-]+/g, '_') : '';
  if (normalized.includes('UDP')) return 'UDP';
  if (normalized.includes('DOCKER')) return 'DOCKER';
  if (normalized.includes('STATIC')) return 'STATIC';
  return 'STATIC';
}

function normalizeClusterNodeMetrics(raw: unknown): ClusterNodeDetail['metrics'] {
  const record = isRecord(raw) ? raw : {};
  const memoryUsedPercent = toFiniteNumber(pickValue(record, ['memoryUsedPercent', 'memoryUsagePercent', 'memoryPercent']) as number | string | null | undefined);

  return {
    cpuUsagePercent: normalizeCpuUsagePercent(record),
    memoryUsedMb: normalizeMemoryMb(pickValue(record, ['memoryUsedMb', 'memoryUsedMB', 'usedMemoryMb', 'usedMemoryMB', 'usedMb', 'memoryUsed', 'usedMemory', 'heapUsedMb', 'heapUsed', 'memoryUsedBytes']) ?? nestedMetricValue(record, 'memory', ['usedMb', 'used', 'usedBytes']) ?? nestedMetricValue(record, 'heap', ['usedMb', 'used', 'usedBytes'])),
    memoryMaxMb: normalizeMemoryMb(pickValue(record, ['memoryMaxMb', 'memoryMaxMB', 'maxMemoryMb', 'maxMemoryMB', 'maxMb', 'memoryMax', 'maxMemory', 'heapMaxMb', 'heapMax', 'memoryMaxBytes']) ?? nestedMetricValue(record, 'memory', ['maxMb', 'max', 'maxBytes']) ?? nestedMetricValue(record, 'heap', ['maxMb', 'max', 'maxBytes'])),
    memoryUsedPercent,
    nettyEventLoopThreads: pickNumber(record, ['nettyEventLoopThreads', 'eventLoopThreads', 'eventLoopThreadCount']),
    workerThreads: pickNumber(record, ['workerThreads', 'workerThreadCount']),
    queueSize: pickNumber(record, ['queueSize', 'pendingQueueSize', 'taskQueueSize']),
    activeTaskCount: pickNumber(record, ['activeTaskCount', 'activeTasks', 'processingTasks', 'runningTasks']),
    inboundEventsPerMinute: pickNumber(record, ['inboundEventsPerMinute', 'receivedEventsPerMinute', 'eventsInPerMinute']),
    routedEventsPerMinute: pickNumber(record, ['routedEventsPerMinute', 'eventsRoutedPerMinute']),
    failedEventsPerMinute: pickNumber(record, ['failedEventsPerMinute', 'eventsFailedPerMinute']),
    averageLatencyMs: toFiniteNumber(pickValue(record, ['averageLatencyMs', 'avgLatencyMs', 'latencyMs']) as number | string | null | undefined),
    timestamp: pickString(record, ['timestamp', 'time', 'generatedAt'], new Date().toISOString())
  };
}

function normalizeClusterPeerRelationKind(value: unknown): ClusterNodePeer['relation'] {
  const normalized = typeof value === 'string' ? value.trim().toUpperCase().replace(/[\s-]+/g, '_') : '';
  if (normalized === 'STATIC') return 'STATIC_SEED';
  if (normalized === 'UDP') return 'UDP_DISCOVERY';
  return normalizeEnum(
    normalized,
    ['UDP_DISCOVERY', 'STATIC_PEER', 'STATIC_SEED', 'DOCKER_NETWORK', 'DISCOVERED', 'HEARTBEAT', 'SELF', 'UNKNOWN'] as const,
    'UNKNOWN'
  );
}

function normalizeClusterPeerSyncStatus(value: unknown): ClusterNodePeer['syncStatus'] {
  return normalizeEnum(value, ['SYNCED', 'FAILED', 'STALE', 'UNKNOWN'] as const, 'UNKNOWN');
}

function normalizeClusterPeerHeartbeatStatus(value: unknown): ClusterNodePeer['heartbeatStatus'] {
  return normalizeEnum(value, ['OK', 'WARNING', 'LOST', 'UNKNOWN'] as const, 'UNKNOWN');
}

function deriveClusterPeerHealthStatus(syncStatus?: ClusterNodePeer['syncStatus'], heartbeatStatus?: ClusterNodePeer['heartbeatStatus']): ClusterNodePeer['healthStatus'] {
  if (heartbeatStatus === 'OK' && syncStatus === 'SYNCED') return 'ONLINE';
  if (heartbeatStatus === 'OK') return 'ONLINE';
  if (heartbeatStatus === 'WARNING' || syncStatus === 'STALE') return 'DEGRADED';
  if (heartbeatStatus === 'LOST' || syncStatus === 'FAILED') return 'OFFLINE';
  return 'UNKNOWN';
}

function peerHealthToNodeStatus(healthStatus: ClusterNodePeer['healthStatus']): ClusterNodePeer['status'] {
  if (healthStatus === 'ONLINE') return 'ONLINE';
  if (healthStatus === 'DEGRADED') return 'DEGRADED';
  if (healthStatus === 'OFFLINE') return 'OFFLINE';
  return 'OFFLINE';
}

function peerHealthToLinkStatus(healthStatus: ClusterNodePeer['healthStatus']): ClusterTopologyLink['status'] {
  if (healthStatus === 'ONLINE') return 'UP';
  if (healthStatus === 'DEGRADED') return 'DEGRADED';
  return 'DOWN';
}

function normalizeClusterPeer(raw: unknown, perspectiveNodeId?: string): ClusterNodePeer {
  const record = isRecord(raw) ? raw : {};
  const syncStatus = normalizeClusterPeerSyncStatus(pickValue(record, ['syncStatus', 'clusterSyncStatus']));
  const heartbeatStatus = normalizeClusterPeerHeartbeatStatus(pickValue(record, ['heartbeatStatus', 'health', 'heartbeatHealth']));
  const healthStatus = deriveClusterPeerHealthStatus(syncStatus, heartbeatStatus);
  const latencyMs = toFiniteNumber(pickValue(record, ['heartbeatLatencyMs', 'latencyMs', 'latency', 'rttMs']) as number | string | null | undefined);
  const lastHeartbeatAt = pickStringDeep(record, ['lastHeartbeatAt', 'lastSeenAt', 'timestamp'], [['heartbeat', 'lastAt'], ['heartbeat', 'lastHeartbeatAt']], '') || undefined;
  const lastSyncAt = pickString(record, ['lastSyncAt', 'syncedAt'], '') || undefined;

  return {
    nodeId: pickString(record, ['nodeId', 'peerNodeId', 'targetNodeId', 'id', 'name']),
    status: peerHealthToNodeStatus(healthStatus),
    relation: normalizeClusterPeerRelationKind(pickValue(record, ['relation', 'type', 'discoveryMode'])),
    syncStatus,
    heartbeatStatus,
    healthStatus,
    latencyMs,
    heartbeatLatencyMs: latencyMs,
    lastSeenAt: lastHeartbeatAt ?? lastSyncAt,
    lastSyncAt,
    lastHeartbeatAt,
    missedHeartbeatCount: toFiniteNumber(pickValue(record, ['missedHeartbeatCount', 'missedCount', 'heartbeatMissedCount']) as number | string | null | undefined),
    lastError: pickString(record, ['lastError', 'error', 'lastSyncError'], '') || null,
    perspectiveNodeId
  };
}

export function normalizeClusterNodeDetail(raw: unknown): ClusterNodeDetail {
  const record = isRecord(raw) ? raw : {};
  const syncStatus = normalizeStatusSource(pickValue(record, ['syncStatus', 'clusterSyncStatus']));
  const sourceStatus = normalizeStatusSource(pickValue(record, ['sourceStatus', 'status', 'state']));
  const healthStatusValue = syncStatus ?? sourceStatus;
  const isCurrentRequestNode = pickBoolean(record, ['isCurrentRequestNode', 'currentRequestNode', 'localRequestNode'], sourceStatus === 'SELF' && syncStatus === undefined);
  const nodeId = pickString(record, ['nodeId', 'id', 'name']);
  const agents = pickArray(record, ['agents', 'agentList']).map((agent) => normalizeAgentInfo(agent));
  const metricsSource = isRecord(record.runtimeMetrics)
    ? record.runtimeMetrics
    : isRecord(record.metrics)
      ? record.metrics
      : record;
  const metrics = normalizeClusterNodeMetrics(metricsSource);
  const host = pickStringDeep(record, ['host', 'ip', 'address', 'hostname'], [['node', 'host'], ['addressInfo', 'host']], '-');
  const advertisedAddress = pickStringDeep(record, ['advertisedAddress', 'endpoint', 'advertiseAddress', 'address'], [['network', 'advertisedAddress'], ['advertised', 'address']], host);
  const drainStatus = normalizeEnum(pickValue(record, ['drainStatus', 'maintenanceStatus']), ['ACTIVE', 'DRAINING', 'DRAINED', 'MAINTENANCE'] as const, 'ACTIVE');

  return {
    nodeId,
    host,
    tcpPort: pickNumberDeep(record, ['tcpPort', 'tcp', 'port'], [['ports', 'tcp'], ['ports', 'tcpPort'], ['network', 'tcpPort']]),
    websocketPort: pickNumberDeep(record, ['websocketPort', 'wsPort', 'webSocketPort'], [['ports', 'websocket'], ['ports', 'websocketPort'], ['ports', 'ws'], ['ports', 'wsPort'], ['network', 'websocketPort']]),
    adminPort: pickNumberDeep(record, ['adminPort', 'managementPort', 'httpPort'], [['ports', 'admin'], ['ports', 'adminPort'], ['ports', 'http'], ['network', 'adminPort']]),
    status: normalizeClusterNodeStatus(healthStatusValue),
    agentCount: pickNumber(record, ['agentCount', 'connectedAgents', 'totalAgents'], agents.length),
    lastHeartbeatAt: pickStringDeep(record, ['lastHeartbeatAt', 'lastSeenAt', 'heartbeatAt', 'timestamp', 'lastSyncAt', 'capturedAt'], [['heartbeat', 'lastAt'], ['heartbeat', 'lastHeartbeatAt'], ['heartbeat', 'lastSeenAt'], ['clusterState', 'lastHeartbeatAt']], new Date().toISOString()),
    discoveryMode: normalizeDiscoveryMode(pickValueDeep(record, ['discoveryMode', 'discovery'], [['discovery', 'mode'], ['clusterDiscovery', 'mode']])),
    advertisedAddress,
    role: normalizeEnum(pickValue(record, ['role', 'nodeRole']), ['LEADER', 'FOLLOWER', 'STANDALONE'] as const, 'STANDALONE'),
    drainStatus,
    acceptsNewTasks: pickBooleanDeep(record, ['acceptsNewTasks', 'acceptNewTasks', 'acceptingTasks'], [['taskRuntime', 'acceptsNewTasks']], drainStatus === 'ACTIVE'),
    isCurrentRequestNode,
    sourceStatus,
    syncStatus,
    startedAt: pickStringDeep(record, ['startedAt', 'startTime'], [['runtime', 'startedAt'], ['runtime', 'startTime']], '') || undefined,
    uptimeSeconds: toFiniteNumber(pickValueDeep(record, ['uptimeSeconds', 'uptime'], [['runtime', 'uptimeSeconds'], ['runtime', 'uptime']]) as number | string | null | undefined),
    region: pickString(record, ['region'], '') || undefined,
    zone: pickString(record, ['zone'], '') || undefined,
    lastDiscoveryAt: pickStringDeep(record, ['lastDiscoveryAt'], [['discovery', 'lastDiscoveryAt'], ['clusterDiscovery', 'lastDiscoveryAt']], '') || undefined,
    metrics,
    peers: pickArray(record, ['peers', 'peerNodes', 'links']).map((peer) => normalizeClusterPeer(peer)),
    agents,
    recentTasks: pickArray(record, ['recentTasks', 'tasks']).map((task) => normalizeGatewayTaskRecord(task))
  };
}

function normalizeClusterTopologyLink(raw: unknown): ClusterTopologyLink {
  const record = isRecord(raw) ? raw : {};
  const syncStatus = normalizeClusterPeerSyncStatus(pickValue(record, ['syncStatus', 'clusterSyncStatus']));
  const heartbeatStatus = normalizeClusterPeerHeartbeatStatus(pickValue(record, ['heartbeatStatus', 'health', 'heartbeatHealth']));
  const healthStatus = deriveClusterPeerHealthStatus(syncStatus, heartbeatStatus);

  return {
    fromNodeId: pickString(record, ['fromNodeId', 'sourceNodeId', 'from', 'source']),
    toNodeId: pickString(record, ['toNodeId', 'targetNodeId', 'peerNodeId', 'nodeId', 'to', 'target']),
    relation: normalizeClusterPeerRelationKind(pickValue(record, ['relation', 'type', 'discoveryMode'])),
    status: normalizeEnum(pickValue(record, ['status', 'state']), ['UP', 'DOWN', 'DEGRADED'] as const, peerHealthToLinkStatus(healthStatus)),
    syncStatus,
    heartbeatStatus,
    latencyMs: toFiniteNumber(pickValue(record, ['heartbeatLatencyMs', 'latencyMs', 'latency', 'rttMs']) as number | string | null | undefined),
    lastSeenAt: pickString(record, ['lastSeenAt', 'lastHeartbeatAt', 'timestamp'], '') || undefined,
    missedHeartbeatCount: toFiniteNumber(pickValue(record, ['missedHeartbeatCount', 'missedCount']) as number | string | null | undefined),
    lastError: pickString(record, ['lastError', 'error', 'lastSyncError'], '') || null
  };
}

function peerToTopologyLink(peer: ClusterNodePeer, localNodeId?: string): ClusterTopologyLink {
  return {
    fromNodeId: localNodeId ?? peer.perspectiveNodeId ?? 'local',
    toNodeId: peer.nodeId,
    relation: peer.relation,
    status: peerHealthToLinkStatus(peer.healthStatus),
    syncStatus: peer.syncStatus,
    heartbeatStatus: peer.heartbeatStatus,
    latencyMs: peer.heartbeatLatencyMs ?? peer.latencyMs,
    lastSeenAt: peer.lastHeartbeatAt ?? peer.lastSeenAt ?? peer.lastSyncAt,
    missedHeartbeatCount: peer.missedHeartbeatCount,
    lastError: peer.lastError
  };
}

function deriveClusterTopologySummary(nodes: ClusterNodeDetail[], rawSummary?: Record<string, unknown>): ClusterTopology['summary'] {
  const summary = rawSummary ?? {};
  const derivedTotalNodes = nodes.length;
  const derivedOnlineNodes = nodes.filter((node) => node.status === 'ONLINE' || node.status === 'DISCOVERED').length;
  const derivedOfflineNodes = nodes.filter((node) => node.status === 'OFFLINE').length;
  const derivedDrainingNodes = nodes.filter((node) => node.drainStatus === 'DRAINING' || node.drainStatus === 'DRAINED' || node.drainStatus === 'MAINTENANCE').length;
  const derivedTotalAgents = nodes.reduce((sum, node) => sum + (toFiniteNumber(node.agentCount) ?? 0), 0);
  const derivedActiveTasks = nodes.reduce((sum, node) => sum + (toFiniteNumber(node.metrics?.activeTaskCount) ?? 0), 0);
  const derivedQueueSize = nodes.reduce((sum, node) => sum + (toFiniteNumber(node.metrics?.queueSize) ?? 0), 0);

  return {
    totalNodes: pickNumber(summary, ['totalNodes', 'nodeCount'], derivedTotalNodes),
    onlineNodes: pickNumber(summary, ['onlineNodes', 'activeNodes'], derivedOnlineNodes),
    offlineNodes: pickNumber(summary, ['offlineNodes'], derivedOfflineNodes),
    drainingNodes: pickNumber(summary, ['drainingNodes', 'maintenanceNodes'], derivedDrainingNodes),
    totalAgents: pickNumber(summary, ['totalAgents', 'agentCount', 'connectedAgents'], derivedTotalAgents),
    activeTasks: pickNumber(summary, ['activeTasks', 'processingTasks', 'runningTasks'], derivedActiveTasks),
    queueSize: pickNumber(summary, ['queueSize', 'pendingQueueSize', 'taskQueueSize'], derivedQueueSize)
  };
}

function getRuntimeMetricTimestampMs(raw: unknown): number | undefined {
  if (!isRecord(raw)) return undefined;
  const timestamp = pickString(raw, ['timestamp', 'time', 'generatedAt'], '');
  if (!timestamp) return undefined;
  const parsed = new Date(timestamp).getTime();
  return Number.isFinite(parsed) ? parsed : undefined;
}

function chooseLatestRuntimeMetrics(...candidates: unknown[]): Record<string, unknown> | undefined {
  let selected: Record<string, unknown> | undefined;
  let selectedTimestamp: number | undefined;

  for (const candidate of candidates) {
    if (!isRecord(candidate)) continue;
    const candidateTimestamp = getRuntimeMetricTimestampMs(candidate);

    if (selected === undefined) {
      selected = candidate;
      selectedTimestamp = candidateTimestamp;
      continue;
    }

    if (candidateTimestamp !== undefined && (selectedTimestamp === undefined || candidateTimestamp >= selectedTimestamp)) {
      selected = candidate;
      selectedTimestamp = candidateTimestamp;
    }
  }

  return selected;
}

function mergeRuntimeMetricMaps(...maps: Record<string, unknown>[]): Record<string, unknown> {
  const merged: Record<string, unknown> = {};
  for (const map of maps) {
    for (const [nodeId, metrics] of Object.entries(map)) {
      const latest = chooseLatestRuntimeMetrics(merged[nodeId], metrics);
      if (latest !== undefined) merged[nodeId] = latest;
    }
  }
  return merged;
}

function buildOverviewStateNode(rawState: unknown, isCurrentRequestNode: boolean): unknown {
  const state = isRecord(rawState) ? rawState : {};
  const node = isRecord(state.node) ? state.node : {};
  const agentSummary = isRecord(state.agentSummary) ? state.agentSummary : {};
  const syncStatus = normalizeStatusSource(pickValue(state, ['syncStatus']));
  const sourceStatus = normalizeStatusSource(pickValue(node, ['status', 'state']));
  const runtimeMetrics = pickValue(state, ['runtimeMetrics']) ?? pickValue(node, ['runtimeMetrics']);
  const lastSeenAt = pickValue(node, ['lastHeartbeatAt', 'lastSeenAt', 'heartbeatAt']) ?? pickValue(state, ['lastSyncAt', 'capturedAt']);

  return {
    ...node,
    nodeId: pickString(state, ['nodeId'], pickString(node, ['nodeId', 'id', 'name'])),
    status: syncStatus ?? sourceStatus,
    sourceStatus,
    syncStatus,
    isCurrentRequestNode,
    runtimeMetrics,
    agentCount: pickNumber(agentSummary, ['total', 'online', 'connected'], pickNumber(node, ['agentCount', 'connectedAgents', 'totalAgents'])),
    agents: pickArray(state, ['agents']),
    recentTasks: pickArray(state, ['recentTasks', 'tasks']),
    lastHeartbeatAt: lastSeenAt,
    lastSyncAt: pickValue(state, ['lastSyncAt']),
    capturedAt: pickValue(state, ['capturedAt'])
  };
}

function extractClusterOverviewNodes(raw: unknown): unknown[] {
  const record = isRecord(raw) ? raw : {};
  const explicitNodes = Array.isArray(raw) ? raw : pickArray(record, ['nodes', 'clusterNodes', 'nodeList', 'items', 'content', 'records', 'rows']);
  if (explicitNodes.length > 0) return explicitNodes;

  const nodes: unknown[] = [];
  if (isRecord(record.localState)) nodes.push(buildOverviewStateNode(record.localState, true));
  for (const remoteState of pickArray(record, ['remoteStates', 'remotes', 'remoteNodeStates'])) {
    nodes.push(buildOverviewStateNode(remoteState, false));
  }

  return nodes;
}

function getNodeIdFromRawNode(rawNode: unknown): string {
  const record = isRecord(rawNode) ? rawNode : {};
  return pickString(record, ['nodeId', 'id', 'name']);
}

function mergeOverviewMetadataIntoNode(rawNode: unknown, overviewNode: unknown): unknown {
  if (!isRecord(rawNode) || !isRecord(overviewNode)) return rawNode;

  return {
    ...overviewNode,
    ...rawNode,
    isCurrentRequestNode: pickBoolean(rawNode, ['isCurrentRequestNode', 'currentRequestNode', 'localRequestNode'], pickBoolean(overviewNode, ['isCurrentRequestNode', 'currentRequestNode', 'localRequestNode'])),
    sourceStatus: pickValue(rawNode, ['sourceStatus']) ?? pickValue(overviewNode, ['sourceStatus']),
    syncStatus: pickValue(rawNode, ['syncStatus']) ?? pickValue(overviewNode, ['syncStatus']),
    runtimeMetrics: chooseLatestRuntimeMetrics(overviewNode.runtimeMetrics, rawNode.runtimeMetrics)
  };
}

function mergeRuntimeMetricsIntoNode(rawNode: unknown, runtimeMetricsByNode: Record<string, unknown>): ClusterNodeDetail {
  const nodeRecord = isRecord(rawNode) ? rawNode : {};
  const nodeId = pickString(nodeRecord, ['nodeId', 'id', 'name']);
  const externalMetrics = runtimeMetricsByNode[nodeId];
  const runtimeMetrics = chooseLatestRuntimeMetrics(nodeRecord.runtimeMetrics, externalMetrics);

  if (runtimeMetrics === undefined) return normalizeClusterNodeDetail(rawNode);

  return normalizeClusterNodeDetail({
    ...nodeRecord,
    runtimeMetrics
  });
}

function pickRuntimeMetricsByNode(raw: unknown): Record<string, unknown> {
  const record = isRecord(raw) ? raw : {};
  const value = pickValue(record, ['runtimeMetricsByNode', 'metricsByNode', 'nodeMetrics']);
  return isRecord(value) ? value : {};
}

function getOverviewLocalNodeId(raw: unknown): string | undefined {
  const record = isRecord(raw) ? raw : {};
  const explicit = pickString(record, ['localNodeId'], '');
  if (explicit) return explicit;

  if (isRecord(record.localState)) {
    const localState = record.localState;
    const localNode = isRecord(localState.node) ? localState.node : {};
    const localNodeId = pickString(localState, ['nodeId'], pickString(localNode, ['nodeId', 'id', 'name'], ''));
    if (localNodeId) return localNodeId;
  }

  return undefined;
}

export function normalizeClusterPeersResponse(raw: unknown, fallbackLocalNodeId?: string): ClusterPeersResponse {
  const record = isRecord(raw) ? raw : {};
  const localNodeId = pickString(record, ['localNodeId', 'nodeId'], fallbackLocalNodeId ?? '') || fallbackLocalNodeId;
  const peers = pickArray(record, ['peers', 'peerRelations', 'relations', 'items', 'content', 'data'])
    .map((peer) => normalizeClusterPeer(peer, localNodeId));

  return {
    localNodeId,
    generatedAt: pickString(record, ['generatedAt', 'timestamp', 'time'], new Date().toISOString()),
    peers
  };
}

function extractClusterPeerRelations(raw: unknown, fallbackPeerResponse?: ClusterPeersResponse): ClusterPeersResponse {
  if (fallbackPeerResponse && fallbackPeerResponse.peers.length > 0) return fallbackPeerResponse;

  const record = isRecord(raw) ? raw : {};
  const localNodeId = getOverviewLocalNodeId(raw);
  const peerRelations = pickArray(record, ['peerRelations', 'peers', 'peerLinks']);
  if (peerRelations.length > 0) {
    return {
      localNodeId,
      generatedAt: pickString(record, ['generatedAt', 'timestamp', 'time'], new Date().toISOString()),
      peers: peerRelations.map((peer) => normalizeClusterPeer(peer, localNodeId))
    };
  }

  return fallbackPeerResponse ?? {
    localNodeId,
    generatedAt: pickString(record, ['generatedAt', 'timestamp', 'time'], new Date().toISOString()),
    peers: []
  };
}

function mergePeerLists(basePeers: ClusterNodePeer[], additionalPeers: ClusterNodePeer[]): ClusterNodePeer[] {
  const merged = new Map<string, ClusterNodePeer>();
  for (const peer of basePeers) merged.set(peer.nodeId, peer);
  for (const peer of additionalPeers) {
    const previous = merged.get(peer.nodeId);
    merged.set(peer.nodeId, { ...previous, ...peer });
  }
  return Array.from(merged.values());
}

function attachPeerRelationsToNodes(nodes: ClusterNodeDetail[], peerResponse?: ClusterPeersResponse): ClusterNodeDetail[] {
  if (!peerResponse || peerResponse.peers.length === 0) return nodes;

  const localNodeId = peerResponse.localNodeId ?? nodes.find((node) => node.isCurrentRequestNode)?.nodeId;

  return nodes.map((node) => {
    const relevantPeers = node.nodeId === localNodeId
      ? peerResponse.peers
      : peerResponse.peers.filter((peer) => peer.nodeId === node.nodeId);

    if (relevantPeers.length === 0) return node;
    return { ...node, peers: mergePeerLists(node.peers, relevantPeers) };
  });
}

function normalizeClusterTopology(raw: unknown, peerResponse?: ClusterPeersResponse): ClusterTopology {
  const record = isRecord(raw) ? raw : {};
  const runtimeMetricsByNode = pickRuntimeMetricsByNode(raw);
  const rawNodes = extractClusterOverviewNodes(raw);
  const normalizedNodes = rawNodes.map((node) => mergeRuntimeMetricsIntoNode(node, runtimeMetricsByNode));
  const overviewPeerResponse = extractClusterPeerRelations(raw, peerResponse);
  const nodes = attachPeerRelationsToNodes(normalizedNodes, overviewPeerResponse);
  const rawLinks = pickArray(record, ['links', 'peerLinks', 'edges', 'connections']);
  const links = rawLinks.length > 0
    ? rawLinks.map(normalizeClusterTopologyLink)
    : overviewPeerResponse.peers.map((peer) => peerToTopologyLink(peer, overviewPeerResponse.localNodeId));
  const rawSummary = isRecord(record.summary) ? record.summary : record;

  return {
    generatedAt: pickString(record, ['generatedAt', 'timestamp', 'time'], overviewPeerResponse.generatedAt ?? new Date().toISOString()),
    summary: deriveClusterTopologySummary(nodes, rawSummary),
    nodes,
    links
  };
}

async function tryApiGet<T>(path: string): Promise<T | undefined> {
  try {
    return await apiGet<T>(path);
  } catch (error) {
    if (isNotFoundOrUnsupportedApiError(error)) return undefined;
    return undefined;
  }
}

async function tryApiGetList<T>(path: string): Promise<T[] | undefined> {
  const value = await tryApiGet<PageLike<T>>(path);
  if (value === undefined) return undefined;
  return toList(value);
}

async function getClusterRuntimeMetricsByNode(): Promise<Record<string, unknown>> {
  const diagnostics = await tryApiGet<Record<string, unknown>>(adminEndpoints.clusterRuntimeMetricsV2);
  return isRecord(diagnostics) ? diagnostics : {};
}

async function getClusterOverviewRaw(): Promise<unknown | undefined> {
  return tryApiGet<unknown>(adminEndpoints.clusterOverviewV2);
}

async function getClusterPeersRaw(): Promise<ClusterPeersResponse | undefined> {
  const rawPeers = await tryApiGet<unknown>(adminEndpoints.clusterPeersV2);
  return rawPeers === undefined ? undefined : normalizeClusterPeersResponse(rawPeers);
}

function coerceByNodeMap(candidate: unknown): Record<string, unknown[]> {
  if (!isRecord(candidate)) return {};

  return Object.entries(candidate).reduce<Record<string, unknown[]>>((accumulator, [nodeId, value]) => {
    if (Array.isArray(value)) {
      accumulator[nodeId] = value;
      return accumulator;
    }
    if (isRecord(value)) {
      const list = toList(value as PageLike<unknown>);
      if (list.length > 0) accumulator[nodeId] = list;
    }
    return accumulator;
  }, {});
}

function pickByNodeMap(raw: unknown, keys: string[]): Record<string, unknown[]> {
  const record = isRecord(raw) ? raw : {};
  const candidate = pickValue(record, keys);
  const explicit = coerceByNodeMap(candidate);
  if (Object.keys(explicit).length > 0) return explicit;

  // Some P4 endpoints may return the by-node map directly:
  // { "gateway-node-001": [...], "gateway-node-002": [...] }
  const topLevel = coerceByNodeMap(record);
  const knownEnvelopeKeys = ['localNodeId', 'generatedAt', 'timestamp', 'items', 'content', 'data', 'agents', 'tasks'];
  const hasEnvelopeList = knownEnvelopeKeys.some((key) => Array.isArray(record[key]));
  return hasEnvelopeList ? {} : topLevel;
}

function flattenByNode<T>(byNode: Record<string, T[]>): T[] {
  return Object.values(byNode).flat();
}

function groupAgentsByNode(agents: AgentInfo[]): Record<string, AgentInfo[]> {
  return agents.reduce<Record<string, AgentInfo[]>>((accumulator, agent) => {
    const nodeId = agent.ownerNodeId ?? agent.nodeId ?? '-';
    accumulator[nodeId] = [...(accumulator[nodeId] ?? []), agent];
    return accumulator;
  }, {});
}

function groupTasksByNode(tasks: GatewayTaskRecord[]): Record<string, GatewayTaskRecord[]> {
  return tasks.reduce<Record<string, GatewayTaskRecord[]>>((accumulator, task) => {
    const nodeId = task.ownerNodeId ?? task.assignedNodeId ?? '-';
    accumulator[nodeId] = [...(accumulator[nodeId] ?? []), task];
    return accumulator;
  }, {});
}

export function normalizeClusterAgentsResponse(raw: unknown, scope: ClusterAgentsResponse['scope'] = 'CLUSTER', fallbackLocalNodeId?: string, fallbackReason?: string): ClusterAgentsResponse {
  const record = isRecord(raw) ? raw : {};
  const localNodeId = pickString(record, ['localNodeId', 'nodeId'], fallbackLocalNodeId ?? '') || fallbackLocalNodeId;
  const rawByNode = pickByNodeMap(raw, ['agentsByNode', 'agentDetailsByNode', 'byNode', 'nodes']);
  const byNode = Object.entries(rawByNode).reduce<Record<string, AgentInfo[]>>((accumulator, [nodeId, agents]) => {
    accumulator[nodeId] = agents.map((agent) => normalizeAgentInfo(agent, nodeId));
    return accumulator;
  }, {});

  const rawAgents = Array.isArray(raw)
    ? raw
    : pickArray(record, ['agents', 'items', 'content', 'data', 'records', 'rows']);
  const directAgents = rawAgents.map((agent) => normalizeAgentInfo(agent, localNodeId));
  const items = Object.keys(byNode).length > 0 ? flattenByNode(byNode) : directAgents;
  const normalizedByNode = Object.keys(byNode).length > 0 ? byNode : groupAgentsByNode(items);

  return {
    scope,
    localNodeId,
    generatedAt: pickString(record, ['generatedAt', 'timestamp', 'time'], new Date().toISOString()),
    items,
    byNode: normalizedByNode,
    fallbackReason
  };
}

export function normalizeClusterTasksResponse(raw: unknown, scope: ClusterTasksResponse['scope'] = 'CLUSTER', fallbackLocalNodeId?: string, fallbackReason?: string): ClusterTasksResponse {
  const record = isRecord(raw) ? raw : {};
  const localNodeId = pickString(record, ['localNodeId', 'nodeId'], fallbackLocalNodeId ?? '') || fallbackLocalNodeId;
  const rawByNode = pickByNodeMap(raw, ['tasksByNode', 'taskDetailsByNode', 'byNode', 'nodes']);
  const byNode = Object.entries(rawByNode).reduce<Record<string, GatewayTaskRecord[]>>((accumulator, [nodeId, tasks]) => {
    accumulator[nodeId] = tasks.map((task) => normalizeGatewayTaskRecord(task, nodeId));
    return accumulator;
  }, {});

  const rawTasks = Array.isArray(raw)
    ? raw
    : pickArray(record, ['tasks', 'items', 'content', 'data', 'records', 'rows']);
  const directTasks = rawTasks.map((task) => normalizeGatewayTaskRecord(task, localNodeId));
  const items = Object.keys(byNode).length > 0 ? flattenByNode(byNode) : directTasks;
  const normalizedByNode = Object.keys(byNode).length > 0 ? byNode : groupTasksByNode(items);

  return {
    scope,
    localNodeId,
    generatedAt: pickString(record, ['generatedAt', 'timestamp', 'time'], new Date().toISOString()),
    items,
    byNode: normalizedByNode,
    fallbackReason
  };
}

function pickCoreTaskNodeId(task: CoreTaskRuntimeView): string | undefined {
  const taskRecord = task as unknown as Record<string, unknown>;
  const payload = isRecord(task.payload) ? task.payload : {};
  return pickString(taskRecord, ['ownerNodeId', 'ownerGatewayNodeId', 'assignedNodeId', 'gatewayNodeId', 'nodeId'], '')
    || pickString(payload, ['ownerNodeId', 'ownerGatewayNodeId', 'assignedNodeId', 'gatewayNodeId', 'nodeId'], '')
    || undefined;
}

function coreTaskStatusForGateway(task: CoreTaskRuntimeView): GatewayTaskRecord['status'] {
  const rawStatus = String(task.callbackStatus ?? task.dispatchExecutionStatus ?? task.dispatchStatus ?? task.status ?? '').toUpperCase();
  const mapped = rawStatus === 'SUCCEEDED' ? 'COMPLETED'
    : rawStatus === 'RETRY_WAIT' || rawStatus === 'RETRY_WAITING' ? 'PENDING'
      : rawStatus === 'DEAD_LETTER' || rawStatus === 'ESCALATED' || rawStatus === 'ORPHANED' ? 'FAILED'
        : rawStatus === 'DELIVERED' || rawStatus === 'DELIVERED_TO_GATEWAY' ? 'DISPATCHED'
          : rawStatus === 'EXECUTING' ? 'PROCESSING'
            : rawStatus === 'WAITING_REVIEW' ? 'WAITING_APPROVAL'
              : rawStatus === 'QUEUED' ? 'PENDING'
                : rawStatus;
  return normalizeGatewayTaskStatus(mapped);
}

function coreTaskRuntimeToGatewayTask(task: CoreTaskRuntimeView): GatewayTaskRecord {
  const createdAt = task.createdAt ?? task.updatedAt ?? new Date().toISOString();
  const status = coreTaskStatusForGateway(task);
  const nodeId = pickCoreTaskNodeId(task);

  return {
    taskId: task.taskId,
    traceId: task.traceId ?? task.sourceEventId ?? task.taskId,
    status,
    assignedAgentId: task.assignedAgentId,
    ownerNodeId: nodeId,
    assignedNodeId: nodeId,
    createdAt,
    assignedAt: task.dispatchRequestId ? task.updatedAt ?? createdAt : undefined,
    startedAt: ['ACKED', 'PROCESSING', 'RUNNING', 'COMPLETED', 'FAILED'].includes(status) ? task.updatedAt ?? createdAt : undefined,
    completedAt: status === 'COMPLETED' ? task.updatedAt ?? createdAt : undefined,
    failedAt: status === 'FAILED' || status === 'TIMEOUT' || status === 'CANCELLED' ? task.updatedAt ?? createdAt : undefined,
    retryCount: task.dispatchAttemptCount ?? 0,
    failureReason: task.failureReason ?? task.dispatchRetryReason ?? task.lifecycleReason,
    requestPayload: task.payload,
    responsePayload: {
      source: 'CORE_TASK_RUNTIME_VIEW',
      dispatchRequestId: task.dispatchRequestId,
      dispatchStatus: task.dispatchStatus,
      callbackStatus: task.callbackStatus,
      nextAction: task.nextAction
    }
  };
}

function coreTaskRuntimeToTraceDetail(task: CoreTaskRuntimeView): TraceDetail {
  const gatewayTask = coreTaskRuntimeToGatewayTask(task);
  const traceId = gatewayTask.traceId;
  const createdAt = gatewayTask.createdAt;
  const steps: TraceDetail['steps'] = [
    {
      stepId: `${traceId}-core-task`,
      traceId,
      stage: 'CORE_TASK_TRUTH',
      status: gatewayTask.status === 'FAILED' ? 'FAILED' : gatewayTask.status === 'COMPLETED' ? 'SUCCESS' : 'RUNNING',
      actorType: 'ROUTER',
      actorId: 'core',
      timestamp: createdAt,
      message: task.lifecycleReason ?? task.createdReason ?? 'Core authoritative task record',
      payload: task.payload
    }
  ];

  if (task.dispatchRequestId) {
    steps.push({
      stepId: `${traceId}-dispatch`,
      traceId,
      stage: 'DISPATCH_REQUEST',
      status: task.dispatchExecutionStatus === 'FAILED' ? 'FAILED' : task.dispatchExecutionStatus === 'COMPLETED' ? 'SUCCESS' : 'RUNNING',
      actorType: 'GATEWAY',
      actorId: gatewayTask.assignedNodeId ?? gatewayTask.assignedAgentId ?? 'gateway',
      timestamp: task.updatedAt ?? createdAt,
      message: `${task.dispatchRequestId} · ${task.dispatchStatus ?? 'UNKNOWN'}`,
      payload: gatewayTask.responsePayload
    });
  }

  return {
    traceId,
    status: gatewayTask.status,
    sourceSystem: task.sourceSystem,
    eventId: task.sourceEventId,
    taskId: task.taskId,
    startedAt: gatewayTask.startedAt ?? createdAt,
    endedAt: gatewayTask.completedAt ?? gatewayTask.failedAt,
    steps
  };
}

function coreTaskRuntimeToGatewayTaskDetail(task: CoreTaskRuntimeView): GatewayTaskDetail {
  const gatewayTask = coreTaskRuntimeToGatewayTask(task);
  const trace = coreTaskRuntimeToTraceDetail(task);
  const attempts: RetryAttempt[] = Array.from({ length: Math.max(0, gatewayTask.retryCount) }, (_, index) => ({
    attemptNo: index + 1,
    taskId: gatewayTask.taskId,
    requestedAt: gatewayTask.createdAt,
    status: gatewayTask.status,
    message: index === gatewayTask.retryCount - 1 ? gatewayTask.failureReason : undefined
  }));
  const logs: TaskLogRecord[] = [
    {
      logId: `${gatewayTask.taskId}-core-runtime-view`,
      taskId: gatewayTask.taskId,
      timestamp: gatewayTask.completedAt ?? gatewayTask.failedAt ?? gatewayTask.createdAt,
      level: gatewayTask.failureReason ? 'WARN' : 'INFO',
      message: gatewayTask.failureReason ?? task.nextAction ?? 'Loaded from Core authoritative task runtime view.',
      payload: gatewayTask.responsePayload
    }
  ];

  return {
    ...gatewayTask,
    trace,
    attempts,
    logs
  };
}

function taskBelongsToNode(task: GatewayTaskRecord, nodeId: string, agentsForNode: ReadonlySet<string>): boolean {
  return task.ownerNodeId === nodeId
    || task.assignedNodeId === nodeId
    || (task.assignedAgentId !== undefined && agentsForNode.has(task.assignedAgentId));
}

async function getClusterAgentsResponse(): Promise<ClusterAgentsResponse> {
  const clusterAgents = await tryApiGet<unknown>(adminEndpoints.clusterAgentsV2)
    ?? await tryApiGet<unknown>(adminEndpoints.clusterAgentsByNodeV2);

  if (clusterAgents !== undefined) return normalizeClusterAgentsResponse(clusterAgents, 'CLUSTER');

  const localAgents = await apiGetList<unknown>(adminEndpoints.agents);
  return normalizeClusterAgentsResponse(
    { agents: localAgents },
    'LOCAL',
    undefined,
    'Cluster aggregated Agent API unavailable; using local /api/admin/agents fallback.'
  );
}

async function getClusterTasksResponse(): Promise<ClusterTasksResponse> {
  const coreTasks = await coreAdminApi.getTasksRuntimeView();
  return normalizeClusterTasksResponse(
    { tasks: coreTasks.map(coreTaskRuntimeToGatewayTask) },
    'LOCAL',
    undefined,
    'Tasks are loaded from Core /admin/tasks/runtime-view. Netty task endpoints are intentionally not used because Netty is transport diagnostics, not task truth.'
  );
}

async function getClusterNodesRaw(): Promise<unknown[]> {
  const v2Nodes = await tryApiGetList<unknown>(adminEndpoints.clusterNodesV2);
  if (v2Nodes !== undefined) return v2Nodes;
  return apiGetList<unknown>(adminEndpoints.clusterNodes);
}

async function getClusterNodesNormalized(): Promise<ClusterNodeDetail[]> {
  const [rawNodes, overview, diagnosticsMetrics, peers] = await Promise.all([
    getClusterNodesRaw(),
    getClusterOverviewRaw(),
    getClusterRuntimeMetricsByNode(),
    getClusterPeersRaw()
  ]);

  const overviewMetrics = pickRuntimeMetricsByNode(overview);
  const runtimeMetricsByNode = mergeRuntimeMetricMaps(overviewMetrics, diagnosticsMetrics);
  const overviewNodes = extractClusterOverviewNodes(overview);
  const overviewNodesById = new Map(overviewNodes.map((node) => [getNodeIdFromRawNode(node), node]));
  const baseNodes = rawNodes.length > 0 ? rawNodes : overviewNodes;
  const peerResponse = extractClusterPeerRelations(overview, peers);
  const nodes = baseNodes.map((node) => mergeRuntimeMetricsIntoNode(mergeOverviewMetadataIntoNode(node, overviewNodesById.get(getNodeIdFromRawNode(node))), runtimeMetricsByNode));

  return validateArrayContract(
    'ClusterNodeDetail',
    attachPeerRelationsToNodes(nodes, peerResponse),
    validateClusterNodeDetail
  );
}

async function getClusterTopologyNormalized(): Promise<ClusterTopology> {
  const [overview, peers] = await Promise.all([getClusterOverviewRaw(), getClusterPeersRaw()]);
  if (overview !== undefined) {
    const overviewRecord = isRecord(overview) ? overview : {};
    const overviewNodes = extractClusterOverviewNodes(overview);
    if (overviewNodes.length > 0) return normalizeClusterTopology(overview, peers);

    const nodes = attachPeerRelationsToNodes(await getClusterNodesNormalized(), extractClusterPeerRelations(overview, peers));
    return {
      generatedAt: pickString(overviewRecord, ['generatedAt', 'timestamp', 'time'], peers?.generatedAt ?? new Date().toISOString()),
      summary: deriveClusterTopologySummary(nodes, overviewRecord),
      nodes,
      links: (peers?.peers.length ?? 0) > 0
        ? peers!.peers.map((peer) => peerToTopologyLink(peer, peers!.localNodeId))
        : pickArray(overviewRecord, ['links', 'peerLinks', 'edges', 'connections']).map(normalizeClusterTopologyLink)
    };
  }

  const adminTopology = await tryApiGet<unknown>(adminEndpoints.clusterTopology);
  if (adminTopology !== undefined) return normalizeClusterTopology(adminTopology, peers);

  const nodes = attachPeerRelationsToNodes(await getClusterNodesNormalized(), peers);
  return {
    generatedAt: peers?.generatedAt ?? new Date().toISOString(),
    summary: deriveClusterTopologySummary(nodes),
    nodes,
    links: peers?.peers.map((peer) => peerToTopologyLink(peer, peers.localNodeId)) ?? []
  };
}

async function getClusterNodeDetailNormalized(nodeId: string): Promise<ClusterNodeDetail> {
  const [nodes, adminDetail] = await Promise.all([
    getClusterNodesNormalized(),
    tryApiGet<unknown>(adminEndpoints.clusterNodeDetail(nodeId))
  ]);

  const listDetail = nodes.find((node) => node.nodeId === nodeId);
  const normalizedAdminDetail = adminDetail === undefined ? undefined : normalizeClusterNodeDetail(adminDetail);

  if (listDetail && normalizedAdminDetail) {
    return validateContract('ClusterNodeDetail', {
      ...normalizedAdminDetail,
      ...listDetail,
      agents: normalizedAdminDetail.agents.length > 0 ? normalizedAdminDetail.agents : listDetail.agents,
      recentTasks: normalizedAdminDetail.recentTasks.length > 0 ? normalizedAdminDetail.recentTasks : listDetail.recentTasks,
      peers: normalizedAdminDetail.peers.length > 0 ? normalizedAdminDetail.peers : listDetail.peers
    }, validateClusterNodeDetail);
  }

  if (listDetail) return validateContract('ClusterNodeDetail', listDetail, validateClusterNodeDetail);
  if (normalizedAdminDetail) return validateContract('ClusterNodeDetail', normalizedAdminDetail, validateClusterNodeDetail);

  return validateContract('ClusterNodeDetail', normalizeClusterNodeDetail({ nodeId }), validateClusterNodeDetail);
}




export function normalizeAgentInfo(raw: unknown, fallbackNodeId?: string): AgentInfo {
  const record = isRecord(raw) ? raw : {};
  const agentId = pickString(record, ['agentId', 'id', 'name']);
  const ownerNodeId = pickString(record, ['ownerNodeId', 'owningNodeId', 'ownerGatewayNodeId', 'gatewayNodeId', 'nodeId', 'node'], fallbackNodeId ?? '-');

  return {
    agentId,
    agentType: normalizeEnum(pickValue(record, ['agentType', 'type']), ['OPENCLAW', 'HERMES', 'TAO', 'CUSTOM'] as const, 'CUSTOM'),
    status: normalizeEnum(pickValue(record, ['status', 'state']), ['CONNECTED', 'IDLE', 'BUSY', 'OFFLINE', 'ERROR'] as const, 'OFFLINE'),
    ownerNodeId,
    nodeId: ownerNodeId,
    transport: normalizeEnum(pickValue(record, ['transport', 'connectionType']), ['WEBSOCKET', 'TCP', 'HTTP_CALLBACK'] as const, 'WEBSOCKET'),
    connectedAt: pickString(record, ['connectedAt', 'createdAt', 'timestamp'], new Date().toISOString()),
    lastSeenAt: pickString(record, ['lastSeenAt', 'lastHeartbeatAt', 'timestamp'], new Date().toISOString()),
    currentTaskId: pickString(record, ['currentTaskId', 'taskId'], '') || undefined,
    errorCount: pickNumber(record, ['errorCount', 'errors'])
  };
}

function normalizeAgentCapability(raw: unknown, index: number): AgentDetail['capabilities'][number] {
  const record = isRecord(raw) ? raw : {};
  const name = pickString(record, ['name', 'capability', 'type'], `CAPABILITY_${index + 1}`);
  const supportedActions = pickArray(record, ['supportedActions', 'actions', 'tools']).map(String);

  return {
    capabilityId: pickString(record, ['capabilityId', 'id'], name),
    name,
    description: pickString(record, ['description', 'message'], ''),
    status: normalizeEnum(pickValue(record, ['status', 'state', 'enabled']), ['ENABLED', 'DISABLED', 'DEGRADED'] as const, pickBoolean(record, ['enabled'], true) ? 'ENABLED' : 'DISABLED'),
    version: pickString(record, ['version'], '') || undefined,
    supportedActions,
    metadata: isRecord(record.metadata) ? record.metadata : undefined
  };
}

function normalizeAgentRuntime(raw: unknown): AgentDetail['runtime'] {
  const record = isRecord(raw) ? raw : {};
  return {
    processId: pickString(record, ['processId', 'pid'], '') || undefined,
    host: pickString(record, ['host', 'hostname', 'ip'], '') || undefined,
    runtimeVersion: pickString(record, ['runtimeVersion', 'version'], '') || undefined,
    protocolVersion: pickString(record, ['protocolVersion'], '') || undefined,
    heartbeatIntervalMs: toFiniteNumber(pickValue(record, ['heartbeatIntervalMs']) as number | string | null | undefined),
    lastHeartbeatAt: pickString(record, ['lastHeartbeatAt', 'lastSeenAt'], '') || undefined,
    averageLatencyMs: toFiniteNumber(pickValue(record, ['averageLatencyMs', 'latencyMs']) as number | string | null | undefined),
    activeTaskCount: toFiniteNumber(pickValue(record, ['activeTaskCount', 'activeTasks']) as number | string | null | undefined),
    maxConcurrentTasks: toFiniteNumber(pickValue(record, ['maxConcurrentTasks']) as number | string | null | undefined)
  };
}

function normalizeAgentError(raw: unknown, fallbackAgentId = '-', index = 0): AgentErrorLog {
  const record = isRecord(raw) ? raw : {};
  return {
    logId: pickString(record, ['logId', 'id'], `${fallbackAgentId}-error-${index + 1}`),
    agentId: pickString(record, ['agentId'], fallbackAgentId),
    timestamp: pickString(record, ['timestamp', 'time', 'createdAt'], new Date().toISOString()),
    level: normalizeEnum(pickValue(record, ['level', 'severity']), ['WARN', 'ERROR'] as const, 'ERROR'),
    message: pickString(record, ['message', 'msg', 'description'], '-'),
    traceId: pickString(record, ['traceId'], '') || undefined,
    taskId: pickString(record, ['taskId'], '') || undefined,
    detail: pickValue(record, ['detail', 'payload', 'data'])
  };
}

function normalizeAgentDetail(raw: unknown): AgentDetail {
  const record = isRecord(raw) ? raw : {};
  const base = normalizeAgentInfo(raw);
  const labels = pickArray(record, ['labels', 'tags']).map(String);
  const capabilities = pickArray(record, ['capabilities', 'capabilityList']).map((capability, index) => normalizeAgentCapability(capability, index));
  const runtime = normalizeAgentRuntime(pickValue(record, ['runtime', 'runtimeInfo']) ?? record);
  const recentErrors = pickArray(record, ['recentErrors', 'errors', 'errorLogs']).map((error, index) => normalizeAgentError(error, base.agentId, index));

  return {
    ...base,
    displayName: pickString(record, ['displayName', 'name'], '') || undefined,
    description: pickString(record, ['description'], '') || undefined,
    labels,
    capabilities,
    runtime,
    recentErrors
  };
}

function normalizeGatewayEventStatus(value: unknown): GatewayEventRecord['status'] {
  return normalizeEnum(value, ['RECEIVED', 'ROUTED', 'FAILED', 'UNKNOWN'] as const, 'UNKNOWN');
}

function normalizeGatewayTaskStatus(value: unknown): GatewayTaskRecord['status'] {
  return normalizeEnum(value, ['CREATED', 'WAITING_APPROVAL', 'PENDING', 'ASSIGNED', 'DISPATCH_REQUESTED', 'DISPATCHED', 'ACKED', 'PROCESSING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT', 'REASSIGNING'] as const, 'PENDING');
}

function normalizeTraceStepStatus(value: unknown): TraceDetail['steps'][number]['status'] {
  return normalizeEnum(value, ['SUCCESS', 'RUNNING', 'FAILED', 'SKIPPED', 'WAITING'] as const, 'WAITING');
}

function normalizeTraceActorType(value: unknown): TraceDetail['steps'][number]['actorType'] {
  return normalizeEnum(value, ['SOURCE_SYSTEM', 'GATEWAY', 'ROUTER', 'CLUSTER', 'AGENT', 'ADAPTER', 'UNKNOWN'] as const, 'UNKNOWN');
}

export function normalizeGatewayEventRecord(raw: unknown): GatewayEventRecord {
  const record = isRecord(raw) ? raw : {};
  const eventId = pickString(record, ['eventId', 'id', 'event_id']);
  const traceId = pickString(record, ['traceId', 'trace_id', 'correlationId'], eventId);

  return {
    eventId,
    traceId,
    sourceSystem: pickString(record, ['sourceSystem', 'source', 'source_system'], '-'),
    eventType: pickString(record, ['eventType', 'type', 'messageType', 'event_type'], '-'),
    status: normalizeGatewayEventStatus(pickValue(record, ['status', 'state'])),
    receivedAt: pickString(record, ['receivedAt', 'createdAt', 'timestamp', 'time'], new Date().toISOString()),
    routedAt: pickString(record, ['routedAt', 'routeAt'], '') || undefined,
    failedAt: pickString(record, ['failedAt', 'failureAt'], '') || undefined,
    message: pickString(record, ['message', 'reason', 'description'], '') || undefined,
    payload: pickValue(record, ['payload', 'body', 'data', 'requestPayload'])
  };
}

export function normalizeGatewayTaskRecord(raw: unknown, fallbackNodeId?: string): GatewayTaskRecord {
  const record = isRecord(raw) ? raw : {};
  const taskId = pickString(record, ['taskId', 'id', 'task_id']);
  const traceId = pickString(record, ['traceId', 'trace_id', 'correlationId'], taskId);
  const ownerNodeId = pickString(record, ['ownerNodeId', 'owningNodeId', 'ownerGatewayNodeId', 'acceptedNodeId', 'gatewayNodeId', 'nodeId', 'node_id'], fallbackNodeId ?? '') || undefined;
  const assignedNodeId = pickString(record, ['assignedNodeId', 'targetNodeId', 'dispatchNodeId'], ownerNodeId ?? '') || ownerNodeId;

  return {
    taskId,
    traceId,
    status: normalizeGatewayTaskStatus(pickValue(record, ['status', 'state'])),
    assignedAgentId: pickString(record, ['assignedAgentId', 'agentId', 'agent_id'], '') || undefined,
    ownerNodeId,
    assignedNodeId,
    createdAt: pickString(record, ['createdAt', 'timestamp', 'time'], new Date().toISOString()),
    assignedAt: pickString(record, ['assignedAt'], '') || undefined,
    startedAt: pickString(record, ['startedAt', 'startAt'], '') || undefined,
    completedAt: pickString(record, ['completedAt', 'finishedAt', 'endAt'], '') || undefined,
    failedAt: pickString(record, ['failedAt', 'failureAt'], '') || undefined,
    durationMs: toFiniteNumber(pickValue(record, ['durationMs', 'elapsedMs']) as number | string | null | undefined),
    retryCount: pickNumber(record, ['retryCount', 'retries', 'attemptCount']),
    failureReason: pickString(record, ['failureReason', 'errorMessage', 'error', 'reason'], '') || undefined,
    requestPayload: pickValue(record, ['requestPayload', 'request', 'inputPayload', 'input']),
    responsePayload: pickValue(record, ['responsePayload', 'response', 'outputPayload', 'output'])
  };
}

function normalizeTraceStep(raw: unknown, fallbackTraceId: string, index: number): TraceDetail['steps'][number] {
  const record = isRecord(raw) ? raw : {};
  return {
    stepId: pickString(record, ['stepId', 'id'], `${fallbackTraceId}-step-${index + 1}`),
    traceId: pickString(record, ['traceId', 'trace_id'], fallbackTraceId),
    stage: pickString(record, ['stage', 'stepType', 'type', 'name'], `STEP_${index + 1}`),
    status: normalizeTraceStepStatus(pickValue(record, ['status', 'state'])),
    actorType: normalizeTraceActorType(pickValue(record, ['actorType', 'actor', 'sourceType'])),
    actorId: pickString(record, ['actorId', 'nodeId', 'agentId', 'sourceId'], '') || undefined,
    timestamp: pickString(record, ['timestamp', 'time', 'createdAt'], new Date().toISOString()),
    durationMs: toFiniteNumber(pickValue(record, ['durationMs', 'elapsedMs']) as number | string | null | undefined),
    message: pickString(record, ['message', 'description'], '') || undefined,
    payload: pickValue(record, ['payload', 'data'])
  };
}

function normalizeTraceDetail(raw: unknown, fallbackTraceId = '-'): TraceDetail {
  const record = isRecord(raw) ? raw : {};
  const traceId = pickString(record, ['traceId', 'trace_id', 'correlationId'], fallbackTraceId);
  const rawSteps = Array.isArray(raw) ? raw : pickArray(record, ['steps', 'timeline', 'records', 'items', 'content', 'data']);
  const steps = rawSteps.map((step, index) => normalizeTraceStep(step, traceId, index));

  return {
    traceId,
    status: normalizeEnum(pickValue(record, ['status', 'state']), ['CREATED', 'WAITING_APPROVAL', 'PENDING', 'ASSIGNED', 'DISPATCH_REQUESTED', 'DISPATCHED', 'ACKED', 'PROCESSING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT', 'REASSIGNING', 'RECEIVED', 'ROUTED', 'PARTIAL_FAILED'] as const, 'PROCESSING'),
    sourceSystem: pickString(record, ['sourceSystem', 'source'], '') || undefined,
    eventId: pickString(record, ['eventId'], '') || undefined,
    taskId: pickString(record, ['taskId'], '') || undefined,
    startedAt: pickString(record, ['startedAt', 'createdAt', 'timestamp'], new Date().toISOString()),
    endedAt: pickString(record, ['endedAt', 'completedAt', 'finishedAt'], '') || undefined,
    durationMs: toFiniteNumber(pickValue(record, ['durationMs', 'elapsedMs']) as number | string | null | undefined),
    steps
  };
}

function normalizeRetryAttempt(raw: unknown, fallbackTaskId: string, index: number): RetryAttempt {
  const record = isRecord(raw) ? raw : {};
  return {
    attemptNo: pickNumber(record, ['attemptNo', 'attempt', 'retryNo', 'sequence'], index + 1),
    taskId: pickString(record, ['taskId', 'id'], fallbackTaskId),
    requestedAt: pickString(record, ['requestedAt', 'createdAt', 'timestamp'], new Date().toISOString()),
    requestedBy: pickString(record, ['requestedBy', 'userId', 'operator'], '') || undefined,
    status: normalizeGatewayTaskStatus(pickValue(record, ['status', 'state'])),
    message: pickString(record, ['message', 'reason', 'description'], '') || undefined
  };
}

function normalizeTaskLog(raw: unknown, fallbackTaskId: string, index: number): TaskLogRecord {
  const record = isRecord(raw) ? raw : {};
  return {
    logId: pickString(record, ['logId', 'id'], `${fallbackTaskId}-log-${index + 1}`),
    taskId: pickString(record, ['taskId'], fallbackTaskId),
    timestamp: pickString(record, ['timestamp', 'time', 'createdAt'], new Date().toISOString()),
    level: normalizeEnum(pickValue(record, ['level', 'severity']), ['INFO', 'WARN', 'ERROR'] as const, 'INFO'),
    message: pickString(record, ['message', 'msg', 'description'], '-'),
    payload: pickValue(record, ['payload', 'data', 'detail'])
  };
}

function normalizeRoutingDecision(raw: unknown): GatewayEventDetail['routingDecision'] | undefined {
  if (!isRecord(raw)) return undefined;
  const reason = pickString(raw, ['reason', 'message', 'description', 'decisionReason'], '') || undefined;
  const matchedSkills = pickArray(raw, ['matchedSkills', 'matchedSkillCodes', 'skillMatched', 'skillMatches']).map(String);
  const missingSkillRequirements = pickArray(raw, ['missingSkillRequirements', 'skillMissing', 'missingSkillCodes']).map(String);
  const inferredSkillAware = Boolean(reason?.includes('skill=') || reason?.includes('skillPenalty='));
  return {
    strategy: pickString(raw, ['strategy', 'routingStrategy', 'type', 'routingPolicy'], '-'),
    selectedAgentId: pickString(raw, ['selectedAgentId', 'agentId'], '') || undefined,
    selectedNodeId: pickString(raw, ['selectedNodeId', 'nodeId', 'selectedGatewayNodeId'], '') || undefined,
    reason,
    score: toFiniteNumber(pickValue(raw, ['score', 'weight', 'selectedScore']) as number | string | null | undefined),
    skillAware: pickBoolean(raw, ['skillAware', 'skillAwareEnabled'], inferredSkillAware),
    skillEligible: pickBoolean(raw, ['skillEligible', 'skillContractEligible'], !missingSkillRequirements.length && matchedSkills.length > 0),
    matchedSkills,
    missingSkillRequirements
  };
}

function normalizeGatewayEventDetail(raw: unknown): GatewayEventDetail {
  const record = isRecord(raw) ? raw : {};
  const base = normalizeGatewayEventRecord(raw);
  const relatedTasks = pickArray(record, ['relatedTasks', 'tasks', 'taskList', 'children']).map((task) => normalizeGatewayTaskRecord(task));
  const rawTrace = pickValue(record, ['trace', 'traceDetail', 'timeline']);
  const trace = normalizeTraceDetail(rawTrace ?? {}, base.traceId);
  const rawRoutingDecision = pickValue(record, ['routingDecision', 'routing', 'decision']);

  return {
    ...base,
    routingDecision: normalizeRoutingDecision(rawRoutingDecision),
    relatedTasks,
    trace
  };
}

export function normalizeGatewayTaskDetail(raw: unknown): GatewayTaskDetail {
  const record = isRecord(raw) ? raw : {};
  const base = normalizeGatewayTaskRecord(raw);
  const rawSourceEvent = pickValue(record, ['sourceEvent', 'event', 'gatewayEvent']);
  const rawTrace = pickValue(record, ['trace', 'traceDetail', 'timeline']);
  const attempts = pickArray(record, ['attempts', 'retryAttempts', 'retryHistory', 'dispatchAttempts']).map((attempt, index) => normalizeRetryAttempt(attempt, base.taskId, index));
  const logs = pickArray(record, ['logs', 'taskLogs', 'logRecords']).map((log, index) => normalizeTaskLog(log, base.taskId, index));

  return {
    ...base,
    sourceEvent: rawSourceEvent === undefined ? undefined : normalizeGatewayEventRecord(rawSourceEvent),
    trace: normalizeTraceDetail(rawTrace ?? {}, base.traceId),
    attempts,
    logs
  };
}


function countRecords(value: unknown): number | undefined {
  if (Array.isArray(value)) return value.length;
  if (!isRecord(value)) return undefined;

  const list = toList(value as PageLike<unknown>);
  if (list.length > 0) return list.length;

  for (const key of ['nodes', 'peers', 'peerRelations', 'agents', 'tasks', 'events', 'items', 'content', 'records', 'rows', 'data']) {
    const candidate = value[key];
    if (Array.isArray(candidate)) return candidate.length;
  }

  for (const key of ['agentsByNode', 'tasksByNode', 'agentDetailsByNode', 'taskDetailsByNode', 'runtimeMetricsByNode']) {
    const byNode = value[key];
    if (!isRecord(byNode)) continue;
    const total = Object.values(byNode).reduce<number>((sum, nodeValue) => {
      if (Array.isArray(nodeValue)) return sum + nodeValue.length;
      if (isRecord(nodeValue)) return sum + toList(nodeValue as PageLike<unknown>).length;
      return sum + 1;
    }, 0);
    return total;
  }

  const topLevelNodeMapValues = Object.values(value).filter((entry) => Array.isArray(entry));
  if (topLevelNodeMapValues.length > 0) return topLevelNodeMapValues.reduce<number>((sum, entry) => sum + (Array.isArray(entry) ? entry.length : 0), 0);

  for (const key of ['totalNodes', 'totalAgents', 'totalTasks']) {
    const total = toFiniteNumber(value[key] as number | string | null | undefined);
    if (total !== undefined) return total;
  }

  return undefined;
}

async function probeApiEndpoint(id: string, label: string, path: string, scope: ApiDiagnosticProbe['scope']): Promise<ApiDiagnosticProbe> {
  const startedAt = globalThis.performance?.now?.() ?? Date.now();

  try {
    const result = await apiGet<unknown>(path);
    const responseTimeMs = Math.round(((globalThis.performance?.now?.() ?? Date.now()) - startedAt) * 10) / 10;
    return {
      id,
      label,
      path,
      scope,
      status: 'AVAILABLE',
      httpStatus: 200,
      responseTimeMs,
      recordCount: countRecords(result),
      lastSuccessAt: new Date().toISOString()
    };
  } catch (error) {
    const responseTimeMs = Math.round(((globalThis.performance?.now?.() ?? Date.now()) - startedAt) * 10) / 10;
    if (isNotFoundOrUnsupportedApiError(error)) {
      return {
        id,
        label,
        path,
        scope,
        status: 'UNAVAILABLE',
        httpStatus: error.status,
        responseTimeMs,
        message: error.message
      };
    }

    return {
      id,
      label,
      path,
      scope,
      status: 'ERROR',
      httpStatus: error instanceof ApiError ? error.status : undefined,
      responseTimeMs,
      message: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

async function getApiDiagnosticsReport(): Promise<ApiDiagnosticsReport> {
  const env = getPublicEnv();
  const probes = await Promise.all([
    probeApiEndpoint('cluster-overview', 'Cluster overview', adminEndpoints.clusterOverviewV2, 'CLUSTER'),
    probeApiEndpoint('cluster-nodes', 'Cluster nodes', adminEndpoints.clusterNodesV2, 'CLUSTER'),
    probeApiEndpoint('cluster-runtime-metrics', 'Cluster runtime metrics', adminEndpoints.clusterRuntimeMetricsV2, 'CLUSTER'),
    probeApiEndpoint('cluster-peers', 'Cluster peer heartbeat', adminEndpoints.clusterPeersV2, 'CLUSTER'),
    probeApiEndpoint('cluster-agents', 'Cluster aggregated agents', adminEndpoints.clusterAgentsV2, 'CLUSTER'),
    probeApiEndpoint('admin-health', 'Local admin health', adminEndpoints.health, 'ADMIN'),
    probeApiEndpoint('local-events', 'Local event stream', adminEndpoints.events, 'LOCAL'),
    probeApiEndpoint('local-agents', 'Local agents fallback', adminEndpoints.agents, 'LOCAL')
  ]);

  return {
    generatedAt: new Date().toISOString(),
    adminApiBaseUrl: env.apiBaseUrl,
    adminWebSocketUrl: env.wsUrl,
    probes
  };
}

export const adminApi = {
  getHealth: () => apiGet<GatewayHealth>(adminEndpoints.health),
  getApiDiagnostics: getApiDiagnosticsReport,
  getMetrics: async () => normalizeGatewayMetrics(await apiGet<unknown>(adminEndpoints.metrics)),
  getClusterNodes: getClusterNodesNormalized,
  getClusterTopology: getClusterTopologyNormalized,
  getClusterNodeDetail: getClusterNodeDetailNormalized,
  getClusterPeers: async () => getClusterPeersRaw().then((result) => result ?? { generatedAt: new Date().toISOString(), peers: [] }),
  getClusterAgents: getClusterAgentsResponse,
  getClusterTasks: getClusterTasksResponse,
  getClusterNodeAgents: async (nodeId: string) => {
    const clusterAgents = await getClusterAgentsResponse();
    const agentsForNode = clusterAgents.byNode[nodeId];
    if (clusterAgents.scope === 'CLUSTER' || agentsForNode !== undefined) {
      return validateArrayContract('AgentInfo', agentsForNode ?? [], validateAgentInfo);
    }

    const localNodeAgents = await tryApiGetList<unknown>(adminEndpoints.clusterNodeAgents(nodeId));
    if (localNodeAgents !== undefined) {
      return validateArrayContract('AgentInfo', localNodeAgents.map((agent) => normalizeAgentInfo(agent, nodeId)), validateAgentInfo);
    }

    return validateArrayContract('AgentInfo', clusterAgents.items.filter((agent) => agent.nodeId === nodeId), validateAgentInfo);
  },
  getClusterNodeTasks: async (nodeId: string) => {
    const clusterTasks = await getClusterTasksResponse();
    const tasksForNode = clusterTasks.byNode[nodeId];
    if (tasksForNode !== undefined) {
      return validateArrayContract('GatewayTaskRecord', tasksForNode, validateGatewayTaskRecord);
    }

    const clusterAgents = await getClusterAgentsResponse().catch(() => undefined);
    const agentsForNode = new Set((clusterAgents?.byNode[nodeId] ?? [])
      .map((agent) => agent.agentId)
      .filter((agentId): agentId is string => Boolean(agentId)));

    return validateArrayContract(
      'GatewayTaskRecord',
      clusterTasks.items.filter((task) => taskBelongsToNode(task, nodeId, agentsForNode)),
      validateGatewayTaskRecord
    );
  },
  drainClusterNode: (nodeId: string) => apiPost<CommandResult>(adminEndpoints.clusterNodeDrain(nodeId)),
  resumeClusterNode: (nodeId: string) => apiPost<CommandResult>(adminEndpoints.clusterNodeResume(nodeId)),
  getAgents: async () => validateArrayContract('AgentInfo', (await getClusterAgentsResponse()).items, validateAgentInfo),
  getAgentsWithScope: getClusterAgentsResponse,
  getAgentDetail: async (agentId: string) => normalizeAgentDetail(await apiGet<unknown>(adminEndpoints.agentDetail(agentId))),
  getAgentTasks: async (agentId: string) => {
    const tasks = (await getClusterTasksResponse()).items.filter((task) => task.assignedAgentId === agentId);
    return validateArrayContract('GatewayTaskRecord', tasks, validateGatewayTaskRecord);
  },
  getAgentErrors: async (agentId: string) => {
    const tasks = (await getClusterTasksResponse()).items.filter((task) => task.assignedAgentId === agentId && task.failureReason);
    return tasks.map((task, index) => normalizeAgentError({
      logId: `${agentId}-task-error-${index + 1}`,
      agentId,
      timestamp: task.failedAt ?? task.completedAt ?? task.createdAt,
      level: 'ERROR',
      message: task.failureReason,
      traceId: task.traceId,
      taskId: task.taskId,
      detail: task.responsePayload
    }, agentId, index));
  },
  pingAgent: (agentId: string) => apiPost<CommandResult>(adminEndpoints.agentPing(agentId)),
  disconnectAgent: (agentId: string) => apiPost<CommandResult>(adminEndpoints.agentDisconnect(agentId)),
  getEvents: async () => validateArrayContract('GatewayEventRecord', (await apiGetList<unknown>(adminEndpoints.events)).map(normalizeGatewayEventRecord), validateGatewayEventRecord),
  getEventDetail: async (eventId: string) => normalizeGatewayEventDetail(await apiGet<unknown>(adminEndpoints.eventDetail(eventId))),
  getEventTrace: async (eventId: string) => validateContract('TraceDetail', normalizeTraceDetail(await apiGet<unknown>(adminEndpoints.traceDetail(eventId)), eventId), validateTraceDetail),
  getTasks: async () => validateArrayContract('GatewayTaskRecord', (await getClusterTasksResponse()).items, validateGatewayTaskRecord),
  getTasksWithScope: getClusterTasksResponse,
  getTaskDetail: async (taskId: string) => {
    const coreTask = await coreAdminApi.getTaskRuntimeView(taskId);
    return validateContract('GatewayTaskDetail', coreTaskRuntimeToGatewayTaskDetail(coreTask), validateGatewayTaskDetail);
  },
  getTaskTrace: async (taskId: string) => {
    const coreTask = await coreAdminApi.getTaskRuntimeView(taskId);
    return validateContract('TraceDetail', coreTaskRuntimeToTraceDetail(coreTask), validateTraceDetail);
  },
  getTraceDetail: async (traceId: string) => validateContract('TraceDetail', normalizeTraceDetail(await apiGet<unknown>(adminEndpoints.traceDetail(traceId)), traceId), validateTraceDetail),
  retryTask: (taskId: string) => coreAdminApi.retryTask(taskId),
  login: (payload: LoginRequest) => authApi.login(payload),
  me: () => authApi.me(),
  logout: () => authApi.logout()
};
