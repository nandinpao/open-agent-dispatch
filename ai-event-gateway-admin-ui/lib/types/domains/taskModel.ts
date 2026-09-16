/**
 * Canonical Task aggregate, dispatch request, issue projection and routing types.
 */
export type CoreTaskStatus =
  | "CREATED"
  | "WAITING_APPROVAL"
  | "ASSIGNED"
  | "DISPATCH_REQUESTED"
  | "DISPATCHED"
  | "ACKED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "TIMEOUT"
  | "REASSIGNING"
  | string;

export type CoreDispatchStatus =
  | "PENDING"
  | "APPROVED"
  | "CLAIMED"
  | "DELIVERING"
  | "DELIVERED"
  | "DELIVERY_FAILED"
  | "CALLBACK_RECEIVED"
  | "COMPLETED"
  | "RETRY_PENDING"
  | "DEAD_LETTER"
  | string;

export type CoreDispatchEventStage = 'EXTERNAL' | 'A2A' | 'RESULT' | 'ISSUE' | 'CALLBACK';

export interface CoreTaskRecord {
  taskId: string;
  version?: number;
  traceId?: string;
  incidentId?: string;
  sourceEventId?: string;
  sourceSystem?: string;
  taskType?: string;
  taskTypeCode?: string;
  effectiveTaskTypeCode?: string;
  status: CoreTaskStatus;
  priority?: string;
  tenantId?: string;
  siteId?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  eventType?: string;
  errorCode?: string;
  eventStage?: CoreDispatchEventStage;
  originSourceSystem?: string;
  targetSystem?: string | null;
  requestedSkill?: string | null;
  capabilityRequirementMode?: string;
  requiredOperation?: string;
  sideEffectLevel?: string;
  candidatePoolMode?: string;
  routingStrategy?: string;
  explicitActionAuthorizationRequired?: boolean;
  requirementModelVersion?: number;
  handoffMode?: string;
  correlationId?: string;
  parentTaskId?: string;
  issueSyncPolicy?: 'NONE' | 'OPTIONAL' | 'REQUIRED' | 'MANUAL' | string;
  issueSyncPolicySource?: string;
  issueSyncPolicyInheritanceMode?: string;
  issueSyncPolicyInheritedFromTaskId?: string;
  matchedFlowId?: string;
  matchedRuleId?: string;
  assignedPoolId?: string;
  targetPoolId?: string;
  targetPoolCode?: string;
  sourceDefaultPool?: boolean;
  poolMemberCount?: number;
  eligibleAgentCount?: number;
  blockerCode?: string;
  blockerReason?: string;
  classificationStatus?: 'UNCLASSIFIED' | 'CLASSIFIED' | 'CLASSIFYING' | 'RECLASSIFIED' | 'CLASSIFICATION_FAILED' | string;
  classificationResultJson?: unknown;
  routingPath?: string;
  routingPolicy?: string;
  assignedAgentId?: string;
  requiredCapabilities?: string[];
  createdReason?: string;
  occurrenceCountAtCreation?: number;
  createdAt?: string;
  updatedAt?: string;
  timeoutAt?: string;
  terminalAt?: string;
  reassignmentCount?: number;
  nextDispatchAttemptAt?: string;
  dispatchAttemptCount?: number;
  dispatchRetryReason?: string;
  dispatchRecoveryClaimedBy?: string;
  dispatchRecoveryClaimUntil?: string;
  lifecycleReason?: string;
  failureDomain?: string;
  failureCode?: string;
  failureAt?: string;
}

