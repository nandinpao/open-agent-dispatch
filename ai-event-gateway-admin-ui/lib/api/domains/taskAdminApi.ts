import type { TaskChainView } from "@/lib/phase7c/taskA2aUx";
import type { A2AResultReliabilityView, A2AResultProcessingView } from "@/lib/a2aResultReliabilityContract";
import type { HandoffReleaseEvidenceView, HandoffSnapshotView } from "@/lib/handoffContextContract";
import {
  coreApiGet,
  coreApiPost,
  coreTenantApiGet,
  coreTenantApiPost,
  requireCoreTenantContext,
} from "@/lib/api/coreClient";
import { ApiError, isNotFoundOrUnsupportedApiError } from "@/lib/api/client";
import { coreAdminEndpoints } from "@/lib/api/endpoints";
import { taskContractAdminApi } from "@/lib/api/domains/taskContractAdminApi";
import type {
  CoreAdapterAction,
} from "@/lib/types/core";
import type {
  CoreAdminFailureQueueResponse,
  CoreDispatchContractBootstrapResponse,
  CoreDispatchContractReadinessResponse,
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchAttemptHistoryRecord,
  CoreDispatchAttemptLedger,
  CoreDispatchEligibilityV2Response,
  CoreDispatchRequest,
  CoreDispatchTimelineResponse,
  CoreRecoveryGovernanceActionRequest,
  CoreRecoveryGovernanceActionResult,
  CoreRoutingDecisionRecord,
  CoreTaskA2AClassificationFlowContract,
  CoreTaskCaseTimelineView,
  CoreTaskClassificationRequest,
  CoreTaskClassificationResult,
  CoreTaskDispatchContractRepairRequest,
  CoreTaskDispatchEvidenceView,
  CoreTaskDispatchRequirements,
  CoreTaskEligibleAgentsResponse,
  CoreTaskIssueDedupSummary,
  CoreIssueRuntimeJourneyView,
  CoreTaskRecord,
  CoreTaskRemediationCommandRequest,
  CoreTaskRemediationCommandResult,
  CoreTaskRuntimeVerificationView,
  CoreTaskFinalizationAuthorityView,
  CoreTaskFinalizationRecoveryItem,
  CoreTaskFlowMatchAuthorityView,
  CoreTaskRuntimeView,
  CoreTaskLineageEvidence,
  CoreTaskOperationsView,
} from "@/lib/types/domains/task";
import type { CommandResult } from "@/lib/types/admin";
import {
  firstDispatchForTask,
  normalizeCoreTaskRecord,
  normalizeCoreTaskRuntimeViewPayload,
  normalizeFailureQueueResponse,
} from "@/lib/api/domains/taskRuntimeNormalizer";


type IssueLinkReconcileCommandResult = CommandResult & {
  payload: CoreIssueRuntimeJourneyView;
};

type PageLike<T> =
  | T[]
  | {
      content?: T[];
      items?: T[];
      records?: T[];
      rows?: T[];
      data?: T[];
    };

function toList<T>(value: PageLike<T>): T[] {
  if (Array.isArray(value)) return value;
  return value.content ?? value.items ?? value.records ?? value.rows ?? value.data ?? [];
}

async function coreGetList<T>(path: string): Promise<T[]> {
  return toList(await coreTenantApiGet<PageLike<T>>(path));
}

/**
 * Physical Task-domain API client.
 *
 * The compatibility coreAdminApi re-exports these methods, but Task product
 * surfaces should import this client directly.
 */
