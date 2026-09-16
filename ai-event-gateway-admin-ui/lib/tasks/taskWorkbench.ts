import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import type { CoreTaskRuntimeView } from '@/lib/types/core';

export interface TaskWorkbenchIssueBridge {
  status: 'LINKED' | 'NOT_LINKED' | 'SYNC_PENDING' | 'SYNC_FAILED' | 'PROVIDER_COMPLETED' | 'NOT_REQUIRED' | 'MANUAL_REQUIRED';
  vendor?: string;
  issueId?: string;
  issueUrl?: string;
  providerExternalIssueId?: string;
  providerIssueUrl?: string;
  issueStatus?: string;
  lastSyncedAt?: string;
  message: string;
  nextAction?: string;
  actionId?: string;
  actionType?: string;
  syncStatus?: string;
  retryable?: boolean;
  failureCode?: string;
  providerStatusCode?: number;
  providerHealthImpact?: string;
  providerOutcomeCertainty?: string;
  idempotencyKey?: string;
  operationFingerprint?: string;
  correlationId?: string;
  a2aRequestId?: string;
  sourceSystemId?: string;
  credentialVersion?: string;
  commentMode?: string;
  latestCommentPreview?: string;
  agentHistorySynced?: boolean;
  policy?: string;
  policySource?: string;
  policyInheritanceMode?: string;
  policyInheritedFromTaskId?: string;
  decision?: string;
  decisionReason?: string;
  bindingStatus?: string;
  automationStatus?: string;
  connectionId?: string;
  projectMappingId?: string;
}

export interface TaskWorkbenchSeverity {
  code: string;
  label: string;
  isHighImpact: boolean;
}

export interface TaskWorkbenchDisplay {
  title: string;
  subtitle: string;
  businessStatus: string;
  businessStatusCode: string;
  severity: TaskWorkbenchSeverity;
  sourceLabel: string;
  targetLabel: string;
  eventLabel: string;
  triggerReason: string;
  assignedAgentLabel: string;
  requiredCapabilityLabel: string;
  expectedOutputs: string[];
  latestAgentSummary: string;
  issueBridge: TaskWorkbenchIssueBridge;
  nextStep: string;
  platformHealth: 'OK' | 'WAITING' | 'BLOCKED' | 'FAILED' | 'COMPLETED';
}

