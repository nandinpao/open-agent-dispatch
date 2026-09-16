import type { RuntimeAttemptSummary, TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import type {
  CoreDispatchTimelineResponse,
  CoreTaskDispatchEvidenceView,
  CoreTaskRuntimeVerificationView,
  CoreTaskRuntimeView,
} from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';
import { taskDiagnosisCatalogEntry, type TaskDiagnosisCatalogCode } from '@/lib/tasks/taskDiagnosisCatalog';

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
  overallStatus: 'NO_TASK' | 'IN_PROGRESS' | 'QUEUED' | 'WAITING_RETRY' | 'DELIVERING' | 'WAITING_AGENT' | 'RUNNING' | 'COMPLETED' | 'BLOCKED' | 'FAILED';
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
    title: 'Event intake',
    waiting: 'Waiting for Core to persist the event or incident context.',
    current: 'Core is accepting and normalizing the event context.',
    done: 'Core accepted the event or incident context.',
    failed: 'Event or incident intake failed.',
    blocked: 'Event intake is blocked by validation or authorization.'
  },
  task: {
    title: 'Task authority',
    waiting: 'Waiting for the authoritative Task record.',
    current: 'Core is creating or updating the Task authority record.',
    done: 'Core created the authoritative Task record.',
    failed: 'Task creation or lifecycle transition failed.',
    blocked: 'Task lifecycle is blocked.'
  },
  assignment: {
    title: 'Agent assignment',
    waiting: 'Waiting for Core routing and eligibility to select an assignable Agent.',
    current: 'Core is evaluating pool membership, eligibility, runtime state, and capacity.',
    done: 'Core recorded an Agent assignment for this Task.',
    failed: 'Core could not create a valid Agent assignment.',
    blocked: 'Agent assignment is blocked by routing, eligibility, or operator policy.'
  },
  'dispatch-request': {
    title: 'Dispatch request',
    waiting: 'Waiting for Core to create the dispatch request after assignment.',
    current: 'Core is creating the dispatch request and delivery intent.',
    done: 'Core created the dispatch request.',
    failed: 'Dispatch request creation failed.',
    blocked: 'Dispatch request creation is blocked.'
  },
  execution: {
    title: 'Delivery',
    waiting: 'Waiting for Gateway delivery evidence.',
    current: 'Core handed delivery intent to the Netty Gateway path.',
    done: 'Gateway delivery evidence was recorded.',
    failed: 'Gateway or Agent transport delivery failed.',
    blocked: 'Delivery is blocked by Gateway or Agent runtime conditions.'
  },
  agent: {
    title: 'Agent result',
    waiting: 'Waiting for Agent ACK, progress, RESULT, or ERROR callback evidence.',
    current: 'Core is waiting for the next authoritative Agent callback.',
    done: 'Core received terminal Agent result evidence.',
    failed: 'Agent execution or Task result processing failed.',
    blocked: 'Agent execution is blocked.'
  }
};

