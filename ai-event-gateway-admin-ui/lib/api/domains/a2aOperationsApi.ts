import { coreTenantApiGet, coreTenantApiPost } from '@/lib/api/coreClient';
import { ApiError } from '@/lib/api/client';
import { createIdempotencyKey } from '@/lib/utils/uuid';
import type { A2ARequestRecord } from '@/lib/a2a/contracts';

export interface A2AOperationsListItem {
  requestId: string;
  rootTaskId?: string | null;
  sourceTaskId?: string | null;
  childTaskId?: string | null;
  sourceDomainId?: string | null;
  targetDomainId?: string | null;
  requestedTaskType?: string | null;
  requestStatus: string;
  operationalStage: string;
  childTaskStatus: string;
  dispatchStatus: string;
  runtimeStatus: string;
  resultStatus: string;
  cancellationStatus: string;
  aggregationStatus: string;
  blockerCode: string;
  blockerMessage?: string | null;
  recommendedAction?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  version: number;
}
export interface A2AOperationsPage { items: A2AOperationsListItem[]; total: number; offset: number; limit: number; nextCursor?: string | null }
export interface A2AStageStatus { stage: string; status: string; authority: string; evidenceReference?: string | null; updatedAt?: string | null }
export interface A2ATimelineEntry { eventId: string; stage: string; eventType: string; status: string; reasonCode?: string | null; message?: string | null; actor?: string | null; evidenceReference?: string | null; occurredAt?: string | null }
export interface A2ABlockerDiagnosis { code: string; title: string; explanation: string; evidence?: string | null; recommendedAction?: string | null; humanDecisionRequired: boolean }
export interface A2AGovernedAction { code: string; label: string; method: string; endpoint: string; requiredPermission: string; confirmationRequired: boolean; guardrail: string }
export interface A2AAuthorityEvidence { authority: string; evidenceType: string; evidenceId: string; status: string; version?: number | null; occurredAt?: string | null; facts: Record<string, string> }
export interface A2ATopology { nodes: Array<{ id: string; type: string; label: string; status: string; authority: string }>; edges: Array<{ source: string; target: string; relation: string; status: string }> }
export interface A2AOperationsDetail {
  summary: A2AOperationsListItem;
  stages: A2AStageStatus[];
  timeline: A2ATimelineEntry[];
  blocker: A2ABlockerDiagnosis;
  actions: A2AGovernedAction[];
  request: A2AAuthorityEvidence;
  requestRecord?: A2ARequestRecord;
  result?: A2AAuthorityEvidence | null;
  cancellation?: A2AAuthorityEvidence | null;
  reconciliationCase?: A2AAuthorityEvidence | null;
  quarantine?: A2AAuthorityEvidence | null;
  aggregation?: A2AAuthorityEvidence | null;
  topology: A2ATopology;
}
export interface A2AOperationsFilters { requestStatus?: string; blockerCode?: string; operationalStage?: string; sourceDomainId?: string; targetDomainId?: string; q?: string; blockerOnly?: boolean; offset?: number; limit?: number; sortBy?: string; sortDirection?: 'ASC' | 'DESC' }

export function searchA2AOperations(filters: A2AOperationsFilters = {}): Promise<A2AOperationsPage> {
  const query = {
    requestStatus: filters.requestStatus,
    blockerCode: filters.blockerCode,
    operationalStage: filters.operationalStage,
    sourceDomainId: filters.sourceDomainId,
    targetDomainId: filters.targetDomainId,
    q: filters.q,
    blockerOnly: filters.blockerOnly,
    offset: filters.offset,
    limit: filters.limit,
    sortBy: filters.sortBy,
    sortDirection: filters.sortDirection,
  };
  return coreTenantApiGet('/api/a2a-operations', query);
}

export async function getA2AOperationsDetail(requestId: string): Promise<A2AOperationsDetail> {
  const detail = await coreTenantApiGet<A2AOperationsDetail>(`/api/a2a-operations/${encodeURIComponent(requestId)}`);
  try {
    const requestRecord = await getA2ARequest(requestId);
    return { ...detail, requestRecord };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 403 || error.status === 404)) return detail;
    throw error;
  }
}

export function getA2ARequest(requestId: string): Promise<A2ARequestRecord> {
  return coreTenantApiGet(`/api/a2a-requests/${encodeURIComponent(requestId)}`);
}

function key(prefix: string): string {
  return createIdempotencyKey(prefix);
}

