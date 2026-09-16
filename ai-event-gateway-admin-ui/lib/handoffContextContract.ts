export const HANDOFF_CONTEXT_POLICY_TYPES = [
  "NONE", "SUMMARY_ONLY", "SELECTED_FIELDS", "SELECTED_COMMENTS", "METADATA_ONLY",
  "NO_ATTACHMENTS", "ATTACHMENT_METADATA_ONLY", "FULL_APPROVED_SNAPSHOT", "CUSTOM_TEMPLATE",
] as const;
export type HandoffContextPolicyType = (typeof HANDOFF_CONTEXT_POLICY_TYPES)[number];
export const FIELD_SHARE_DECISIONS = ["ALLOW", "MASK", "OMIT", "REQUIRE_APPROVAL"] as const;
export type FieldShareDecision = (typeof FIELD_SHARE_DECISIONS)[number];
export const HANDOFF_SNAPSHOT_STATUSES = ["DRAFT", "PENDING_APPROVAL", "APPROVED", "SUPERSEDED", "EXPIRED", "REJECTED"] as const;
export const ISSUE_RELAY_STATES = [
  "PENDING_SOURCE_CONTEXT", "SOURCE_CONTEXT_READY", "TARGET_ISSUE_PENDING", "TARGET_ISSUE_CREATED",
  "BACKLINK_PENDING", "LINKED", "PARTIALLY_LINKED", "FAILED_RETRYABLE", "FAILED_PERMANENT",
] as const;
export type IssueRelayState = (typeof ISSUE_RELAY_STATES)[number];
export const ISSUE_RELAY_STRATEGIES = ["NATIVE_RELATION", "DUAL_BACKLINK_COMMENT", "OPEN_DISPATCH_ONLY", "SHARED_SCOPED_PRINCIPAL"] as const;
export const handoffContextCopy = {
  title: "Handoff Context Preview",
  shared: "Shared fields",
  masked: "Masked fields",
  omitted: "Omitted content",
  attachments: "Attachment metadata",
  approvalRequired: "Approval is required before this Snapshot can be delivered to the target Agent.",
  metadataOnly: "Attachment content is not shared. Only approved metadata is included.",
  canonicalRelation: "Cross-provider relays use OpenDispatch as the canonical relationship authority.",
  partialRelay: "The target Issue was created, but one or more backlink operations require attention.",
  retryRelay: "The relay failed temporarily and can be retried without creating a duplicate target Issue.",
  accessDenied: "The Agent is not the current assignee or the dispatch evidence does not match.",
} as const;
export function relayStateLabel(state: IssueRelayState): string {
  return state.toLowerCase().split("_").map((part) => part[0].toUpperCase() + part.slice(1)).join(" ");
}

export const HANDOFF_RELEASE_STATUSES = [
  "WAITING_APPROVAL", "READY", "RELEASING", "RELEASED", "FAILED_RETRYABLE", "WAIT_HUMAN",
] as const;
export type HandoffReleaseStatus = (typeof HANDOFF_RELEASE_STATUSES)[number];

export const HANDOFF_RECONCILIATION_CLASSIFICATIONS = [
  "NONE", "RELEASE_EVENT_MISSING", "APPROVAL_RELEASE_GAP", "SNAPSHOT_EXPIRED",
  "TARGET_BINDING_CHANGED", "HASH_CONFLICT", "RELEASE_PERSISTENCE_UNCERTAIN", "RETRY_EXHAUSTED",
] as const;
export type HandoffReconciliationClassification =
  (typeof HANDOFF_RECONCILIATION_CLASSIFICATIONS)[number];

export interface HandoffContextFieldView {
  fieldPath: string;
  decision?: FieldShareDecision;
  maskingMethod?: string;
  classification?: string;
  sourceReference?: string;
}

export interface HandoffSnapshotView {
  tenantId: string;
  snapshotId: string;
  aggregateId?: string;
  schemaVersion?: number;
  rootTaskId?: string;
  sourceTaskId: string;
  targetTaskId: string;
  sourceAgentId?: string;
  targetAgentId?: string;
  targetDomainId?: string;
  targetBindingHash?: string;
  contextPolicyId: string;
  policyVersion?: number;
  snapshotVersion?: number;
  status: (typeof HANDOFF_SNAPSHOT_STATUSES)[number];
  summary?: string;
  fieldDecisions?: HandoffContextFieldView[];
  redactedFieldPaths?: string[];
  omittedContentReasons?: string[];
  contentHash?: string;
  expiresAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  approvalEvidenceHash?: string;
  releaseStatus?: HandoffReleaseStatus;
  releaseEvidenceId?: string;
  releasedAt?: string;
  lastReleaseErrorCode?: string;
  reconciliationClassification?: HandoffReconciliationClassification;
  nextReconcileAt?: string;
  reconciliationCount?: number;
  rowVersion?: number;
}

export interface HandoffReleaseEvidenceView {
  tenantId: string;
  evidenceId: string;
  snapshotId: string;
  aggregateId?: string;
  evidenceType: string;
  releaseStatus?: HandoffReleaseStatus;
  reconciliationClassification?: HandoffReconciliationClassification;
  attemptNo?: number;
  evidenceReference?: string;
  reasonCode?: string;
  actorType?: string;
  actorId?: string;
  occurredAt?: string;
}
