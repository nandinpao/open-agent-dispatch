import type { AgentSecurityEvent, CoreAgentProfile, CoreDashboardSnapshot, CoreTaskRuntimeView } from '@/lib/types/core';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { NettyCallbackRelayRuntime, NettyDeliveryRuntime, NettyRejectedConnection, NettyRuntimeSnapshot } from '@/lib/types/nettyRuntime';

export interface ControlPlaneSummary {
  openIncidents: number;
  pendingTasks: number;
  failedDispatches: number;
  deadLetterDispatches: number;
  pendingAgentApprovals: number;
  approvedAgents: number;
  suspendedOrRevokedAgents: number;
}

export interface RuntimePlaneSummary {
  onlineAgents: number;
  authorizedAgents: number;
  runtimeOnlyAgents: number;
  gatewayNodeCount: number;
  rejectedConnections: number;
  deliveryFailures: number;
  callbackRelayFailures: number;
  inflightDeliveries: number;
}

export interface TrustPlaneSummary {
  recentSecurityEvents: number;
  criticalSecurityEvents: number;
  connectedButNotApproved: number;
  connectedButDisabled: number;
  profileMissingRuntimePresent: number;
  approvedButOffline: number;
}

export interface DashboardSummaries {
  control: ControlPlaneSummary;
  runtime: RuntimePlaneSummary;
  trust: TrustPlaneSummary;
}

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value as T[] : [];
}

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function getPath(value: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((current, segment) => {
    if (!isRecord(current)) return undefined;
    return current[segment];
  }, value);
}

function firstNumber(value: unknown, paths: string[], fallback = 0): number {
  for (const path of paths) {
    const parsed = toNumber(getPath(value, path));
    if (parsed !== null) return parsed;
  }
  return fallback;
}

function countTasksByStatus(tasks: CoreTaskRuntimeView[], statuses: string[]): number {
  const statusSet = new Set(statuses);
  return tasks.filter((task) => statusSet.has(String(task.status))).length;
}

function countProfilesByApprovalStatus(profiles: CoreAgentProfile[], statuses: string[]): number {
  const statusSet = new Set(statuses);
  return profiles.filter((profile) => statusSet.has(String(profile.approvalStatus))).length;
}

function countProfilesByRiskStatus(profiles: CoreAgentProfile[], statuses: string[]): number {
  const statusSet = new Set(statuses);
  return profiles.filter((profile) => profile.riskStatus && statusSet.has(String(profile.riskStatus))).length;
}

function countSecurityBySeverity(events: AgentSecurityEvent[], severities: string[]): number {
  const severitySet = new Set(severities);
  return events.filter((event) => event.severity && severitySet.has(String(event.severity))).length;
}

function countAgentsFromRuntime(snapshot: NettyRuntimeSnapshot | null, rows: AgentDashboardRow[]): number {
  const snapshotAgents = asArray<unknown>(snapshot?.agents);
  if (snapshotAgents.length > 0) return snapshotAgents.length;
  return rows.filter((row) => row.runtime).length;
}

function countGatewayNodes(snapshot: NettyRuntimeSnapshot | null): number {
  const candidates = [
    firstNumber(snapshot, ['gatewayNodeCount', 'nodeCount', 'cluster.gatewayNodeCount', 'cluster.nodeCount', 'metrics.gatewayNodeCount'], -1),
    asArray<unknown>(getPath(snapshot, 'cluster.nodes')).length,
    asArray<unknown>(getPath(snapshot, 'nodes')).length
  ];
  return candidates.find((value) => value >= 0) ?? 0;
}

