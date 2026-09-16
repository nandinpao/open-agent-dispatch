import type {
  CoreTaskRemediationCommandRequest,
  CoreTaskRemediationCommandType,
  CoreTaskRuntimeView,
} from '@/lib/types/core';
import type { TaskDiagnosisReadModel } from '@/lib/tasks/taskDiagnosisReadModel';

export interface TaskRemediationCommandDefinition {
  commandType: CoreTaskRemediationCommandType;
  label: string;
  description: string;
  tone: 'primary' | 'warning' | 'danger';
  riskLevel: 'MODERATE' | 'HIGH';
  scope: 'ROUTING' | 'ASSIGNMENT' | 'DELIVERY' | 'TASK' | 'HUMAN_CONTROL';
  requiredPayload?: 'targetAgentId' | 'targetPoolId';
  requiredPhrase?: string;
}

const COMMAND_DEFINITIONS: Record<CoreTaskRemediationCommandType, TaskRemediationCommandDefinition> = {
  REEVALUATE_ROUTING: {
    commandType: 'REEVALUATE_ROUTING',
    label: 'Re-evaluate Routing',
    description: 'Re-evaluate Source Flow, pool, eligibility, and routing selection using Core authority.',
    tone: 'warning',
    riskLevel: 'MODERATE',
    scope: 'ROUTING',
  },
  ASSIGN_AGENT: {
    commandType: 'ASSIGN_AGENT',
    label: 'Reassign Agent',
    description: 'Assign an eligible Agent explicitly. Tenant, credential, authorization, and runtime identity remain Core-authoritative.',
    tone: 'warning',
    riskLevel: 'MODERATE',
    scope: 'ASSIGNMENT',
    requiredPayload: 'targetAgentId',
  },
  CHANGE_POOL: {
    commandType: 'CHANGE_POOL',
    label: 'Change Agent Pool',
    description: 'Change the Task target pool and let Core re-evaluate the eligible Agent set.',
    tone: 'warning',
    riskLevel: 'MODERATE',
    scope: 'ROUTING',
    requiredPayload: 'targetPoolId',
  },
  MOVE_TO_MANUAL_QUEUE: {
    commandType: 'MOVE_TO_MANUAL_QUEUE',
    label: 'Move to Manual Queue',
    description: 'Pause automatic dispatch and require an operator to choose the next Agent or Agent Pool action.',
    tone: 'warning',
    riskLevel: 'MODERATE',
    scope: 'HUMAN_CONTROL',
  },
  RETRY_DELIVERY: {
    commandType: 'RETRY_DELIVERY',
    label: 'Retry Delivery',
    description: 'Retry the current Dispatch Request delivery without recreating the Task or replacing routing evidence.',
    tone: 'warning',
    riskLevel: 'MODERATE',
    scope: 'DELIVERY',
  },
  RETRY_TASK: {
    commandType: 'RETRY_TASK',
    label: 'Retry Task',
    description: 'Retry the failed Task through the authoritative Task lifecycle and dispatch pipeline.',
    tone: 'warning',
    riskLevel: 'MODERATE',
    scope: 'TASK',
  },
  CANCEL_TASK: {
    commandType: 'CANCEL_TASK',
    label: 'Cancel Task',
    description: 'Cancel this non-terminal Task and preserve before/after audit evidence.',
    tone: 'danger',
    riskLevel: 'HIGH',
    scope: 'TASK',
    requiredPhrase: 'CONFIRM_CANCEL_TASK',
  },
  IGNORE_TASK: {
    commandType: 'IGNORE_TASK',
    label: 'Ignore Task',
    description: 'Mark this failed Task as intentionally ignored/dead-lettered with explicit operator evidence.',
    tone: 'danger',
    riskLevel: 'HIGH',
    scope: 'TASK',
    requiredPhrase: 'CONFIRM_IGNORE_TASK',
  },
};

