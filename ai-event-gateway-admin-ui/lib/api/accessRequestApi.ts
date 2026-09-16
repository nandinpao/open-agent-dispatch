import { coreApiGet, coreApiPost } from '@/lib/api/client';

export type UiAccessRequestState = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'CANCELLED' | 'EXPIRED';

export interface UiAccessRequestResponse {
  requestId: string;
  uiActionId: string;
  resourceType: string;
  resourceId: string;
  resourceVersionAtRequest: number;
  requestedVisibility: string;
  validFrom: string;
  validTo: string;
  state: UiAccessRequestState;
  requestVersion: number;
  grantVersion: number;
  independentlyApproved: boolean;
  requesterIsCurrentUser: boolean;
  updatedAt: string;
}

export interface SubmitUiAccessRequest {
  uiActionId: string;
  resourceId: string;
  expectedResourceVersion: number;
  requestedVisibility: 'METADATA' | 'SUMMARY' | 'STANDARD';
  durationHours: number;
  businessPurpose: string;
}

export interface ReviewUiAccessRequest {
  requestVersion: number;
  grantVersion: number;
  resourceVersion: number;
  reason: string;
  idempotencyKey: string;
}

const options = { requireStandardEnvelope: false } as const;

export function submitUiAccessRequest(input: SubmitUiAccessRequest, idempotencyKey: string) {
  return coreApiPost<UiAccessRequestResponse>('/api/ui/access-requests', input, {
    ...options,
    headers: { 'Idempotency-Key': idempotencyKey, Accept: 'application/json' },
  });
}

export function getUiAccessRequest(requestId: string) {
  return coreApiGet<UiAccessRequestResponse>(`/api/ui/access-requests/${encodeURIComponent(requestId)}`, undefined, options);
}

export function getUiAccessRequestForReview(requestId: string) {
  return coreApiGet<UiAccessRequestResponse>(`/api/ui/access-requests/${encodeURIComponent(requestId)}/review`, undefined, options);
}

export function approveUiAccessRequest(requestId: string, review: ReviewUiAccessRequest) {
  return reviewUiAccessRequest(requestId, 'approve', review);
}

export function rejectUiAccessRequest(requestId: string, review: ReviewUiAccessRequest) {
  return reviewUiAccessRequest(requestId, 'reject', review);
}

function reviewUiAccessRequest(requestId: string, decision: 'approve' | 'reject', review: ReviewUiAccessRequest) {
  return coreApiPost<UiAccessRequestResponse>(
    `/api/ui/access-requests/${encodeURIComponent(requestId)}/${decision}`,
    undefined,
    {
      ...options,
      headers: {
        'If-Match': String(review.requestVersion),
        'X-Scope-Grant-Version': String(review.grantVersion),
        'X-Resource-Version': String(review.resourceVersion),
        'X-Audit-Reason': review.reason,
        'Idempotency-Key': review.idempotencyKey,
      },
    },
  );
}
