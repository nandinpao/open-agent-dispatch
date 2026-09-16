import { coreTenantApiGet } from '@/lib/api/coreClient';
import type { A2APolicyView, A2ARequestRecord } from '@/lib/a2a/contracts';

/** Read-only adapter for retired directional A2A evidence. No mutation belongs in this client. */
export function listLegacyA2APolicies(sourceDomainId?: string): Promise<A2APolicyView[]> {
  return coreTenantApiGet('/api/legacy/a2a/policies', { sourceDomainId, limit: 200 });
}

export function getLegacyA2APolicy(policyId: string): Promise<A2APolicyView> {
  return coreTenantApiGet(`/api/legacy/a2a/policies/${encodeURIComponent(policyId)}`);
}

export function getLegacyA2ARequest(requestId: string): Promise<A2ARequestRecord> {
  return coreTenantApiGet(`/api/legacy/a2a/requests/${encodeURIComponent(requestId)}`);
}

export function listLegacyA2ARequestsForTask(taskId: string): Promise<A2ARequestRecord[]> {
  return coreTenantApiGet(`/api/legacy/a2a/tasks/${encodeURIComponent(taskId)}/requests`, { limit: 100 });
}

export function getLegacyA2ARequestStateHistory(requestId: string): Promise<unknown[]> {
  return coreTenantApiGet(`/api/legacy/a2a/requests/${encodeURIComponent(requestId)}/state-history`, { limit: 100 });
}
