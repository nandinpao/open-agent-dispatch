"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import { normalizeCoreTaskRecord, normalizeCoreTaskRuntimeViewPayload, taskAdminApi } from "@/lib/api/domains/taskAdminApi";
import { nettyRuntimeApi } from "@/lib/api/nettyRuntimeApi";
import { getPublicEnv } from "@/lib/constants/env";
import { createIdempotencyKey } from "@/lib/utils/uuid";
import { mergeIssueTrackingIntoTask } from "@/lib/tasks/issueTrackingBridge";
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
  CoreTaskRemediationCommandRequest,
  CoreTaskRemediationCommandResult,
  CoreTaskIssueDedupSummary,
  CoreAdapterAction,
  CoreTaskIssueTracking,
} from "@/lib/types/core";
import { usePollingResource } from "@/hooks/usePollingResource";
import type { HandoffReleaseEvidenceView, HandoffSnapshotView } from "@/lib/handoffContextContract";
import type { A2AResultReliabilityView, A2AResultProcessingView } from "@/lib/a2aResultReliabilityContract";
import type {
  CoreAdapterExecutorAuditView,
  CoreIssuePolicyDecisionView,
  CoreTaskLineageEvidence,
  CoreTaskOperationsView,
} from "@/lib/types/domains/task";

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
  issueTracking?: CoreTaskIssueTracking;
  issueDedup?: CoreTaskIssueDedupSummary;
  issuePolicyDecision?: CoreIssuePolicyDecisionView;
  issueAdapterActions?: CoreAdapterAction[];
  issueProviderExecutions?: CoreAdapterExecutorAuditView[];
  issueOperationsError?: string;
  issueDedupError?: string;
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
  handoffSnapshots?: HandoffSnapshotView[];
  handoffReleaseEvidence?: HandoffReleaseEvidenceView[];
  handoffReliabilityError?: string;
  a2aResultReliability?: A2AResultReliabilityView;
  a2aResultReliabilityError?: string;
  lineage?: CoreTaskLineageEvidence[];
  lineageError?: string;
}

interface TaskOverviewPollingResource {
  row: TaskDispatchDashboardRow;
  operations?: CoreTaskOperationsView;
}

type TaskDetailSupplement = Partial<Omit<TaskDispatchDetailResource, "row">>;

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

