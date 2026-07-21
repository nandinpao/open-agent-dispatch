import type {
  CoreCallbackInboxSummary,
  CoreDispatchAttemptLedger,
  CoreDispatchRequest,
  CoreDispatchTimelineResponse,
  CoreDispatchTimelineEvent,
  CoreTaskCommandAudit,
  CoreTaskDispatchEvidenceView,
  CoreTaskEligibleAgentsResponse,
  CoreDispatchEligibilityV2Response,
  CoreTaskIssueTracking,
  CoreTaskRuntimeVerificationView,
  CoreTaskRuntimeView,
} from '@/lib/types/core';
import type { TaskDispatchDiagnosis } from '@/lib/tasks/dispatchLifecycle';

export type TaskDiagnosisBlockerCategory =
  | 'NONE'
  | 'CONFIGURATION_BLOCKED'
  | 'RUNTIME_BLOCKED'
  | 'DELIVERY_BLOCKED'
  | 'EXECUTION_BLOCKED'
  | 'MANUAL_ACTION_REQUIRED';

export type TaskDiagnosisOwnerPlane =
  | 'CORE_CONFIGURATION'
  | 'CORE_ROUTING'
  | 'NETTY_RUNTIME'
  | 'AGENT_RUNTIME'
  | 'OPERATOR';

export type TaskDiagnosisSeverity = 'success' | 'info' | 'warning' | 'danger';

export type TaskUnifiedTimelineStage =
  | 'EVENT'
  | 'TASK'
  | 'ROUTING'
  | 'FLOW'
  | 'POOL'
  | 'ASSIGNMENT'
  | 'DELIVERY'
  | 'ACK'
  | 'RESULT'
  | 'RETRY'
  | 'MANUAL_ACTION'
  | 'ISSUE'
  | 'COMPLETION';

export interface TaskDiagnosisActionLink {
  type:
    | 'VIEW_DISPATCH_FLOW'
    | 'VIEW_AGENT'
    | 'VIEW_GATEWAY_RUNTIME'
    | 'WAIT_FOR_CALLBACK'
    | 'RETRY_ALLOWED'
    | 'REVIEW_ONLY'
    | 'COMMAND_NOT_AVAILABLE';
  label: string;
  description: string;
  href?: string;
  enabled: boolean;
}

export interface TaskDiagnosisPrimaryBlocker {
  category: TaskDiagnosisBlockerCategory;
  ownerPlane: TaskDiagnosisOwnerPlane;
  code: string;
  userTitle: string;
  userDescription: string;
  technicalCode: string;
  severity: TaskDiagnosisSeverity;
  retryable: boolean;
  recommendedActions: TaskDiagnosisActionLink[];
}

export interface TaskPoolEligibilitySummary {
  totalMembers: number;
  activeMembers: number;
  runtimeFound: number;
  connected: number;
  eligible: number;
  blockedCounts: Record<string, number>;
  source: 'TASK_FIELDS' | 'ELIGIBLE_AGENTS' | 'ELIGIBILITY_V2' | 'EVIDENCE_ONLY';
}

export interface TaskRoutingEvidenceSummary {
  flowId?: string;
  ruleId?: string;
  targetPoolId?: string;
  selectionStrategy?: string;
  poolMemberCount?: number;
  eligibleAgentCount?: number;
  selectedAgentId?: string;
  correlationId?: string;
  traceId?: string;
}

export interface TaskAssignmentSummary {
  assignedAgentId?: string;
  dispatchRequestId?: string;
  dispatchRequestStatus?: string;
  assignmentId?: string;
  assignmentKnown: boolean;
}

export interface TaskDeliverySummary {
  dispatchStatus?: string;
  dispatchExecutionStatus?: string;
  dispatchDeliveryStatus?: string;
  latestLedgerStatus?: string;
  latestLedgerDeliveryState?: string;
  deliveryKnown: boolean;
}

export interface TaskAckResultSummary {
  callbackStatus?: string;
  latestCallbackType?: string;
  latestCallbackId?: string;
  terminalCallbackReceived?: boolean;
  resultKnown: boolean;
}

