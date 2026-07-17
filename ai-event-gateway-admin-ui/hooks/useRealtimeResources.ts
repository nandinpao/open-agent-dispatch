'use client';

import { useMemo } from 'react';
import { useAdminRealtime } from '@/hooks/useAdminRealtime';
import { applyAgentRealtimeEvents, applyClusterRealtimeEvents, applyTaskRealtimeEvents } from '@/lib/websocket/eventAppliers';
import type { AgentInfo, ClusterNode, GatewayTaskRecord } from '@/lib/types/admin';

export function useRealtimeAgents(baseAgents: AgentInfo[] | null | undefined): AgentInfo[] | null {
  const { events } = useAdminRealtime();
  return useMemo(() => applyAgentRealtimeEvents(baseAgents, events), [baseAgents, events]);
}

export function useRealtimeClusterNodes(baseNodes: ClusterNode[] | null | undefined): ClusterNode[] | null {
  const { events } = useAdminRealtime();
  return useMemo(() => applyClusterRealtimeEvents(baseNodes, events), [baseNodes, events]);
}

export function useRealtimeTasks(baseTasks: GatewayTaskRecord[] | null | undefined): GatewayTaskRecord[] | null {
  const { events } = useAdminRealtime();
  return useMemo(() => applyTaskRealtimeEvents(baseTasks, events), [baseTasks, events]);
}
