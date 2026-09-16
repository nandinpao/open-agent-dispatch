export const A2A_CANCELLATION_OUTCOMES = [
  'PENDING','CANCELLED_CONFIRMED','CANCELLED_UNCONFIRMED','CANCELLATION_TIMEOUT','AGENT_ALREADY_COMPLETED','STALE_CANCELLATION'
] as const;
export const A2A_CANCELLATION_PROCESSING_STATUSES = [
  'REQUESTED','FENCING_ROTATED','RUNTIME_DELIVERY_PENDING','RUNTIME_ACK_PENDING','CONFIRMED','FAILED_RETRYABLE','WAIT_HUMAN'
] as const;
export interface A2ACancellationView {
  tenantId:string;cancellationId:string;requestId:string;childTaskId?:string;assignmentId?:string;executionAttemptId?:string;
  attemptNo?:number;dispatchRequestId?:string;agentId?:string;agentSessionId?:string;ownerGatewayNodeId?:string;
  revokedFencingTokenHash?:string;activeFencingTokenHash?:string;cancellationFingerprint?:string;status:string;
  outcome:(typeof A2A_CANCELLATION_OUTCOMES)[number];processingStatus:(typeof A2A_CANCELLATION_PROCESSING_STATUSES)[number];
  reconciliationClassification:string;reason?:string;deliveryStatus?:string;lastError?:string;retryCount:number;reconciliationCount:number;
  requestedAt?:string;resultCutoffAt?:string;deliveryAt?:string;acknowledgedAt?:string;deadlineAt?:string;nextReconcileAt?:string;
  lastReconciledAt?:string;completedAt?:string;updatedAt?:string;version:number;
}
export interface A2ACancellationEvidenceView { evidenceId:string;eventKey?:string;attemptNo?:number;assignmentId?:string;executionAttemptId?:string;evidenceType:string;evidenceReference?:string;evidenceHash?:string;decision:string;reasonCode?:string;details?:string;occurredAt?:string; }
export interface A2ACancellationLateResultView { quarantineId:string;classification:string;reasonCode:string;reason?:string;assignmentId?:string;executionAttemptId?:string;status:string;quarantinedAt?:string; }
export interface A2ACancellationReliabilityView { cancellation:A2ACancellationView;evidence:A2ACancellationEvidenceView[];lateResults:A2ACancellationLateResultView[]; }
