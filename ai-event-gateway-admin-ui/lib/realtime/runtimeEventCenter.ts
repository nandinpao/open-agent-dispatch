import type { AdminEventType, AdminWebSocketEvent } from '@/lib/types/admin';

export type RuntimeEventCategory =
  | 'AGENT_AUTH'
  | 'AGENT_SESSION'
  | 'DELIVERY'
  | 'CALLBACK'
  | 'SECURITY'
  | 'TASK'
  | 'CLUSTER'
  | 'METRICS'
  | 'SYSTEM'
  | 'OTHER';

export type RuntimeEventSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';

export interface RuntimeEventDisplay {
  category: RuntimeEventCategory;
  severity: RuntimeEventSeverity;
  title: string;
  description: string;
  status: string;
  entityLabel: string;
}

export interface RuntimeEventCategorySummary {
  category: RuntimeEventCategory;
  label: string;
  count: number;
  errorCount: number;
  warningCount: number;
  latestAt: string | null;
}

export interface RuntimeEventCenterSummary {
  totalEvents: number;
  latestAt: string | null;
  agentAuthEvents: number;
  deniedAuthorizations: number;
  securityEvents: number;
  deliveryEvents: number;
  deliveryFailures: number;
  callbackEvents: number;
  callbackFailures: number;
  taskEvents: number;
  clusterEvents: number;
  metricsEvents: number;
  failedOrRiskEvents: number;
  categories: RuntimeEventCategorySummary[];
}

const categoryLabels: Record<RuntimeEventCategory, string> = {
  AGENT_AUTH: 'Agent authorization',
  AGENT_SESSION: 'Agent session',
  DELIVERY: 'Delivery',
  CALLBACK: 'Callback relay',
  SECURITY: 'Security',
  TASK: 'Task',
  CLUSTER: 'Cluster',
  METRICS: 'Metrics',
  SYSTEM: 'System',
  OTHER: 'Other'
};

const eventTitles: Partial<Record<AdminEventType, string>> = {
  AGENT_CONNECTED: 'Agent connected',
  AGENT_AUTHORIZED: 'Agent authorized by Core',
  AGENT_AUTHORIZATION_DENIED: 'Agent authorization denied',
  AGENT_DISCONNECTED: 'Agent disconnected',
  AGENT_STATUS_CHANGED: 'Agent status changed',
  DELIVERY_STARTED: 'Command delivery started',
  DELIVERY_SUCCEEDED: 'Command delivery succeeded',
  DELIVERY_FAILED: 'Command delivery failed',
  CALLBACK_RECEIVED: 'Callback received',
  CALLBACK_RELAYED: 'Callback relayed to Core',
  CALLBACK_RELAY_FAILED: 'Callback relay failed',
  SECURITY_CONNECTION_REJECTED: 'Unauthorized connection rejected',
  SECURITY_CREDENTIAL_REVOKED_ATTEMPT: 'Revoked credential connection attempt',
  TASK_CREATED: 'Task created',
  TASK_ASSIGNED: 'Task assigned',
  TASK_COMPLETED: 'Task completed',
  TASK_FAILED: 'Task failed',
  TASK_CANCELLED: 'Task cancelled',
  NODE_JOINED: 'Cluster node joined',
  NODE_LEFT: 'Cluster node left',
  NODE_HEARTBEAT_TIMEOUT: 'Cluster node heartbeat timeout',
  NODE_DRAINING: 'Cluster node draining',
  NODE_DRAINED: 'Cluster node drained',
  NODE_RESUMED: 'Cluster node resumed',
  NODE_METRICS_UPDATED: 'Node metrics updated',
  CLUSTER_METRICS_UPDATED: 'Cluster metrics updated',
  GATEWAY_METRICS_UPDATED: 'Gateway metrics updated',
  METRICS_UPDATED: 'Runtime metrics updated',
  SYSTEM_MESSAGE: 'System message'
};

function isFailureStatus(status: string | undefined): boolean {
  if (!status) return false;
  const normalized = status.toUpperCase();
  return normalized.includes('FAILED')
    || normalized.includes('ERROR')
    || normalized.includes('TIMEOUT')
    || normalized.includes('DENIED')
    || normalized.includes('REJECTED')
    || normalized.includes('REVOKED');
}

export function getRuntimeEventCategory(event: Pick<AdminWebSocketEvent, 'eventType'>): RuntimeEventCategory {
  if (event.eventType === 'AGENT_AUTHORIZED' || event.eventType === 'AGENT_AUTHORIZATION_DENIED') return 'AGENT_AUTH';
  if (event.eventType.startsWith('AGENT_')) return 'AGENT_SESSION';
  if (event.eventType.startsWith('DELIVERY_')) return 'DELIVERY';
  if (event.eventType.startsWith('CALLBACK_')) return 'CALLBACK';
  if (event.eventType.startsWith('SECURITY_')) return 'SECURITY';
  if (event.eventType.startsWith('TASK_')) return 'TASK';
  if (event.eventType.startsWith('NODE_') || event.eventType.startsWith('CLUSTER_')) return 'CLUSTER';
  if (event.eventType.includes('METRICS')) return 'METRICS';
  if (event.eventType === 'SYSTEM_MESSAGE') return 'SYSTEM';
  return 'OTHER';
}

