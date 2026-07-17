import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import type { CoreTaskRuntimeView } from '@/lib/types/core';

export interface TaskWorkbenchIssueBridge {
  status: 'LINKED' | 'NOT_LINKED' | 'SYNC_PENDING' | 'SYNC_FAILED';
  vendor?: string;
  issueId?: string;
  issueUrl?: string;
  issueStatus?: string;
  lastSyncedAt?: string;
  message: string;
  nextAction?: string;
  actionId?: string;
  actionType?: string;
  syncStatus?: string;
  retryable?: boolean;
  commentMode?: string;
  latestCommentPreview?: string;
  agentHistorySynced?: boolean;
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
    CRITICAL: 'CRITICAL · 立即處理',
    HIGH: 'HIGH · 高優先',
    MIDDLE: 'MIDDLE · 中優先',
    MEDIUM: 'MIDDLE · 中優先',
    LOW: 'LOW · 低優先',
    UNKNOWN: 'UNKNOWN · 未標示'
  };
  return {
    code: code === 'MEDIUM' ? 'MIDDLE' : code,
    label: labels[code] ?? `${code} · 事件重要性`,
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

  if (errorCode === 'TEMP_HIGH') return '溫度超標';
  if (eventType.includes('EQUIPMENT') && eventType.includes('ALARM')) return '設備告警';
  if (objectType === 'EQUIPMENT') return '設備異常';
  if (eventType) return humanizeCode(eventType);
  return humanizeCode(task.taskType ?? 'Agent task');
}

function taskPurpose(task: CoreTaskRuntimeView): string {
  const taskType = normalize(task.taskType);
  const capabilities = (task.requiredCapabilities ?? []).map(normalize);
  if (capabilities.includes('INCIDENT_ANALYSIS')) return '告警分析';
  if (taskType.includes('INCIDENT')) return '事件回應';
  if (capabilities.includes('ISSUE_CREATION')) return 'Issue 建立';
  if (capabilities.includes('TASK_EXECUTION')) return '任務執行';
  return humanizeCode(task.taskType ?? 'Agent task');
}

function sourceSystem(task: CoreTaskRuntimeView): string | undefined {
  return pickFromTask(task, 'sourceSystem', ['sourceSystem', 'source_system', 'systemCode', 'system_code', 'source']);
}

function buildTitle(task: CoreTaskRuntimeView): string {
  const priority = severityDisplay(task).code || 'TASK';
  const system = sourceSystem(task) ?? inferSystem(task);
  const target = task.objectId ?? task.objectType ?? '目標對象';
  return `【${priority}】${system} ${target} ${problemLabel(task)}${taskPurpose(task) ? ` ${taskPurpose(task)}` : ''}`;
}

function inferSystem(task: CoreTaskRuntimeView): string {
  const eventType = normalize(task.eventType);
  const objectType = normalize(task.objectType);
  if (eventType.includes('EQUIPMENT') || objectType === 'EQUIPMENT' || task.plantId) return 'MES';
  if (eventType.includes('ERP')) return 'ERP';
  if (eventType.includes('HR')) return 'HR';
  return 'OpenDispatch';
}

function sourceLabel(task: CoreTaskRuntimeView): string {
  const system = sourceSystem(task) ?? inferSystem(task);
  const site = task.siteId ?? '-';
  const plant = task.plantId;
  return [system, site, plant].filter(Boolean).join(' / ');
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
    return { label: 'Agent 已完成處理', code: 'COMPLETED', health: 'COMPLETED', nextStep: '查看 Agent 摘要或外部 Issue 歷程' };
  }
  if (['CANCELLED', 'CANCELED'].includes(status)) {
    return { label: 'Task 已取消結案', code: 'CANCELLED', health: 'COMPLETED', nextStep: '此 Task 已取消，不需要再派工；如需追蹤請查看 Timeline / Issue。' };
  }
  if (task.blockedReason) {
    return { label: '平台投遞阻塞', code: 'BLOCKED', health: 'BLOCKED', nextStep: task.nextAction ?? '檢查 Dispatch client / Gateway / policy 設定' };
  }
  if (task.failureReason || ['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(status) || execution === 'FAILED') {
    return { label: '處理失敗，需平台診斷', code: 'FAILED', health: 'FAILED', nextStep: task.failureReason ?? '查看進階診斷並視情況重試' };
  }
  if (['RUNNING'].includes(status) || execution === 'RUNNING') {
    return { label: 'Agent 正在處理', code: 'RUNNING', health: 'OK', nextStep: '等待 Agent 回報結果' };
  }
  if (callback === 'CALLBACK_RECEIVED' || execution === 'ACKED') {
    return { label: 'Agent 已回應，等待結果', code: 'ACKED', health: 'OK', nextStep: '等待 Agent 完成分析' };
  }
  if (delivery === 'DELIVERED_TO_GATEWAY' || execution === 'DELIVERED') {
    return { label: '任務已送達 Gateway，等待 Agent 接收', code: 'DELIVERED', health: 'WAITING', nextStep: '等待 Agent ACK / callback' };
  }
  if (task.dispatchRequestId && execution === 'QUEUED') {
    return { label: '任務已排入自動投遞', code: 'QUEUED', health: 'WAITING', nextStep: '等待 Core dispatch worker 投遞到 Gateway' };
  }
  if (task.assignedAgentId) {
    return { label: '已分派 Agent，準備投遞', code: 'ASSIGNED', health: 'WAITING', nextStep: '等待建立或送出 Dispatch Request' };
  }
  return { label: '等待可處理 Agent', code: 'WAITING_AGENT', health: 'WAITING', nextStep: task.dispatchWaitReason ?? task.dispatchRetryReason ?? '檢查 Agent capability、scope、runtime load' };
}

