'use client';

import { useCallback } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import { mergeAgentDashboardRows } from '@/lib/dashboard/agentMerge';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { AgentEnrollmentRequest, CoreAgentProfile } from '@/lib/types/core';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';
import { usePollingResource } from '@/hooks/usePollingResource';

function attachEnrollments(rows: AgentDashboardRow[], enrollments: AgentEnrollmentRequest[]): AgentDashboardRow[] {
  const enrollmentByAgentId = new Map<string, AgentEnrollmentRequest>();
  for (const enrollment of enrollments) {
    const key = enrollment.claimedAgentId ?? enrollment.agentName;
    if (!key) continue;
    enrollmentByAgentId.set(key, enrollment);
  }
  return rows.map((row) => ({ ...row, enrollment: enrollmentByAgentId.get(row.agentId) }));
}

export interface AgentRuntimeDashboardData {
  generatedAt: string;
  profiles: CoreAgentProfile[];
  runtimes: NettyAgentRuntime[];
  enrollments: AgentEnrollmentRequest[];
  rows: AgentDashboardRow[];
}

export function useAgentRuntimeDashboard() {
  const loader = useCallback(async (): Promise<AgentRuntimeDashboardData> => {
    const [profiles, runtimes, enrollments] = await Promise.all([
      coreAdminApi.getAgentsRuntimeView().catch(() => coreAdminApi.getAgents()),
      nettyRuntimeApi.getClusterRuntimeAgents().catch(() => nettyRuntimeApi.getRuntimeAgents()),
      coreAdminApi.getAgentEnrollments().catch(() => [])
    ]);

    return {
      generatedAt: new Date().toISOString(),
      profiles,
      runtimes,
      enrollments,
      rows: attachEnrollments(mergeAgentDashboardRows(profiles, runtimes), enrollments)
    };
  }, []);

  return usePollingResource<AgentRuntimeDashboardData>(loader);
}
