import type { CoreDispatchReadinessCheck } from '@/lib/types/core';

export interface BeginnerLabel {
  label: string;
  description: string;
  shortLabel?: string;
  beginnerAction?: string;
}

export const capabilityBeginnerLabels: Record<string, BeginnerLabel> = {
  TASK_EXECUTION: {
    label: 'Task execution capability',
    description: 'Capability required by a Task before an Agent can be considered eligible.'
  },
  GENERAL_AGENT: {
    label: 'General Agent capability',
    description: 'A general capability approved for an Agent. Agent Pool membership alone does not satisfy a Task capability requirement.'
  }
};

export const statusBeginnerLabels: Record<string, BeginnerLabel> = {
  APPROVED: { label: 'Approved', description: 'Core governance has approved this configuration or capability.' },
  REJECTED: { label: 'Rejected', description: 'Core governance rejected this configuration or capability.' },
  REVOKED: { label: 'Revoked', description: 'The previous authorization has been revoked and cannot be used for dispatch.' },
  SUSPENDED: { label: 'Suspended', description: 'The Agent is temporarily excluded from dispatch.' },
  NORMAL: { label: 'Healthy', description: 'No quarantine, revocation, or compromised-risk condition is active.' },
  CONNECTED: { label: 'Connected', description: 'The Agent currently has an active runtime session through the Gateway.' },
  OFFLINE: { label: 'Offline', description: 'No active runtime connection is available for this Agent.' },
  IDLE: { label: 'Idle', description: 'The Agent is connected and currently has no active workload.' },
  RUNNING: { label: 'Running', description: 'The Agent or Task is actively processing work.' },
  ASSIGNED: { label: 'Assigned', description: 'Core selected an eligible Agent for this Task.' },
  DISPATCH_REQUESTED: { label: 'Dispatch requested', description: 'Core created a Dispatch Request and is waiting for Gateway delivery.' },
  DISPATCHED: { label: 'Dispatched', description: 'The Gateway delivered the Task to the selected Agent.' },
  ACKED: { label: 'Agent acknowledged', description: 'The Agent acknowledged the Task; Core is waiting for the execution result.' },
  COMPLETED: { label: 'Completed', description: 'The Task completed successfully.' },
  FAILED: { label: 'Failed', description: 'The Task or operation failed. Review the blocking reason and evidence.' },
  CANCELLED: { label: 'Cancelled', description: 'The Task was cancelled.' },
  PENDING: { label: 'Pending', description: 'The workflow is waiting for its next state transition.' },
  QUEUED: { label: 'Queued', description: 'The request is waiting for a worker or Gateway processing.' },
  DELIVERED_TO_GATEWAY: { label: 'Delivered to Gateway', description: 'Core handed the Dispatch Request to Netty Gateway and is waiting for Agent transport evidence.' },
  WAIT_FOR_AGENT_ACK: { label: 'Waiting for Agent acknowledgement', description: 'Gateway delivered the Task and Core is waiting for Agent ACK or callback.' },
  WAIT_FOR_AGENT_RESULT: { label: 'Waiting for Agent result', description: 'Core is waiting for RESULT or ERROR callback from the Agent.' },
  WAITING_AGENT: { label: 'Waiting for eligible Agent', description: 'No currently eligible Agent is available. Review capability, runtime, capacity, and backoff evidence.' },
  WAIT_GATEWAY_ACK: { label: 'Waiting for Gateway acknowledgement', description: 'Core is waiting for Gateway delivery acknowledgement.' },
  NETTY_ACK: { label: 'Gateway acknowledged', description: 'Netty accepted the dispatch and Core is waiting for Agent-side evidence.' },
  CALLBACK_RELAY: { label: 'Callback relay', description: 'Gateway is relaying the Agent callback to Core.' }
};

export const policyBeginnerLabels: Record<string, BeginnerLabel> = {
  READ_ONLY: { label: 'Read only', description: 'The operation can read data but cannot mutate provider state.' },
  PROPOSE_ONLY: { label: 'Propose only', description: 'The Agent may propose an action but cannot execute it automatically.' },
  SAFE_COMMAND_ALLOWED: { label: 'Safe command allowed', description: 'Approved low-risk commands may be executed within the governed scope.' },
  ANALYZE: { label: 'Analyze', description: 'The Agent may analyze the event or Task evidence.' },
  PROPOSE: { label: 'Propose', description: 'The Agent may propose a next action for review.' },
  READ: { label: 'Read', description: 'The Agent may read the governed resource.' }
};

export const readinessCheckLabels: Record<string, string> = {
  TASK_REQUIRES_CAPABILITY: 'Task Required Capability',
  CAPABILITY_DEFINED: 'Canonical Capability Definition',
  DISPATCH_CONTRACT_RESOLVED: 'Source Flow / Agent Pool',
  GOVERNANCE_PROFILE: 'Agent governance status',
  GOVERNANCE_APPROVED_CAPABILITY: 'Approved Agent Capability',
  RUNTIME_AGENT_ONLINE: 'Agent runtime connection',
  RUNTIME_REPORTED_CAPABILITY: 'Runtime capability evidence',
  AGENT_CAPACITY_AVAILABLE: 'Agent capacity',
  CAPABILITY_CONTRACT_ELIGIBLE: 'Capability eligibility'
};

