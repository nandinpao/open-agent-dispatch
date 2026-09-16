export const ISSUE_PROJECTION_STRATEGIES = [
  "NONE", "APPEND_TO_PARENT_ISSUE", "CREATE_NEW_ISSUE", "CREATE_NEW_AND_RELATE",
  "LINK_EXISTING_ISSUE", "MANUAL_DECISION",
] as const;
export type IssueProjectionStrategy = (typeof ISSUE_PROJECTION_STRATEGIES)[number];
export const ISSUE_SYNC_STATUSES = [
  "NOT_REQUIRED", "PENDING", "IN_PROGRESS", "SYNCED", "FAILED_RETRYABLE",
  "FAILED_PERMANENT", "CONFLICT", "DISABLED",
] as const;
export type IssueSyncStatus = (typeof ISSUE_SYNC_STATUSES)[number];
export const ISSUE_LINK_ROLES = ["PRIMARY", "CHILD_PROCESS", "REFERENCE", "AUDIT", "MANUAL"] as const;
export const PROJECTION_PURPOSES = ["PRIMARY_ISSUE","INCIDENT_ESCALATION","HANDOFF_AUDIT","RESULT_SUMMARY","OPERATIONAL_FOLLOW_UP"] as const;
export type ProjectionPurpose = (typeof PROJECTION_PURPOSES)[number];
export const PROJECTION_LIFECYCLE = ["REQUESTED","RESOLVING_MAPPING","VALIDATING_PRINCIPAL","READY","IN_PROGRESS","VERIFYING_EXTERNAL_RESULT","SYNCED","FAILED","CONFLICT","DEAD_LETTER","SUPERSEDED","DISABLED"] as const;
export type ProjectionLifecycleStatus = (typeof PROJECTION_LIFECYCLE)[number];
export const PROJECTION_FAILURES = ["NONE","MAPPING_NOT_FOUND","MAPPING_NOT_ACTIVE","MAPPING_SCHEMA_DRIFT","PRINCIPAL_SCOPE_DENIED","PRINCIPAL_PERMISSION_DENIED","CANONICAL_DOCUMENT_INVALID","SENSITIVE_FIELD_BLOCKED","IDEMPOTENCY_CONFLICT","STALE_DOMAIN_EVENT","VERSION_CONFLICT","PROVIDER_REJECTED","PROVIDER_TRANSIENT_FAILURE","EXTERNAL_STATE_CONFLICT","RETRY_EXHAUSTED","DISABLED_BY_OPERATOR"] as const;
export const PROJECTION_RECOVERY = ["NONE","RETRY","REFRESH_MAPPING","REVALIDATE_PRINCIPAL","VERIFY_EXTERNAL_RESULT","MANUAL_REVIEW","DEAD_LETTER","SUPERSEDE","DISABLE"] as const;
export interface ProjectionAggregateKey { tenantId:string; taskId:string; connectionId:string; projectMappingId:string; projectionPurpose:ProjectionPurpose; }
export interface ExternalIssueDocumentView { schemaVersion:number; projectionId:string; taskReference:{taskId:string;taskType:string;sourceSystem:string;correlationId:string}; summary:string; description:string; issueType:string; priority:string; labels:string[]; components:string[]; approvedContext:Record<string,unknown>; comments:unknown[]; links:unknown[]; sourceEvidence:Record<string,string>; mappingVersion:number; mappingSchemaHash:string; }
export interface ProjectionIntentPreviewView { aggregateKey:ProjectionAggregateKey; projectionId:string; document:ExternalIssueDocumentView; documentHash:string; warnings:string[]; accepted:boolean; }
export interface IssueProjectionStateView { tenantId:string; projectionId:string; projectionAggregateKey:string; projectionPurpose:ProjectionPurpose; taskId:string; connectionId:string; projectMappingId:string; projectMappingVersion:number; projectMappingSchemaHash:string; canonicalDocumentSchemaVersion:number; canonicalDocumentHash?:string|null; lifecycleStatus:ProjectionLifecycleStatus; failureClassification:(typeof PROJECTION_FAILURES)[number]; recoveryStrategy:(typeof PROJECTION_RECOVERY)[number]; lastAppliedDomainEventId:string; operationSequence:number; projectionVersion:number; supersededBy?:string|null; }
export const issueProjectionCopy = {
  externalIssues: "External Issues", syncReliability: "Integration Sync", retry: "Retry Sync", resolveConflict: "Resolve Conflict", openDeadLetters: "Open Dead Letters",
  providerAuthority: "External Issue status does not change the OpenDispatch Task status.",
  duplicateProtection: "Provider retries reuse the same idempotency key and do not create duplicate Issues.",
  webhookReplay: "Webhook events are verified and protected against replay.", circuitOpen: "Delivery is paused because the Provider circuit breaker is open.",
  conflict: "The external Issue changed, but the OpenDispatch Task remains authoritative.",
  aggregateRule: "One active Projection is identified by Tenant, Task, Connection, Project Mapping, and Projection Purpose.",
  canonicalBoundary: "Only the Canonical ExternalIssueDocument crosses the Provider boundary; full Task and Handoff payloads are prohibited.",
} as const;
export function issueSyncStatusLabel(status: IssueSyncStatus): string { return status.toLowerCase().split("_").map((part) => part[0].toUpperCase() + part.slice(1)).join(" "); }
