import type { CoreAdapterAction, CoreTaskIssueTracking, CoreTaskRuntimeView } from '@/lib/types/core';
import type { CoreAdapterExecutorAuditView, CoreIssuePolicyDecisionView } from '@/lib/types/domains/task';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function text(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
}

function pick(record: Record<string, unknown> | undefined, keys: string[]): string | undefined {
  if (!record) return undefined;
  for (const key of keys) {
    const value = text(record[key]);
    if (value) return value;
  }
  return undefined;
}


function nested(record: Record<string, unknown>, key: string): Record<string, unknown> | undefined {
  const value = record[key];
  return isRecord(value) ? value : undefined;
}

function truncate(value: string | undefined, max = 220): string | undefined {
  if (!value) return undefined;
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`;
}

function normalizeVendor(value: string | undefined): string | undefined {
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase().replace(/[-_](COMMENT|NOTE|ISSUE|RESPONSE)$/i, '');
  if (normalized.includes('REDMINE')) return 'REDMINE';
  if (normalized.includes('GITLAB')) return 'GITLAB';
  if (normalized.includes('JIRA')) return 'JIRA';
  if (normalized.includes('MOCK')) return 'MOCK';
  return normalized || undefined;
}

function parseIssueReference(value?: string): Record<string, unknown> | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  const colon = trimmed.indexOf(':');
  if (colon > 0 && colon < trimmed.length - 1) {
    return { vendor: normalizeVendor(trimmed.slice(0, colon)), issueId: trimmed.slice(colon + 1).trim() };
  }
  const hash = trimmed.lastIndexOf('#');
  if (hash > 0 && hash < trimmed.length - 1) {
    return { issueId: trimmed.slice(hash + 1).trim() };
  }
  return { issueId: trimmed };
}

function parseResponseRef(responseRef?: string): Record<string, unknown> | undefined {
  if (!responseRef) return undefined;
  try {
    const parsed = JSON.parse(responseRef);
    if (!isRecord(parsed)) return undefined;
    const vendor = normalizeVendor(pick(parsed, ['vendor', 'issueVendor']));
    return vendor ? { ...parsed, vendor } : parsed;
  } catch {
    return parseIssueReference(responseRef);
  }
}

function actionTime(action: CoreAdapterAction): number {
  return Date.parse(action.updatedAt ?? action.completedAt ?? action.createdAt ?? '') || 0;
}

function actionMatchesTask(action: CoreAdapterAction, taskId: string): boolean {
  const payload = isRecord(action.payload) ? action.payload : {};
  const payloadTask = nested(payload, 'task') ?? {};
  const agentResult = nested(payload, 'agentResult') ?? {};
  const candidates = [
    action.taskId,
    pick(payload, ['taskId', 'task_id']),
    pick(payloadTask, ['taskId', 'task_id']),
    pick(agentResult, ['taskId', 'task_id'])
  ].filter(Boolean);
  return candidates.includes(taskId);
}

function issueActions(actions: CoreAdapterAction[]): CoreAdapterAction[] {
  return actions
    .filter((action) => String(action.adapterType ?? '').toUpperCase() === 'ISSUE_TRACKING')
    .sort((left, right) => actionTime(right) - actionTime(left));
}

function issueTrackingFromAction(action: CoreAdapterAction, audit?: CoreAdapterExecutorAuditView): Record<string, unknown> {
  const payload = isRecord(action.payload) ? action.payload : {};
  const ref = parseResponseRef(action.responseRef) ?? {};
  const payloadIssueRef = parseIssueReference(pick(payload, ['linkedIssueId', 'issueId', 'externalIssueId', 'iid', 'key'])) ?? {};
  const status = String(action.status ?? '').toUpperCase();
  const agentResult = nested(payload, 'agentResult') ?? {};
  const syncError = audit?.providerFailureCode || (status === 'FAILED' || status === 'EXECUTOR_UNAVAILABLE' ? action.lastError ?? action.reason : undefined);
  const pending = ['PENDING', 'CLAIMED', 'EXECUTING', 'RETRY_WAITING'].includes(status);
  const completed = status === 'COMPLETED';
  const auditOutcome = String(audit?.outcome ?? '').toUpperCase();
  const providerConfirmed = Boolean(audit && (
    auditOutcome === 'SUCCESS' ||
    (typeof audit.providerStatusCode === 'number' && audit.providerStatusCode >= 200 && audit.providerStatusCode < 300)
  ));
  const providerExternalIssueId = audit?.externalIssueId ?? pick(ref, ['issueId', 'iid', 'key']) ?? pick(payloadIssueRef, ['issueId']) ?? pick(payload, ['linkedIssueId', 'issueId', 'externalIssueId', 'iid', 'key']);
  const providerIssueUrl = pick(ref, ['issueUrl', 'webUrl', 'url']) ?? pick(payload, ['issueUrl', 'webUrl', 'url']);

  return {
    issueVendor: normalizeVendor(pick(ref, ['vendor']) ?? pick(payloadIssueRef, ['vendor']) ?? pick(payload, ['issueVendor', 'vendor', 'provider', 'issueProvider'])),
    // Provider result is evidence, not the canonical TaskIssueLink. Keep it separate so the UI cannot
    // call a Task LINKED until the TaskIssueLink read model actually exists.
    providerExternalIssueId,
    providerIssueUrl,
    issueStatus: pick(ref, ['issueStatus', 'state', 'status']) ?? (completed ? 'completed' : status.toLowerCase()),
    lastSyncedAt: providerConfirmed ? audit?.createdAt ?? action.completedAt ?? action.updatedAt : undefined,
    syncError,
    syncStatus: pending ? 'SYNC_PENDING' : completed ? (providerConfirmed ? 'PROVIDER_CONFIRMED' : 'ACTION_COMPLETED') : status,
    issueActionId: action.actionId,
    issueActionType: action.actionType,
    issueActionStatus: action.status,
    issueRetryable: ['FAILED', 'EXECUTOR_UNAVAILABLE', 'RETRY_WAITING'].includes(status),
    providerStatusCode: audit?.providerStatusCode,
    providerFailureCode: audit?.providerFailureCode,
    providerHealthImpact: audit?.providerHealthImpact,
    providerOutcomeCertainty: audit?.providerOutcomeCertainty,
    operationFingerprint: audit?.operationFingerprint,
    correlationId: audit?.correlationId,
    a2aRequestId: audit?.a2aRequestId,
    sourceSystemId: audit?.sourceSystemId,
    technicalPrincipalId: audit?.technicalPrincipalId,
    credentialId: audit?.credentialId,
    credentialVersion: audit?.credentialVersion,
    idempotencyKey: audit?.idempotencyKey ?? action.idempotencyKey,
    latestAgentSummary: pick(agentResult, ['summary', 'agentSummary', 'resultSummary']) ?? pick(payload, ['agentSummary', 'summary', 'resultSummary', 'callbackMessage']),
    issueCommentMode: pick(payload, ['issueCommentMode']) ?? 'APPEND',
    issueCommentPreview: truncate(pick(payload, ['issueComment'])),
    agentResultFormatVersion: pick(payload, ['agentResultFormatVersion']) ?? pick(agentResult, ['formatVersion']),
    message: pending
      ? 'Issue Tracking action is waiting for adapter execution.'
      : syncError
        ? `Issue provider operation failed: ${syncError}`
        : completed
          ? 'Issue provider operation completed. Provider evidence is recorded on the canonical AdapterAction execution audit.'
          : 'Issue operation has not completed.'
  };
}

function latestAuditForAction(actionId: string | undefined, audits: CoreAdapterExecutorAuditView[]): CoreAdapterExecutorAuditView | undefined {
  if (!actionId) return undefined;
  return audits
    .filter((audit) => audit.actionId === actionId)
    .sort((left, right) => (Date.parse(right.createdAt ?? '') || 0) - (Date.parse(left.createdAt ?? '') || 0))[0];
}

function existingIssueTracking(task: CoreTaskRuntimeView): Record<string, unknown> {
  if (isRecord(task.issueTracking)) return task.issueTracking;
  if (isRecord(task.payload) && isRecord(task.payload.issueTracking)) return task.payload.issueTracking;
  return {};
}

export function mergeIssueTrackingIntoTask(
  task: CoreTaskRuntimeView,
  actions: CoreAdapterAction[],
  providerExecutions: CoreAdapterExecutorAuditView[] = [],
  issuePolicyDecision?: CoreIssuePolicyDecisionView,
): CoreTaskRuntimeView {
  const issues = issueActions(actions);
  const payload = isRecord(task.payload) ? { ...task.payload } : {};
  const existing = existingIssueTracking(task);

  if (issues.length === 0) {
    if (!issuePolicyDecision && providerExecutions.length === 0) return task;
    return {
      ...task,
      payload: {
        ...payload,
        ...(Object.keys(existing).length > 0 ? { issueTracking: existing } : {}),
        ...(issuePolicyDecision ? { issuePolicyDecision } : {}),
        ...(providerExecutions.length > 0 ? { issueProviderExecutions: providerExecutions } : {}),
      },
    };
  }

  const requestedActionId = issuePolicyDecision?.adapterActionId;
  const best = (requestedActionId ? issues.find((action) => action.actionId === requestedActionId) : undefined) ?? issues[0];
  const actionTracking = issueTrackingFromAction(best, latestAuditForAction(best.actionId, providerExecutions));
  // TaskIssueLink is the canonical provider read model when it exists. AdapterAction/audit only fill missing evidence.
  const issueTracking = { ...actionTracking, ...existing } as CoreTaskIssueTracking;
  return {
    ...task,
    issueTracking,
    payload: {
      ...payload,
      issueTracking,
      issueActions: issues,
      ...(issuePolicyDecision ? { issuePolicyDecision } : {}),
      ...(providerExecutions.length > 0 ? { issueProviderExecutions: providerExecutions } : {}),
    }
  };
}

export function mergeIssueTrackingIntoTasks(
  tasks: CoreTaskRuntimeView[],
  actions: CoreAdapterAction[],
  providerExecutions: CoreAdapterExecutorAuditView[] = [],
): CoreTaskRuntimeView[] {
  if (actions.length === 0 && providerExecutions.length === 0) return tasks;
  return tasks.map((task) => mergeIssueTrackingIntoTask(
    task,
    actions.filter((action) => actionMatchesTask(action, task.taskId)),
    providerExecutions.filter((audit) => !audit.taskId || audit.taskId === task.taskId),
  ));
}
