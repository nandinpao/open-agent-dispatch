export const API_GOVERNANCE_SECTIONS = [
  "API Contract",
  "Domain Events",
  "Audit Evidence",
  "Permission Points",
  "Reason Codes",
] as const;

export const API_RESPONSE_METADATA = [
  "resourceVersion",
  "correlationId",
  "authorizationDecisionId",
  "syncStatus",
  "warnings",
] as const;

/** Actor identity is derived from the authenticated session, never from caller-supplied headers. */
export const MUTATION_HEADERS = [
  "Idempotency-Key",
  "X-Correlation-Id",
  "If-Match",
  "X-Audit-Reason",
] as const;

export const AUDIT_TIMELINE_LABELS = {
  title: "Audit Timeline",
  actor: "Actor",
  action: "Action",
  reason: "Reason",
  outcome: "Outcome",
  correlation: "Correlation ID",
  authorization: "Authorization Decision",
  occurredAt: "Occurred At",
} as const;

export const API_ERROR_COPY = {
  versionConflict:
    "This resource changed after it was loaded. Refresh the page and try again.",
  invalidVersion:
    "The resource version is invalid. Refresh the page before retrying this action.",
  idempotencyConflict:
    "This Idempotency-Key was already used with different request content.",
  idempotencyInProgress:
    "A request with this Idempotency-Key is still being processed.",
  idempotencyReplay:
    "This operation was already completed. OpenDispatch did not execute it again.",
  completionBlocked:
    "Task completion is blocked until the required Handoff Context is approved.",
  authorizationDenied: "You are not authorized to perform this operation.",
  auditReasonRequired: "Provide a reason before submitting this change.",
} as const;
