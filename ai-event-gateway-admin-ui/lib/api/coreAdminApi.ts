import {
  coreApiDelete,
  coreApiGet,
  coreApiPost,
  coreApiPut,
  requireCoreTenantContext,
} from "@/lib/api/coreClient";
import { ApiError, isNotFoundOrUnsupportedApiError } from "@/lib/api/client";
import { coreAdminEndpoints } from "@/lib/api/endpoints";
import type {
  AgentCredentialIssueRequest,
  AgentProfileApprovalRequest,
  AgentEnrollmentApprovalRequest,
  AgentEnrollmentCreateRequest,
  AgentEnrollmentRequest,
  AgentGovernanceTableDiagnostic,
  AgentProfileUpdateRequest,
  AgentSecurityEvent,
  CoreAgentProfile,
  CoreAgentRuntimeCapabilityItem,
  CoreAgentRuntimeCapabilityProfile,
  CoreAgentRuntimeDescriptor,
  CoreDispatchTaskDefinition,
  CoreDispatchTaskDefinitionImpactPreview,
  CoreDispatchTaskDefinitionReviewCommand,
  CoreDispatchContractBootstrapRequest,
  CoreDispatchContractBootstrapResponse,
  CoreDispatchContractChainInspectionRequest,
  CoreDispatchContractChainInspectionResponse,
  CoreDispatchContractReadinessRequest,
  CoreDispatchContractReadinessResponse,
  CoreDispatchContractTraceRequest,
  CoreDispatchContractTraceResponse,
  CoreDispatchContractTestTaskRequest,
  CoreDispatchContractTestTaskResponse,
  CoreDispatchSourceSystemOption,
  CoreSourceSystem,
  CoreSourceSystemCommand,
  CoreAgentPoolView,
  CoreDispatchFlowAgentOptionView,
  CoreDispatchFlowAgentView,
  CoreDispatchFlowReadinessRequest,
  CoreDispatchFlowReadinessResponse,
  CoreDispatchFlowRuleView,
  CoreDispatchFlowRequiredSkillView,
  CoreDispatchFlowView,
  CoreDispatchFlowTraceChainView,
  CoreDispatchEventStage,
  CoreEventIntakeEnvelope,
  CoreEventIntakeDecisionResponse,
  CoreDispatchPolicy,
  CoreDispatchPolicyScope,
  CoreDispatchPolicyRequiredCapability,
  CoreDispatchPolicyRequiredRuntimeFeature,
  CoreDispatchPolicyQualityRule,
  CoreDispatchPolicyScoringRule,
  CoreAgentAssignmentProfile,
  CoreAssignmentProfileImpactAction,
  CoreAssignmentProfileImpactPreview,
  CoreAssignmentProfileRelationshipMap,
  CoreAssignmentProfilePolicyBinding,
  CoreAssignmentProfileCapabilityBinding,
  CoreAgentCapabilityCatalog,
  CoreAgentCapabilityAssignment,
  CoreAgentCapabilityCommand,
  CoreRuntimeFeatureCatalog,
  CoreRuntimeResource,
  CoreSupplyProfile,
  CoreAgentQualityMetricsDaily,
  CoreAgentQualityMetricsWindow,
  CoreRuntimeQualityMetricsDaily,
  CoreSupplyProfileQualitySnapshot,
  CoreAgentRuntimeBinding,
  CoreAgentRuntimeFeatureObservation,
  CoreAgentRuntimeFeatureTrust,
  CoreAgentRuntimeFeatureCommand,
  CoreAgentQualification,
  CoreAgentQualificationCommand,
  CoreAgentDispatchEligibility,
  CoreAgentCertificationProfile,
  CoreAgentCertificationRun,
  CoreAgentCertificationRunCommand,
  CoreAgentCertificationDecisionCommand,
  CoreAgentEnterpriseGovernanceSummary,
  CoreTaskDispatchRequirements,
  CoreTaskDispatchEvidenceView,
  CoreTaskRuntimeVerificationView,
  CoreTaskDispatchContractRepairRequest,
  CoreTaskEligibleAgentsResponse,
  CoreDispatchEligibilityV2Response,
  CoreAgentRuntimeLoadSnapshot,
  CoreAgentRuntimeView,
  CoreAgentSetupRequest,
  CoreAgentSetupReadinessResponse,
  CoreAgentOperationalView,
  CoreAgentLatestAuthFailureResponse,
  CoreAgentConnectionRepairActionRequest,
  CoreAgentConnectionRepairActionResult,
  CoreAgentConnectionRepairActionsResponse,
  CoreAgentSetupResponse,
  CoreDashboardSnapshot,
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchAttemptHistoryRecord,
  CoreDispatchAttemptLedger,
  CoreRoutingDecisionRecord,
  CoreDispatchUserFacingError,
  CoreDispatchRequest,
  CoreRecoveryOperationMetricsSnapshot,
  CoreDispatchTimelineResponse,
  CoreAdminFailureQueueResponse,
  CoreAdminFailureQueueItem,
  CoreRecoveryApprovalRequest,
  CoreRecoveryGovernanceActionRequest,
  CoreRecoveryGovernanceActionResult,
  CoreRecoveryOperatorRunbook,
  CoreTaskRecord,
  CoreTaskClassificationRequest,
  CoreTaskClassificationResult,
  CoreTaskIssueTracking,
  CoreTaskRuntimeSnapshot,
  CoreTaskRuntimeView,
  CoreTaskCaseTimelineView,
  CoreAdapterAction,
  CoreAdapterActionMetadata,
  CoreRuntimeDisconnectResult,
  CoreRuntimeDisconnectAllRequest,
  CoreRuntimeDisconnectReconcileRequest,
  CoreRuntimeDisconnectReconcileReport,
  CoreDuplicateRuntimeSecurityRequest,
  CoreDuplicateRuntimeResolveRequest,
  CoreAgentSecurityEnforcementResponse,
  CoreAgentSecurityEnforcementPolicy,
  CoreAgentSecurityEnforcementPolicyUpdateRequest,
  CoreAgentSkillDefinition,
  CoreAgentSkillEvaluationRequest,
  CoreAgentSkillEvaluationResult,
  CoreDispatchReadinessEvaluationRequest,
  CoreDispatchReadinessEvaluationResult,
  CoreDispatchReadinessTemplates,
  CoreDispatchRecipe,
  CoreDispatchRecipeEvaluationRequest,
  CoreDispatchRecipeEvaluationResult,
  CoreTaskCapabilityResolveRequest,
  CoreTaskCapabilityResolveResult,
  CoreAgentApprovedSkill,
  CoreAgentApprovedSkillSyncCommand,
  CoreAgentApprovedSkillSyncResult,
  CoreTaskDispatchContractResolveRequest,
  CoreTaskDispatchContractResolveResult,
  CoreAgentSkillRegistryMetadata,
  CoreAgentSkillVersion,
  CoreAgentSkillAuditEntry,
  CoreAgentSkillWorkflowCommand,
  CoreAgentSkillWorkflowResult,
  CoreAgentSkillDiffResult,
  CoreAgentSkillImpactAnalysisResult,
  CoreAgentSkillApprovalPolicy,
  CoreAgentCapabilityDriftReport,
  CoreSkillDriftPolicyEvaluationRequest,
  CoreSkillDriftPolicyEvaluationResponse,
  CoreAgentSkillDeprecationCommand,
  CoreAgentSkillDeprecationMigrationPlan,
  CoreAgentSkillDeprecationPlan,
  CoreAgentSkillDependencyCommand,
  CoreAgentSkillDependencyEdge,
  CoreAgentSkillDependencyGraph,
  CoreAgentSkillRemediationProposal,
  CoreAgentRemediationProposal,
  CoreAgentRemediationProposalRequest,
  CoreAgentRemediationWorkflow,
  CoreAgentRemediationStaleLeaseQueue,
  CoreAgentRemediationRecoveredLeaseQueue,
  CoreAgentRemediationStaleLeaseRecoveryRun,
  CoreAgentRemediationWorkflowCreateRequest,
  CoreAgentRemediationWorkflowDecisionRequest,
  CoreAdapterExecutorAuditRecord,
  CoreIssueTrackingRedmineCollectionResult,
  CoreIssueTrackingRedmineConnectionResult,
  CoreIssueTrackingRedmineDiagnostics,
  CoreIssueTrackingRedmineTestIssueRequest,
  CoreIssueTrackingRedmineTestIssueResult,
  CoreEnforceObservabilitySnapshot,
  CoreEnforceRoutingAuditRecord,
  CoreEnforceOperatorIncidentRequest,
  CoreEnforceOperatorIncidentResult,
  CoreEnforceLegacyFinalReportItem,
  CoreEnforceArtifactRetentionRecord,
} from "@/lib/types/core";
import type { CommandResult } from "@/lib/types/admin";

type PageLike<T> =
  | T[]
  | {
      content?: T[];
      items?: T[];
      records?: T[];
      rows?: T[];
      data?: T[];
    };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function toList<T>(value: PageLike<T>): T[] {
  if (Array.isArray(value)) return value;
  return (
    value.content ??
    value.items ??
    value.records ??
    value.rows ??
    value.data ??
    []
  );
}

async function coreGetList<T>(path: string): Promise<T[]> {
  return toList(await coreApiGet<PageLike<T>>(path));
}

function requireTenantId(tenantId: string | undefined, operation: string): string {
  void operation;
  return requireCoreTenantContext(tenantId);
}

function pickString(
  record: Record<string, unknown>,
  keys: string[],
): string | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number" && Number.isFinite(value))
      return String(value);
  }
  return undefined;
}

function pickDispatchEventStage(
  record: Record<string, unknown>,
  keys: string[],
  fallback?: string,
): CoreDispatchEventStage | undefined {
  const raw = fallback ?? pickString(record, keys);
  const normalized = String(raw ?? '').trim().toUpperCase();
  if (['EXTERNAL', 'A2A', 'RESULT', 'ISSUE', 'CALLBACK'].includes(normalized)) {
    return normalized as CoreDispatchEventStage;
  }
  return undefined;
}

function firstDispatchForTask(
  taskId: string,
  dispatchRequests: CoreDispatchRequest[],
): CoreDispatchRequest | undefined {
  return dispatchRequests
    .filter((dispatch) => dispatch.taskId === taskId)
    .sort(
      (left, right) =>
        Date.parse(right.updatedAt ?? right.createdAt ?? "") -
        Date.parse(left.updatedAt ?? left.createdAt ?? ""),
    )[0];
}

export function normalizeCoreAgentRuntimeViewPayload(
  value: unknown,
): CoreAgentProfile[] {
  const list = toList<unknown>(value as PageLike<unknown>);
  return list
    .map((item) => {
      if (!isRecord(item)) return undefined;
      const maybeRuntimeView = item as Partial<CoreAgentRuntimeView> &
        Record<string, unknown>;
      const profile = maybeRuntimeView.profile;
      if (isRecord(profile)) return profile as CoreAgentProfile;
      return item as unknown as CoreAgentProfile;
    })
    .filter((profile): profile is CoreAgentProfile =>
      Boolean(profile?.agentId),
    );
}

function normalizeIssueTracking(
  value: unknown,
): CoreTaskIssueTracking | undefined {
  return isRecord(value) ? (value as CoreTaskIssueTracking) : undefined;
}

function issueTrackingTaskId(value: CoreTaskIssueTracking): string | undefined {
  return pickString(value as Record<string, unknown>, ["taskId", "task_id"]);
}

function issueTrackingMap(value: unknown): Map<string, CoreTaskIssueTracking> {
  const map = new Map<string, CoreTaskIssueTracking>();
  if (Array.isArray(value)) {
    value.forEach((item) => {
      const link = normalizeIssueTracking(item);
      const taskId = link ? issueTrackingTaskId(link) : undefined;
      if (link && taskId) map.set(taskId, link);
    });
    return map;
  }
  if (isRecord(value)) {
    Object.entries(value).forEach(([taskId, item]) => {
      const link = normalizeIssueTracking(item);
      if (link && taskId)
        map.set(taskId, { ...link, taskId: link.taskId ?? taskId });
    });
  }
  return map;
}

