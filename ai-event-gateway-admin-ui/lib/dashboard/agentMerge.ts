import type { CoreAgentProfile } from '@/lib/types/core';
import type { AgentDashboardRow, AgentRuntimeSummary } from '@/lib/types/dashboard';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';

function pickNonBlankString(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value === 'string' && value.trim() !== '') return value.trim();
    if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  }
  return undefined;
}

function runtimeAgentKey(runtime: NettyAgentRuntime): string | undefined {
  const record = runtime as NettyAgentRuntime & Record<string, unknown>;
  return pickNonBlankString(runtime.agentId, record.id, record.claimedAgentId, record.agentName);
}

function profileAgentKey(profile: CoreAgentProfile): string | undefined {
  const record = profile as CoreAgentProfile & Record<string, unknown>;
  return pickNonBlankString(profile.agentId, record.id, record.claimedAgentId, profile.agentName);
}

function runtimeNodeKey(runtime: NettyAgentRuntime): string {
  return pickNonBlankString(runtime.gatewayNodeId, runtime.nodeId, 'unknown') ?? 'unknown';
}

function runtimeSessionKey(runtime: NettyAgentRuntime): string {
  return pickNonBlankString(runtime.sessionId, runtime.connectionId, `${runtimeNodeKey(runtime)}:${runtime.agentId}`) ?? `${runtimeNodeKey(runtime)}:${runtime.agentId}`;
}

function sortAgentRows(rows: AgentDashboardRow[]): AgentDashboardRow[] {
  return rows.sort((left, right) => String(left.agentId ?? '').localeCompare(String(right.agentId ?? '')));
}

function sortRuntimes(runtimes: NettyAgentRuntime[]): NettyAgentRuntime[] {
  return [...runtimes].sort((left, right) => {
    const nodeCmp = runtimeNodeKey(left).localeCompare(runtimeNodeKey(right));
    if (nodeCmp !== 0) return nodeCmp;
    return runtimeSessionKey(left).localeCompare(runtimeSessionKey(right));
  });
}

function choosePrimaryRuntime(runtimes: NettyAgentRuntime[]): NettyAgentRuntime | undefined {
  return sortRuntimes(runtimes).sort((left, right) => {
    const connectedScore = Number(right.connected) - Number(left.connected);
    if (connectedScore !== 0) return connectedScore;
    const rightTime = Date.parse(right.lastHeartbeatAt ?? right.lastSeenAt ?? right.connectedAt ?? '') || 0;
    const leftTime = Date.parse(left.lastHeartbeatAt ?? left.lastSeenAt ?? left.connectedAt ?? '') || 0;
    return rightTime - leftTime;
  })[0];
}

export function summarizeRuntimes(runtimes: NettyAgentRuntime[] | undefined): AgentRuntimeSummary {
  const normalized = sortRuntimes(runtimes ?? []);
  const gatewayNodeIds = Array.from(new Set(normalized.map(runtimeNodeKey))).filter(Boolean);
  const connected = normalized.filter((runtime) => runtime.connected === true);
  const connectedGatewayNodeIds = Array.from(new Set(connected.map(runtimeNodeKey))).filter(Boolean);
  return {
    connectedCount: connected.length,
    sessionCount: normalized.length,
    gatewayNodeIds,
    connectedGatewayNodeIds,
    duplicateRuntimeDetected: connected.length > 1 || connectedGatewayNodeIds.length > 1
  };
}

export function mergeAgentDashboardRows(
  profiles: CoreAgentProfile[],
  runtimes: NettyAgentRuntime[]
): AgentDashboardRow[] {
  const runtimeByAgentId = new Map<string, NettyAgentRuntime[]>();

  runtimes.forEach((runtime) => {
    const agentId = runtimeAgentKey(runtime);
    if (!agentId) return;
    const list = runtimeByAgentId.get(agentId) ?? [];
    list.push(runtime);
    runtimeByAgentId.set(agentId, list);
  });

  const rows: AgentDashboardRow[] = [];

  profiles.forEach((profile) => {
    const agentId = profileAgentKey(profile);
    if (!agentId) return;
    const agentRuntimes = sortRuntimes(runtimeByAgentId.get(agentId) ?? []);
    const runtime = choosePrimaryRuntime(agentRuntimes);
    const runtimeSummary = summarizeRuntimes(agentRuntimes);
    runtimeByAgentId.delete(agentId);

    rows.push({
      agentId,
      profile,
      runtime,
      runtimes: agentRuntimes,
      runtimeSummary,
      approvalStatus: profile.approvalStatus,
      enabled: profile.enabled,
      riskStatus: profile.riskStatus,
      connected: runtimeSummary.connectedCount > 0,
      source: {
        profile: 'CORE',
        runtime: agentRuntimes.length > 0 ? 'NETTY' : 'MISSING'
      }
    });
  });

  runtimeByAgentId.forEach((agentRuntimes, agentId) => {
    const sorted = sortRuntimes(agentRuntimes);
    const runtime = choosePrimaryRuntime(sorted);
    const runtimeSummary = summarizeRuntimes(sorted);
    rows.push({
      agentId,
      runtime,
      runtimes: sorted,
      runtimeSummary,
      approvalStatus: undefined,
      enabled: undefined,
      riskStatus: undefined,
      connected: runtimeSummary.connectedCount > 0,
      source: {
        profile: 'MISSING',
        runtime: 'NETTY'
      }
    });
  });

  return sortAgentRows(rows);
}

export function deriveAgentRuntimeWarning(row: AgentDashboardRow): string | null {
  if (row.runtimeSummary?.duplicateRuntimeDetected) {
    return `Duplicate runtime sessions detected: ${row.runtimeSummary.connectedCount} connected sessions across ${row.runtimeSummary.connectedGatewayNodeIds.join(', ') || 'unknown nodes'}. Treat this as a security-sensitive split-brain state and disconnect all sessions before re-authorizing the Agent.`;
  }
  if (row.source.profile === 'MISSING' && row.source.runtime === 'NETTY') {
    return 'Runtime observed this Agent, but Core has no governance profile. Treat as ungoverned/high-risk until an enrollment is reviewed and approved.';
  }
  if (row.profile && row.runtime?.connected && row.profile.approvalStatus !== 'APPROVED') {
    return 'Agent 已連線，但 Core 審核狀態不是 APPROVED。請檢查授權流程或 runtime cache。';
  }
  if (row.profile && row.runtime?.connected && !row.profile.enabled) {
    return 'Agent 已連線，但 Core enabled=false。Netty 應斷線或拒絕後續 delivery。';
  }
  return null;
}
