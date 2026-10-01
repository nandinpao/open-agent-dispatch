import { coreApiGet, coreApiPost } from '@/lib/api/coreClient';

export type RuntimeConfigurationApplicationStatus =
  | 'STARTUP_FALLBACK'
  | 'PUBLISHED_APPLYING'
  | 'APPLIED'
  | 'PARTIALLY_APPLIED'
  | 'CONFIGURATION_INCOMPLETE'
  | 'FAILED'
  | 'NOT_MIGRATED';

export type RuntimeConfigurationDataType =
  | 'BOOLEAN'
  | 'INTEGER'
  | 'LONG'
  | 'DECIMAL'
  | 'STRING'
  | 'DURATION'
  | 'ENUM'
  | 'URI'
  | 'JSON';

export interface RuntimeConfigurationSettingSummary {
  key: string;
  label: string;
  description: string;
  runtimeValue: unknown;
  desiredValue: unknown;
  unit: string;
  risk: string;
  effect: string;
  applicationStatus: RuntimeConfigurationApplicationStatus;
}

export interface RuntimeConfigurationCategory {
  id: string;
  title: string;
  description: string;
  lifecycle: 'PLANNED' | 'PILOT' | 'MIGRATING' | 'MIGRATED' | 'MIXED';
  applicationStatus: RuntimeConfigurationApplicationStatus;
  settings: RuntimeConfigurationSettingSummary[];
  definitionCount: number;
  migrationAuthorizedCount: number;
  editableCount: number;
  configSetKeys: string[];
  readOnly: boolean;
}

export interface RuntimeConfigurationRecentChange {
  category: string;
  configSetKey: string;
  revisionId: string;
  sequenceNo: number;
  state: string;
  reason: string;
  actor?: string | null;
  publishedAt?: string | null;
}

export interface RuntimeConfigurationMigrationCoverage {
  runtimeTargetDefinitions: number;
  migrationAuthorized: number;
  runtimeAuthoritative: number;
  adminEditable: number;
  proposed: number;
  pendingConsumerMigration: number;
  pendingCutover: number;
}

export interface RuntimeConfigurationOverview {
  environment: string;
  environmentLabel: string;
  health: 'HEALTHY' | 'APPLYING' | 'ACTION_REQUIRED';
  activeChanges: number;
  appliedNodes: number;
  knownNodes: number;
  pendingApproval: number;
  migrationCoverage: RuntimeConfigurationMigrationCoverage;
  categories: RuntimeConfigurationCategory[];
  recentChanges: RuntimeConfigurationRecentChange[];
  governanceNotice: string;
}

export interface RuntimeConfigurationSettingDetail {
  categoryId: string;
  key: string;
  label: string;
  description: string;
  recommended: string;
  unit: string;
  dataType: RuntimeConfigurationDataType;
  risk: string;
  effect: string;
  impactPositive: string;
  impactTradeoff: string;
  runtimeValue: unknown;
  localEffectiveValue: unknown;
  desiredValue: unknown;
  valuePresentInActiveRevision: boolean;
  authoritySource: 'STARTUP_FALLBACK' | 'RUNTIME_CONFIGURATION';
  effectiveSource: 'STARTUP_FALLBACK' | 'LOCAL_RUNTIME_SNAPSHOT' | 'NONE';
  configurationState: 'HEALTHY' | 'INCOMPLETE' | 'DRIFT' | 'FAILED';
  convergenceState: RuntimeConfigurationApplicationStatus;
  migrationState: 'PROPOSED' | 'MIGRATION_READY' | 'MIGRATED' | 'LEGACY_RETIRED';
  authorityMode: 'STARTUP_ONLY' | 'DUAL_READ' | 'RUNTIME_ONLY';
  authority: string;
  owner: string;
  scope: string;
  mutability: string;
  configSetKey: string;
  configSetId?: string | null;
  activeRevisionId?: string | null;
  desiredRevisionId?: string | null;
  localAppliedRevisionId?: string | null;
  applicationStatus: RuntimeConfigurationApplicationStatus;
  appliedNodes: number;
  knownNodes: number;
  editable: boolean;
  validation: Record<string, unknown>;
  errorCode?: string | null;
  errorDetail?: string | null;
  governanceNotice: string;
}