function isTerminalTaskStatus(status?: string): boolean {
  return new Set([
    "COMPLETED", "SUCCEEDED", "FAILED", "CANCELLED", "DEAD_LETTER",
    "TIMED_OUT", "EXPIRED", "ESCALATED", "SUPPRESSED",
  ]).has(String(status ?? "").toUpperCase());
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
  const [remediatingCommand, setRemediatingCommand] = useState<string | null>(null);
  const [retryingHandoffSnapshotId, setRetryingHandoffSnapshotId] = useState<string | null>(null);
  const [reconcilingA2AResultId, setReconcilingA2AResultId] = useState<string | null>(null);

  // Issue authority is a first-class Task Detail summary, not an advanced-only diagnostic.
  const activeSectionsRef = useRef<Set<string>>(new Set(["execution", "issue"]));
  const revisionRef = useRef<{ execution?: number; issue?: number; relationships?: number }>({});
  const [supplement, setSupplement] = useState<TaskDetailSupplement>({});
  const [supplementError, setSupplementError] = useState<string | null>(null);
  const [supplementRefreshing, setSupplementRefreshing] = useState(false);

  const overviewLoader = useCallback(async (): Promise<TaskOverviewPollingResource> => {
    const env = getPublicEnv();
    if (env.useMock) {
      return { row: buildTaskDispatchRows([mockCoreTask(taskId)])[0] };
    }
    const operations = await taskAdminApi.getTaskOperationsView(taskId, ["overview"]);
    const runtimePayload = operations.overview?.payload?.runtimeView;
    const task = normalizeCoreTaskRuntimeViewPayload(runtimePayload)[0];
    if (!task) throw new Error(`Core task ${taskId} operations overview is empty.`);
    return { row: buildTaskDispatchRows([task])[0], operations };
  }, [taskId]);

  const overviewResource = usePollingResource<TaskOverviewPollingResource>(
    overviewLoader,
    true,
    (current) => !isTerminalTaskStatus(current?.row.task.status),
  );

  const loadOperationsSections = useCallback(async (
    sections: Array<"execution" | "issue" | "relationships">,
    force = false,
  ) => {
    const env = getPublicEnv();
    if (env.useMock) {
      setSupplement((current) => ({
        ...current,
        dispatchRequirements: {
          taskId,
          taskType: "INCIDENT_RESPONSE",
          requiredCapabilities: ["MOCK_ANALYSIS"],
          requiredRuntimeFeatures: ["TASK_ACK", "TASK_RESULT"],
          requirementSource: "MOCK",
        },
        taskFamily: { childTasks: [] },
        handoffSnapshots: [],
        handoffReleaseEvidence: [],
        a2aResultReliability: { taskId, results: [], aggregations: [] },
      }));
      return;
    }
    if (!sections.length) return;
    setSupplementRefreshing(true);
    setSupplementError(null);
    try {
      const operations = await taskAdminApi.getTaskOperationsView(taskId, sections);
      const next: TaskDetailSupplement = {};

      if (sections.includes("execution") && operations.execution.status !== "OMITTED") {
        const payload = operations.execution.payload;
        next.dispatchRequests = payload?.dispatchRequests ?? [];
        next.attemptHistory = payload?.attemptHistory ?? [];
        next.dispatchLedger = payload?.dispatchLedger ?? [];
        next.callbackInbox = payload?.callbackInbox ?? [];
        next.callbackInboxSummary = payload?.callbackInboxSummary;
        next.timeline = payload?.timeline;
        next.caseTimeline = payload?.caseTimeline;
        next.routingDecisions = payload?.routingDecisions ?? [];
        next.dispatchEvidence = payload?.dispatchEvidence;
        next.runtimeVerification = payload?.runtimeVerification;
        if (operations.execution.status === "UNAVAILABLE") {
          next.timelineError = operations.execution.errorMessage ?? operations.execution.errorCode;
          next.runtimeVerificationError = operations.execution.errorMessage ?? operations.execution.errorCode;
        }

        const [delivery, callbackRelay, dispatchRequirements, eligibleAgentsV2, dispatchContractTrace] = await Promise.all([
          safeRuntime(() => nettyRuntimeApi.getDeliveryRuntime()),
          safeRuntime(() => nettyRuntimeApi.getCallbackRelayRuntime()),
          safeRuntime(() => taskAdminApi.getTaskDispatchRequirements(taskId)),
          safeRuntime(() => taskAdminApi.getTaskEligibleAgentsV2(taskId, 500)),
          safeRuntime(() => coreAdminApi.traceDispatchContract({ taskId })),
        ]);
        next.deliveryRuntimeError = delivery.error;
        next.callbackRelayRuntimeError = callbackRelay.error;
        next.dispatchRequirements = dispatchRequirements.data;
        next.dispatchRequirementsError = dispatchRequirements.error;
        next.eligibleAgents = payload?.dispatchEvidence?.eligibleAgents;
        next.eligibleAgentsV2 = eligibleAgentsV2.data;
        next.eligibleAgentsV2Error = eligibleAgentsV2.error;
        next.dispatchContractTrace = dispatchContractTrace.data;
        next.dispatchContractTraceError = dispatchContractTrace.error;
        // Runtime summaries live on the merged dashboard row; preserve them in supplement for merge below.
        (next as TaskDetailSupplement & { __deliveryRuntime?: Awaited<ReturnType<typeof nettyRuntimeApi.getDeliveryRuntime>>; __callbackRelayRuntime?: Awaited<ReturnType<typeof nettyRuntimeApi.getCallbackRelayRuntime>> }).__deliveryRuntime = delivery.data;
        (next as TaskDetailSupplement & { __deliveryRuntime?: Awaited<ReturnType<typeof nettyRuntimeApi.getDeliveryRuntime>>; __callbackRelayRuntime?: Awaited<ReturnType<typeof nettyRuntimeApi.getCallbackRelayRuntime>> }).__callbackRelayRuntime = callbackRelay.data;
        revisionRef.current.execution = operations.execution.revision;
      }

      if (sections.includes("issue") && operations.issue.status !== "OMITTED") {
        next.issueTracking = operations.issue.payload?.issueTracking;
        next.issueDedup = operations.issue.payload?.issueDedup;
        next.issuePolicyDecision = operations.issue.payload?.issuePolicyDecision;
        next.issueAdapterActions = operations.issue.payload?.adapterActions ?? [];
        next.issueProviderExecutions = operations.issue.payload?.providerExecutions ?? [];
        next.issueOperationsError = operations.issue.status === "UNAVAILABLE"
          ? operations.issue.errorMessage ?? operations.issue.errorCode
          : undefined;
        next.issueDedupError = next.issueOperationsError;
        revisionRef.current.issue = operations.issue.revision;
      }

      if (sections.includes("relationships") && operations.relationships.status !== "OMITTED") {
        const relationPayload = operations.relationships.payload;
        const parentTask = relationPayload?.parentTask
          ? normalizeCoreTaskRecord(relationPayload.parentTask)
          : undefined;
        const childTasks = (relationPayload?.childTasks ?? [])
          .map((value) => normalizeCoreTaskRecord(value));
        next.taskFamily = { parentTask, childTasks };
        next.taskFamilyError = operations.relationships.status === "UNAVAILABLE"
          ? operations.relationships.errorMessage ?? operations.relationships.errorCode
          : undefined;
        const [lineage, handoffReliability, a2aResultReliability] = await Promise.all([
          safeRuntime<CoreTaskLineageEvidence[]>(() => taskAdminApi.getTaskLineage(taskId, 500)),
          safeRuntime<{ snapshots: HandoffSnapshotView[]; evidence: HandoffReleaseEvidenceView[] }>(async () => {
            const snapshots = await taskAdminApi.getTaskHandoffSnapshots(taskId, 200);
            const latest = snapshots[0];
            const evidence = latest ? await taskAdminApi.getHandoffReleaseEvidence(latest.snapshotId, 200) : [];
            return { snapshots, evidence };
          }),
          safeRuntime<A2AResultReliabilityView>(() => taskAdminApi.getTaskA2AResultReliability(taskId, 100)),
        ]);
        next.lineage = lineage.data ?? [];
        next.lineageError = lineage.error;
        next.handoffSnapshots = handoffReliability.data?.snapshots ?? [];
        next.handoffReleaseEvidence = handoffReliability.data?.evidence ?? [];
        next.handoffReliabilityError = handoffReliability.error;
        next.a2aResultReliability = a2aResultReliability.data;
        next.a2aResultReliabilityError = a2aResultReliability.error;
        revisionRef.current.relationships = operations.relationships.revision;
      }

      setSupplement((current) => ({ ...current, ...next }));
    } catch (error) {
      setSupplementError(error instanceof Error ? error.message : "Task operations section refresh failed.");
      if (force) throw error;
    } finally {
      setSupplementRefreshing(false);
    }
  }, [taskId]);

  useEffect(() => {
    if (!overviewResource.data) return;
    const revisions = overviewResource.data.operations?.overview?.payload;
    const sections: Array<"execution" | "issue" | "relationships"> = [];
    if (activeSectionsRef.current.has("execution") && revisions?.executionRevision !== revisionRef.current.execution) sections.push("execution");
    if (activeSectionsRef.current.has("issue") && revisions?.issueRevision !== revisionRef.current.issue) sections.push("issue");
    if (activeSectionsRef.current.has("relationships") && revisions?.relationshipsRevision !== revisionRef.current.relationships) sections.push("relationships");
    if (sections.length) void loadOperationsSections(sections);
  }, [overviewResource.data, loadOperationsSections]);

  const activateOperationsSection = useCallback((section: "execution" | "issue" | "relationships") => {
    activeSectionsRef.current.add(section);
    void loadOperationsSections([section]);
  }, [loadOperationsSections]);

  const refresh = useCallback(async () => {
    await overviewResource.refresh();
    const sections = Array.from(activeSectionsRef.current)
      .filter((value): value is "execution" | "issue" | "relationships" => value === "execution" || value === "issue" || value === "relationships");
    await loadOperationsSections(sections, true);
  }, [overviewResource, loadOperationsSections]);

  const mergedRow = (() => {
    const base = overviewResource.data?.row;
    if (!base) return undefined;
    const runtime = supplement as TaskDetailSupplement & { __deliveryRuntime?: Awaited<ReturnType<typeof nettyRuntimeApi.getDeliveryRuntime>>; __callbackRelayRuntime?: Awaited<ReturnType<typeof nettyRuntimeApi.getCallbackRelayRuntime>> };
    const issueTracking = runtime.issueTracking ?? base.task.issueTracking;
    const taskWithLatestIssueLink: CoreTaskRuntimeView = issueTracking
      ? {
          ...base.task,
          issueTracking,
          payload: {
            ...(typeof base.task.payload === 'object' && base.task.payload !== null && !Array.isArray(base.task.payload) ? base.task.payload as Record<string, unknown> : {}),
            issueTracking,
          },
        }
      : base.task;
    const issueMergedTask = mergeIssueTrackingIntoTask(
      taskWithLatestIssueLink,
      runtime.issueAdapterActions ?? [],
      runtime.issueProviderExecutions ?? [],
      runtime.issuePolicyDecision,
    );
    return buildTaskDispatchRows([issueMergedTask], runtime.__deliveryRuntime, runtime.__callbackRelayRuntime)[0];
  })();

  const data: TaskDispatchDetailResource | null = mergedRow ? { row: mergedRow, ...supplement } : null;
  const resource = {
    data,
    loading: overviewResource.loading,
    refreshing: overviewResource.refreshing || supplementRefreshing,
    error: overviewResource.error ?? supplementError,
    lastUpdatedAt: overviewResource.lastUpdatedAt,
    refresh,
  };


  async function runTaskRemediationCommand(
    body: CoreTaskRemediationCommandRequest,
  ): Promise<CoreTaskRemediationCommandResult> {
    const env = getPublicEnv();
    setRemediatingCommand(body.commandType);
    try {
      if (env.useMock) {
        const mock = getMockCommandResult(`Task remediation command accepted: ${body.commandType}`);
        const task = resource.data?.row.task ?? mockCoreTask(taskId);
        const result: CoreTaskRemediationCommandResult = {
          success: mock.success,
          message: mock.message,
          timestamp: mock.timestamp,
          taskId,
          commandType: body.commandType,
          allowedCommandsAfter: [],
          audit: {
            operatorId: 'mock-authenticated-operator',
            timestamp: mock.timestamp,
            commandType: body.commandType,
            reason: body.reason,
            beforeState: { taskId, status: task.status, version: body.expectedTaskVersion ?? 0 },
            afterState: { taskId, status: task.status, version: body.expectedTaskVersion ?? 0 },
            idempotencyKey: body.idempotencyKey,
            expectedTaskVersion: body.expectedTaskVersion,
            resultingTaskVersion: body.expectedTaskVersion ?? 0,
            evidencePolicy: 'Mock remediation preserves routing evidence.',
          },
          task,
          idempotentReplay: false,
        };
        setCommandMessage(result.message);
        return result;
      }
      const result = await taskAdminApi.runTaskRemediationCommand(taskId, body);
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setRemediatingCommand(null);
    }
  }

  async function retryTask(): Promise<CommandResult> {
    const env = getPublicEnv();
    const row = resource.data?.row;
    setRetrying(true);
    try {
      const result = env.useMock
        ? getMockCommandResult(`Retry command accepted for ${taskId}`)
        : row?.task.dispatchRequestId
          ? await taskAdminApi.retryDispatchRequest(row.task.dispatchRequestId)
          : await taskAdminApi.retryTask(taskId);
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
        : await taskAdminApi.cancelTask(taskId);
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
        : await taskAdminApi.reassignTask(taskId, body);
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
        : await taskAdminApi.triggerTaskRecoveryNow(
            taskId,
            body ?? {
              reason:
                "Manual immediate delayed recovery trigger from Task detail",
              riskAcknowledged: true,
              confirmationPhrase: "CONFIRM_RECOVERY_ACTION",
              requestId: createIdempotencyKey("task-recovery"),
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
        : await taskAdminApi.moveTaskToDeadLetter(
            taskId,
            body ?? {
              reason:
                "Manual move latest task dispatch to dead-letter from Task detail",
              riskAcknowledged: true,
              confirmationPhrase: "CONFIRM_HIGH_RISK_RECOVERY",
              requestId: createIdempotencyKey("task-dead-letter"),
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
          `Issue provider-operation retry accepted for ${actionId}`,
        );
        setCommandMessage(result.message);
        return result;
      }
      await coreAdminApi.retryAdapterAction(actionId);
      await coreAdminApi.executeAdapterAction(actionId);
      const result = getMockCommandResult(
        `Issue provider-operation retry executed for ${actionId}`,
      );
      setCommandMessage(result.message);
      await resource.refresh();
      return result;
    } finally {
      setRetryingIssueSyncActionId(null);
    }
  }

  async function retryHandoffRelease(snapshotId: string, reason: string): Promise<HandoffSnapshotView> {
    setRetryingHandoffSnapshotId(snapshotId);
    try {
      const result = await coreAdminApi.retryHandoffRelease(snapshotId, reason);
      setCommandMessage(`Handoff release retry accepted for ${snapshotId}.`);
      await resource.refresh();
      return result;
    } finally {
      setRetryingHandoffSnapshotId(null);
    }
  }

  async function reconcileA2AResult(resultId: string, reason: string): Promise<A2AResultProcessingView> {
    setReconcilingA2AResultId(resultId);
    try {
      const result = await coreAdminApi.reconcileA2AResult(resultId, reason);
      setCommandMessage(`A2A Result reconciliation executed for ${resultId}.`);
      await resource.refresh();
      return result;
    } finally {
      setReconcilingA2AResultId(null);
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
        : await taskAdminApi.restoreTaskFromDeadLetter(
            taskId,
            body ?? {
              reason:
                "Manual restore latest task dead-letter dispatch from Task detail",
              resetAttempts: true,
              immediate: true,
              riskAcknowledged: true,
              confirmationPhrase: "CONFIRM_HIGH_RISK_RECOVERY",
              requestId: createIdempotencyKey("task-restore-dead-letter"),
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
    remediatingCommand,
    retryingHandoffSnapshotId,
    reconcilingA2AResultId,
    runTaskRemediationCommand,
    retryTask,
    cancelTask,
    reassignTask,
    triggerRecoveryNow,
    moveToDeadLetter,
    restoreFromDeadLetter,
    retryIssueSync,
    retryHandoffRelease,
    reconcileA2AResult,
    activateOperationsSection,
  };
}
