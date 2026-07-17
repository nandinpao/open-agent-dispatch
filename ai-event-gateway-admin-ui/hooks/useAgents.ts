'use client';

import { useCallback, useState } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { mockAgents, mockCommandResult } from '@/lib/mock/admin';
import type { AgentInfo, ClusterAgentsResponse, CommandResult } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';
import { useRealtimeAgents } from '@/hooks/useRealtimeResources';

function mockAgentsResponse(): ClusterAgentsResponse {
  const byNode = mockAgents.reduce<Record<string, AgentInfo[]>>((accumulator, agent) => {
    const nodeId = agent.nodeId || '-';
    accumulator[nodeId] = [...(accumulator[nodeId] ?? []), agent];
    return accumulator;
  }, {});

  return {
    scope: 'CLUSTER',
    generatedAt: new Date().toISOString(),
    items: mockAgents,
    byNode
  };
}

export function useAgents() {
  const [commandMessage, setCommandMessage] = useState<string | null>(null);

  const loader = useCallback(async (): Promise<ClusterAgentsResponse> => {
    const env = getPublicEnv();
    return env.useMock ? mockAgentsResponse() : adminApi.getAgentsWithScope();
  }, []);

  const resource = usePollingResource<ClusterAgentsResponse>(loader);
  const realtimeData = useRealtimeAgents(resource.data?.items);

  async function pingAgent(agentId: string): Promise<CommandResult> {
    const env = getPublicEnv();
    const result = env.useMock ? mockCommandResult : await adminApi.pingAgent(agentId);
    setCommandMessage(result.message);
    await resource.refresh();
    return result;
  }

  async function disconnectAgent(agentId: string): Promise<CommandResult> {
    const env = getPublicEnv();
    const result = env.useMock ? mockCommandResult : await adminApi.disconnectAgent(agentId);
    setCommandMessage(result.message);
    await resource.refresh();
    return result;
  }

  return {
    ...resource,
    data: realtimeData,
    byNode: resource.data?.byNode ?? {},
    dataScope: resource.data?.scope ?? 'LOCAL',
    localNodeId: resource.data?.localNodeId,
    generatedAt: resource.data?.generatedAt,
    fallbackReason: resource.data?.fallbackReason,
    commandMessage,
    pingAgent,
    disconnectAgent
  };
}