export interface RuntimeConfigurationRevisionView {
  revisionId: string;
  configSetId: string;
  sequenceNo: number;
  baseRevisionId?: string | null;
  rollbackOfRevisionId?: string | null;
  restoreSourceRevisionId?: string | null;
  state: 'DRAFT' | 'VALIDATED' | 'PENDING_APPROVAL' | 'APPROVED' | 'PUBLISHED' | 'SUPERSEDED' | 'REJECTED' | 'CANCELLED';
  reason: string;
  createdBy: string;
  createdAt: string;
  submittedBy?: string | null;
  submittedAt?: string | null;
  approvedBy?: string | null;
  approvedAt?: string | null;
  publishedBy?: string | null;
  publishedAt?: string | null;
}

export interface RuntimeConfigurationEmergencyOverrideView {
  overrideId: string;
  configSetId: string;
  definitionKey: string;
  baseRevisionId: string;
  value: unknown;
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED';
  reason: string;
  createdBy: string;
  createdAt: string;
  expiresAt: string;
  revokedBy?: string | null;
  revokedAt?: string | null;
  revokeReason?: string | null;
}

export interface RuntimeConfigurationGovernanceOverview {
  pendingRevisions: RuntimeConfigurationRevisionView[];
  activeEmergencyOverrides: RuntimeConfigurationEmergencyOverrideView[];
  notice: string;
}

export type RuntimeConfigurationNodeState = 'NOT_SEEN' | 'DESIRED' | 'DISTRIBUTED' | 'APPLIED' | 'FAILED' | 'STALE';

export interface RuntimeConfigurationNodeApplyView {
  nodeId: string;
  nodeRole: 'CORE' | 'GATEWAY' | 'WORKER';
  nodeInstanceId: string;
  activeRevisionId?: string | null;
  desiredRevisionId?: string | null;
  appliedRevisionId?: string | null;
  desiredFingerprint?: string | null;
  appliedFingerprint?: string | null;
  state: RuntimeConfigurationNodeState;
  matchesDesired: boolean;
  lastSeenAt?: string | null;
  appliedAt?: string | null;
  errorCode?: string | null;
  errorDetail?: string | null;
  authorityRuntimeState?: RuntimeConfigurationAuthorityRuntimeState | null;
  snapshotExpiresAt?: string | null;
  authorityObservedAt?: string | null;
}

export interface RuntimeConfigurationApplyStateView {
  configSetId?: string | null;
  activeRevisionId?: string | null;
  desiredFingerprint?: string | null;
  requiredNodes: number;
  appliedNodes: number;
  notSeenNodes: number;
  staleNodes: number;
  failedNodes: number;
  nodes: RuntimeConfigurationNodeApplyView[];
}


export type RuntimeConfigurationAuthorityRuntimeState = 'ACTIVE' | 'STALE_LKG' | 'EXPIRED' | 'INVALID' | 'MISSING';

export interface RuntimeConfigurationCutoverNodeStatus {
  nodeId: string;
  nodeRole: string;
  supportedAuthorityContractVersion: number;
  applyState: string;
  appliedRevisionId?: string | null;
  snapshotFingerprint?: string | null;
  authorityRuntimeState?: RuntimeConfigurationAuthorityRuntimeState | null;
  snapshotExpiresAt?: string | null;
  authorityObservedAt?: string | null;
  converged: boolean;
}

export interface RuntimeConfigurationCutoverKeyStatus {
  key: string;
  governanceStatus: string;
  consumerContract: string;
}

export interface RuntimeConfigurationCutoverStatus {
  setKey: string;
  configSetId: string;
  environment: string;
  activeRevisionId: string;
  phase: 'READY_TO_PREPARE' | 'NOT_READY' | 'PREPARED_AWAITING_CONVERGENCE' | 'READY_TO_FINALIZE' | 'ALREADY_MIGRATED' | 'MIGRATED_WITH_DRIFT';
  cutoverId?: string | null;
  expectedSnapshotFingerprint: string;
  requiredKeys: string[];
  blockers: string[];
  keys: RuntimeConfigurationCutoverKeyStatus[];
  nodes: RuntimeConfigurationCutoverNodeStatus[];
  requiredNodeCount: number;
  convergedNodeCount: number;
}


export interface RuntimeConfigurationCutoverWaveMemberStatus {
  setKey: string;
  sequenceNo: number;
  expectedRuntimeKeyCount: number;
  actualRuntimeKeyCount: number;
  configSetId: string;
  phase: string;
  expectedSnapshotFingerprint: string;
  requiredNodeCount: number;
  convergedNodeCount: number;
  blockers: string[];
}

