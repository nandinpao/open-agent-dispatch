export type TaskDiagnosisCatalogCode =
  | 'COMPLETED'
  | 'IN_PROGRESS'
  | 'NO_MATCHING_FLOW'
  | 'NO_MATCHING_RULE'
  | 'NO_FLOW_AGENT'
  | 'AGENT_OFFLINE'
  | 'AGENT_CAPACITY_FULL'
  | 'MANUAL_ASSIGNMENT_REQUIRED'
  | 'DISPATCH_DELIVERY_FAILED'
  | 'RESULT_TIMEOUT';

export type TaskDiagnosisCatalogCategory =
  | 'NONE'
  | 'CONFIGURATION_BLOCKED'
  | 'RUNTIME_BLOCKED'
  | 'DELIVERY_BLOCKED'
  | 'EXECUTION_BLOCKED'
  | 'MANUAL_ACTION_REQUIRED';

export type TaskDiagnosisCatalogOwnerPlane =
  | 'CORE_CONFIGURATION'
  | 'CORE_ROUTING'
  | 'NETTY_RUNTIME'
  | 'AGENT_RUNTIME'
  | 'OPERATOR';

export interface TaskDiagnosisCatalogEntry {
  code: TaskDiagnosisCatalogCode;
  title: string;
  explanation: string;
  nextAction: string;
  category: TaskDiagnosisCatalogCategory;
  ownerPlane: TaskDiagnosisCatalogOwnerPlane;
  evidencePointer: string;
  recommendedCommand?:
    | 'REEVALUATE_ROUTING'
    | 'ASSIGN_AGENT'
    | 'CHANGE_POOL'
    | 'MOVE_TO_MANUAL_QUEUE'
    | 'RETRY_DELIVERY'
    | 'RETRY_TASK'
    | 'CANCEL_TASK'
    | 'IGNORE_TASK';
}

