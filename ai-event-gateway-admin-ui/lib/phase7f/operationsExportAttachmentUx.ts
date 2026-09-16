export type ExportFieldDisposition = 'INCLUDED' | 'MASKED' | 'OMITTED' | 'REQUIRES_APPROVAL';
export type ExportJobStatus = 'QUEUED' | 'AUTHORIZING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'EXPIRED' | 'REVOKED';
export type AttachmentMalwareStatus = 'NOT_SCANNED' | 'PENDING' | 'CLEAN' | 'INFECTED' | 'SCAN_FAILED';
export type AttachmentAvailability = 'SCANNING' | 'DOWNLOAD_AVAILABLE' | 'DOWNLOAD_BLOCKED' | 'CONTENT_UNAVAILABLE' | 'APPROVAL_REQUIRED';
export type BulkPolicy = 'ALL_OR_NOTHING' | 'PARTIAL_ALLOWED' | 'DRY_RUN_ONLY';
export type BulkResultStatus = 'SUCCEEDED' | 'DENIED' | 'CONFLICT' | 'FAILED' | 'SKIPPED';
export type ImportStage = 'UPLOAD' | 'PARSE' | 'VALIDATE' | 'DRY_RUN' | 'AUTHORIZE' | 'CONFLICT_PREVIEW' | 'EXECUTE' | 'RESULT';

export interface ExportFieldInput {
  field: string;
  classification: 'METADATA' | 'SUMMARY' | 'STANDARD' | 'SENSITIVE' | 'FULL' | 'SECRET';
  exportAllowed: boolean;
  visibilityGranted: 'NONE' | 'METADATA' | 'SUMMARY' | 'STANDARD' | 'SENSITIVE' | 'FULL' | 'SECRET_METADATA';
  approvalRequired?: boolean;
}

const VISIBILITY_ORDER = ['NONE', 'METADATA', 'SUMMARY', 'STANDARD', 'SENSITIVE', 'FULL', 'SECRET_METADATA'] as const;
const CLASSIFICATION_VISIBILITY: Record<ExportFieldInput['classification'], number> = {
  METADATA: 1, SUMMARY: 2, STANDARD: 3, SENSITIVE: 4, FULL: 5, SECRET: 99,
};

export function classifyExportField(input: ExportFieldInput): { disposition: ExportFieldDisposition; explanation: string } {
  if (input.classification === 'SECRET') return { disposition: 'OMITTED', explanation: 'Secret fields are never exported.' };
  if (!input.exportAllowed) return { disposition: 'OMITTED', explanation: 'The active field policy does not allow export.' };
  if (input.approvalRequired) return { disposition: 'REQUIRES_APPROVAL', explanation: 'The field requires an approved export lane before artifact generation.' };
  const granted = VISIBILITY_ORDER.indexOf(input.visibilityGranted);
  const required = CLASSIFICATION_VISIBILITY[input.classification];
  if (granted < required) return { disposition: 'MASKED', explanation: 'The field exceeds the current visibility ceiling and will be masked.' };
  return { disposition: 'INCLUDED', explanation: 'The field is permitted by the current field and visibility policy.' };
}

export interface ExportDraftInput {
  resourceId: string;
  format: 'CSV' | 'JSON' | 'NDJSON' | 'XLSX';
  estimatedRowCount: number;
  purpose: string;
  fields: readonly ExportFieldInput[];
}

export function validateExportDraft(input: ExportDraftInput) {
  const issues: string[] = [];
  if (!input.resourceId.trim()) issues.push('A governed resource is required.');
  if (!Number.isFinite(input.estimatedRowCount) || input.estimatedRowCount < 1) issues.push('Estimated row count must be at least one.');
  if (input.estimatedRowCount > 100_000) issues.push('The standard export lane cannot exceed 100,000 estimated rows.');
  if (input.purpose.trim().length < 20) issues.push('Business purpose must contain at least 20 characters.');
  if (!input.fields.length) issues.push('Select at least one field.');
  const preview = input.fields.map((field) => ({ ...field, ...classifyExportField(field) }));
  if (preview.every((field) => field.disposition === 'OMITTED')) issues.push('No selected field is eligible for export.');
  const requiresApproval = preview.some((field) => field.disposition === 'REQUIRES_APPROVAL') || input.estimatedRowCount > 25_000 || input.format === 'XLSX';
  return {
    status: issues.length ? 'BLOCKED' as const : requiresApproval ? 'APPROVAL_REQUIRED' as const : 'READY' as const,
    issues,
    preview,
    requiresApproval,
    directDownloadAllowed: false,
    safestNextAction: issues.length ? 'Correct the blocked export fields before requesting authorization.' : 'Create a governed export authorization and track the owning domain job to completion.',
  };
}