export interface CoreDispatchRequest {
  dispatchRequestId: string;
  assignmentId?: string;
  taskId: string;
  incidentId?: string;
  agentId?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  siteId?: string;
  status?: CoreDispatchStatus;
  reviewMode?: string;
  eligibilityStatus?: string;
  dispatchMethod?: string;
  gatewayDispatchPath?: string;
  reason?: string;
  createdAt?: string;
  updatedAt?: string;
  approvedAt?: string;
  dispatchedAt?: string;
  failedAt?: string;
  completedAt?: string;
  timedOutAt?: string;
  retryWaitingAt?: string;
  nextRetryAt?: string;
  deadLetterAt?: string;
  attemptCount?: number;
  lastError?: string;
  lastCallbackId?: string;
  outboxStatus?: "PENDING" | "CLAIMED" | "DISPATCHING" | "ACKNOWLEDGED" | "FAILED_RETRYABLE" | "DEAD_LETTER" | string;
  claimedBy?: string;
  claimStartedAt?: string;
  claimUntil?: string;
  claimHeartbeatAt?: string;
  dispatchTokenHash?: string;
  fencingTokenHash?: string;
  runtimeSessionId?: string;
  ackEvidenceId?: string;
  ackedAt?: string;
  recoveryClassification?: string;
  uncertainSince?: string;
  lastReconciledAt?: string;
  reconciliationCount?: number;
  rowVersion?: number;
}

