import { coreApiPost } from '@/lib/api/coreClient';
import { createIdempotencyKey } from '@/lib/utils/uuid';

export type ExportResourceType = 'TASK' | 'TASK_CHAIN' | 'TASK_RESULT' | 'TASK_CONTEXT_SNAPSHOT' | 'TASK_ISSUE_LINK' | 'ISSUE_CONTEXT_SNAPSHOT' | 'ISSUE_CONFLICT' | 'ISSUE_DEAD_LETTER' | 'ISSUE_TOPOLOGY' | 'ISSUE_CONNECTION' | 'ISSUE_PRINCIPAL' | 'ISSUE_CREDENTIAL_METADATA' | 'ISSUE_PROJECT_MAPPING';
export type ExportFormat = 'CSV' | 'JSON' | 'NDJSON' | 'XLSX';
export interface ExportAuthorizationView { exportAuthorizationId: string; resourceRef: { tenantId: string; resourceType: string; resourceId: string }; principalId: string; format: ExportFormat; allowedFields: string[]; fieldSetHash: string; maximumRows: number; runtimeLeaseId: string; fencingVersion: number; issuedAt: string; expiresAt: string; executable: boolean; }
export interface AttachmentGrantView { attachmentRef: { tenantId: string; resourceType: string; resourceId: string }; filename: string; contentType: string; sizeBytes: number; sha256: string; readDecisionId: string; downloadDecisionId: string; runtimeLeaseId: string; fencingVersion: number; contentHandleId: string; handleExpiresAt: string; issuedAt: string; }

const idempotencyKey = (prefix: string) => createIdempotencyKey(prefix);

export function authorizeExport(input: { resourceType: ExportResourceType; resourceId: string; format: ExportFormat; fields: string[]; estimatedRowCount: number; purpose: string }) {
  return coreApiPost<ExportAuthorizationView>('/api/resource-access/artifacts/exports/authorize', input, { headers: { 'Idempotency-Key': idempotencyKey('export-authorize') } });
}

export function requestAttachmentDownloadGrant(input: { resourceType: 'TASK_ATTACHMENT' | 'ISSUE_ATTACHMENT'; resourceId: string; purpose: string }) {
  return coreApiPost<AttachmentGrantView>(`/api/resource-access/artifacts/attachments/${encodeURIComponent(input.resourceType)}/${encodeURIComponent(input.resourceId)}/download-grants`, { purpose: input.purpose });
}