export const readinessCheckBeginnerHints: Record<string, string> = {
  TASK_REQUIRES_CAPABILITY: 'A normal Dispatch Task must declare at least one Required Capability. It is a blocking Agent-eligibility contract.',
  CAPABILITY_DEFINED: 'Every Required Capability must resolve to an active Canonical Capability Definition.',
  DISPATCH_CONTRACT_RESOLVED: 'The Source Flow selects the Agent Pool. The Pool defines where Core searches; it does not prove capability qualification.',
  GOVERNANCE_PROFILE: 'The Agent must be enabled, approved, and not suspended or revoked.',
  GOVERNANCE_APPROVED_CAPABILITY: 'A Pool member must have the Task-required Canonical Capability approved by Core governance.',
  RUNTIME_AGENT_ONLINE: 'At least one capability-qualified Pool member must have a healthy Gateway runtime connection.',
  RUNTIME_REPORTED_CAPABILITY: 'Runtime capability evidence must be compatible with the governed Agent capability contract when the protocol reports it.',
  AGENT_CAPACITY_AVAILABLE: 'The eligible Agent must have an available slot and must not be draining or under backoff.',
  CAPABILITY_CONTRACT_ELIGIBLE: 'Core evaluates Source Flow, Agent Pool, Required Capability, runtime eligibility, capacity/backoff, and then routing score.'
};

export function normalizeCode(value?: string): string {
  return value?.trim().replace(/[.-]/g, '_').toUpperCase() ?? '';
}

function fallbackCodeLabel(value?: string): string {
  const normalized = normalizeCode(value);
  if (!normalized) return '-';
  return normalized;
}

export function beginnerCapabilityLabel(capabilityCode?: string): string {
  const normalized = normalizeCode(capabilityCode);
  return capabilityBeginnerLabels[normalized]?.label ?? fallbackCodeLabel(capabilityCode);
}

export function beginnerCapabilityDescription(capabilityCode?: string): string {
  const normalized = normalizeCode(capabilityCode);
  return capabilityBeginnerLabels[normalized]?.description ?? 'Canonical capability code used by Task and Agent eligibility contracts.';
}

export function beginnerStatusLabel(status?: string): string {
  const normalized = normalizeCode(status);
  return statusBeginnerLabels[normalized]?.label ?? policyBeginnerLabels[normalized]?.label ?? fallbackCodeLabel(status);
}

export function beginnerStatusDescription(status?: string): string {
  const normalized = normalizeCode(status);
  return statusBeginnerLabels[normalized]?.description ?? policyBeginnerLabels[normalized]?.description ?? 'Current workflow status.';
}

export function beginnerCheckLabel(check?: CoreDispatchReadinessCheck): string {
  const key = normalizeCode(check?.key);
  return readinessCheckLabels[key] ?? check?.label ?? key;
}

export function beginnerCheckHint(check?: CoreDispatchReadinessCheck): string | undefined {
  const key = normalizeCode(check?.key);
  return check?.beginnerHint ?? readinessCheckBeginnerHints[key];
}

export function humanizedCodeLabel(code?: string, type?: 'skill' | 'capability' | 'status' | 'policy' | 'operation' | 'taskType' | 'generic'): string {
  const normalized = normalizeCode(code);
  if (!normalized) return '-';
  if (type === 'skill' || type === 'capability') return beginnerCapabilityLabel(normalized);
  if (type === 'status') return beginnerStatusLabel(normalized);
  if (type === 'policy' || type === 'operation') return policyBeginnerLabels[normalized]?.label ?? fallbackCodeLabel(normalized);
  return capabilityBeginnerLabels[normalized]?.label
    ?? statusBeginnerLabels[normalized]?.label
    ?? policyBeginnerLabels[normalized]?.label
    ?? fallbackCodeLabel(normalized);
}

export function humanizedCodeDescription(code?: string, type?: 'skill' | 'capability' | 'status' | 'policy' | 'operation' | 'taskType' | 'generic'): string {
  const normalized = normalizeCode(code);
  if (!normalized) return 'No code is available.';
  if (type === 'skill' || type === 'capability') return beginnerCapabilityDescription(normalized);
  if (type === 'status') return beginnerStatusDescription(normalized);
  if (type === 'policy' || type === 'operation') return policyBeginnerLabels[normalized]?.description ?? 'Governed policy or operation code.';
  return capabilityBeginnerLabels[normalized]?.description
    ?? statusBeginnerLabels[normalized]?.description
    ?? policyBeginnerLabels[normalized]?.description
    ?? 'System code used by the current workflow.';
}

export const beginnerSkillLabel = beginnerCapabilityLabel;
export const beginnerSkillDescription = beginnerCapabilityDescription;