function normalizeRoutingDecisionRecord(
  value: unknown,
  fallbackTaskId?: string,
): CoreRoutingDecisionRecord | undefined {
  if (!isRecord(value)) return undefined;
  const taskId = pickString(value, ["taskId", "task_id"]) ?? fallbackTaskId;
  if (!taskId) return undefined;
  const decisionId =
    pickString(value, ["decisionId", "decision_id", "id"]) ??
    `${taskId}:latest-routing-decision`;
  const partial = value as Partial<CoreRoutingDecisionRecord>;
  return {
    ...partial,
    decisionId,
    taskId,
  };
}

function routingDecisionMap(
  value: unknown,
): Map<string, CoreRoutingDecisionRecord> {
  const map = new Map<string, CoreRoutingDecisionRecord>();
  if (!value) return map;
  if (Array.isArray(value)) {
    value.forEach((decision) => {
      const record = normalizeRoutingDecisionRecord(decision);
      if (record) map.set(record.taskId, record);
    });
    return map;
  }
  if (isRecord(value)) {
    Object.entries(value).forEach(([taskId, decision]) => {
      const record = normalizeRoutingDecisionRecord(decision, taskId);
      if (record) map.set(record.taskId, record);
    });
  }
  return map;
}

function withIssueTrackingPayload(
  task: CoreTaskRuntimeView,
): CoreTaskRuntimeView {
  if (!task.issueTracking) return task;
  const payload = isRecord(task.payload) ? task.payload : {};
  return {
    ...task,
    payload: {
      ...payload,
      issueTracking: task.issueTracking,
    },
  };
}

export function normalizeCoreTaskRuntimeViewPayload(
  value: unknown,
): CoreTaskRuntimeView[] {
  if (Array.isArray(value))
    return value.map((task) =>
      withIssueTrackingPayload(task as CoreTaskRuntimeView),
    );

  if (!isRecord(value)) return [];

  if (isRecord(value.task)) {
    const detail = value as {
      task: CoreTaskRecord;
      dispatchRequests?: CoreDispatchRequest[];
      latestRoutingDecision?: CoreRoutingDecisionRecord;
      issueTracking?: CoreTaskIssueTracking;
    };
    const dispatchRequests = Array.isArray(detail.dispatchRequests)
      ? detail.dispatchRequests
      : [];
    return [
      normalizeCoreTaskRecord(
        detail.task,
        firstDispatchForTask(detail.task.taskId, dispatchRequests),
        normalizeIssueTracking(detail.issueTracking),
        detail.latestRoutingDecision,
      ),
    ].filter((task): task is CoreTaskRuntimeView => Boolean(task.taskId));
  }

  const snapshot = value as Partial<CoreTaskRuntimeSnapshot> &
    Record<string, unknown>;
  if (Array.isArray(snapshot.tasks)) {
    const tasks = snapshot.tasks as CoreTaskRecord[];
    const dispatchRequests = Array.isArray(snapshot.dispatchRequests)
      ? (snapshot.dispatchRequests as CoreDispatchRequest[])
      : [];
    const issueLinks = issueTrackingMap(
      snapshot.taskIssueLinks ?? snapshot.issueTrackingByTask,
    );
    const latestRoutingDecisions = routingDecisionMap(
      snapshot.latestRoutingDecisions ?? snapshot.routingDecisionsByTask,
    );

    return tasks
      .map((task) =>
        normalizeCoreTaskRecord(
          task,
          firstDispatchForTask(task.taskId, dispatchRequests),
          issueLinks.get(task.taskId),
          latestRoutingDecisions.get(task.taskId),
        ),
      )
      .filter((task): task is CoreTaskRuntimeView => Boolean(task.taskId));
  }

  return toList<CoreTaskRuntimeView>(
    value as PageLike<CoreTaskRuntimeView>,
  ).map(withIssueTrackingPayload);
}

const terminalFailureTaskStatuses = new Set([
  "FAILED",
  "TIMED_OUT",
  "TIMEOUT",
  "DEAD_LETTER",
  "CANCELLED",
]);
const terminalFailureDispatchStatuses = new Set([
  "DELIVERY_FAILED",
  "FAILED",
  "TIMED_OUT",
  "TIMEOUT",
  "DEAD_LETTER",
  "CANCELLED",
]);

function normalizedStatus(value: unknown): string {
  return typeof value === "string" ? value.trim().toUpperCase() : "";
}

function dispatchReason(dispatch?: CoreDispatchRequest): string {
  return String(dispatch?.reason ?? dispatch?.lastError ?? "").trim();
}

function deriveBlockedReason(
  dispatch?: CoreDispatchRequest,
): string | undefined {
  const reason = dispatchReason(dispatch).toLowerCase();
  const status = normalizedStatus(dispatch?.status);
  if (reason.includes("client is disabled")) return "DISPATCH_CLIENT_DISABLED";
  if (reason.includes("paused")) return "DISPATCH_EXECUTION_PAUSED";
  if (reason.includes("explicit operator execution"))
    return "MANUAL_EXECUTION_HOLD";
  if (
    ["FAILED", "TIMED_OUT", "TIMEOUT", "DEAD_LETTER", "CANCELLED"].includes(
      status,
    )
  )
    return status;
  return undefined;
}

function deriveDispatchExecutionStatus(
  dispatch?: CoreDispatchRequest,
): string | undefined {
  const status = normalizedStatus(dispatch?.status);
  const blocked = deriveBlockedReason(dispatch);
  if (!dispatch) return undefined;
  if (
    blocked === "DISPATCH_CLIENT_DISABLED" ||
    blocked === "DISPATCH_EXECUTION_PAUSED" ||
    blocked === "MANUAL_EXECUTION_HOLD"
  )
    return "BLOCKED";
  if (status === "PENDING_REVIEW") return "WAITING_REVIEW";
  if (status === "APPROVED") return "QUEUED";
  if (status === "DISPATCHING") return "EXECUTING";
  if (status === "DISPATCHED") return "DELIVERED";
  if (status === "ACKED") return "ACKED";
  if (status === "RUNNING") return "RUNNING";
  if (status === "COMPLETED") return "COMPLETED";
  if (status === "RETRY_WAITING") return "RETRY_WAIT";
  if (terminalFailureDispatchStatuses.has(status)) return "FAILED";
  return status || undefined;
}

function deriveDispatchDeliveryStatus(
  dispatch?: CoreDispatchRequest,
): string | undefined {
  const status = normalizedStatus(dispatch?.status);
  if (!dispatch) return undefined;
  if (status === "APPROVED") return "NOT_DELIVERED";
  if (status === "DISPATCHING") return "DELIVERING";
  if (["DISPATCHED", "ACKED", "RUNNING", "COMPLETED"].includes(status))
    return "DELIVERED_TO_GATEWAY";
  if (
    ["FAILED", "TIMED_OUT", "TIMEOUT", "DEAD_LETTER", "CANCELLED"].includes(
      status,
    )
  )
    return "DELIVERY_FAILED";
  if (status === "RETRY_WAITING") return "RETRY_WAIT";
  return status || undefined;
}

function deriveNextAction(dispatch?: CoreDispatchRequest): string | undefined {
  if (!dispatch) return undefined;
  const executionStatus = deriveDispatchExecutionStatus(dispatch);
  const blocked = deriveBlockedReason(dispatch);
  if (blocked === "DISPATCH_CLIENT_DISABLED") return "ENABLE_DISPATCH_CLIENT";
  if (blocked === "DISPATCH_EXECUTION_PAUSED")
    return "RESUME_DISPATCH_EXECUTION";
  if (blocked === "MANUAL_EXECUTION_HOLD") return "EXECUTE_OR_RELEASE_HOLD";
  if (executionStatus === "QUEUED") return "WAIT_FOR_AUTO_DISPATCH_WORKER";
  if (executionStatus === "EXECUTING") return "WAIT_FOR_GATEWAY_DELIVERY";
  if (executionStatus === "DELIVERED") return "WAIT_FOR_AGENT_ACK";
  if (executionStatus === "ACKED" || executionStatus === "RUNNING")
    return "WAIT_FOR_AGENT_RESULT";
  if (executionStatus === "RETRY_WAIT")
    return "WAIT_FOR_RETRY_OR_TRIGGER_RECOVERY";
  if (executionStatus === "FAILED") return "RETRY_OR_MOVE_TO_DEAD_LETTER";
  if (executionStatus === "COMPLETED") return "NONE";
  return undefined;
}

function taskFailureReason(
  task: CoreTaskRecord,
  dispatch?: CoreDispatchRequest,
): string | undefined {
  const taskStatus = normalizedStatus(task.status);
  const dispatchStatus = normalizedStatus(dispatch?.status);

  if (terminalFailureTaskStatuses.has(taskStatus))
    return task.lifecycleReason ?? dispatch?.lastError ?? dispatch?.reason;
  if (terminalFailureDispatchStatuses.has(dispatchStatus))
    return dispatch?.lastError ?? dispatch?.reason;
  return undefined;
}

function taskDispatchWaitReason(
  task: CoreTaskRecord,
  dispatch?: CoreDispatchRequest,
  userFacingDispatchError?: CoreDispatchUserFacingError,
): string | undefined {
  const taskStatus = normalizedStatus(task.status);
  const dispatchStatus = normalizedStatus(dispatch?.status);
  if (
    taskStatus === "RETRY_WAIT" ||
    dispatchStatus === "RETRY_WAIT" ||
    dispatchStatus === "RETRY_WAITING" ||
    Boolean(task.nextDispatchAttemptAt) ||
    Boolean(task.dispatchRetryReason)
  ) {
    return (
      task.dispatchRetryReason ??
      userFacingDispatchError?.message ??
      task.lifecycleReason ??
      dispatch?.reason
    );
  }
  return undefined;
}

function taskBlockedReason(
  task: CoreTaskRecord,
  dispatch: CoreDispatchRequest | undefined,
  userFacingDispatchError?: CoreDispatchUserFacingError,
): string | undefined {
  const explicitBlocked = deriveBlockedReason(dispatch);
  if (explicitBlocked) return explicitBlocked;

  const taskStatus = normalizedStatus(task.status);
  if (
    taskStatus === "RETRY_WAIT" ||
    terminalFailureTaskStatuses.has(taskStatus) ||
    task.nextDispatchAttemptAt ||
    task.dispatchRetryReason
  ) {
    return undefined;
  }

  if (userFacingDispatchError?.code?.startsWith("DISPATCH_")) {
    return userFacingDispatchError.code;
  }
  return undefined;
}

function taskReasonCategory(
  task: CoreTaskRecord,
  dispatch: CoreDispatchRequest | undefined,
  blockedReason?: string,
  failureReason?: string,
  dispatchWaitReason?: string,
): string | undefined {
  const taskStatus = normalizedStatus(task.status);
  const dispatchStatus = normalizedStatus(dispatch?.status);
  if (dispatchWaitReason || taskStatus === "RETRY_WAIT" || dispatchStatus === "RETRY_WAIT" || dispatchStatus === "RETRY_WAITING")
    return "WAITING_RETRY";
  if (taskStatus === "DEAD_LETTER") return "DEAD_LETTER";
  if (taskStatus === "ESCALATED") return "ESCALATED";
  if (["ORPHANED", "RECONCILING"].includes(taskStatus))
    return "NEEDS_OPERATOR_RECONCILIATION";
  if (failureReason || terminalFailureTaskStatuses.has(taskStatus))
    return "TERMINAL_FAILURE";
  if (blockedReason) return "DISPATCH_BLOCKED";
  return undefined;
}

