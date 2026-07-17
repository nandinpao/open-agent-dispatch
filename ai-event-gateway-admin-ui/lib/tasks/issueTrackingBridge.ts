import type { CoreAdapterAction, CoreTaskRuntimeView } from '@/lib/types/core';

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

function issueTrackingFromAction(action: CoreAdapterAction): Record<string, unknown> {
  const payload = isRecord(action.payload) ? action.payload : {};
  const ref = parseResponseRef(action.responseRef) ?? {};
  const payloadIssueRef = parseIssueReference(pick(payload, ['linkedIssueId', 'issueId', 'externalIssueId', 'iid', 'key'])) ?? {};
  const status = String(action.status ?? '').toUpperCase();
  const agentResult = nested(payload, 'agentResult') ?? {};
  const syncError = status === 'FAILED' || status === 'EXECUTOR_UNAVAILABLE' ? action.lastError ?? action.reason : undefined;
  const pending = ['PENDING', 'CLAIMED', 'EXECUTING', 'RETRY_WAITING'].includes(status);

  return {
    issueVendor: normalizeVendor(pick(ref, ['vendor']) ?? pick(payloadIssueRef, ['vendor']) ?? pick(payload, ['issueVendor', 'vendor', 'provider', 'issueProvider'])),
    issueId: pick(ref, ['issueId', 'iid', 'key']) ?? pick(payloadIssueRef, ['issueId']) ?? pick(payload, ['linkedIssueId', 'issueId', 'externalIssueId', 'iid', 'key']),
    issueUrl: pick(ref, ['issueUrl', 'webUrl', 'url']) ?? pick(payload, ['issueUrl', 'webUrl', 'url']),
    issueStatus: pick(ref, ['issueStatus', 'state', 'status']) ?? (status === 'COMPLETED' ? 'synced' : status.toLowerCase()),
    lastSyncedAt: status === 'COMPLETED' ? action.completedAt ?? action.updatedAt : undefined,
    syncError,
    syncStatus: pending ? 'SYNC_PENDING' : status === 'COMPLETED' ? 'SYNCED' : status,
    issueActionId: action.actionId,
    issueActionType: action.actionType,
    issueActionStatus: action.status,
    issueRetryable: ['FAILED', 'EXECUTOR_UNAVAILABLE', 'RETRY_WAITING'].includes(status),
    latestAgentSummary: pick(agentResult, ['summary', 'agentSummary', 'resultSummary']) ?? pick(payload, ['agentSummary', 'summary', 'resultSummary', 'callbackMessage']),
    issueCommentMode: pick(payload, ['issueCommentMode']) ?? 'APPEND',
    issueCommentPreview: truncate(pick(payload, ['issueComment'])),
    agentResultFormatVersion: pick(payload, ['agentResultFormatVersion']) ?? pick(agentResult, ['formatVersion']),
    message: pending
      ? 'Issue Tracking action is waiting for adapter execution.'
      : syncError
        ? `Issue Tracking sync failed: ${syncError}`
        : 'Issue Tracking sync completed. Agent result history is written to the external issue comment stream.'
  };
}

export function mergeIssueTrackingIntoTask(task: CoreTaskRuntimeView, actions: CoreAdapterAction[]): CoreTaskRuntimeView {
  const issues = issueActions(actions);
  if (issues.length === 0) return task;
  const best = issues.find((action) => String(action.status ?? '').toUpperCase() === 'COMPLETED') ?? issues[0];
  const issueTracking = issueTrackingFromAction(best);
  const payload = isRecord(task.payload) ? { ...task.payload } : {};
  return {
    ...task,
    payload: {
      ...payload,
      issueTracking,
      issueActions: issues
    }
  };
}

export function mergeIssueTrackingIntoTasks(tasks: CoreTaskRuntimeView[], actions: CoreAdapterAction[]): CoreTaskRuntimeView[] {
  if (actions.length === 0) return tasks;
  return tasks.map((task) => mergeIssueTrackingIntoTask(task, actions.filter((action) => actionMatchesTask(action, task.taskId))));
}
