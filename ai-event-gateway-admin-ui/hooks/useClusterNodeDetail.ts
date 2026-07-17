'use client';

import { useCallback, useMemo, useState } from 'react';
import { adminApi } from '@/lib/api/adminApi';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { getMockClusterNodeAgents, getMockClusterNodeDetail, getMockClusterNodeTasks, getMockCommandResult } from '@/lib/mock/admin';
import { correlateGatewayNodeTasks, gatewayTasksFromDeliveryRuntime, type NodeTaskCorrelationResult } from '@/lib/cluster/nodeTaskCorrelation';
import type { AgentInfo, ClusterNodeDetail, CommandResult } from '@/lib/types/admin';
import { usePollingResource } from '@/hooks/usePollingResource';

export function useClusterNodeDetail(nodeId: string) {
  const env = getPublicEnv();
  const [commandMessage, setCommandMessage] = useState<string | null>(null);
  const [commandRunning, setCommandRunning] = useState(false);

  const detailLoader = useCallback(async (): Promise<ClusterNodeDetail> => {
    const currentEnv = getPublicEnv();
    return currentEnv.useMock ? getMockClusterNodeDetail(nodeId) : adminApi.getClusterNodeDetail(nodeId);
  }, [nodeId]);

  const agentsLoader = useCallback(async (): Promise<AgentInfo[]> => {
    const currentEnv = getPublicEnv();
    return currentEnv.useMock ? getMockClusterNodeAgents(nodeId) : adminApi.getClusterNodeAgents(nodeId);
  }, [nodeId]);

  const tasksLoader = useCallback(async (): Promise<NodeTaskCorrelationResult> => {
    const currentEnv = getPublicEnv();
    const nettyTasks = currentEnv.useMock ? getMockClusterNodeTasks(nodeId) : await adminApi.getClusterNodeTasks(nodeId);
    if (currentEnv.useMock || nettyTasks.length > 0) {
      return correlateGatewayNodeTasks({
        nodeId,
        nettyTasks,
        coreTasks: [],
        nodeCount: 1
      });
    }

    const [runtimeDeliveryResult, coreTasksResult, nodesResult, agentsResult] = await Promise.allSettled([
      nettyRuntimeApi.getDeliveryRuntime(),
      coreAdminApi.getTasksRuntimeView(),
      adminApi.getClusterNodes(),
      adminApi.getClusterNodeAgents(nodeId)
    ]);
    const deliveryRuntimeTasks = runtimeDeliveryResult.status === 'fulfilled'
      ? gatewayTasksFromDeliveryRuntime(runtimeDeliveryResult.value, nodeId)
      : [];

    return correlateGatewayNodeTasks({
      nodeId,
      nettyTasks: deliveryRuntimeTasks,
      coreTasks: coreTasksResult.status === 'fulfilled' ? coreTasksResult.value : [],
      nodeCount: nodesResult.status === 'fulfilled' ? Math.max(nodesResult.value.length, 1) : 1,
      agentIds: agentsResult.status === 'fulfilled' ? agentsResult.value.map((agent) => agent.agentId) : []
    });
  }, [nodeId]);

  const detail = usePollingResource<ClusterNodeDetail>(detailLoader, Boolean(nodeId));
  const agents = usePollingResource<AgentInfo[]>(agentsLoader, Boolean(nodeId));
  const taskCorrelation = usePollingResource<NodeTaskCorrelationResult>(tasksLoader, Boolean(nodeId));
  const tasks = useMemo(() => ({
    ...taskCorrelation,
    data: taskCorrelation.data?.tasks ?? null
  }), [taskCorrelation]);

  const executeNodeCommand = useCallback(async (command: 'DRAIN' | 'RESUME'): Promise<CommandResult> => {
    setCommandRunning(true);
    setCommandMessage(null);
    try {
      const result = env.useMock
        ? getMockCommandResult(`Mock ${command} accepted for ${nodeId}`)
        : command === 'DRAIN'
          ? await adminApi.drainClusterNode(nodeId)
          : await adminApi.resumeClusterNode(nodeId);
      setCommandMessage(result.message);
      await Promise.all([detail.refresh(), agents.refresh(), tasks.refresh()]);
      return result;
    } finally {
      setCommandRunning(false);
    }
  }, [agents, detail, env.useMock, nodeId, tasks]);

  return {
    detail,
    agents,
    tasks,
    taskCorrelation: taskCorrelation.data,
    commandMessage,
    commandRunning,
    drainNode: () => executeNodeCommand('DRAIN'),
    resumeNode: () => executeNodeCommand('RESUME')
  };
}