export interface TaskIssueDedupSummary {
  activeIssueKey: string;
  issueType: string;
  issueScope: string;
  activeStatus: 'ACTIVE' | 'AUTO_RESOLVED' | 'REVIEW_REQUIRED' | 'NOT_REQUIRED';
  syncStatus?: string;
  externalIssueId?: string;
  externalIssueUrl?: string;
  occurrenceCount: number;
  firstOccurredAt?: string;
  lastOccurredAt?: string;
  autoResolutionPolicy: 'RECOVERABLE_AUTO_RESOLVE_ON_COMPLETION' | 'GOVERNANCE_REVIEW_REQUIRED' | 'NO_ACTIVE_ISSUE_REQUIRED';
  governanceReviewRequired: boolean;
  dedupRule: 'taskId + issueType + issueScope + activeStatus';
  repeatedOccurrenceBehavior: 'Update occurrenceCount and lastOccurredAt; do not create duplicate active Issue.';
  summary: string;
}

export interface TaskDiagnosisTimelineLink {
  stage: TaskUnifiedTimelineStage;
  label: string;
  status: 'done' | 'current' | 'blocked' | 'failed' | 'waiting';
  detail: string;
  occurredAt?: string;
  source?: 'TASK' | 'ROUTING_EVIDENCE' | 'DISPATCH_LEDGER' | 'CALLBACK_INBOX' | 'ISSUE_DEDUP' | 'REMEDIATION_COMMAND_AUDIT' | 'CORE_TIMELINE';
  references?: Record<string, string>;
}

export interface TaskDiagnosisReadModel {
  taskId: string;
  currentStage: string;
  primaryBlocker: TaskDiagnosisPrimaryBlocker;
  secondaryBlockers: TaskDiagnosisPrimaryBlocker[];
  routingEvidence: TaskRoutingEvidenceSummary;
  poolEligibility: TaskPoolEligibilitySummary;
  assignment: TaskAssignmentSummary;
  delivery: TaskDeliverySummary;
  ackResult: TaskAckResultSummary;
  issueDedup: TaskIssueDedupSummary;
  timeline: TaskDiagnosisTimelineLink[];
  generatedAt: string;
}

export interface BuildTaskDiagnosisReadModelInput {
  task: CoreTaskRuntimeView;
  diagnosis: TaskDispatchDiagnosis;
  dispatchEvidence?: CoreTaskDispatchEvidenceView;
  runtimeVerification?: CoreTaskRuntimeVerificationView;
  dispatchRequests?: CoreDispatchRequest[];
  dispatchLedger?: CoreDispatchAttemptLedger[];
  callbackInboxSummary?: CoreCallbackInboxSummary;
  timeline?: CoreDispatchTimelineResponse;
  eligibleAgents?: CoreTaskEligibleAgentsResponse;
  eligibleAgentsV2?: CoreDispatchEligibilityV2Response;
  issueTracking?: CoreTaskIssueTracking;
  externalIssueDedup?: Partial<TaskIssueDedupSummary>;
  remediationCommandAudits?: CoreTaskCommandAudit[];
}

function upper(value: unknown): string {
  return typeof value === 'string' ? value.trim().toUpperCase() : '';
}

function text(value: unknown): string | undefined {
  if (value === null || value === undefined) return undefined;
  const rendered = String(value).trim();
  return rendered.length ? rendered : undefined;
}

function firstNonBlank(...values: Array<unknown>): string | undefined {
  for (const value of values) {
    const rendered = text(value);
    if (rendered) return rendered;
  }
  return undefined;
}

function increment(map: Record<string, number>, code: string | undefined): void {
  const normalized = upper(code) || 'UNSPECIFIED';
  map[normalized] = (map[normalized] ?? 0) + 1;
}

function blockerCategory(code: string): TaskDiagnosisBlockerCategory {
  const normalized = upper(code);
  if (['NO_MATCHING_FLOW', 'NO_FLOW_AGENT', 'AGENT_CAPACITY_FULL', 'POOL_HAS_NO_ACTIVE_MEMBER', 'NO_ELIGIBLE_AGENT_IN_POOL'].includes(normalized)) return 'CONFIGURATION_BLOCKED';
  if (['AGENT_OFFLINE', 'AGENT_RUNTIME_NOT_FOUND', 'POOL_AGENT_RUNTIME_NOT_FOUND', 'HEARTBEAT_STALE', 'POOL_AGENT_OFFLINE', 'POOL_AGENT_CAPACITY_FULL', 'POOL_AGENT_BACKOFF'].includes(normalized)) return 'RUNTIME_BLOCKED';
  if (['DISPATCH_DELIVERY_FAILED', 'DELIVERY_REJECTED', 'DISPATCH_REQUEST_FAILED', 'ACK_TIMEOUT'].includes(normalized)) return 'DELIVERY_BLOCKED';
  if (['RESULT_TIMEOUT', 'CALLBACK_TIMEOUT', 'TASK_FAILED', 'RESULT_FAILURE'].includes(normalized)) return 'EXECUTION_BLOCKED';
  if (['MANUAL_ASSIGNMENT_REQUIRED', 'WAITING_MANUAL_ASSIGNMENT', 'OPERATOR_REVIEW_REQUIRED'].includes(normalized)) return 'MANUAL_ACTION_REQUIRED';
  if (['COMPLETED', 'IN_PROGRESS'].includes(normalized)) return 'NONE';
  return 'CONFIGURATION_BLOCKED';
}

