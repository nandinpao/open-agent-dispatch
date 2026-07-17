"use client";

import { useCallback, useState } from "react";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import { nettyRuntimeApi } from "@/lib/api/nettyRuntimeApi";
import { getPublicEnv } from "@/lib/constants/env";
import {
  buildTaskDispatchRows,
  type TaskDispatchDashboardRow,
} from "@/lib/dashboard/taskDispatchMerge";
import { getMockCommandResult, getMockTaskDetail } from "@/lib/mock/admin";
import type { CommandResult } from "@/lib/types/admin";
import type {
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchAttemptHistoryRecord,
  CoreDispatchAttemptLedger,
  CoreDispatchRequest,
  CoreDispatchTimelineResponse,
  CoreRecoveryGovernanceActionRequest,
  CoreRoutingDecisionRecord,
  CoreTaskDispatchRequirements,
  CoreTaskDispatchEvidenceView,
  CoreTaskRuntimeVerificationView,
  CoreTaskEligibleAgentsResponse,
  CoreDispatchEligibilityV2Response,
  CoreDispatchContractTraceResponse,
  CoreTaskCaseTimelineView,
  CoreTaskRuntimeView,
} from "@/lib/types/core";
import { usePollingResource } from "@/hooks/usePollingResource";

export interface CoreTaskFamilyEvidence {
  parentTask?: CoreTaskRuntimeView;
  childTasks: CoreTaskRuntimeView[];
}

export interface TaskDispatchDetailResource {
  row: TaskDispatchDashboardRow;
  deliveryRuntimeError?: string;
  callbackRelayRuntimeError?: string;
  attemptHistory?: CoreDispatchAttemptHistoryRecord[];
  attemptHistoryError?: string;
  dispatchLedger?: CoreDispatchAttemptLedger[];
  dispatchLedgerError?: string;
  dispatchRequests?: CoreDispatchRequest[];
  dispatchRequestsError?: string;
  callbackInbox?: CoreCallbackInboxEntry[];
  callbackInboxSummary?: CoreCallbackInboxSummary;
  callbackInboxError?: string;
  timeline?: CoreDispatchTimelineResponse;
  timelineError?: string;
  caseTimeline?: CoreTaskCaseTimelineView;
  caseTimelineError?: string;
  dispatchEvidence?: CoreTaskDispatchEvidenceView;
  dispatchEvidenceError?: string;
  runtimeVerification?: CoreTaskRuntimeVerificationView;
  runtimeVerificationError?: string;
  routingDecisions?: CoreRoutingDecisionRecord[];
  routingDecisionsError?: string;
  dispatchRequirements?: CoreTaskDispatchRequirements;
  dispatchRequirementsError?: string;
  eligibleAgents?: CoreTaskEligibleAgentsResponse;
  eligibleAgentsError?: string;
  eligibleAgentsV2?: CoreDispatchEligibilityV2Response;
  eligibleAgentsV2Error?: string;
  dispatchContractTrace?: CoreDispatchContractTraceResponse;
  dispatchContractTraceError?: string;
  taskFamily?: CoreTaskFamilyEvidence;
  taskFamilyError?: string;
}

async function safeRuntime<T>(
  loader: () => Promise<T>,
): Promise<{ data?: T; error?: string }> {
  try {
    return { data: await loader() };
  } catch (error) {
    return {
      error:
        error instanceof Error ? error.message : "Unknown runtime API error",
    };
  }
}

function mockCoreTask(taskId: string): CoreTaskRuntimeView {
  const mock = getMockTaskDetail(taskId);
  return {
    taskId: mock.taskId,
    traceId: mock.traceId,
    status: mock.status,
    assignedAgentId: mock.assignedAgentId,
    createdAt: mock.createdAt,
    updatedAt:
      mock.completedAt ??
      mock.failedAt ??
      mock.startedAt ??
      mock.assignedAt ??
      mock.createdAt,
    eventStage: "EXTERNAL",
    requestedSkill: "MOCK_ANALYSIS",
    correlationId: `mock-case-${mock.taskId}`,
    matchedFlowId: "MOCK_ANALYSIS_FLOW",
    matchedRuleId: "MOCK_ANALYSIS_INTAKE_RULE",
    routingPath: "FLOW_RULE",
    dispatchRequestId: `mock-dispatch-${mock.taskId}`,
    dispatchStatus:
      mock.status === "FAILED"
        ? "DELIVERY_FAILED"
        : mock.status === "COMPLETED"
          ? "COMPLETED"
          : "DELIVERING",
    callbackStatus: mock.status === "COMPLETED" ? "COMPLETED" : undefined,
    failureReason: mock.failureReason,
    payload: mock.requestPayload,
  };
}

