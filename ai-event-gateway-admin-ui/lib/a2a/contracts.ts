export type A2AApprovalMode = 'NONE' | 'OPERATOR' | 'SECURITY' | 'DUAL_APPROVAL';
export type A2AResultAggregationPolicy = 'ALL_SUCCESS' | 'ANY_SUCCESS' | 'QUORUM' | 'PARTIAL_ALLOWED' | 'MANUAL_DECISION' | 'FAIL_FAST';
export type A2ACancellationPolicy = 'CANCEL_ALL_CHILDREN' | 'CANCEL_PENDING_ONLY' | 'DO_NOT_CANCEL_RUNNING' | 'MANUAL_DECISION';
export type A2AFailurePropagationPolicy = 'FAIL_PARENT' | 'BLOCK_PARENT' | 'MARK_PARTIAL' | 'CONTINUE' | 'WAIT_HUMAN';
export type A2AGovernanceScopeClass = 'TENANT' | 'SAME_SCOPE' | 'CROSS_SCOPE';

export interface A2APolicyView {
  tenantId?: string | null;
  policyId: string;
  policyCode?: string | null;
  policyName?: string | null;
  sourceDomainId?: string | null;
  sourceDepartmentId?: string | null;
  sourceGroupId?: string | null;
  sourceAgentPoolId?: string | null;
  sourceAgentId?: string | null;
  targetDomainId?: string | null;
  targetDepartmentId?: string | null;
  targetGroupId?: string | null;
  targetAgentPoolId?: string | null;
  governanceOwnerDepartmentId?: string | null;
  governanceOwnerGroupId?: string | null;
  governanceScopeClass?: A2AGovernanceScopeClass | string | null;
  governanceScopeStatus?: string | null;
  allowedTaskTypes?: string[];
  allowedServiceCodes?: string[];
  allowedCapabilityCodes?: string[];
  maxSensitivityLevel?: string | null;
  approvalMode?: A2AApprovalMode | string | null;
  maxHopCount?: number;
  rateLimitPerMinute?: number;
  timeoutSeconds?: number;
  issueProjectionPolicy?: string | null;
  handoffContextPolicyId?: string | null;
  handoffContextRequirement?: string | null;
  resultAggregationPolicy?: A2AResultAggregationPolicy | string | null;
  aggregationQuorum?: number;
  cancellationPolicy?: A2ACancellationPolicy | string | null;
  failurePropagationPolicy?: A2AFailurePropagationPolicy | string | null;
  allowReturnToExistingDomain?: boolean;
  enabled?: boolean;
  effectiveAt?: string | null;
  expiresAt?: string | null;
  version: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** Phase 0 placeholder. New delegation is not executable until the canonical Capability model lands in Phase 1. */
export interface CreateA2ARequestInput {
  requestedTaskType?: string;
  requestedServiceCode?: string;
  requestedCapabilityCodes: string[];
  reason: string;
  inputPayloadRef?: string;
  sensitivityLevel?: string;
}

export interface A2ARequestRecord {
  requestId: string;
  rootTaskId?: string | null;
  sourceTaskId?: string | null;
  childTaskId?: string | null;
  sourceDomainId?: string | null;
  targetDomainId?: string | null;
  targetDepartmentId?: string | null;
  targetGroupId?: string | null;
  requestedTaskType?: string | null;
  requestedServiceCode?: string | null;
  requestedCapabilityCodes?: string[];
  policyId?: string | null;
  policyVersion?: number;
  approvalStatus?: string | null;
  approvalCount?: number;
  requiredApprovalCount?: number;
  approvalActorIds?: string[];
  requestStatus?: string | null;
  operationalStage?: string | null;
  blockerCode?: string | null;
  blockerReason?: string | null;
  requestedByType?: string | null;
  requestedById?: string | null;
  sensitivityLevel?: string | null;
  expiresAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  version: number;
}

const LABELS: Record<string, string> = {
  REQUESTED: 'Requested',
  VALIDATING: 'Validating',
  REJECTED: 'Rejected',
  WAITING_APPROVAL: 'Waiting for approval',
  APPROVED: 'Approved',
  CHILD_TASK_CREATING: 'Creating child task',
  CHILD_TASK_CREATED: 'Child task created',
  DISPATCH_REQUESTED: 'Dispatch requested',
  DISPATCHING: 'Dispatching',
  RUNNING: 'Running',
  WAITING_RESULT: 'Waiting for result',
  WAIT_HUMAN: 'Waiting for human decision',
  BLOCKED: 'Blocked',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCEL_REQUESTED: 'Cancellation requested',
  CANCELLED_CONFIRMED: 'Cancellation confirmed',
  CANCELLED_UNCONFIRMED: 'Cancellation unconfirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  TERMINAL: 'Finished',
  NONE: 'No blocker',
  OPERATOR: 'Operator approval',
  SECURITY: 'Security approval',
  DUAL_APPROVAL: 'Two distinct approvers',
  ALL_SUCCESS: 'All child work must succeed',
  ANY_SUCCESS: 'Any successful child result is enough',
  QUORUM: 'Quorum',
  PARTIAL_ALLOWED: 'Partial result allowed',
  MANUAL_DECISION: 'Human decision',
  FAIL_FAST: 'Fail fast',
  CANCEL_ALL_CHILDREN: 'Cancel all child work',
  CANCEL_PENDING_ONLY: 'Cancel pending child work only',
  DO_NOT_CANCEL_RUNNING: 'Keep running child work',
  FAIL_PARENT: 'Fail parent work',
  BLOCK_PARENT: 'Block parent work',
  MARK_PARTIAL: 'Mark parent as partial',
  CONTINUE: 'Continue parent work',
};

export function humanizeA2ACode(value?: string | null): string {
  const normalized = String(value ?? '').trim();
  if (!normalized) return 'Not specified';
  const upper = normalized.toUpperCase();
  if (LABELS[upper]) return LABELS[upper];
  return normalized
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function a2aPolicyDisplayName(policy: A2APolicyView): string {
  return policy.policyName?.trim() || policy.policyCode?.trim() || policy.policyId;
}
