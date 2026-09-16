/**
 * Task runtime callback and dispatch-attempt types.
 *
 * These definitions are physically owned by the Task domain. core.ts re-exports
 * them as a compatibility barrel while callers migrate to domain imports.
 */
export interface CoreCallbackInboxEntry {
  callbackId?: string;
  taskId?: string;
  dispatchRequestId?: string;
  assignmentId?: string;
  agentId?: string;
  receivedByGatewayNodeId?: string;
  receivedAgentSessionId?: string;
  attemptNo?: number;
  callbackType?: string;
  processStatus?: string;
  accepted?: boolean;
  duplicate?: boolean;
  replayDetected?: boolean;
  idempotencyKey?: string;
  callbackFingerprint?: string;
  ignoredReason?: string;
  message?: string;
  errorCode?: string;
  errorMessage?: string;
  previousTaskStatus?: string;
  newTaskStatus?: string;
  previousDispatchStatus?: string;
  newDispatchStatus?: string;
  payload?: Record<string, unknown>;
  occurredAt?: string;
  receivedAt?: string;
  processedAt?: string;
  authoritative?: boolean;
}

export interface CoreCallbackInboxSummary {
  taskId?: string;
  dispatchRequestId?: string;
  totalCallbacks?: number;
  acceptedCallbacks?: number;
  rejectedCallbacks?: number;
  duplicateCallbacks?: number;
  replayRejectedCallbacks?: number;
  latestCallbackId?: string;
  latestCallbackType?: string;
  latestProcessStatus?: string;
  terminalCallbackReceived?: boolean;
  recoveryRequired?: boolean;
  nextAction?: string;
}

export interface CoreDispatchAttemptLedgerEvent {
  eventId?: string;
  eventType: string;
  source?: string;
  status?: string;
  taskId?: string;
  dispatchRequestId?: string;
  callbackId?: string;
  agentId?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  attemptNo?: number;
  idempotencyKey?: string;
  reason?: string;
  errorCode?: string;
  errorMessage?: string;
  authoritative?: boolean;
  occurredAt?: string;
}

export interface CoreDispatchAttemptLedger {
  dispatchRequestId: string;
  taskId: string;
  incidentId?: string;
  assignmentId?: string;
  agentId?: string;
  lastKnownGatewayNodeId?: string;
  lastKnownAgentSessionId?: string;
  dispatchStatus?: string;
  deliveryState?: string;
  callbackState?: string;
  resultState?: string;
  attemptNo?: number;
  lastCallbackId?: string;
  dispatchTokenPresent?: boolean;
  authoritative?: boolean;
  recoveryRequired?: boolean;
  nextAction?: string;
  lastError?: string;
  createdAt?: string;
  updatedAt?: string;
  dispatchedAt?: string;
  ackReceivedAt?: string;
  progressReceivedAt?: string;
  resultReceivedAt?: string;
  errorReceivedAt?: string;
  terminalAt?: string;
  leaseExpiresAt?: string;
  callbackDeadlineAt?: string;
  events?: CoreDispatchAttemptLedgerEvent[];
}

export interface CoreDispatchAttemptHistoryRecord {
  historyId: string;
  taskId: string;
  incidentId?: string;
  assignmentId?: string;
  dispatchRequestId?: string;
  agentId?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  siteId?: string;
  routingDecisionId?: string;
  eventType: string;
  status?: string;
  attemptNo?: number;
  taskDispatchAttemptNo?: number;
  reason?: string;
  errorCode?: string;
  errorMessage?: string;
  nextAttemptAt?: string;
  runtimeBackoffUntil?: string;
  workerId?: string;
  claimUntil?: string;
  payloadJson?: string;
  occurredAt?: string;
  createdAt?: string;
}

