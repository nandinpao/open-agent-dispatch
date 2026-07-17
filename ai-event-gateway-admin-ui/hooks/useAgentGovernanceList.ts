'use client';

import { useCallback } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import { runtimeToEnrollmentCandidate } from '@/lib/agents/enrollmentWorkflow';
import { normalizeEnrollmentStatus } from '@/lib/agents/governanceStatus';
import { mergeAgentDashboardRows } from '@/lib/dashboard/agentMerge';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { AgentEnrollmentRequest, CoreAgentProfile, CoreDispatchFlowView } from '@/lib/types/core';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';
import { usePollingResource } from '@/hooks/usePollingResource';

interface SafeLoadResult<T> {
  data: T | null;
  error: string | null;
}

async function safeLoad<T>(loader: () => Promise<T>): Promise<SafeLoadResult<T>> {
  try {
    return { data: await loader(), error: null };
  } catch (error) {
    return { data: null, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

function enrollmentAgentKey(enrollment: AgentEnrollmentRequest): string | undefined {
  return enrollment.claimedAgentId ?? enrollment.agentName;
}

function enrollmentTimestamp(enrollment: AgentEnrollmentRequest): number {
  const value = enrollment.reviewedAt ?? enrollment.updatedAt ?? enrollment.submittedAt ?? enrollment.createdAt;
  return value ? Date.parse(value) || 0 : 0;
}

function enrollmentRank(enrollment: AgentEnrollmentRequest, row?: AgentDashboardRow): number {
  const status = normalizeEnrollmentStatus(enrollment.status);
  if (row?.profile?.approvalStatus === 'APPROVED' && status === 'APPROVED') return 0;
  if (status === 'PENDING_REVIEW' || status === 'REGISTERED' || status === 'SUBMITTED') return 1;
  if (status === 'APPROVED') return 2;
  if (status === 'REJECTED') return 3;
  if (status === 'RUNTIME_OBSERVED') return 9;
  return 5;
}

function betterEnrollment(left: AgentEnrollmentRequest | undefined, right: AgentEnrollmentRequest, row?: AgentDashboardRow): AgentEnrollmentRequest {
  if (!left) return right;
  const rankDiff = enrollmentRank(left, row) - enrollmentRank(right, row);
  if (rankDiff > 0) return right;
  if (rankDiff < 0) return left;
  return enrollmentTimestamp(right) >= enrollmentTimestamp(left) ? right : left;
}

function attachEnrollments(rows: AgentDashboardRow[], enrollments: AgentEnrollmentRequest[]): AgentDashboardRow[] {
  const enrollmentsByAgentId = new Map<string, AgentEnrollmentRequest[]>();
  for (const enrollment of enrollments) {
    const key = enrollmentAgentKey(enrollment);
    if (!key) continue;
    const list = enrollmentsByAgentId.get(key) ?? [];
    list.push(enrollment);
    enrollmentsByAgentId.set(key, list);
  }
  return rows.map((row) => {
    const list = enrollmentsByAgentId.get(row.agentId) ?? [];
    const enrollment = list.reduce<AgentEnrollmentRequest | undefined>((selected, item) => betterEnrollment(selected, item, row), undefined);
    if (enrollment) return { ...row, enrollment };
    if (!row.profile && row.runtime) {
      return { ...row, enrollment: runtimeToEnrollmentCandidate(row.runtime) };
    }
    return row;
  });
}

function rowTenantId(row: AgentDashboardRow): string {
  return String(row.profile?.tenantId ?? row.enrollment?.tenantId ?? '').trim();
}

function flowUsesAgent(flow: CoreDispatchFlowView, agentId: string): boolean {
  return (flow.agents ?? []).some((agent) => agent.agentId === agentId);
}

async function enrichRowsForDirectFlowUsage(rows: AgentDashboardRow[]): Promise<AgentDashboardRow[]> {
  // Phase 2: Agent list dispatch state is derived from Dispatch Flow ownership only.
  // It must not read retired parallel dispatch gates.
  const tenants = Array.from(new Set(rows.map(rowTenantId).filter(Boolean)));
  const flowsByTenant = new Map<string, CoreDispatchFlowView[]>();
  await Promise.all(tenants.map(async (tenantId) => {
    try {
      flowsByTenant.set(tenantId, await coreAdminApi.getDispatchFlows(tenantId));
    } catch {
      flowsByTenant.set(tenantId, []);
    }
  }));
  return rows.map((row) => {
    const tenantId = rowTenantId(row);
    const flows = (flowsByTenant.get(tenantId) ?? []).filter((flow) => flowUsesAgent(flow, row.agentId));
    return {
      ...row,
      dispatchFlows: flows,
      dispatchEligibility: undefined
    };
  });
}

export interface AgentGovernanceListData {
  generatedAt: string;
  profiles: CoreAgentProfile[];
  runtimes: NettyAgentRuntime[];
  enrollments: AgentEnrollmentRequest[];
  rows: AgentDashboardRow[];
  sourceErrors: {
    coreAgents?: string;
    coreEnrollments?: string;
    nettyAgents?: string;
    nettyClusterAgents?: string;
  };
}

export function useAgentGovernanceList() {
  const loader = useCallback(async (): Promise<AgentGovernanceListData> => {
    const [coreAgentsResult, coreEnrollmentsResult, nettyAgentsResult, nettyClusterAgentsResult] = await Promise.all([
      safeLoad(() => coreAdminApi.getAgentsRuntimeView().catch(() => coreAdminApi.getAgents())),
      safeLoad(() => coreAdminApi.getAgentEnrollments()),
      safeLoad(() => nettyRuntimeApi.getRuntimeAgents()),
      safeLoad(() => nettyRuntimeApi.getClusterRuntimeAgents())
    ]);

    const profiles = coreAgentsResult.data ?? [];
    const enrollments = coreEnrollmentsResult.data ?? [];
    const runtimeByKey = new Map<string, NettyAgentRuntime>();
    for (const runtime of [...(nettyAgentsResult.data ?? []), ...(nettyClusterAgentsResult.data ?? [])]) {
      runtimeByKey.set(`${runtime.gatewayNodeId ?? runtime.nodeId ?? 'unknown'}::${runtime.agentId}`, runtime);
    }
    const runtimes = Array.from(runtimeByKey.values());
    const rows = await enrichRowsForDirectFlowUsage(attachEnrollments(mergeAgentDashboardRows(profiles, runtimes), enrollments));

    return {
      generatedAt: new Date().toISOString(),
      profiles,
      runtimes,
      enrollments,
      rows,
      sourceErrors: {
        coreAgents: coreAgentsResult.error ?? undefined,
        coreEnrollments: coreEnrollmentsResult.error ?? undefined,
        nettyAgents: nettyAgentsResult.error ?? undefined,
        nettyClusterAgents: nettyClusterAgentsResult.error ?? undefined
      }
    };
  }, []);

  return usePollingResource<AgentGovernanceListData>(loader);
}