export function exportJobExperience(status: ExportJobStatus, accessChanged = false) {
  if (accessChanged && !['COMPLETED', 'EXPIRED', 'REVOKED'].includes(status)) {
    return { status: 'REVOKED' as const, title: 'Export stopped', message: 'This export was stopped because access changed.', retryable: false, downloadAvailable: false };
  }
  return {
    QUEUED: { status, title: 'Queued', message: 'The export is waiting for an authorized worker.', retryable: false, downloadAvailable: false },
    AUTHORIZING: { status, title: 'Authorizing', message: 'Resource, field policy, visibility, epoch, and runtime lease are being checked.', retryable: false, downloadAvailable: false },
    RUNNING: { status, title: 'Running', message: 'The owning domain is producing rows under the fixed authorization.', retryable: false, downloadAvailable: false },
    PAUSED: { status, title: 'Paused', message: 'The job is paused pending an operator or authority decision.', retryable: true, downloadAvailable: false },
    COMPLETED: { status, title: 'Completed', message: 'The committed artifact may be released through a short-lived governed handle.', retryable: false, downloadAvailable: true },
    FAILED: { status, title: 'Failed', message: 'The job failed. Review sanitized evidence before retrying.', retryable: true, downloadAvailable: false },
    EXPIRED: { status, title: 'Expired', message: 'The authorization or artifact retention period expired.', retryable: true, downloadAvailable: false },
    REVOKED: { status, title: 'Revoked', message: 'Access changed or the runtime lease was fenced.', retryable: false, downloadAvailable: false },
  }[status];
}

export interface AttachmentExperienceInput {
  malwareStatus: AttachmentMalwareStatus;
  contentAvailable: boolean;
  legalHold?: boolean;
  downloadCapability: 'ENABLED' | 'STEP_UP_REQUIRED' | 'APPROVAL_REQUIRED' | 'DENIED';
}

export function attachmentAvailability(input: AttachmentExperienceInput) {
  if (input.malwareStatus === 'INFECTED') return { status: 'DOWNLOAD_BLOCKED' as AttachmentAvailability, downloadAllowed: false, reason: 'Malware was detected. The attachment cannot be released.' };
  if (input.malwareStatus === 'SCAN_FAILED') return { status: 'DOWNLOAD_BLOCKED' as AttachmentAvailability, downloadAllowed: false, reason: 'The malware scan failed. A successful clean scan is required.' };
  if (input.malwareStatus === 'NOT_SCANNED' || input.malwareStatus === 'PENDING') return { status: 'SCANNING' as AttachmentAvailability, downloadAllowed: false, reason: 'Scanning is in progress. Download is unavailable until the status is CLEAN.' };
  if (!input.contentAvailable) return { status: 'CONTENT_UNAVAILABLE' as AttachmentAvailability, downloadAllowed: false, reason: 'Content is not available in governed internal storage.' };
  if (input.legalHold || input.downloadCapability === 'APPROVAL_REQUIRED' || input.downloadCapability === 'STEP_UP_REQUIRED') return { status: 'APPROVAL_REQUIRED' as AttachmentAvailability, downloadAllowed: false, reason: input.legalHold ? 'Legal-hold policy requires a governed release decision.' : 'Additional assurance or approval is required before release.' };
  if (input.downloadCapability !== 'ENABLED') return { status: 'DOWNLOAD_BLOCKED' as AttachmentAvailability, downloadAllowed: false, reason: 'The current resource access decision does not allow download.' };
  return { status: 'DOWNLOAD_AVAILABLE' as AttachmentAvailability, downloadAllowed: true, reason: 'A short-lived opaque content handle may be requested. Provider URLs and credentials remain hidden.' };
}

