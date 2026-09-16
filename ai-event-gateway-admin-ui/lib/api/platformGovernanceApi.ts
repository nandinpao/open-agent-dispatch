import { ApiError } from '@/lib/api/errors';
import { apiRequestFor, type HttpMethod } from '@/lib/api/client';
import type * as T from '@/lib/iam/types';
import { createIdempotencyKey } from '@/lib/utils/uuid';

const BASE = '/api/admin/access/platform';
type Options = { auditReason?: string; expectedVersion?: number; idempotencyKey?: string };
type GovernanceRequest = { method?: HttpMethod; body?: unknown; headers?: Record<string, string> };

function q(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : '';
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function throwIfProxyEnvelope(body: unknown, path: string): void {
  const envelope = asRecord(body);
  if (!envelope) return;
  const code = typeof envelope.code === 'string' ? envelope.code : '';
  const message = typeof envelope.message === 'string' ? envelope.message : '';
  if (!code.startsWith('ADMIN_PROXY_')) return;
  throw new ApiError(message || `Platform governance proxy failed for ${path}`, 502, body, code || undefined);
}

function contractError(path: string, expected: string, body: unknown): never {
  throw new ApiError(
    `Platform governance response contract mismatch for ${path}: expected ${expected}.`,
    502,
    body,
    'PLATFORM_GOVERNANCE_RESPONSE_CONTRACT_MISMATCH',
  );
}

async function request<R>(path: string, init: GovernanceRequest = {}, options: Options = {}): Promise<R> {
  const method = init.method ?? 'GET';
  const write = method !== 'GET';
  const headers: Record<string, string> = { ...(init.headers ?? {}) };
  if (write) headers['Idempotency-Key'] = options.idempotencyKey ?? createIdempotencyKey('platform-governance');
  if (options.auditReason) headers['X-Audit-Reason'] = options.auditReason;
  if (options.expectedVersion !== undefined) headers['If-Match'] = String(options.expectedVersion);

  const value = await apiRequestFor<R>('core', `${BASE}${path}`, {
    method,
    body: init.body,
    headers,
    requireStandardEnvelope: false,
  });
  throwIfProxyEnvelope(value, path);
  return value;
}

async function requestArray<R>(path: string): Promise<R[]> {
  const body = await request<unknown>(path);
  if (!Array.isArray(body)) contractError(path, 'an array', body);
  return body as R[];
}

const json = (method: HttpMethod, body: unknown): GovernanceRequest => ({ method, body });
const catalog = (path: string) => `/permission-catalog${path}`;
const readiness = (path: string) => `/permission-readiness${path}`;

export const platformGovernanceApi = {
  permissionCatalogRevisions: () => requestArray<T.PermissionCatalogRevision>(catalog('/revisions')),
  activePermissionCatalogRevision: () => request<T.PermissionCatalogRevision>(catalog('/active')),
  permissionCatalogRevision: (revisionId: string) => request<T.PermissionCatalogRevision>(catalog(`/revisions/${encodeURIComponent(revisionId)}`)),
  permissionCatalogDefinitions: (revisionId: string) => requestArray<T.PermissionCatalogDefinition>(catalog(`/revisions/${encodeURIComponent(revisionId)}/entries`)),
  permissionCatalogAliases: (revisionId: string) => requestArray<T.PermissionCatalogAlias>(catalog(`/revisions/${encodeURIComponent(revisionId)}/aliases`)),
  permissionCatalogDiff: (revisionId: string) => request<T.PermissionCatalogDiff>(catalog(`/revisions/${encodeURIComponent(revisionId)}/diff`)),
  permissionCatalogValidation: (revisionId: string) => request<T.PermissionCatalogValidation>(catalog(`/revisions/${encodeURIComponent(revisionId)}/validation`)),
  permissionCatalogPublications: (limit = 50) => requestArray<T.PermissionCatalogPublication>(catalog(`/publications${q({ limit })}`)),
  createPermissionCatalogDraft: (value: Record<string, unknown>, reason: string) => request<T.PermissionCatalogRevision>(catalog('/revisions'), json('POST', value), { auditReason: reason }),
  createPermissionCatalogDefinition: (revisionId: string, value: Record<string, unknown>, reason: string) => request<T.PermissionCatalogDefinition>(catalog(`/revisions/${encodeURIComponent(revisionId)}/entries`), json('POST', value), { auditReason: reason }),
  updatePermissionCatalogDefinition: (revisionId: string, permissionCode: string, value: Record<string, unknown>, version: number, reason: string) => request<T.PermissionCatalogDefinition>(catalog(`/revisions/${encodeURIComponent(revisionId)}/entries/${encodeURIComponent(permissionCode)}`), json('PUT', value), { expectedVersion: version, auditReason: reason }),
  deletePermissionCatalogDefinition: (revisionId: string, permissionCode: string, version: number, reason: string) => request<void>(catalog(`/revisions/${encodeURIComponent(revisionId)}/entries/${encodeURIComponent(permissionCode)}`), { method: 'DELETE' }, { expectedVersion: version, auditReason: reason }),
  createPermissionCatalogAlias: (revisionId: string, value: Record<string, unknown>, reason: string) => request<T.PermissionCatalogAlias>(catalog(`/revisions/${encodeURIComponent(revisionId)}/aliases`), json('POST', value), { auditReason: reason }),
  updatePermissionCatalogAlias: (revisionId: string, aliasCode: string, value: Record<string, unknown>, version: number, reason: string) => request<T.PermissionCatalogAlias>(catalog(`/revisions/${encodeURIComponent(revisionId)}/aliases/${encodeURIComponent(aliasCode)}`), json('PUT', value), { expectedVersion: version, auditReason: reason }),
  deletePermissionCatalogAlias: (revisionId: string, aliasCode: string, version: number, reason: string) => request<void>(catalog(`/revisions/${encodeURIComponent(revisionId)}/aliases/${encodeURIComponent(aliasCode)}`), { method: 'DELETE' }, { expectedVersion: version, auditReason: reason }),
  publishPermissionCatalog: (revisionId: string, version: number, reason: string) => request<T.PermissionCatalogPublicationResult>(catalog(`/revisions/${encodeURIComponent(revisionId)}/publish`), json('POST', { confirm: true }), { expectedVersion: version, auditReason: reason }),

  entryPointReadinessSummary: () => request<T.EntryPointBurnDownSummary>(readiness('/summary')),
  entryPoints: (text = '', state = '', ownerModule = '', limit = 200) => requestArray<T.EntryPointAuthority>(readiness(`/entry-points${q({ text, state, ownerModule, limit })}`)),
  updateEntryPoint: (id: string, value: Record<string, unknown>, version: number, reason: string) => request<T.EntryPointAuthority>(readiness(`/entry-points/${encodeURIComponent(id)}`), json('PUT', value), { expectedVersion: version, auditReason: reason }),
  legacyAuthorityMappings: (status = '', limit = 200) => requestArray<T.LegacyAuthorityMapping>(readiness(`/legacy-mappings${q({ status, limit })}`)),
  updateLegacyAuthorityMapping: (id: string, value: Record<string, unknown>, version: number, reason: string) => request<T.LegacyAuthorityMapping>(readiness(`/legacy-mappings/${encodeURIComponent(id)}`), json('PUT', value), { expectedVersion: version, auditReason: reason }),
  entryPointBypasses: (status = '', limit = 200) => requestArray<T.EntryPointBypass>(readiness(`/bypasses${q({ status, limit })}`)),
  createEntryPointBypass: (value: Record<string, unknown>, reason: string) => request<T.EntryPointBypass>(readiness('/bypasses'), json('POST', value), { auditReason: reason }),
  revokeEntryPointBypass: (id: string, version: number, reason: string) => request<T.EntryPointBypass>(readiness(`/bypasses/${encodeURIComponent(id)}/revoke`), json('POST', { reason }), { expectedVersion: version, auditReason: reason }),
  applicationPermissionManifests: (limit = 50) => requestArray<T.ApplicationPermissionManifest>(readiness(`/manifests${q({ limit })}`)),
  applicationPermissionManifest: (id: string) => request<T.ApplicationPermissionManifest>(readiness(`/manifests/${encodeURIComponent(id)}`)),
  applicationPermissionManifestEntries: (id: string, driftStatus = '', limit = 300) => requestArray<T.ApplicationPermissionManifestEntry>(readiness(`/manifests/${encodeURIComponent(id)}/entries${q({ driftStatus, limit })}`)),
  applicationPermissionManifestDrift: (id: string) => request<T.PermissionManifestDriftSummary>(readiness(`/manifests/${encodeURIComponent(id)}/drift`)),
  permissionCoverageEvidence: (limit = 100) => requestArray<T.PermissionCoverageEvidence>(readiness(`/coverage-evidence${q({ limit })}`)),
  registerApplicationPermissionManifest: (value: Record<string, unknown>, reason: string) => request<T.ApplicationPermissionManifest>(readiness('/manifests'), json('POST', value), { auditReason: reason }),
  shadowObservationSummary: (tenantId = '', domainCode = '') => request<T.ShadowObservationSummary>(readiness(`/shadow/summary${q({ tenantId, domainCode })}`)),
  shadowObservations: (tenantId = '', domainCode = '', category = '', limit = 200) => requestArray<T.ShadowObservation>(readiness(`/shadow/observations${q({ tenantId, domainCode, category, limit })}`)),
  mismatchCases: (tenantId = '', domainCode = '', status = '', limit = 200) => requestArray<T.ShadowMismatchCase>(readiness(`/mismatch-cases${q({ tenantId, domainCode, status, limit })}`)),
  createMismatchCase: (value: Record<string, unknown>, reason: string) => request<T.ShadowMismatchCase>(readiness('/mismatch-cases'), json('POST', value), { auditReason: reason }),
  updateMismatchCase: (id: string, value: Record<string, unknown>, version: number, reason: string) => request<T.ShadowMismatchCase>(readiness(`/mismatch-cases/${encodeURIComponent(id)}`), json('PUT', value), { expectedVersion: version, auditReason: reason }),
  createMismatchWaiver: (id: string, value: Record<string, unknown>, reason: string) => request<T.ShadowMismatchWaiver>(readiness(`/mismatch-cases/${encodeURIComponent(id)}/waivers`), json('POST', value), { auditReason: reason }),
  addMismatchRegression: (id: string, value: Record<string, unknown>, reason: string) => request<T.ShadowRegressionEvidence>(readiness(`/mismatch-cases/${encodeURIComponent(id)}/regressions`), json('POST', value), { auditReason: reason }),
  domainReadiness: (tenantId: string, domainCode = '', limit = 50) => requestArray<T.DomainReadinessEvidence>(readiness(`/domain-readiness${q({ tenantId, domainCode, limit })}`)),
  evaluateDomainReadiness: (value: Record<string, unknown>, reason: string) => request<T.DomainReadinessEvidence>(readiness('/domain-readiness/evaluate'), json('POST', value), { auditReason: reason }),
  phase6Eligibility: (tenantId: string, limit = 50) => requestArray<T.Phase6EligibilityEvidence>(readiness(`/phase6-eligibility${q({ tenantId, limit })}`)),
  evaluatePhase6Eligibility: (tenantId: string, reason: string) => request<T.Phase6EligibilityEvidence>(readiness('/phase6-eligibility/evaluate'), json('POST', { tenantId }), { auditReason: reason }),
  shadowPipelineReadiness: () => request<T.ShadowPipelineReadiness>(readiness('/shadow/pipeline')),
  shadowStorageReadiness: () => request<T.ShadowStorageReadiness>(readiness('/shadow/storage')),
  shadowRetentionRuns: (limit = 50) => requestArray<T.ShadowRetentionRun>(readiness(`/shadow/retention-runs${q({ limit })}`)),
  processShadowPipeline: (batchSize: number, reason: string) => request<string>(readiness('/shadow/pipeline/process'), json('POST', { batchSize }), { auditReason: reason }),
  runShadowRetention: (reason: string) => request<T.ShadowRetentionRun>(readiness('/shadow/storage/retention'), json('POST', {}), { auditReason: reason }),
  phase5RuntimeCertificationEvidence: (limit = 50) => requestArray<T.Phase5RuntimeCertificationEvidence>(readiness(`/runtime-certification${q({ limit })}`)),
  generatePhase5RuntimeCertificationEvidence: (value: Record<string, unknown>, reason: string) => request<T.Phase5RuntimeCertificationEvidence>(readiness('/runtime-certification'), json('POST', value), { auditReason: reason }),
};
