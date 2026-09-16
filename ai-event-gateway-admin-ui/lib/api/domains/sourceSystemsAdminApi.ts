import { coreApiDelete, coreApiGet, coreApiPost, coreApiPut, requireCoreTenantContext } from '@/lib/api/coreClient';
import { coreAdminEndpoints } from '@/lib/api/endpoints';
import type { CoreDispatchFlowView, CoreSourceSystem, CoreSourceSystemCommand, CoreWorkloadSourceRegistration, CoreWorkloadSourceRegistrationCommand } from '@/lib/types/core';

type PageLike<T> = T[] | { content?: T[]; items?: T[]; records?: T[]; rows?: T[]; data?: T[] };
const toList = <T>(value: PageLike<T>): T[] => Array.isArray(value) ? value : value.content ?? value.items ?? value.records ?? value.rows ?? value.data ?? [];
const coreGetList = async <T>(path: string): Promise<T[]> => toList(await coreApiGet<PageLike<T>>(path));
const tenant = (tenantId: string | undefined): string => requireCoreTenantContext(tenantId);

/** Physical Source System administration client. coreAdminApi only composes this boundary for compatibility. */
export const sourceSystemsAdminApi = {
  getSourceSystems(tenantId = ''): Promise<CoreSourceSystem[]> {
    const query = new URLSearchParams({ tenantId: tenant(tenantId) });
    return coreGetList(`${coreAdminEndpoints.sourceSystems}?${query.toString()}`);
  },
  createSourceSystem(tenantId = '', body: CoreSourceSystemCommand): Promise<CoreSourceSystem> {
    const scopedTenantId = tenant(tenantId);
    return coreApiPost(`${coreAdminEndpoints.sourceSystems}?tenantId=${encodeURIComponent(scopedTenantId)}`, { ...body, tenantId: scopedTenantId });
  },
  updateSourceSystem(tenantId = '', sourceSystemId: string, body: CoreSourceSystemCommand): Promise<CoreSourceSystem> {
    const scopedTenantId = tenant(tenantId);
    return coreApiPut(`${coreAdminEndpoints.sourceSystem(sourceSystemId)}?tenantId=${encodeURIComponent(scopedTenantId)}`, { ...body, tenantId: scopedTenantId, sourceSystemId });
  },
  retireSourceSystem(tenantId = '', sourceSystemId: string): Promise<{ sourceSystemId: string; status: string }> {
    return coreApiDelete(`${coreAdminEndpoints.sourceSystem(sourceSystemId)}?tenantId=${encodeURIComponent(tenant(tenantId))}`);
  },
  getSourceRegistrations(tenantId = '', sourceSystemId: string): Promise<CoreWorkloadSourceRegistration[]> {
    return coreGetList(`${coreAdminEndpoints.sourceSystemRegistrations(sourceSystemId)}?tenantId=${encodeURIComponent(tenant(tenantId))}`);
  },
  createSourceRegistration(tenantId = '', sourceSystemId: string, body: CoreWorkloadSourceRegistrationCommand): Promise<CoreWorkloadSourceRegistration> {
    return coreApiPost(`${coreAdminEndpoints.sourceSystemRegistrations(sourceSystemId)}?tenantId=${encodeURIComponent(tenant(tenantId))}`, body);
  },
  updateSourceRegistration(tenantId = '', sourceSystemId: string, registrationId: string, body: CoreWorkloadSourceRegistrationCommand): Promise<CoreWorkloadSourceRegistration> {
    return coreApiPut(`${coreAdminEndpoints.sourceSystemRegistration(sourceSystemId, registrationId)}?tenantId=${encodeURIComponent(tenant(tenantId))}`, body);
  },
  retireSourceRegistration(tenantId = '', sourceSystemId: string, registrationId: string): Promise<{ sourceRegistrationId: string; status: string }> {
    return coreApiDelete(`${coreAdminEndpoints.sourceSystemRegistration(sourceSystemId, registrationId)}?tenantId=${encodeURIComponent(tenant(tenantId))}`);
  },
  getDispatchFlows(tenantId = '', sourceSystem?: string | null): Promise<CoreDispatchFlowView[]> {
    const query = new URLSearchParams({ tenantId: tenant(tenantId) });
    if (sourceSystem) query.set('sourceSystem', sourceSystem);
    return coreGetList(`${coreAdminEndpoints.dispatchFlows}?${query.toString()}`);
  },
  getDispatchFlowsForAgent(tenantId: string, agentId: string): Promise<CoreDispatchFlowView[]> {
    return coreGetList(`${coreAdminEndpoints.dispatchFlowsByAgent(agentId)}?tenantId=${encodeURIComponent(tenant(tenantId))}`);
  },
} as const;