export function getRuntimeEventSeverity(event: AdminWebSocketEvent): RuntimeEventSeverity {
  if (event.eventType.endsWith('FAILED')
    || event.eventType.includes('TIMEOUT')
    || event.eventType === 'SECURITY_CREDENTIAL_REVOKED_ATTEMPT'
    || isFailureStatus(event.status)) {
    return 'ERROR';
  }
  if (event.eventType === 'AGENT_AUTHORIZATION_DENIED'
    || event.eventType === 'SECURITY_CONNECTION_REJECTED'
    || event.eventType === 'AGENT_DISCONNECTED'
    || event.eventType === 'NODE_LEFT'
    || event.eventType === 'TASK_CANCELLED') {
    return 'WARNING';
  }
  if (event.eventType === 'AGENT_AUTHORIZED'
    || event.eventType === 'AGENT_CONNECTED'
    || event.eventType === 'DELIVERY_SUCCEEDED'
    || event.eventType === 'CALLBACK_RELAYED'
    || event.eventType === 'TASK_COMPLETED'
    || event.eventType === 'NODE_JOINED') {
    return 'SUCCESS';
  }
  return 'INFO';
}

export function isRuntimeRiskEvent(event: AdminWebSocketEvent): boolean {
  const severity = getRuntimeEventSeverity(event);
  return severity === 'ERROR' || severity === 'WARNING';
}

export function categoryLabel(category: RuntimeEventCategory): string {
  return categoryLabels[category];
}

export function getRuntimeEventDisplay(event: AdminWebSocketEvent): RuntimeEventDisplay {
  const category = getRuntimeEventCategory(event);
  const severity = getRuntimeEventSeverity(event);
  const entityParts = [
    event.nodeId ? `Node ${event.nodeId}` : null,
    event.agentId ? `Agent ${event.agentId}` : null,
    event.taskId ? `Task ${event.taskId}` : null,
    event.traceId ? `Trace ${event.traceId}` : null
  ].filter(Boolean);

  return {
    category,
    severity,
    title: eventTitles[event.eventType] ?? event.eventType,
    description: event.message ?? categoryLabels[category],
    status: event.status ?? severity,
    entityLabel: entityParts.length > 0 ? entityParts.join(' / ') : '-'
  };
}

function latestTimestamp(events: AdminWebSocketEvent[]): string | null {
  let latest: string | null = null;
  for (const event of events) {
    if (!latest || new Date(event.timestamp).getTime() > new Date(latest).getTime()) {
      latest = event.timestamp;
    }
  }
  return latest;
}

function buildCategorySummaries(events: AdminWebSocketEvent[]): RuntimeEventCategorySummary[] {
  const categories: RuntimeEventCategory[] = ['AGENT_AUTH', 'AGENT_SESSION', 'DELIVERY', 'CALLBACK', 'SECURITY', 'TASK', 'CLUSTER', 'METRICS', 'SYSTEM', 'OTHER'];

  return categories.map((category) => {
    const matching = events.filter((event) => getRuntimeEventCategory(event) === category);
    return {
      category,
      label: categoryLabels[category],
      count: matching.length,
      errorCount: matching.filter((event) => getRuntimeEventSeverity(event) === 'ERROR').length,
      warningCount: matching.filter((event) => getRuntimeEventSeverity(event) === 'WARNING').length,
      latestAt: latestTimestamp(matching)
    };
  });
}

export function buildRuntimeEventCenterSummary(events: AdminWebSocketEvent[]): RuntimeEventCenterSummary {
  return {
    totalEvents: events.length,
    latestAt: latestTimestamp(events),
    agentAuthEvents: events.filter((event) => getRuntimeEventCategory(event) === 'AGENT_AUTH').length,
    deniedAuthorizations: events.filter((event) => event.eventType === 'AGENT_AUTHORIZATION_DENIED').length,
    securityEvents: events.filter((event) => getRuntimeEventCategory(event) === 'SECURITY').length,
    deliveryEvents: events.filter((event) => getRuntimeEventCategory(event) === 'DELIVERY').length,
    deliveryFailures: events.filter((event) => event.eventType === 'DELIVERY_FAILED').length,
    callbackEvents: events.filter((event) => getRuntimeEventCategory(event) === 'CALLBACK').length,
    callbackFailures: events.filter((event) => event.eventType === 'CALLBACK_RELAY_FAILED').length,
    taskEvents: events.filter((event) => getRuntimeEventCategory(event) === 'TASK').length,
    clusterEvents: events.filter((event) => getRuntimeEventCategory(event) === 'CLUSTER').length,
    metricsEvents: events.filter((event) => getRuntimeEventCategory(event) === 'METRICS').length,
    failedOrRiskEvents: events.filter(isRuntimeRiskEvent).length,
    categories: buildCategorySummaries(events)
  };
}

export function sortRuntimeEventsByNewest(events: AdminWebSocketEvent[]): AdminWebSocketEvent[] {
  return [...events].sort((left, right) => new Date(right.timestamp).getTime() - new Date(left.timestamp).getTime());
}
