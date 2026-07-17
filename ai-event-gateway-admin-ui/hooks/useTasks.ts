'use client';

import { useCallback, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import { getPublicEnv } from '@/lib/constants/env';
import { createTaskDispatchRuntimeBundle, type TaskDispatchDashboardRow, type TaskDispatchRuntimeBundle } from '@/lib/dashboard/taskDispatchMerge';
import { mockCommandResult, mockTasks } from '@/lib/mock/admin';
import type { CommandResult } from '@/lib/types/admin';
import type { CoreTaskRuntimeView } from '@/lib/types/core';
import { usePollingResource } from '@/hooks/usePollingResource';

function mockCoreTasks(): CoreTaskRuntimeView[] {
  return mockTasks.map((task) => ({
    taskId: task.taskId,
    traceId: task.traceId,
    status: task.status,
    assignedAgentId: task.assignedAgentId,
    createdAt: task.createdAt,
    updatedAt: task.completedAt ?? task.failedAt ?? task.startedAt ?? task.assignedAt ?? task.createdAt,
    dispatchRequestId: `mock-dispatch-${task.taskId}`,
    dispatchStatus: task.status === 'FAILED' ? 'DELIVERY_FAILED' : task.status === 'COMPLETED' ? 'COMPLETED' : 'DELIVERING',
    callbackStatus: task.status === 'COMPLETED' ? 'COMPLETED' : undefined,
    failureReason: task.failureReason,
    payload: task.requestPayload
  }));
}

async function safeRuntime<T>(loader: () => Promise<T>): Promise<{ data?: T; error?: string }> {
  try {
    return { data: await loader() };
  } catch (error) {
    return { error: error instanceof Error ? error.message : 'Unknown runtime API error' };
  }
}

export function useTasks() {
  const [commandMessage, setCommandMessage] = useState<string | null>(null);

  const loader = useCallback(async (): Promise<TaskDispatchRuntimeBundle> => {
    const env = getPublicEnv();
    if (env.useMock) {
      return createTaskDispatchRuntimeBundle({ tasks: mockCoreTasks() });
    }

    const tasks = await coreAdminApi.getTasksRuntimeView();
    const [delivery, callbackRelay] = await Promise.all([
      safeRuntime(() => nettyRuntimeApi.getDeliveryRuntime()),
      safeRuntime(() => nettyRuntimeApi.getCallbackRelayRuntime())
    ]);

    return createTaskDispatchRuntimeBundle({
      tasks,
      deliveryRuntime: delivery.data,
      callbackRelayRuntime: callbackRelay.data,
      deliveryError: delivery.error,
      callbackRelayError: callbackRelay.error
    });
  }, []);

  const resource = usePollingResource<TaskDispatchRuntimeBundle>(loader);

  async function retryTask(row: TaskDispatchDashboardRow): Promise<CommandResult> {
    const env = getPublicEnv();
    const result = env.useMock
      ? mockCommandResult
      : row.task.dispatchRequestId
        ? await coreAdminApi.retryDispatchRequest(row.task.dispatchRequestId)
        : await coreAdminApi.retryTask(row.task.taskId);
    setCommandMessage(result.message);
    await resource.refresh();
    return result;
  }

  async function cancelTask(row: TaskDispatchDashboardRow): Promise<CommandResult> {
    const env = getPublicEnv();
    const result = env.useMock ? mockCommandResult : await coreAdminApi.cancelTask(row.task.taskId);
    setCommandMessage(result.message);
    await resource.refresh();
    return result;
  }

  return {
    ...resource,
    rows: resource.data?.rows ?? [],
    generatedAt: resource.data?.generatedAt,
    coreTaskCount: resource.data?.coreTaskCount ?? 0,
    deliveryRuntimeAvailable: resource.data?.deliveryRuntimeAvailable ?? false,
    callbackRelayRuntimeAvailable: resource.data?.callbackRelayRuntimeAvailable ?? false,
    deliveryError: resource.data?.deliveryError,
    callbackRelayError: resource.data?.callbackRelayError,
    commandMessage,
    retryTask,
    cancelTask
  };
}