function ownerPlane(category: TaskDiagnosisBlockerCategory, code: string): TaskDiagnosisOwnerPlane {
  const normalized = upper(code);
  if (category === 'CONFIGURATION_BLOCKED') return normalized === 'NO_FLOW_AGENT' ? 'CORE_ROUTING' : 'CORE_CONFIGURATION';
  if (category === 'RUNTIME_BLOCKED') return 'AGENT_RUNTIME';
  if (category === 'DELIVERY_BLOCKED') return 'NETTY_RUNTIME';
  if (category === 'EXECUTION_BLOCKED') return 'AGENT_RUNTIME';
  if (category === 'MANUAL_ACTION_REQUIRED') return 'OPERATOR';
  return 'CORE_ROUTING';
}

function currentStageFromDiagnosis(diagnosis: TaskDispatchDiagnosis): string {
  switch (diagnosis.code) {
    case 'COMPLETED': return 'COMPLETION';
    case 'NO_MATCHING_FLOW': return 'ROUTING';
    case 'NO_FLOW_AGENT':
    case 'AGENT_OFFLINE':
    case 'AGENT_CAPACITY_FULL': return 'POOL_ELIGIBILITY';
    case 'DISPATCH_DELIVERY_FAILED': return 'DELIVERY';
    case 'RESULT_TIMEOUT': return 'RESULT';
    case 'MANUAL_ASSIGNMENT_REQUIRED': return 'MANUAL_ACTION';
    default: return 'TASK_ROUTING';
  }
}

function actionLinks(task: CoreTaskRuntimeView, diagnosis: TaskDispatchDiagnosis, category: TaskDiagnosisBlockerCategory): TaskDiagnosisActionLink[] {
  const actions: TaskDiagnosisActionLink[] = [];
  if (diagnosis.actionHref && diagnosis.actionLabel) {
    actions.push({
      type: diagnosis.actionHref.startsWith('/agents') ? 'VIEW_AGENT' : 'VIEW_DISPATCH_FLOW',
      label: diagnosis.actionLabel,
      description: diagnosis.nextAction,
      href: diagnosis.actionHref,
      enabled: true,
    });
  }
  if (category === 'DELIVERY_BLOCKED') {
    actions.push({
      type: 'VIEW_GATEWAY_RUNTIME',
      label: '查看 Gateway Runtime',
      description: '確認 Netty delivery 與 Agent transport 是否可用。',
      href: '/cluster',
      enabled: true,
    });
  }
  if (category === 'EXECUTION_BLOCKED') {
    actions.push({
      type: 'WAIT_FOR_CALLBACK',
      label: '查看 Callback Inbox',
      description: '確認 ACK / RESULT / ERROR callback 是否已進入 Core。',
      href: `/tasks/${encodeURIComponent(task.taskId)}`,
      enabled: true,
    });
  }
  if (category !== 'NONE') {
    actions.push({
      type: 'RETRY_ALLOWED',
      label: '查看可執行人工處置',
      description: 'Task command model 會在下方只顯示目前狀態合法的 remediation command。',
      enabled: false,
    });
  }
  if (!actions.length) {
    actions.push({
      type: 'REVIEW_ONLY',
      label: diagnosis.code === 'COMPLETED' ? '查看結果與時間線' : '查看派工證據',
      description: diagnosis.nextAction,
      href: `/tasks/${encodeURIComponent(task.taskId)}`,
      enabled: true,
    });
  }
  return actions;
}

function buildPrimaryBlocker(task: CoreTaskRuntimeView, diagnosis: TaskDispatchDiagnosis): TaskDiagnosisPrimaryBlocker {
  const category = blockerCategory(diagnosis.code);
  const owner = ownerPlane(category, diagnosis.code);
  return {
    category,
    ownerPlane: owner,
    code: diagnosis.code,
    userTitle: diagnosis.title,
    userDescription: diagnosis.reason,
    technicalCode: diagnosis.code,
    severity: diagnosis.tone,
    retryable: ['DELIVERY_BLOCKED', 'EXECUTION_BLOCKED', 'RUNTIME_BLOCKED'].includes(category),
    recommendedActions: actionLinks(task, diagnosis, category),
  };
}

