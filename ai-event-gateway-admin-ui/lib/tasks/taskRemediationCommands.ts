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
  requiredPayload?: 'targetAgentId' | 'targetPoolId';
  requiredPhrase?: string;
}

const COMMAND_DEFINITIONS: Record<CoreTaskRemediationCommandType, TaskRemediationCommandDefinition> = {
  REEVALUATE_ROUTING: {
    commandType: 'REEVALUATE_ROUTING',
    label: '重新評估派工',
    description: '清除目前可重試阻擋並重新走 Source Flow、Pool、Eligibility 與 Selection。',
    tone: 'warning',
  },
  ASSIGN_AGENT: {
    commandType: 'ASSIGN_AGENT',
    label: '指定 Agent',
    description: '由 Operator 指定可用 Agent。Tenant、Credential 與 Runtime identity 仍由 Core 檢查。',
    tone: 'warning',
    requiredPayload: 'targetAgentId',
  },
  CHANGE_POOL: {
    commandType: 'CHANGE_POOL',
    label: '更換工作池',
    description: '將 Task 目標 Pool 改為指定工作池，再重新評估派工。',
    tone: 'warning',
    requiredPayload: 'targetPoolId',
  },
  MOVE_TO_MANUAL_QUEUE: {
    commandType: 'MOVE_TO_MANUAL_QUEUE',
    label: '轉人工佇列',
    description: '停止自動派工，等待 Operator 指定 Agent 或更換工作池。',
    tone: 'warning',
  },
  RETRY_DELIVERY: {
    commandType: 'RETRY_DELIVERY',
    label: '重送 Delivery',
    description: '重送最近一次 Dispatch Request，不覆蓋原始 Routing Evidence。',
    tone: 'warning',
  },
  RETRY_TASK: {
    commandType: 'RETRY_TASK',
    label: '重試 Task',
    description: '將失敗或等待中的 Task 放回可派工狀態。',
    tone: 'warning',
  },
  CANCEL_TASK: {
    commandType: 'CANCEL_TASK',
    label: '取消 Task',
    description: '取消尚未完成的 Task，會保留 before/after audit。',
    tone: 'danger',
    requiredPhrase: 'CONFIRM_CANCEL_TASK',
  },
  IGNORE_TASK: {
    commandType: 'IGNORE_TASK',
    label: '標記忽略',
    description: '將 Task 移至不再處理的人工決策結果，適用於已確認不需處理的失敗。',
    tone: 'danger',
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

export function taskRuntimeVersion(task: CoreTaskRuntimeView): number | undefined {
  if (!task.updatedAt) return undefined;
  const time = Date.parse(task.updatedAt);
  return Number.isFinite(time) ? time : undefined;
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
  operatorId?: string;
  targetAgentId?: string;
  targetPoolId?: string;
}>): CoreTaskRemediationCommandRequest {
  const payload: Record<string, unknown> = {};
  if (input.targetAgentId) payload.targetAgentId = input.targetAgentId;
  if (input.targetPoolId) payload.targetPoolId = input.targetPoolId;
  return {
    commandType: input.commandType,
    expectedTaskVersion: taskRuntimeVersion(input.task),
    idempotencyKey: `admin-ui-${input.commandType}-${input.task.taskId}-${Date.now()}`,
    reason: input.reason,
    operatorId: input.operatorId ?? 'admin-ui',
    payload,
  };
}
