import type { GatewayTaskRecord } from '@/lib/types/admin';
import type { CoreTaskRuntimeView } from '@/lib/types/core';

export type NodeTaskCorrelationSource = 'NETTY_NODE' | 'TELEMETRY_MISSING';

export interface NodeTaskCorrelationResult {
  tasks: GatewayTaskRecord[];
  source: NodeTaskCorrelationSource;
  reason: string;
  correlationPending: boolean;
  coreRecentTasksCount: number;
  agentOwnershipHintCount: number;
  telemetryMissing: boolean;
  truthSource: 'GATEWAY_RUNTIME_DIAGNOSTICS';
}

export interface CorrelateNodeTasksInput {
  nodeId: string;
  nettyTasks: GatewayTaskRecord[];
  coreTasks: CoreTaskRuntimeView[];
  nodeCount: number;
  agentIds?: string[];
}


function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function pickString(record: Record<string, unknown>, keys: string[], fallback = ''): string {
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && String(value).trim() !== '') return String(value);
  }
  return fallback;
}

function pickNumber(record: Record<string, unknown>, keys: string[], fallback = 0): number {
  for (const key of keys) {
    const value = record[key];
    const parsed = typeof value === 'number' ? value : Number(String(value ?? ''));
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function deliveryStatusToGatewayTaskStatus(status: string): GatewayTaskRecord['status'] {
  const normalized = status.toUpperCase();
  if (normalized === 'DELIVERED') return 'DISPATCHED';
  if (normalized.includes('TIMEOUT')) return 'TIMEOUT';
  if (normalized.includes('FAILED') || normalized.includes('INVALID') || normalized.includes('NOT_')) return 'FAILED';
  return 'DISPATCH_REQUESTED';
}

function runtimeDeliveryRecords(runtimeDelivery: unknown): unknown[] {
  if (!isRecord(runtimeDelivery)) return [];
  const history = isRecord(runtimeDelivery.history) ? runtimeDelivery.history : {};
  for (const value of [history.records, runtimeDelivery.records, runtimeDelivery.recentDeliveries, runtimeDelivery.items, runtimeDelivery.data]) {
    if (Array.isArray(value)) return value;
  }
  return [];
}

export function gatewayTasksFromDeliveryRuntime(runtimeDelivery: unknown, nodeId: string): GatewayTaskRecord[] {
  return runtimeDeliveryRecords(runtimeDelivery)
    .filter(isRecord)
    .filter((record) => {
      const gatewayNodeId = pickString(record, ['gatewayNodeId', 'nodeId', 'ownerNodeId'], nodeId);
      return !gatewayNodeId || gatewayNodeId === nodeId;
    })
    .filter((record) => pickString(record, ['taskId', 'task_id', 'id']) !== '')
    .map((record) => {
      const taskId = pickString(record, ['taskId', 'task_id', 'id']);
      const gatewayNodeId = pickString(record, ['gatewayNodeId', 'nodeId', 'ownerNodeId'], nodeId);
      const dispatchRequestId = pickString(record, ['dispatchRequestId', 'dispatchId', 'requestId']);
      const assignmentId = pickString(record, ['assignmentId']);
      return {
        taskId,
        traceId: pickString(record, ['traceId', 'correlationId', 'commandId', 'attemptId'], taskId),
        status: deliveryStatusToGatewayTaskStatus(pickString(record, ['deliveryStatus', 'status', 'state'], 'DISPATCH_REQUESTED')),
        assignedAgentId: pickString(record, ['agentId', 'assignedAgentId']) || undefined,
        ownerNodeId: gatewayNodeId || undefined,
        assignedNodeId: gatewayNodeId || undefined,
        createdAt: pickString(record, ['requestedAt', 'createdAt', 'timestamp'], new Date().toISOString()),
        assignedAt: pickString(record, ['requestedAt']) || undefined,
        completedAt: pickString(record, ['completedAt']) || undefined,
        durationMs: pickNumber(record, ['durationMillis', 'durationMs'], 0),
        retryCount: Math.max(0, pickNumber(record, ['attemptNo'], 1) - 1),
        failureReason: pickString(record, ['message', 'failureReason', 'errorMessage']) || undefined,
        requestPayload: {
          dispatchRequestId,
          assignmentId,
          commandId: pickString(record, ['commandId']),
          messageType: pickString(record, ['messageType'])
        }
      } satisfies GatewayTaskRecord;
    });
}

function uniqueTasks(tasks: GatewayTaskRecord[]): GatewayTaskRecord[] {
  const seen = new Set<string>();
  const unique: GatewayTaskRecord[] = [];
  for (const task of tasks) {
    const key = task.taskId || task.traceId;
    if (!key || seen.has(key)) continue;
    seen.add(key);
    unique.push(task);
  }
  return unique;
}

function taskTime(task: GatewayTaskRecord): number {
  const value = task.createdAt ? Date.parse(task.createdAt) : Number.NaN;
  return Number.isFinite(value) ? value : 0;
}

function sortRecent(tasks: GatewayTaskRecord[]): GatewayTaskRecord[] {
  return [...tasks].sort((left, right) => taskTime(right) - taskTime(left));
}

export function correlateGatewayNodeTasks(input: CorrelateNodeTasksInput): NodeTaskCorrelationResult {
  const nettyTasks = sortRecent(uniqueTasks(input.nettyTasks));
  const coreRecentTasksCount = input.coreTasks.length;
  const agentOwnershipHintCount = (input.agentIds ?? []).filter(Boolean).length;
  if (nettyTasks.length > 0) {
    return {
      tasks: nettyTasks,
      source: 'NETTY_NODE',
      reason: 'Gateway node runtime telemetry is available. These rows are diagnostics from this node, not the authoritative task/callback state.',
      correlationPending: false,
      coreRecentTasksCount,
      agentOwnershipHintCount,
      telemetryMissing: false,
      truthSource: 'GATEWAY_RUNTIME_DIAGNOSTICS'
    };
  }

  const topologyLabel = input.nodeCount <= 1 ? 'single-node' : 'cluster';
  return {
    tasks: [],
    source: 'TELEMETRY_MISSING',
    reason: `No gateway delivery telemetry has been reported by ${input.nodeId} in ${topologyLabel} mode. Core may still have tasks; use Task Detail / Dispatch Ledger / Callback Inbox as the authoritative source.`,
    correlationPending: coreRecentTasksCount > 0,
    coreRecentTasksCount,
    agentOwnershipHintCount,
    telemetryMissing: true,
    truthSource: 'GATEWAY_RUNTIME_DIAGNOSTICS'
  };
}

export function nodeTaskCorrelationLabel(source: NodeTaskCorrelationSource): string {
  if (source === 'NETTY_NODE') return 'Gateway runtime diagnostics';
  return 'Gateway telemetry missing';
}

export function gatewayTelemetryMissingEmptyText(result?: NodeTaskCorrelationResult): string {
  const coreHint = result?.coreRecentTasksCount
    ? `Core currently has ${result.coreRecentTasksCount} recent task(s), but those records must not be mixed into a Gateway node diagnostics table.`
    : 'Core may still have task history, but this node has not reported delivery telemetry.';
  return `${coreHint} Task truth belongs to Core Task Detail, Dispatch Attempt Ledger, and the durable Callback Inbox; Gateway Node Detail only shows live runtime diagnostics.`;
}