export interface RuntimeConfigurationCutoverWaveCertification {
  certificationId: string;
  waveId: string;
  status: 'PASS';
  authorityContractVersion: number;
  configSetCount: number;
  runtimeKeyCount: number;
  requiredNodeCount: number;
  convergedNodeCount: number;
  evidenceJson: string;
  certifiedBy: string;
  reason: string;
  certifiedAt: string;
}


export interface RuntimeConfigurationCutoverWaveSafetyAttestation {
  attestationId: string;
  waveId: string;
  status: 'PASS' | 'FAIL';
  evidenceJson: string;
  attestedBy: string;
  reason: string;
  capturedAt: string;
  expiresAt: string;
}

export interface RuntimeConfigurationCutoverWaveSafetyAttestationStatus {
  waveId: string;
  required: boolean;
  profile?: 'TASK_DISPATCH' | 'EXTERNAL_INTEGRATION_A2A' | 'PLATFORM_READINESS_RECOVERY' | null;
  latestAttestation?: RuntimeConfigurationCutoverWaveSafetyAttestation | null;
  blockers: string[];
}

export interface RuntimeConfigurationCutoverWaveStatus {
  waveId: string;
  sequenceNo: number;
  displayName: string;
  riskTier: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  requiredAuthorityContractVersion: number;
  releaseStage: string;
  phase: 'READY_TO_PREPARE' | 'NOT_READY' | 'PREPARED_AWAITING_CONVERGENCE' | 'READY_TO_FINALIZE' | 'FINALIZED' | 'FINALIZED_WITH_DRIFT' | 'INVALID_PARTIAL_FINALIZATION';
  blockers: string[];
  members: RuntimeConfigurationCutoverWaveMemberStatus[];
  configSetCount: number;
  runtimeKeyCount: number;
  requiredNodeCount: number;
  convergedNodeCount: number;
  predecessorWaveId?: string | null;
  predecessorCertificationStatus?: 'PASS' | null;
  predecessorCertificationId?: string | null;
  safetyAttestationRequired: boolean;
  safetyAttestationProfile?: 'TASK_DISPATCH' | 'EXTERNAL_INTEGRATION_A2A' | 'PLATFORM_READINESS_RECOVERY' | null;
  safetyAttestationStatus?: 'PASS' | 'FAIL' | null;
  safetyAttestationId?: string | null;
  safetyAttestationExpiresAt?: string | null;
}

export interface RuntimeConfigurationRollbackDiff {
  key: string;
  label: string;
  currentValue: unknown;
  restoreValue: unknown;
}

export interface RuntimeConfigurationRollbackPreview {
  configSetId: string;
  currentRevisionId?: string | null;
  restoreSourceRevisionId: string;
  restoreSourceSequence: number;
  changes: RuntimeConfigurationRollbackDiff[];
}

const BASE = '/api/platform/runtime-configuration';