function operatorHintFor(step: DispatchLifecycleStep): string {
  const labels = operatorStageLabels[step.id];
  if (!labels) return step.description ?? 'View details Timeline Event Log ';
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
      return 'No mutation is recommended. Review result and audit evidence.';
    case 'WAIT_FOR_AGENT_ACK':
      return 'Wait for Agent ACK and inspect Agent runtime or callback relay evidence if the wait exceeds policy.';
    case 'WAIT_FOR_AGENT_RESULT':
      return 'Wait for the Agent RESULT or ERROR callback, or inspect the runtime callback relay.';
    case 'WAIT_FOR_AUTO_DISPATCH_WORKER':
      return 'Wait for the Core dispatch worker and inspect worker or Gateway health if the Task remains queued.';
    case 'RETRY':
    case 'RETRY_DELIVERY':
      return 'Use the governed Retry Delivery command and then review the timeline for a new delivery attempt.';
    case 'REASSIGN':
      return 'Review Agent eligibility and use the governed Reassign Agent command if an alternate Agent is appropriate.';
    case 'CANCEL':
    case 'CANCEL_TASK':
      return 'Cancel only when the Task no longer requires execution; Core will preserve audit evidence.';
    case 'CHECK_AGENT':
    case 'CHECK_AGENT_RUNTIME':
      return 'Inspect Agent runtime, credentials, required capabilities, capacity, and heartbeat evidence.';
    case 'CHECK_GATEWAY':
    case 'CHECK_GATEWAY_RUNTIME':
      return 'Inspect Netty Gateway health, delivery queue, and connection evidence.';
    case 'CHECK_CALLBACK_RELAY':
      return 'Inspect callback relay evidence for the Agent RESULT or ERROR callback.';
    default:
      return normalized ? `${fallback} (${normalized})` : fallback;
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
    return { title: 'Lifecycle completed', description: 'Core recorded terminal result evidence for this Task.', currentStage: current?.title ?? 'Completed', nextAction: 'Review the Agent result, issue history, and audit evidence.', tone: 'success' };
  }
  if (summary.overallStatus === 'FAILED') {
    return { title: 'Lifecycle failed', description: summary.headline, currentStage: current?.title ?? 'Failed', nextAction: operatorNextActionText(summary.nextAction, 'Review the timeline and choose a governed remediation command from Agent Assignment.'), tone: 'danger' };
  }
  if (summary.overallStatus === 'BLOCKED') {
    return { title: 'Lifecycle blocked', description: summary.blockedReason ?? summary.headline, currentStage: current?.title ?? 'Blocked', nextAction: operatorNextActionText(summary.nextAction, 'Review routing, Agent Pool, and runtime evidence before choosing a remediation command.'), tone: 'warning' };
  }
  if (summary.overallStatus === 'WAITING_RETRY') {
    return { title: 'Waiting for automatic retry', description: summary.headline, currentStage: current?.title ?? 'Retry queue', nextAction: operatorNextActionText(summary.nextAction, 'Wait for the scheduled retry, or inspect Agent availability and routing evidence.'), tone: 'warning' };
  }
  if (summary.overallStatus === 'WAITING_AGENT') {
    return { title: 'Waiting for Agent callback', description: 'Delivery reached the Agent path and Core is waiting for authoritative callback evidence.', currentStage: current?.title ?? 'Agent callback', nextAction: operatorNextActionText(summary.nextAction, 'Wait for the Agent RESULT or ERROR callback, or inspect the runtime callback relay.'), tone: 'warning' };
  }
  return { title: summary.headline, description: 'The Task lifecycle is still in progress and no terminal blocker has been recorded.', currentStage: current?.title ?? 'Lifecycle', nextAction: operatorNextActionText(summary.nextAction, 'Wait for the next authoritative lifecycle transition and review evidence if progress stalls.'), tone: 'info' };
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
  const taskStatus = normalize(task.status);
  if (terminalSuccessTaskStatuses.has(taskStatus)) return true;
  if (taskStatus === 'RETRY_WAIT' || task.nextDispatchAttemptAt || task.dispatchWaitReason || task.dispatchRetryReason) return false;
  return normalize(task.dispatchExecutionStatus) === 'COMPLETED';
}