function buildSecondaryBlockers(input: BuildTaskDiagnosisReadModelInput): TaskDiagnosisPrimaryBlocker[] {
  const blockers: TaskDiagnosisPrimaryBlocker[] = [];
  const seen = new Set<string>([input.diagnosis.code]);
  const add = (code: string | undefined, reason: string | undefined, sourceTitle: string) => {
    const normalized = upper(code);
    if (!normalized || seen.has(normalized)) return;
    seen.add(normalized);
    const category = blockerCategory(normalized);
    blockers.push({
      category,
      ownerPlane: ownerPlane(category, normalized),
      code: normalized,
      userTitle: sourceTitle,
      userDescription: reason ?? normalized,
      technicalCode: normalized,
      severity: category === 'NONE' ? 'info' : 'warning',
      retryable: ['DELIVERY_BLOCKED', 'EXECUTION_BLOCKED', 'RUNTIME_BLOCKED'].includes(category),
      recommendedActions: [],
    });
  };
  add(input.dispatchEvidence?.firstBlockingCode, input.dispatchEvidence?.firstBlockingReason, 'Routing Evidence secondary blocker');
  add(input.runtimeVerification?.firstBlockingCode, input.runtimeVerification?.firstBlockingReason, 'Runtime Verification secondary blocker');
  input.dispatchEvidence?.stages?.forEach((stage) => add(stage.blockingCode, stage.summary ?? stage.nextAction, stage.title ?? stage.stage));
  input.runtimeVerification?.steps?.forEach((step) => add(step.blockingCode, step.summary ?? step.nextAction, step.title ?? step.step));
  return blockers.slice(0, 5);
}

function buildBlockedCounts(input: BuildTaskDiagnosisReadModelInput): Record<string, number> {
  const blockedCounts: Record<string, number> = {};
  input.eligibleAgents?.blockedAgents?.forEach((candidate) => increment(blockedCounts, candidate.reason ?? candidate.dispatchStatus));
  input.eligibleAgentsV2?.blockedCandidates?.forEach((candidate) => {
    if (candidate.blockingReasons?.length) candidate.blockingReasons.forEach((reason) => increment(blockedCounts, reason.code ?? reason.message));
    else increment(blockedCounts, candidate.dispatchStatus);
  });
  input.eligibleAgentsV2?.globalBlockingReasons?.forEach((reason) => increment(blockedCounts, reason.code ?? reason.message));
  input.dispatchEvidence?.stages?.forEach((stage) => {
    if (stage.blockingCode) increment(blockedCounts, stage.blockingCode);
  });
  input.runtimeVerification?.steps?.forEach((step) => {
    if (step.blockingCode) increment(blockedCounts, step.blockingCode);
  });
  if (!Object.keys(blockedCounts).length && input.task.blockerCode) increment(blockedCounts, input.task.blockerCode);
  return blockedCounts;
}

function buildPoolEligibility(input: BuildTaskDiagnosisReadModelInput): TaskPoolEligibilitySummary {
  const legacyEligible = input.eligibleAgents?.eligibleAgents?.length ?? 0;
  const legacyBlocked = input.eligibleAgents?.blockedAgents?.length ?? 0;
  const v2Eligible = input.eligibleAgentsV2?.eligibleCandidates?.filter((candidate) => candidate.eligible !== false).length ?? 0;
  const v2Blocked = input.eligibleAgentsV2?.blockedCandidates?.length ?? 0;
  const taskTotal = input.task.poolMemberCount;
  const taskEligible = input.task.eligibleAgentCount;
  const totalMembers = taskTotal ?? (legacyEligible + legacyBlocked || v2Eligible + v2Blocked || 0);
  const eligible = taskEligible ?? (legacyEligible || v2Eligible || 0);
  const blockedCounts = buildBlockedCounts(input);
  const offlineBlocked = Object.entries(blockedCounts)
    .filter(([code]) => code.includes('OFFLINE') || code.includes('RUNTIME_NOT_FOUND') || code.includes('HEARTBEAT'))
    .reduce((sum, [, count]) => sum + count, 0);
  const connected = Math.max(0, totalMembers - offlineBlocked);
  return {
    totalMembers,
    activeMembers: totalMembers,
    runtimeFound: connected,
    connected,
    eligible,
    blockedCounts,
    source: taskTotal !== undefined ? 'TASK_FIELDS' : legacyEligible + legacyBlocked > 0 ? 'ELIGIBLE_AGENTS' : v2Eligible + v2Blocked > 0 ? 'ELIGIBILITY_V2' : 'EVIDENCE_ONLY',
  };
}