function normalizeFailureQueueItem(
  item: CoreAdminFailureQueueItem,
): CoreAdminFailureQueueItem {
  const userFacingDispatchError =
    item.userFacingDispatchError ?? item.latestRoutingDecision?.userFacingError;
  const fallbackTask = {
    status: item.status,
    lifecycleReason: item.lifecycleReason,
    dispatchRetryReason: item.dispatchRetryReason,
    nextDispatchAttemptAt: item.nextDispatchAttemptAt,
    errorCode: item.errorCode,
  } as CoreTaskRecord;
  const dispatchWaitReason =
    item.dispatchWaitReason ??
    taskDispatchWaitReason(fallbackTask, undefined, userFacingDispatchError);
  const failureReason = item.failureReason ?? taskFailureReason(fallbackTask);
  const blockedReason =
    item.blockedReason ??
    taskBlockedReason(fallbackTask, undefined, userFacingDispatchError);
  const reasonCategory =
    item.reasonCategory ??
    taskReasonCategory(
      fallbackTask,
      undefined,
      blockedReason,
      failureReason,
      dispatchWaitReason,
    );
  return {
    ...item,
    reasonCategory,
    blockedReason,
    failureReason,
    dispatchWaitReason,
    userFacingDispatchError,
  };
}

function normalizeFailureQueueResponse(
  response: CoreAdminFailureQueueResponse,
): CoreAdminFailureQueueResponse {
  const items = (response.items ?? []).map(normalizeFailureQueueItem);
  const reasonCategoryCounts =
    response.reasonCategoryCounts ??
    countBy<CoreAdminFailureQueueItem>(items, (item) => item.reasonCategory ?? "UNKNOWN");
  const dispatchErrorCounts =
    response.dispatchErrorCounts ??
    countBy<CoreAdminFailureQueueItem>(items, (item) => item.userFacingDispatchError?.code);
  return {
    ...response,
    items,
    reasonCategoryCounts,
    dispatchErrorCounts,
  };
}