const TASK_DIAGNOSIS_CATALOG: Record<TaskDiagnosisCatalogCode, TaskDiagnosisCatalogEntry> = {
  COMPLETED: {
    code: 'COMPLETED',
    title: 'Task completed',
    explanation: 'Core recorded terminal success and result evidence is available for review.',
    nextAction: 'Review the result, dispatch evidence, and audit timeline.',
    category: 'NONE',
    ownerPlane: 'CORE_ROUTING',
    evidencePointer: 'RESULT / COMPLETION',
  },
  IN_PROGRESS: {
    code: 'IN_PROGRESS',
    title: 'Task execution is in progress',
    explanation: 'Core has not recorded a terminal blocker or terminal result for this Task.',
    nextAction: 'Review the current lifecycle stage and wait for the next authoritative transition.',
    category: 'NONE',
    ownerPlane: 'CORE_ROUTING',
    evidencePointer: 'TASK / ROUTING / ASSIGNMENT',
  },
  NO_MATCHING_FLOW: {
    code: 'NO_MATCHING_FLOW',
    title: 'No matching active Source Flow',
    explanation: 'Core could not match this Task to an enabled Source Flow and rule for the current event attributes.',
    nextAction: 'Review Source Flow criteria, rule activation, ownership, and target Agent Pool configuration.',
    category: 'CONFIGURATION_BLOCKED',
    ownerPlane: 'CORE_CONFIGURATION',
    evidencePointer: 'ROUTING / FLOW',
    recommendedCommand: 'REEVALUATE_ROUTING',
  },
  NO_MATCHING_RULE: {
    code: 'NO_MATCHING_RULE',
    title: 'No matching dispatch rule',
    explanation: 'A Source Flow was identified, but no enabled rule matched the Task attributes.',
    nextAction: 'Review rule criteria and ordering, then re-evaluate routing after the configuration is corrected.',
    category: 'CONFIGURATION_BLOCKED',
    ownerPlane: 'CORE_CONFIGURATION',
    evidencePointer: 'FLOW / RULE',
    recommendedCommand: 'REEVALUATE_ROUTING',
  },
  NO_FLOW_AGENT: {
    code: 'NO_FLOW_AGENT',
    title: 'No eligible Agent is available',
    explanation: 'Core could not select an assignable Agent from the routed Agent Pool under the current eligibility policy.',
    nextAction: 'Review pool membership, approval, capabilities, runtime state, capacity, and backoff evidence.',
    category: 'CONFIGURATION_BLOCKED',
    ownerPlane: 'CORE_ROUTING',
    evidencePointer: 'POOL / ELIGIBILITY',
    recommendedCommand: 'REEVALUATE_ROUTING',
  },
  AGENT_OFFLINE: {
    code: 'AGENT_OFFLINE',
    title: 'Agent runtime is not connected',
    explanation: 'The selected or required Agent does not currently have an authoritative runtime session available for dispatch.',
    nextAction: 'Check Agent connection, heartbeat, credential state, and runtime identity before retrying delivery.',
    category: 'RUNTIME_BLOCKED',
    ownerPlane: 'AGENT_RUNTIME',
    evidencePointer: 'AGENT_RUNTIME / SESSION',
    recommendedCommand: 'RETRY_DELIVERY',
  },
  AGENT_CAPACITY_FULL: {
    code: 'AGENT_CAPACITY_FULL',
    title: 'Eligible Agent capacity is exhausted',
    explanation: 'Core found a candidate Agent, but current runtime capacity prevents a new assignment or delivery.',
    nextAction: 'Wait for capacity to recover or review alternate eligible Agents in the routed pool.',
    category: 'RUNTIME_BLOCKED',
    ownerPlane: 'AGENT_RUNTIME',
    evidencePointer: 'ELIGIBILITY / CAPACITY',
    recommendedCommand: 'REEVALUATE_ROUTING',
  },
  MANUAL_ASSIGNMENT_REQUIRED: {
    code: 'MANUAL_ASSIGNMENT_REQUIRED',
    title: 'Operator assignment is required',
    explanation: 'Core has placed this Task in a governed manual-assignment state and will not select a target automatically.',
    nextAction: 'Assign an eligible Agent, change the target Agent Pool, or cancel the Task with an audit reason.',
    category: 'MANUAL_ACTION_REQUIRED',
    ownerPlane: 'OPERATOR',
    evidencePointer: 'MANUAL_ACTION / ASSIGNMENT',
    recommendedCommand: 'ASSIGN_AGENT',
  },
  DISPATCH_DELIVERY_FAILED: {
    code: 'DISPATCH_DELIVERY_FAILED',
    title: 'Dispatch delivery failed',
    explanation: 'Core created dispatch intent, but the Netty or Agent transport path did not complete delivery successfully.',
    nextAction: 'Inspect Gateway and Agent runtime evidence, then retry delivery without recreating the Task.',
    category: 'DELIVERY_BLOCKED',
    ownerPlane: 'NETTY_RUNTIME',
    evidencePointer: 'DELIVERY / GATEWAY',
    recommendedCommand: 'RETRY_DELIVERY',
  },
  RESULT_TIMEOUT: {
    code: 'RESULT_TIMEOUT',
    title: 'Agent result callback timed out',
    explanation: 'The Task reached the Agent execution path, but Core has not received the required RESULT or ERROR callback within the expected window.',
    nextAction: 'Inspect Agent execution and callback relay evidence before deciding whether to retry the Task.',
    category: 'EXECUTION_BLOCKED',
    ownerPlane: 'AGENT_RUNTIME',
    evidencePointer: 'ACK / RESULT / CALLBACK',
    recommendedCommand: 'RETRY_TASK',
  },
};

export function taskDiagnosisCatalogEntry(code: TaskDiagnosisCatalogCode): TaskDiagnosisCatalogEntry {
  return TASK_DIAGNOSIS_CATALOG[code];
}

export function taskDiagnosisCatalogEntries(): readonly TaskDiagnosisCatalogEntry[] {
  return Object.values(TASK_DIAGNOSIS_CATALOG);
}