function latestDispatchRequest(requests?: CoreDispatchRequest[]): CoreDispatchRequest | undefined {
  return [...(requests ?? [])].sort((left, right) => String(right.updatedAt ?? right.createdAt ?? '').localeCompare(String(left.updatedAt ?? left.createdAt ?? '')))[0];
}

function latestLedger(ledger?: CoreDispatchAttemptLedger[]): CoreDispatchAttemptLedger | undefined {
  return [...(ledger ?? [])].sort((left, right) => String(right.updatedAt ?? right.createdAt ?? '').localeCompare(String(left.updatedAt ?? left.createdAt ?? '')))[0];
}

function isRecoverableCategory(category: TaskDiagnosisBlockerCategory): boolean {
  return ['RUNTIME_BLOCKED', 'DELIVERY_BLOCKED', 'EXECUTION_BLOCKED'].includes(category);
}

function buildIssueDedupSummary(input: BuildTaskDiagnosisReadModelInput, primaryBlocker: TaskDiagnosisPrimaryBlocker): TaskIssueDedupSummary {
  const issueTracking = input.issueTracking ?? input.task.issueTracking;
  const taskCompleted = input.diagnosis.code === 'COMPLETED' || upper(input.task.status) === 'COMPLETED';
  const issueType = primaryBlocker.category === 'NONE' ? 'NO_ACTIVE_BLOCKER' : primaryBlocker.category;
  const issueScope = firstNonBlank(
    input.task.dispatchRequestId,
    input.task.assignedPoolId,
    input.task.targetPoolId,
    input.task.matchedFlowId,
    input.task.taskId,
  ) ?? input.task.taskId;
  const recoverable = isRecoverableCategory(primaryBlocker.category);
  const governanceReviewRequired = primaryBlocker.category === 'CONFIGURATION_BLOCKED' || primaryBlocker.category === 'MANUAL_ACTION_REQUIRED';
  const activeStatus = primaryBlocker.category === 'NONE'
    ? 'NOT_REQUIRED'
    : taskCompleted && recoverable
      ? 'AUTO_RESOLVED'
      : governanceReviewRequired
        ? 'REVIEW_REQUIRED'
        : 'ACTIVE';
  const occurrenceCount = Number(input.task.occurrenceCountAtCreation ?? 0) > 0 ? Number(input.task.occurrenceCountAtCreation) : 1;
  const lastOccurredAt = firstNonBlank(issueTracking?.updatedAt, issueTracking?.lastAdapterActionAt, input.task.updatedAt, input.task.createdAt);
  const firstOccurredAt = firstNonBlank(issueTracking?.createdAt, input.task.createdAt, lastOccurredAt);
  const autoResolutionPolicy = primaryBlocker.category === 'NONE'
    ? 'NO_ACTIVE_ISSUE_REQUIRED'
    : recoverable
      ? 'RECOVERABLE_AUTO_RESOLVE_ON_COMPLETION'
      : 'GOVERNANCE_REVIEW_REQUIRED';
  const activeIssueKey = `${input.task.taskId}:${issueType}:${issueScope}:${activeStatus}`;
  const derived: TaskIssueDedupSummary = {
    activeIssueKey,
    issueType,
    issueScope,
    activeStatus,
    syncStatus: issueTracking?.syncStatus,
    externalIssueId: issueTracking?.issueId,
    externalIssueUrl: issueTracking?.issueUrl,
    occurrenceCount,
    firstOccurredAt,
    lastOccurredAt,
    autoResolutionPolicy,
    governanceReviewRequired,
    dedupRule: 'taskId + issueType + issueScope + activeStatus',
    repeatedOccurrenceBehavior: 'Update occurrenceCount and lastOccurredAt; do not create duplicate active Issue.',
    summary: activeStatus === 'NOT_REQUIRED'
      ? '目前沒有 active issue 需要建立。'
      : activeStatus === 'AUTO_RESOLVED'
        ? '此 Task 已完成；可恢復型 Issue 應自動解決，治理型 Issue 仍需人工 review。'
        : '相同 blocker 重複發生時更新 occurrence count，不重複建立 active Issue。',
  };
  return { ...derived, ...input.externalIssueDedup } as TaskIssueDedupSummary;
}