export const runtimeConfigurationApi = {
  overview: () => coreApiGet<RuntimeConfigurationOverview>(`${BASE}/overview`),
  governance: () => coreApiGet<RuntimeConfigurationGovernanceOverview>(`${BASE}/governance`),
  detail: (key: string) => coreApiGet<RuntimeConfigurationSettingDetail>(`${BASE}/settings/${encodeURIComponent(key)}`),
  revisions: (key: string) => coreApiGet<RuntimeConfigurationRevisionView[]>(`${BASE}/settings/${encodeURIComponent(key)}/revisions`),
  applyState: (key: string) => coreApiGet<RuntimeConfigurationApplyStateView>(`${BASE}/settings/${encodeURIComponent(key)}/apply-state`),
  cutoverStatus: (configSetId: string) => coreApiGet<RuntimeConfigurationCutoverStatus>(`${BASE}/config-sets/${encodeURIComponent(configSetId)}/cutover`),
  prepareCutover: (configSetId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverStatus>(`${BASE}/config-sets/${encodeURIComponent(configSetId)}/cutover/prepare`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  finalizeCutover: (configSetId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverStatus>(`${BASE}/config-sets/${encodeURIComponent(configSetId)}/cutover/finalize`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  cancelCutover: (configSetId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverStatus>(`${BASE}/config-sets/${encodeURIComponent(configSetId)}/cutover/cancel`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  cutoverWaves: () => coreApiGet<RuntimeConfigurationCutoverWaveStatus[]>(`${BASE}/cutover-waves`),
  cutoverWave: (waveId: string) => coreApiGet<RuntimeConfigurationCutoverWaveStatus>(`${BASE}/cutover-waves/${encodeURIComponent(waveId)}`),
  prepareCutoverWave: (waveId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverWaveStatus>(`${BASE}/cutover-waves/${encodeURIComponent(waveId)}/prepare`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  finalizeCutoverWave: (waveId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverWaveStatus>(`${BASE}/cutover-waves/${encodeURIComponent(waveId)}/finalize`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  cancelCutoverWave: (waveId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverWaveStatus>(`${BASE}/cutover-waves/${encodeURIComponent(waveId)}/cancel`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  certifyCutoverWave: (waveId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverWaveCertification>(`${BASE}/cutover-waves/${encodeURIComponent(waveId)}/certify`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  cutoverWaveSafetyAttestation: (waveId: string) => coreApiGet<RuntimeConfigurationCutoverWaveSafetyAttestationStatus>(`${BASE}/cutover-waves/${encodeURIComponent(waveId)}/safety-attestation`),
  assessCutoverWaveSafety: (waveId: string, reason: string) => coreApiPost<RuntimeConfigurationCutoverWaveSafetyAttestationStatus>(`${BASE}/cutover-waves/${encodeURIComponent(waveId)}/safety-attestation`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  rollbackPreview: (key: string, restoreSourceRevisionId: string) =>
    coreApiGet<RuntimeConfigurationRollbackPreview>(`${BASE}/settings/${encodeURIComponent(key)}/rollback-preview?restoreSourceRevisionId=${encodeURIComponent(restoreSourceRevisionId)}`),
  requestChange: (key: string, value: unknown, expectedBaseRevisionId: string | null | undefined, reason: string) =>
    coreApiPost<RuntimeConfigurationRevisionView>(`${BASE}/settings/${encodeURIComponent(key)}/change-requests`, { value, expectedBaseRevisionId: expectedBaseRevisionId ?? null, reason }, { headers: { 'X-Audit-Reason': reason } }),
  approve: (revisionId: string, reason: string) => coreApiPost<RuntimeConfigurationRevisionView>(`${BASE}/revisions/${encodeURIComponent(revisionId)}/approve`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  reject: (revisionId: string, reason: string) => coreApiPost<RuntimeConfigurationRevisionView>(`${BASE}/revisions/${encodeURIComponent(revisionId)}/reject`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
  publishApproved: (revisionId: string, expectedBaseRevisionId: string | null | undefined, reason: string) =>
    coreApiPost<RuntimeConfigurationRevisionView>(`${BASE}/revisions/${encodeURIComponent(revisionId)}/publish`, { expectedBaseRevisionId: expectedBaseRevisionId ?? null, reason }, { headers: { 'X-Audit-Reason': reason } }),
  requestRollback: (key: string, restoreSourceRevisionId: string, reason: string) =>
    coreApiPost<RuntimeConfigurationRevisionView>(`${BASE}/settings/${encodeURIComponent(key)}/rollback-requests`, { restoreSourceRevisionId, reason }, { headers: { 'X-Audit-Reason': reason } }),
  emergencyOverride: (key: string, value: unknown, ttlMinutes: number, reason: string) =>
    coreApiPost<RuntimeConfigurationEmergencyOverrideView>(`${BASE}/settings/${encodeURIComponent(key)}/emergency-overrides`, { value, ttlMinutes, reason }, { headers: { 'X-Audit-Reason': reason } }),
  revokeEmergencyOverride: (overrideId: string, reason: string) =>
    coreApiPost<RuntimeConfigurationEmergencyOverrideView>(`${BASE}/emergency-overrides/${encodeURIComponent(overrideId)}/revoke`, { reason }, { headers: { 'X-Audit-Reason': reason } }),
};

export type ConfigurationInventoryGovernanceStatus =
  | 'DISCOVERED' | 'CLASSIFIED' | 'OWNER_REVIEWED' | 'ARCHITECTURE_APPROVED' | 'MIGRATION_READY' | 'MIGRATED' | 'LEGACY_RETIRED';

export interface ConfigurationInventorySummary {
  key: string;
  namespace: string;
  status: ConfigurationInventoryGovernanceStatus;
  sourceObservationHash: string;
  mutabilityFloor: string;
  reviewFlags: string[];
  advisoryClassification: Record<string, unknown>;
  domainOwner?: string | null;
  authorityClass?: string | null;
  scope?: string | null;
  risk?: string | null;
  mutability?: string | null;
  consumerContract?: string | null;
  version: number;
}

export interface ConfigurationInventoryGovernanceView {
  status: ConfigurationInventoryGovernanceStatus;
  sourceObservationHash: string;
  domainOwner?: string | null;
  authorityClass?: string | null;
  scope?: string | null;
  risk?: string | null;
  mutability?: string | null;
  consumerContract?: string | null;
  adminEditable?: boolean | null;
  requiresApproval?: boolean | null;
  classifiedBy?: string | null;
  classifiedAt?: string | null;
  ownerReviewedBy?: string | null;
  ownerReviewedAt?: string | null;
  architectureApprovedBy?: string | null;
  architectureApprovedAt?: string | null;
  migrationAuthorizedBy?: string | null;
  migrationAuthorizedAt?: string | null;
  reason?: string | null;
  version: number;
}

export interface ConfigurationMigrationReadiness { ready: boolean; blockers: string[]; }
export interface ConfigurationDefinitionEvidence {
  key: string; sourceRef: string; domainOwner: string; authorityClass: string; scope: string; risk: string;
  mutability: string; consumerContract: string; adminEditable: boolean; requiresApproval: boolean; migrationAuthorized: boolean;
}
export interface ConfigurationGovernanceEvent {
  eventId: number; eventType: string; fromStatus?: string | null; toStatus: string; actor: string; reason: string;
  sourceObservationHash: string; detail: Record<string, unknown>; createdAt: string;
}
export interface ConfigurationInventoryDetail {
  observation: ConfigurationInventorySummary;
  governance: ConfigurationInventoryGovernanceView;
  readiness: ConfigurationMigrationReadiness;
  definition?: ConfigurationDefinitionEvidence | null;
  events: ConfigurationGovernanceEvent[];
}
export interface ConfigurationClassificationRequest {
  authorityClass: string; domainOwner: string; scope: string; risk: string; mutability: string; consumerContract: string;
  adminEditable: boolean; requiresApproval: boolean; expectedSourceObservationHash: string; expectedVersion: number; reason: string;
}

export const configurationMigrationGovernanceApi = {
  list: (status?: string, namespace?: string, query?: string) => {
    const p = new URLSearchParams(); if (status) p.set('status', status); if (namespace) p.set('namespace', namespace); if (query) p.set('query', query); p.set('limit', '500');
    return coreApiGet<ConfigurationInventorySummary[]>(`${BASE}/inventory?${p.toString()}`);
  },
  detail: (key: string) => coreApiGet<ConfigurationInventoryDetail>(`${BASE}/inventory/${encodeURIComponent(key)}`),
  classify: (key: string, body: ConfigurationClassificationRequest) => coreApiPost<ConfigurationInventoryDetail>(`${BASE}/inventory/${encodeURIComponent(key)}/classify`, body, { headers: { 'X-Audit-Reason': body.reason } }),
  ownerReview: (key: string, expectedSourceObservationHash: string, expectedVersion: number, reason: string) => coreApiPost<ConfigurationInventoryDetail>(`${BASE}/inventory/${encodeURIComponent(key)}/owner-review`, { expectedSourceObservationHash, expectedVersion, reason }, { headers: { 'X-Audit-Reason': reason } }),
  architectureApprove: (key: string, expectedSourceObservationHash: string, expectedVersion: number, reason: string) => coreApiPost<ConfigurationInventoryDetail>(`${BASE}/inventory/${encodeURIComponent(key)}/architecture-approve`, { expectedSourceObservationHash, expectedVersion, reason }, { headers: { 'X-Audit-Reason': reason } }),
  authorizeMigration: (key: string, expectedSourceObservationHash: string, expectedVersion: number, reason: string) => coreApiPost<ConfigurationInventoryDetail>(`${BASE}/inventory/${encodeURIComponent(key)}/migration-authorize`, { expectedSourceObservationHash, expectedVersion, reason }, { headers: { 'X-Audit-Reason': reason } }),
};