export const taskAdminApi = {
  ...taskContractAdminApi,
  getTaskChain(taskId: string, limit = 100): Promise<TaskChainView> {
    return coreApiGet<TaskChainView>(
      `/api/tasks/${encodeURIComponent(taskId)}/chain?limit=${encodeURIComponent(String(limit))}`,
      undefined,
      { tenantScoped: true },
    );
  },

  getTaskLineage(taskId: string, limit = 250): Promise<CoreTaskLineageEvidence[]> {
    return coreTenantApiGet<CoreTaskLineageEvidence[]>(
      `/api/tasks/${encodeURIComponent(taskId)}/lineage?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getAgentTaskLineage(agentId: string, limit = 250): Promise<CoreTaskLineageEvidence[]> {
    return coreTenantApiGet<CoreTaskLineageEvidence[]>(
      `/api/tasks/lineage/agents/${encodeURIComponent(agentId)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getTaskA2AResultReliability(taskId: string, limit = 100): Promise<A2AResultReliabilityView> {
      return coreTenantApiGet<A2AResultReliabilityView>(
        `/api/tasks/${encodeURIComponent(taskId)}/a2a-result-reliability?limit=${limit}`,
      );
    },

  reconcileA2AResult(resultId: string, reason: string): Promise<A2AResultProcessingView> {
      return coreApiPost<A2AResultProcessingView>(
        `/api/a2a-results/${encodeURIComponent(resultId)}/reconcile`,
        { reason },
        { tenantScoped: true, headers: { "Idempotency-Key": `admin-ui-a2a-result-reconcile-${resultId}-${Date.now()}` } },
      );
    },

  getTaskHandoffSnapshots(taskId: string, limit = 200): Promise<HandoffSnapshotView[]> {
      return coreTenantApiGet<HandoffSnapshotView[]>(
        `/api/tasks/${encodeURIComponent(taskId)}/handoff-contexts?limit=${limit}`,
      );
    },

  getHandoffReleaseEvidence(snapshotId: string, limit = 200): Promise<HandoffReleaseEvidenceView[]> {
      return coreTenantApiGet<HandoffReleaseEvidenceView[]>(
        `/api/handoff-contexts/${encodeURIComponent(snapshotId)}/release-evidence?limit=${limit}`,
      );
    },

  retryHandoffRelease(snapshotId: string, reason: string): Promise<HandoffSnapshotView> {
      return coreTenantApiPost<HandoffSnapshotView>(
        `/api/handoff-contexts/${encodeURIComponent(snapshotId)}/retry-release`,
        { reason },
        { headers: { "Idempotency-Key": `admin-ui-handoff-release-${snapshotId}-${Date.now()}` } },
      );
    },

  getTaskCaseTimeline(taskId: string): Promise<CoreTaskCaseTimelineView> {
      return coreTenantApiGet<CoreTaskCaseTimelineView>(
        coreAdminEndpoints.taskCaseTimeline(taskId),
      );
    },

  getTaskFinalizationAuthority(taskId: string): Promise<CoreTaskFinalizationAuthorityView> {
    return coreTenantApiGet<CoreTaskFinalizationAuthorityView>(coreAdminEndpoints.taskFinalizationAuthority(taskId));
  },

  getTaskFlowMatchAuthority(taskId: string): Promise<CoreTaskFlowMatchAuthorityView> {
    return coreTenantApiGet<CoreTaskFlowMatchAuthorityView>(coreAdminEndpoints.taskFlowMatchAuthority(taskId));
  },

  retryTaskFinalization(taskId: string, reason: string): Promise<CoreTaskFinalizationAuthorityView> {
    return coreTenantApiPost<CoreTaskFinalizationAuthorityView>(
      coreAdminEndpoints.taskFinalizationRetry(taskId),
      { reason },
      { headers: { "Idempotency-Key": `task-finalization-retry-${taskId}-${Date.now()}` } },
    );
  },

  getTaskFinalizationRecovery(limit = 100): Promise<CoreTaskFinalizationRecoveryItem[]> {
    return coreTenantApiGet<CoreTaskFinalizationRecoveryItem[]>(
      `${coreAdminEndpoints.taskFinalizationRecovery}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getTaskDispatchRequirements(
      taskId: string,
    ): Promise<CoreTaskDispatchRequirements> {
      return coreTenantApiGet<CoreTaskDispatchRequirements>(
        coreAdminEndpoints.taskDispatchRequirements(taskId),
      );
    },

  getTaskEligibleAgents(
      taskId: string,
      limit = 500,
    ): Promise<CoreTaskEligibleAgentsResponse> {
      return coreTenantApiGet<CoreTaskEligibleAgentsResponse>(
        `${coreAdminEndpoints.taskEligibleAgents(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskEligibleAgentsV2(
      taskId: string,
      limit = 500,
    ): Promise<CoreDispatchEligibilityV2Response> {
      return coreTenantApiGet<CoreDispatchEligibilityV2Response>(
        `${coreAdminEndpoints.taskEligibleAgentsV2(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  async getTasksRuntimeView(): Promise<CoreTaskRuntimeView[]> {
      return normalizeCoreTaskRuntimeViewPayload(
        await coreTenantApiGet<unknown>(coreAdminEndpoints.tasksRuntimeView(requireCoreTenantContext())),
      );
    },

  getTaskDispatchRequests(
      taskId: string,
      limit = 100,
    ): Promise<CoreDispatchRequest[]> {
      return coreGetList<CoreDispatchRequest>(
        `${coreAdminEndpoints.taskDispatchRequests(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskDispatchAttemptHistory(
      taskId: string,
      limit = 100,
    ): Promise<CoreDispatchAttemptHistoryRecord[]> {
      return coreGetList<CoreDispatchAttemptHistoryRecord>(
        `${coreAdminEndpoints.taskDispatchAttemptHistory(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskDispatchLedger(
      taskId: string,
      limit = 100,
    ): Promise<CoreDispatchAttemptLedger[]> {
      return coreGetList<CoreDispatchAttemptLedger>(
        `${coreAdminEndpoints.taskDispatchLedger(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskCallbackInbox(
      taskId: string,
      limit = 100,
    ): Promise<CoreCallbackInboxEntry[]> {
      return coreGetList<CoreCallbackInboxEntry>(
        `${coreAdminEndpoints.taskCallbackInbox(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskCallbackInboxSummary(
      taskId: string,
      limit = 100,
    ): Promise<CoreCallbackInboxSummary> {
      return coreTenantApiGet<CoreCallbackInboxSummary>(
        `${coreAdminEndpoints.taskCallbackInboxSummary(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskRoutingDecisions(
      taskId: string,
      limit = 20,
    ): Promise<CoreRoutingDecisionRecord[]> {
      return coreGetList<CoreRoutingDecisionRecord>(
        `${coreAdminEndpoints.taskRoutingDecisions(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskTimeline(
      taskId: string,
      limit = 200,
    ): Promise<CoreDispatchTimelineResponse> {
      return coreTenantApiGet<CoreDispatchTimelineResponse>(
        `${coreAdminEndpoints.taskTimeline(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskIssueDedup(taskId: string): Promise<CoreTaskIssueDedupSummary> {
      return coreTenantApiGet<CoreTaskIssueDedupSummary>(
        coreAdminEndpoints.taskIssueDedup(taskId),
      );
    },

  getTaskDispatchEvidence(
      taskId: string,
      limit = 200,
    ): Promise<CoreTaskDispatchEvidenceView> {
      return coreTenantApiGet<CoreTaskDispatchEvidenceView>(
        `${coreAdminEndpoints.taskDispatchEvidence(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  getTaskRuntimeVerification(
      taskId: string,
      timeoutSeconds = 90,
      limit = 200,
    ): Promise<CoreTaskRuntimeVerificationView> {
      return coreTenantApiGet<CoreTaskRuntimeVerificationView>(
        `${coreAdminEndpoints.taskRuntimeVerification(taskId)}?timeoutSeconds=${encodeURIComponent(String(timeoutSeconds))}&limit=${encodeURIComponent(String(limit))}`,
      );
    },

  runTaskDispatchContractReadiness(
      taskId: string,
      body?: CoreTaskDispatchContractRepairRequest,
    ): Promise<CoreDispatchContractReadinessResponse> {
      return coreTenantApiPost<CoreDispatchContractReadinessResponse>(
        coreAdminEndpoints.taskDispatchContractReadiness(taskId),
        body ?? {},
      );
    },

  repairTaskDispatchContract(
      taskId: string,
      body?: CoreTaskDispatchContractRepairRequest,
    ): Promise<CoreDispatchContractBootstrapResponse> {
      return coreTenantApiPost<CoreDispatchContractBootstrapResponse>(
        coreAdminEndpoints.taskRepairDispatchContract(taskId),
        body ?? {},
      );
    },

  getTaskAdapterActions(
      taskId: string,
      limit = 100,
    ): Promise<CoreAdapterAction[]> {
      return coreGetList<CoreAdapterAction>(
        `${coreAdminEndpoints.adapterActionsByTask(taskId)}?limit=${encodeURIComponent(String(limit))}`,
      );
    },

  async getTaskFailureQueue(limit = 100): Promise<CoreAdminFailureQueueResponse> {
      return normalizeFailureQueueResponse(
        await coreTenantApiGet<CoreAdminFailureQueueResponse>(
          `${coreAdminEndpoints.taskFailureQueue}?limit=${encodeURIComponent(String(limit))}`,
        ),
      );
    },

  manualRetryTask(taskId: string, body?: unknown): Promise<CommandResult> {
      return coreTenantApiPost<CommandResult>(
        coreAdminEndpoints.taskManualRetry(taskId),
        body ?? {},
      );
    },

  deadLetterTask(taskId: string, body?: unknown): Promise<CommandResult> {
      return coreTenantApiPost<CommandResult>(
        coreAdminEndpoints.taskDeadLetter(taskId),
        body ?? {},
      );
    },

  escalateTask(taskId: string, body?: unknown): Promise<CommandResult> {
      return coreTenantApiPost<CommandResult>(
        coreAdminEndpoints.taskEscalate(taskId),
        body ?? {},
      );
    },

  reconcileTaskIssueLink(taskId: string): Promise<IssueLinkReconcileCommandResult> {
      return coreTenantApiPost<IssueLinkReconcileCommandResult>(
        coreAdminEndpoints.taskIssueLinkReconcile(taskId),
        {},
      );
    },

  getTaskOperationsView(
      taskId: string,
      include: Array<'overview' | 'execution' | 'issue' | 'relationships'> = ['overview', 'execution', 'issue', 'relationships'],
    ): Promise<CoreTaskOperationsView> {
      const requested = include.length ? include.join(',') : 'overview';
      return coreTenantApiGet<CoreTaskOperationsView>(
        `${coreAdminEndpoints.taskOperationsView(taskId)}?include=${encodeURIComponent(requested)}`,
      );
    },

  async getTaskRuntimeView(taskId: string): Promise<CoreTaskRuntimeView> {
      try {
        const runtimeDetail = await coreTenantApiGet<unknown>(
          coreAdminEndpoints.taskRuntimeView(taskId),
        );
        const normalized = normalizeCoreTaskRuntimeViewPayload(runtimeDetail)[0];
        if (normalized) return normalized;
        throw new ApiError(
          `Core task ${taskId} runtime-view response is empty`,
          200,
          undefined,
          "CORE_TASK_RUNTIME_VIEW_EMPTY",
        );
      } catch (error) {
        if (!isNotFoundOrUnsupportedApiError(error)) {
          throw error;
        }
      }
  
      try {
        const detail = await coreTenantApiGet<CoreTaskRecord>(
          coreAdminEndpoints.taskDetail(taskId),
        );
        const dispatches = await taskAdminApi.getTaskDispatchRequests(taskId, 100);
        return normalizeCoreTaskRecord(
          detail,
          firstDispatchForTask(taskId, dispatches),
        );
      } catch (error) {
        if (!isNotFoundOrUnsupportedApiError(error)) {
          throw error;
        }
  
        const tasks = await taskAdminApi.getTasksRuntimeView();
        const task = tasks.find((candidate) => candidate.taskId === taskId);
        if (!task) {
          throw new ApiError(`Core task ${taskId} not found`, 200, undefined, 'CORE_TASK_NOT_FOUND');
        }
        return task;
      }
    },

  getTaskA2AClassificationContract(): Promise<CoreTaskA2AClassificationFlowContract> {
      return coreTenantApiGet<CoreTaskA2AClassificationFlowContract>(coreAdminEndpoints.taskA2AClassificationContract);
    },

  submitTaskClassificationResult(
      taskId: string,
      body: CoreTaskClassificationRequest,
    ): Promise<CoreTaskClassificationResult> {
      return coreTenantApiPost<CoreTaskClassificationResult>(
        coreAdminEndpoints.taskClassificationResult(taskId),
        body,
      );
    },

  runTaskRemediationCommand(
      taskId: string,
      body: CoreTaskRemediationCommandRequest,
    ): Promise<CoreTaskRemediationCommandResult> {
      return coreTenantApiPost<CoreTaskRemediationCommandResult>(
        coreAdminEndpoints.taskCommands(taskId),
        body,
      );
    },

  retryTask(taskId: string): Promise<CommandResult> {
      return coreTenantApiPost<CommandResult>(coreAdminEndpoints.taskRetry(taskId), {});
    },

  retryDispatchRequest(dispatchRequestId: string): Promise<CommandResult> {
      return coreTenantApiPost<CommandResult>(
        coreAdminEndpoints.dispatchRequestRetry(dispatchRequestId),
        {},
      );
    },

  cancelTask(taskId: string, body?: unknown): Promise<CommandResult> {
      return coreTenantApiPost<CommandResult>(
        coreAdminEndpoints.taskCancel(taskId),
        body ?? {},
      );
    },

  reassignTask(taskId: string, body?: unknown): Promise<CommandResult> {
      return coreTenantApiPost<CommandResult>(
        coreAdminEndpoints.taskReassign(taskId),
        body ?? {},
      );
    },

  triggerTaskRecoveryNow(
      taskId: string,
      body?: CoreRecoveryGovernanceActionRequest,
    ): Promise<CoreRecoveryGovernanceActionResult> {
      return coreTenantApiPost<CoreRecoveryGovernanceActionResult>(
        coreAdminEndpoints.recoveryTriggerTaskNow(taskId),
        body ?? {},
      );
    },

  moveTaskToDeadLetter(
      taskId: string,
      body?: CoreRecoveryGovernanceActionRequest,
    ): Promise<CoreRecoveryGovernanceActionResult> {
      return coreTenantApiPost<CoreRecoveryGovernanceActionResult>(
        coreAdminEndpoints.recoveryTaskDeadLetter(taskId),
        body ?? {},
      );
    },

  restoreTaskFromDeadLetter(
      taskId: string,
      body?: CoreRecoveryGovernanceActionRequest,
    ): Promise<CoreRecoveryGovernanceActionResult> {
      return coreTenantApiPost<CoreRecoveryGovernanceActionResult>(
        coreAdminEndpoints.recoveryTaskRestoreDeadLetter(taskId),
        body ?? {},
      );
    },

  moveDispatchToDeadLetter(
      dispatchRequestId: string,
      body?: CoreRecoveryGovernanceActionRequest,
    ): Promise<CoreRecoveryGovernanceActionResult> {
      return coreTenantApiPost<CoreRecoveryGovernanceActionResult>(
        coreAdminEndpoints.recoveryDispatchDeadLetter(dispatchRequestId),
        body ?? {},
      );
    },

  restoreDispatchFromDeadLetter(
      dispatchRequestId: string,
      body?: CoreRecoveryGovernanceActionRequest,
    ): Promise<CoreRecoveryGovernanceActionResult> {
      return coreTenantApiPost<CoreRecoveryGovernanceActionResult>(
        coreAdminEndpoints.recoveryDispatchRestoreDeadLetter(dispatchRequestId),
        body ?? {},
      );
    },
} as const;

export { normalizeCoreTaskRecord, normalizeCoreTaskRuntimeViewPayload };