function timelineStatus(stage: TaskUnifiedTimelineStage, input: BuildTaskDiagnosisReadModelInput): TaskDiagnosisTimelineLink['status'] {
  const diagnosisCode = input.diagnosis.code;
  if (stage === 'COMPLETION') return input.diagnosis.code === 'COMPLETED' ? 'done' : 'waiting';
  if (input.diagnosis.code === 'COMPLETED') return 'done';
  if ((stage === 'ROUTING' || stage === 'FLOW') && diagnosisCode === 'NO_MATCHING_FLOW') return 'blocked';
  if ((stage === 'POOL') && ['NO_FLOW_AGENT', 'AGENT_OFFLINE', 'AGENT_CAPACITY_FULL'].includes(diagnosisCode)) return 'blocked';
  if (stage === 'DELIVERY' && diagnosisCode === 'DISPATCH_DELIVERY_FAILED') return 'failed';
  if ((stage === 'ACK' || stage === 'RESULT') && diagnosisCode === 'RESULT_TIMEOUT') return 'failed';
  if (stage === 'EVENT' || stage === 'TASK') return 'done';
  if (stage === 'ROUTING') return input.task.matchedFlowId ? 'done' : 'current';
  if (stage === 'FLOW') return input.task.matchedFlowId ? 'done' : 'current';
  if (stage === 'POOL') return input.task.targetPoolId || input.task.assignedPoolId || input.task.assignedAgentId ? 'done' : 'waiting';
  if (stage === 'ASSIGNMENT') return input.task.assignedAgentId || input.task.dispatchRequestId ? 'done' : 'waiting';
  if (stage === 'DELIVERY') return input.task.dispatchDeliveryStatus || input.task.dispatchStatus ? 'current' : 'waiting';
  if (stage === 'ACK') return input.callbackInboxSummary?.latestCallbackType === 'ACK' || input.callbackInboxSummary?.terminalCallbackReceived ? 'done' : 'waiting';
  if (stage === 'RESULT') return input.callbackInboxSummary?.terminalCallbackReceived || input.task.callbackStatus ? 'current' : 'waiting';
  if (stage === 'RETRY') return input.task.dispatchAttemptCount && input.task.dispatchAttemptCount > 1 ? 'done' : 'waiting';
  if (stage === 'MANUAL_ACTION') return input.remediationCommandAudits?.length ? 'done' : 'waiting';
  if (stage === 'ISSUE') return input.task.issueTracking || input.issueTracking ? 'current' : 'waiting';
  return 'waiting';
}

function fromCoreTimeline(event: CoreDispatchTimelineEvent): TaskDiagnosisTimelineLink | undefined {
  const stage = upper(event.stage);
  const normalized: TaskUnifiedTimelineStage | undefined = stage.includes('RETRY') ? 'RETRY'
    : stage.includes('ISSUE') ? 'ISSUE'
      : stage.includes('ACK') ? 'ACK'
        : stage.includes('RESULT') || stage.includes('CALLBACK') ? 'RESULT'
          : stage.includes('DELIVERY') || stage.includes('DISPATCH') ? 'DELIVERY'
            : stage.includes('ASSIGN') ? 'ASSIGNMENT'
              : stage.includes('FLOW') || stage.includes('ROUT') ? 'ROUTING'
                : stage.includes('TASK') ? 'TASK'
                  : stage.includes('EVENT') || stage.includes('INTAKE') ? 'EVENT'
                    : undefined;
  if (!normalized) return undefined;
  const statusText = upper(event.status);
  const status: TaskDiagnosisTimelineLink['status'] = statusText.includes('FAIL') ? 'failed'
    : statusText.includes('BLOCK') ? 'blocked'
      : statusText.includes('WAIT') ? 'waiting'
        : statusText.includes('CURRENT') || statusText.includes('RUNNING') ? 'current'
          : 'done';
  return {
    stage: normalized,
    label: event.action || event.stage || normalized,
    status,
    detail: event.message ?? `${event.stage} ${event.action}`,
    occurredAt: event.occurredAt,
    source: 'CORE_TIMELINE',
    references: event.references,
  };
}

