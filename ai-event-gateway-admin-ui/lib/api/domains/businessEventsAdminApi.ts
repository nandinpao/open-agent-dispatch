import { coreTenantApiGet } from '@/lib/api/coreClient';

export interface BusinessEventView {
  eventId: string;
  tenantId: string;
  sourceSystem?: string | null;
  eventType?: string | null;
  eventStage?: string | null;
  correlationId?: string | null;
  normalizedMessage?: string | null;
  decisionType?: string | null;
  duplicate: boolean;
  occurrenceCount: number;
  incidentId?: string | null;
  occurredAt?: string | null;
  decidedAt?: string | null;
  ownerDepartmentId?: string | null;
  ownerGroupId?: string | null;
  scopeStatus: 'TENANT_OWNED' | 'INHERITED' | 'UNRESOLVED';
  scopeSourceVersion?: number | null;
  scopeInheritedAt?: string | null;
}

export interface BusinessEventPayloadView { eventId: string; payload: Record<string, unknown>; }

export const businessEventsAdminApi = {
  list(params?: { sourceSystem?: string; eventType?: string; limit?: number }) {
    return coreTenantApiGet<BusinessEventView[]>('/admin/business-events', params);
  },
  detail(eventId: string) {
    return coreTenantApiGet<BusinessEventView>(`/admin/business-events/${encodeURIComponent(eventId)}`);
  },
  payload(eventId: string) {
    return coreTenantApiGet<BusinessEventPayloadView>(`/admin/business-events/${encodeURIComponent(eventId)}/payload`);
  },
} as const;
