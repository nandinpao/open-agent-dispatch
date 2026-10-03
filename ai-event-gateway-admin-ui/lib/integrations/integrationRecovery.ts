import { coreTenantApiGet, coreTenantApiPost } from '@/lib/api/client';
import type { CoreAdapterAction } from '@/lib/types/core';

export interface IntegrationRecoveryFailure {
  auditId?: string;
  actionId?: string;
  taskId?: string;
  tenantId?: string;
  sourceSystemId?: string;
  taskType?: string;
  connectionId?: string;
  projectMappingId?: string;
  externalProjectId?: string;
  providerStatusCode?: number;
  providerFailureCode?: string;
  providerHealthImpact?: string;
  providerOutcomeCertainty?: string;
  category: string;
  retryPolicy: string;
  message?: string;
  correlationId?: string;
  attemptCount?: number;
  occurredAt?: string;
  actionStatus?: string;
  nextAttemptAt?: string;
  current?: boolean;
}

export interface IntegrationRecoverySnapshot {
  pending: number;
  retryWaiting: number;
  failed: number;
  completed: number;
  needsAttention: number;
  uncertain: number;
  providerState: string;
  nextRetryAt?: string;
  latestFailure?: IntegrationRecoveryFailure | null;
  failures: IntegrationRecoveryFailure[];
}

export interface IntegrationRecoveryPreflight {
  actionId: string;
  retryAllowed: boolean;
  category: string;
  failureCode?: string;
  tenantId?: string;
  connectionId?: string;
  projectMappingId?: string;
  metadataSnapshotId?: string;
  checkedAt?: string;
  checks: string[];
  blockers: string[];
  nextAction: string;
}

export function loadIntegrationRecovery(limit = 200) {
  return coreTenantApiGet<IntegrationRecoverySnapshot>('/api/adapter-actions/integration-recovery', { limit });
}

export function preflightIntegrationRecovery(actionId: string) {
  return coreTenantApiPost<IntegrationRecoveryPreflight>(`/api/adapter-actions/${encodeURIComponent(actionId)}/recovery-preflight`, {});
}

export function retryIntegrationRecovery(actionId: string, reason: string) {
  return coreTenantApiPost<CoreAdapterAction>(`/api/adapter-actions/${encodeURIComponent(actionId)}/recovery-retry`, { reason });
}

export function recoveryCategoryLabel(category?: string) {
  const value = String(category ?? '').toUpperCase();
  if (value === 'REQUEST_VALIDATION') return 'Request validation';
  if (value === 'AUTHENTICATION') return 'Authentication';
  if (value === 'PERMISSION') return 'Provider permission';
  if (value === 'RATE_LIMIT') return 'Rate limit';
  if (value === 'PROVIDER_UNAVAILABLE') return 'Provider unavailable';
  if (value === 'OUTCOME_UNCERTAIN') return 'Outcome uncertain';
  if (value === 'RESOURCE_NOT_FOUND') return 'Provider resource not found';
  if (value === 'CONFLICT') return 'Provider conflict';
  if (value === 'EXECUTOR_UNAVAILABLE') return 'Executor unavailable';
  return 'Needs review';
}

export function recoveryGuidance(category?: string) {
  const value = String(category ?? '').toUpperCase();
  if (value === 'REQUEST_VALIDATION') return 'Repair or validate the governed Source/Task mapping and provider field metadata before retrying.';
  if (value === 'AUTHENTICATION') return 'Validate the technical Service Account and active credential in Integration Configuration.';
  if (value === 'PERMISSION') return 'Check provider-side project membership and CREATE/UPDATE permission for the configured Service Account.';
  if (value === 'RATE_LIMIT') return 'Honor the provider retry window; repeated manual retries can extend throttling.';
  if (value === 'OUTCOME_UNCERTAIN') return 'Do not retry blindly. Reconcile whether the provider already applied the mutating operation.';
  if (value === 'PROVIDER_UNAVAILABLE') return 'Re-run Recovery Preflight to verify the provider is reachable before retrying.';
  if (value === 'RESOURCE_NOT_FOUND') return 'Verify the provider project/tracker still exists and that the published mapping targets the correct resource.';
  if (value === 'CONFLICT') return 'Review the provider state and mapping before deciding whether the operation is still valid.';
  return 'Review the execution evidence before retrying.';
}

export function integrationConfigurationHref(failure?: IntegrationRecoveryFailure | null) {
  const query = new URLSearchParams();
  if (failure?.sourceSystemId) query.set('sourceSystem', failure.sourceSystemId);
  if (failure?.taskType) query.set('taskType', failure.taskType);
  if (failure?.projectMappingId) query.set('mappingId', failure.projectMappingId);
  const suffix = query.toString();
  return `/settings/integrations${suffix ? `?${suffix}` : ''}`;
}