function buildTimeline(input: BuildTaskDiagnosisReadModelInput, pool: TaskPoolEligibilitySummary, issueDedup: TaskIssueDedupSummary): TaskDiagnosisTimelineLink[] {
  const latest = latestLedger(input.dispatchLedger);
  const coreEvents = input.timeline?.events?.map(fromCoreTimeline).filter(Boolean) as TaskDiagnosisTimelineLink[] | undefined;
  const remediationEvents: TaskDiagnosisTimelineLink[] = (input.remediationCommandAudits ?? []).map((audit) => ({
    stage: 'MANUAL_ACTION',
    label: `Manual command · ${audit.commandType}`,
    status: 'done',
    detail: `${audit.reason}; before=${audit.beforeState.status ?? '-'} after=${audit.afterState.status ?? '-'}`,
    occurredAt: audit.timestamp,
    source: 'REMEDIATION_COMMAND_AUDIT',
    references: {
      idempotencyKey: audit.idempotencyKey,
      operatorId: audit.operatorId,
    },
  }));
  const base: TaskDiagnosisTimelineLink[] = [
    { stage: 'EVENT', label: 'Event', status: timelineStatus('EVENT', input), detail: input.task.sourceEventId ? `Event ${input.task.sourceEventId}` : 'Core 已建立或接收 Task 來源。', occurredAt: input.task.createdAt, source: 'TASK' },
    { stage: 'TASK', label: 'Task', status: timelineStatus('TASK', input), detail: `Task ${input.task.taskId}`, occurredAt: input.task.createdAt, source: 'TASK' },
    { stage: 'ROUTING', label: 'Routing', status: timelineStatus('ROUTING', input), detail: input.task.matchedFlowId ? `Flow ${input.task.matchedFlowId}${input.task.matchedRuleId ? ` · Rule ${input.task.matchedRuleId}` : ' · Default Pool'}` : '尚未命中 Source Flow。', source: 'ROUTING_EVIDENCE' },
    { stage: 'ASSIGNMENT', label: 'Assignment', status: timelineStatus('ASSIGNMENT', input), detail: input.task.assignedAgentId ? `Agent ${input.task.assignedAgentId}` : '尚未選出 Agent。', source: 'ROUTING_EVIDENCE' },
    { stage: 'DELIVERY', label: 'Delivery', status: timelineStatus('DELIVERY', input), detail: latest?.deliveryState ?? input.task.dispatchDeliveryStatus ?? input.task.dispatchStatus ?? '尚未建立或送達 Delivery。', occurredAt: latest?.dispatchedAt, source: 'DISPATCH_LEDGER' },
    { stage: 'ACK', label: 'ACK', status: timelineStatus('ACK', input), detail: input.callbackInboxSummary?.latestCallbackType === 'ACK' ? '已收到 ACK callback。' : '等待 ACK callback。', occurredAt: latest?.ackReceivedAt, source: 'CALLBACK_INBOX' },
    { stage: 'RESULT', label: 'Result', status: timelineStatus('RESULT', input), detail: input.callbackInboxSummary?.terminalCallbackReceived ? '已收到 RESULT / ERROR 終態 callback。' : input.task.callbackStatus ?? '等待 RESULT callback。', occurredAt: latest?.resultReceivedAt ?? latest?.terminalAt, source: 'CALLBACK_INBOX' },
    { stage: 'RETRY', label: 'Retry', status: timelineStatus('RETRY', input), detail: input.task.dispatchAttemptCount && input.task.dispatchAttemptCount > 1 ? `dispatchAttemptCount=${input.task.dispatchAttemptCount}` : '尚未發生 retry。', occurredAt: input.task.nextDispatchAttemptAt, source: 'DISPATCH_LEDGER' },
    ...remediationEvents,
    { stage: 'ISSUE', label: 'Issue', status: issueDedup.activeStatus === 'ACTIVE' || issueDedup.activeStatus === 'REVIEW_REQUIRED' ? 'current' : issueDedup.activeStatus === 'AUTO_RESOLVED' ? 'done' : 'waiting', detail: `${issueDedup.issueType} · ${issueDedup.summary}`, occurredAt: issueDedup.lastOccurredAt, source: 'ISSUE_DEDUP', references: { activeIssueKey: issueDedup.activeIssueKey } },
    { stage: 'COMPLETION', label: 'Completion', status: timelineStatus('COMPLETION', input), detail: input.diagnosis.code === 'COMPLETED' ? 'Task 已完成。' : '尚未完成。', occurredAt: input.task.updatedAt, source: 'TASK' },
  ];
  return [...(coreEvents ?? []), ...base].sort((left, right) => String(left.occurredAt ?? '').localeCompare(String(right.occurredAt ?? '')) || 0);
}

