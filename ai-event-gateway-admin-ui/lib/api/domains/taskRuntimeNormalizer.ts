import type {
  CoreAdminFailureQueueItem,
  CoreAdminFailureQueueResponse,
  CoreDispatchEventStage,
  CoreDispatchRequest,
  CoreDispatchUserFacingError,
  CoreRoutingDecisionRecord,
  CoreTaskIssueTracking,
  CoreTaskRecord,
  CoreTaskRuntimeSnapshot,
  CoreTaskRuntimeView,
} from "@/lib/types/core";

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

export function firstDispatchForTask(
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

export function normalizeFailureQueueResponse(
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
    version: typeof task.version === "number" ? task.version : undefined,
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
    issueSyncPolicy:
      task.issueSyncPolicy ?? pickString(taskRecord, ["issueSyncPolicy", "issue_sync_policy"]),
    issueSyncPolicySource:
      task.issueSyncPolicySource ?? pickString(taskRecord, ["issueSyncPolicySource", "issue_sync_policy_source"]),
    issueSyncPolicyInheritanceMode:
      task.issueSyncPolicyInheritanceMode ?? pickString(taskRecord, ["issueSyncPolicyInheritanceMode", "issue_sync_policy_inheritance_mode"]),
    issueSyncPolicyInheritedFromTaskId:
      task.issueSyncPolicyInheritedFromTaskId ?? pickString(taskRecord, ["issueSyncPolicyInheritedFromTaskId", "issue_sync_policy_inherited_from_task_id"]),
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