function text(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function pick(record: Record<string, unknown> | undefined, keys: string[]): string | undefined {
  if (!record) return undefined;
  for (const key of keys) {
    const value = text(record[key]);
    if (value) return value;
  }
  return undefined;
}

function nestedRecords(task: CoreTaskRuntimeView): Record<string, unknown>[] {
  const records: Record<string, unknown>[] = [];
  if (isRecord(task.payload)) {
    records.push(task.payload);
    if (isRecord(task.payload.task)) records.push(task.payload.task);
    if (isRecord(task.payload.dispatch)) records.push(task.payload.dispatch);
    if (isRecord(task.payload.issue)) records.push(task.payload.issue);
    if (isRecord(task.payload.issueLink)) records.push(task.payload.issueLink);
    if (isRecord(task.payload.issueTracking)) records.push(task.payload.issueTracking);
    if (isRecord(task.payload.issuePolicyDecision)) records.push(task.payload.issuePolicyDecision);
    if (isRecord(task.payload.agentResult)) records.push(task.payload.agentResult);
    if (isRecord(task.payload.issueTracking)) {
      const issueTracking = task.payload.issueTracking;
      if (isRecord(issueTracking.agentResult)) records.push(issueTracking.agentResult);
    }
    if (isRecord(task.payload.result)) records.push(task.payload.result);
  }
  return records;
}

function pickFromTask(task: CoreTaskRuntimeView, direct: keyof CoreTaskRuntimeView, payloadKeys: string[]): string | undefined {
  const directValue = text(task[direct]);
  if (directValue) return directValue;
  for (const record of nestedRecords(task)) {
    const value = pick(record, payloadKeys);
    if (value) return value;
  }
  return undefined;
}

function normalize(value: unknown): string {
  return text(value)?.toUpperCase() ?? '';
}

function pickFromNested(task: CoreTaskRuntimeView, keys: string[]): string | undefined {
  for (const record of nestedRecords(task)) {
    const value = pick(record, keys);
    if (value) return value;
  }
  return undefined;
}

function severityDisplay(task: CoreTaskRuntimeView): TaskWorkbenchSeverity {
  const raw = text(task.priority)
    ?? pickFromNested(task, ['severity', 'eventSeverity', 'incidentSeverity', 'priority', 'riskLevel', 'risk_level'])
    ?? 'UNKNOWN';
  const code = normalize(raw) || 'UNKNOWN';
  const labels: Record<string, string> = {
    CRITICAL: 'CRITICAL · ',
    HIGH: 'HIGH · ',
    MIDDLE: 'MIDDLE · Medium priority',
    MEDIUM: 'MIDDLE · Medium priority',
    LOW: 'LOW · ',
    UNKNOWN: 'UNKNOWN · '
  };
  return {
    code: code === 'MEDIUM' ? 'MIDDLE' : code,
    label: labels[code] ?? `${code} · Event details`,
    isHighImpact: ['CRITICAL', 'HIGH'].includes(code)
  };
}

function humanizeCode(value: string | undefined): string {
  if (!value) return '-';
  return value
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
    .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
}

function problemLabel(task: CoreTaskRuntimeView): string {
  const eventType = normalize(task.eventType);
  const errorCode = normalize(task.errorCode);
  const objectType = normalize(task.objectType);

  if (errorCode === 'TEMP_HIGH') return 'High temperature';
  if (eventType.includes('EQUIPMENT') && eventType.includes('ALARM')) return 'Equipment alarm';
  if (objectType === 'EQUIPMENT') return 'Equipment event';
  if (eventType) return humanizeCode(eventType);
  return humanizeCode(task.taskType ?? 'Agent task');
}

function taskPurpose(task: CoreTaskRuntimeView): string {
  const taskType = normalize(task.taskType);
  const capabilities = (task.requiredCapabilities ?? []).map(normalize);
  if (capabilities.includes('INCIDENT_ANALYSIS')) return 'Incident analysis';
  if (taskType.includes('INCIDENT')) return 'Event details';
  if (capabilities.includes('ISSUE_CREATION')) return 'Issue create';
  if (capabilities.includes('TASK_EXECUTION')) return 'Taskrun';
  return humanizeCode(task.taskType ?? 'Agent task');
}

function sourceSystem(task: CoreTaskRuntimeView): string | undefined {
  return pickFromTask(task, 'sourceSystem', ['sourceSystem', 'source_system', 'systemCode', 'system_code', 'source']);
}

function buildTitle(task: CoreTaskRuntimeView): string {
  const priority = severityDisplay(task).code || 'TASK';
  const system = sourceSystem(task) ?? 'No Source System was provided';
  const target = task.objectId ?? task.objectType ?? 'Target';
  return `[${priority}]${system} ${target} ${problemLabel(task)}${taskPurpose(task) ? ` ${taskPurpose(task)}` : ''}`;
}

function sourceLabel(task: CoreTaskRuntimeView): string {
  const system = sourceSystem(task) ?? 'No Source System was provided';
  const location = [task.siteId, task.plantId].filter((value): value is string => Boolean(value));
  return location.length > 0 ? [system, ...location].join(' / ') : system;
}

function targetLabel(task: CoreTaskRuntimeView): string {
  const objectType = task.objectType ? humanizeCode(task.objectType) : 'Target';
  return `${objectType}: ${task.objectId ?? '-'}`;
}

function eventLabel(task: CoreTaskRuntimeView): string {
  const event = task.eventType ? humanizeCode(task.eventType) : '-';
  const error = task.errorCode ? ` / ${task.errorCode}` : '';
  return `${event}${error}`;
}

function businessStatus(row: TaskDispatchDashboardRow): { label: string; code: string; health: TaskWorkbenchDisplay['platformHealth']; nextStep: string } {
  const task = row.task;
  const status = normalize(task.status);
  const execution = normalize(task.dispatchExecutionStatus);
  const delivery = normalize(task.dispatchDeliveryStatus);
  const callback = normalize(task.callbackStatus);

  if (['COMPLETED', 'SUCCEEDED'].includes(status) || execution === 'COMPLETED') {
    return { label: 'Agent completed work', code: 'COMPLETED', health: 'COMPLETED', nextStep: 'Review the Agent result and any Issue operation evidence.' };
  }
  if (['CANCELLED', 'CANCELED'].includes(status)) {
    return { label: 'Task cancelled', code: 'CANCELLED', health: 'COMPLETED', nextStep: 'This Task was cancelled. Review timeline and issue evidence.' };
  }
  if (task.blockedReason) {
    return { label: 'Task blocked', code: 'BLOCKED', health: 'BLOCKED', nextStep: task.nextAction ?? 'Review dispatch, Gateway, Agent runtime, or policy evidence.' };
  }
  if (task.failureReason || ['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(status) || execution === 'FAILED') {
    return { label: 'Task failed', code: 'FAILED', health: 'FAILED', nextStep: task.failureReason ?? 'View details' };
  }
  if (['RUNNING'].includes(status) || execution === 'RUNNING') {
    return { label: 'Agent is working', code: 'RUNNING', health: 'OK', nextStep: 'waiting Agent Result' };
  }
  if (callback === 'CALLBACK_RECEIVED' || execution === 'ACKED') {
    return { label: 'Waiting for Agent result', code: 'ACKED', health: 'OK', nextStep: 'waiting Agent is working' };
  }
  if (delivery === 'DELIVERED_TO_GATEWAY' || execution === 'DELIVERED') {
    return { label: 'Task details Gateway, waiting Agent is working', code: 'DELIVERED', health: 'WAITING', nextStep: 'waiting Agent ACK / callback' };
  }
  if (task.dispatchRequestId && execution === 'QUEUED') {
    return { label: 'Dispatch queued', code: 'QUEUED', health: 'WAITING', nextStep: 'Wait for the Core dispatch worker to deliver the request to the Gateway.' };
  }
  if (task.assignedAgentId) {
    return { label: 'Agent assigned', code: 'ASSIGNED', health: 'WAITING', nextStep: 'Waiting for dispatch request and delivery evidence' };
  }
  return { label: 'Waiting for an eligible Agent', code: 'WAITING_AGENT', health: 'WAITING', nextStep: task.dispatchWaitReason ?? task.dispatchRetryReason ?? 'Review Source Flow, Agent Pool, Required Capability, runtime eligibility, capacity, and backoff.' };
}

function expectedOutputs(task: CoreTaskRuntimeView): string[] {
  const capabilities = (task.requiredCapabilities ?? []).map(normalize);
  const outputs = new Set<string>();
  if (capabilities.includes('INCIDENT_ANALYSIS') || normalize(task.taskType).includes('INCIDENT')) {
    outputs.add('Event summary');
    outputs.add('Incident diagnosis');
  }
  if (capabilities.includes('LOG_DIAGNOSTICS')) outputs.add('Log / metric / trace summary');
  if (capabilities.includes('ISSUE_CREATION')) outputs.add('Issue operation result');
  if (capabilities.includes('TASK_EXECUTION')) outputs.add('Task execution result');
  if (outputs.size === 0) outputs.add('Task-type result');
  return Array.from(outputs);
}

function latestAgentSummary(row: TaskDispatchDashboardRow): string {
  const task = row.task;
  for (const record of nestedRecords(task)) {
    const summary = pick(record, ['agentSummary', 'summary', 'resultSummary', 'latestAgentSummary', 'message']);
    if (summary) return summary;
  }
  if (row.callbackRelay?.reason) return row.callbackRelay.reason;
  if (task.lifecycleReason && !task.lifecycleReason.startsWith('Assigned to agent')) return task.lifecycleReason;
  if (task.dispatchWaitReason) return task.dispatchWaitReason;
  if (task.dispatchRetryReason) return task.dispatchRetryReason;
  return 'No Agent result has been received yet.';
}

function issueBridge(task: CoreTaskRuntimeView): TaskWorkbenchIssueBridge {
  const records = nestedRecords(task);
  let vendor: string | undefined;
  let issueId: string | undefined;
  let issueUrl: string | undefined;
  let issueStatus: string | undefined;
  let providerExternalIssueId: string | undefined;
  let providerIssueUrl: string | undefined;
  let lastSyncedAt: string | undefined;
  let syncError: string | undefined;
  let actionId: string | undefined;
  let actionType: string | undefined;
  let syncStatus: string | undefined;
  let failureCode: string | undefined;
  let providerStatusCode: number | undefined;
  let providerHealthImpact: string | undefined;
  let providerOutcomeCertainty: string | undefined;
  let idempotencyKey: string | undefined;
  let operationFingerprint: string | undefined;
  let correlationId: string | undefined;
  let a2aRequestId: string | undefined;
  let sourceSystemId: string | undefined;
  let credentialVersion: string | undefined;
  let retryable = false;

  for (const record of records) {
    vendor ??= pick(record, ['issueVendor', 'vendor', 'provider', 'issueProvider']);
    issueId ??= pick(record, ['issueId', 'issue_id', 'externalIssueId', 'external_issue_id', 'iid', 'key']);
    issueUrl ??= pick(record, ['issueUrl', 'issue_url', 'webUrl', 'web_url', 'url']);
    issueStatus ??= pick(record, ['issueStatus', 'issue_status', 'externalStatus', 'external_status']);
    providerExternalIssueId ??= pick(record, ['providerExternalIssueId']);
    providerIssueUrl ??= pick(record, ['providerIssueUrl']);
    lastSyncedAt ??= pick(record, ['lastSyncedAt', 'last_synced_at', 'syncedAt', 'synced_at', 'completedAt']);
    syncError ??= pick(record, ['syncError', 'sync_error', 'issueSyncError', 'lastError', 'lastErrorMessage']);
    actionId ??= pick(record, ['issueActionId', 'adapterActionId', 'actionId']);
    actionType ??= pick(record, ['issueActionType', 'issueOperation', 'actionType']);
    syncStatus ??= pick(record, ['syncStatus', 'issueActionStatus', 'actionStatus']);
    failureCode ??= pick(record, ['providerFailureCode', 'failureCode', 'lastErrorCode', 'errorCode']);
    const rawStatus = record.providerStatusCode ?? record.provider_status_code;
    if (providerStatusCode === undefined && typeof rawStatus === 'number' && Number.isFinite(rawStatus)) providerStatusCode = rawStatus;
    if (providerStatusCode === undefined && typeof rawStatus === 'string' && /^\d+$/.test(rawStatus)) providerStatusCode = Number(rawStatus);
    providerHealthImpact ??= pick(record, ['providerHealthImpact', 'healthImpact']);
    providerOutcomeCertainty ??= pick(record, ['providerOutcomeCertainty', 'outcomeCertainty']);
    idempotencyKey ??= pick(record, ['idempotencyKey', 'actionIdempotencyKey', 'issueActionIdempotencyKey']);
    operationFingerprint ??= pick(record, ['operationFingerprint']);
    correlationId ??= pick(record, ['correlationId']);
    a2aRequestId ??= pick(record, ['a2aRequestId']);
    sourceSystemId ??= pick(record, ['sourceSystemId']);
    credentialVersion ??= pick(record, ['credentialVersion']);
    if (record.issueRetryable === true || record.retryable === true) retryable = true;
  }

  const policy = task.issueSyncPolicy ?? pickFromNested(task, ['taskIssueSyncPolicy', 'issueSyncPolicy']);
  const policySource = task.issueSyncPolicySource ?? pickFromNested(task, ['issueSyncPolicySource']);
  const policyInheritanceMode = task.issueSyncPolicyInheritanceMode ?? pickFromNested(task, ['issueSyncPolicyInheritanceMode']);
  const policyInheritedFromTaskId = task.issueSyncPolicyInheritedFromTaskId ?? pickFromNested(task, ['issueSyncPolicyInheritedFromTaskId']);
  const decision = pickFromNested(task, ['decision']);
  const decisionReason = pickFromNested(task, ['reasonCode']);
  const bindingStatus = pickFromNested(task, ['bindingStatus']);
  const automationStatus = pickFromNested(task, ['automationStatus']);
  const connectionId = pickFromNested(task, ['connectionId']);
  const projectMappingId = pickFromNested(task, ['projectMappingId']);
  const normalizedDecision = normalize(decision);
  const normalizedAutomation = normalize(automationStatus);
  const normalizedSync = normalize(syncStatus);

  const common = {
    vendor, issueId, issueUrl, providerExternalIssueId, providerIssueUrl, issueStatus, lastSyncedAt, actionId, actionType, syncStatus, retryable,
    failureCode, providerStatusCode, providerHealthImpact, providerOutcomeCertainty, idempotencyKey, operationFingerprint,
    correlationId, a2aRequestId, sourceSystemId, credentialVersion,
    commentMode: pick(records.find((record) => pick(record, ['issueCommentMode', 'commentMode'])), ['issueCommentMode', 'commentMode']) ?? 'APPEND',
    latestCommentPreview: pick(records.find((record) => pick(record, ['issueCommentPreview'])), ['issueCommentPreview']),
    policy, policySource, policyInheritanceMode, policyInheritedFromTaskId, decision, decisionReason, bindingStatus, automationStatus, connectionId, projectMappingId,
  };

  if (normalizedDecision === 'NOT_REQUIRED' || normalizedAutomation === 'NOT_REQUIRED') {
    return {
      ...common,
      status: 'NOT_REQUIRED',
      agentHistorySynced: false,
      message: `No external Issue is required by policy${decisionReason ? ` (${decisionReason})` : ''}.`,
      nextAction: 'No provider action is required unless the Issue Policy is changed.'
    };
  }

  if (normalizedDecision === 'MANUAL_DECISION' || normalizedAutomation === 'WAITING_MANUAL_DECISION') {
    return {
      ...common,
      status: 'MANUAL_REQUIRED',
      agentHistorySynced: false,
      message: `Issue Policy requires an operator decision${decisionReason ? ` (${decisionReason})` : ''}.`,
      nextAction: 'Review Issue Policy authority and decide whether an external Issue should be created.'
    };
  }

  if (syncError || normalizedAutomation === 'FAILED' || ['FAILED', 'EXECUTOR_UNAVAILABLE'].includes(normalizedSync)) {
    return {
      ...common,
      status: 'SYNC_FAILED',
      agentHistorySynced: false,
      message: failureCode === 'ISSUE_PROVIDER_OUTCOME_UNCERTAIN'
        ? 'The provider may have accepted this write, but OpenDispatch could not confirm the final outcome. Do not retry blindly.'
        : failureCode === 'ISSUE_PROVIDER_PERMISSION_DENIED'
          ? 'The Issue provider denied this operation. Permissions and workflow rules are managed by the provider.'
          : failureCode === 'ISSUE_PROVIDER_AUTHENTICATION_FAILED'
            ? 'Issue provider authentication failed. Review the configured integration credential.'
            : syncError ? `Issue operation failed: ${syncError}` : 'Issue automation failed before a confirmed provider result.',
      nextAction: failureCode === 'ISSUE_PROVIDER_OUTCOME_UNCERTAIN'
        ? 'Inspect the provider and reconcile before any retry.'
        : retryable ? 'Retry the provider operation when appropriate.' : 'Review Issue Policy, binding, AdapterAction, and provider evidence.'
    };
  }

  if (issueId || issueUrl) {
    return {
      ...common,
      status: 'LINKED',
      agentHistorySynced: true,
      message: 'External Issue is linked to this Task with canonical provider/read-model evidence.',
      nextAction: 'Open the external Issue or review provider evidence.'
    };
  }

  if (actionId && normalizedSync === 'PROVIDER_CONFIRMED') {
    return {
      ...common,
      status: 'PROVIDER_COMPLETED',
      agentHistorySynced: false,
      message: 'Provider execution is confirmed, but no canonical TaskIssueLink reference is currently available.',
      nextAction: 'Inspect TaskIssueLink/read-model evidence before retrying the provider operation.'
    };
  }

  if (actionId) {
    const actionCompletedWithoutProviderEvidence = normalizedSync === 'ACTION_COMPLETED';
    return {
      ...common,
      status: 'SYNC_PENDING',
      agentHistorySynced: false,
      message: actionCompletedWithoutProviderEvidence
        ? 'AdapterAction completed, but canonical provider execution evidence has not been observed yet.'
        : 'Issue automation entered Route B and the AdapterAction is pending or executing.',
      nextAction: 'Wait for canonical provider evidence or inspect the current Route B failure boundary.'
    };
  }

  return {
    ...common,
    status: 'NOT_LINKED',
    agentHistorySynced: false,
    message: decision
      ? `Issue Policy decision is ${decision}, but no AdapterAction/provider evidence is available yet.`
      : 'No Issue Policy decision or external Issue operation evidence is available yet.',
    nextAction: 'Inspect Issue Policy authority before changing provider or mapping configuration.'
  };
}

export function buildTaskWorkbenchDisplay(row: TaskDispatchDashboardRow): TaskWorkbenchDisplay {
  const task = row.task;
  const status = businessStatus(row);
  const capabilities = task.requiredCapabilities?.length ? task.requiredCapabilities.join(', ') : 'Not declared';
  const agent = task.assignedAgentId ? task.assignedAgentId : 'Not assigned';
  const severity = severityDisplay(task);

  return {
    title: buildTitle(task),
    subtitle: `Task ${task.taskId}${task.incidentId ? ` · Incident ${task.incidentId}` : ''}`,
    businessStatus: status.label,
    businessStatusCode: status.code,
    severity,
    sourceLabel: sourceLabel(task),
    targetLabel: targetLabel(task),
    eventLabel: eventLabel(task),
    triggerReason: task.createdReason ?? task.lifecycleReason ?? 'Created by the Core task/routing workflow',
    assignedAgentLabel: agent,
    requiredCapabilityLabel: capabilities,
    expectedOutputs: expectedOutputs(task),
    latestAgentSummary: latestAgentSummary(row),
    issueBridge: issueBridge(task),
    nextStep: status.nextStep,
    platformHealth: status.health
  };
}