function taskFailed(task: CoreTaskRuntimeView): boolean {
  const taskStatus = normalize(task.status);
  if (terminalFailureTaskStatuses.has(taskStatus)) return true;
  if (taskStatus === 'RETRY_WAIT' || task.nextDispatchAttemptAt || task.dispatchWaitReason || task.dispatchRetryReason) return false;
  return normalize(task.dispatchExecutionStatus) === 'FAILED';
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
  const waitingRetry = taskStatus === 'RETRY_WAIT' || Boolean(task.nextDispatchAttemptAt) || Boolean(waitReason);
  const blocked = !waitingRetry && (executionStatus === 'BLOCKED' || Boolean(blockedReason));

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
  if (waitingRetry) {
    return {
      overallStatus: 'WAITING_RETRY',
      headline: task.nextDispatchAttemptAt ? `Waiting for automatic retry at ${formatDateTime(task.nextDispatchAttemptAt)}` : 'Waiting for automatic retry',
      nextAction: task.nextAction ?? 'WAIT_FOR_RETRY_OR_TRIGGER_RECOVERY',
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


export type TaskDispatchDiagnosisCode = TaskDiagnosisCatalogCode;

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

const noRuleCodes = new Set([
  'MISSING_FLOW_RULE',
  'DISPATCH_RULE_MISSING',
  'FLOW_RULE_REQUIRED_BLOCKED',
  'NO_MATCHING_FLOW_RULE',
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

const manualAssignmentCodes = new Set([
  'MANUAL_ASSIGNMENT_REQUIRED',
  'WAITING_MANUAL_ASSIGNMENT',
  'OPERATOR_REVIEW_REQUIRED',
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
  const execution = normalize(task.dispatchExecutionStatus);
  const missingCapabilities = Array.from(new Set([...(task.requiredCapabilities ?? [])].filter(Boolean)));
  const common = {
    flowId: task.matchedFlowId,
    ruleId: task.matchedRuleId,
    agentId: task.assignedAgentId ?? runtimeVerification?.selectedAgentId,
    traceId: task.traceId,
    missingCapabilities,
  };

  let code: TaskDispatchDiagnosisCode = 'IN_PROGRESS';
  if (taskCompleted(task)) code = 'COMPLETED';
  else if (task.matchedFlowId && containsCode(tokens, noRuleCodes)) code = 'NO_MATCHING_RULE';
  else if (containsCode(tokens, noFlowCodes) || (!task.matchedFlowId && normalize(task.routingPath).includes('FLOW_RULE_REQUIRED'))) code = 'NO_MATCHING_FLOW';
  else if (containsCode(tokens, offlineCodes)) code = 'AGENT_OFFLINE';
  else if (containsCode(tokens, capacityCodes)) code = 'AGENT_CAPACITY_FULL';
  else if (containsCode(tokens, manualAssignmentCodes)) code = 'MANUAL_ASSIGNMENT_REQUIRED';
  else if (containsCode(tokens, noAgentCodes)) code = 'NO_FLOW_AGENT';
  else if (containsCode(tokens, deliveryCodes) || execution === 'DISPATCH_DELIVERY_FAILED' || taskFailed(task)) code = 'DISPATCH_DELIVERY_FAILED';
  else if (containsCode(tokens, callbackCodes)) code = 'RESULT_TIMEOUT';

  const catalog = taskDiagnosisCatalogEntry(code);
  const action = (() => {
    if (code === 'NO_MATCHING_FLOW' || code === 'NO_MATCHING_RULE') return { actionHref: flowCreateHref(task), actionLabel: 'Open Dispatch Setup' };
    if (code === 'AGENT_OFFLINE' || code === 'RESULT_TIMEOUT') return { actionHref: common.agentId ? `/agents/${encodeURIComponent(common.agentId)}` : '/agents', actionLabel: 'Open Agent Runtime' };
    if (code === 'AGENT_CAPACITY_FULL' || code === 'NO_FLOW_AGENT') return { actionHref: task.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : '/dispatch-flows', actionLabel: 'Review Agent Pool' };
    return {};
  })();

  const technicalReason = code === 'DISPATCH_DELIVERY_FAILED'
    ? firstPresent(task.failureReason, task.blockedReason, evidence?.firstBlockingReason, catalog.explanation)
    : catalog.explanation;

  return {
    ...common,
    code,
    title: catalog.title,
    reason: technicalReason ?? catalog.explanation,
    nextAction: catalog.nextAction,
    tone: code === 'COMPLETED' ? 'success' : code === 'IN_PROGRESS' ? 'info' : code === 'DISPATCH_DELIVERY_FAILED' || code === 'RESULT_TIMEOUT' ? 'danger' : 'warning',
    ...action,
  };
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
    : ['AGENT_OFFLINE', 'AGENT_CAPACITY_FULL', 'NO_FLOW_AGENT', 'MANUAL_ASSIGNMENT_REQUIRED'].includes(diagnosis.code) ? 'agent'
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
    { id: 'event', title: 'Event ', state: state('event', Boolean(task.sourceEventId || task.incidentId || task.createdAt)), detail: task.sourceEventId ? `Event ${task.sourceEventId}` : 'Core Event details', timestamp: task.createdAt },
    { id: 'task', title: 'Task created', state: state('task', true), detail: `Task ${task.taskId}`, timestamp: task.createdAt },
    { id: 'flow', title: 'Flow and rule match', state: state('flow', flowMatched, !flowMatched && !blockedAt), detail: flowMatched ? `Flow ${task.matchedFlowId} / Rule ${task.matchedRuleId}` : diagnosis.code === 'NO_MATCHING_FLOW' ? diagnosis.reason : 'Waiting for Core to record the matched Source Flow and rule.', timestamp: timelineTime(timeline, 'FLOW_MATCHED', 'RULE_MATCHED') },
    { id: 'agent', title: ' Agent', state: state('agent', agentSelected, flowMatched && !agentSelected && !blockedAt), detail: agentSelected ? `Agent ${task.assignedAgentId}` : blockedAt === 'agent' ? diagnosis.reason : 'Capability,Runtime and capacity.', timestamp: timelineTime(timeline, 'AGENT_ELIGIBLE', 'AGENT_SELECTED') },
    { id: 'assignment', title: 'create Assignment', state: state('assignment', assignmentCreated, agentSelected && !assignmentCreated), detail: task.dispatchRequestId ? `Dispatch Request ${task.dispatchRequestId}` : assignmentCreated ? 'Core recorded assignment evidence.' : 'Waiting for Agent selection.', timestamp: timelineTime(timeline, 'ASSIGNMENT_CREATED', 'DISPATCH_REQUEST_CREATED') },
    { id: 'delivery', title: 'Netty ', state: state('delivery', delivered, assignmentCreated && !delivered && blockedAt !== 'delivery'), detail: delivered ? 'Gateway or Agent transport delivery evidence is recorded.' : blockedAt === 'delivery' ? diagnosis.reason : 'Waiting for Gateway delivery evidence.', timestamp: timelineTime(timeline, 'DELIVERED', 'GATEWAY_ACCEPTED') },
    { id: 'ack', title: 'Agent ACK', state: state('ack', acked, delivered && !acked), detail: acked ? 'Agent ' : 'waiting Agent ACK.', timestamp: timelineTime(timeline, 'ACK', 'AGENT_ACCEPTED') },
    { id: 'result', title: 'Agent Result', state: state('result', completed, acked && !completed && blockedAt !== 'result'), detail: completed ? 'production Result return to Core,Task ' : blockedAt === 'result' ? diagnosis.reason : 'waiting RESULT/ERROR callback.', timestamp: task.updatedAt ?? timelineTime(timeline, 'RESULT', 'COMPLETED') },
  ];
}