export function rejectA2ARequest(id: string, reasonCode: string, reason: string) {
  return coreTenantApiPost(`/api/a2a-requests/${encodeURIComponent(id)}/reject`, { reasonCode, reason }, { headers: { 'Idempotency-Key': key('a2a-reject') } });
}
export function cancelA2ARequest(id: string, reason = 'Cancellation requested by operator after evidence review.') {
  return coreTenantApiPost(`/api/a2a-requests/${encodeURIComponent(id)}/cancel`, { reason }, { headers: { 'Idempotency-Key': key('a2a-cancel') } });
}
export function resolveA2AReconciliation(id: string, status: 'RESOLVED' | 'IGNORED', reason: string) {
  return coreTenantApiPost(`/api/a2a-reconciliation-cases/${encodeURIComponent(id)}/resolve`, { status, reason });
}
export function resolveA2AQuarantine(id: string, decision: 'ACCEPT_AS_GOVERNANCE_EVIDENCE' | 'REJECT', reason: string) {
  return coreTenantApiPost(`/api/a2a-result-quarantine/${encodeURIComponent(id)}/resolve`, { decision, reason });
}
export function getA2ACancellationReliability(id: string): Promise<import('@/lib/a2aCancellationReliabilityContract').A2ACancellationReliabilityView> {
  return coreTenantApiGet(`/api/a2a-cancellations/${encodeURIComponent(id)}/reliability`);
}
export function reconcileA2ACancellation(id: string, reason: string) {
  return coreTenantApiPost(`/api/a2a-cancellations/${encodeURIComponent(id)}/reconcile`, { reason }, { headers: { 'Idempotency-Key': key('a2a-cancellation-reconcile') } });
}

export interface A2ARepairPlan { caseId: string; caseType: string; diagnosisCode: string; authorityOwner: string; action: string; requiredPermission: string; expectedResourceVersion: number; evidenceSnapshotHash: string; impactSummary: string; preconditions: string[]; planHash: string }
export interface A2AReconciliationCase { caseId: string; requestId?: string | null; caseType: string; status: string; authorityOwner?: string | null; diagnosisCode?: string | null; reasonCode: string; reason?: string | null; repairAction?: string | null; requiredPermission?: string | null; impactSummary?: string | null; planHash?: string | null; attemptCount: number; repairAttemptNo: number; nextAttemptAt?: string | null; lastErrorCode?: string | null; lastError?: string | null; version: number }
export interface A2AReconciliationEvidence { evidenceId: string; eventKey: string; evidenceType: string; evidenceReference?: string | null; decision: string; reasonCode?: string | null; safeSummary?: string | null; actorId?: string | null; occurredAt?: string | null }
export interface A2AReconciliationDetail { reconciliationCase: A2AReconciliationCase; repairPlan: A2ARepairPlan; evidence: A2AReconciliationEvidence[] }
export function getA2AReconciliationDetail(caseId: string): Promise<A2AReconciliationDetail> { return coreTenantApiGet(`/api/a2a-reconciliation/${encodeURIComponent(caseId)}`); }
export function executeA2ARepair(caseId: string, expectedVersion: number, planHash: string, reason: string): Promise<A2AReconciliationCase> {
  return coreTenantApiPost(`/api/a2a-reconciliation/${encodeURIComponent(caseId)}/execute`, { expectedVersion, planHash, reason }, { headers: { 'Idempotency-Key': key('a2a-repair') } });
}

export interface CapabilityDelegationListItem {
  delegationId: string;
  parentTaskId: string;
  childTaskId?: string | null;
  capabilityCode: string;
  operation: string;
  status: string;
  selectedProviderId?: string | null;
  selectedProviderName?: string | null;
  selectedProviderType?: string | null;
  executionKind?: string | null;
  resultStatus?: string | null;
  resultNotificationStatus?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CapabilityDelegationDecisionStage {
  code: 'WHO_CAN' | 'WHO_MAY' | 'WHO_SHOULD' | 'HOW' | 'EXECUTION' | string;
  label: string;
  status: string;
  authority: string;
  evidenceId?: string | null;
  summary: string;
  reasons: string[];
  details: Record<string, string>;
}

export interface CapabilityDelegationEventEvidence {
  eventId: string;
  eventType: string;
  fromStatus?: string | null;
  toStatus?: string | null;
  reasons: string[];
  evidence: Record<string, string>;
  occurredAt?: string | null;
}

export interface CapabilityDelegationDetail {
  summary: CapabilityDelegationListItem;
  request: Record<string, string>;
  execution: Record<string, string>;
  reasonCodes: string[];
  stages: CapabilityDelegationDecisionStage[];
  events: CapabilityDelegationEventEvidence[];
}

export interface CapabilityDelegationPage {
  items: CapabilityDelegationListItem[];
  total: number;
  offset: number;
  limit: number;
}

export interface CapabilityDelegationFilters {
  status?: string;
  parentTaskId?: string;
  capabilityCode?: string;
  providerType?: string;
  q?: string;
  offset?: number;
  limit?: number;
  sortDirection?: 'ASC' | 'DESC';
}

export function searchCapabilityDelegations(filters: CapabilityDelegationFilters = {}): Promise<CapabilityDelegationPage> {
  const query: Record<string, string | number | boolean | null | undefined> = { ...filters };
  return coreTenantApiGet('/api/a2a-operations/capability-delegations', query);
}

export function getCapabilityDelegationDetail(delegationId: string): Promise<CapabilityDelegationDetail> {
  return coreTenantApiGet(`/api/a2a-operations/capability-delegations/${encodeURIComponent(delegationId)}`);
}
