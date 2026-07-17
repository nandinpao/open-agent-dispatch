import type { ClusterNodeMetrics, GatewayMetrics } from '@/lib/types/admin';
import { toFiniteNumber } from '@/lib/utils/format';

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function pickValue(record: Record<string, unknown>, keys: string[]): unknown {
  for (const key of keys) {
    if (record[key] !== undefined && record[key] !== null) return record[key];
  }
  return undefined;
}

export function pickString(record: Record<string, unknown>, keys: string[], fallback = '-'): string {
  const value = pickValue(record, keys);
  if (value === undefined || value === null || value === '') return fallback;
  return String(value);
}

export function pickNumber(record: Record<string, unknown>, keys: string[], fallback = 0): number {
  const numberValue = toFiniteNumber(pickValue(record, keys) as number | string | null | undefined);
  return numberValue ?? fallback;
}

export function normalizePercentValue(value: unknown): number {
  const numberValue = toFiniteNumber(value as number | string | null | undefined);
  if (numberValue === undefined) return 0;

  // 後端可能回 0.35，也可能回 35 或 '35%'; 統一轉成百分比數字。
  if (numberValue >= 0 && numberValue <= 1) return numberValue * 100;
  return numberValue;
}

function nestedMetricValue(record: Record<string, unknown>, objectKey: string, keys: string[]): unknown {
  const nested = record[objectKey];
  if (!isRecord(nested)) return undefined;
  return pickValue(nested, keys);
}

function normalizeMemoryMb(value: unknown): number {
  const numberValue = toFiniteNumber(value as number | string | null | undefined);
  if (numberValue === undefined) return 0;

  // 若後端回 byte 值，轉成 MB。低於 1,000,000 視為已是 MB。
  if (numberValue > 1_000_000) return Math.round((numberValue / 1024 / 1024) * 10) / 10;
  return numberValue;
}

export function normalizeGatewayMetrics(raw: unknown): GatewayMetrics {
  const record = isRecord(raw) ? raw : {};
  const cpuValue = pickValue(record, ['cpuUsagePercent', 'cpuPercent', 'cpuUsage', 'cpu', 'processCpuLoad', 'systemCpuLoad']);

  return {
    nodeId: pickString(record, ['nodeId', 'node', 'gatewayNodeId'], '-'),
    cpuUsagePercent: normalizePercentValue(cpuValue),
    memoryUsedMb: normalizeMemoryMb(pickValue(record, ['memoryUsedMb', 'memoryUsedMB', 'usedMemoryMb', 'usedMemoryMB', 'usedMb', 'memoryUsed', 'usedMemory', 'heapUsedMb', 'heapUsed', 'memoryUsedBytes']) ?? nestedMetricValue(record, 'memory', ['usedMb', 'used', 'usedBytes']) ?? nestedMetricValue(record, 'heap', ['usedMb', 'used', 'usedBytes'])),
    memoryMaxMb: normalizeMemoryMb(pickValue(record, ['memoryMaxMb', 'memoryMaxMB', 'maxMemoryMb', 'maxMemoryMB', 'maxMb', 'memoryMax', 'maxMemory', 'heapMaxMb', 'heapMax', 'memoryMaxBytes']) ?? nestedMetricValue(record, 'memory', ['maxMb', 'max', 'maxBytes']) ?? nestedMetricValue(record, 'heap', ['maxMb', 'max', 'maxBytes'])),
    nettyEventLoopThreads: pickNumber(record, ['nettyEventLoopThreads', 'eventLoopThreads', 'eventLoopThreadCount']),
    workerThreads: pickNumber(record, ['workerThreads', 'workerThreadCount']),
    queueSize: pickNumber(record, ['queueSize', 'pendingQueueSize', 'taskQueueSize']),
    inboundEventsPerMinute: pickNumber(record, ['inboundEventsPerMinute', 'receivedEventsPerMinute', 'eventsInPerMinute']),
    routedEventsPerMinute: pickNumber(record, ['routedEventsPerMinute', 'eventsRoutedPerMinute']),
    failedEventsPerMinute: pickNumber(record, ['failedEventsPerMinute', 'eventsFailedPerMinute']),
    timestamp: pickString(record, ['timestamp', 'time', 'generatedAt'], new Date().toISOString())
  };
}

export function normalizeClusterNodeMetrics(raw: unknown): ClusterNodeMetrics {
  const record = isRecord(raw) ? raw : {};
  const cpuValue = pickValue(record, ['cpuUsagePercent', 'cpuPercent', 'cpuUsage', 'cpu', 'processCpuLoad', 'systemCpuLoad']);

  return {
    cpuUsagePercent: normalizePercentValue(cpuValue),
    memoryUsedMb: normalizeMemoryMb(pickValue(record, ['memoryUsedMb', 'memoryUsedMB', 'usedMemoryMb', 'usedMemoryMB', 'usedMb', 'memoryUsed', 'usedMemory', 'heapUsedMb', 'heapUsed', 'memoryUsedBytes']) ?? nestedMetricValue(record, 'memory', ['usedMb', 'used', 'usedBytes']) ?? nestedMetricValue(record, 'heap', ['usedMb', 'used', 'usedBytes'])),
    memoryMaxMb: normalizeMemoryMb(pickValue(record, ['memoryMaxMb', 'memoryMaxMB', 'maxMemoryMb', 'maxMemoryMB', 'maxMb', 'memoryMax', 'maxMemory', 'heapMaxMb', 'heapMax', 'memoryMaxBytes']) ?? nestedMetricValue(record, 'memory', ['maxMb', 'max', 'maxBytes']) ?? nestedMetricValue(record, 'heap', ['maxMb', 'max', 'maxBytes'])),
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

export function extractMetricsPayload(payload: unknown): unknown {
  if (!isRecord(payload)) return payload;
  if (isRecord(payload.metrics)) return payload.metrics;
  if (isRecord(payload.data)) return payload.data;
  if (isRecord(payload.payload)) return payload.payload;
  return payload;
}

export function readMetricsNodeId(event: { nodeId?: string; payload?: unknown }): string | undefined {
  if (event.nodeId) return event.nodeId;
  const payload = extractMetricsPayload(event.payload);
  if (!isRecord(payload)) return undefined;
  const value = pickValue(payload, ['nodeId', 'gatewayNodeId', 'node', 'id', 'name']);
  return value === undefined || value === null ? undefined : String(value);
}
