import type { AgentEnrollmentRequest } from '@/lib/types/core';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : undefined;
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;
}

function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function payload(runtime?: NettyAgentRuntime): Record<string, unknown> | undefined {
  return asRecord(runtime?.payload);
}

export function getAgentConnectionStatus(runtime?: NettyAgentRuntime): string {
  if (!runtime) return 'OFFLINE';
  return runtime.connected ? 'CONNECTED' : 'OFFLINE';
}

export function getAgentWorkloadStatus(runtime?: NettyAgentRuntime): string {
  if (!runtime) return 'OFFLINE';
  const body = payload(runtime);
  const candidate =
    runtime.agentStatus ??
    stringValue(body?.agentStatus) ??
    stringValue(body?.status) ??
    stringValue(body?.state) ??
    stringValue(body?.workloadState) ??
    stringValue(body?.runtimeStatus);

  if (candidate) return candidate.toUpperCase();
  if (!runtime.connected) return 'OFFLINE';
  if ((runtime.activeTaskCount ?? 0) > 0 || runtime.currentTaskId) return 'BUSY';
  return 'IDLE';
}

export function getHeartbeatStatus(runtime?: NettyAgentRuntime, now: number = Date.now()): string {
  if (!runtime) return 'OFFLINE';
  const body = payload(runtime);
  const candidate = runtime.heartbeatStatus ?? stringValue(body?.heartbeatStatus) ?? stringValue(body?.heartbeatState);
  if (candidate) return candidate.toUpperCase();
  if (!runtime.connected) return 'OFFLINE';

  const lastHeartbeat = runtime.lastHeartbeatAt ?? runtime.lastSeenAt;
  if (!lastHeartbeat) return 'UNKNOWN';
  const heartbeatTime = Date.parse(lastHeartbeat);
  if (!Number.isFinite(heartbeatTime)) return 'UNKNOWN';
  const ageMs = Math.max(0, now - heartbeatTime);
  if (ageMs <= 60_000) return 'HEALTHY';
  if (ageMs <= 180_000) return 'STALE';
  return 'TIMEOUT';
}

export function getHeartbeatAgeMs(runtime?: NettyAgentRuntime, now: number = Date.now()): number | undefined {
  const lastHeartbeat = runtime?.lastHeartbeatAt ?? runtime?.lastSeenAt;
  if (!lastHeartbeat) return undefined;
  const heartbeatTime = Date.parse(lastHeartbeat);
  return Number.isFinite(heartbeatTime) ? Math.max(0, now - heartbeatTime) : undefined;
}

export function getRuntimeLatencyMs(runtime?: NettyAgentRuntime): number | undefined {
  const body = payload(runtime);
  return runtime?.latencyMs ?? numberValue(body?.latencyMs) ?? numberValue(body?.heartbeatLatencyMs) ?? numberValue(body?.averageLatencyMs);
}

export function hasRuntimeLatencyMetric(runtime?: NettyAgentRuntime): boolean {
  return getRuntimeLatencyMs(runtime) !== undefined;
}

export function getRuntimeLatencyLabel(runtime?: NettyAgentRuntime): string {
  const latencyMs = getRuntimeLatencyMs(runtime);
  if (latencyMs === undefined) return 'not reported';
  if (latencyMs < 1000) return `${Math.round(latencyMs)} ms`;
  return `${(latencyMs / 1000).toFixed(1)} s`;
}

export function getRuntimeCapacityLabel(runtime?: NettyAgentRuntime): string {
  if (!runtime) return '-';
  const active = runtime.activeTaskCount ?? 0;
  const max = runtime.maxConcurrentTasks;
  const slots = runtime.availableSlots;
  const utilization = runtime.capacityUtilization;
  const capacity = max !== undefined ? `${active}/${max}` : `${active}`;
  const parts = [`tasks ${capacity}`];
  if (slots !== undefined) parts.push(`slots ${slots}`);
  if (utilization !== undefined) parts.push(`util ${(utilization * 100).toFixed(0)}%`);
  return parts.join(' · ');
}

export function getRuntimeBacklogLabel(runtime?: NettyAgentRuntime): string {
  if (!runtime) return '-';
  const parts = [
    `outbox ${runtime.outboxPending ?? 0}/${runtime.outboxInFlight ?? 0}`,
    `recovery ${runtime.recoveryPendingAssignments ?? 0}`
  ];
  if (runtime.draining) parts.push('draining');
  return parts.join(' · ');
}

export function getRuntimeCapabilityLabel(runtime?: NettyAgentRuntime): string {
  if (!runtime) return '-';
  const plugin = [runtime.pluginName, runtime.pluginVersion].filter(Boolean).join('@');
  const revision = runtime.capabilityRevision ? `rev ${runtime.capabilityRevision}` : undefined;
  return [plugin || undefined, revision].filter(Boolean).join(' · ') || '-';
}

export function getSuccessfulConnectedAt(runtime?: NettyAgentRuntime): string | undefined {
  const body = payload(runtime);
  return runtime?.connectedAt ?? stringValue(body?.connectedAt) ?? stringValue(body?.firstConnectedAt) ?? stringValue(body?.acceptedAt);
}

export function getReviewedTimestamp(enrollment?: AgentEnrollmentRequest): { label: string; value?: string } {
  if (!enrollment) return { label: 'Review Time' };
  if (enrollment.status === 'APPROVED') return { label: 'Approved Time', value: enrollment.reviewedAt };
  if (enrollment.status === 'REJECTED') return { label: 'Rejected Time', value: enrollment.reviewedAt };
  return { label: 'Review Time', value: enrollment.reviewedAt };
}

export function getRowReviewTimestamp(row: AgentDashboardRow): { label: string; value?: string } {
  const reviewed = getReviewedTimestamp(row.enrollment);
  if (reviewed.value || row.enrollment) return reviewed;

  if (row.profile?.approvalStatus === 'APPROVED') {
    return { label: 'Approved Time', value: row.profile.updatedAt ?? row.profile.createdAt };
  }

  if (row.profile?.approvalStatus === 'REJECTED') {
    return { label: 'Rejected Time', value: row.profile.updatedAt ?? row.profile.createdAt };
  }

  return reviewed;
}