export function buildTaskDiagnosisReadModel(input: BuildTaskDiagnosisReadModelInput): TaskDiagnosisReadModel {
  const latestRequest = latestDispatchRequest(input.dispatchRequests);
  const latest = latestLedger(input.dispatchLedger);
  const poolEligibility = buildPoolEligibility(input);
  const primaryBlocker = buildPrimaryBlocker(input.task, input.diagnosis);
  const issueDedup = buildIssueDedupSummary(input, primaryBlocker);
  return {
    taskId: input.task.taskId,
    currentStage: currentStageFromDiagnosis(input.diagnosis),
    primaryBlocker,
    secondaryBlockers: buildSecondaryBlockers(input),
    routingEvidence: {
      flowId: input.task.matchedFlowId ?? input.dispatchEvidence?.latestRoutingDecision?.routingPolicy,
      ruleId: input.task.matchedRuleId,
      targetPoolId: input.task.targetPoolId ?? input.task.assignedPoolId ?? input.task.targetPoolCode,
      selectionStrategy: input.task.routingStrategy ?? input.task.routingPolicy,
      poolMemberCount: input.task.poolMemberCount ?? poolEligibility.totalMembers,
      eligibleAgentCount: input.task.eligibleAgentCount ?? poolEligibility.eligible,
      selectedAgentId: input.task.assignedAgentId ?? input.runtimeVerification?.selectedAgentId ?? input.dispatchEvidence?.latestRoutingDecision?.selectedAgentId,
      correlationId: input.task.correlationId,
      traceId: input.task.traceId,
    },
    poolEligibility,
    assignment: {
      assignedAgentId: input.task.assignedAgentId ?? latestRequest?.agentId,
      dispatchRequestId: input.task.dispatchRequestId ?? latestRequest?.dispatchRequestId,
      dispatchRequestStatus: latestRequest?.status,
      assignmentId: latestRequest?.assignmentId ?? latest?.assignmentId,
      assignmentKnown: Boolean(input.task.assignedAgentId || input.task.dispatchRequestId || latestRequest?.assignmentId || latest?.assignmentId),
    },
    delivery: {
      dispatchStatus: input.task.dispatchStatus,
      dispatchExecutionStatus: input.task.dispatchExecutionStatus,
      dispatchDeliveryStatus: input.task.dispatchDeliveryStatus,
      latestLedgerStatus: latest?.dispatchStatus,
      latestLedgerDeliveryState: latest?.deliveryState,
      deliveryKnown: Boolean(input.task.dispatchStatus || input.task.dispatchDeliveryStatus || latest?.deliveryState || latest?.dispatchedAt),
    },
    ackResult: {
      callbackStatus: input.task.callbackStatus,
      latestCallbackType: input.callbackInboxSummary?.latestCallbackType,
      latestCallbackId: input.callbackInboxSummary?.latestCallbackId,
      terminalCallbackReceived: input.callbackInboxSummary?.terminalCallbackReceived,
      resultKnown: Boolean(input.task.callbackStatus || input.callbackInboxSummary?.terminalCallbackReceived || latest?.resultReceivedAt || latest?.terminalAt),
    },
    issueDedup,
    timeline: buildTimeline(input, poolEligibility, issueDedup),
    generatedAt: new Date().toISOString(),
  };
}

export function taskDiagnosisCategoryLabel(category: TaskDiagnosisBlockerCategory): string {
  switch (category) {
    case 'CONFIGURATION_BLOCKED': return '設定問題';
    case 'RUNTIME_BLOCKED': return 'Runtime 問題';
    case 'DELIVERY_BLOCKED': return 'Delivery 問題';
    case 'EXECUTION_BLOCKED': return '執行／Callback 問題';
    case 'MANUAL_ACTION_REQUIRED': return '需要人工處置';
    default: return '沒有阻擋';
  }
}

export function taskDiagnosisOwnerPlaneLabel(ownerPlane: TaskDiagnosisOwnerPlane): string {
  switch (ownerPlane) {
    case 'CORE_CONFIGURATION': return 'Core 設定';
    case 'CORE_ROUTING': return 'Core Routing';
    case 'NETTY_RUNTIME': return 'Netty Runtime';
    case 'AGENT_RUNTIME': return 'Agent Runtime';
    default: return '操作員';
  }
}
