'use client';

import { useCallback } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import { mergeAgentDashboardRows } from '@/lib/dashboard/agentMerge';
import { buildDashboardSummaries, type DashboardSummaries } from '@/lib/dashboard/summary';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { AgentSecurityEvent, CoreAgentProfile, CoreDashboardSnapshot, CoreRecoveryApprovalRequest, CoreRecoveryOperationMetricsSnapshot, CoreRecoveryOperatorRunbook, CoreTaskRuntimeView } from '@/lib/types/core';
import type { NettyAgentRuntime, NettyCallbackRelayRuntime, NettyDeliveryRuntime, NettyRejectedConnection, NettyRuntimeSnapshot, NettyRuntimeSloSnapshot } from '@/lib/types/nettyRuntime';
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

function fallbackList<T>(value: T[] | null): T[] {
  return value ?? [];
}

export interface DualDashboardData {
  generatedAt: string;
  coreSnapshot: CoreDashboardSnapshot | null;
  nettySnapshot: NettyRuntimeSnapshot | null;
  profiles: CoreAgentProfile[];
  runtimes: NettyAgentRuntime[];
  agentRows: AgentDashboardRow[];
  tasks: CoreTaskRuntimeView[];
  securityEvents: AgentSecurityEvent[];
  rejectedConnections: NettyRejectedConnection[];
  delivery: NettyDeliveryRuntime | null;
  callbackRelay: NettyCallbackRelayRuntime | null;
  runtimeSlo: NettyRuntimeSloSnapshot | null;
  recoveryMetrics: CoreRecoveryOperationMetricsSnapshot | null;
  recoveryRunbook: CoreRecoveryOperatorRunbook | null;
  recoveryApprovals: CoreRecoveryApprovalRequest[];
  summaries: DashboardSummaries;
  sourceErrors: {
    coreSnapshot?: string;
    coreAgents?: string;
    coreTasks?: string;
    coreSecurityEvents?: string;
    nettySnapshot?: string;
    nettyAgents?: string;
    nettyRejectedConnections?: string;
    nettyDelivery?: string;
    nettyCallbackRelay?: string;
    nettyRuntimeSlo?: string;
    coreRecoveryMetrics?: string;
    coreRecoveryRunbook?: string;
    coreRecoveryApprovals?: string;
  };
}

export function useDualDashboard() {
  const loader = useCallback(async (): Promise<DualDashboardData> => {
    const [
      coreSnapshotResult,
      coreAgentsResult,
      coreTasksResult,
      coreSecurityEventsResult,
      nettySnapshotResult,
      nettyAgentsResult,
      nettyRejectedConnectionsResult,
      nettyDeliveryResult,
      nettyCallbackRelayResult,
      nettyRuntimeSloResult,
      coreRecoveryMetricsResult,
      coreRecoveryRunbookResult,
      coreRecoveryApprovalsResult
    ] = await Promise.all([
      safeLoad(() => coreAdminApi.getDashboardSnapshot()),
      safeLoad(() => coreAdminApi.getAgentsRuntimeView().catch(() => coreAdminApi.getAgents())),
      safeLoad(() => coreAdminApi.getTasksRuntimeView()),
      safeLoad(() => coreAdminApi.getSecurityEvents().catch(() => coreAdminApi.getAgentSecurityEvents())),
      safeLoad(() => nettyRuntimeApi.getRuntimeSnapshot()),
      safeLoad(() => nettyRuntimeApi.getClusterRuntimeAgents().catch(() => nettyRuntimeApi.getRuntimeAgents())),
      safeLoad(() => nettyRuntimeApi.getRejectedConnections()),
      safeLoad(() => nettyRuntimeApi.getDeliveryRuntime()),
      safeLoad(() => nettyRuntimeApi.getCallbackRelayRuntime()),
      safeLoad(() => nettyRuntimeApi.getRuntimeSlo()),
      safeLoad(() => coreAdminApi.getRecoveryMetrics()),
      safeLoad(() => coreAdminApi.getRecoveryRunbook()),
      safeLoad(() => coreAdminApi.getRecoveryApprovalRequests('PENDING', 25))
    ]);

    const profiles = fallbackList(coreAgentsResult.data);
    const nettySnapshot = nettySnapshotResult.data;
    const runtimes = fallbackList(nettyAgentsResult.data ?? nettySnapshot?.agents ?? null);
    const agentRows = mergeAgentDashboardRows(profiles, runtimes);
    const tasks = fallbackList(coreTasksResult.data);
    const securityEvents = fallbackList(coreSecurityEventsResult.data);
    const rejectedConnections = fallbackList(nettyRejectedConnectionsResult.data ?? nettySnapshot?.rejectedConnections ?? null);
    const delivery = nettyDeliveryResult.data ?? nettySnapshot?.delivery ?? null;
    const callbackRelay = nettyCallbackRelayResult.data ?? nettySnapshot?.callbackRelay ?? null;
    const runtimeSlo = nettyRuntimeSloResult.data ?? null;
    const recoveryMetrics = coreRecoveryMetricsResult.data ?? null;
    const recoveryRunbook = coreRecoveryRunbookResult.data ?? null;
    const recoveryApprovals = fallbackList(coreRecoveryApprovalsResult.data);

    return {
      generatedAt: new Date().toISOString(),
      coreSnapshot: coreSnapshotResult.data,
      nettySnapshot,
      profiles,
      runtimes,
      agentRows,
      tasks,
      securityEvents,
      rejectedConnections,
      delivery,
      callbackRelay,
      runtimeSlo,
      recoveryMetrics,
      recoveryRunbook,
      recoveryApprovals,
      summaries: buildDashboardSummaries({
        coreSnapshot: coreSnapshotResult.data,
        nettySnapshot,
        agentRows,
        profiles,
        tasks,
        securityEvents,
        rejectedConnections,
        delivery,
        callbackRelay
      }),
      sourceErrors: {
        coreSnapshot: coreSnapshotResult.error ?? undefined,
        coreAgents: coreAgentsResult.error ?? undefined,
        coreTasks: coreTasksResult.error ?? undefined,
        coreSecurityEvents: coreSecurityEventsResult.error ?? undefined,
        nettySnapshot: nettySnapshotResult.error ?? undefined,
        nettyAgents: nettyAgentsResult.error ?? undefined,
        nettyRejectedConnections: nettyRejectedConnectionsResult.error ?? undefined,
        nettyDelivery: nettyDeliveryResult.error ?? undefined,
        nettyCallbackRelay: nettyCallbackRelayResult.error ?? undefined,
        nettyRuntimeSlo: nettyRuntimeSloResult.error ?? undefined,
        coreRecoveryMetrics: coreRecoveryMetricsResult.error ?? undefined,
        coreRecoveryRunbook: coreRecoveryRunbookResult.error ?? undefined,
        coreRecoveryApprovals: coreRecoveryApprovalsResult.error ?? undefined
      }
    };
  }, []);

  return usePollingResource<DualDashboardData>(loader);
}