export function useTaskDetail(taskId: string) {
  const [commandMessage, setCommandMessage] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reassigning, setReassigning] = useState(false);
  const [triggeringRecovery, setTriggeringRecovery] = useState(false);
  const [movingDeadLetter, setMovingDeadLetter] = useState(false);
  const [restoringDeadLetter, setRestoringDeadLetter] = useState(false);
  const [retryingIssueSyncActionId, setRetryingIssueSyncActionId] = useState<
    string | null
  >(null);

  const loader = useCallback(async (): Promise<TaskDispatchDetailResource> => {
    const env = getPublicEnv();
    if (env.useMock) {
      return {
        row: buildTaskDispatchRows([mockCoreTask(taskId)])[0],
        dispatchRequirements: {
          taskId,
          taskType: "INCIDENT_RESPONSE",
          requiredCapabilities: ["MOCK_ANALYSIS"],
          requiredRuntimeFeatures: ["TASK_ACK", "TASK_RESULT"],
          requirementSource: "MOCK",
        },
        eligibleAgents: {
          taskId,
          eligibleAgents: [
            {
              agentId: "mock-agent-openclaw-001",
              profileCode: "MOCK_ANALYST_PROFILE",
              score: 90,
              eligible: true,
              dispatchStatus: "ELIGIBLE",
            },
          ],
          blockedAgents: [],
        },
        eligibleAgentsV2: {
          taskId,
          engineMode: "SHADOW",
          requirementSource: "P3H_DEMAND_POLICY_SUPPLY_SHADOW",
          applicablePolicies: [
            {
              policyCode: "MOCK_ANALYSIS_POLICY",
              requiredCapabilities: [
                "MOCK_ANALYSIS",
                "MOCK_CLASSIFICATION",
              ],
            },
          ],
          eligibleCandidates: [
            {
              agentId: "mock-agent-openclaw-001",
              supplyProfileCode: "MOCK_ANALYST_L1",
              score: 92,
              eligible: true,
              dispatchStatus: "ELIGIBLE_V2_SHADOW",
            },
          ],
          blockedCandidates: [],
        },
        taskFamily: {
          childTasks: [],
        },
        dispatchContractTrace: {
          tenantId: "fixture-tenant",
          taskId,
          sourceSystem: "MOCK_SOURCE",
          taskType: "MOCK_ANALYSIS",
          status: "READY",
          ready: true,
          summary: "Mock dispatch contract trace resolved MOCK_SOURCE / MOCK_ANALYSIS and found one eligible agent.",
          requiredCapabilities: ["MOCK_ANALYSIS"],
          checks: [
            {
              code: "TASK_CONTRACT_READY",
              status: "PASS",
              message: "Mock Dispatch Flow, Flow Agent selection and optional required Capability are ready.",
              blocking: false,
            },
          ],
          capabilityResolution: {
            primaryCapability: "MOCK_ANALYSIS",
            requiredCapabilities: ["MOCK_ANALYSIS"],
            taskType: "MOCK_ANALYSIS",
            sourceSystem: "MOCK_SOURCE",
            matchedRecipeCode: "MOCK_ANALYSIS_DEFAULT",
            resolutionReasons: ["Mock trace uses the generic mock dispatch contract."],
            fallback: false,
          },
          diagnostics: { authority: "MOCK_DISPATCH_CONTRACT_TRACE" },
          generatedAt: new Date().toISOString(),
        },
      };
    }

    const task = await coreAdminApi.getTaskRuntimeView(taskId);
    const [
      delivery,
      callbackRelay,
      attemptHistory,
      dispatchLedger,
      dispatchRequests,
      callbackInbox,
      callbackInboxSummary,
      timeline,
      caseTimeline,
      routingDecisions,
      dispatchEvidence,
      runtimeVerification,
      dispatchRequirements,
      eligibleAgents,
      eligibleAgentsV2,
      dispatchContractTrace,
      taskFamily,
    ] = await Promise.all([
      safeRuntime(() => nettyRuntimeApi.getDeliveryRuntime()),
      safeRuntime(() => nettyRuntimeApi.getCallbackRelayRuntime()),
      safeRuntime(() =>
        coreAdminApi.getTaskDispatchAttemptHistory(taskId, 100),
      ),
      safeRuntime(() => coreAdminApi.getTaskDispatchLedger(taskId, 100)),
      safeRuntime(() => coreAdminApi.getTaskDispatchRequests(taskId, 100)),
      safeRuntime(() => coreAdminApi.getTaskCallbackInbox(taskId, 100)),
      safeRuntime(() => coreAdminApi.getTaskCallbackInboxSummary(taskId, 100)),
      safeRuntime(() => coreAdminApi.getTaskTimeline(taskId, 200)),
      safeRuntime(() => coreAdminApi.getTaskCaseTimeline(taskId)),
      safeRuntime(() => coreAdminApi.getTaskRoutingDecisions(taskId, 20)),
      safeRuntime(() => coreAdminApi.getTaskDispatchEvidence(taskId, 200)),
      safeRuntime(() => coreAdminApi.getTaskRuntimeVerification(taskId, 90, 200)),
      safeRuntime(() => coreAdminApi.getTaskDispatchRequirements(taskId)),
      safeRuntime(() => coreAdminApi.getTaskEligibleAgents(taskId, 500)),
      safeRuntime(() => coreAdminApi.getTaskEligibleAgentsV2(taskId, 500)),
      safeRuntime(() => coreAdminApi.traceDispatchContract({ taskId })),
      safeRuntime(async (): Promise<CoreTaskFamilyEvidence> => {
        const tasks = await coreAdminApi.getTasksRuntimeView();
        const effectiveParentId = task.parentTaskId ?? task.taskId;
        return {
          parentTask: task.parentTaskId
            ? tasks.find((candidate) => candidate.taskId === task.parentTaskId)
            : undefined,
          childTasks: tasks
            .filter((candidate) => candidate.parentTaskId === effectiveParentId)
            .sort((left, right) => String(left.createdAt ?? "").localeCompare(String(right.createdAt ?? ""))),
        };
      }),
    ]);

    return {
      row: buildTaskDispatchRows([task], delivery.data, callbackRelay.data)[0],
      deliveryRuntimeError: delivery.error,
      callbackRelayRuntimeError: callbackRelay.error,
      attemptHistory: attemptHistory.data ?? [],
      attemptHistoryError: attemptHistory.error,
      dispatchLedger: dispatchLedger.data ?? [],
      dispatchLedgerError: dispatchLedger.error,
      dispatchRequests: dispatchRequests.data ?? [],
      dispatchRequestsError: dispatchRequests.error,
      callbackInbox: callbackInbox.data ?? [],
      callbackInboxSummary: callbackInboxSummary.data,
      callbackInboxError: callbackInbox.error ?? callbackInboxSummary.error,
      timeline: timeline.data,
      timelineError: timeline.error,
      caseTimeline: caseTimeline.data,
      caseTimelineError: caseTimeline.error,
      routingDecisions: routingDecisions.data ?? [],
      routingDecisionsError: routingDecisions.error,
      dispatchEvidence: dispatchEvidence.data,
      dispatchEvidenceError: dispatchEvidence.error,
      runtimeVerification: runtimeVerification.data,
      runtimeVerificationError: runtimeVerification.error,
      dispatchRequirements: dispatchRequirements.data,
      dispatchRequirementsError: dispatchRequirements.error,
      eligibleAgents: eligibleAgents.data,
      eligibleAgentsError: eligibleAgents.error,
      eligibleAgentsV2: eligibleAgentsV2.data,
      eligibleAgentsV2Error: eligibleAgentsV2.error,
      dispatchContractTrace: dispatchContractTrace.data,
      dispatchContractTraceError: dispatchContractTrace.error,
      taskFamily: taskFamily.data,
      taskFamilyError: taskFamily.error,
    };
  }, [taskId]);

  const resource = usePollingResource<TaskDispatchDetailResource>(loader);

  async function retryTask(): Promise<CommandResult> {
    const env = getPublicEnv();
    const row = resource.data?.row;
    setRetrying(true);
    try {
      const result = env.useMock
        ? getMockCommandResult(`Retry command accepted for ${taskId}`)
        : row?.task.dispatchRequestId
          ? await coreAdminApi.retryDispatchRequest(row.task.dispatchRequestId)
          : await coreAdminApi.retryTask(taskId);
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setRetrying(false);
    }
  }

  async function cancelTask(): Promise<CommandResult> {
    const env = getPublicEnv();
    setCancelling(true);
    try {
      const result = env.useMock
        ? getMockCommandResult(`Cancel command accepted for ${taskId}`)
        : await coreAdminApi.cancelTask(taskId);
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setCancelling(false);
    }
  }

  async function reassignTask(agentId?: string): Promise<CommandResult> {
    const env = getPublicEnv();
    setReassigning(true);
    try {
      const body = agentId ? { targetAgentId: agentId } : {};
      const result = env.useMock
        ? getMockCommandResult(`Reassign command accepted for ${taskId}`)
        : await coreAdminApi.reassignTask(taskId, body);
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setReassigning(false);
    }
  }

  async function triggerRecoveryNow(
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CommandResult> {
    const env = getPublicEnv();
    setTriggeringRecovery(true);
    try {
      const result = env.useMock
        ? getMockCommandResult(`Immediate recovery accepted for ${taskId}`)
        : await coreAdminApi.triggerTaskRecoveryNow(
            taskId,
            body ?? {
              operatorId: "admin-ui",
              reason:
                "Manual immediate delayed recovery trigger from Task detail",
              riskAcknowledged: true,
              confirmationPhrase: "CONFIRM_RECOVERY_ACTION",
              requestId: `admin-ui-recovery-${Date.now()}`,
            },
          );
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setTriggeringRecovery(false);
    }
  }

  async function moveToDeadLetter(
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CommandResult> {
    const env = getPublicEnv();
    setMovingDeadLetter(true);
    try {
      const result = env.useMock
        ? getMockCommandResult(`Dead-letter action accepted for ${taskId}`)
        : await coreAdminApi.moveTaskToDeadLetter(
            taskId,
            body ?? {
              operatorId: "admin-ui",
              reason:
                "Manual move latest task dispatch to dead-letter from Task detail",
              riskAcknowledged: true,
              confirmationPhrase: "CONFIRM_HIGH_RISK_RECOVERY",
              requestId: `admin-ui-dead-letter-${Date.now()}`,
            },
          );
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setMovingDeadLetter(false);
    }
  }

  async function retryIssueSync(actionId: string): Promise<CommandResult> {
    const env = getPublicEnv();
    setRetryingIssueSyncActionId(actionId);
    try {
      if (env.useMock) {
        const result = getMockCommandResult(
          `Issue sync retry accepted for ${actionId}`,
        );
        setCommandMessage(result.message);
        return result;
      }
      await coreAdminApi.retryAdapterAction(actionId);
      await coreAdminApi.executeAdapterAction(actionId);
      const result = getMockCommandResult(
        `Issue sync retry executed for ${actionId}`,
      );
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setRetryingIssueSyncActionId(null);
    }
  }

  async function restoreFromDeadLetter(
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CommandResult> {
    const env = getPublicEnv();
    setRestoringDeadLetter(true);
    try {
      const result = env.useMock
        ? getMockCommandResult(`Dead-letter restore accepted for ${taskId}`)
        : await coreAdminApi.restoreTaskFromDeadLetter(
            taskId,
            body ?? {
              operatorId: "admin-ui",
              reason:
                "Manual restore latest task dead-letter dispatch from Task detail",
              resetAttempts: true,
              immediate: true,
              riskAcknowledged: true,
              confirmationPhrase: "CONFIRM_HIGH_RISK_RECOVERY",
              requestId: `admin-ui-restore-dead-letter-${Date.now()}`,
            },
          );
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setRestoringDeadLetter(false);
    }
  }


  return {
    ...resource,
    commandMessage,
    retrying,
    cancelling,
    reassigning,
    triggeringRecovery,
    movingDeadLetter,
    restoringDeadLetter,
    retryingIssueSyncActionId,
    retryTask,
    cancelTask,
    reassignTask,
    triggerRecoveryNow,
    moveToDeadLetter,
    restoreFromDeadLetter,
    retryIssueSync,
  };
}
