import type { CoreTaskRuntimeView } from '@/lib/types/core';
import type { NettyCallbackRelayRuntime, NettyDeliveryRuntime } from '@/lib/types/nettyRuntime';

export interface RuntimeAttemptSummary {
  status?: string;
  gatewayNodeId?: string;
  sessionId?: string;
  agentId?: string;
  occurredAt?: string;
  latencyMs?: number;
  reason?: string;
  payload?: unknown;
}

export interface TaskDispatchDashboardRow {
  task: CoreTaskRuntimeView;
  delivery?: RuntimeAttemptSummary;
  callbackRelay?: RuntimeAttemptSummary;
  source: {
    task: 'CORE';
    delivery: 'NETTY' | 'MISSING';
    callbackRelay: 'NETTY' | 'MISSING';
  };
}

export interface TaskDispatchRuntimeBundle {
  rows: TaskDispatchDashboardRow[];
  generatedAt: string;
  coreTaskCount: number;
  deliveryRuntimeAvailable: boolean;
  callbackRelayRuntimeAvailable: boolean;
  deliveryError?: string;
  callbackRelayError?: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function pickString(record: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'string' && value.trim()) return value;
    if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  }
  return undefined;
}

function pickNumber(record: Record<string, unknown>, keys: string[]): number | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value))) return Number(value);
  }
  return undefined;
}

function toAttemptList(runtime: NettyDeliveryRuntime | NettyCallbackRelayRuntime | undefined, keys: string[]): unknown[] {
  if (!runtime) return [];
  for (const key of keys) {
    const value = runtime[key];
    if (Array.isArray(value)) return value;
  }
  return [];
}

function nestedPayload(record: Record<string, unknown>): unknown {
  return record.payload ?? record.detail ?? record.data ?? record.raw ?? record;
}

function matchesTask(record: Record<string, unknown>, task: CoreTaskRuntimeView): boolean {
  const payload = isRecord(record.payload) ? record.payload : undefined;
  const candidates = [
    pickString(record, ['taskId', 'task_id', 'id']),
    pickString(record, ['dispatchRequestId', 'dispatch_request_id', 'requestId']),
    payload ? pickString(payload, ['taskId', 'task_id', 'id']) : undefined,
    payload ? pickString(payload, ['dispatchRequestId', 'dispatch_request_id', 'requestId']) : undefined
  ].filter(Boolean);

  return candidates.includes(task.taskId) || (!!task.dispatchRequestId && candidates.includes(task.dispatchRequestId));
}

function summarizeAttempt(raw: unknown): RuntimeAttemptSummary | undefined {
  if (!isRecord(raw)) return undefined;
  const payload = isRecord(raw.payload) ? raw.payload : undefined;
  return {
    status: pickString(raw, ['status', 'deliveryStatus', 'relayStatus', 'state']) ?? (payload ? pickString(payload, ['status', 'state']) : undefined),
    gatewayNodeId: pickString(raw, ['gatewayNodeId', 'nodeId', 'ownerNodeId']) ?? (payload ? pickString(payload, ['gatewayNodeId', 'nodeId']) : undefined),
    sessionId: pickString(raw, ['sessionId', 'connectionId']) ?? (payload ? pickString(payload, ['sessionId', 'connectionId']) : undefined),
    agentId: pickString(raw, ['agentId', 'assignedAgentId']) ?? (payload ? pickString(payload, ['agentId', 'assignedAgentId']) : undefined),
    occurredAt: pickString(raw, ['occurredAt', 'timestamp', 'createdAt', 'lastAttemptAt', 'completedAt']) ?? (payload ? pickString(payload, ['occurredAt', 'timestamp', 'createdAt']) : undefined),
    latencyMs: pickNumber(raw, ['latencyMs', 'durationMs', 'elapsedMs']) ?? (payload ? pickNumber(payload, ['latencyMs', 'durationMs', 'elapsedMs']) : undefined),
    reason: pickString(raw, ['reason', 'message', 'error', 'failureReason']) ?? (payload ? pickString(payload, ['reason', 'message', 'error', 'failureReason']) : undefined),
    payload: nestedPayload(raw)
  };
}

function newestMatchingAttempt(attempts: unknown[], task: CoreTaskRuntimeView): RuntimeAttemptSummary | undefined {
  const matched = attempts
    .filter(isRecord)
    .filter((record) => matchesTask(record, task))
    .map(summarizeAttempt)
    .filter((attempt): attempt is RuntimeAttemptSummary => attempt !== undefined);

  return matched.sort((left, right) => Date.parse(right.occurredAt ?? '') - Date.parse(left.occurredAt ?? ''))[0];
}

function taskTime(task: CoreTaskRuntimeView): number {
  return Date.parse(task.updatedAt ?? task.createdAt ?? '') || 0;
}

export function buildTaskDispatchRows(
  tasks: CoreTaskRuntimeView[],
  deliveryRuntime?: NettyDeliveryRuntime,
  callbackRelayRuntime?: NettyCallbackRelayRuntime
): TaskDispatchDashboardRow[] {
  const deliveryAttempts = toAttemptList(deliveryRuntime, ['recentDeliveries', 'recentAttempts', 'items', 'records', 'data']);
  const callbackAttempts = toAttemptList(callbackRelayRuntime, ['recentAttempts', 'recentRelays', 'items', 'records', 'data']);

  return tasks
    .slice()
    .sort((left, right) => taskTime(right) - taskTime(left))
    .map((task) => {
      const delivery = newestMatchingAttempt(deliveryAttempts, task);
      const callbackRelay = newestMatchingAttempt(callbackAttempts, task);
      return {
        task,
        delivery,
        callbackRelay,
        source: {
          task: 'CORE',
          delivery: delivery ? 'NETTY' : 'MISSING',
          callbackRelay: callbackRelay ? 'NETTY' : 'MISSING'
        }
      };
    });
}

export function createTaskDispatchRuntimeBundle(input: {
  tasks: CoreTaskRuntimeView[];
  deliveryRuntime?: NettyDeliveryRuntime;
  callbackRelayRuntime?: NettyCallbackRelayRuntime;
  deliveryError?: string;
  callbackRelayError?: string;
}): TaskDispatchRuntimeBundle {
  return {
    rows: buildTaskDispatchRows(input.tasks, input.deliveryRuntime, input.callbackRelayRuntime),
    generatedAt: new Date().toISOString(),
    coreTaskCount: input.tasks.length,
    deliveryRuntimeAvailable: input.deliveryRuntime !== undefined,
    callbackRelayRuntimeAvailable: input.callbackRelayRuntime !== undefined,
    deliveryError: input.deliveryError,
    callbackRelayError: input.callbackRelayError
  };
}