export interface BackgroundJobInput {
  servicePrincipal: boolean;
  explicitResourceCount: number;
  decisionCount: number;
  runtimeLeaseCount: number;
  executable: boolean;
}
export function backgroundJobExperience(input: BackgroundJobInput) {
  const issues: string[] = [];
  if (!input.servicePrincipal) issues.push('Background work requires a Service Account or System Service principal.');
  if (input.explicitResourceCount < 1) issues.push('An explicit resource set is required.');
  if (input.decisionCount !== input.explicitResourceCount) issues.push('Every resource requires a formal authorization decision.');
  if (input.executable && input.runtimeLeaseCount !== input.explicitResourceCount) issues.push('Every executable resource requires an epoch-bound runtime lease.');
  return { ready: issues.length === 0 && input.executable, issues, safestNextAction: issues.length ? 'Repair the service identity, resource set, decisions, or runtime leases.' : 'Run one job iteration and re-check leases before result commit.' };
}

export interface BulkPreviewInput { selected: number; eligible: number; denied: number; locked: number; stale: number; requiresApproval: number; policy: BulkPolicy; }
export function bulkOperationPreview(input: BulkPreviewInput) {
  const accounted = input.eligible + input.denied + input.locked + input.stale + input.requiresApproval;
  const valid = input.selected >= 0 && accounted === input.selected;
  const executable = valid && input.policy !== 'DRY_RUN_ONLY' && input.eligible > 0 && (input.policy === 'PARTIAL_ALLOWED' || accounted === input.eligible);
  return { valid, executable, partial: input.policy === 'PARTIAL_ALLOWED' && input.eligible < input.selected, safestNextAction: !valid ? 'Refresh the backend dry-run because the item counts do not reconcile.' : input.policy === 'DRY_RUN_ONLY' ? 'Review the dry-run result; execution is disabled by policy.' : !executable ? 'Resolve denied, locked, stale, or approval-required resources before execution.' : 'Submit the governed bulk operation; the backend will authorize every resource again.' };
}

export function importStageExperience(stage: ImportStage) {
  return {
    UPLOAD: 'Accept a file as untrusted input; it grants no authority.',
    PARSE: 'Parse format and schema without executing business mutations.',
    VALIDATE: 'Map validation errors to rows and fields.',
    DRY_RUN: 'Resolve resources and produce a non-mutating impact preview.',
    AUTHORIZE: 'Authorize each resource, scope, and visibility independently.',
    CONFLICT_PREVIEW: 'Show stale versions and conflicting state before execution.',
    EXECUTE: 'Execute idempotently under the selected all-or-nothing or partial policy.',
    RESULT: 'Return succeeded, denied, conflict, failed, and skipped outcomes without secrets.',
  }[stage];
}

export interface SupportDiagnosticInput {
  correlationIds: readonly string[];
  versions: readonly string[];
  healthCategories: readonly string[];
  recentSafeEvents: readonly string[];
  secretValues?: readonly string[];
  rawPayloads?: readonly string[];
}
export function supportDiagnosticPreview(input: SupportDiagnosticInput) {
  return {
    supportReference: `support-${input.correlationIds.length}-${input.versions.length}`,
    correlationIds: [...new Set(input.correlationIds.map((value) => value.trim()).filter(Boolean))],
    versions: [...new Set(input.versions.map((value) => value.trim()).filter(Boolean))],
    healthCategories: [...new Set(input.healthCategories.map((value) => value.trim()).filter(Boolean))],
    recentSafeEvents: input.recentSafeEvents.map((value) => value.trim()).filter(Boolean).slice(0, 20),
    omitted: ['Secret', 'Token', 'Credential', 'Raw payload', 'Sensitive field value'],
    secretValueCountIgnored: input.secretValues?.length ?? 0,
    rawPayloadCountIgnored: input.rawPayloads?.length ?? 0,
  };
}

export function notificationSafety(resourceStillVisible: boolean) {
  return resourceStillVisible
    ? { outcome: 'OPEN_CONTEXT' as const, message: 'Reauthorize the target resource before showing notification context.' }
    : { outcome: 'SAFE_UNAVAILABLE' as const, message: 'The notification target is no longer available. Sensitive title and existence details remain hidden.' };
}