export interface CoreTaskIssueTracking {
  taskId?: string;
  incidentId?: string;
  dispatchRequestId?: string;
  assignmentId?: string;
  agentId?: string;
  issueVendor?: string;
  issueId?: string;
  issueUrl?: string;
  issueStatus?: string;
  syncStatus?:
    "NOT_LINKED" | "SYNC_PENDING" | "SYNCED" | "SYNC_FAILED" | string;
  issueActionId?: string;
  issueActionType?: string;
  issueActionStatus?: string;
  issueRetryable?: boolean;
  issueCommentMode?: string;
  agentSummary?: string;
  issueCommentPreview?: string;
  lastSyncedAt?: string;
  syncError?: string;
  providerFailureCode?: string;
  providerStatusCode?: number;
  providerHealthImpact?: string;
  providerOutcomeCertainty?: string;
  operationFingerprint?: string;
  correlationId?: string;
  a2aRequestId?: string;
  sourceSystemId?: string;
  technicalPrincipalId?: string;
  credentialId?: string;
  credentialVersion?: string;
  idempotencyKey?: string;
  message?: string;
  lastAdapterActionAt?: string;
  createdAt?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

export interface CoreTaskIssueDedupSummary {
  activeIssueKey: string;
  issueType: string;
  issueScope?: string;
  activeStatus: 'ACTIVE' | 'AUTO_RESOLVED' | 'REVIEW_REQUIRED' | 'NOT_REQUIRED';
  syncStatus?: string;
  externalIssueId?: string;
  externalIssueUrl?: string;
  occurrenceCount: number;
  firstOccurredAt?: string;
  lastOccurredAt?: string;
  autoResolutionPolicy: 'RECOVERABLE_AUTO_RESOLVE_ON_COMPLETION' | 'GOVERNANCE_REVIEW_REQUIRED' | 'NO_ACTIVE_ISSUE_REQUIRED';
  governanceReviewRequired: boolean;
  dedupRule: 'taskId + issueType + issueScope + activeStatus';
  repeatedOccurrenceBehavior: 'Update occurrenceCount and lastOccurredAt; do not create duplicate active Issue.';
  summary: string;
}

export interface CoreTaskRuntimeSnapshot {
  tasks: CoreTaskRecord[];
  dispatchRequests: CoreDispatchRequest[];
  callbacks?: unknown[];
  latestRoutingDecisions?:
    Record<string, CoreRoutingDecisionRecord> | CoreRoutingDecisionRecord[];
  routingDecisionsByTask?:
    Record<string, CoreRoutingDecisionRecord> | CoreRoutingDecisionRecord[];
  taskIssueLinks?:
    Record<string, CoreTaskIssueTracking> | CoreTaskIssueTracking[];
  issueTrackingByTask?:
    Record<string, CoreTaskIssueTracking> | CoreTaskIssueTracking[];
  tasksByStatus?: Record<string, number>;
  dispatchByStatus?: Record<string, number>;
  generatedAt?: string;
}

export interface CoreTaskRuntimeView {
  taskId: string;
  version?: number;
  traceId?: string;
  incidentId?: string;
  sourceEventId?: string;
  sourceSystem?: string;
  taskType?: string;
  taskTypeCode?: string;
  effectiveTaskTypeCode?: string;
  status: CoreTaskStatus;
  priority?: string;
  tenantId?: string;
  siteId?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  eventType?: string;
  errorCode?: string;
  eventStage?: CoreDispatchEventStage;
  originSourceSystem?: string;
  targetSystem?: string | null;
  requestedSkill?: string | null;
  capabilityRequirementMode?: string;
  requiredOperation?: string;
  sideEffectLevel?: string;
  candidatePoolMode?: string;
  routingStrategy?: string;
  explicitActionAuthorizationRequired?: boolean;
  requirementModelVersion?: number;
  handoffMode?: string;
  correlationId?: string;
  parentTaskId?: string;
  issueSyncPolicy?: 'NONE' | 'OPTIONAL' | 'REQUIRED' | 'MANUAL' | string;
  issueSyncPolicySource?: string;
  issueSyncPolicyInheritanceMode?: string;
  issueSyncPolicyInheritedFromTaskId?: string;
  matchedFlowId?: string;
  matchedRuleId?: string;
  assignedPoolId?: string;
  targetPoolId?: string;
  targetPoolCode?: string;
  sourceDefaultPool?: boolean;
  poolMemberCount?: number;
  eligibleAgentCount?: number;
  blockerCode?: string;
  blockerReason?: string;
  classificationStatus?: 'UNCLASSIFIED' | 'CLASSIFIED' | 'CLASSIFYING' | 'RECLASSIFIED' | 'CLASSIFICATION_FAILED' | string;
  classificationResultJson?: unknown;
  routingPath?: string;
  routingPolicy?: string;
  createdReason?: string;
  occurrenceCountAtCreation?: number;
  assignedAgentId?: string;
  requiredCapabilities?: string[];
  createdAt?: string;
  updatedAt?: string;
  dispatchRequestId?: string;
  dispatchStatus?: CoreDispatchStatus;
  dispatchExecutionStatus?: string;
  dispatchDeliveryStatus?: string;
  blockedReason?: string;
  nextAction?: string;
  callbackStatus?: string;
  lifecycleReason?: string;
  failureReason?: string;
  dispatchWaitReason?: string;
  reasonCategory?: string;
  nextDispatchAttemptAt?: string;
  dispatchAttemptCount?: number;
  dispatchRetryReason?: string;
  dispatchRecoveryClaimedBy?: string;
  dispatchRecoveryClaimUntil?: string;
  latestRoutingDecision?: CoreRoutingDecisionRecord;
  userFacingDispatchError?: CoreDispatchUserFacingError;
  issueTracking?: CoreTaskIssueTracking;
  payload?: unknown;
}

export interface CoreAgentCandidateScore {
  agentId: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  siteId?: string;
  status?: string;
  score: number;
  matchedCapabilities?: string[];
  missingCapabilities?: string[];
  reason?: string;
  scoreBreakdown?: Record<string, unknown>;
}

export interface CoreDispatchUserFacingError {
  code?: string;
  severity?: string;
  message?: string;
  nextAction?: string;
  runbookRef?: string;
  context?: Record<string, unknown>;
  technicalDetails?: Record<string, unknown> | string;
}

export interface CoreRoutingDecisionRecord {
  decisionId: string;
  taskId: string;
  incidentId?: string;
  routingPolicy?: string;
  status?:
    | "SELECTED"
    | "NO_CANDIDATE"
    | "SUPPRESSED"
    | "MANUAL_REVIEW_REQUIRED"
    | string;
  selectedAgentId?: string;
  selectedGatewayNodeId?: string;
  selectedAgentSessionId?: string;
  selectedSiteId?: string;
  selectedScore?: number;
  decisionReason?: string;
  userFacingError?: CoreDispatchUserFacingError;
  candidates?: CoreAgentCandidateScore[];
  createdAt?: string;
}
