import { coreApiGet, coreApiPost, coreApiPut, requireCoreTenantContext } from '@/lib/api/coreClient';
import { coreAdminEndpoints } from '@/lib/api/endpoints';
import type {
  CoreDispatchTaskDefinition,
  CoreDispatchTaskDefinitionImpactPreview,
  CoreDispatchTaskDefinitionReviewCommand,
  CoreEventIntakeDecisionResponse,
  CoreEventIntakeEnvelope,
} from '@/lib/types/core';
import type {
  CoreDispatchContractBootstrapRequest,
  CoreDispatchContractBootstrapResponse,
  CoreDispatchContractChainInspectionRequest,
  CoreDispatchContractChainInspectionResponse,
  CoreDispatchContractReadinessRequest,
  CoreDispatchContractReadinessResponse,
  CoreDispatchContractTestTaskRequest,
  CoreDispatchContractTestTaskResponse,
  CoreDispatchContractTraceRequest,
  CoreDispatchContractTraceResponse,
  CoreDispatchReadinessEvaluationRequest,
  CoreDispatchReadinessEvaluationResult,
  CoreDispatchReadinessTemplates,
  CoreDispatchRecipe,
  CoreDispatchRecipeEvaluationRequest,
  CoreDispatchRecipeEvaluationResult,
  CoreDispatchSourceSystemOption,
  CoreTaskCapabilityResolveRequest,
  CoreTaskCapabilityResolveResult,
  CoreTaskDispatchContractResolveRequest,
  CoreTaskDispatchContractResolveResult,
} from '@/lib/types/domains/task';

type PageLike<T> = T[] | { content?: T[]; items?: T[]; records?: T[]; rows?: T[]; data?: T[] };
const toList = <T>(value: PageLike<T>): T[] => Array.isArray(value) ? value : value.content ?? value.items ?? value.records ?? value.rows ?? value.data ?? [];
const coreGetList = async <T>(path: string): Promise<T[]> => toList(await coreApiGet<PageLike<T>>(path));
const requireTenantId = (tenantId: string | undefined): string => requireCoreTenantContext(tenantId);

/**
 * Physical Task dispatch-contract/readiness API ownership.
 *
 * coreAdminApi composes this client for compatibility; Current Task surfaces
 * should depend on taskAdminApi instead of the all-domain facade.
 */
