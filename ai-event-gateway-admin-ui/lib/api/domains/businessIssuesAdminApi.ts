import { coreTenantApiGet } from '@/lib/api/coreClient';

export interface BusinessIssueView {
  linkId: string;
  taskId: string;
  providerType?: string | null;
  externalProjectId?: string | null;
  externalIssueId?: string | null;
  externalIssueKey?: string | null;
  externalIssueUrl?: string | null;
  issueStatus?: string | null;
  syncStatus?: string | null;
  ownerDepartmentId?: string | null;
  ownerGroupId?: string | null;
  requesterDepartmentId?: string | null;
  requesterGroupId?: string | null;
  executorDepartmentId?: string | null;
  executorGroupId?: string | null;
  scopeStatus: 'RESOLVED' | 'UNRESOLVED';
  scopeSourceType?: string | null;
  scopeSourceId?: string | null;
  scopeSourceVersion?: number | null;
  scopeInheritedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface IssueAttachmentView {
  attachmentId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  malwareStatus: string;
  contentAvailable: boolean;
  legalHold: boolean;
  metadataVersion: number;
  observedAt: string;
}

export const businessIssuesAdminApi = {
  list(limit = 200) { return coreTenantApiGet<BusinessIssueView[]>('/api/issues', { limit }); },
  detail(linkId: string) { return coreTenantApiGet<BusinessIssueView>(`/api/issues/${encodeURIComponent(linkId)}`); },
  attachments(linkId: string) { return coreTenantApiGet<IssueAttachmentView[]>(`/api/issues/${encodeURIComponent(linkId)}/attachments`); },
} as const;