function expectedOutputs(task: CoreTaskRuntimeView): string[] {
  const capabilities = (task.requiredCapabilities ?? []).map(normalize);
  const outputs = new Set<string>();
  if (capabilities.includes('INCIDENT_ANALYSIS') || normalize(task.taskType).includes('INCIDENT')) {
    outputs.add('分析事件上下文與可能原因');
    outputs.add('回報初步處理建議或需升級事項');
  }
  if (capabilities.includes('LOG_DIAGNOSTICS')) outputs.add('彙整相關 log / metric / trace 摘要');
  if (capabilities.includes('ISSUE_CREATION')) outputs.add('建立或更新外部 issue tracking 紀錄');
  if (capabilities.includes('TASK_EXECUTION')) outputs.add('執行已允許的任務步驟並回報結果');
  if (outputs.size === 0) outputs.add('依任務類型回報處理結果');
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
  return '尚未收到 Agent 可對外呈現的分析摘要；完整歷程未來會同步到 Redmine/GitLab/Jira。';
}

function issueBridge(task: CoreTaskRuntimeView): TaskWorkbenchIssueBridge {
  const records = nestedRecords(task);
  let vendor: string | undefined;
  let issueId: string | undefined;
  let issueUrl: string | undefined;
  let issueStatus: string | undefined;
  let lastSyncedAt: string | undefined;
  let syncError: string | undefined;
  let actionId: string | undefined;
  let actionType: string | undefined;
  let syncStatus: string | undefined;
  let retryable = false;

  for (const record of records) {
    vendor ??= pick(record, ['issueVendor', 'vendor', 'provider', 'issueProvider']);
    issueId ??= pick(record, ['issueId', 'issue_id', 'externalIssueId', 'external_issue_id', 'iid', 'key']);
    issueUrl ??= pick(record, ['issueUrl', 'issue_url', 'webUrl', 'web_url', 'url']);
    issueStatus ??= pick(record, ['issueStatus', 'issue_status', 'externalStatus', 'external_status', 'status']);
    lastSyncedAt ??= pick(record, ['lastSyncedAt', 'last_synced_at', 'syncedAt', 'synced_at']);
    syncError ??= pick(record, ['syncError', 'sync_error', 'issueSyncError', 'lastError']);
    actionId ??= pick(record, ['issueActionId', 'actionId']);
    actionType ??= pick(record, ['issueActionType', 'actionType']);
    syncStatus ??= pick(record, ['syncStatus', 'issueActionStatus', 'actionStatus']);
    if (record.issueRetryable === true || record.retryable === true) retryable = true;
  }

  if (issueId || issueUrl || actionId) {
    return {
      status: syncError ? 'SYNC_FAILED' : (syncStatus && syncStatus !== 'SYNCED' && syncStatus !== 'COMPLETED' ? 'SYNC_PENDING' : 'LINKED'),
      vendor,
      issueId,
      issueUrl,
      issueStatus,
      lastSyncedAt,
      actionId,
      actionType,
      syncStatus,
      retryable,
      commentMode: pick(records.find((record) => pick(record, ['issueCommentMode', 'commentMode'])), ['issueCommentMode', 'commentMode']) ?? 'APPEND',
      latestCommentPreview: pick(records.find((record) => pick(record, ['issueCommentPreview'])), ['issueCommentPreview']),
      agentHistorySynced: syncStatus === 'SYNCED' || syncStatus === 'COMPLETED',
      message: syncError ? `Issue 同步失敗：${syncError}` : (syncStatus && syncStatus !== 'SYNCED' && syncStatus !== 'COMPLETED' ? 'Agent Result 已轉成 issue comment，等待 adapter executor 同步。' : 'Agent Result 已寫入外部 issue 歷程；Admin UI 僅保留摘要與同步狀態。'),
      nextAction: syncError ? '檢查 Issue Adapter 設定並重試同步' : (syncStatus && syncStatus !== 'SYNCED' && syncStatus !== 'COMPLETED' ? '等待 Issue Adapter 執行' : 'Open issue')
    };
  }

  return {
    status: 'NOT_LINKED',
    message: '尚未連結 Redmine/GitLab/Jira。Agent Result 完整歷程會在 Issue Adapter 啟用後寫入外部 issue comment。',
    nextAction: '等待 Issue Tracking Adapter 啟用'
  };
}

export function buildTaskWorkbenchDisplay(row: TaskDispatchDashboardRow): TaskWorkbenchDisplay {
  const task = row.task;
  const status = businessStatus(row);
  const capabilities = task.requiredCapabilities?.length ? task.requiredCapabilities.join(', ') : '-';
  const agent = task.assignedAgentId ? task.assignedAgentId : '尚未指派';
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
    triggerReason: task.createdReason ?? task.lifecycleReason ?? '由 Core 規則建立任務',
    assignedAgentLabel: agent,
    requiredCapabilityLabel: capabilities,
    expectedOutputs: expectedOutputs(task),
    latestAgentSummary: latestAgentSummary(row),
    issueBridge: issueBridge(task),
    nextStep: status.nextStep,
    platformHealth: status.health
  };
}