export function buildDashboardSummaries(input: {
  coreSnapshot: CoreDashboardSnapshot | null;
  nettySnapshot: NettyRuntimeSnapshot | null;
  agentRows: AgentDashboardRow[];
  profiles: CoreAgentProfile[];
  tasks: CoreTaskRuntimeView[];
  securityEvents: AgentSecurityEvent[];
  rejectedConnections: NettyRejectedConnection[];
  delivery: NettyDeliveryRuntime | null;
  callbackRelay: NettyCallbackRelayRuntime | null;
}): DashboardSummaries {
  const control: ControlPlaneSummary = {
    openIncidents: firstNumber(input.coreSnapshot, ['incidents.open', 'incidents.openCount', 'incident.openCount', 'controlPlane.openIncidents'], 0),
    pendingTasks: firstNumber(input.coreSnapshot, ['tasks.pending', 'tasks.pendingCount', 'controlPlane.pendingTasks'], countTasksByStatus(input.tasks, ['CREATED', 'WAITING_APPROVAL', 'ASSIGNED', 'DISPATCH_REQUESTED', 'PENDING'])),
    failedDispatches: firstNumber(input.coreSnapshot, ['dispatch.failed', 'dispatch.failedCount', 'dispatch.deliveryFailed', 'controlPlane.failedDispatches'], countTasksByStatus(input.tasks, ['FAILED', 'TIMEOUT'])),
    deadLetterDispatches: firstNumber(input.coreSnapshot, ['dispatch.deadLetter', 'dispatch.deadLetterCount', 'controlPlane.deadLetterDispatches'], countTasksByStatus(input.tasks, ['DEAD_LETTER'])),
    pendingAgentApprovals: firstNumber(input.coreSnapshot, ['agentGovernance.pendingApprovals', 'agentGovernance.pendingReview', 'agents.pendingApprovals', 'controlPlane.pendingAgentApprovals'], countProfilesByApprovalStatus(input.profiles, ['REGISTERED', 'PENDING_REVIEW'])),
    approvedAgents: firstNumber(input.coreSnapshot, ['agentGovernance.approvedAgents', 'agents.approved', 'controlPlane.approvedAgents'], countProfilesByApprovalStatus(input.profiles, ['APPROVED'])),
    suspendedOrRevokedAgents: firstNumber(input.coreSnapshot, ['agentGovernance.suspendedOrRevokedAgents', 'agents.suspendedOrRevoked', 'controlPlane.suspendedOrRevokedAgents'], countProfilesByApprovalStatus(input.profiles, ['SUSPENDED', 'REVOKED']) + countProfilesByRiskStatus(input.profiles, ['SUSPENDED', 'REVOKED', 'QUARANTINED', 'COMPROMISED']))
  };

  const runtime: RuntimePlaneSummary = {
    onlineAgents: input.agentRows.filter((row) => row.runtime?.connected || row.connected).length,
    authorizedAgents: input.agentRows.filter((row) => row.runtime?.authorizationState === 'AUTHORIZED').length,
    runtimeOnlyAgents: input.agentRows.filter((row) => row.source.profile === 'MISSING' && row.source.runtime === 'NETTY').length,
    gatewayNodeCount: countGatewayNodes(input.nettySnapshot),
    rejectedConnections: firstNumber(input.nettySnapshot, ['rejectedConnectionsCount', 'metrics.rejectedConnections', 'security.rejectedConnections'], input.rejectedConnections.length),
    deliveryFailures: firstNumber(input.delivery, ['failedCount', 'failureCount', 'failed'], firstNumber(input.nettySnapshot, ['delivery.failedCount', 'delivery.failureCount'], 0)),
    callbackRelayFailures: firstNumber(input.callbackRelay, ['failedCount', 'failureCount', 'failed'], firstNumber(input.nettySnapshot, ['callbackRelay.failedCount', 'callbackRelay.failureCount'], 0)),
    inflightDeliveries: firstNumber(input.delivery, ['inFlightCount', 'inflightCount', 'pendingCount'], firstNumber(input.nettySnapshot, ['delivery.inFlightCount', 'delivery.inflightCount'], 0))
  };

  if (runtime.onlineAgents === 0) {
    runtime.onlineAgents = countAgentsFromRuntime(input.nettySnapshot, input.agentRows);
  }

  const trust: TrustPlaneSummary = {
    recentSecurityEvents: firstNumber(input.coreSnapshot, ['security.recentCount', 'security.eventCount', 'security.total'], input.securityEvents.length),
    criticalSecurityEvents: firstNumber(input.coreSnapshot, ['security.criticalCount', 'security.highRiskCount'], countSecurityBySeverity(input.securityEvents, ['ERROR', 'CRITICAL'])),
    connectedButNotApproved: input.agentRows.filter((row) => row.runtime?.connected && row.profile && row.profile.approvalStatus !== 'APPROVED').length,
    connectedButDisabled: input.agentRows.filter((row) => row.runtime?.connected && row.profile && !row.profile.enabled).length,
    profileMissingRuntimePresent: input.agentRows.filter((row) => row.source.profile === 'MISSING' && row.source.runtime === 'NETTY').length,
    approvedButOffline: input.agentRows.filter((row) => row.profile?.approvalStatus === 'APPROVED' && row.profile.enabled && !row.runtime?.connected).length
  };

  return { control, runtime, trust };
}