function normalizedStatus(task: CoreTaskRuntimeView): string {
  const status = String(task.status ?? '').toUpperCase();
  if (status === 'CREATED') return 'QUEUED';
  if (status === 'DISPATCHED') return 'ASSIGNED';
  if (status === 'COMPLETED') return 'SUCCEEDED';
  if (status === 'TIMED_OUT' || status === 'TIMEOUT' || status === 'CANCELLED') return 'FAILED';
  return status;
}

function isTerminal(task: CoreTaskRuntimeView): boolean {
  const raw = String(task.status ?? '').toUpperCase();
  return ['SUCCEEDED', 'COMPLETED', 'DEAD_LETTER', 'CANCELLED'].includes(raw);
}

export function taskRuntimeVersion(task: CoreTaskRuntimeView): number {
  const version = task.version;
  if (!Number.isSafeInteger(version) || Number(version) < 1) {
    throw new Error('Task resource version is unavailable. Refresh the Task before running a remediation command.');
  }
  return Number(version);
}

export function deriveAllowedTaskRemediationCommands(
  task: CoreTaskRuntimeView,
  diagnosis?: TaskDiagnosisReadModel,
): TaskRemediationCommandDefinition[] {
  if (isTerminal(task)) return [];
  const retryReason = String(task.dispatchRetryReason ?? '').toUpperCase();
  const status = normalizedStatus(task);
  const category = diagnosis?.primaryBlocker.category;
  const commandTypes: CoreTaskRemediationCommandType[] = retryReason.startsWith('MANUAL_ASSIGNMENT_REQUIRED')
    ? ['ASSIGN_AGENT', 'CHANGE_POOL', 'CANCEL_TASK']
    : ['QUEUED', 'RETRY_WAIT', 'ORPHANED', 'RECONCILING'].includes(status)
      ? ['REEVALUATE_ROUTING', 'ASSIGN_AGENT', 'CHANGE_POOL', 'MOVE_TO_MANUAL_QUEUE', 'CANCEL_TASK']
      : ['ASSIGNED', 'RUNNING'].includes(status)
        ? ['RETRY_DELIVERY', 'CANCEL_TASK']
        : ['FAILED', 'ESCALATED'].includes(status)
          ? ['RETRY_TASK', 'ASSIGN_AGENT', 'CHANGE_POOL', 'IGNORE_TASK', 'CANCEL_TASK']
          : ['REEVALUATE_ROUTING', 'CANCEL_TASK'];

  if (category === 'DELIVERY_BLOCKED' && !commandTypes.includes('RETRY_DELIVERY')) commandTypes.unshift('RETRY_DELIVERY');
  if (category === 'MANUAL_ACTION_REQUIRED') {
    const manualCommands: CoreTaskRemediationCommandType[] = ['ASSIGN_AGENT', 'CHANGE_POOL', 'CANCEL_TASK'];
    return manualCommands.map((command) => COMMAND_DEFINITIONS[command]);
  }
  return Array.from(new Set(commandTypes)).map((command) => COMMAND_DEFINITIONS[command]);
}

export function buildTaskRemediationCommandRequest(input: Readonly<{
  task: CoreTaskRuntimeView;
  commandType: CoreTaskRemediationCommandType;
  reason: string;
  idempotencyKey: string;
  targetAgentId?: string;
  targetPoolId?: string;
}>): CoreTaskRemediationCommandRequest {
  if (!input.idempotencyKey.trim()) throw new Error('A logical idempotency key is required.');
  const payload: Record<string, unknown> = {};
  if (input.targetAgentId) payload.targetAgentId = input.targetAgentId;
  if (input.targetPoolId) payload.targetPoolId = input.targetPoolId;
  return {
    commandType: input.commandType,
    expectedTaskVersion: taskRuntimeVersion(input.task),
    idempotencyKey: input.idempotencyKey,
    reason: input.reason,
    payload,
  };
}