function countBy<T>(items: T[], selector: (item: T) => string | undefined): Record<string, number> {
  return items.reduce<Record<string, number>>((acc, item) => {
    const key = selector(item);
    if (!key) return acc;
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
}

export function normalizeCoreTaskRecord(
  task: CoreTaskRecord,
  dispatch?: CoreDispatchRequest,
  authoritativeIssueTracking?: CoreTaskIssueTracking,
  latestRoutingDecision?: CoreRoutingDecisionRecord,
): CoreTaskRuntimeView {
  const taskRecord = task as CoreTaskRecord & Record<string, unknown>;
  const embeddedLatestRoutingDecision = normalizeRoutingDecisionRecord(
    taskRecord.latestRoutingDecision,
    pickString(taskRecord, ["taskId", "task_id"]),
  );
  const effectiveLatestRoutingDecision =
    latestRoutingDecision ?? embeddedLatestRoutingDecision;
  const embeddedUserFacingDispatchError = isRecord(
    taskRecord.userFacingDispatchError,
  )
    ? (taskRecord.userFacingDispatchError as CoreDispatchUserFacingError)
    : undefined;
  const userFacingDispatchError =
    embeddedUserFacingDispatchError ??
    effectiveLatestRoutingDecision?.userFacingError;
  const issueTracking =
    authoritativeIssueTracking ??
    normalizeIssueTracking(taskRecord.issueTracking);
  const blockedReason = taskBlockedReason(task, dispatch, userFacingDispatchError);
  const failureReason = taskFailureReason(task, dispatch);
  const dispatchWaitReason = taskDispatchWaitReason(
    task,
    dispatch,
    userFacingDispatchError,
  );
  const reasonCategory = taskReasonCategory(
    task,
    dispatch,
    blockedReason,
    failureReason,
    dispatchWaitReason,
  );
  return {
    taskId: task.taskId ?? pickString(taskRecord, ["id", "task_id"]) ?? "",
    traceId: task.traceId ?? pickString(taskRecord, ["trace_id"]),
    incidentId: task.incidentId,
    sourceEventId:
      task.sourceEventId ??
      pickString(taskRecord, ["sourceEventId", "source_event_id"]),
    taskType: task.taskType,
    taskTypeCode:
      task.taskTypeCode ?? pickString(taskRecord, ["taskTypeCode", "task_type_code"]),
    effectiveTaskTypeCode:
      task.effectiveTaskTypeCode ??
      pickString(taskRecord, ["effectiveTaskTypeCode", "effective_task_type_code"]) ??
      task.taskTypeCode ??
      pickString(taskRecord, ["taskTypeCode", "task_type_code"]) ??
      task.taskType,
    status: task.status,
    priority: task.priority,
    tenantId:
      task.tenantId ?? pickString(taskRecord, ["tenantId", "tenant_id"]),
    sourceSystem: pickString(taskRecord, [
      "sourceSystem",
      "source_system",
      "systemCode",
      "system_code",
      "source",
    ]),
    siteId: task.siteId ?? pickString(taskRecord, ["siteId", "site_id"]),
    plantId: task.plantId ?? pickString(taskRecord, ["plantId", "plant_id"]),
    objectType:
      task.objectType ?? pickString(taskRecord, ["objectType", "object_type"]),
    objectId:
      task.objectId ?? pickString(taskRecord, ["objectId", "object_id"]),
    eventType:
      task.eventType ?? pickString(taskRecord, ["eventType", "event_type"]),
    errorCode:
      task.errorCode ?? pickString(taskRecord, ["errorCode", "error_code"]),
    eventStage: pickDispatchEventStage(taskRecord, ["eventStage", "event_stage"], task.eventStage),
    originSourceSystem:
      task.originSourceSystem ??
      pickString(taskRecord, ["originSourceSystem", "origin_source_system"]),
    targetSystem:
      task.targetSystem ?? pickString(taskRecord, ["targetSystem", "target_system"]),
    requestedSkill:
      task.requestedSkill ?? pickString(taskRecord, ["requestedSkill", "requested_skill"]),
    handoffMode:
      task.handoffMode ?? pickString(taskRecord, ["handoffMode", "handoff_mode"]),
    correlationId:
      task.correlationId ?? pickString(taskRecord, ["correlationId", "correlation_id"]),
    parentTaskId:
      task.parentTaskId ?? pickString(taskRecord, ["parentTaskId", "parent_task_id"]),
    matchedFlowId:
      task.matchedFlowId ?? pickString(taskRecord, ["matchedFlowId", "matched_flow_id"]),
    matchedRuleId:
      task.matchedRuleId ?? pickString(taskRecord, ["matchedRuleId", "matched_rule_id"]),
    assignedPoolId:
      task.assignedPoolId ?? pickString(taskRecord, ["assignedPoolId", "assigned_pool_id"]),
    targetPoolId:
      task.targetPoolId ?? pickString(taskRecord, ["targetPoolId", "target_pool_id"]),
    classificationStatus:
      task.classificationStatus ?? pickString(taskRecord, ["classificationStatus", "classification_status"]),
    classificationResultJson:
      task.classificationResultJson ?? taskRecord?.["classification_result_json"],
    routingPath:
      task.routingPath ?? pickString(taskRecord, ["routingPath", "routing_path"]),
    routingPolicy:
      task.routingPolicy ??
      pickString(taskRecord, ["routingPolicy", "routing_policy"]),
    createdReason:
      task.createdReason ??
      pickString(taskRecord, ["createdReason", "created_reason"]),
    occurrenceCountAtCreation: task.occurrenceCountAtCreation,
    assignedAgentId: task.assignedAgentId ?? dispatch?.agentId,
    requiredCapabilities: task.requiredCapabilities ?? [],
    createdAt: task.createdAt,
    updatedAt:
      task.updatedAt ?? task.terminalAt ?? task.timeoutAt ?? task.createdAt,
    dispatchRequestId: dispatch?.dispatchRequestId,
    dispatchStatus: dispatch?.status,
    dispatchExecutionStatus: deriveDispatchExecutionStatus(dispatch),
    dispatchDeliveryStatus: deriveDispatchDeliveryStatus(dispatch),
    blockedReason,
    nextAction: deriveNextAction(dispatch),
    callbackStatus: dispatch?.lastCallbackId ? "CALLBACK_RECEIVED" : undefined,
    lifecycleReason: task.lifecycleReason,
    failureReason,
    dispatchWaitReason,
    reasonCategory,
    nextDispatchAttemptAt: task.nextDispatchAttemptAt,
    dispatchAttemptCount: task.dispatchAttemptCount,
    dispatchRetryReason: task.dispatchRetryReason,
    dispatchRecoveryClaimedBy: task.dispatchRecoveryClaimedBy,
    dispatchRecoveryClaimUntil: task.dispatchRecoveryClaimUntil,
    latestRoutingDecision: effectiveLatestRoutingDecision,
    userFacingDispatchError,
    issueTracking,
    payload: {
      task,
      dispatch,
      ...(effectiveLatestRoutingDecision
        ? { latestRoutingDecision: effectiveLatestRoutingDecision }
        : {}),
      ...(userFacingDispatchError ? { userFacingDispatchError } : {}),
      ...(issueTracking ? { issueTracking } : {}),
    },
  };
}

export const coreAdminApi = {

  getEnforceObservabilitySnapshot(): Promise<CoreEnforceObservabilitySnapshot> {
    return coreApiGet<CoreEnforceObservabilitySnapshot>(
      coreAdminEndpoints.enforceObservabilitySnapshot,
    );
  },

  searchEnforceRoutingAudit(params?: { taskId?: string; agentId?: string; blockingCode?: string; policyCode?: string; window?: string; limit?: number }): Promise<CoreEnforceRoutingAuditRecord[]> {
    const query = new URLSearchParams();
    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && String(value).trim()) query.set(key, String(value));
    });
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return coreGetList<CoreEnforceRoutingAuditRecord>(
      `${coreAdminEndpoints.enforceRoutingAuditSearch}${suffix}`,
    );
  },

  createEnforceOperatorIncident(body: CoreEnforceOperatorIncidentRequest): Promise<CoreEnforceOperatorIncidentResult> {
    return coreApiPost<CoreEnforceOperatorIncidentResult>(
      coreAdminEndpoints.enforceOperatorIncidents,
      body,
    );
  },

  getEnforceLegacyFinalReport(): Promise<CoreEnforceLegacyFinalReportItem[]> {
    return coreGetList<CoreEnforceLegacyFinalReportItem>(
      coreAdminEndpoints.enforceLegacyFinalReport,
    );
  },

  getEnforceArtifactRetention(): Promise<CoreEnforceArtifactRetentionRecord[]> {
    return coreGetList<CoreEnforceArtifactRetentionRecord>(
      coreAdminEndpoints.enforceArtifactRetention,
    );
  },

  getTaskCaseTimeline(taskId: string): Promise<CoreTaskCaseTimelineView> {
    return coreApiGet<CoreTaskCaseTimelineView>(
      coreAdminEndpoints.taskCaseTimeline(taskId),
    );
  },

  getDashboardSnapshot(): Promise<CoreDashboardSnapshot> {
    return coreApiGet<CoreDashboardSnapshot>(
      coreAdminEndpoints.dashboardSnapshot,
    );
  },

  getAgentGovernanceSummary(): Promise<Record<string, unknown>> {
    return coreApiGet<Record<string, unknown>>(
      coreAdminEndpoints.agentGovernanceSummary,
    );
  },

  getAgentGovernanceMetadata(): Promise<Record<string, unknown>> {
    return coreApiGet<Record<string, unknown>>(
      coreAdminEndpoints.agentGovernanceMetadata,
    );
  },

  getAgentGovernanceTableDiagnostics(): Promise<
    AgentGovernanceTableDiagnostic[]
  > {
    return coreGetList<AgentGovernanceTableDiagnostic>(
      coreAdminEndpoints.agentGovernanceTableDiagnostics,
    );
  },

  getAgentEnrollments(): Promise<AgentEnrollmentRequest[]> {
    return coreGetList<AgentEnrollmentRequest>(
      coreAdminEndpoints.agentEnrollments,
    );
  },

  createAgentEnrollment(
    body: AgentEnrollmentCreateRequest,
  ): Promise<AgentEnrollmentRequest> {
    return coreApiPost<AgentEnrollmentRequest>(
      coreAdminEndpoints.agentEnrollments,
      body,
    );
  },

  setupAgent(body: CoreAgentSetupRequest): Promise<CoreAgentSetupResponse> {
    return coreApiPost<CoreAgentSetupResponse>(
      coreAdminEndpoints.agentSetup,
      body,
    );
  },

  getAgentSetupReadiness(agentId: string): Promise<CoreAgentSetupReadinessResponse> {
    return coreApiGet<CoreAgentSetupReadinessResponse>(
      coreAdminEndpoints.agentSetupReadiness(agentId),
    );
  },

  getAgentOperationalView(agentId: string): Promise<CoreAgentOperationalView> {
    return coreApiGet<CoreAgentOperationalView>(
      coreAdminEndpoints.agentOperationalView(agentId),
    );
  },

  approveAgentEnrollment(
    enrollmentId: string,
    body?: AgentEnrollmentApprovalRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentEnrollmentApprove(enrollmentId),
      body ?? {},
    );
  },

  rejectAgentEnrollment(
    enrollmentId: string,
    body?: unknown,
  ): Promise<AgentEnrollmentRequest> {
    return coreApiPost<AgentEnrollmentRequest>(
      coreAdminEndpoints.agentEnrollmentReject(enrollmentId),
      body ?? {},
    );
  },

  getAgents(): Promise<CoreAgentProfile[]> {
    return coreGetList<CoreAgentProfile>(coreAdminEndpoints.agents);
  },

  async getAgentsRuntimeView(): Promise<CoreAgentProfile[]> {
    return normalizeCoreAgentRuntimeViewPayload(
      await coreApiGet<unknown>(coreAdminEndpoints.agentsRuntimeView),
    );
  },

  getAgent(agentId: string): Promise<CoreAgentProfile> {
    return coreApiGet<CoreAgentProfile>(
      coreAdminEndpoints.agentDetail(agentId),
    );
  },

  updateAgentProfile(
    agentId: string,
    body: AgentProfileUpdateRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPut<CoreAgentProfile>(
      coreAdminEndpoints.agentUpdate(agentId),
      body,
    );
  },

  enableAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentEnable(agentId),
      body ?? {},
    );
  },

  disableAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentDisable(agentId),
      body ?? {},
    );
  },

  suspendAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentSuspend(agentId),
      body ?? {},
    );
  },

  revokeAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentRevoke(agentId),
      body ?? {},
    );
  },

  approveAgent(
    agentId: string,
    body?: AgentProfileApprovalRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentApprove(agentId),
      body ?? {},
    );
  },

  issueAgentCredential(
    agentId: string,
    body: AgentCredentialIssueRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentCredentialIssue(agentId),
      body,
    );
  },



  getDispatchPoliciesV2(status?: string | null, tenantId = ""): Promise<CoreDispatchPolicy[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch policy lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchPolicy>(`${coreAdminEndpoints.dispatchPoliciesV2}${suffix}`);
  },

  upsertDispatchPolicyV2(policyCode: string, body: CoreDispatchPolicy, tenantId = ""): Promise<CoreDispatchPolicy> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch policy update");
    const suffix = `?tenantId=${encodeURIComponent(scopedTenantId)}`;
    return coreApiPut<CoreDispatchPolicy>(`${coreAdminEndpoints.dispatchPolicyV2(policyCode)}${suffix}`, body);
  },

  addDispatchPolicyScope(policyCode: string, body: CoreDispatchPolicyScope): Promise<CoreDispatchPolicyScope> {
    return coreApiPost<CoreDispatchPolicyScope>(coreAdminEndpoints.dispatchPolicyScopes(policyCode), body);
  },

  addDispatchPolicyRequiredCapability(policyCode: string, body: CoreDispatchPolicyRequiredCapability): Promise<CoreDispatchPolicyRequiredCapability> {
    return coreApiPost<CoreDispatchPolicyRequiredCapability>(coreAdminEndpoints.dispatchPolicyRequiredCapabilities(policyCode), body);
  },

  addDispatchPolicyRequiredRuntimeFeature(policyCode: string, body: CoreDispatchPolicyRequiredRuntimeFeature): Promise<CoreDispatchPolicyRequiredRuntimeFeature> {
    return coreApiPost<CoreDispatchPolicyRequiredRuntimeFeature>(coreAdminEndpoints.dispatchPolicyRequiredRuntimeFeatures(policyCode), body);
  },

  addDispatchPolicyQualityRule(policyCode: string, body: CoreDispatchPolicyQualityRule): Promise<CoreDispatchPolicyQualityRule> {
    return coreApiPost<CoreDispatchPolicyQualityRule>(coreAdminEndpoints.dispatchPolicyQualityRules(policyCode), body);
  },

  addDispatchPolicyScoringRule(policyCode: string, body: CoreDispatchPolicyScoringRule): Promise<CoreDispatchPolicyScoringRule> {
    return coreApiPost<CoreDispatchPolicyScoringRule>(coreAdminEndpoints.dispatchPolicyScoringRules(policyCode), body);
  },

  getDispatchTaskDefinitions(
    status?: string | null,
    tenantId = "",
  ): Promise<CoreDispatchTaskDefinition[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchTaskDefinition>(
      `${coreAdminEndpoints.dispatchTaskDefinitions}${params}`,
    );
  },




  getSourceSystems(tenantId = ""): Promise<CoreSourceSystem[]> {
    const scopedTenantId = requireTenantId(tenantId, "source-system lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreGetList<CoreSourceSystem>(`${coreAdminEndpoints.sourceSystems}?${query.toString()}`);
  },

  createSourceSystem(tenantId = "", body: CoreSourceSystemCommand): Promise<CoreSourceSystem> {
    const scopedTenantId = requireTenantId(tenantId, "source-system create");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPost<CoreSourceSystem>(`${coreAdminEndpoints.sourceSystems}?${query.toString()}`, { ...body, tenantId: scopedTenantId });
  },

  updateSourceSystem(tenantId = "", sourceSystemId: string, body: CoreSourceSystemCommand): Promise<CoreSourceSystem> {
    const scopedTenantId = requireTenantId(tenantId, "source-system update");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPut<CoreSourceSystem>(`${coreAdminEndpoints.sourceSystem(sourceSystemId)}?${query.toString()}`, { ...body, tenantId: scopedTenantId, sourceSystemId });
  },

  retireSourceSystem(tenantId = "", sourceSystemId: string): Promise<{ sourceSystemId: string; status: string }> {
    const scopedTenantId = requireTenantId(tenantId, "source-system retire");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiDelete<{ sourceSystemId: string; status: string }>(`${coreAdminEndpoints.sourceSystem(sourceSystemId)}?${query.toString()}`);
  },

  getDispatchFlows(tenantId = "", sourceSystem?: string | null): Promise<CoreDispatchFlowView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    if (sourceSystem) query.set("sourceSystem", sourceSystem);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlows}${params}`);
  },

  getDispatchFlowsForAgent(tenantId: string, agentId: string): Promise<CoreDispatchFlowView[]> {
    const scopedTenantId = requireTenantId(tenantId, "Dispatch Flow by-Agent lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreGetList<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlowsByAgent(agentId)}?${query.toString()}`);
  },

  getDispatchFlowAgentOptions(tenantId = ""): Promise<CoreDispatchFlowAgentOptionView[]> {
    const scopedTenantId = requireTenantId(tenantId, "Dispatch Flow Agent options lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreGetList<CoreDispatchFlowAgentOptionView>(`${coreAdminEndpoints.dispatchFlowAgentOptions}?${query.toString()}`);
  },

  getAgentPools(tenantId = "", sourceSystem?: string | null): Promise<CoreAgentPoolView[]> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    if (sourceSystem) query.set("sourceSystem", sourceSystem);
    return coreGetList<CoreAgentPoolView>(`${coreAdminEndpoints.agentPools}?${query.toString()}`);
  },

  getAgentPool(poolId: string, tenantId = ""): Promise<CoreAgentPoolView> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool detail lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiGet<CoreAgentPoolView>(`${coreAdminEndpoints.agentPool(poolId)}?${query.toString()}`);
  },

  createAgentPool(body: CoreAgentPoolView, tenantId = ""): Promise<CoreAgentPoolView> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool creation");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPost<CoreAgentPoolView>(`${coreAdminEndpoints.agentPools}?${query.toString()}`, { ...body, tenantId: scopedTenantId });
  },

  updateAgentPool(poolId: string, body: CoreAgentPoolView, tenantId = ""): Promise<CoreAgentPoolView> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool update");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPut<CoreAgentPoolView>(`${coreAdminEndpoints.agentPool(poolId)}?${query.toString()}`, { ...body, tenantId: scopedTenantId, poolId });
  },

  getDispatchFlow(flowId: string, tenantId = ""): Promise<CoreDispatchFlowView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow detail lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlow(flowId)}${params}`);
  },

  createDispatchFlow(body: CoreDispatchFlowView, tenantId = ""): Promise<CoreDispatchFlowView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow creation");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlows}${params}`, body);
  },

  updateDispatchFlow(flowId: string, body: CoreDispatchFlowView, tenantId = ""): Promise<CoreDispatchFlowView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow update");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPut<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlow(flowId)}${params}`, body);
  },

  createDispatchFlowRealTestEvent(
    flowId: string,
    body: { message?: string; severity?: string; objectId?: string; correlationId?: string; siteId?: string; plantId?: string; attributes?: Record<string, unknown> } = {},
    tenantId = "",
  ): Promise<CoreEventIntakeDecisionResponse> {
    const scopedTenantId = requireTenantId(tenantId, "real Dispatch Flow test event");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPost<CoreEventIntakeDecisionResponse>(
      `${coreAdminEndpoints.dispatchFlowRealTestEvent(flowId)}?${query.toString()}`,
      body,
    );
  },

  dryRunDispatchFlow(body: CoreDispatchFlowReadinessRequest, tenantId = ""): Promise<CoreDispatchFlowReadinessResponse> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow dry-run");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowReadinessResponse>(`${coreAdminEndpoints.dispatchFlowDryRun}${params}`, body);
  },

  dryRunDispatchFlowById(flowId: string, body: CoreDispatchFlowReadinessRequest, tenantId = ""): Promise<CoreDispatchFlowReadinessResponse> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow scoped dry-run");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowReadinessResponse>(`${coreAdminEndpoints.dispatchFlowScopedDryRun(flowId)}${params}`, body);
  },

  getDispatchFlowRules(flowId: string, tenantId = ""): Promise<CoreDispatchFlowRuleView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow rule lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchFlowRuleView>(`${coreAdminEndpoints.dispatchFlowRules(flowId)}${params}`);
  },

  getDispatchFlowSkills(flowId: string, tenantId = ""): Promise<CoreDispatchFlowRequiredSkillView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow skill lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchFlowRequiredSkillView>(`${coreAdminEndpoints.dispatchFlowSkills(flowId)}${params}`);
  },

  getDispatchFlowAgents(flowId: string, tenantId = ""): Promise<CoreDispatchFlowAgentView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow agent lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchFlowAgentView>(`${coreAdminEndpoints.dispatchFlowAgents(flowId)}${params}`);
  },

  previewDispatchFlowAgentAssignment(flowId: string, body: CoreDispatchFlowAgentView): Promise<Record<string, unknown>> {
    return coreApiPost<Record<string, unknown>>(coreAdminEndpoints.dispatchFlowAgentPreview(flowId), body);
  },

  previewDispatchFlowRule(flowId: string, body: CoreDispatchFlowRuleView): Promise<Record<string, unknown>> {
    return coreApiPost<Record<string, unknown>>(coreAdminEndpoints.dispatchFlowRulePreview(flowId), body);
  },

  getDispatchFlowTrace(flowId: string, tenantId = "", testMode = "CHAIN"): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow trace lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    if (testMode) query.set("testMode", testMode);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTrace(flowId)}${params}`);
  },

  testDispatchFlowExternal(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow external test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestExternal(flowId)}${params}`, body);
  },

  testDispatchFlowA2a(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow A2A test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestA2a(flowId)}${params}`, body);
  },

  testDispatchFlowResult(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow result test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestResult(flowId)}${params}`, body);
  },

  testDispatchFlowChain(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow chain test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestChain(flowId)}${params}`, body);
  },

  getDispatchContractSourceSystems(tenantId = "", limit = 500): Promise<CoreDispatchSourceSystemOption[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch source-system lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    query.set("limit", String(limit));
    return coreGetList<CoreDispatchSourceSystemOption>(`${coreAdminEndpoints.dispatchContractSourceSystems}?${query.toString()}`);
  },

  bootstrapDispatchContract(body: CoreDispatchContractBootstrapRequest): Promise<CoreDispatchContractBootstrapResponse> {
    return coreApiPost<CoreDispatchContractBootstrapResponse>(coreAdminEndpoints.dispatchContractBootstrap, body);
  },

  checkDispatchContractReadiness(body: CoreDispatchContractReadinessRequest): Promise<CoreDispatchContractReadinessResponse> {
    return coreApiPost<CoreDispatchContractReadinessResponse>(coreAdminEndpoints.dispatchContractReadiness, body);
  },

  inspectDispatchContract(body: CoreDispatchContractChainInspectionRequest): Promise<CoreDispatchContractChainInspectionResponse> {
    return coreApiPost<CoreDispatchContractChainInspectionResponse>(coreAdminEndpoints.dispatchContractInspect, body);
  },

  traceDispatchContract(body: CoreDispatchContractTraceRequest): Promise<CoreDispatchContractTraceResponse> {
    return coreApiPost<CoreDispatchContractTraceResponse>(coreAdminEndpoints.dispatchContractTrace, body);
  },

  createDispatchContractTestTask(body: CoreDispatchContractTestTaskRequest): Promise<CoreDispatchContractTestTaskResponse> {
    return coreApiPost<CoreDispatchContractTestTaskResponse>(coreAdminEndpoints.dispatchContractTestTask, body);
  },

  getDispatchTaskDefinitionImpactPreview(
    definitionId: string,
    action?: string | null,
    tenantId = "",
  ): Promise<CoreDispatchTaskDefinitionImpactPreview> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (action) query.set("action", action);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreDispatchTaskDefinitionImpactPreview>(
      `${coreAdminEndpoints.dispatchTaskDefinitionImpactPreview(definitionId)}${params}`,
    );
  },

  activateDispatchTaskDefinition(
    definitionId: string,
    body: CoreDispatchTaskDefinitionReviewCommand,
    tenantId = "",
  ): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreDispatchTaskDefinition>(
      `${coreAdminEndpoints.dispatchTaskDefinitionActivate(definitionId)}${suffix}`,
      body,
    );
  },

  retireDispatchTaskDefinition(
    definitionId: string,
    body: CoreDispatchTaskDefinitionReviewCommand,
    tenantId = "",
  ): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreDispatchTaskDefinition>(
      `${coreAdminEndpoints.dispatchTaskDefinitionRetire(definitionId)}${suffix}`,
      body,
    );
  },

  mergeDispatchTaskDefinition(
    definitionId: string,
    body: CoreDispatchTaskDefinitionReviewCommand,
    tenantId = "",
  ): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreDispatchTaskDefinition>(
      `${coreAdminEndpoints.dispatchTaskDefinitionMerge(definitionId)}${suffix}`,
      body,
    );
  },


  upsertDispatchTaskDefinition(
    definitionId: string,
    body: CoreDispatchTaskDefinition,
    tenantId = "",
  ): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreDispatchTaskDefinition>(
      `${coreAdminEndpoints.dispatchTaskDefinition(definitionId)}${suffix}`,
      body,
    );
  },








  getAgentQualityDaily(agentId: string, tenantId = "", limit = 90): Promise<CoreAgentQualityMetricsDaily[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentQualityMetricsDaily>(`${coreAdminEndpoints.agentQualityDaily(agentId)}${suffix}`);
  },

  getAgentQualityWindows(agentId: string, metricWindow = "24h", tenantId = "", limit = 30): Promise<CoreAgentQualityMetricsWindow[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (metricWindow) query.set("metricWindow", metricWindow);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentQualityMetricsWindow>(`${coreAdminEndpoints.agentQualityWindows(agentId)}${suffix}`);
  },

  upsertAgentQualityWindow(agentId: string, body: CoreAgentQualityMetricsWindow, tenantId = ""): Promise<CoreAgentQualityMetricsWindow> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreAgentQualityMetricsWindow>(`${coreAdminEndpoints.agentQualityWindows(agentId)}${suffix}`, body);
  },

  getRuntimeQualityDaily(runtimeId: string, tenantId = "", limit = 90): Promise<CoreRuntimeQualityMetricsDaily[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreRuntimeQualityMetricsDaily>(`${coreAdminEndpoints.runtimeQualityDaily(runtimeId)}${suffix}`);
  },

  getSupplyProfileQualitySnapshots(metricWindow = "24h", agentId?: string, runtimeId?: string, tenantId = "", limit = 500): Promise<CoreSupplyProfileQualitySnapshot[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (metricWindow) query.set("metricWindow", metricWindow);
    if (agentId) query.set("agentId", agentId);
    if (runtimeId) query.set("runtimeId", runtimeId);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreSupplyProfileQualitySnapshot>(`${coreAdminEndpoints.supplyProfileQualitySnapshots}${suffix}`);
  },

  getSupplyProfileQualitySnapshot(profileCode: string, metricWindow = "24h", tenantId = ""): Promise<CoreSupplyProfileQualitySnapshot> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (metricWindow) query.set("metricWindow", metricWindow);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreSupplyProfileQualitySnapshot>(`${coreAdminEndpoints.supplyProfileQualitySnapshot(profileCode)}${suffix}`);
  },

  getSupplyProfiles(status?: string, agentId?: string, runtimeBindingId?: string, tenantId = ""): Promise<CoreSupplyProfile[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (agentId) query.set("agentId", agentId);
    if (runtimeBindingId) query.set("runtimeBindingId", runtimeBindingId);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreSupplyProfile>(`${coreAdminEndpoints.supplyProfiles}${suffix}`);
  },

  getAgentSupplyProfiles(agentId: string, status?: string, tenantId = ""): Promise<CoreSupplyProfile[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreSupplyProfile>(`${coreAdminEndpoints.agentSupplyProfiles(agentId)}${suffix}`);
  },

  upsertSupplyProfile(profileCode: string, body: CoreSupplyProfile, tenantId = ""): Promise<CoreSupplyProfile> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreSupplyProfile>(`${coreAdminEndpoints.supplyProfile(profileCode)}${suffix}`, body);
  },


  getRuntimeResources(status?: string, trustStatus?: string, tenantId = ""): Promise<CoreRuntimeResource[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (trustStatus) query.set("trustStatus", trustStatus);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreRuntimeResource>(`${coreAdminEndpoints.runtimeResources}${suffix}`);
  },

  upsertRuntimeResource(runtimeId: string, body: CoreRuntimeResource, tenantId = ""): Promise<CoreRuntimeResource> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreRuntimeResource>(`${coreAdminEndpoints.runtimeResource(runtimeId)}${suffix}`, body);
  },

  getAgentRuntimeBindings(agentId: string, status?: string): Promise<CoreAgentRuntimeBinding[]> {
    const query = new URLSearchParams();
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentRuntimeBinding>(`${coreAdminEndpoints.agentRuntimeBindings(agentId)}${suffix}`);
  },

  upsertAgentRuntimeBinding(agentId: string, body: CoreAgentRuntimeBinding): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindings(agentId), body);
  },

  activateAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingActivate(agentId, bindingId), body);
  },

  pauseAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingPause(agentId, bindingId), body);
  },

  resumeAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingResume(agentId, bindingId), body);
  },

  revokeAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingRevoke(agentId, bindingId), body);
  },

  getRuntimeFeatures(status?: string, tenantId = ""): Promise<CoreRuntimeFeatureCatalog[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreRuntimeFeatureCatalog>(`${coreAdminEndpoints.runtimeFeatures}${suffix}`);
  },

  upsertRuntimeFeature(featureCode: string, body: CoreRuntimeFeatureCatalog, tenantId = ""): Promise<CoreRuntimeFeatureCatalog> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreRuntimeFeatureCatalog>(`${coreAdminEndpoints.runtimeFeature(featureCode)}${suffix}`, body);
  },

  getCapabilities(
    status?: string,
    taskDefinitionId?: string,
    tenantId = "",
  ): Promise<CoreAgentCapabilityCatalog[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (taskDefinitionId) query.set("taskDefinitionId", taskDefinitionId);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentCapabilityCatalog>(
      `${coreAdminEndpoints.capabilities}${suffix}`,
    );
  },

  upsertCapability(
    capabilityCode: string,
    body: CoreAgentCapabilityCatalog,
    tenantId = "",
  ): Promise<CoreAgentCapabilityCatalog> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAgentCapabilityCatalog>(
      `${coreAdminEndpoints.capability(capabilityCode)}${suffix}`,
      body,
    );
  },

  getAssignmentProfiles(
    agentType?: string,
    active: boolean | null = true,
    tenantId = "",
  ): Promise<CoreAgentAssignmentProfile[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (agentType) query.set("agentType", agentType);
    if (active !== null) query.set("active", String(active));
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentAssignmentProfile>(
      `${coreAdminEndpoints.assignmentProfiles}${params}`,
    );
  },

  upsertAssignmentProfile(
    profileCode: string,
    body: CoreAgentAssignmentProfile,
    tenantId = "",
  ): Promise<CoreAgentAssignmentProfile> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAgentAssignmentProfile>(
      `${coreAdminEndpoints.assignmentProfile(profileCode)}${suffix}`,
      body,
    );
  },



  deleteAssignmentProfile(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAgentAssignmentProfile> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiDelete<CoreAgentAssignmentProfile>(
      `${coreAdminEndpoints.assignmentProfile(profileCode)}${suffix}`,
    );
  },

  getAssignmentProfileRelationshipMap(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfileRelationshipMap> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiGet<CoreAssignmentProfileRelationshipMap>(
      `${coreAdminEndpoints.assignmentProfileRelationshipMap(profileCode)}${suffix}`,
    );
  },

  getAssignmentProfileImpactPreview(
    profileCode: string,
    action: CoreAssignmentProfileImpactAction,
    tenantId = "",
  ): Promise<CoreAssignmentProfileImpactPreview> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (action) query.set("action", action);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreAssignmentProfileImpactPreview>(
      `${coreAdminEndpoints.assignmentProfileImpactPreview(profileCode)}${suffix}`,
    );
  },



  getAssignmentProfileCapabilities(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfileCapabilityBinding[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreAssignmentProfileCapabilityBinding>(
      `${coreAdminEndpoints.assignmentProfileCapabilities(profileCode)}${suffix}`,
    );
  },

  upsertAssignmentProfileCapability(
    profileCode: string,
    capabilityCode: string,
    body: CoreAssignmentProfileCapabilityBinding,
    tenantId = "",
  ): Promise<CoreAssignmentProfileCapabilityBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAssignmentProfileCapabilityBinding>(
      `${coreAdminEndpoints.assignmentProfileCapability(profileCode, capabilityCode)}${suffix}`,
      body,
    );
  },

  deleteAssignmentProfileCapability(
    profileCode: string,
    capabilityCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfileCapabilityBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiDelete<CoreAssignmentProfileCapabilityBinding>(
      `${coreAdminEndpoints.assignmentProfileCapability(profileCode, capabilityCode)}${suffix}`,
    );
  },

  getAssignmentProfilePolicies(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfilePolicyBinding[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreAssignmentProfilePolicyBinding>(
      `${coreAdminEndpoints.assignmentProfilePolicies(profileCode)}${suffix}`,
    );
  },

  upsertAssignmentProfilePolicy(
    profileCode: string,
    policyCode: string,
    body: CoreAssignmentProfilePolicyBinding,
    tenantId = "",
  ): Promise<CoreAssignmentProfilePolicyBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAssignmentProfilePolicyBinding>(
      `${coreAdminEndpoints.assignmentProfilePolicy(profileCode, policyCode)}${suffix}`,
      body,
    );
  },

  deleteAssignmentProfilePolicy(
    profileCode: string,
    policyCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfilePolicyBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiDelete<CoreAssignmentProfilePolicyBinding>(
      `${coreAdminEndpoints.assignmentProfilePolicy(profileCode, policyCode)}${suffix}`,
    );
  },

  getCertificationProfiles(
    profileCode?: string,
  ): Promise<CoreAgentCertificationProfile[]> {
    const params = profileCode
      ? `?profileCode=${encodeURIComponent(profileCode)}&active=true`
      : "?active=true";
    return coreGetList<CoreAgentCertificationProfile>(
      `${coreAdminEndpoints.certificationProfiles}${params}`,
    );
  },

  getAgentCertifications(
    agentId: string,
    limit = 50,
  ): Promise<CoreAgentCertificationRun[]> {
    return coreGetList<CoreAgentCertificationRun>(
      `${coreAdminEndpoints.agentCertifications(agentId)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  runAgentCertification(
    agentId: string,
    body: CoreAgentCertificationRunCommand,
  ): Promise<CoreAgentCertificationRun> {
    return coreApiPost<CoreAgentCertificationRun>(
      coreAdminEndpoints.runAgentCertification(agentId),
      body,
    );
  },

  passAgentCertification(
    agentId: string,
    runId: string,
    body?: CoreAgentCertificationDecisionCommand,
  ): Promise<CoreAgentCertificationRun> {
    return coreApiPost<CoreAgentCertificationRun>(
      coreAdminEndpoints.passAgentCertification(agentId, runId),
      body ?? {},
    );
  },

  failAgentCertification(
    agentId: string,
    runId: string,
    body?: CoreAgentCertificationDecisionCommand,
  ): Promise<CoreAgentCertificationRun> {
    return coreApiPost<CoreAgentCertificationRun>(
      coreAdminEndpoints.failAgentCertification(agentId, runId),
      body ?? {},
    );
  },

  scanCertificationTimeouts(limit = 100): Promise<CoreAgentCertificationRun[]> {
    return coreApiPost<CoreAgentCertificationRun[]>(
      `${coreAdminEndpoints.certificationTimeoutScan}?limit=${encodeURIComponent(String(limit))}`,
      {},
    );
  },



  getAgentCapabilities(agentId: string): Promise<CoreAgentCapabilityAssignment[]> {
    return coreGetList<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilities(agentId),
    );
  },

  requestAgentCapability(
    agentId: string,
    body: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilities(agentId),
      body,
    );
  },

  removeAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityRemove(agentId, assignmentId),
      body ?? {},
    );
  },

  approveAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityApprove(agentId, assignmentId),
      body ?? {},
    );
  },

  suspendAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilitySuspend(agentId, assignmentId),
      body ?? {},
    );
  },

  resumeAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityResume(agentId, assignmentId),
      body ?? {},
    );
  },

  revokeAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityRevoke(agentId, assignmentId),
      body ?? {},
    );
  },



  getAgentRuntimeFeatureObservations(agentId: string): Promise<CoreAgentRuntimeFeatureObservation[]> {
    return coreGetList<CoreAgentRuntimeFeatureObservation>(coreAdminEndpoints.agentRuntimeFeatureObservations(agentId));
  },

  getAgentRuntimeFeatureTrusts(agentId: string): Promise<CoreAgentRuntimeFeatureTrust[]> {
    return coreGetList<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureTrusts(agentId));
  },

  observeAgentRuntimeFeature(agentId: string, body: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureObserve(agentId), body);
  },

  verifyAgentRuntimeFeature(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureVerify(agentId, trustId), body ?? {});
  },

  trustAgentRuntimeFeature(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureTrust(agentId, trustId), body ?? {});
  },

  suspendAgentRuntimeFeatureTrust(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureSuspend(agentId, trustId), body ?? {});
  },

  resumeAgentRuntimeFeatureTrust(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureResume(agentId, trustId), body ?? {});
  },

  revokeAgentRuntimeFeatureTrust(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureRevoke(agentId, trustId), body ?? {});
  },

  getAgentQualifications(agentId: string): Promise<CoreAgentQualification[]> {
    return coreGetList<CoreAgentQualification>(
      coreAdminEndpoints.agentQualifications(agentId),
    );
  },

  assignAgentQualification(
    agentId: string,
    body: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualifications(agentId),
      body,
    );
  },

  removeAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiDelete<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationRemove(agentId, qualificationId),
      body?.operatorId ? { headers: { 'X-Operator-Id': body.operatorId } } : undefined,
    );
  },

  approveAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationApprove(agentId, qualificationId),
      body ?? {},
    );
  },

  suspendAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationSuspend(agentId, qualificationId),
      body ?? {},
    );
  },

  resumeAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationResume(agentId, qualificationId),
      body ?? {},
    );
  },

  revokeAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationRevoke(agentId, qualificationId),
      body ?? {},
    );
  },

  getAgentEnterpriseGovernance(
    agentId: string,
  ): Promise<CoreAgentEnterpriseGovernanceSummary> {
    return coreApiGet<CoreAgentEnterpriseGovernanceSummary>(
      coreAdminEndpoints.agentEnterpriseGovernance(agentId),
    );
  },

  getAgentDispatchEligibility(
    agentId: string,
    taskId?: string,
  ): Promise<CoreAgentDispatchEligibility> {
    const endpoint = taskId
      ? coreAdminEndpoints.agentDispatchEligibilityForTask(agentId, taskId)
      : coreAdminEndpoints.agentDispatchEligibility(agentId);
    return coreApiGet<CoreAgentDispatchEligibility>(endpoint);
  },

  getTaskDispatchRequirements(
    taskId: string,
  ): Promise<CoreTaskDispatchRequirements> {
    return coreApiGet<CoreTaskDispatchRequirements>(
      coreAdminEndpoints.taskDispatchRequirements(taskId),
    );
  },

  getTaskEligibleAgents(
    taskId: string,
    limit = 500,
  ): Promise<CoreTaskEligibleAgentsResponse> {
    return coreApiGet<CoreTaskEligibleAgentsResponse>(
      `${coreAdminEndpoints.taskEligibleAgents(taskId)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getTaskEligibleAgentsV2(
    taskId: string,
    limit = 500,
  ): Promise<CoreDispatchEligibilityV2Response> {
    return coreApiGet<CoreDispatchEligibilityV2Response>(
      `${coreAdminEndpoints.taskEligibleAgentsV2(taskId)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  disconnectAgent(
    agentId: string,
    body?: unknown,
  ): Promise<CoreRuntimeDisconnectResult> {
    return coreApiPost<CoreRuntimeDisconnectResult>(
      coreAdminEndpoints.agentDisconnect(agentId),
      body ?? {},
    );
  },

  disconnectAllAgentSessions(
    agentId: string,
    body?: CoreRuntimeDisconnectAllRequest,
  ): Promise<CoreRuntimeDisconnectResult> {
    return coreApiPost<CoreRuntimeDisconnectResult>(
      coreAdminEndpoints.agentDisconnectAll(agentId),
      body ?? {},
    );
  },

  reconcileBlockedAgentRuntimes(
    body?: CoreRuntimeDisconnectReconcileRequest,
  ): Promise<CoreRuntimeDisconnectReconcileReport> {
    return coreApiPost<CoreRuntimeDisconnectReconcileReport>(
      coreAdminEndpoints.agentRuntimeDisconnectReconcile,
      body ?? {},
    );
  },

  enforceDuplicateRuntimeSecurity(
    agentId: string,
    body?: CoreDuplicateRuntimeSecurityRequest,
  ): Promise<CoreAgentSecurityEnforcementResponse> {
    return coreApiPost<CoreAgentSecurityEnforcementResponse>(
      coreAdminEndpoints.agentDuplicateRuntimeEnforce(agentId),
      body ?? {},
    );
  },

  resolveDuplicateRuntimeSecurity(
    agentId: string,
    body?: CoreDuplicateRuntimeResolveRequest,
  ): Promise<CoreAgentSecurityEnforcementResponse> {
    return coreApiPost<CoreAgentSecurityEnforcementResponse>(
      coreAdminEndpoints.agentDuplicateRuntimeResolve(agentId),
      body ?? {},
    );
  },

  getAgentSecurityEnforcementPolicy(
    agentId: string,
  ): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiGet<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.agentSecurityEnforcementPolicy(agentId),
    );
  },

  updateAgentSecurityEnforcementPolicy(
    agentId: string,
    body: CoreAgentSecurityEnforcementPolicyUpdateRequest,
  ): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiPut<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.agentSecurityEnforcementPolicy(agentId),
      body,
    );
  },

  getDefaultSecurityEnforcementPolicy(): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiGet<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.defaultSecurityEnforcementPolicy,
    );
  },

  updateDefaultSecurityEnforcementPolicy(
    body: CoreAgentSecurityEnforcementPolicyUpdateRequest,
  ): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiPut<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.defaultSecurityEnforcementPolicy,
      body,
    );
  },

  getAgentSkillRegistryMetadata(): Promise<CoreAgentSkillRegistryMetadata> {
    return coreApiGet<CoreAgentSkillRegistryMetadata>(
      coreAdminEndpoints.agentSkillsMetadata,
    );
  },

  getAgentSkillDefinitions(
    domain?: string,
    enabledOnly = true,
  ): Promise<CoreAgentSkillDefinition[]> {
    const query = new URLSearchParams();
    if (domain) query.set("domain", domain);
    query.set("enabledOnly", String(enabledOnly));
    const suffix = `?${query.toString()}`;
    return coreGetList<CoreAgentSkillDefinition>(
      `${coreAdminEndpoints.agentSkills}${suffix}`,
    );
  },

  upsertAgentSkillDefinition(
    skillCode: string,
    body: CoreAgentSkillDefinition,
  ): Promise<CoreAgentSkillDefinition> {
    return coreApiPut<CoreAgentSkillDefinition>(
      coreAdminEndpoints.agentSkill(skillCode),
      body,
    );
  },

  deleteAgentSkillDefinition(
    skillCode: string,
  ): Promise<{ skillCode: string; deleted: boolean }> {
    return coreApiDelete<{ skillCode: string; deleted: boolean }>(
      coreAdminEndpoints.agentSkill(skillCode),
    );
  },

  getAgentSkillVersions(skillCode: string): Promise<CoreAgentSkillVersion[]> {
    return coreGetList<CoreAgentSkillVersion>(
      coreAdminEndpoints.agentSkillVersions(skillCode),
    );
  },

  createAgentSkillDraftVersion(
    skillCode: string,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    return coreApiPost<CoreAgentSkillWorkflowResult>(
      coreAdminEndpoints.agentSkillDraftVersion(skillCode),
      body,
    );
  },

  submitAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    return coreApiPost<CoreAgentSkillWorkflowResult>(
      coreAdminEndpoints.agentSkillVersionAction(skillCode, version, "submit"),
      body,
    );
  },

  approveAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    return coreApiPost<CoreAgentSkillWorkflowResult>(
      coreAdminEndpoints.agentSkillVersionAction(skillCode, version, "approve"),
      body,
    );
  },

  rejectAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    return coreApiPost<CoreAgentSkillWorkflowResult>(
      coreAdminEndpoints.agentSkillVersionAction(skillCode, version, "reject"),
      body,
    );
  },

  publishAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    return coreApiPost<CoreAgentSkillWorkflowResult>(
      coreAdminEndpoints.agentSkillVersionAction(skillCode, version, "publish"),
      body,
    );
  },

  rollbackAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    return coreApiPost<CoreAgentSkillWorkflowResult>(
      coreAdminEndpoints.agentSkillVersionAction(
        skillCode,
        version,
        "rollback",
      ),
      body,
    );
  },

  getAgentSkillAuditEntries(
    skillCode: string,
    limit = 100,
  ): Promise<CoreAgentSkillAuditEntry[]> {
    return coreGetList<CoreAgentSkillAuditEntry>(
      `${coreAdminEndpoints.agentSkillAudit(skillCode)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getAgentSkillVersionDiff(
    skillCode: string,
    version: number,
    baseVersion?: number,
  ): Promise<CoreAgentSkillDiffResult> {
    const query = baseVersion
      ? `?baseVersion=${encodeURIComponent(String(baseVersion))}`
      : "";
    return coreApiGet<CoreAgentSkillDiffResult>(
      `${coreAdminEndpoints.agentSkillVersionDiff(skillCode, version)}${query}`,
    );
  },

  getAgentSkillVersionImpact(
    skillCode: string,
    version: number,
  ): Promise<CoreAgentSkillImpactAnalysisResult> {
    return coreApiGet<CoreAgentSkillImpactAnalysisResult>(
      coreAdminEndpoints.agentSkillVersionImpact(skillCode, version),
    );
  },

  getAgentSkillApprovalPolicy(
    skillCode: string,
  ): Promise<CoreAgentSkillApprovalPolicy> {
    return coreApiGet<CoreAgentSkillApprovalPolicy>(
      coreAdminEndpoints.agentSkillApprovalPolicy(skillCode),
    );
  },

  updateAgentSkillApprovalPolicy(
    skillCode: string,
    body: CoreAgentSkillApprovalPolicy,
    operatorId?: string,
  ): Promise<CoreAgentSkillApprovalPolicy> {
    const query = operatorId
      ? `?operatorId=${encodeURIComponent(operatorId)}`
      : "";
    return coreApiPut<CoreAgentSkillApprovalPolicy>(
      `${coreAdminEndpoints.agentSkillApprovalPolicy(skillCode)}${query}`,
      body,
    );
  },

  getFleetSkillDrift(limit = 500): Promise<CoreAgentCapabilityDriftReport> {
    return coreApiGet<CoreAgentCapabilityDriftReport>(
      `${coreAdminEndpoints.agentSkillsDrift}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getAgentSkillDrift(agentId: string): Promise<CoreAgentCapabilityDriftReport> {
    return coreApiGet<CoreAgentCapabilityDriftReport>(
      coreAdminEndpoints.agentSkillDrift(agentId),
    );
  },

  evaluateSkillDriftPolicy(
    body: CoreSkillDriftPolicyEvaluationRequest,
  ): Promise<CoreSkillDriftPolicyEvaluationResponse> {
    return coreApiPost<CoreSkillDriftPolicyEvaluationResponse>(
      coreAdminEndpoints.agentSkillsDriftPolicyEvaluate,
      body,
    );
  },

  getAgentSkillDeprecationPlan(
    skillCode: string,
  ): Promise<CoreAgentSkillDeprecationPlan> {
    return coreApiGet<CoreAgentSkillDeprecationPlan>(
      coreAdminEndpoints.agentSkillDeprecationPlan(skillCode),
    );
  },

  updateAgentSkillDeprecationPlan(
    skillCode: string,
    body: CoreAgentSkillDeprecationCommand,
  ): Promise<CoreAgentSkillDeprecationPlan> {
    return coreApiPut<CoreAgentSkillDeprecationPlan>(
      coreAdminEndpoints.agentSkillDeprecationPlan(skillCode),
      body,
    );
  },

  analyzeAgentSkillDeprecationMigration(
    skillCode: string,
    limit = 500,
  ): Promise<CoreAgentSkillDeprecationMigrationPlan> {
    return coreApiPost<CoreAgentSkillDeprecationMigrationPlan>(
      `${coreAdminEndpoints.agentSkillDeprecationAnalyze(skillCode)}?limit=${encodeURIComponent(String(limit))}`,
      {},
    );
  },

  getAgentSkillDependencyGraph(
    skillCode: string,
    depth = 2,
  ): Promise<CoreAgentSkillDependencyGraph> {
    return coreApiGet<CoreAgentSkillDependencyGraph>(
      `${coreAdminEndpoints.agentSkillDependencyGraph(skillCode)}?depth=${encodeURIComponent(String(depth))}`,
    );
  },

  replaceAgentSkillDependencies(
    skillCode: string,
    body: CoreAgentSkillDependencyCommand,
  ): Promise<CoreAgentSkillDependencyEdge[]> {
    return coreApiPut<CoreAgentSkillDependencyEdge[]>(
      coreAdminEndpoints.agentSkillDependencies(skillCode),
      body,
    );
  },

  proposeFleetSkillRemediation(
    limit = 500,
  ): Promise<CoreAgentSkillRemediationProposal> {
    return coreApiPost<CoreAgentSkillRemediationProposal>(
      `${coreAdminEndpoints.agentSkillFleetRemediationProposal}?limit=${encodeURIComponent(String(limit))}`,
      {},
    );
  },

  proposeAgentSkillRemediation(
    agentId: string,
  ): Promise<CoreAgentSkillRemediationProposal> {
    return coreApiPost<CoreAgentSkillRemediationProposal>(
      coreAdminEndpoints.agentSkillRemediationProposal(agentId),
      {},
    );
  },

  getAgentRemediationProposal(
    agentId: string,
  ): Promise<CoreAgentRemediationProposal> {
    return coreApiGet<CoreAgentRemediationProposal>(
      coreAdminEndpoints.agentRemediationProposal(agentId),
    );
  },

  createAgentRemediationProposal(
    agentId: string,
    body?: CoreAgentRemediationProposalRequest,
  ): Promise<CoreAgentRemediationProposal> {
    return coreApiPost<CoreAgentRemediationProposal>(
      coreAdminEndpoints.agentRemediationProposal(agentId),
      body ?? {},
    );
  },

  listAgentRemediationWorkflows(
    agentId: string,
  ): Promise<CoreAgentRemediationWorkflow[]> {
    return coreApiGet<CoreAgentRemediationWorkflow[]>(
      coreAdminEndpoints.agentRemediationWorkflows(agentId),
    );
  },

  createAgentRemediationWorkflow(
    agentId: string,
    body?: CoreAgentRemediationWorkflowCreateRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflows(agentId),
      body ?? {},
    );
  },

  approveAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowApprove(agentId, workflowId),
      body ?? {},
    );
  },

  rejectAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowReject(agentId, workflowId),
      body ?? {},
    );
  },

  cancelAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowCancel(agentId, workflowId),
      body ?? {},
    );
  },

  executeAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowExecute(agentId, workflowId),
      body ?? {},
    );
  },

  listStaleAgentRemediationWorkflowLeases(
    limit = 50,
  ): Promise<CoreAgentRemediationStaleLeaseQueue> {
    return coreApiGet<CoreAgentRemediationStaleLeaseQueue>(
      `${coreAdminEndpoints.agentRemediationWorkflowStaleLeases}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  listRecoveredAgentRemediationWorkflowLeases(
    limit = 50,
  ): Promise<CoreAgentRemediationRecoveredLeaseQueue> {
    return coreApiGet<CoreAgentRemediationRecoveredLeaseQueue>(
      `${coreAdminEndpoints.agentRemediationWorkflowRecoveredLeases}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  recoverStaleAgentRemediationWorkflowLeases(
    limit = 100,
    operatorId = "admin-ui",
    reason = "Manual P11 stale workflow execution lease recovery from Admin UI.",
  ): Promise<CoreAgentRemediationStaleLeaseRecoveryRun> {
    return coreApiPost<CoreAgentRemediationStaleLeaseRecoveryRun>(
      `${coreAdminEndpoints.agentRemediationWorkflowRecoverStaleLeases}?limit=${encodeURIComponent(String(limit))}&operatorId=${encodeURIComponent(operatorId)}&reason=${encodeURIComponent(reason)}`,
      {},
    );
  },

  evaluateAgentSkillContract(
    agentId: string,
    body: CoreAgentSkillEvaluationRequest,
  ): Promise<CoreAgentSkillEvaluationResult> {
    return coreApiPost<CoreAgentSkillEvaluationResult>(
      coreAdminEndpoints.agentSkillEvaluation(agentId),
      body,
    );
  },

  resolveDispatchContract(
    body: CoreTaskDispatchContractResolveRequest,
  ): Promise<CoreTaskDispatchContractResolveResult> {
    return coreApiPost<CoreTaskDispatchContractResolveResult>(
      coreAdminEndpoints.dispatchContractResolve,
      body,
    );
  },

  listDispatchRecipes(
    domain?: string,
    enabledOnly = true,
  ): Promise<CoreDispatchRecipe[]> {
    const params = new URLSearchParams();
    if (domain) params.set("domain", domain);
    params.set("enabledOnly", String(enabledOnly));
    const suffix = params.toString() ? `?${params.toString()}` : "";
    return coreGetList<CoreDispatchRecipe>(
      `${coreAdminEndpoints.dispatchRecipes}${suffix}`,
    );
  },

  getDispatchRecipeTemplates(): Promise<CoreDispatchReadinessTemplates> {
    return coreApiGet<CoreDispatchReadinessTemplates>(
      coreAdminEndpoints.dispatchRecipeTemplates,
    );
  },

  evaluateDispatchRecipe(
    recipeCode: string,
    body: CoreDispatchRecipeEvaluationRequest,
  ): Promise<CoreDispatchRecipeEvaluationResult> {
    return coreApiPost<CoreDispatchRecipeEvaluationResult>(
      coreAdminEndpoints.dispatchRecipeEvaluate(recipeCode),
      body,
    );
  },

  resolveTaskCapabilities(
    body: CoreTaskCapabilityResolveRequest,
  ): Promise<CoreTaskCapabilityResolveResult> {
    return coreApiPost<CoreTaskCapabilityResolveResult>(
      coreAdminEndpoints.taskCapabilityResolve,
      body,
    );
  },

  evaluateDispatchReadiness(
    body: CoreDispatchReadinessEvaluationRequest,
  ): Promise<CoreDispatchReadinessEvaluationResult> {
    return coreApiPost<CoreDispatchReadinessEvaluationResult>(
      coreAdminEndpoints.dispatchReadinessEvaluate,
      body,
    );
  },

  getDispatchReadinessTemplates(): Promise<CoreDispatchReadinessTemplates> {
    return coreApiGet<CoreDispatchReadinessTemplates>(
      coreAdminEndpoints.dispatchReadinessTemplates,
    );
  },

  createDispatchReadinessTestEvent(
    body: CoreEventIntakeEnvelope,
  ): Promise<CoreEventIntakeDecisionResponse> {
    return coreApiPost<CoreEventIntakeDecisionResponse>(
      coreAdminEndpoints.eventIntake,
      body,
    );
  },

  getAgentApprovedSkills(
    agentId: string,
    enabledOnly = true,
  ): Promise<CoreAgentApprovedSkill[]> {
    return coreGetList<CoreAgentApprovedSkill>(
      `${coreAdminEndpoints.agentApprovedSkills(agentId)}?enabledOnly=${String(enabledOnly)}`,
    );
  },

  replaceAgentApprovedSkills(
    agentId: string,
    body: CoreAgentApprovedSkillSyncCommand,
  ): Promise<CoreAgentApprovedSkillSyncResult> {
    return coreApiPut<CoreAgentApprovedSkillSyncResult>(
      coreAdminEndpoints.agentApprovedSkills(agentId),
      body,
    );
  },

  syncAgentApprovedSkillsAndCapabilities(
    agentId: string,
    body: CoreAgentApprovedSkillSyncCommand,
  ): Promise<CoreAgentApprovedSkillSyncResult> {
    return coreApiPost<CoreAgentApprovedSkillSyncResult>(
      coreAdminEndpoints.agentSkillSyncApprovedCapabilities(agentId),
      body,
    );
  },

  getAgentRuntimeCapabilityProfile(
    agentId: string,
  ): Promise<CoreAgentRuntimeCapabilityProfile> {
    return coreApiGet<CoreAgentRuntimeCapabilityProfile>(
      coreAdminEndpoints.agentRuntimeCapabilityProfile(agentId),
    );
  },

  getAgentRuntimeDescriptor(
    agentId: string,
  ): Promise<CoreAgentRuntimeDescriptor> {
    return coreApiGet<CoreAgentRuntimeDescriptor>(
      coreAdminEndpoints.agentRuntimeDescriptor(agentId),
    );
  },

  getAgentRuntimeCapabilities(
    agentId: string,
  ): Promise<CoreAgentRuntimeCapabilityItem[]> {
    return coreGetList<CoreAgentRuntimeCapabilityItem>(
      coreAdminEndpoints.agentRuntimeCapabilities(agentId),
    );
  },

  getAgentRuntimeLoad(agentId: string): Promise<CoreAgentRuntimeLoadSnapshot> {
    return coreApiGet<CoreAgentRuntimeLoadSnapshot>(
      coreAdminEndpoints.agentRuntimeLoad(agentId),
    );
  },

  async getTasksRuntimeView(): Promise<CoreTaskRuntimeView[]> {
    return normalizeCoreTaskRuntimeViewPayload(
      await coreApiGet<unknown>(coreAdminEndpoints.tasksRuntimeView),
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
    return coreApiGet<CoreCallbackInboxSummary>(
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
    return coreApiGet<CoreDispatchTimelineResponse>(
      `${coreAdminEndpoints.taskTimeline(taskId)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getTaskDispatchEvidence(
    taskId: string,
    limit = 200,
  ): Promise<CoreTaskDispatchEvidenceView> {
    return coreApiGet<CoreTaskDispatchEvidenceView>(
      `${coreAdminEndpoints.taskDispatchEvidence(taskId)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getTaskRuntimeVerification(
    taskId: string,
    timeoutSeconds = 90,
    limit = 200,
  ): Promise<CoreTaskRuntimeVerificationView> {
    return coreApiGet<CoreTaskRuntimeVerificationView>(
      `${coreAdminEndpoints.taskRuntimeVerification(taskId)}?timeoutSeconds=${encodeURIComponent(String(timeoutSeconds))}&limit=${encodeURIComponent(String(limit))}`,
    );
  },

  runTaskDispatchContractReadiness(
    taskId: string,
    body?: CoreTaskDispatchContractRepairRequest,
  ): Promise<CoreDispatchContractReadinessResponse> {
    return coreApiPost<CoreDispatchContractReadinessResponse>(
      coreAdminEndpoints.taskDispatchContractReadiness(taskId),
      body ?? {},
    );
  },

  repairTaskDispatchContract(
    taskId: string,
    body?: CoreTaskDispatchContractRepairRequest,
  ): Promise<CoreDispatchContractBootstrapResponse> {
    return coreApiPost<CoreDispatchContractBootstrapResponse>(
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

  getAdapterActions(limit = 100): Promise<CoreAdapterAction[]> {
    return coreGetList<CoreAdapterAction>(
      `${coreAdminEndpoints.adapterActions}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getAdapterActionsByStatus(
    status: string,
    limit = 100,
  ): Promise<CoreAdapterAction[]> {
    return coreGetList<CoreAdapterAction>(
      `${coreAdminEndpoints.adapterActionsByStatus(status)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  executeAdapterAction(actionId: string): Promise<CoreAdapterAction> {
    return coreApiPost<CoreAdapterAction>(
      coreAdminEndpoints.adapterActionExecute(actionId),
      {},
    );
  },

  executeRetryAdapterAction(actionId: string): Promise<CoreAdapterAction> {
    return coreApiPost<CoreAdapterAction>(
      coreAdminEndpoints.adapterActionExecuteRetry(actionId),
      {},
    );
  },

  getAdapterActionMetadata(): Promise<CoreAdapterActionMetadata> {
    return coreApiGet<CoreAdapterActionMetadata>(
      coreAdminEndpoints.adapterActionsMetadata,
    );
  },

  executePendingAdapterActions(limit = 50): Promise<Record<string, unknown>> {
    return coreApiPost<Record<string, unknown>>(
      `${coreAdminEndpoints.adapterActionExecutePending}?limit=${encodeURIComponent(String(limit))}`,
      {},
    );
  },

  getAdapterExecutorAudit(
    limit = 100,
  ): Promise<CoreAdapterExecutorAuditRecord[]> {
    return coreGetList<CoreAdapterExecutorAuditRecord>(
      `${coreAdminEndpoints.adapterActionExecutorAudit}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getRedmineIssueTrackingDiagnostics(): Promise<CoreIssueTrackingRedmineDiagnostics> {
    return coreApiGet<CoreIssueTrackingRedmineDiagnostics>(
      coreAdminEndpoints.issueTrackingRedmineDiagnostics,
    );
  },

  testRedmineIssueTrackingConnection(): Promise<CoreIssueTrackingRedmineConnectionResult> {
    return coreApiPost<CoreIssueTrackingRedmineConnectionResult>(
      coreAdminEndpoints.issueTrackingRedmineTestConnection,
      {},
    );
  },

  getRedmineProjects(): Promise<CoreIssueTrackingRedmineCollectionResult> {
    return coreApiGet<CoreIssueTrackingRedmineCollectionResult>(
      coreAdminEndpoints.issueTrackingRedmineProjects,
    );
  },

  getRedmineTrackers(): Promise<CoreIssueTrackingRedmineCollectionResult> {
    return coreApiGet<CoreIssueTrackingRedmineCollectionResult>(
      coreAdminEndpoints.issueTrackingRedmineTrackers,
    );
  },

  createRedmineTestIssue(
    body: CoreIssueTrackingRedmineTestIssueRequest,
  ): Promise<CoreIssueTrackingRedmineTestIssueResult> {
    return coreApiPost<CoreIssueTrackingRedmineTestIssueResult>(
      coreAdminEndpoints.issueTrackingRedmineTestIssue,
      body,
    );
  },

  retryAdapterAction(
    actionId: string,
    reason = "Retry issue tracking sync from Admin UI",
  ): Promise<CoreAdapterAction> {
    return coreApiPost<CoreAdapterAction>(
      coreAdminEndpoints.adapterActionRetry(actionId),
      { reason, resetAttempts: false },
    );
  },

  async getTaskFailureQueue(limit = 100): Promise<CoreAdminFailureQueueResponse> {
    return normalizeFailureQueueResponse(
      await coreApiGet<CoreAdminFailureQueueResponse>(
        `${coreAdminEndpoints.taskFailureQueue}?limit=${encodeURIComponent(String(limit))}`,
      ),
    );
  },

  manualRetryTask(taskId: string, body?: unknown): Promise<CommandResult> {
    return coreApiPost<CommandResult>(
      coreAdminEndpoints.taskManualRetry(taskId),
      body ?? {},
    );
  },

  deadLetterTask(taskId: string, body?: unknown): Promise<CommandResult> {
    return coreApiPost<CommandResult>(
      coreAdminEndpoints.taskDeadLetter(taskId),
      body ?? {},
    );
  },

  escalateTask(taskId: string, body?: unknown): Promise<CommandResult> {
    return coreApiPost<CommandResult>(
      coreAdminEndpoints.taskEscalate(taskId),
      body ?? {},
    );
  },

  async getTaskRuntimeView(taskId: string): Promise<CoreTaskRuntimeView> {
    try {
      const runtimeDetail = await coreApiGet<unknown>(
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
      const detail = await coreApiGet<CoreTaskRecord>(
        coreAdminEndpoints.taskDetail(taskId),
      );
      const dispatches = await this.getTaskDispatchRequests(taskId, 100);
      return normalizeCoreTaskRecord(
        detail,
        firstDispatchForTask(taskId, dispatches),
      );
    } catch (error) {
      if (!isNotFoundOrUnsupportedApiError(error)) {
        throw error;
      }

      const tasks = await this.getTasksRuntimeView();
      const task = tasks.find((candidate) => candidate.taskId === taskId);
      if (!task) {
        throw new ApiError(`Core task ${taskId} not found`, 200, undefined, 'CORE_TASK_NOT_FOUND');
      }
      return task;
    }
  },


  submitTaskClassificationResult(
    taskId: string,
    body: CoreTaskClassificationRequest,
  ): Promise<CoreTaskClassificationResult> {
    return coreApiPost<CoreTaskClassificationResult>(
      coreAdminEndpoints.taskClassificationResult(taskId),
      body,
    );
  },

  retryTask(taskId: string): Promise<CommandResult> {
    return coreApiPost<CommandResult>(coreAdminEndpoints.taskRetry(taskId), {});
  },

  retryDispatchRequest(dispatchRequestId: string): Promise<CommandResult> {
    return coreApiPost<CommandResult>(
      coreAdminEndpoints.dispatchRequestRetry(dispatchRequestId),
      {},
    );
  },

  cancelTask(taskId: string, body?: unknown): Promise<CommandResult> {
    return coreApiPost<CommandResult>(
      coreAdminEndpoints.taskCancel(taskId),
      body ?? {},
    );
  },

  reassignTask(taskId: string, body?: unknown): Promise<CommandResult> {
    return coreApiPost<CommandResult>(
      coreAdminEndpoints.taskReassign(taskId),
      body ?? {},
    );
  },

  getSecurityEvents(): Promise<AgentSecurityEvent[]> {
    return coreGetList<AgentSecurityEvent>(coreAdminEndpoints.securityEvents);
  },

  getAgentSecurityEvents(): Promise<AgentSecurityEvent[]> {
    return coreGetList<AgentSecurityEvent>(
      coreAdminEndpoints.agentSecurityEvents,
    );
  },

  getAgentLatestAuthFailure(agentId: string): Promise<CoreAgentLatestAuthFailureResponse> {
    return coreApiGet<CoreAgentLatestAuthFailureResponse>(
      coreAdminEndpoints.agentLatestAuthFailure(agentId),
    );
  },

  getAgentConnectionRepairActions(agentId: string): Promise<CoreAgentConnectionRepairActionsResponse> {
    return coreApiGet<CoreAgentConnectionRepairActionsResponse>(
      coreAdminEndpoints.agentConnectionRepairActions(agentId),
    );
  },

  executeAgentConnectionRepairAction(
    agentId: string,
    actionCode: string,
    body: CoreAgentConnectionRepairActionRequest,
  ): Promise<CoreAgentConnectionRepairActionResult> {
    return coreApiPost<CoreAgentConnectionRepairActionResult>(
      coreAdminEndpoints.agentConnectionRepairActionExecute(agentId, actionCode),
      body,
    );
  },

  getRecoveryMetrics(
    windowMinutes = 15,
    limit = 2000,
  ): Promise<CoreRecoveryOperationMetricsSnapshot> {
    const params = `?windowMinutes=${encodeURIComponent(String(windowMinutes))}&limit=${encodeURIComponent(String(limit))}`;
    return coreApiGet<CoreRecoveryOperationMetricsSnapshot>(
      `${coreAdminEndpoints.recoveryMetrics}${params}`,
    );
  },

  getRecoveryRunbook(): Promise<CoreRecoveryOperatorRunbook> {
    return coreApiGet<CoreRecoveryOperatorRunbook>(
      coreAdminEndpoints.recoveryRunbook,
    );
  },

  getRecoveryApprovalRequests(
    status = "PENDING",
    limit = 100,
  ): Promise<CoreRecoveryApprovalRequest[]> {
    const params = `?status=${encodeURIComponent(status)}&limit=${encodeURIComponent(String(limit))}`;
    return coreGetList<CoreRecoveryApprovalRequest>(
      `${coreAdminEndpoints.recoveryApprovalRequests}${params}`,
    );
  },

  approveRecoveryApprovalRequest(
    approvalId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryApprovalApprove(approvalId),
      body ?? {},
    );
  },

  rejectRecoveryApprovalRequest(
    approvalId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryApprovalReject(approvalId),
      body ?? {},
    );
  },

  cancelRecoveryApprovalRequest(
    approvalId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryApprovalCancel(approvalId),
      body ?? {},
    );
  },

  clearRuntimeBackoff(
    agentId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryClearRuntimeBackoff(agentId),
      body ?? {},
    );
  },

  triggerTaskRecoveryNow(
    taskId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryTriggerTaskNow(taskId),
      body ?? {},
    );
  },

  moveTaskToDeadLetter(
    taskId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryTaskDeadLetter(taskId),
      body ?? {},
    );
  },

  restoreTaskFromDeadLetter(
    taskId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryTaskRestoreDeadLetter(taskId),
      body ?? {},
    );
  },

  moveDispatchToDeadLetter(
    dispatchRequestId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryDispatchDeadLetter(dispatchRequestId),
      body ?? {},
    );
  },

  restoreDispatchFromDeadLetter(
    dispatchRequestId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryDispatchRestoreDeadLetter(dispatchRequestId),
      body ?? {},
    );
  },
} as const;
