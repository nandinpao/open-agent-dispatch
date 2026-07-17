import type { RuntimeAttemptSummary, TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import type {
  CoreDispatchTimelineResponse,
  CoreTaskDispatchEvidenceView,
  CoreTaskRuntimeVerificationView,
  CoreTaskRuntimeView,
} from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';

export type DispatchLifecycleStepState = 'done' | 'current' | 'waiting' | 'blocked' | 'failed' | 'skipped';

export interface DispatchLifecycleStep {
  id: string;
  title: string;
  status: DispatchLifecycleStepState;
  badge?: string;
  description?: string;
  timestamp?: string;
  references?: string[];
}

export interface DispatchLifecycleSummary {
  overallStatus: 'NO_TASK' | 'IN_PROGRESS' | 'QUEUED' | 'DELIVERING' | 'WAITING_AGENT' | 'RUNNING' | 'COMPLETED' | 'BLOCKED' | 'FAILED';
  headline: string;
  nextAction?: string;
  blockedReason?: string;
  steps: DispatchLifecycleStep[];
}

export interface OperatorLifecycleStage {
  id: string;
  title: string;
  status: DispatchLifecycleStepState;
  code: string;
  summary: string;
  operatorHint: string;
  references?: string[];
  timestamp?: string;
}

const operatorStageLabels: Record<string, { title: string; waiting: string; current: string; done: string; failed: string; blocked: string }> = {
  event: {
    title: '事件已收到',
    waiting: '系統尚未看到事件或 Incident。',
    current: '事件正在進入 Core。',
    done: 'Core 已收到事件或已建立 Incident。',
    failed: '事件或 Incident 建立失敗。',
    blocked: '事件被規則或治理條件阻擋。'
  },
  task: {
    title: 'Task 已建立',
    waiting: 'Task 尚未建立。',
    current: 'Core 正在建立 Task。',
    done: 'Core 已建立 Task，後續會進入派工判斷。',
    failed: 'Task 建立或狀態轉換失敗。',
    blocked: 'Task 被治理或規則阻擋。'
  },
  assignment: {
    title: '已選擇 Agent',
    waiting: '尚未找到可派工 Agent。',
    current: 'Core 正在檢查 Skill、Governance、Runtime 與容量。',
    done: 'Core 已選出可接任務的 Agent。',
    failed: '找不到符合 Task required capabilities 的 Agent。',
    blocked: 'Agent 指派被治理條件阻擋。'
  },
  'dispatch-request': {
    title: '派工請求已建立',
    waiting: '等待 Agent 指派成功後才會建立派工請求。',
    current: 'Core 正在建立派工請求。',
    done: 'Core 已保存 Dispatch Request。',
    failed: 'Dispatch Request 建立失敗。',
    blocked: 'Dispatch Request 被治理條件阻擋。'
  },
  execution: {
    title: 'Gateway 已接收',
    waiting: '尚未開始送到 Gateway。',
    current: 'Core 正在把任務送到 Netty Gateway。',
    done: 'Netty Gateway 已接受派工。',
    failed: '派工送到 Gateway 前或過程中失敗。',
    blocked: '派工執行被治理條件阻擋。'
  },
  agent: {
    title: 'Agent 回傳結果',
    waiting: '等待 Agent ACK、progress 或 result callback。',
    current: 'Agent 已接收或執行中，Core 正在等待 callback。',
    done: 'Agent 已回傳終態結果。',
    failed: 'Agent 或 Task 已進入失敗狀態。',
    blocked: 'Agent 執行被治理條件阻擋。'
  }
};

function operatorHintFor(step: DispatchLifecycleStep): string {
  const labels = operatorStageLabels[step.id];
  if (!labels) return step.description ?? '查看下方 Timeline Event Log 取得細節。';
  switch (step.status) {
    case 'done': return labels.done;
    case 'current': return labels.current;
    case 'failed': return labels.failed;
    case 'blocked': return labels.blocked;
    default: return labels.waiting;
  }
}

function operatorTitleFor(step: DispatchLifecycleStep): string {
  return operatorStageLabels[step.id]?.title ?? step.title;
}

function operatorNextActionText(value: string | undefined, fallback: string): string {
  const normalized = normalize(value);
  switch (normalized) {
    case 'NONE':
      return '不需要進一步處理，請查看結果摘要與 Issue Sync。';
    case 'WAIT_FOR_AGENT_ACK':
      return '等待 Agent ACK 或 callback；若長時間無回應，請檢查 Agent runtime 與 callback relay。';
    case 'WAIT_FOR_AGENT_RESULT':
      return '等待 Agent RESULT / ERROR callback，或檢查 runtime callback relay。';
    case 'WAIT_FOR_AUTO_DISPATCH_WORKER':
      return '等待 Core dispatch worker 自動投遞；若停留過久，請檢查 dispatch worker 與 Gateway 連線。';
    case 'RETRY':
    case 'RETRY_DELIVERY':
      return '檢查 Timeline Event Log 後執行 Retry delivery。';
    case 'REASSIGN':
      return '改派給其他符合 Skill / Governance / Runtime 條件的 Agent。';
    case 'CANCEL':
    case 'CANCEL_TASK':
      return '確認此 Task 不再需要處理後取消任務。';
    case 'CHECK_AGENT':
    case 'CHECK_AGENT_RUNTIME':
      return '檢查 Agent runtime 是否在線、是否回報 required capabilities。';
    case 'CHECK_GATEWAY':
    case 'CHECK_GATEWAY_RUNTIME':
      return '檢查 Netty Gateway 是否在線，並確認 delivery queue 是否正常。';
    case 'CHECK_CALLBACK_RELAY':
      return '檢查 callback relay 是否收到 Agent RESULT / ERROR callback。';
    default:
      if (!normalized) return fallback;
      return `${fallback}（系統建議代碼：${normalized}）`;
  }
}

export function buildOperatorLifecycleStages(summary: DispatchLifecycleSummary): OperatorLifecycleStage[] {
  return summary.steps.map((step) => ({
    id: step.id,
    title: operatorTitleFor(step),
    status: step.status,
    code: step.badge ?? step.status,
    summary: step.description ?? operatorHintFor(step),
    operatorHint: operatorHintFor(step),
    references: step.references,
    timestamp: step.timestamp
  }));
}

export function lifecycleOperatorDecision(summary: DispatchLifecycleSummary): { title: string; description: string; currentStage: string; nextAction: string; tone: 'success' | 'warning' | 'danger' | 'info' } {
  const current = summary.steps.find((step) => ['current', 'blocked', 'failed'].includes(step.status)) ?? summary.steps[summary.steps.length - 1];
  if (summary.overallStatus === 'COMPLETED') {
    return { title: 'Lifecycle 已完成', description: 'Agent 已回傳結果，請查看結果摘要或 Issue Sync。', currentStage: current?.title ?? 'Completed', nextAction: '查看 Agent 結果與 Issue History。', tone: 'success' };
  }
  if (summary.overallStatus === 'FAILED') {
    return { title: 'Lifecycle 失敗', description: summary.headline, currentStage: current?.title ?? 'Failed', nextAction: operatorNextActionText(summary.nextAction, '查看 Timeline Event Log 後決定 Retry / Reassign / Cancel。'), tone: 'danger' };
  }
  if (summary.overallStatus === 'BLOCKED') {
    return { title: 'Lifecycle 被阻擋', description: summary.blockedReason ?? summary.headline, currentStage: current?.title ?? 'Blocked', nextAction: operatorNextActionText(summary.nextAction, '修正治理、能力或 runtime 條件後再派工。'), tone: 'warning' };
  }
  if (summary.overallStatus === 'WAITING_AGENT') {
    return { title: '等待 Agent 回覆', description: 'Gateway 已接受或 Agent 已 ACK，Core 正在等待 callback。', currentStage: current?.title ?? 'Agent callback', nextAction: operatorNextActionText(summary.nextAction, '等待 Agent RESULT / ERROR callback，或檢查 runtime callback relay。'), tone: 'warning' };
  }
  return { title: summary.headline, description: '派工流程尚未完成，請查看目前所在階段與下一步。', currentStage: current?.title ?? 'Lifecycle', nextAction: operatorNextActionText(summary.nextAction, '等待下一個 dispatch lifecycle event。'), tone: 'info' };
}

const terminalSuccessTaskStatuses = new Set(['COMPLETED', 'SUCCEEDED', 'SUCCESS']);
const terminalFailureTaskStatuses = new Set(['FAILED', 'TIMED_OUT', 'TIMEOUT', 'DEAD_LETTER', 'ORPHANED', 'CANCELLED']);
const gatewayDeliveredStatuses = new Set(['DISPATCHED', 'DELIVERED', 'ACKED', 'RUNNING', 'COMPLETED']);
const agentRunningStatuses = new Set(['ACKED', 'RUNNING']);

function normalize(value: unknown): string {
  return typeof value === 'string' ? value.trim().toUpperCase() : '';
}

function hasValue(value: unknown): boolean {
  return typeof value === 'string' ? Boolean(value.trim()) : value !== undefined && value !== null;
}

function attemptStatus(attempt?: RuntimeAttemptSummary): string {
  return normalize(attempt?.status);
}

function formatTimestamp(value?: string): string | undefined {
  return value ? formatDateTime(value) : undefined;
}

function firstPresent(...values: Array<string | undefined>): string | undefined {
  return values.find((value) => typeof value === 'string' && value.trim());
}

function doneStep(id: string, title: string, options: Partial<DispatchLifecycleStep> = {}): DispatchLifecycleStep {
  return { ...options, id, title, status: 'done' };
}

function waitingStep(id: string, title: string, options: Partial<DispatchLifecycleStep> = {}): DispatchLifecycleStep {
  return { ...options, id, title, status: 'waiting' };
}

function currentStep(id: string, title: string, options: Partial<DispatchLifecycleStep> = {}): DispatchLifecycleStep {
  return { ...options, id, title, status: 'current' };
}

function blockedStep(id: string, title: string, options: Partial<DispatchLifecycleStep> = {}): DispatchLifecycleStep {
  return { ...options, id, title, status: 'blocked' };
}

function failedStep(id: string, title: string, options: Partial<DispatchLifecycleStep> = {}): DispatchLifecycleStep {
  return { ...options, id, title, status: 'failed' };
}

function taskCompleted(task: CoreTaskRuntimeView): boolean {
  return terminalSuccessTaskStatuses.has(normalize(task.status)) || normalize(task.dispatchExecutionStatus) === 'COMPLETED';
}

function taskFailed(task: CoreTaskRuntimeView): boolean {
  return terminalFailureTaskStatuses.has(normalize(task.status)) || normalize(task.dispatchExecutionStatus) === 'FAILED';
}

function gatewayDelivered(task: CoreTaskRuntimeView, delivery?: RuntimeAttemptSummary): boolean {
  const dispatchStatus = normalize(task.dispatchStatus);
  const deliveryStatus = normalize(task.dispatchDeliveryStatus);
  const nettyStatus = attemptStatus(delivery);
  return gatewayDeliveredStatuses.has(dispatchStatus)
    || ['DELIVERED_TO_GATEWAY', 'DELIVERED'].includes(deliveryStatus)
    || ['DELIVERED', 'SUCCESS', 'OK'].includes(nettyStatus);
}

function agentAcknowledged(task: CoreTaskRuntimeView, callbackRelay?: RuntimeAttemptSummary): boolean {
  const dispatchStatus = normalize(task.dispatchStatus);
  const callbackStatus = normalize(task.callbackStatus);
  const relayStatus = attemptStatus(callbackRelay);
  return agentRunningStatuses.has(dispatchStatus)
    || ['CALLBACK_RECEIVED', 'ACKED', 'RUNNING', 'COMPLETED'].includes(callbackStatus)
    || ['RELAYED', 'SUCCESS', 'OK', 'ACKED'].includes(relayStatus);
}

function agentRunning(task: CoreTaskRuntimeView): boolean {
  return ['RUNNING'].includes(normalize(task.status)) || normalize(task.dispatchExecutionStatus) === 'RUNNING';
}

export function buildDispatchLifecycleSummary(row: TaskDispatchDashboardRow): DispatchLifecycleSummary {
  const { task, delivery, callbackRelay } = row;
  const taskStatus = normalize(task.status);
  const executionStatus = normalize(task.dispatchExecutionStatus);
  const deliveryStatus = normalize(task.dispatchDeliveryStatus);
  const dispatchStatus = normalize(task.dispatchStatus);
  const blockedReason = task.blockedReason;
  const failureReason = task.failureReason;
  const waitReason = task.dispatchWaitReason ?? task.dispatchRetryReason;
  const assigned = hasValue(task.assignedAgentId);
  const hasDispatch = hasValue(task.dispatchRequestId);
  const delivered = gatewayDelivered(task, delivery);
  const acked = agentAcknowledged(task, callbackRelay);
  const completed = taskCompleted(task);
  const failed = taskFailed(task);
  const blocked = executionStatus === 'BLOCKED' || Boolean(blockedReason);

  const steps: DispatchLifecycleStep[] = [];

  steps.push(doneStep('event', 'Event / Incident accepted', {
    badge: task.incidentId ? 'INCIDENT_READY' : 'TASK_READY',
    description: task.incidentId ? `Incident ${task.incidentId}` : 'Task exists in Core runtime view',
    timestamp: formatTimestamp(task.createdAt),
    references: [task.incidentId, task.traceId].filter(Boolean) as string[]
  }));

  steps.push(doneStep('task', 'Task created', {
    badge: taskStatus || 'CREATED',
    description: [task.taskType, task.priority].filter(Boolean).join(' · ') || 'Core task authority record created',
    timestamp: formatTimestamp(task.createdAt),
    references: [task.taskId]
  }));

  if (assigned) {
    steps.push(doneStep('assignment', 'Agent assigned', {
      badge: 'ASSIGNED',
      description: task.lifecycleReason || `Selected ${task.assignedAgentId}`,
      timestamp: formatTimestamp(task.updatedAt),
      references: [task.assignedAgentId].filter(Boolean) as string[]
    }));
  } else if (failed || blocked) {
    steps.push(failedStep('assignment', 'Agent assignment unavailable', {
      badge: taskStatus || 'NO_ASSIGNMENT',
      description: failureReason || task.lifecycleReason || 'Core has not selected an assignable agent',
      references: task.requiredCapabilities?.length ? [`requires ${task.requiredCapabilities.join(', ')}`] : undefined
    }));
  } else {
    steps.push(currentStep('assignment', 'Waiting for assignable agent', {
      badge: taskStatus || 'WAITING',
      description: waitReason || task.lifecycleReason || 'Core routing has not produced an assignment yet',
      references: task.requiredCapabilities?.length ? [`requires ${task.requiredCapabilities.join(', ')}`] : undefined
    }));
  }

  if (hasDispatch) {
    steps.push(doneStep('dispatch-request', 'Dispatch request created', {
      badge: dispatchStatus || 'REQUESTED',
      description: 'Core persisted the dispatch request contract',
      references: [task.dispatchRequestId].filter(Boolean) as string[]
    }));
  } else if (assigned) {
    steps.push(currentStep('dispatch-request', 'Creating dispatch request', {
      badge: 'PENDING',
      description: task.nextAction || 'Waiting for Core dispatch request persistence'
    }));
  } else {
    steps.push(waitingStep('dispatch-request', 'Dispatch request pending', {
      badge: 'IN_PROGRESS',
      description: 'Dispatch request will be created after assignment succeeds'
    }));
  }

  if (blocked) {
    steps.push(blockedStep('execution', 'Execution blocked', {
      badge: blockedReason ?? 'BLOCKED',
      description: task.nextAction ? `${blockedReason ?? 'Blocked'} · ${task.nextAction}` : blockedReason ?? 'Dispatch execution is blocked',
      references: [task.dispatchRequestId].filter(Boolean) as string[]
    }));
  } else if (failed && hasDispatch && !delivered) {
    steps.push(failedStep('execution', 'Dispatch execution failed', {
      badge: executionStatus || dispatchStatus || 'FAILED',
      description: failureReason || 'Core dispatch execution failed before gateway delivery'
    }));
  } else if (delivered) {
    steps.push(doneStep('execution', 'Delivered to gateway', {
      badge: deliveryStatus || dispatchStatus || 'DELIVERED',
      description: firstPresent(delivery?.reason, task.nextAction, 'Gateway delivery observed'),
      timestamp: formatTimestamp(delivery?.occurredAt),
      references: [delivery?.gatewayNodeId].filter(Boolean) as string[]
    }));
  } else if (hasDispatch && executionStatus === 'QUEUED') {
    steps.push(currentStep('execution', 'Queued for automatic delivery', {
      badge: 'QUEUED',
      description: task.nextAction || 'Waiting for Core dispatch worker to deliver the command'
    }));
  } else if (hasDispatch && executionStatus === 'EXECUTING') {
    steps.push(currentStep('execution', 'Delivering to gateway', {
      badge: 'DELIVERING',
      description: task.nextAction || 'Core dispatch worker is delivering the command'
    }));
  } else if (hasDispatch) {
    steps.push(currentStep('execution', 'Waiting for delivery', {
      badge: executionStatus || dispatchStatus || 'WAITING',
      description: task.nextAction || 'Dispatch request exists but delivery is not completed yet'
    }));
  } else {
    steps.push(waitingStep('execution', 'Delivery pending', {
      badge: 'NOT_STARTED',
      description: 'Gateway delivery starts after dispatch request creation'
    }));
  }

  if (completed) {
    steps.push(doneStep('agent', 'Agent completed', {
      badge: taskStatus || 'COMPLETED',
      description: task.lifecycleReason || callbackRelay?.reason || 'Core observed terminal success',
      timestamp: formatTimestamp(task.updatedAt ?? callbackRelay?.occurredAt),
      references: [callbackRelay?.agentId ?? task.assignedAgentId].filter(Boolean) as string[]
    }));
  } else if (failed) {
    steps.push(failedStep('agent', 'Agent / task failed', {
      badge: taskStatus || executionStatus || 'FAILED',
      description: failureReason || task.lifecycleReason || 'Core observed a terminal failure',
      timestamp: formatTimestamp(task.updatedAt ?? callbackRelay?.occurredAt),
      references: [callbackRelay?.agentId ?? task.assignedAgentId].filter(Boolean) as string[]
    }));
  } else if (agentRunning(task)) {
    steps.push(currentStep('agent', 'Agent running', {
      badge: 'RUNNING',
      description: task.nextAction || callbackRelay?.reason || 'Waiting for agent result callback',
      timestamp: formatTimestamp(callbackRelay?.occurredAt),
      references: [callbackRelay?.agentId ?? task.assignedAgentId].filter(Boolean) as string[]
    }));
  } else if (acked) {
    steps.push(currentStep('agent', 'Agent acknowledged', {
      badge: normalize(task.callbackStatus) || dispatchStatus || 'ACKED',
      description: task.nextAction || callbackRelay?.reason || 'Waiting for agent progress or result',
      timestamp: formatTimestamp(callbackRelay?.occurredAt),
      references: [callbackRelay?.agentId ?? task.assignedAgentId].filter(Boolean) as string[]
    }));
  } else if (delivered) {
    steps.push(currentStep('agent', 'Waiting for agent ACK', {
      badge: 'WAITING_AGENT',
      description: task.nextAction || 'Gateway delivery succeeded; Core is waiting for agent callback',
      references: [task.assignedAgentId].filter(Boolean) as string[]
    }));
  } else {
    steps.push(waitingStep('agent', 'Agent execution pending', {
      badge: 'NOT_STARTED',
      description: 'Agent execution starts after gateway delivery succeeds'
    }));
  }

  if (blocked) {
    return {
      overallStatus: 'BLOCKED',
      headline: `Dispatch blocked: ${blockedReason ?? executionStatus}`,
      nextAction: task.nextAction,
      blockedReason,
      steps
    };
  }
  if (failed) {
    return {
      overallStatus: 'FAILED',
      headline: failureReason ? `Dispatch failed: ${failureReason}` : 'Dispatch or task failed',
      nextAction: task.nextAction,
      steps
    };
  }
  if (completed) {
    return {
      overallStatus: 'COMPLETED',
      headline: 'Task completed by agent',
      nextAction: 'NONE',
      steps
    };
  }
  if (agentRunning(task)) {
    return {
      overallStatus: 'RUNNING',
      headline: `Agent ${task.assignedAgentId ?? ''} is running the task`.trim(),
      nextAction: task.nextAction ?? 'WAIT_FOR_AGENT_RESULT',
      steps
    };
  }
  if (acked) {
    return {
      overallStatus: 'WAITING_AGENT',
      headline: `Agent ${task.assignedAgentId ?? ''} acknowledged the task`.trim(),
      nextAction: task.nextAction ?? 'WAIT_FOR_AGENT_RESULT',
      steps
    };
  }
  if (delivered) {
    return {
      overallStatus: 'WAITING_AGENT',
      headline: 'Delivered to gateway; waiting for agent ACK',
      nextAction: task.nextAction ?? 'WAIT_FOR_AGENT_ACK',
      steps
    };
  }
  if (hasDispatch) {
    return {
      overallStatus: executionStatus === 'EXECUTING' ? 'DELIVERING' : 'QUEUED',
      headline: executionStatus === 'EXECUTING' ? 'Dispatch worker is delivering the task' : 'Dispatch queued for automatic delivery',
      nextAction: task.nextAction ?? 'WAIT_FOR_AUTO_DISPATCH_WORKER',
      steps
    };
  }
  if (assigned) {
    return {
      overallStatus: 'IN_PROGRESS',
      headline: 'Agent assigned; waiting for dispatch request',
      nextAction: task.nextAction,
      steps
    };
  }
  return {
    overallStatus: 'IN_PROGRESS',
    headline: 'Waiting for routing and assignment',
    nextAction: task.nextAction,
    steps
  };
}

export function lifecycleStateClass(status: DispatchLifecycleStepState): string {
  switch (status) {
    case 'done':
      return 'border-emerald-200 bg-emerald-50 text-emerald-800';
    case 'current':
      return 'border-blue-200 bg-blue-50 text-blue-800';
    case 'blocked':
      return 'border-amber-200 bg-amber-50 text-amber-900';
    case 'failed':
      return 'border-rose-200 bg-rose-50 text-rose-800';
    case 'skipped':
      return 'border-slate-200 bg-slate-50 text-slate-400';
    default:
      return 'border-slate-200 bg-slate-50 text-slate-500';
  }
}

export function lifecycleStateIcon(status: DispatchLifecycleStepState): string {
  switch (status) {
    case 'done': return '✓';
    case 'current': return '●';
    case 'blocked': return '⏸';
    case 'failed': return '!';
    case 'skipped': return '–';
    default: return '○';
  }
}


export type TaskDispatchDiagnosisCode =
  | 'COMPLETED'
  | 'IN_PROGRESS'
  | 'NO_MATCHING_FLOW'
  | 'NO_MATCHING_RULE'
  | 'NO_FLOW_AGENT'
  | 'MISSING_REQUIRED_CAPABILITY'
  | 'AGENT_OFFLINE'
  | 'AGENT_CAPACITY_FULL'
  | 'DISPATCH_DELIVERY_FAILED'
  | 'RESULT_TIMEOUT';

export interface TaskDispatchDiagnosis {
  code: TaskDispatchDiagnosisCode;
  title: string;
  reason: string;
  nextAction: string;
  tone: 'success' | 'info' | 'warning' | 'danger';
  flowId?: string;
  ruleId?: string;
  agentId?: string;
  traceId?: string;
  missingCapabilities: string[];
  actionHref?: string;
  actionLabel?: string;
}

export interface StandardDispatchTimelineStep {
  id: 'event' | 'task' | 'flow' | 'agent' | 'assignment' | 'delivery' | 'ack' | 'result';
  title: string;
  state: DispatchLifecycleStepState;
  detail: string;
  timestamp?: string;
}

const noFlowCodes = new Set([
  'NO_MATCHING_FLOW',
  'MISSING_FLOW_RULE',
  'DISPATCH_RULE_MISSING',
  'FLOW_RULE_REQUIRED_BLOCKED',
  'NO_MATCHING_FLOW_RULE',
  'NO_MATCHING_DISPATCH_FLOW',
  'DISPATCH_PROFILE_NOT_CONFIGURED',
  'NO_ACTIVE_TASK_DEFINITION_PROFILE_MATCH',
  'NO_ASSIGNMENT_PROFILE_MATCH',
  'NO_SOURCE_SYSTEM_PROFILE_MATCH',
]);

const capabilityCodes = new Set([
  'MISSING_REQUIRED_CAPABILITY',
  'P3H_MISSING_REQUIRED_CAPABILITY',
  'AGENT_SKILL_GRANT_MISSING',
  'RUNTIME_CAPABILITY_MISSING',
  'CAPABILITY_NOT_APPROVED',
  'MISSING_REQUIRED_CAPABILITY',
  'REQUIRED_SKILL_MISSING',
]);

const offlineCodes = new Set([
  'AGENT_OFFLINE',
  'RUNTIME_NOT_CONNECTED',
  'NO_ACTIVE_RUNTIME_SESSION',
  'DISPATCH_AGENT_NOT_ASSIGNABLE',
  'AGENT_RUNTIME_UNAVAILABLE',
  'AGENT_DISCONNECTED',
]);

const capacityCodes = new Set([
  'AGENT_CAPACITY_FULL',
  'DISPATCH_AGENT_CAPACITY_FULL',
  'CAPACITY_EXHAUSTED',
  'AGENT_BUSY',
]);

const noAgentCodes = new Set([
  'NO_FLOW_AGENT',
  'NO_CANDIDATE_AGENT',
  'NO_CANDIDATE',
  'DISPATCH_DELAYED_NO_FLOW_AGENT',
  'CANDIDATE_POOL_EMPTY',
]);

const deliveryCodes = new Set([
  'DISPATCH_DELIVERY_FAILED',
  'GATEWAY_DISPATCH_DELIVERY_FAILED',
  'DISPATCH_DELIVERY_FAILED',
  'DELIVERY_REJECTED',
  'DISPATCH_REQUEST_FAILED',
]);

const callbackCodes = new Set([
  'RESULT_TIMEOUT',
  'CALLBACK_TIMEOUT',
  'AGENT_RESULT_TIMEOUT',
  'RESULT_RESULT_TIMEOUT',
]);

function collectDiagnosisTokens(
  task: CoreTaskRuntimeView,
  evidence?: CoreTaskDispatchEvidenceView,
  verification?: CoreTaskRuntimeVerificationView,
): string[] {
  const raw = [
    evidence?.firstBlockingCode,
    verification?.firstBlockingCode,
    task.userFacingDispatchError?.code,
    task.latestRoutingDecision?.decisionReason,
    task.reasonCategory,
    task.routingPath,
    task.blockedReason,
    task.failureReason,
    task.dispatchWaitReason,
    task.dispatchRetryReason,
    task.lifecycleReason,
    task.createdReason,
    evidence?.firstBlockingReason,
    verification?.firstBlockingReason,
  ];
  return raw
    .flatMap((value) => String(value ?? '').toUpperCase().split(/[^A-Z0-9_]+/))
    .filter(Boolean);
}

function containsCode(tokens: string[], codes: Set<string>): boolean {
  return tokens.some((token) => codes.has(token));
}

function flowCreateHref(task: CoreTaskRuntimeView): string {
  const query = new URLSearchParams({ create: '1' });
  if (task.sourceSystem) query.set('sourceSystem', task.sourceSystem);
  if (task.objectType) query.set('objectType', task.objectType);
  if (task.eventType) query.set('eventType', task.eventType);
  if (task.errorCode) query.set('errorCode', task.errorCode);
  return `/dispatch-flows?${query.toString()}`;
}

export function deriveTaskDispatchDiagnosis(input: {
  task: CoreTaskRuntimeView;
  evidence?: CoreTaskDispatchEvidenceView;
  runtimeVerification?: CoreTaskRuntimeVerificationView;
}): TaskDispatchDiagnosis {
  const { task, evidence, runtimeVerification } = input;
  const tokens = collectDiagnosisTokens(task, evidence, runtimeVerification);
  const status = normalize(task.status);
  const execution = normalize(task.dispatchExecutionStatus);
  const missingCapabilities = Array.from(new Set([...(task.requiredCapabilities ?? [])].filter(Boolean)));
  const common = {
    flowId: task.matchedFlowId,
    ruleId: task.matchedRuleId,
    agentId: task.assignedAgentId ?? runtimeVerification?.selectedAgentId,
    traceId: task.traceId,
    missingCapabilities,
  };

  if (taskCompleted(task)) {
    return { ...common, code: 'COMPLETED', title: 'Task 已完成', reason: 'Agent 已回傳正式結果，Task lifecycle 已完成。', nextAction: '查看結果與完整派工時間線。', tone: 'success' };
  }
  if (containsCode(tokens, noFlowCodes) || (!task.matchedFlowId && normalize(task.routingPath).includes('FLOW_RULE_REQUIRED'))) {
    return {
      ...common,
      code: 'NO_MATCHING_FLOW',
      title: '沒有符合的派工流程',
      reason: '此事件沒有命中已啟用的 Dispatch Flow Rule，因此尚未進入 Agent 候選判斷。',
      nextAction: '建立或啟用符合事件條件的派工流程，並在流程中選擇至少一個已核准 Agent。',
      tone: 'warning',
      actionHref: flowCreateHref(task),
      actionLabel: '建立派工流程',
    };
  }
  if (containsCode(tokens, capabilityCodes)) {
    return {
      ...common,
      code: 'MISSING_REQUIRED_CAPABILITY',
      title: 'Agent 缺少必要特殊能力',
      reason: missingCapabilities.length
        ? `此流程要求 ${missingCapabilities.join('、')}，目前沒有符合的已核准 Agent。`
        : '目前沒有 Agent 具備此 Flow Rule 明確要求的特殊能力。',
      nextAction: '在派工流程中確認必要 Capability，或核准具備該 Capability 的 Agent。',
      tone: 'warning',
      actionHref: task.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : '/dispatch-flows',
      actionLabel: '查看派工流程',
    };
  }
  if (containsCode(tokens, offlineCodes)) {
    return {
      ...common,
      code: 'AGENT_OFFLINE',
      title: '處理 Agent 目前離線',
      reason: common.agentId ? `Agent ${common.agentId} 沒有可用 Runtime Session。` : '流程中的 Agent 目前沒有可用 Runtime Session。',
      nextAction: '檢查 Agent 連線、Heartbeat 與 Credential；恢復連線後再重新派工。',
      tone: 'warning',
      actionHref: common.agentId ? `/agents/${encodeURIComponent(common.agentId)}` : '/agents',
      actionLabel: '查看 Agent',
    };
  }
  if (containsCode(tokens, capacityCodes)) {
    return {
      ...common,
      code: 'AGENT_CAPACITY_FULL',
      title: 'Agent 暫無可用容量',
      reason: '符合條件的 Agent 目前忙碌或沒有可用工作槽位。',
      nextAction: '等待 Agent 釋放容量，或在派工流程中增加其他處理 Agent。',
      tone: 'warning',
      actionHref: task.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : '/dispatch-flows',
      actionLabel: '調整處理 Agent',
    };
  }
  if (containsCode(tokens, noAgentCodes)) {
    return {
      ...common,
      code: 'NO_FLOW_AGENT',
      title: '派工流程沒有可用 Agent',
      reason: 'Flow Rule 已命中，但目前沒有同時符合核准、Capability、Runtime 與容量條件的 Agent。',
      nextAction: '查看 Flow 選擇的 Agent，依阻擋原因修正後再重新派工。',
      tone: 'warning',
      actionHref: task.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : '/dispatch-flows',
      actionLabel: '查看派工流程',
    };
  }
  if (containsCode(tokens, deliveryCodes) || execution === 'DISPATCH_DELIVERY_FAILED') {
    return {
      ...common,
      code: 'DISPATCH_DELIVERY_FAILED',
      title: '派工送達失敗',
      reason: 'Core 已建立派工資料，但 Netty Gateway 或 Agent transport 沒有成功接收。',
      nextAction: '確認 Gateway 與 Agent Runtime 後執行重新派工。',
      tone: 'danger',
      actionHref: common.agentId ? `/agents/${encodeURIComponent(common.agentId)}` : '/agents',
      actionLabel: '檢查 Agent',
    };
  }
  if (containsCode(tokens, callbackCodes)) {
    return {
      ...common,
      code: 'RESULT_TIMEOUT',
      title: 'Agent Result 逾時',
      reason: 'Task 已送達或已 ACK，但正式 RESULT／ERROR callback 尚未成功回到 Core。',
      nextAction: '檢查 Agent 執行狀態與 callback relay。',
      tone: 'danger',
      actionHref: common.agentId ? `/agents/${encodeURIComponent(common.agentId)}` : '/agents',
      actionLabel: '查看 Agent',
    };
  }
  if (taskFailed(task)) {
    return { ...common, code: 'DISPATCH_DELIVERY_FAILED', title: 'Task 執行失敗', reason: task.failureReason ?? task.blockedReason ?? 'Task 已進入失敗終態。', nextAction: '查看標準時間線與 Trace，確認後再 Retry、Reassign 或取消。', tone: 'danger' };
  }
  if (task.assignedAgentId || task.dispatchRequestId || ['RUNNING', 'ACKED', 'DISPATCHED'].includes(normalize(task.dispatchStatus))) {
    return { ...common, code: 'IN_PROGRESS', title: 'Task 派工執行中', reason: '已命中流程並進入 Agent 派送或執行階段。', nextAction: '等待 Agent ACK／Result，並查看下方標準時間線。', tone: 'info' };
  }
  return { ...common, code: 'IN_PROGRESS', title: '等待派工', reason: status === 'OPEN' ? 'Task 已建立，正在進行 Flow Rule 與 Agent eligibility 判斷。' : 'Task 尚未完成 Agent 指派。', nextAction: '查看標準時間線，確認目前停留階段。', tone: 'info' };
}

function timelineHas(timeline: CoreDispatchTimelineResponse | undefined, ...needles: string[]): boolean {
  return Boolean(timeline?.events?.some((event) => {
    const text = `${event.stage} ${event.action} ${event.status} ${event.message}`.toUpperCase();
    return needles.some((needle) => text.includes(needle));
  }));
}

function timelineTime(timeline: CoreDispatchTimelineResponse | undefined, ...needles: string[]): string | undefined {
  return timeline?.events?.find((event) => {
    const text = `${event.stage} ${event.action} ${event.status} ${event.message}`.toUpperCase();
    return needles.some((needle) => text.includes(needle));
  })?.occurredAt;
}

export function buildStandardDispatchTimeline(
  task: CoreTaskRuntimeView,
  timeline: CoreDispatchTimelineResponse | undefined,
  diagnosis: TaskDispatchDiagnosis,
): StandardDispatchTimelineStep[] {
  const flowMatched = Boolean(task.matchedFlowId && task.matchedRuleId);
  const agentSelected = Boolean(task.assignedAgentId);
  const assignmentCreated = agentSelected || Boolean(task.dispatchRequestId) || timelineHas(timeline, 'ASSIGNMENT_CREATED', 'AGENT_ASSIGNED');
  const delivered = gatewayDelivered(task) || timelineHas(timeline, 'DELIVERED', 'GATEWAY_ACCEPTED');
  const acked = agentAcknowledged(task) || timelineHas(timeline, 'ACK', 'AGENT_ACCEPTED');
  const completed = taskCompleted(task);
  const blockedAt = diagnosis.code === 'NO_MATCHING_FLOW' ? 'flow'
    : ['MISSING_REQUIRED_CAPABILITY', 'AGENT_OFFLINE', 'AGENT_CAPACITY_FULL', 'NO_FLOW_AGENT'].includes(diagnosis.code) ? 'agent'
      : diagnosis.code === 'DISPATCH_DELIVERY_FAILED' ? 'delivery'
        : diagnosis.code === 'RESULT_TIMEOUT' ? 'result'
          : undefined;
  const state = (id: StandardDispatchTimelineStep['id'], done: boolean, current = false): DispatchLifecycleStepState => {
    if (done) return 'done';
    if (blockedAt === id) return diagnosis.tone === 'danger' ? 'failed' : 'blocked';
    if (current) return 'current';
    return 'waiting';
  };
  return [
    { id: 'event', title: 'Event 已接收', state: state('event', Boolean(task.sourceEventId || task.incidentId || task.createdAt)), detail: task.sourceEventId ? `Event ${task.sourceEventId}` : 'Core 已接受事件。', timestamp: task.createdAt },
    { id: 'task', title: 'Task 已建立', state: state('task', true), detail: `Task ${task.taskId}`, timestamp: task.createdAt },
    { id: 'flow', title: '命中派工流程', state: state('flow', flowMatched, !flowMatched && !blockedAt), detail: flowMatched ? `Flow ${task.matchedFlowId} / Rule ${task.matchedRuleId}` : diagnosis.code === 'NO_MATCHING_FLOW' ? diagnosis.reason : '正在比對 Dispatch Flow Rule。', timestamp: timelineTime(timeline, 'FLOW_MATCHED', 'RULE_MATCHED') },
    { id: 'agent', title: '選出符合 Agent', state: state('agent', agentSelected, flowMatched && !agentSelected && !blockedAt), detail: agentSelected ? `Agent ${task.assignedAgentId}` : blockedAt === 'agent' ? diagnosis.reason : '正在檢查核准、Capability、Runtime 與容量。', timestamp: timelineTime(timeline, 'AGENT_ELIGIBLE', 'AGENT_SELECTED') },
    { id: 'assignment', title: '建立 Assignment', state: state('assignment', assignmentCreated, agentSelected && !assignmentCreated), detail: task.dispatchRequestId ? `Dispatch Request ${task.dispatchRequestId}` : assignmentCreated ? 'Core 已保存派工關聯。' : '等待 Agent selection。', timestamp: timelineTime(timeline, 'ASSIGNMENT_CREATED', 'DISPATCH_REQUEST_CREATED') },
    { id: 'delivery', title: 'Netty 已送達', state: state('delivery', delivered, assignmentCreated && !delivered && blockedAt !== 'delivery'), detail: delivered ? 'Gateway／Agent transport 已接受派工。' : blockedAt === 'delivery' ? diagnosis.reason : '等待 Gateway delivery。', timestamp: timelineTime(timeline, 'DELIVERED', 'GATEWAY_ACCEPTED') },
    { id: 'ack', title: 'Agent ACK', state: state('ack', acked, delivered && !acked), detail: acked ? 'Agent 已確認接收並開始處理。' : '等待 Agent ACK。', timestamp: timelineTime(timeline, 'ACK', 'AGENT_ACCEPTED') },
    { id: 'result', title: 'Agent Result', state: state('result', completed, acked && !completed && blockedAt !== 'result'), detail: completed ? '正式 Result 已回到 Core，Task 完成。' : blockedAt === 'result' ? diagnosis.reason : '等待 RESULT／ERROR callback。', timestamp: task.updatedAt ?? timelineTime(timeline, 'RESULT', 'COMPLETED') },
  ];
}