export const taskContractAdminApi = {
  getDispatchTaskDefinitions(status?: string | null, tenantId = ''): Promise<CoreDispatchTaskDefinition[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set('tenantId', tenantId);
    if (status) query.set('status', status);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return coreGetList<CoreDispatchTaskDefinition>(`${coreAdminEndpoints.dispatchTaskDefinitions}${suffix}`);
  },

  getDispatchContractSourceSystems(tenantId = '', limit = 500): Promise<CoreDispatchSourceSystemOption[]> {
    const scopedTenantId = requireTenantId(tenantId);
    const query = new URLSearchParams({ tenantId: scopedTenantId, limit: String(limit) });
    return coreGetList<CoreDispatchSourceSystemOption>(`${coreAdminEndpoints.dispatchContractSourceSystems}?${query.toString()}`);
  },

  bootstrapDispatchContract(body: CoreDispatchContractBootstrapRequest): Promise<CoreDispatchContractBootstrapResponse> {
    return coreApiPost(coreAdminEndpoints.dispatchContractBootstrap, body);
  },

  checkDispatchContractReadiness(body: CoreDispatchContractReadinessRequest): Promise<CoreDispatchContractReadinessResponse> {
    return coreApiPost(coreAdminEndpoints.dispatchContractReadiness, body);
  },

  inspectDispatchContract(body: CoreDispatchContractChainInspectionRequest): Promise<CoreDispatchContractChainInspectionResponse> {
    return coreApiPost(coreAdminEndpoints.dispatchContractInspect, body);
  },

  traceDispatchContract(body: CoreDispatchContractTraceRequest): Promise<CoreDispatchContractTraceResponse> {
    return coreApiPost(coreAdminEndpoints.dispatchContractTrace, body);
  },

  createDispatchContractTestTask(body: CoreDispatchContractTestTaskRequest): Promise<CoreDispatchContractTestTaskResponse> {
    return coreApiPost(coreAdminEndpoints.dispatchContractTestTask, body);
  },

  getDispatchTaskDefinitionImpactPreview(definitionId: string, action?: string | null, tenantId = ''): Promise<CoreDispatchTaskDefinitionImpactPreview> {
    const query = new URLSearchParams();
    if (tenantId) query.set('tenantId', tenantId);
    if (action) query.set('action', action);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return coreApiGet(`${coreAdminEndpoints.dispatchTaskDefinitionImpactPreview(definitionId)}${suffix}`);
  },

  activateDispatchTaskDefinition(definitionId: string, body: CoreDispatchTaskDefinitionReviewCommand, tenantId = ''): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
    return coreApiPost(`${coreAdminEndpoints.dispatchTaskDefinitionActivate(definitionId)}${suffix}`, body);
  },

  retireDispatchTaskDefinition(definitionId: string, body: CoreDispatchTaskDefinitionReviewCommand, tenantId = ''): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
    return coreApiPost(`${coreAdminEndpoints.dispatchTaskDefinitionRetire(definitionId)}${suffix}`, body);
  },

  mergeDispatchTaskDefinition(definitionId: string, body: CoreDispatchTaskDefinitionReviewCommand, tenantId = ''): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
    return coreApiPost(`${coreAdminEndpoints.dispatchTaskDefinitionMerge(definitionId)}${suffix}`, body);
  },

  upsertDispatchTaskDefinition(definitionId: string, body: CoreDispatchTaskDefinition, tenantId = ''): Promise<CoreDispatchTaskDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
    return coreApiPut(`${coreAdminEndpoints.dispatchTaskDefinition(definitionId)}${suffix}`, body);
  },

  resolveDispatchContract(body: CoreTaskDispatchContractResolveRequest): Promise<CoreTaskDispatchContractResolveResult> {
    return coreApiPost(coreAdminEndpoints.dispatchContractResolve, body);
  },

  listDispatchRecipes(domain?: string, enabledOnly = true): Promise<CoreDispatchRecipe[]> {
    const query = new URLSearchParams();
    if (domain) query.set('domain', domain);
    query.set('enabledOnly', String(enabledOnly));
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return coreGetList<CoreDispatchRecipe>(`${coreAdminEndpoints.dispatchRecipes}${suffix}`);
  },

  getDispatchRecipeTemplates(): Promise<CoreDispatchReadinessTemplates> {
    return coreApiGet(coreAdminEndpoints.dispatchRecipeTemplates);
  },

  evaluateDispatchRecipe(recipeCode: string, body: CoreDispatchRecipeEvaluationRequest): Promise<CoreDispatchRecipeEvaluationResult> {
    return coreApiPost(coreAdminEndpoints.dispatchRecipeEvaluate(recipeCode), body);
  },

  resolveTaskCapabilities(body: CoreTaskCapabilityResolveRequest): Promise<CoreTaskCapabilityResolveResult> {
    return coreApiPost(coreAdminEndpoints.taskCapabilityResolve, body);
  },

  evaluateDispatchReadiness(body: CoreDispatchReadinessEvaluationRequest): Promise<CoreDispatchReadinessEvaluationResult> {
    return coreApiPost(coreAdminEndpoints.dispatchReadinessEvaluate, body);
  },

  getDispatchReadinessTemplates(): Promise<CoreDispatchReadinessTemplates> {
    return coreApiGet(coreAdminEndpoints.dispatchReadinessTemplates);
  },

  createDispatchReadinessTestEvent(body: CoreEventIntakeEnvelope): Promise<CoreEventIntakeDecisionResponse> {
    return coreApiPost(coreAdminEndpoints.eventIntake, body);
  },
} as const;
