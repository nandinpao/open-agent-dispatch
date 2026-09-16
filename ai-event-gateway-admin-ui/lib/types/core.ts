import type { CoreDispatchAttemptHistoryRecord } from "@/lib/types/domains/taskRuntime";
import type { CoreDispatchEventStage, CoreTaskRuntimeView } from "@/lib/types/domains/taskModel";
export type {
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchAttemptHistoryRecord,
  CoreDispatchAttemptLedger,
  CoreDispatchAttemptLedgerEvent,
} from "@/lib/types/domains/taskRuntime";

export type AgentEnrollmentStatus =
  | "SUBMITTED"
  | "REGISTERED"
  | "PENDING_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | "CANCELLED"
  | "RUNTIME_OBSERVED"
  | string;
export type AgentApprovalStatus =
  | "REGISTERED"
  | "PENDING_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | "SUSPENDED"
  | "REVOKED"
  | string;
export type AgentRiskStatus =
  "NORMAL" | "SUSPENDED" | "REVOKED" | "QUARANTINED" | "COMPROMISED" | string;
export type AgentCredentialStatus =
  "ACTIVE" | "EXPIRED" | "REVOKED" | "MISSING" | string;

export interface CoreAgentSetupReadinessCheck {
  code?: string;
  label?: string;
  status?: string;
  ready?: boolean;
  description?: string;
  action?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSetupTroubleshootingStep {
  code?: string;
  label?: string;
  severity?: 'INFO' | 'WARN' | 'ERROR' | string;
  description?: string;
  action?: string;
  command?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSetupStartCommand {
  runtimeType?: string;
  gatewayUrl?: string;
  command?: string;
  dockerCommand?: string;
  localCommand?: string;
  remoteCommand?: string;
  healthCheckCommand?: string;
  logsCommand?: string;
  verifyConnectionCommand?: string;
  expectedCapabilities?: string[];
  capabilityEnvironmentVariable?: string;
  startupSteps?: string[];
  troubleshooting?: CoreAgentSetupTroubleshootingStep[];
  environment?: Record<string, unknown>;
  diagnostics?: Record<string, unknown>;
}

export interface CoreAgentSetupRequest {
  tenantId?: string;
  agentId: string;
  agentName: string;
  ownerTeam?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  businessOwnerUserId?: string;
  technicalStewardUserId?: string;
  responsibilityRoleId?: string;
  description?: string;
  purpose?: string;
  runtimeType?: string;
  gatewayUrl?: string;
  credentialToken?: string;
  autoApprove?: boolean;
  createDefaultCapabilities?: boolean;
  createRuntimeBinding?: boolean;
  createSupplyProfile?: boolean;
  createDefaultDispatchRule?: boolean;
  capacityLimit?: number;
  operatorId?: string;
  defaultCapabilities?: string[];
  defaultTaskTypes?: string[];
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSetupReadinessResponse {
  tenantId?: string;
  agentId?: string;
  ready?: boolean;
  status?: string;
  summary?: string;
  blockingReasons?: string[];
  checks?: CoreAgentSetupReadinessCheck[];
  startCommand?: CoreAgentSetupStartCommand;
  profileCapabilities?: string[];
  runtimeReportedCapabilities?: string[];
  missingRuntimeCapabilities?: string[];
  extraRuntimeCapabilities?: string[];
  troubleshooting?: CoreAgentSetupTroubleshootingStep[];
  metadata?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreAgentOperationalNextAction {
  code?: string;
  label?: string;
  target?: string;
  severity?: string;
  payload?: Record<string, unknown>;
}

export interface CoreAgentOperationalAuthorityCheck {
  code?: string;
  label?: string;
  status?: string;
  ready?: boolean;
  blocking?: boolean;
  message?: string;
  source?: string;
  category?: string;
  nextAction?: CoreAgentOperationalNextAction;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentOperationalRuntimeSummary {
  status?: string;
  connectionStatus?: string;
  online?: boolean;
  assignable?: boolean;
  draining?: boolean;
  currentTaskCount?: number;
  reservedTaskCount?: number;
  maxConcurrentTasks?: number;
  availableSlots?: number;
  capacityUtilization?: number;
  runtimeFailureCount?: number;
  lastHeartbeatAt?: string;
  leaseExpiresAt?: string;
  runtimeBackoffUntil?: string;
  runtimeBackoffReason?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
}

export interface CoreAgentOperationalView {
  tenantId?: string;
  agentId?: string;
  canReceiveTask?: boolean;
  readinessStatus?: string;
  readinessLevel?: string;
  summary?: string;
  firstBlockingCode?: string;
  firstBlockingReason?: string;
  nextAction?: CoreAgentOperationalNextAction;
  authorityChecks?: CoreAgentOperationalAuthorityCheck[];
  runtime?: CoreAgentOperationalRuntimeSummary;
  setupReadiness?: CoreAgentSetupReadinessResponse;
  dispatchEligibility?: CoreAgentDispatchEligibility;
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreAgentSetupResponse {
  tenantId?: string;
  agentId?: string;
  setupStatus?: string;
  setupMode?: string;
  enrollment?: AgentEnrollmentRequest;
  agentProfile?: CoreAgentProfile;
  runtimeResource?: CoreRuntimeResource;
  runtimeBinding?: CoreAgentRuntimeBinding;
  supplyProfile?: CoreSupplyProfile;
  dispatchPolicy?: CoreDispatchPolicy;
  capabilityCatalog?: CoreAgentCapabilityCatalog[];
  capabilityAssignments?: CoreAgentCapabilityAssignment[];
  readinessChecks?: CoreAgentSetupReadinessCheck[];
  startCommand?: CoreAgentSetupStartCommand;
  metadata?: Record<string, unknown>;
  createdAt?: string;
}

export interface CoreAgentCapability {
  capabilityCode: string;
  capabilityVersion?: string;
  enabled?: boolean;
  approvedBy?: string;
  approvedAt?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentAuthorizationScope {
  scopeId?: string;
  agentId?: string;
  tenantId?: string;
  systemCode?: string;
  siteCode?: string;
  eventType?: string;
  taskType?: string;
  dataClassificationLimit?: string;
  enabled?: boolean;
}

export interface CoreAgentCredentialSummary {
  credentialId?: string;
  credentialType?: string;
  credentialVersion?: number;
  credentialStatus?: AgentCredentialStatus;
  publicKeyFingerprint?: string;
  issuedAt?: string;
  expiresAt?: string;
  revokedAt?: string;
}

export interface CoreAgentProfile {
  agentId: string;
  tenantId?: string;
  agentName?: string;
  agentType?: string;
  ownerTeam?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  businessOwnerUserId?: string;
  technicalStewardUserId?: string;
  responsibilityRoleId?: string;
  responsibilityBindingId?: string;
  ownershipReviewStatus?: string;
  ownershipReviewReason?: string;
  nextOwnershipReviewAt?: string;
  description?: string;
  approvalStatus: AgentApprovalStatus;
  enabled: boolean;
  riskStatus?: AgentRiskStatus;
  credential?: CoreAgentCredentialSummary;
  capabilities?: CoreAgentCapability[];
  authorizationScopes?: CoreAgentAuthorizationScope[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentRuntimeCapabilityProfile {
  agentId: string;
  agentType?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  pluginName?: string;
  pluginVersion?: string;
  capabilityRevision?: string;
  executorMode?: string;
  placementPool?: string;
  placementRegion?: string;
  placementZone?: string;
  maxConcurrentTasks?: number;
  capabilityProfile?: Record<string, unknown>;
  firstSeenAt?: string;
  lastSeenAt?: string;
}

export interface CoreAgentRuntimeDescriptor {
  agentId: string;
  agentType?: string;
  pluginName?: string;
  pluginVersion?: string;
  protocolVersion?: string;
  connectionType?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  siteId?: string;
  region?: string;
  zone?: string;
  status?: string;
  runtimeFeatures?: string[];
  activeTasks?: number;
  maxConcurrentTasks?: number;
  availableSlots?: number;
  capacityUtilization?: number;
  draining?: boolean;
  heartbeatSequence?: number;
  connectedAt?: string;
  lastHeartbeatAt?: string;
  lastSeenAt?: string;
  rawPayload?: Record<string, unknown>;
  firstSeenAt?: string;
  updatedAt?: string;
}

// Certification evidence remains metadata on capability catalog/assignments in v24.
// A first-class certification run API/domain is intentionally not declared until Core implements it.

export type CoreDispatchTaskDefinitionStatus = "DRAFT" | "ACTIVE" | "DISABLED" | "RETIRED" | "NEEDS_REVIEW" | string;

export interface CoreDispatchTaskDefinition {
  tenantId?: string;
  definitionId: string;
  sourceSystem: string;
  taskType: string;
  displayName?: string;
  description?: string;
  domain?: string;
  riskLevel?: string;
  defaultSeverity?: string;
  ownerTeam?: string;
  status?: CoreDispatchTaskDefinitionStatus;
  version?: number;
  effectiveFrom?: string;
  retiredAt?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDispatchTaskDefinitionImpactPreview {
  tenantId?: string;
  definitionId: string;
  sourceSystem?: string;
  taskType?: string;
  action?: string;
  allowed?: boolean;
  severity?: "INFO" | "WARN" | "BLOCKING" | string;
  summary?: string;
  blockingReasons?: string[];
  warnings?: string[];
  affectedCounts?: Record<string, number>;
  affectedProfileCodes?: string[];
  affectedActiveProfileCodes?: string[];
  affectedPolicyCodes?: string[];
  affectedRecipeCodes?: string[];
  generatedAt?: string;
}

export interface CoreDispatchTaskDefinitionReviewCommand {
  operatorId?: string;
  reason?: string;
  targetDefinitionId?: string;
  force?: boolean;
  metadata?: Record<string, unknown>;
}

export type CoreDispatchPolicyStatus = "DRAFT" | "READY_FOR_REVIEW" | "APPROVED" | "ACTIVE" | "SUSPENDED" | "RETIRED" | string;

export interface CoreDispatchPolicyScope {
  tenantId?: string;
  scopeId?: string;
  policyCode?: string;
  sourceSystem?: string;
  taskType?: string;
  taskDefinitionId?: string;
  riskLevel?: string;
  priority?: string;
  conditionExpr?: string;
  active?: boolean;
  priorityOrder?: number;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDispatchPolicyRequiredCapability {
  tenantId?: string;
  ruleId?: string;
  policyCode?: string;
  capabilityCode: string;
  capabilityName?: string;
  requiredMode?: string;
  minVersion?: string;
  conditionExpr?: string;
  blocking?: boolean;
  priority?: number;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDispatchPolicyRequiredRuntimeFeature {
  tenantId?: string;
  ruleId?: string;
  policyCode?: string;
  featureCode: string;
  featureName?: string;
  trustStatus?: string;
  conditionExpr?: string;
  blocking?: boolean;
  priority?: number;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDispatchPolicyQualityRule {
  tenantId?: string;
  ruleId?: string;
  policyCode?: string;
  metricName: string;
  operator?: string;
  thresholdValue?: string;
  metricWindow?: string;
  blocking?: boolean;
  scoreWeight?: number;
  conditionExpr?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDispatchPolicyScoringRule {
  tenantId?: string;
  ruleId?: string;
  policyCode?: string;
  factorName: string;
  weight?: number;
  direction?: string;
  conditionExpr?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDispatchPolicy {
  tenantId?: string;
  policyId?: string;
  policyCode: string;
  policyName?: string;
  description?: string;
  ownerTeam?: string;
  riskLevel?: string;
  status?: CoreDispatchPolicyStatus;
  version?: number;
  effectiveFrom?: string;
  retiredAt?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
  scopes?: CoreDispatchPolicyScope[];
  requiredCapabilities?: CoreDispatchPolicyRequiredCapability[];
  requiredRuntimeFeatures?: CoreDispatchPolicyRequiredRuntimeFeature[];
  qualityRules?: CoreDispatchPolicyQualityRule[];
  scoringRules?: CoreDispatchPolicyScoringRule[];
}

export type CoreCanonicalCapabilityStatus = "DRAFT" | "ACTIVE" | "DISABLED" | "RETIRED" | string;

/** Phase 1 provider-neutral semantic WHAT contract. */
export interface CoreCanonicalCapabilityDefinition {
  tenantId?: string;
  capabilityId?: string;
  capabilityCode: string;
  displayName: string;
  description?: string;
  semanticDomain?: string;
  category?: string;
  capabilityType?: string;
  operations?: string[];
  inputSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
  resourceTypes?: string[];
  dataClasses?: string[];
  version?: number;
  status?: CoreCanonicalCapabilityStatus;
  serviceCodes?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreCapabilityRequirement {
  capabilityCode: string;
  operation?: string;
  inputContext?: Record<string, unknown>;
  resourceConstraints?: Record<string, unknown>;
  dataClassification?: string;
  requiredAssurance?: string;
  deadline?: string;
  qualityPreference?: string;
}

export type CoreCapabilityProviderType = "MANAGED_AGENT" | "REMOTE_A2A_AGENT" | "MCP_TOOL" | "INTERNAL_SERVICE" | string;
export type CoreCapabilityBindingTrustStatus = "DISCOVERED" | "PROPOSED" | "VERIFIED" | "APPROVED" | "SUSPENDED" | "REVOKED" | "STALE" | string;

/** Phase 2 provider identity. WHO CAN metadata only; not runtime authorization or routing selection. */
export interface CoreCapabilityProvider {
  tenantId?: string;
  providerId: string;
  providerType: CoreCapabilityProviderType;
  displayName: string;
  providerRef: string;
  registrationSource?: string;
  catalogStatus?: string;
  metadata?: Record<string, unknown>;
  observedAt?: string;
  lastVerifiedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** Phase 2 evidence that a provider CAN provide a Canonical Capability. */
export interface CoreCapabilityBinding {
  tenantId?: string;
  bindingId: string;
  capabilityCode: string;
  providerId: string;
  providerType?: CoreCapabilityProviderType;
  providerDisplayName?: string;
  supportedOperations?: string[];
  trustStatus?: CoreCapabilityBindingTrustStatus;
  sourceRevision?: string;
  verificationMethod?: string;
  verificationEvidenceRef?: string;
  observedAt?: string;
  verifiedAt?: string;
  approvedAt?: string;
  staleAfter?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreCapabilityBindingTrustEvent {
  tenantId?: string;
  eventId: string;
  bindingId: string;
  fromStatus?: string;
  toStatus: string;
  reason?: string;
  actorRef?: string;
  occurredAt?: string;
}

export type CoreDelegationPolicyEffect = "ALLOW" | "DENY" | string;
export type CoreDelegationPolicyStatus = "DRAFT" | "ACTIVE" | "SUSPENDED" | "RETIRED" | string;
export type CoreDelegationAuthorizationResult = "PASS" | "FAIL" | "WAITING_APPROVAL" | string;

/** Phase 3 WHO MAY hard-gate policy. No target Domain/System/Pool/Agent is represented. */
export interface CoreDelegationPolicy {
  tenantId?: string;
  policyId: string;
  displayName: string;
  description?: string;
  effect?: CoreDelegationPolicyEffect;
  status?: CoreDelegationPolicyStatus;
  requesterPrincipalTypes?: string[];
  requesterDepartmentIds?: string[];
  requesterGroupIds?: string[];
  requesterRoleCodes?: string[];
  capabilityCodes?: string[];
  operations?: string[];
  resourceConstraints?: Record<string, unknown>;
  allowedDataClasses?: string[];
  maxSensitivityLevel?: string;
  allowedAccessModes?: string[];
  requiredProviderTypes?: string[];
  requiredProviderCertifications?: string[];
  approvalMode?: string;
  maxEstimatedCost?: number;
  maxDelegationDepth?: number;
  maxAgentCalls?: number;
  maxExecutionTimeMs?: number;
  priority?: number;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDelegationPolicyVersion {
  tenantId?: string;
  policyId: string;
  version: number;
  snapshot: Record<string, unknown>;
  changeReason: string;
  actorRef: string;
  createdAt: string;
}

export interface CoreDelegationPolicyAuditEvent {
  tenantId?: string;
  eventId: string;
  policyId: string;
  policyVersion: number;
  action: string;
  reason: string;
  actorRef: string;
  occurredAt: string;
}

/** Admin-only Phase 3 preview input. Runtime identity must later be server-derived. */
export interface CoreDelegationAuthorizationRequest {
  requirement: CoreCapabilityRequirement;
  bindingId: string;
  requesterPrincipalType: string;
  requesterDepartmentId?: string;
  requesterGroupIds?: string[];
  requesterRoleCodes?: string[];
  accessMode?: string;
  sensitivityLevel?: string;
  estimatedCost?: number;
  delegationDepth?: number;
  agentCalls?: number;
  executionTimeMs?: number;
  approvalCount?: number;
}

export interface CoreDelegationAuthorizationDecision {
  decisionId: string;
  tenantId?: string;
  result: CoreDelegationAuthorizationResult;
  capabilityCode: string;
  operation: string;
  bindingId: string;
  providerId: string;
  providerType: string;
  selectedPolicyId?: string;
  selectedPolicyVersion?: number;
  approvalMode?: string;
  reasonCodes?: string[];
  consideredPolicyIds?: string[];
  evaluatedAt: string;
}

export type CoreRoutingProfileType = "FAST" | "LOW_COST" | "BALANCED" | "HIGH_ACCURACY" | "CRITICAL" | "CUSTOM" | string;
export type CoreProviderRoutingResult = "SELECTED" | "NO_ELIGIBLE_PROVIDER" | "ROUTING_AMBIGUOUS" | string;

/** Phase 4 WHO SHOULD ranking policy. Authorization and transport are intentionally absent. */
export interface CoreRoutingProfile {
  tenantId?: string;
  profileId: string;
  displayName: string;
  description?: string;
  profileType?: CoreRoutingProfileType;
  status?: string;
  weights?: Record<string, number>;
  minQualityScore?: number;
  minReliabilityScore?: number;
  maxP95LatencyMs?: number;
  maxEstimatedCost?: number;
  maxLoadPercent?: number;
  maxObservationAgeSeconds?: number;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreRoutingProfileVersion {
  tenantId?: string;
  profileId: string;
  version: number;
  snapshot: Record<string, unknown>;
  changeReason: string;
  actorRef: string;
  createdAt: string;
}

/** Protocol-neutral runtime/quality evidence for Phase 4 eligibility and ranking. */
export interface CoreProviderEligibilityObservation {
  tenantId?: string;
  observationId?: string;
  bindingId: string;
  providerId: string;
  observationSource?: string;
  available: boolean;
  healthy: boolean;
  capacityAvailable: boolean;
  qualityScore?: number;
  reliabilityScore?: number;
  p95LatencyMs?: number;
  estimatedCost?: number;
  loadPercent?: number;
  localityScore?: number;
  observedAt?: string;
  expiresAt?: string;
  createdAt?: string;
}

export interface CoreProviderRoutingCandidateScore {
  bindingId: string;
  providerId: string;
  providerType: string;
  authorizationDecisionId: string;
  eligibilityObservationId?: string;
  eligibilityResult: string;
  exclusionReasons?: string[];
  scoreComponents?: Record<string, number>;
  totalScore?: number;
}

/** Phase 4 preview references persisted Phase 3 PASS decisions. It never accepts a selected Provider as input. */
export interface CoreProviderRoutingPreviewRequest {
  capabilityCode: string;
  operation: string;
  routingProfileId: string;
  authorizationDecisionIds: string[];
}

export interface CoreProviderRoutingDecision {
  decisionId: string;
  tenantId?: string;
  decisionMode: string;
  result: CoreProviderRoutingResult;
  capabilityCode: string;
  operation: string;
  routingProfileId: string;
  routingProfileVersion: number;
  selectedBindingId?: string;
  selectedProviderId?: string;
  reasonCodes?: string[];
  candidates?: CoreProviderRoutingCandidateScore[];
  evaluatedAt: string;
}

export interface CoreR6EligibilityCandidateEvaluation {
  evaluationId: string; bindingId: string; providerId: string; providerType: string; bindingClass: string;
  agentPoolId?: string; observationId?: string; result: string; reasonCodes?: string[];
  scoreDimensions?: Record<string, number>; normalizedWeightedScore?: number;
}
export interface CoreR6EligibilityDecision {
  decisionId: string; tenantId?: string; planId: string; planRevision: number; stepId: string; taskId?: string; envelopeId: string;
  capabilityCode: string; capabilityVersion: number; operation: string; routingProfileId: string; routingProfileVersion: number;
  candidateCount: number; eligibleCount: number; excludedCount: number; result: string; exclusionSummary?: Record<string, number>;
  candidateEvaluationSetDigest: string; policySnapshotRef?: string; evaluatedAt: string;
}
export interface CoreR6RoutingFeatureSnapshot {
  snapshotId: string; tenantId?: string; eligibilityDecisionId: string; candidateCatalogVersion: string; metricAsOf: string;
  qualityModelVersion: string; poolHealthRevision: string; availabilitySnapshot?: Record<string, unknown>; loadSnapshot?: Record<string, unknown>;
  pricingVersion: string; routingProfileId: string; routingProfileVersion: number; scoringConfigVersion: string; capturedAt: string;
}
export interface CoreR6RoutingDecision {
  decisionId: string; tenantId?: string; result: string; planId: string; planRevision: number; stepId: string; envelopeId: string;
  eligibilityDecisionId: string; routingFeatureSnapshotRef: string; capabilityCode: string; operation: string;
  routingProfileId: string; routingProfileVersion: number; selectedBindingId?: string; selectedProviderId?: string; selectedAgentPoolId?: string;
  reasonCodes?: string[]; authorityMode: 'SHADOW' | string; decidedAt: string;
}
export interface CoreR6ExecutionAssignmentShadow {
  assignmentId: string; tenantId?: string; taskId?: string; planId: string; planRevision: number; stepId: string; routingDecisionId: string;
  bindingId: string; providerId: string; agentPoolId?: string; selectedAgentId?: string; selectedSessionId?: string;
  selectedPeerInterfaceId?: string; selectedMcpServerId?: string; selectedAdapterId?: string; assignmentReason: string;
  runtimeLoadSnapshot?: Record<string, unknown>; attemptNumber: number; previousAssignmentId?: string; authorityMode: 'SHADOW_ONLY' | string;
  sideEffectAllowed: boolean; assignedAt: string; releasedAt?: string; outcome: string;
}
export interface CoreFlowRoutingMigrationState {
  tenantId?: string; flowId: string; migrationState: 'LEGACY_AUTHORITATIVE' | 'SHADOW' | string; version: number;
  changeReason: string; changedBy: string; changedAt?: string;
}
export interface CoreRoutingAuthorityShadowRequest { planRevision?: number; stepId: string; routingProfileId: string; }
export interface CoreRoutingAuthorityShadowResult {
  eligibilityDecision: CoreR6EligibilityDecision; candidateEvaluations?: CoreR6EligibilityCandidateEvaluation[];
  routingFeatureSnapshot: CoreR6RoutingFeatureSnapshot; routingDecision: CoreR6RoutingDecision;
  executionAssignment?: CoreR6ExecutionAssignmentShadow; flowMigrationState?: CoreFlowRoutingMigrationState;
  legacyAssignmentId?: string; legacyAgentId?: string; legacyPoolId?: string; executorEquivalent: boolean;
}
export interface CoreRebindingAdmissionRequest { planRevision?: number; stepId: string; requestedBindingId: string; }
export interface CoreRebindingAdmissionDecision {
  decisionId: string; tenantId?: string; planId: string; planRevision: number; stepId: string; envelopeId: string;
  requestedBindingId: string; requestedBindingClass: string; sideEffect: string; writeSemantics?: string;
  result: string; reasonCodes?: string[]; evaluatedAt: string;
}

export interface CoreExecutionSafetyActivationRequest { targetState: 'LEGACY_AUTHORITATIVE' | 'SHADOW' | 'NEW_AUTHORITATIVE'; reason: string; releaseCandidateId?: string; }
export interface CoreExecutionSafetyPrepareRequest {
  planRevision?: number; stepId: string; routingProfileId: string; ownerNodeId: string; leaseTtlSeconds?: number;
  humanApprovalRef?: string; input?: Record<string, unknown>;
}
export interface CoreExecutionAssignmentV206 {
  assignmentId: string; tenantId?: string; shadowAssignmentId?: string; taskId: string; planId: string; planRevision: number; stepId: string; flowId: string;
  routingDecisionId: string; envelopeId: string; bindingId: string; providerId: string; providerType: string; agentPoolId?: string;
  selectedAgentId?: string; selectedSessionId?: string; selectedPeerInterfaceId?: string; selectedMcpServerId?: string; selectedAdapterId?: string; adapterType: string;
  executionSafetyMode: 'LOCAL_FENCED' | 'REMOTE_NATIVE_IDEMPOTENT' | 'REMOTE_UNFENCED' | string; sideEffect: string; writeSemantics?: string; humanApprovalRef?: string;
  leaseId: string; fencingToken: number; leaseUntil: string; attemptNumber: number; previousAssignmentId?: string; authorityMode: string; status: string; assignmentReason: string; createdAt: string; releasedAt?: string; outcome?: string;
}
export interface CoreExecutionLeaseV206 { leaseId: string; tenantId?: string; taskId: string; planId: string; planRevision: number; stepId: string; assignmentId: string; ownerNodeId: string; fencingToken: number; leaseUntil: string; status: string; acquiredAt: string; releasedAt?: string; releaseReason?: string; version: number; }
export interface CoreExecutionDispatchIntentV206 { intentId: string; tenantId?: string; assignmentId: string; leaseId: string; fencingToken: number; taskId: string; planId: string; planRevision: number; stepId: string; flowId: string; bindingId: string; providerId: string; providerType: string; selectedAdapterId: string; adapterType: string; executionSafetyMode: string; sideEffect: string; writeSemantics?: string; payload?: Record<string, unknown>; status: string; attemptCount: number; claimedBy?: string; claimToken?: string; claimUntil?: string; sendStartedAt?: string; handedOffAt?: string; externalExecutionRef?: string; deliveryUnknownSince?: string; lastErrorCode?: string; lastErrorMessage?: string; createdAt: string; updatedAt: string; }
export interface CoreExecutionSafetyPrepareResult { routingEvidence: CoreRoutingAuthorityShadowResult; executionAssignment: CoreExecutionAssignmentV206; executionLease: CoreExecutionLeaseV206; dispatchIntent: CoreExecutionDispatchIntentV206; cutoverState: string; }

export type CoreExecutionAdapterType = "MANAGED_AGENT_NETTY" | "REMOTE_A2A" | "MCP_TOOL" | "INTERNAL_SERVICE" | string;
export type CoreExecutionAdapterResolutionResult = "SELECTED" | "NO_ACTIVE_ADAPTER" | "ADAPTER_AMBIGUOUS" | "ROUTING_DECISION_STALE" | string;

/** Phase 5 HOW configuration. endpointRef/credentialRef are opaque registry references, never raw secrets. */
export interface CoreExecutionAdapterRegistration {
  tenantId?: string; adapterId: string; providerId: string; providerType: string; adapterType: CoreExecutionAdapterType;
  protocol: string; protocolVersion?: string; endpointRef?: string; credentialRef?: string; runtimeRef?: string;
  status?: string; selectionPriority?: number; configuration?: Record<string, unknown>; version?: number; createdAt?: string; updatedAt?: string;
}
export interface CoreExecutionAdapterVersion { tenantId?: string; adapterId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreExecutionAdapterResolutionRequest { routingDecisionId: string; }
export interface CoreExecutionAdapterResolution {
  resolutionId: string; tenantId?: string; resolutionMode: string; result: CoreExecutionAdapterResolutionResult; routingDecisionId: string;
  capabilityCode: string; operation: string; bindingId?: string; providerId?: string; providerType?: string; adapterId?: string; adapterVersion?: number;
  adapterType?: CoreExecutionAdapterType; protocol?: string; protocolVersion?: string; endpointRef?: string; credentialRef?: string; runtimeRef?: string; reasonCodes?: string[]; resolvedAt: string;
}

export type CoreAgentCapabilityCatalogStatus = "DRAFT" | "ACTIVE" | "DISABLED" | "RETIRED" | "NEEDS_REVIEW" | string;

// Capability catalog defines the canonical vocabulary used by Task requirements and Agent assignments.
// A catalog entry alone does not grant eligibility; Pool membership and an APPROVED Agent assignment are evaluated separately.
export interface CoreAgentCapabilityCatalog {
  tenantId?: string;
  capabilityId?: string;
  capabilityCode: string;
  capabilityName?: string;
  category?: string;
  capabilityType?: string;
  domain?: string;
  resourceType?: string;
  operation?: string;
  dataClass?: string;
  serviceLevel?: string;
  legacyTaskCoupling?: boolean;
  migrationStatus?: string;
  description?: string;
  taskDefinitionId?: string;
  sourceSystem?: string;
  taskType?: string;
  riskLevel?: string;
  status?: CoreAgentCapabilityCatalogStatus;
  version?: number;
  ownerTeam?: string;
  capabilitySource?: string;
  certificationRef?: string;
  lastReportedAt?: string;
  referenceOnly?: boolean;
  routingGate?: boolean;
  requiresApproval?: boolean;
  requiresCertification?: boolean;
  requiresRuntimeProbe?: boolean;
  dispatchEligible?: boolean;
  effectiveFrom?: string;
  retiredAt?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreAssignmentProfileCapabilityBindingMode = "REQUIRED" | "OPTIONAL" | "ALLOW" | "DENY" | "CONDITIONAL" | string;

export interface CoreAssignmentProfileCapabilityBinding {
  tenantId?: string;
  bindingId?: string;
  profileCode: string;
  capabilityCode: string;
  capabilityName?: string;
  bindingMode?: CoreAssignmentProfileCapabilityBindingMode;
  required?: boolean;
  active?: boolean;
  priority?: number;
  approvalStatus?: string;
  conditionExpr?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreAgentCapabilityAssignmentStatus = "DECLARED" | "PENDING_APPROVAL" | "APPROVED" | "SUSPENDED" | "REVOKED" | "EXPIRED" | "REJECTED" | string;

// An APPROVED Agent capability assignment is the canonical blocking qualification authority for a matching Task Required Capability.
export interface CoreAgentCapabilityAssignment {
  tenantId?: string;
  assignmentId?: string;
  agentId: string;
  capabilityCode: string;
  capabilityName?: string;
  status?: CoreAgentCapabilityAssignmentStatus;
  source?: string;
  capabilityVersion?: string;
  lastReportedAt?: string;
  certificationRef?: string;
  requestedBy?: string;
  requestedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  revokedBy?: string;
  revokedAt?: string;
  expiresAt?: string;
  evidenceRef?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentCapabilityCommand {
  tenantId?: string;
  capabilityCode?: string;
  operatorId?: string;
  source?: string;
  evidenceRef?: string;
  expiresAt?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
}

export interface CoreAdvancedSelectionStrategyContract {
  strategyCode: string;
  displayName?: string;
  status?: 'CONTRACT_ONLY' | 'READY_FOR_IMPLEMENTATION' | 'ENABLED' | string;
  productionEnabled?: boolean;
  simulationRequired?: boolean;
  simulationSupportStatus?: string;
  formula?: string;
  stateStorage?: string;
  concurrencyDefinition?: string;
  fallbackStrategy?: string;
  assignmentEvidenceContract?: string;
  uiExplanation?: string;
  requiredReadinessChecks?: string[];
  localityDimensions?: string[];
  metadata?: Record<string, unknown>;
}

export type CoreAgentPoolCapabilityPolicyMatchMode = 'ANY' | 'ALL' | string;
export type CoreAgentPoolCapabilityPolicyEnforcementMode = 'ADVISORY' | 'REQUIRED' | string;

export interface CoreAgentPoolCapabilityPolicy {
  tenantId?: string;
  policyId?: string;
  targetPoolId: string;
  targetPoolName?: string;
  requiredCapabilities?: string[];
  matchMode?: CoreAgentPoolCapabilityPolicyMatchMode;
  enforcementMode?: CoreAgentPoolCapabilityPolicyEnforcementMode;
  advisoryOnly?: boolean;
  explicitPoolPolicy?: boolean;
  routingGate?: boolean;
  riskLevel?: string;
  riskWarning?: string;
  simulationImpact?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  createdBy?: string;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreAgentAdvisoryRecommendationType =
  | 'ADD_AGENT_TO_POOL'
  | 'ADJUST_AGENT_WEIGHT'
  | 'INCREASE_POOL_CAPACITY'
  | 'POOL_CAPACITY_RISK'
  | string;

export type CoreAgentAdvisoryRecommendationStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED' | 'SUPERSEDED' | string;

export interface CoreAgentAdvisoryRecommendation {
  tenantId?: string;
  recommendationId: string;
  recommendationType: CoreAgentAdvisoryRecommendationType;
  status?: CoreAgentAdvisoryRecommendationStatus;
  targetPoolId?: string;
  targetAgentId?: string;
  capabilityCode?: string;
  evidenceWindow?: string;
  confidence?: number | string;
  reason?: string;
  suggestedChange?: Record<string, unknown>;
  evidence?: Record<string, unknown>;
  decisionAudit?: Record<string, unknown>;
  advisoryOnly?: boolean;
  autoApply?: boolean;
  routingImpact?: 'NONE' | string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  acceptedBy?: string;
  acceptedAt?: string;
  rejectedBy?: string;
  rejectedAt?: string;
  decisionReason?: string;
}

export interface CoreAgentAdvisoryRecommendationDecisionCommand {
  tenantId?: string;
  operatorId?: string;
  reason?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentQualityMetricsWindow {
  tenantId?: string;
  metricId?: string;
  agentId?: string;
  runtimeId?: string;
  bindingId?: string;
  supplyProfileId?: string;
  profileCode?: string;
  metricWindow?: string;
  windowStart?: string;
  windowEnd?: string;
  successRate?: number | string;
  failureRate?: number | string;
  timeoutRate?: number | string;
  slaBreachRate?: number | string;
  avgAckLatencyMs?: number;
  avgCompletionLatencyMs?: number;
  recentFailureCount?: number;
  manualRating?: number | string;
  qualityGrade?: string;
  riskPenalty?: number | string;
  score?: number | string;
  sampleSize?: number;
  /** Phase 9B: observation-only quality fields. These support UI/operator visibility only and must not feed Selection Strategy. */
  p95CompletionLatencyMs?: number;
  ackTimeoutRate?: number | string;
  resultFailureRate?: number | string;
  retryRate?: number | string;
  manualReassignmentRate?: number | string;
  recentHealthScore?: number | string;
  observationWindow?: string;
  minimumSample?: number;
  decayWindow?: string;
  responsibilityScope?: 'AGENT' | 'UPSTREAM_PAYLOAD' | 'SYSTEM_CONFIGURATION' | 'UNKNOWN' | string;
  observationOnly?: boolean;
  selectionImpact?: 'NONE' | string;
  calculatedAt?: string;
  source?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentQualityMetricsDaily extends CoreAgentQualityMetricsWindow {
  metricDate?: string;
}

export interface CoreRuntimeQualityMetricsDaily extends CoreAgentQualityMetricsWindow {
  metricDate?: string;
  runtimeId?: string;
}

export interface CoreSupplyProfileQualitySnapshot extends CoreAgentQualityMetricsWindow {
  snapshotId?: string;
  supplyProfileId?: string;
  profileCode: string;
}

export type CoreSupplyProfileStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "SUSPENDED" | "RETIRED" | string;

export interface CoreSupplyProfile {
  tenantId?: string;
  supplyProfileId?: string;
  profileCode: string;
  profileName?: string;
  agentId?: string;
  runtimeBindingId?: string;
  runtimeId?: string;
  serviceRole?: string;
  serviceLevel?: string;
  qualityGrade?: string;
  riskLimit?: string;
  dataScope?: string;
  capacityPolicy?: string;
  status?: CoreSupplyProfileStatus;
  effectiveFrom?: string;
  expiresAt?: string;
  capabilitySnapshot?: string[];
  runtimeFeatureSnapshot?: string[];
  qualitySnapshot?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreRuntimeResourceStatus = "REGISTERED" | "ACTIVE" | "SUSPENDED" | "OFFLINE" | "RETIRED" | string;
export type CoreRuntimeResourceTrustStatus = "UNVERIFIED" | "VERIFIED" | "TRUSTED" | "UNTRUSTED" | "SUSPENDED" | "REVOKED" | string;

export interface CoreRuntimeResource {
  tenantId?: string;
  runtimeId: string;
  runtimeCode?: string;
  runtimeName?: string;
  runtimeType?: string;
  connectorType?: string;
  executionHost?: string;
  environment?: string;
  region?: string;
  zone?: string;
  trustStatus?: CoreRuntimeResourceTrustStatus;
  status?: CoreRuntimeResourceStatus;
  capacityLimit?: number;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreAgentRuntimeBindingStatus = "PENDING" | "ACTIVE" | "PAUSED" | "SUSPENDED" | "REVOKED" | "EXPIRED" | string;

export interface CoreAgentRuntimeBinding {
  tenantId?: string;
  bindingId?: string;
  agentId: string;
  runtimeId: string;
  runtimeCode?: string;
  bindingStatus?: CoreAgentRuntimeBindingStatus;
  verifiedBy?: string;
  verifiedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  effectiveFrom?: string;
  expiresAt?: string;
  capacityLimit?: number;
  region?: string;
  zone?: string;
  dataScope?: string;
  riskLimit?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreRuntimeFeatureCatalogStatus = "DRAFT" | "ACTIVE" | "DISABLED" | "RETIRED" | "NEEDS_REVIEW" | string;

export interface CoreRuntimeFeatureCatalog {
  tenantId?: string;
  featureId?: string;
  featureCode: string;
  featureName?: string;
  category?: string;
  description?: string;
  status?: CoreRuntimeFeatureCatalogStatus;
  version?: number;
  requiresProbe?: boolean;
  requiresTrustApproval?: boolean;
  dispatchEligible?: boolean;
  ownerTeam?: string;
  effectiveFrom?: string;
  retiredAt?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentRuntimeFeatureObservation {
  tenantId?: string;
  observationId?: string;
  agentId: string;
  featureCode: string;
  featureName?: string;
  observedValue?: string;
  source?: string;
  probeResult?: string;
  observedAt?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreAgentRuntimeFeatureTrustStatus = "OBSERVED" | "VERIFIED" | "TRUSTED" | "SUSPENDED" | "REVOKED" | string;

export interface CoreAgentRuntimeFeatureTrust {
  tenantId?: string;
  trustId?: string;
  agentId: string;
  featureCode: string;
  featureName?: string;
  trustStatus?: CoreAgentRuntimeFeatureTrustStatus;
  source?: string;
  observedAt?: string;
  verifiedBy?: string;
  verifiedAt?: string;
  trustedBy?: string;
  trustedAt?: string;
  revokedBy?: string;
  revokedAt?: string;
  expiresAt?: string;
  evidenceRef?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentRuntimeFeatureCommand {
  tenantId?: string;
  featureCode?: string;
  operatorId?: string;
  source?: string;
  observedValue?: string;
  probeResult?: string;
  evidenceRef?: string;
  expiresAt?: string;
  reason?: string;
  confirmationPhrase?: string;
  metadata?: Record<string, unknown>;
}

export type CoreAgentQualificationStatus =
  "PENDING" | "APPROVED" | "SUSPENDED" | "REVOKED" | "EXPIRED" | string;

export type CoreAssignmentProfilePolicyBindingMode = "REQUIRED" | "ALLOW" | "DENY" | "CONDITIONAL" | string;

export interface CoreAssignmentProfilePolicyBinding {
  tenantId?: string;
  bindingId?: string;
  profileCode: string;
  policyCode: string;
  policyName?: string;
  bindingMode?: CoreAssignmentProfilePolicyBindingMode;
  required?: boolean;
  active?: boolean;
  priority?: number;
  conditionExpr?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

// Phase 4-4: Assignment Profile is a legacy/governance type. Do not use it as the Current Source Flow / Agent Pool setup model.
export interface CoreAgentAssignmentProfile {
  tenantId?: string;
  profileId?: string;
  profileCode: string;
  profileName?: string;
  agentType?: string;
  taskDefinitionId?: string;
  sourceSystem?: string;
  taskType?: string;
  description?: string;
  allowedTaskTypes?: string[];
  allowedIssueProviders?: string[];
  requiredRuntimeFeatures?: string[];
  requiredCapabilities?: string[];
  requiredPolicyCodes?: string[];
  toolPolicy?: string;
  riskLevelLimit?: string;
  requiresCertification?: boolean;
  requiresHumanApproval?: boolean;
  active?: boolean;
  policyVersion?: number;
  effectiveAt?: string;
  expiresAt?: string;
  renewalRequiredBeforeDays?: number;
  metadata?: Record<string, unknown>;
  policyBindings?: CoreAssignmentProfilePolicyBinding[];
  capabilityBindings?: CoreAssignmentProfileCapabilityBinding[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAssignmentProfileRelationshipMap {
  tenantId?: string;
  profileCode: string;
  profileName?: string;
  active?: boolean;
  policyVersion?: number;
  sourceSystems?: string[];
  taskTypes?: string[];
  policyBindings?: CoreAssignmentProfilePolicyBinding[];
  capabilityBindings?: CoreAssignmentProfileCapabilityBinding[];
  qualifications?: CoreAgentQualification[];
  qualificationStatusCounts?: Record<string, number>;
  assignedAgentCount?: number;
  approvedAgentCount?: number;
  pendingAgentCount?: number;
  suspendedAgentCount?: number;
  revokedAgentCount?: number;
  expiredAgentCount?: number;
  relationshipSteps?: string[];
  warnings?: string[];
  generatedAt?: string;
}

export type CoreAssignmentProfileImpactAction =
  | "DISABLE_PROFILE"
  | "DELETE_PROFILE"
  | "REMOVE_POLICY_BINDING"
  | "REVOKE_APPROVED_QUALIFICATIONS"
  | "SUSPEND_APPROVED_QUALIFICATIONS"
  | string;

export interface CoreAssignmentProfileImpactPreview {
  tenantId?: string;
  profileCode: string;
  action?: CoreAssignmentProfileImpactAction;
  allowed?: boolean;
  severity?: "INFO" | "WARN" | "BLOCKING" | string;
  summary?: string;
  blockingReasons?: string[];
  warnings?: string[];
  affectedCounts?: Record<string, number>;
  affectedPolicyBindings?: CoreAssignmentProfilePolicyBinding[];
  affectedCapabilityBindings?: CoreAssignmentProfileCapabilityBinding[];
  affectedQualifications?: CoreAgentQualification[];
  generatedAt?: string;
}

export interface CoreAgentQualification {
  tenantId?: string;
  qualificationId: string;
  agentId: string;
  profileCode: string;
  qualificationStatus: CoreAgentQualificationStatus;
  evidenceType?: string;
  evidenceRef?: string;
  approvedBy?: string;
  approvedAt?: string;
  expiresAt?: string;
  grantedPolicyVersion?: number;
  lastRenewedAt?: string;
  renewalDueAt?: string;
  renewalStatus?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentQualificationCommand {
  tenantId?: string;
  profileCode?: string;
  evidenceType?: string;
  evidenceRef?: string;
  operatorId?: string;
  expiresAt?: string;
  grantedPolicyVersion?: number;
  lastRenewedAt?: string;
  renewalDueAt?: string;
  renewalStatus?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentPolicyDriftFinding {
  code: string;
  severity?: string;
  profileCode?: string;
  qualificationId?: string;
  message?: string;
  blocking?: boolean;
  details?: Record<string, unknown>;
}

export interface CoreAgentGovernanceRemediationAction {
  action: string;
  label?: string;
  severity?: string;
  profileCode?: string;
  qualificationId?: string;
  payload?: Record<string, unknown>;
}

export interface CoreAgentEnterpriseGovernanceSummary {
  agentId: string;
  status: string;
  policyDriftCount?: number;
  renewalDueCount?: number;
  expiredQualificationCount?: number;
  blockingCount?: number;
  driftFindings?: CoreAgentPolicyDriftFinding[];
  remediationActions?: CoreAgentGovernanceRemediationAction[];
  generatedAt?: string;
}

export interface CoreDispatchEligibilityCheck {
  code: string;
  status: "PASS" | "WARN" | "BLOCKED" | "INFO" | string;
  blocking?: boolean;
  message?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchNextAction {
  action: string;
  label?: string;
  severity?: "INFO" | "WARN" | "BLOCKING" | string;
  payload?: Record<string, unknown>;
}

export interface CoreAgentDispatchEligibility {
  agentId: string;
  taskId?: string;
  taskType?: string;
  eligible?: boolean;
  dispatchStatus?: "ELIGIBLE" | "LIMITED" | "BLOCKED" | string;
  connectionStatus?: string;
  approvedProfiles?: string[];
  requiredProfiles?: string[];
  qualifications?: CoreAgentQualification[];
  runtimeDescriptor?: CoreAgentRuntimeDescriptor;
  checks?: CoreDispatchEligibilityCheck[];
  nextActions?: CoreDispatchNextAction[];
  generatedAt?: string;
}

// Eligible-agent responses are read-only projections of canonical dispatch eligibility; they must not implement a second eligibility authority.

export interface CoreAgentRuntimeCapabilityItem {
  agentId: string;
  capabilityKind:
    | "flat"
    | "taskType"
    | "issueProvider"
    | "toolPolicy"
    | "executorMode"
    | string;
  capabilityValue: string;
  capabilityRevision?: string;
  source?: string;
  updatedAt?: string;
}

export interface CoreAgentRuntimeLoadSnapshot {
  agentId: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  status?: string;
  activeTasks?: number;
  maxConcurrentTasks?: number;
  availableSlots?: number;
  capacityUtilization?: number;
  outboxPending?: number;
  outboxInFlight?: number;
  recoveryPendingAssignments?: number;
  draining?: boolean;
  heartbeatSequence?: number;
  runtimeLoad?: Record<string, unknown>;
  heartbeatAt?: string;
  updatedAt?: string;
}

export type CoreSecurityEnforcementMode =
  | "ALERT_ONLY"
  | "QUARANTINE"
  | "QUARANTINE_AND_DISCONNECT"
  | "QUARANTINE_REVOKE_AND_DISCONNECT"
  | string;

export interface CoreAgentSecurityEnforcementPolicy {
  policyId?: string;
  agentId?: string;
  enabled?: boolean;
  duplicateRuntimeMode?: CoreSecurityEnforcementMode;
  requireCredentialRotation?: boolean;
  notifyEmail?: boolean;
  notifySlack?: boolean;
  notifySiem?: boolean;
  emailRecipients?: string[];
  slackChannels?: string[];
  siemTopics?: string[];
  metadata?: Record<string, unknown>;
  updatedBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentSecurityEnforcementPolicyUpdateRequest {
  enabled?: boolean;
  duplicateRuntimeMode?: CoreSecurityEnforcementMode;
  requireCredentialRotation?: boolean;
  notifyEmail?: boolean;
  notifySlack?: boolean;
  notifySiem?: boolean;
  emailRecipients?: string[];
  slackChannels?: string[];
  siemTopics?: string[];
  metadata?: Record<string, unknown>;
  operatorId?: string;
}

export interface CoreRuntimeDisconnectResult {
  agentId?: string;
  gatewayNodeId?: string;
  status?: string;
  requested?: boolean;
  closed?: boolean;
  httpStatus?: number;
  message?: string;
  details?: Record<string, unknown>;
  occurredAt?: string;
}

export interface CoreRuntimeDisconnectReconcileRequest {
  operatorId?: string;
  gatewayNodeId?: string;
  limit?: number;
  includeAgentsWithoutOwner?: boolean;
}

export interface CoreRuntimeDisconnectReconcileItem {
  agentId?: string;
  gatewayNodeId?: string;
  status?: string;
  runtimeDisconnect?: CoreRuntimeDisconnectResult | null;
}

export interface CoreRuntimeDisconnectReconcileReport {
  evaluated?: number;
  attempted?: number;
  closed?: number;
  failed?: number;
  items?: CoreRuntimeDisconnectReconcileItem[];
  occurredAt?: string;
}

export interface CoreRuntimeDisconnectAllRequest {
  operatorId?: string;
  reason?: string;
  gatewayNodeIds?: string[];
}

export interface CoreDuplicateRuntimeSecurityRequest {
  operatorId?: string;
  reason?: string;
  gatewayNodeIds?: string[];
  connectedCount?: number;
  disconnectAll?: boolean;
  requireCredentialRotation?: boolean;
  revokeCredentials?: boolean;
}

export interface CoreDuplicateRuntimeResolveRequest {
  operatorId?: string;
  reason?: string;
  enableAfterRotation?: boolean;
}

export interface CoreAgentSecurityEnforcementResponse {
  agentId?: string;
  status?: string;
  profile?: CoreAgentProfile;
  runtimeDisconnect?: CoreRuntimeDisconnectResult | null;
  credentialRotationRequired?: boolean;
  credentialsRevoked?: boolean;
  nextActions?: string[];
  occurredAt?: string;
}

export interface AgentProfileApprovalRequest {
  operatorId?: string;
  reason?: string;
  enabled?: boolean;
  riskStatus?: AgentRiskStatus;
  credentialType?: "TOKEN" | "PUBLIC_KEY" | "CERTIFICATE" | string;
  credentialToken?: string;
  credentialHash?: string;
  publicKeyFingerprint?: string;
  credentialExpiresAt?: string;
  revokeExisting?: boolean;
}

export interface AgentCredentialIssueRequest {
  operatorId?: string;
  reason?: string;
  credentialType?: "TOKEN" | "PUBLIC_KEY" | "CERTIFICATE" | string;
  credentialToken?: string;
  credentialHash?: string;
  publicKeyFingerprint?: string;
  credentialExpiresAt?: string;
  revokeExisting?: boolean;
}

export interface AgentEnrollmentRequest {
  enrollmentId: string;
  claimedAgentId?: string;
  tenantId?: string;
  agentName?: string;
  agentType?: string;
  /** Canonical Core REST fields. */
  submittedMetadata?: unknown;
  evidence?: unknown;
  /** Backward-compatible aliases used by older Admin UI builds / persistence DTO naming. */
  submittedMetadataJson?: unknown;
  evidenceJson?: unknown;
  fingerprint?: string;
  remoteAddress?: string;
  status: AgentEnrollmentStatus;
  submittedAt?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewComment?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AgentGovernanceTableDiagnostic {
  tableName: string;
  rowCount: number;
  expectedWriter?: string;
  emptyMeaning?: string;
}

export interface CoreAgentConnectionRepairAction {
  actionCode: string;
  label?: string;
  description?: string;
  actionType?: 'EXECUTE' | 'NAVIGATE' | string;
  method?: string;
  endpoint?: string;
  enabled?: boolean;
  requiresCredentialToken?: boolean;
  highRisk?: boolean;
  disabledReason?: string;
  nextStep?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentConnectionRepairActionsResponse {
  agentId: string;
  hasFailure: boolean;
  denyReason?: string;
  securityEventId?: string;
  summary?: string;
  actions?: CoreAgentConnectionRepairAction[];
  metadata?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreAgentConnectionRepairActionRequest {
  operatorId?: string;
  reason?: string;
  credentialToken?: string;
  credentialHash?: string;
  publicKeyFingerprint?: string;
  credentialExpiresAt?: string;
  revokeExisting?: boolean;
  enableAfterRepair?: boolean;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentConnectionRepairActionResult {
  agentId: string;
  actionCode: string;
  status: string;
  message?: string;
  profile?: CoreAgentProfile;
  latestAuthFailure?: CoreAgentLatestAuthFailureResponse;
  nextActions?: CoreAgentConnectionRepairAction[];
  troubleshooting?: CoreAgentSetupTroubleshootingStep[];
  metadata?: Record<string, unknown>;
  occurredAt?: string;
}

export interface CoreAgentLatestAuthFailureResponse {
  agentId: string;
  hasFailure: boolean;
  securityEventId?: string;
  eventType?: string;
  denyReason?: string;
  reason?: string;
  summary?: string;
  gatewayNodeId?: string;
  claimedAgentId?: string;
  remoteAddress?: string;
  occurredAt?: string;
  securityEventLink?: string;
  troubleshooting?: CoreAgentSetupTroubleshootingStep[];
  repairActions?: CoreAgentConnectionRepairAction[];
  metadata?: Record<string, unknown>;
  generatedAt?: string;
}

export interface AgentSecurityEvent {
  eventId: string;
  agentId?: string;
  claimedAgentId?: string;
  eventType: string;
  severity?: "INFO" | "WARN" | "ERROR" | "CRITICAL" | string;
  reason?: string;
  remoteAddress?: string;
  gatewayNodeId?: string;
  occurredAt: string;
  payload?: unknown;
}

export interface CoreAgentDirectorySnapshot {
  agentId: string;
  status?: string;
  assignable?: boolean;
  gatewayNodeId?: string;
  nodeId?: string;
  sessionId?: string;
  siteId?: string;
  connectedAt?: string;
  lastSeenAt?: string;
  [key: string]: unknown;
}

export interface CoreAgentRuntimeView {
  profile?: CoreAgentProfile;
  directory?: CoreAgentDirectorySnapshot;
  coreGovernanceAssignable?: boolean;
  coreDirectoryAssignable?: boolean;
  sourceOfTruth?: string;
}

export interface CoreAdapterAction {
  actionId: string;
  idempotencyKey?: string;
  incidentId?: string;
  taskId?: string;
  dispatchRequestId?: string;
  assignmentId?: string;
  agentId?: string;
  adapterName?: string;
  adapterType?: "MCP" | "ISSUE_TRACKING" | string;
  actionType?:
    "MCP_CONTEXT_FETCH" | "ISSUE_CREATE" | "ISSUE_UPDATE_COMMENT" | string;
  status?:
    | "PENDING"
    | "CLAIMED"
    | "EXECUTING"
    | "RETRY_WAITING"
    | "EXECUTOR_UNAVAILABLE"
    | "SUPPRESSED"
    | "COMPLETED"
    | "FAILED"
    | "CANCELLED"
    | string;
  reason?: string;
  responseRef?: string;
  payload?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
  executingAt?: string;
  completedAt?: string;
  failedAt?: string;
  nextAttemptAt?: string;
  retryWaitingAt?: string;
  executorUnavailableAt?: string;
  claimedBy?: string;
  attemptCount?: number;
  maxAttempts?: number;
  executorName?: string;
  lastError?: string;
}

export interface CoreAdapterActionMetadata {
  store?: string;
  createSuppressedRecords?: boolean;
  mcpEnabled?: boolean;
  issueEnabled?: boolean;
  issueCreateOnCompletedTask?: boolean;
  issueCreateOnFailedTask?: boolean;
  issueUpdateExistingIssueComment?: boolean;
  executorMode?: string;
  executorEmbeddedMode?: boolean;
  executorExternalMode?: boolean;
  executorDisabledMode?: boolean;
  executorEnabled?: boolean;
  executorAutoExecutePending?: boolean;
  executorBatchSize?: number;
  executorMaxAttempts?: number;
  executorMockEnabled?: boolean;
  executorExecutionTimeout?: string;
  executorCircuitBreakerEnabled?: boolean;
  executorAuditStore?: string;
  issueDefaultVendor?: string;
  redmineMockEnabled?: boolean;
  issueScopedIdentityEnabled?: boolean;
  issueScopedIdentityRequired?: boolean;
  gitlabMockEnabled?: boolean;
  [key: string]: unknown;
}

export interface CoreRecoveryApprovalRequest {
  approvalId: string;
  status:
    | "PENDING"
    | "APPROVED"
    | "REJECTED"
    | "EXECUTED"
    | "CANCELLED"
    | "EXPIRED"
    | "FAILED"
    | string;
  action: string;
  targetType: string;
  targetId: string;
  dispatchRequestId?: string;
  taskId?: string;
  agentId?: string;
  riskLevel?: string;
  requestedBy?: string;
  requesterPrincipal?: string;
  requesterRole?: string;
  requestReason?: string;
  requestId?: string;
  requestClientAddress?: string;
  requestUserAgent?: string;
  approvalReason?: string;
  approvedBy?: string;
  approverPrincipal?: string;
  approverRole?: string;
  approvalRequestId?: string;
  approvalClientAddress?: string;
  approvalUserAgent?: string;
  rejectedBy?: string;
  rejectedReason?: string;
  cancelledBy?: string;
  cancelledReason?: string;
  executionResult?: string;
  executionError?: string;
  expiresAt?: string;
  approvedAt?: string;
  executedAt?: string;
  rejectedAt?: string;
  cancelledAt?: string;
  createdAt?: string;
  updatedAt?: string;
  payloadJson?: string;
}

export interface CoreRecoveryRunbookEntry {
  alertCode: string;
  title: string;
  diagnosis: string[];
  safeActions: string[];
  escalation: string[];
}

export interface CoreRecoveryGovernancePolicyView {
  requireReason?: boolean;
  minReasonLength?: number;
  requireConfirmation?: boolean;
  moderateConfirmationPhrase?: string;
  highRiskConfirmationPhrase?: string;
  approvalConfirmationPhrase?: string;
  requireDualControlForHighRisk?: boolean;
  forbidSelfApproval?: boolean;
  approvalTtl?: string;
  recoveryOperatorRole?: string;
  recoveryAdminRole?: string;
  recoveryApproverRole?: string;
}

export interface CoreRecoveryOperatorRunbook {
  version: string;
  policy?: CoreRecoveryGovernancePolicyView;
  entries: CoreRecoveryRunbookEntry[];
}

export interface CoreRecoveryOperationTotals {
  gatewayDelivered: number;
  gatewayRetryWaiting: number;
  runtimeDeliveryFailed: number;
  runtimeBackoffApplied: number;
  taskRequeued: number;
  delayedRequeueScheduled: number;
  delayedRequeueClaimed: number;
  delayedRequeueFailed: number;
  recoveryExhausted: number;
  deadLettered: number;
}

export interface CoreRecoveryMetricBucket {
  key: string;
  count: number;
  latestOccurredAt?: string;
}

export interface CoreRecoveryAlertEvaluation {
  code: string;
  severity: "OK" | "WARNING" | "CRITICAL" | string;
  metric: string;
  observed: number;
  warningThreshold: number;
  criticalThreshold: number;
  message: string;
}

export interface CoreRecoveryAlertPolicyView {
  enabled: boolean;
  window: string;
  historyLimit: number;
  runtimeFailureWarningThreshold: number;
  runtimeFailureCriticalThreshold: number;
  delayedRequeueWarningThreshold: number;
  delayedRequeueCriticalThreshold: number;
  deadLetterWarningThreshold: number;
  deadLetterCriticalThreshold: number;
  scannerFailureWarningThreshold: number;
  scannerFailureCriticalThreshold: number;
  recoveryExhaustedWarningThreshold: number;
  recoveryExhaustedCriticalThreshold: number;
}

export interface CoreRecoveryOperationMetricsSnapshot {
  status: "OK" | "WARNING" | "CRITICAL" | "DISABLED" | string;
  generatedAt: string;
  windowStart: string;
  window: string;
  historyLimit: number;
  totalEvents: number;
  totals: CoreRecoveryOperationTotals;
  byEventType: CoreRecoveryMetricBucket[];
  byAgent: CoreRecoveryMetricBucket[];
  alerts: CoreRecoveryAlertEvaluation[];
  recentCriticalEvents: CoreDispatchAttemptHistoryRecord[];
  alertPolicy: CoreRecoveryAlertPolicyView;
}

export interface CoreDashboardSnapshot {
  generatedAt: string;
  controlPlane?: Record<string, unknown>;
  agentGovernance?: Record<string, unknown>;
  incidents?: Record<string, unknown>;
  tasks?: Record<string, unknown>;
  dispatch?: Record<string, unknown>;
  callbacks?: Record<string, unknown>;
  security?: Record<string, unknown>;
  operationalSlo?: Record<string, unknown>;
  agents?: CoreAgentProfile[];
  recentTasks?: CoreTaskRuntimeView[];
  recentSecurityEvents?: AgentSecurityEvent[];
}

export type AgentEnrollmentCreateRequest = Partial<
  Omit<AgentEnrollmentRequest, "enrollmentId" | "status">
>;

export interface AgentEnrollmentApprovalRequest {
  agentId?: string;
  approvedBy?: string;
  tenantId?: string;
  agentName?: string;
  agentType?: string;
  ownerTeam?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  businessOwnerUserId?: string;
  technicalStewardUserId?: string;
  responsibilityRoleId?: string;
  description?: string;
  comment?: string;
  capabilities?: string[];
  scopes?: CoreAgentAuthorizationScope[];
  credentialType?: string;
  credentialToken?: string;
  credentialHash?: string;
  publicKeyFingerprint?: string;
  credentialExpiresAt?: string;
}

export interface AgentProfileUpdateRequest {
  tenantId?: string;
  agentName?: string;
  agentType?: string;
  ownerTeam?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  businessOwnerUserId?: string;
  technicalStewardUserId?: string;
  responsibilityRoleId?: string;
  description?: string;
  approvalStatus?: AgentApprovalStatus;
  enabled?: boolean;
  riskStatus?: AgentRiskStatus;
  capabilities?: string[];
  scopes?: CoreAgentAuthorizationScope[];
  operatorId?: string;
  reason?: string;
}

export interface CoreAgentSkillRegistryMetadata {
  taxonomyVersion?: string;
  storeMode?: string;
}

export interface CoreAgentSkillDefinition {
  skillCode: string;
  displayName?: string;
  domain?: string;
  description?: string;
  taxonomyVersion?: string;
  taskDefinitionId?: string;
  sourceSystem?: string;
  taskType?: string;
  providers?: string[];
  taskTypes?: string[];
  operations?: string[];
  toolPolicies?: string[];
  resourceScopes?: string[];
  dataClasses?: string[];
  riskLevel?: string;
  requiresHumanApproval?: boolean;
  maskingRequired?: boolean;
  enabled?: boolean;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export type CoreAgentSkillLifecycleStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "PUBLISHED"
  | "DEPRECATED"
  | "ROLLED_BACK";

export interface CoreAgentSkillVersion {
  skillCode?: string;
  version?: number;
  status?: CoreAgentSkillLifecycleStatus | string;
  definition?: CoreAgentSkillDefinition;
  submittedBy?: string;
  submittedAt?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewComment?: string;
  publishedBy?: string;
  publishedAt?: string;
  supersedesVersion?: number;
  rollbackOfVersion?: number;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentSkillAuditEntry {
  auditId?: string;
  skillCode?: string;
  version?: number;
  action?: string;
  operatorId?: string;
  reason?: string;
  fromStatus?: CoreAgentSkillLifecycleStatus | string;
  toStatus?: CoreAgentSkillLifecycleStatus | string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
}

export interface CoreAgentSkillWorkflowCommand {
  operatorId?: string;
  reason?: string;
  operatorRoles?: string[];
  definition?: CoreAgentSkillDefinition;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSkillWorkflowResult {
  skillCode?: string;
  version?: number;
  status?: CoreAgentSkillLifecycleStatus | string;
  definition?: CoreAgentSkillDefinition;
  auditEntry?: CoreAgentSkillAuditEntry;
  message?: string;
  metadata?: Record<string, unknown>;
  occurredAt?: string;
}

export interface CoreAgentSkillDiffEntry {
  field?: string;
  changeType?: string;
  beforeValues?: string[];
  afterValues?: string[];
  addedValues?: string[];
  removedValues?: string[];
  breakingChange?: boolean;
  note?: string;
}

export interface CoreAgentSkillDiffResult {
  skillCode?: string;
  baseVersion?: number;
  targetVersion?: number;
  baseStatus?: CoreAgentSkillLifecycleStatus | string;
  targetStatus?: CoreAgentSkillLifecycleStatus | string;
  entries?: CoreAgentSkillDiffEntry[];
  changedFields?: string[];
  breakingFields?: string[];
  breakingChange?: boolean;
  summary?: string;
  generatedAt?: string;
}

export interface CoreAgentSkillImpactAgent {
  agentId?: string;
  skillCode?: string;
  policyVersion?: number;
  enabled?: boolean;
  approvedBy?: string;
  approvedAt?: string;
  impactReason?: string;
}

export interface CoreAgentSkillImpactAnalysisResult {
  skillCode?: string;
  version?: number;
  severity?: string;
  breakingChange?: boolean;
  impactedAgents?: CoreAgentSkillImpactAgent[];
  impactedAgentIds?: string[];
  impactedTaskTypes?: string[];
  impactedProviders?: string[];
  impactedDataClasses?: string[];
  impactedToolPolicies?: string[];
  notes?: string[];
  diff?: CoreAgentSkillDiffResult;
  generatedAt?: string;
}

export interface CoreAgentSkillApprovalPolicy {
  skillCode?: string;
  enabled?: boolean;
  submitRoles?: string[];
  approveRoles?: string[];
  publishRoles?: string[];
  rollbackRoles?: string[];
  separationOfDuties?: boolean;
  updatedBy?: string;
  updatedAt?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentCapabilityDriftItem {
  agentId?: string;
  skillCode?: string;
  driftType?: string;
  severity?: string;
  approved?: boolean;
  reported?: boolean;
  taxonomyKnown?: boolean;
  taxonomyEnabled?: boolean;
  deprecated?: boolean;
  replacementSkillCodes?: string[];
  suggestedAction?: string;
  detail?: string;
  metadata?: Record<string, unknown>;
  detectedAt?: string;
}

export interface CoreAgentCapabilityDriftReport {
  agentId?: string;
  taxonomyVersion?: string;
  scannedAgents?: number;
  alignedCount?: number;
  driftCount?: number;
  highSeverityCount?: number;
  driftTypeCounts?: Record<string, number>;
  items?: CoreAgentCapabilityDriftItem[];
  generatedAt?: string;
}

export interface CoreSkillDriftPolicyEvaluationRequest {
  operatorId?: string;
  limit?: number;
  persistEvents?: boolean;
}

export interface CoreSkillDriftPolicyAction {
  agentId?: string;
  skillCode?: string;
  driftType?: string;
  severity?: string;
  recommendedEnforcement?:
    "REVIEW_REQUIRED" | "DISPATCH_DEGRADED" | "QUARANTINE_RECOMMENDED" | string;
  suggestedAction?: string;
}

export interface CoreSkillDriftPolicyEvaluationResponse {
  scannedAgents?: number;
  driftCount?: number;
  highSeverityCount?: number;
  actionCount?: number;
  quarantineRecommendations?: number;
  dispatchDegradeRecommendations?: number;
  persistedEvents?: number;
  actions?: CoreSkillDriftPolicyAction[];
  evaluatedAt?: string;
}

export interface CoreAgentSkillDeprecationPlan {
  skillCode?: string;
  status?: string;
  replacementSkillCodes?: string[];
  migrationDeadline?: string;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSkillDeprecationCommand {
  status?: string;
  replacementSkillCodes?: string[];
  migrationDeadline?: string;
  operatorId?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSkillDeprecationMigrationPlan {
  skillCode?: string;
  status?: string;
  deprecated?: boolean;
  canDeprecate?: boolean;
  replacementSkillCodes?: string[];
  impactedAgentIds?: string[];
  impactedAgents?: CoreAgentSkillImpactAgent[];
  driftItems?: CoreAgentCapabilityDriftItem[];
  migrationSteps?: string[];
  blockingReasons?: string[];
  severity?: string;
  metadata?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreAgentSkillDependencyEdge {
  edgeId?: string;
  sourceSkillCode?: string;
  targetSkillCode?: string;
  relationType?: string;
  required?: boolean;
  enabled?: boolean;
  confidence?: number;
  description?: string;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSkillDependencyCommand {
  operatorId?: string;
  reason?: string;
  edges?: CoreAgentSkillDependencyEdge[];
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSkillDependencyGraph {
  rootSkillCode?: string;
  taxonomyVersion?: string;
  depth?: number;
  nodes?: string[];
  edges?: CoreAgentSkillDependencyEdge[];
  requiredSkillCodes?: string[];
  replacementSkillCodes?: string[];
  conflictSkillCodes?: string[];
  cycleDetected?: boolean;
  warnings?: string[];
  metadata?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreAgentSkillRemediationAction {
  actionId?: string;
  agentId?: string;
  skillCode?: string;
  actionType?: string;
  severity?: string;
  executable?: boolean;
  targetSkillCode?: string;
  prerequisites?: string[];
  reason?: string;
  commandHint?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentSkillRemediationProposal {
  agentId?: string;
  taxonomyVersion?: string;
  sourceDriftCount?: number;
  highSeverityCount?: number;
  actions?: CoreAgentSkillRemediationAction[];
  summary?: string[];
  metadata?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreAgentRemediationAction {
  actionId?: string;
  agentId?: string;
  actionType?: string;
  severity?: string;
  executable?: boolean;
  reason?: string;
  prerequisites?: string[];
  commandHint?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentRemediationProposalRequest {
  operatorId?: string;
  reason?: string;
  sourceTaskId?: string;
  sourceRoutingDecisionId?: string;
  persistEvent?: boolean;
}

export interface CoreAgentRemediationProposal {
  proposalId?: string;
  agentId?: string;
  severity?: string;
  executableActionCount?: number;
  reviewOnlyActionCount?: number;
  actions?: CoreAgentRemediationAction[];
  skillProposal?: CoreAgentSkillRemediationProposal;
  context?: Record<string, unknown>;
  summary?: string[];
  generatedAt?: string;
}

export interface CoreAgentRemediationWorkflowCreateRequest {
  proposalId?: string;
  actionIds?: string[];
  operatorId?: string;
  reason?: string;
  riskAcknowledged?: boolean;
}

export interface CoreAgentRemediationWorkflowDecisionRequest {
  operatorId?: string;
  reason?: string;
  dryRun?: boolean;
}

export interface CoreAgentRemediationWorkflowHistoryEntry {
  historyId?: string;
  eventType?: string;
  operatorId?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
  occurredAt?: string;
}

export interface CoreAgentRemediationWorkflowActionExecution {
  actionExecutionId?: string;
  workflowId?: string;
  agentId?: string;
  actionId?: string;
  actionType?: string;
  idempotencyKey?: string;
  status?: "PENDING" | "RUNNING" | "SUCCEEDED" | "SKIPPED" | "FAILED" | string;
  attemptCount?: number;
  lastOperatorId?: string;
  lastReason?: string;
  lastResult?: Record<string, unknown>;
  lastError?: string;
  firstAttemptAt?: string;
  lastAttemptAt?: string;
  completedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentRemediationWorkflow {
  workflowId?: string;
  proposalId?: string;
  agentId?: string;
  status?:
    | "PENDING_APPROVAL"
    | "APPROVED"
    | "REJECTED"
    | "CANCELLED"
    | "EXECUTED"
    | string;
  severity?: string;
  approvalRequired?: boolean;
  actions?: CoreAgentRemediationAction[];
  rollbackSuggestions?: string[];
  history?: CoreAgentRemediationWorkflowHistoryEntry[];
  actionExecutions?: CoreAgentRemediationWorkflowActionExecution[];
  createdBy?: string;
  lastOperatorId?: string;
  createdAt?: string;
  updatedAt?: string;
  metadata?: Record<string, unknown>;
  executionLeaseOwner?: string;
  executionLeaseAcquiredAt?: string;
  executionLeaseExpiresAt?: string;
  executionLeaseRemainingSeconds?: number;
  executionLeaseActive?: boolean;
}

export interface CoreAgentRemediationStaleWorkflowExecutionLease {
  workflowId?: string;
  agentId?: string;
  status?: string;
  severity?: string;
  leaseOwner?: string;
  leaseAcquiredAt?: string;
  leaseExpiresAt?: string;
  leaseExpiredSeconds?: number;
  updatedAt?: string;
}

export interface CoreAgentRemediationRecoveredWorkflowExecutionLease {
  workflowId?: string;
  agentId?: string;
  operatorId?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
  occurredAt?: string;
}

export interface CoreAgentRemediationStaleLeaseQueue {
  generatedAt?: string;
  staleLeases?: CoreAgentRemediationStaleWorkflowExecutionLease[];
}

export interface CoreAgentRemediationRecoveredLeaseQueue {
  generatedAt?: string;
  recoveredLeases?: CoreAgentRemediationRecoveredWorkflowExecutionLease[];
}

export interface CoreAgentRemediationStaleLeaseRecoveryRun {
  recoveredAt?: string;
  scannedCount?: number;
  recoveredCount?: number;
  raceLostCount?: number;
  recovered?: CoreAgentRemediationStaleWorkflowExecutionLease[];
  races?: CoreAgentRemediationStaleWorkflowExecutionLease[];
}

export interface CoreAgentApprovedSkill {
  agentId?: string;
  skillCode: string;
  policyVersion?: number;
  enabled?: boolean;
  approvedBy?: string;
  approvedAt?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentApprovedSkillSyncCommand {
  skillCodes?: string[];
  enabled?: boolean;
  syncProfileCapabilities?: boolean;
  operatorId?: string;
  reason?: string;
}

export interface CoreAgentApprovedSkillSyncResult {
  agentId?: string;
  approvedSkillCodes?: string[];
  profileCapabilityCodes?: string[];
  addedToApprovedSkills?: string[];
  addedToProfileCapabilities?: string[];
  profileCapabilitiesSynced?: boolean;
  reason?: string;
  syncedAt?: string;
}

// Task dispatch-contract resolution is a compatibility evidence surface; prefer the canonical Source Flow / Agent Pool simulation for current operator decisions.

export interface CoreAgentSkillEvaluationRequest {
  domain?: string;
  provider?: string;
  taskType?: string;
  siteCode?: string;
  operation?: string;
  requiredToolPolicy?: string;
  requiredCapabilities?: string[];
  dataClasses?: string[];
}

export interface CoreAgentSkillEvaluationResult {
  agentId?: string;
  taxonomyVersion?: string;
  eligible?: boolean;
  reason?: string;
  missingRequirements?: string[];
  matchedSkillCodes?: string[];
  approvedSkillCodes?: string[];
  reportedSkillCodes?: string[];
  effectiveSkillCodes?: string[];
  evaluatedAt?: string;
}

export interface CoreAdapterExecutorAuditRecord {
  auditId?: string;
  actionId?: string;
  adapterType?: string;
  executorName?: string;
  outcome?: string;
  status?: string;
  message?: string;
  error?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt?: string;
  durationMs?: number;
  [key: string]: unknown;
}

export interface CoreEnforceObservabilitySnapshot {
  generatedAt: string;
  source: string;
  mode: string;
  window: string;
  v2Allowed: number;
  v2Blocked: number;
  noCandidate: number;
  fallbackDenied: number;
  qualityUnavailable: number;
  scoreBreakdownMissing: number;
  blockedRate: number;
  noCandidateRate: number;
  qualityUnavailableRate: number;
  latestAcceptanceArtifact?: string;
  latestReadinessArtifact?: string;
  latestArchiveManifest?: string;
  readinessBlockingCount?: number;
}

export interface CoreEnforceRoutingAuditRecord {
  decisionId: string;
  taskId?: string;
  agentId?: string;
  policyCode?: string;
  blockingCode?: string;
  eligibilityEngineMode?: string;
  eligibilityV2Applied?: boolean;
  eligibilityV2CandidateEligible?: boolean;
  eligibilityV2Score?: number;
  eligibilityV2BlockingReasons?: string[];
  eligibilityV2ScoreBreakdown?: Record<string, unknown>;
  createdAt?: string;
}

export interface CoreEnforceOperatorIncidentRequest {
  triggerCode: string;
  severity?: string;
  taskId?: string;
  agentId?: string;
  message?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreEnforceOperatorIncidentResult {
  incidentId?: string;
  issueUrl?: string;
  status?: string;
  message?: string;
}

export interface CoreEnforceLegacyFinalReportItem {
  category: string;
  count: number;
  severity?: string;
  sampleRefs?: string[];
}

export interface CoreEnforceArtifactRetentionRecord {
  artifactName: string;
  artifactPath?: string;
  generatedAt?: string;
  retainedUntil?: string;
  source?: string;
}

// Phase 4-4: Dispatch-contract readiness is a legacy repair/readiness surface, not the Current setup API.

export interface CoreSourceSystem {
  tenantId?: string;
  sourceSystemId: string;
  displayName: string;
  description?: string;
  status: "ACTIVE" | "DISABLED" | "RETIRED" | string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreSourceSystemCommand {
  tenantId?: string;
  sourceSystemId: string;
  displayName: string;
  description?: string;
  status?: "ACTIVE" | "DISABLED" | "RETIRED" | string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
}

export interface CoreWorkloadSourceRegistration {
  tenantId: string;
  sourceRegistrationId: string;
  sourceSystemId: string;
  registrationName: string;
  channelType: "EVENT" | "API" | "HUMAN" | "SCHEDULE" | "A2A_INBOUND" | "REPLAY" | string;
  principalBindingMode: "STATIC" | "DYNAMIC" | string;
  allowedPrincipalTypes: string[];
  staticPrincipalRef?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  allowedEventTypes: string[];
  allowedObjectTypes: string[];
  inputSchemas: string[];
  idempotencyStrategy: "NONE" | "OPTIONAL_KEY" | "REQUIRED_KEY" | "SOURCE_EVENT_ID" | string;
  idempotencyRetentionSeconds: number;
  orderingStrategy: string;
  acknowledgementMode: "SYNC_RESPONSE" | "CALLBACK" | "OUTBOX_EVENT" | "POLL" | "NONE" | string;
  rateLimitPerMinute?: number;
  quotaPerDay?: number;
  dataClassificationProfile?: string;
  residencyProfile?: string;
  status: "ACTIVE" | "DISABLED" | "RETIRED" | string;
  effectiveFrom?: string;
  effectiveTo?: string;
  defaultRegistration: boolean;
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreWorkloadSourceRegistrationCommand {
  sourceRegistrationId?: string;
  registrationName: string;
  channelType?: string;
  principalBindingMode?: string;
  allowedPrincipalTypes?: string[];
  staticPrincipalRef?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  allowedEventTypes?: string[];
  allowedObjectTypes?: string[];
  inputSchemas?: string[];
  idempotencyStrategy?: string;
  idempotencyRetentionSeconds?: number;
  orderingStrategy?: string;
  acknowledgementMode?: string;
  rateLimitPerMinute?: number;
  quotaPerDay?: number;
  dataClassificationProfile?: string;
  residencyProfile?: string;
  status?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  defaultRegistration?: boolean;
}

export type CoreDispatchRuleScope = 'EXTERNAL_INTAKE' | 'A2A_DISPATCH' | 'RESULT_CALLBACK' | 'ISSUE_TRACKING';

export interface CoreDispatchFlowReadinessRequest {
  tenantId?: string;
  flowId?: string;
  sourceSystem?: string;
  originSourceSystem?: string;
  targetSystem?: string;
  eventStage?: CoreDispatchEventStage | string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  requestedSkill?: string;
  agentId?: string;
  severity?: string;
  message?: string;
  attributes?: Record<string, unknown>;
}

export interface CoreDispatchFlowReadinessCheck {
  code: string;
  status: string;
  message: string;
  blocking?: boolean;
  nextAction?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchFlowCandidateAgentView {
  agentId?: string;
  agentName?: string;
  eventStage?: CoreDispatchEventStage | string;
  agentRole?: string;
  assignmentStatus?: string;
  approvalStatus?: string;
  readinessStatus?: string;
  runtimeStatus?: string;
  skillGrantStatus?: string;
  assignmentActive?: boolean;
  approvalReady?: boolean;
  readinessReady?: boolean;
  requestedSkillGranted?: boolean;
  dispatchable?: boolean;
  blockingReasons?: string[];
}

export interface CoreDispatchFlowReadinessResponse {
  tenantId?: string;
  flowId?: string;
  flowCode?: string;
  ruleId?: string;
  ruleCode?: string;
  sourceSystem?: string;
  originSourceSystem?: string;
  targetSystem?: string;
  eventStage?: CoreDispatchEventStage | string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  requestedSkill?: string;
  selectedAgentId?: string;
  ready?: boolean;
  dispatchable?: boolean;
  status?: string;
  summary?: string;
  firstBlockingCode?: string;
  firstBlockingReason?: string;
  checks?: CoreDispatchFlowReadinessCheck[];
  requiredCapabilities?: string[];
  /** Compatibility projection from older readiness payloads. */
  requiredSkills?: string[];
  candidateAgents?: CoreDispatchFlowCandidateAgentView[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreDispatchSimulationRequest {
  tenantId?: string;
  flowId?: string;
  sourceSystem?: string;
  originSourceSystem?: string;
  targetSystem?: string;
  eventStage?: CoreDispatchEventStage | string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  severity?: string;
  message?: string;
  siteId?: string;
  plantId?: string;
  attributes?: Record<string, unknown>;
  includeRuntimeSnapshot?: boolean;
  /** C7 separates side-effect-free Draft evaluation from ACTIVE-only runtime readiness. */
  evaluationMode?: 'DRAFT_SIMULATION' | 'RUNTIME_READINESS' | string;
}

export interface CoreDispatchSimulationCandidateView {
  agentId?: string;
  status?: string;
  score?: number;
  eligible?: boolean;
  selected?: boolean;
  blockingReasons?: string[];
  reason?: string;
  scoreBreakdown?: Record<string, unknown>;
}

export interface CoreDispatchSimulationResponse {
  tenantId?: string;
  sourceSystem?: string;
  eventStage?: CoreDispatchEventStage | string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  matchedFlowId?: string;
  /** Persisted Source Flow version used by this evaluation. */
  flowVersion?: string;
  evaluationMode?: 'DRAFT_SIMULATION' | 'RUNTIME_READINESS' | string;
  matchedRuleId?: string;
  resolutionType?: string;
  targetPoolId?: string;
  targetPoolCode?: string;
  selectionStrategy?: string;
  poolMemberCount?: number;
  candidateAgentCount?: number;
  eligibleAgentCount?: number;
  selectedAgentId?: string;
  manualOnly?: boolean;
  dispatchable?: boolean;
  status?: string;
  summary?: string;
  blockerCode?: string;
  blockerReason?: string;
  sideEffectFree?: boolean;
  createdArtifacts?: string[];
  candidateEvidence?: CoreDispatchSimulationCandidateView[];
  blockedCandidates?: CoreDispatchSimulationCandidateView[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreDispatchFlowRuleView {
  tenantId?: string;
  ruleId?: string;
  flowId?: string;
  ruleCode?: string;
  ruleName?: string;
  serviceCode?: string;
  ruleScope?: CoreDispatchRuleScope | string;
  eventStage?: CoreDispatchEventStage | string;
  sourceSystem?: string;
  originSourceSystem?: string;
  targetSystem?: string;
  eventType?: string;
  objectType?: string;
  errorCode?: string;
  condition?: Record<string, unknown>;
  matchMode?: string;
  targetPoolId?: string;
  targetPoolCode?: string;
  requestedSkill?: string;
  capabilityRequirementMode?: string;
  requiredOperation?: string;
  sideEffectLevel?: string;
  candidatePoolMode?: string;
  routingStrategy?: string;
  explicitActionAuthorizationRequired?: boolean;
  requirementModelVersion?: number;
  handoffMode?: string;
  issuePolicyId?: string;
  issueSyncPolicy?: 'NONE' | 'OPTIONAL' | 'REQUIRED' | 'MANUAL' | string;
  priority?: number;
  enabled?: boolean;
  legacyStatus?: string;
  updatedAt?: string;
}

export interface CoreDispatchFlowRuleConflictView {
  flowId?: string;
  ruleIdA?: string;
  ruleCodeA?: string;
  ruleIdB?: string;
  ruleCodeB?: string;
  priority?: number;
  conflictReason?: string;
}

export interface CoreFlowDirectAgentCompatibilityBinding {
  bridgeId?: string;
  flowAgentAssignmentId?: string;
  eventStage?: string;
  agentId?: string;
  legacySkillCode?: string;
  capabilityCode?: string;
  providerId?: string;
  capabilityBindingId?: string;
  bridgeStatus?: string;
  reasonCodes?: string[];
  bridgeRevision?: number;
}

export interface CoreFlowCapabilityBridgeRefresh {
  flowId?: string;
  bridgeRevision?: number;
  readyCount?: number;
  blockedCount?: number;
  rows?: CoreFlowDirectAgentCompatibilityBinding[];
}

export interface CoreFlowCapabilityLegacyEquivalenceEvidence {
  evidenceId?: string;
  taskId?: string;
  taskVersion?: number;
  assignmentId?: string;
  flowId?: string;
  ruleId?: string;
  flowMatchDecisionId?: string;
  legacySelectedAgentId?: string;
  legacyCandidateAgentIds?: string[];
  requiredCapabilities?: string[];
  directBridgeAgentIds?: string[];
  directBridgeBindingIds?: string[];
  fullyRepresentableAgentIds?: string[];
  selectedExecutorEquivalent?: boolean;
  candidateSetEquivalent?: boolean;
  comparisonResult?: string;
  reasonCodes?: string[];
  evaluatorVersion?: string;
  evaluatedBy?: string;
  evaluatedAt?: string;
}

export interface CoreFlowCapabilityEquivalenceReadiness {
  flowId?: string;
  migrationState?: string;
  authoritativeMode?: string;
  compatibilityBridgeRevision?: number;
  sampleSize?: number;
  minimumSampleSize?: number;
  selectedExecutorEquivalentCount?: number;
  candidateSetEquivalentCount?: number;
  exactEquivalenceCount?: number;
  blockedBridgeRows?: number;
  selectedEquivalenceRate?: number;
  requiredSelectedEquivalenceRate?: number;
  recommendedState?: string;
  blockers?: string[];
  lastEvaluatedAt?: string;
}

export interface CoreDispatchFlowRequiredCapabilityView {
  tenantId?: string;
  id?: string;
  flowId?: string;
  ruleId?: string;
  eventStage?: CoreDispatchEventStage | string;
  agentRole?: string;
  capabilityCode?: string;
  capabilityName?: string;
  capabilityKind?: string;
  authorityCode?: string;
  required?: boolean;
  openClawCapability?: boolean;
  description?: string;
  legacyStatus?: string;
  /** Compatibility projection for the existing flow_required_capabilities table. Standard UI must use capabilityCode. */
  skillCode?: string;
  skillName?: string;
  skillKind?: string;
  openClawSkill?: boolean;
}

export type CoreDispatchFlowRequiredSkillView = CoreDispatchFlowRequiredCapabilityView;

export interface CoreAgentPoolView {
  tenantId?: string;
  poolId: string;
  poolCode?: string;
  poolName?: string;
  sourceSystem?: string;
  poolType?: 'TRIAGE' | 'RESOLUTION' | 'ESCALATION' | 'MANUAL_REVIEW' | string;
  selectionStrategy?: 'ROUND_ROBIN' | 'LOWEST_LOAD' | 'LOCAL_FIRST' | 'WEIGHTED_SCORE' | 'MANUAL_ONLY' | string;
  status?: string;
  description?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  memberCount?: number;
  availableAgentCount?: number;
  members?: CoreAgentPoolMemberView[];
  metadata?: Record<string, unknown>;
  version?: number;
  updatedBy?: string;
  updatedAt?: string;
}

export interface CoreAgentPoolMemberView {
  tenantId?: string;
  poolId: string;
  poolCode?: string;
  agentId: string;
  agentName?: string;
  memberStatus?: string;
  priority?: number;
  weight?: number;
  approvalStatus?: string;
  runtimeStatus?: string;
  metadata?: Record<string, unknown>;
  version?: number;
  updatedBy?: string;
  updatedAt?: string;
}

export interface CoreDispatchFlowAgentOptionView {
  tenantId?: string;
  agentId: string;
  agentName?: string;
  approvalStatus?: string;
  enabled?: boolean;
  riskStatus?: string;
  runtimeStatus?: string;
  runtimeConnected?: boolean;
  heartbeatHealthy?: boolean;
  capacityAvailable?: boolean;
  activeFlowCount?: number;
  selectable?: boolean;
  disabledReason?: string | null;
}

export interface CoreDispatchFlowAgentView {
  tenantId?: string;
  id?: string;
  flowId?: string;
  agentId?: string;
  agentName?: string;
  eventStage?: CoreDispatchEventStage | string;
  agentRole?: string;
  assignmentStatus?: string;
  runtimeStatus?: string;
  approvalStatus?: string;
  capabilityCoverageTotal?: number;
  capabilityCoverageMatched?: number;
  missingCapabilities?: string[];
  /** Compatibility fields retained only for older API payloads. */
  skillCoverageTotal?: number;
  skillCoverageMatched?: number;
  missingSkills?: string[];
  missingAuthorities?: string[];
  readinessStatus?: string;
  legacyStatus?: string;
  updatedAt?: string;
}

export type CoreEventIntakeStage = CoreDispatchEventStage;

export interface CoreEventIntakeEnvelope {
  tenantId: string;
  sourceSystem: string;
  sourceRegistrationId?: string;
  sourceEventId?: string;
  idempotencyKey?: string;
  assertedOriginPrincipal?: string;
  eventType?: string;
  eventStage?: CoreEventIntakeStage | string;
  originSourceSystem?: string;
  targetSystem?: string | null;
  requestedSkill?: string | null;
  capabilityRequirementMode?: string;
  requiredOperation?: string;
  sideEffectLevel?: string;
  candidatePoolMode?: string;
  routingStrategy?: string;
  explicitActionAuthorizationRequired?: boolean;
  requirementModelVersion?: number;
  handoffMode?: string;
  correlationId?: string;
  parentTaskId?: string;
  siteId?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  errorCode?: string;
  severity?: string;
  message?: string;
  occurredAt?: string;
  attributes?: Record<string, unknown>;
}

export interface CoreEventIntakeDecisionResponse {
  eventId?: string;
  fingerprint?: string;
  incidentId?: string;
  decisionType?: string;
  duplicate?: boolean;
  occurrenceCount?: number;
  severity?: string;
  taskCreated?: boolean;
  taskId?: string;
  taskType?: string;
  assignmentCreated?: boolean;
  assignmentId?: string;
  selectedAgentId?: string;
  dispatchRequestCreated?: boolean;
  dispatchRequestId?: string;
  reason?: string;
  decidedAt?: string;
  eventStage?: CoreEventIntakeStage | string;
  originSourceSystem?: string;
  targetSystem?: string | null;
  requestedSkill?: string | null;
  capabilityRequirementMode?: string;
  requiredOperation?: string;
  sideEffectLevel?: string;
  candidatePoolMode?: string;
  routingStrategy?: string;
  explicitActionAuthorizationRequired?: boolean;
  requirementModelVersion?: number;
  handoffMode?: string | null;
  correlationId?: string | null;
  parentTaskId?: string | null;
  primaryStatus?: string;
  primaryReasonCode?: string;
  nextAction?: string;
  intakeAuthority?: {
    ingestionId?: string;
    sourceRegistrationId?: string;
    disposition?: string;
    dispositionReason?: string;
    idempotencyStatus?: string;
    replay?: boolean;
    keyExpired?: boolean;
    authenticatedPrincipalRef?: string;
    originPrincipalRef?: string;
    recordedAt?: string;
  };
}

export interface CoreDispatchFlowTraceStepView {
  sequence?: number;
  stepCode?: string;
  eventStage?: CoreDispatchEventStage | string;
  eventType?: string;
  sourceSystem?: string;
  originSourceSystem?: string;
  targetSystem?: string;
  matchedFlowId?: string;
  matchedRuleId?: string;
  requestedSkill?: string;
  routingPath?: string;
  selectedAgentId?: string;
  status?: string;
  failureStage?: string;
  fixAction?: string;
  message?: string;
  parentTaskId?: string;
  correlationId?: string;
  createdAt?: string;
  metadata?: Record<string, unknown>;
}

export interface CoreDispatchFlowTraceChainView {
  tenantId?: string;
  flowId?: string;
  flowCode?: string;
  testMode?: 'EXTERNAL' | 'A2A' | 'RESULT' | 'CHAIN' | string;
  status?: string;
  summary?: string;
  failureStage?: string;
  fixAction?: string;
  correlationId?: string;
  parentTaskId?: string;
  steps?: CoreDispatchFlowTraceStepView[];
  metadata?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreDispatchFlowView {
  tenantId?: string;
  flowId: string;
  flowCode?: string;
  flowName?: string;
  sourceSystem?: string;
  flowType?: 'SOURCE_FLOW' | string;
  defaultPoolId?: string;
  defaultCandidatePoolMode?: string;
  defaultRoutingStrategy?: string;
  defaultIssueSyncPolicy?: 'NONE' | 'OPTIONAL' | 'REQUIRED' | 'MANUAL' | string;
  /** Evidence-only request hint. Core does not persist or return this field as product state. */
  evidenceMutationSource?: string;
  status?: string;
  description?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  externalRuleCount?: number;
  a2aRuleCount?: number;
  capabilityCount?: number;
  /** Compatibility count for the existing flow_required_capabilities table. */
  skillCount?: number;
  agentCount?: number;
  lastTestStatus?: string;
  rules?: CoreDispatchFlowRuleView[];
  requiredCapabilities?: CoreDispatchFlowRequiredCapabilityView[];
  /** Compatibility collection for the existing flow_required_capabilities table. Standard UI must use requiredCapabilities semantics. */
  requiredSkills?: CoreDispatchFlowRequiredSkillView[];
  agents?: CoreDispatchFlowAgentView[];
  metadata?: Record<string, unknown>;
  version?: number;
  updatedBy?: string;
  updatedAt?: string;
}

// Task-domain compatibility re-exports. New Task product code should import from lib/types/domains/task.
export type {
  CoreTaskStatus,
  CoreDispatchStatus,
  CoreDispatchEventStage,
  CoreTaskRecord,
  CoreDispatchRequest,
  CoreTaskIssueTracking,
  CoreTaskIssueDedupSummary,
  CoreTaskRuntimeSnapshot,
  CoreTaskRuntimeView,
  CoreAgentCandidateScore,
  CoreDispatchUserFacingError,
  CoreRoutingDecisionRecord,
} from '@/lib/types/domains/taskModel';
export type {
  CoreTaskDispatchRequirementProfile,
  CoreTaskDispatchRequirements,
  CoreEligibleAgentCandidate,
  CoreTaskEligibleAgentsResponse,
  CoreDispatchEligibilityV2BlockingReason,
  CoreDispatchEligibilityV2ScoreBreakdown,
  CoreDispatchEligibilityV2PolicyMatch,
  CoreDispatchEligibilityV2Candidate,
  CoreDispatchEligibilityV2Response,
} from '@/lib/types/domains/taskEligibility';
export type {
  CoreTaskRemediationCommandType,
  CoreTaskRemediationCommandRequest,
  CoreTaskCommandSnapshot,
  CoreTaskCommandAudit,
  CoreTaskRemediationCommandResult,
  CoreRecoveryGovernanceActionRequest,
  CoreRecoveryGovernanceAuditSummary,
  CoreRecoveryGovernanceActionResult,
  CoreDispatchTimelineEvent,
  CoreDispatchTimelineResponse,
  CoreTaskCaseTimelineStepView,
  CoreTaskCaseTimelineView,
  CoreTaskDispatchEvidenceStage,
  CoreTaskDispatchRecoveryAction,
  CoreTaskDispatchContractRepairRequest,
  CoreTaskDispatchEvidenceView,
  CoreTaskRuntimeVerificationStep,
  CoreTaskRuntimeVerificationView,
  CoreAdminFailureQueueItem,
  CoreAdminFailureQueueResponse,
  CoreTaskA2AClassificationFlowContract,
  CoreTaskClassificationRequest,
  CoreTaskClassificationResult,
  CoreTaskDispatchContractResolveRequest,
  CoreTaskDispatchContractResolveResult,
  CoreDispatchRecipe,
  CoreDispatchRecipeEvaluationRequest,
  CoreTaskCapabilityResolveRequest,
  CoreTaskCapabilityResolveResult,
  CoreDispatchRecipeEvaluationResult,
  CoreDispatchReadinessStatus,
  CoreDispatchReadinessFixAction,
  CoreDispatchReadinessCheck,
  CoreDispatchReadinessEvaluationRequest,
  CoreDispatchReadinessEvaluationResult,
  CoreDispatchReadinessScenarioTemplate,
  CoreDispatchReadinessTemplates,
  CoreDispatchContractReadinessCheck,
  CoreDispatchContractReadinessRequest,
  CoreDispatchContractReadinessResponse,
  CoreDispatchSourceSystemOption,
  CoreDispatchContractBootstrapRequest,
  CoreDispatchContractChainInspectionRequest,
  CoreDispatchContractChainInspectionItem,
  CoreDispatchContractChainInspectionResponse,
  CoreDispatchContractTraceRequest,
  CoreDispatchContractTraceResponse,
  CoreDispatchContractTestTaskRequest,
  CoreDispatchContractTestTaskResponse,
  CoreDispatchContractBootstrapResponse,
} from '@/lib/types/domains/taskOperations';

// Phase 6 — UNKNOWN Problem / Semantic Triage. WHAT proposals only; never provider/routing/transport authority.
export interface CoreProblemClassification {
  classificationCode: string;
  displayName?: string;
  semanticTaxonomy?: string;
  confidence: number;
  rationale?: string;
}
export interface CoreTriageCapabilitySuggestion { capabilityCode: string; operation?: string; confidence: number; rationale?: string; }
export interface CoreTriagePreviewRequest { taskRef?: string; serviceCode?: string; problemStatement?: string; contextRefs?: string[]; inputContext?: Record<string, unknown>; dataClassification?: string; }
export interface CoreTriageRequest { requestId: string; tenantId?: string; taskRef?: string; serviceCode?: string; problemStatement?: string; contextRefs?: string[]; inputContext?: Record<string, unknown>; dataClassification?: string; status: string; createdAt: string; }
export interface CoreTriageProposal { proposalId?: string; tenantId?: string; requestId?: string; proposerType?: string; proposerRef?: string; classification: CoreProblemClassification; capabilityResolutionConfidence: number; capabilitySuggestions?: CoreTriageCapabilitySuggestion[]; investigationSuggestions?: string[]; explanatoryTaxonomies?: string[]; rationale?: string; proposedAt?: string; }
export interface CoreTriagePolicy { tenantId?: string; policyId: string; displayName: string; minClassificationConfidence: number; minCapabilityResolutionConfidence: number; maxCapabilitySuggestions: number; requireHumanReviewOnCapabilityGap: boolean; status?: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CoreTriagePolicyVersion { tenantId?: string; policyId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreTriageDecision { decisionId: string; tenantId?: string; requestId: string; decisionMode: string; result: string; serviceCode?: string; classification?: CoreProblemClassification; classificationConfidence: number; capabilityResolutionConfidence: number; triagePolicyId?: string; triagePolicyVersion?: number; acceptedRequirements?: CoreCapabilityRequirement[]; reasonCodes?: string[]; requiresHumanReview: boolean; decidedAt: string; }

// Phase 7 — Execution Plan / Multi-Capability Planning. Planner proposes WHAT + dependencies only.
export interface CoreExecutionPlanBudget { maxSteps: number; maxPlanDepth: number; maxConcurrentBranches: number; maxCapabilityInvocations: number; maxTokenBudget?: number; maxEstimatedCost?: number; maxExecutionTimeSeconds?: number; }
export interface CoreExecutionPlanPolicy { tenantId?: string; policyId: string; displayName: string; maxSteps: number; maxPlanDepth: number; maxConcurrentBranches: number; maxCapabilityInvocations: number; maxTokenBudget?: number; maxEstimatedCost?: number; maxExecutionTimeSeconds?: number; requireHumanReviewOnPlanChange: boolean; status?: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CoreExecutionPlanPolicyVersion { tenantId?: string; policyId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreExecutionPlanStep { stepId: string; requiredCapability: CoreCapabilityRequirement; dependsOn?: string[]; purpose?: string; required: boolean; sequenceHint?: number; sideEffect?: 'NONE'|'READ'|'WRITE'; writeSemantics?: 'IDEMPOTENT'|'COMPENSATABLE'|'NON_COMPENSATABLE'; compensationBindingId?: string; maxBindingFallback?: number; }
export interface CoreExecutionPlanPreviewRequest { taskRef?: string; sourceTriageDecisionId?: string; classificationCode?: string; initialRequirements?: CoreCapabilityRequirement[]; contextRefs?: string[]; }
export interface CoreExecutionPlanRequest { requestId: string; tenantId?: string; taskRef?: string; sourceTriageDecisionId?: string; classificationCode?: string; initialRequirements?: CoreCapabilityRequirement[]; contextRefs?: string[]; status: string; createdAt: string; }
export interface CoreExecutionPlanProposal { proposalId?: string; tenantId?: string; requestId?: string; proposerType?: string; proposerRef?: string; steps: CoreExecutionPlanStep[]; rationale?: string; proposedAt?: string; }
export interface CoreExecutionPlanAmendmentRequest { proposerType?: string; proposerRef?: string; steps: CoreExecutionPlanStep[]; rationale?: string; changeReason: string; }
export interface CoreExecutionPlan { planId: string; tenantId?: string; requestId: string; taskRef?: string; sourceTriageDecisionId?: string; classificationCode?: string; policyId: string; policyVersion: number; status: string; currentRevision: number; budget?: CoreExecutionPlanBudget; steps?: CoreExecutionPlanStep[]; createdAt: string; updatedAt: string; }
export interface CoreExecutionPlanRevision { tenantId?: string; planId: string; revision: number; proposalId?: string; snapshot: Record<string, unknown>; steps?: CoreExecutionPlanStep[]; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreExecutionPlanDecision { decisionId: string; tenantId?: string; requestId: string; proposalId?: string; decisionMode: string; result: string; planId?: string; planRevision?: number; policyId?: string; policyVersion?: number; maxDepthObserved?: number; maxConcurrentBranchesObserved?: number; reasonCodes?: string[]; requiresHumanReview: boolean; decidedAt: string; }

// Phase 8 — Governed Plan Execution / Fan-out / Fan-in. READY is dependency readiness only.
export interface CorePlanExecutionPolicy { tenantId?: string; policyId: string; displayName: string; maxAttemptsPerStep: number; defaultStepTimeoutSeconds: number; maxActiveSteps: number; requireArtifactOnSuccess: boolean; status?: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CorePlanExecutionPolicyVersion { tenantId?: string; policyId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CorePlanExecutionStartRequest { planId: string; planRevision?: number; planDecisionId: string; executionMode: 'SIMULATION'|'RUNTIME'|string; idempotencyKey: string; }
export interface CorePlanExecutionRun { runId: string; tenantId?: string; planId: string; planRevision: number; planDecisionId: string; executionMode: string; status: string; policyId: string; policyVersion: number; idempotencyKey: string; fencingToken: number; startedAt: string; updatedAt: string; completedAt?: string; }
export interface CoreGovernedPlanExecutionStep { tenantId?: string; runId: string; stepId: string; requiredCapability: CoreCapabilityRequirement; dependsOn?: string[]; required: boolean; state: string; attemptCount: number; authorizationDecisionId?: string; routingDecisionId?: string; adapterResolutionId?: string; childTaskRef?: string; deadlineAt?: string; updatedAt: string; bindingAuthorizationEnvelopeId?: string; sideEffect?: string; writeSemantics?: string; compensationBindingId?: string; maxBindingFallback?: number; }
export interface CorePlanStepAuthorityRequest { authorizationDecisionId: string; routingDecisionId: string; adapterResolutionId: string; }
export interface CorePlanStepSubmitRequest { idempotencyKey: string; deadlineAt?: string; }
export interface CorePlanStepCompletionRequest { result: 'SUCCEEDED'|'FAILED'|'TIMED_OUT'|'CANCELLED'|string; finding?: string; confidence?: number; evidenceRefs?: string[]; output?: Record<string, unknown>; dataClassification?: string; failureReason?: string; }
export interface CorePlanExecutionAttempt { attemptId: string; tenantId?: string; runId: string; stepId: string; attemptNo: number; idempotencyKey: string; state: string; adapterResolutionId: string; childTaskRef?: string; externalExecutionRef?: string; reasonCodes?: string[]; submittedAt: string; deadlineAt?: string; completedAt?: string; }
export interface CorePlanExecutionArtifact { artifactId: string; tenantId?: string; runId: string; stepId: string; attemptId: string; capabilityCode: string; finding?: string; confidence?: number; evidenceRefs?: string[]; output?: Record<string, unknown>; dataClassification?: string; createdAt: string; }
export interface CorePlanExecutionEvent { eventId: string; tenantId?: string; runId: string; stepId?: string; eventType: string; fromState?: string; toState?: string; reason?: string; actorRef: string; evidence?: Record<string, unknown>; occurredAt: string; }

// Phase 9 — Execution Artifact Aggregation / Case Convergence.
export interface CoreAggregationDefinition { tenantId?: string; aggregationId: string; displayName: string; aggregationCapabilityCode: string; operation: string; requiredInputCapabilities?: string[]; minInputConfidence?: number; allowPartialEvidence: boolean; requireHumanReviewOnPartial: boolean; status?: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CoreAggregationDefinitionVersion { tenantId?: string; aggregationId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreAggregationPreparationRequest { aggregationId: string; runId: string; artifactIds?: string[]; }
export interface CoreAggregationPreparationDecision { decisionId: string; tenantId?: string; aggregationId: string; aggregationVersion: number; runId: string; result: string; aggregationRequirement?: CoreCapabilityRequirement; inputArtifactIds?: string[]; reasonCodes?: string[]; requiresHumanReview: boolean; createdAt: string; }
export interface CoreCaseConvergenceRequest { caseId?: string; runId: string; aggregationId: string; aggregationArtifactId: string; classification?: string; affectedResources?: string[]; idempotencyKey: string; }
export interface CoreEnterpriseCaseRecord { caseId: string; tenantId?: string; sourceTaskRef?: string; planId: string; runId: string; aggregationId: string; aggregationVersion: number; aggregationArtifactId: string; classification?: string; affectedResources?: string[]; rootCause?: string; confidence?: number; recommendedActions?: string[]; humanDecision?: string; accountableRef?: string; status: string; version: number; correlationId: string; traceId: string; createdAt: string; updatedAt: string; }
export interface CoreCaseArtifactLink { tenantId?: string; caseId: string; artifactId: string; role: string; stepId: string; capabilityCode: string; linkedAt: string; }
export interface CoreCaseReviewRequest { status: string; humanDecision?: string; accountableRef?: string; reason: string; }
export interface CoreCaseIssueProjectionRequest { projectionId?: string; connectorType: string; targetRef: string; primaryProjection: boolean; existingProjectionRef?: string; idempotencyKey: string; }
export interface CoreCaseIssueProjection { tenantId?: string; projectionId: string; caseId: string; connectorType: string; targetRef: string; primaryProjection: boolean; status: string; existingProjectionRef?: string; externalIssueRef?: string; idempotencyKey: string; createdAt: string; updatedAt: string; }
export interface CoreCaseEvent { tenantId?: string; eventId: string; caseId: string; eventType: string; actorRef: string; reason?: string; evidence?: Record<string, unknown>; occurredAt: string; }

// Phase 10 — Outcome Evidence / Learning Fast Path / Drift Governance.
export interface CoreLearningPolicy { policyId: string; tenantId?: string; displayName: string; minCandidateSamples: number; minCandidateSuccessRate: number; minCandidateHumanAcceptanceRate: number; minShadowSamples: number; minActiveSuccessRate: number; minActiveHumanAcceptanceRate: number; maxP95LatencyMs?: number; driftWindowSize: number; requireHumanApprovalForPromotion: boolean; status: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CoreLearningPolicyVersion { tenantId?: string; policyId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreExecutionOutcomeEvidence { outcomeId: string; tenantId?: string; caseId: string; runId: string; planId: string; planRevision: number; problemSignature: string; signatureVersion: string; classification?: string; capabilityKeys?: string[]; executionSucceeded: boolean; humanAccepted: boolean; latencyMs?: number; tokenUsage?: number; estimatedCost?: number; humanDecision: string; accountableRef: string; recordedAt: string; }
export interface CoreRoutingPatternCandidate { candidateId: string; tenantId?: string; problemSignature: string; signatureVersion: string; classification?: string; capabilityKeys?: string[]; planTemplate?: Record<string, unknown>; sampleCount: number; successRate: number; humanAcceptanceRate: number; p95LatencyMs?: number; status: string; evidenceCaseIds?: string[]; firstObservedAt: string; lastObservedAt: string; }
export interface CoreRoutingPattern { patternId: string; tenantId?: string; candidateId: string; problemSignature: string; signatureVersion: string; classification?: string; capabilityKeys?: string[]; planTemplate?: Record<string, unknown>; status: string; version: number; learningPolicyId: string; learningPolicyVersion: number; activatedAt?: string; degradedAt?: string; createdAt: string; updatedAt: string; }
export interface CoreRoutingPatternVersion { tenantId?: string; patternId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CorePatternPromotionRequest { targetStatus?: string; reason: string; }
export interface CorePatternPromotionDecision { decisionId: string; tenantId?: string; candidateId?: string; patternId?: string; fromStatus?: string; requestedStatus: string; result: string; reasonCodes?: string[]; actorRef: string; decidedAt: string; }
export interface CorePatternQualitySnapshot { snapshotId: string; tenantId?: string; patternId: string; windowSize: number; sampleCount: number; successRate: number; humanAcceptanceRate: number; p95LatencyMs?: number; outcomeIds?: string[]; observedAt: string; }
export interface CoreDriftObservation { driftId: string; tenantId?: string; patternId: string; result: string; reasonCodes?: string[]; qualitySnapshotId?: string; patternDegraded: boolean; observedAt: string; }
export interface CoreFastPathResolutionRequest { classification?: string; requiredCapabilities: CoreCapabilityRequirement[]; }
export interface CoreFastPathResolutionDecision { decisionId: string; tenantId?: string; problemSignature: string; result: string; patternId?: string; patternVersion?: number; planTemplate?: Record<string, unknown>; reasonCodes?: string[]; decidedAt: string; }

// Phase 11 — Fast Path Runtime Integration / Shadow Execution & Certification.
export interface CoreFastPathRuntimePolicy { tenantId?: string; policyId: string; displayName: string; activeFastPathEnabled: boolean; shadowEvaluationEnabled: boolean; emergencyKillSwitch: boolean; minShadowComparisons: number; minShadowPlanMatchRate: number; minShadowCapabilityCoverage: number; status: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CoreFastPathRuntimePolicyVersion { tenantId?: string; policyId: string; version: number; snapshot: Record<string, unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreFastPathShadowComparisonRequest { patternId: string; actualPlanId: string; actualPlanRevision?: number; }
export interface CoreFastPathShadowComparison { comparisonId: string; tenantId?: string; patternId: string; patternVersion: number; actualPlanId: string; actualPlanRevision: number; result: string; planMatch: boolean; dependencyMatch: boolean; capabilityCoverage: number; patternTemplateHash: string; actualTemplateHash: string; reasonCodes?: string[]; observedAt: string; }
export interface CoreFastPathCertificationRequest { targetStatus: "CERTIFIED" | "SUSPENDED" | "REVOKED" | string; reason: string; }
export interface CoreFastPathRuntimeCertification { certificationId: string; tenantId?: string; patternId: string; patternVersion: number; runtimePolicyId: string; runtimePolicyVersion: number; status: string; shadowComparisonCount: number; shadowPlanMatchRate: number; shadowCapabilityCoverage: number; reason: string; actorRef: string; certifiedAt: string; }
export interface CoreFastPathRuntimeDecision { decisionId: string; tenantId?: string; taskRef: string; problemSignature: string; result: string; patternId?: string; patternVersion?: number; certificationId?: string; planId?: string; planRevision?: number; planDecisionId?: string; runId?: string; reasonCodes?: string[]; decidedAt: string; }

// Phase 12: READY-step authority automation. WHO CAN → WHO MAY → WHO SHOULD → HOW is rebuilt server-side.
export interface CoreRuntimeStepAuthorityPolicy { tenantId?: string; policyId: string; displayName: string; routingProfileId: string; defaultAccessMode: string; operationAccessModes?: Record<string,string>; maxCandidateBindings: number; automaticAttachmentEnabled: boolean; status: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CoreRuntimeStepAuthorityPolicyVersion { tenantId?: string; policyId: string; version: number; snapshot: Record<string,unknown>; changeReason: string; actorRef: string; createdAt: string; }
export interface CoreRuntimeStepAuthorityDecision { decisionId: string; tenantId?: string; runId: string; stepId: string; attemptGeneration: number; result: string; capabilityCode: string; operation: string; requesterPrincipalType?: string; requesterPrincipalId?: string; runtimePolicyId?: string; runtimePolicyVersion?: number; routingProfileId?: string; candidateCount: number; passCount: number; waitingApprovalCount: number; authorizationDecisionIds?: string[]; routingDecisionId?: string; adapterResolutionId?: string; reasonCodes?: string[]; decidedAt: string; }
export interface CoreRuntimeStepAuthorityAutomationResult { result: string; decision: CoreRuntimeStepAuthorityDecision; authorityRequest?: CorePlanStepAuthorityRequest; }

// MRS A0-R5 — Plan Admission + Binding Authorization Envelope.
export interface CorePlanAdmissionPolicy { tenantId?: string; policyId: string; displayName: string; maxCandidateBindingsPerStep: number; envelopeTtlSeconds: number; allowedBindingClasses: string[]; allowedDataClasses: string[]; maxSensitivityLevel: string; externalEgressAllowed: boolean; allowedRegions: string[]; maxSideEffect: string; securityCeilingRef: string; dataPolicyRef: string; egressPolicyRef: string; residencyConstraintRef: string; status: string; version?: number; createdAt?: string; updatedAt?: string; }
export interface CorePlanAdmissionDecision { decisionId: string; tenantId?: string; planId: string; planRevision: number; result: string; policyId?: string; policyVersion?: number; policySnapshotRef?: string; stepCount: number; admittedStepCount: number; deniedStepCount: number; waitingApprovalStepCount: number; candidateBindingCount: number; admittedBindingCount: number; reasonCodes: string[]; actorRef: string; decidedAt: string; }
export interface CoreBindingAuthorizationEnvelope { envelopeId: string; tenantId?: string; decisionId: string; planId: string; planRevision: number; stepId: string; capabilityCode: string; capabilityVersion: number; admittedBindingIds: string[]; admittedBindingClasses: string[]; admittedTrustDomainRefs: string[]; maxSideEffect: string; securityCeilingRef: string; dataPolicyRef: string; egressPolicyRef: string; residencyConstraintRef: string; policySnapshotRef: string; authorizationEpoch: number; revocationVersion: number; status: string; issuedAt: string; validUntil: string; revokedAt?: string; revocationReason?: string; }
export interface CorePlanAdmissionBindingEvaluation { evaluationId: string; tenantId?: string; decisionId: string; planId: string; planRevision: number; stepId: string; capabilityCode: string; capabilityVersion: number; bindingId: string; providerId: string; providerType: string; bindingClass: string; trustDomainRef?: string; result: string; authorizationDecisionId?: string; reasonCodes: string[]; evaluatedAt: string; }
export interface CorePlanAdmissionResult { decision: CorePlanAdmissionDecision; envelopes: CoreBindingAuthorizationEnvelope[]; }

export interface CoreRuntimeAcceptanceScenario { scenario_code: string; category: string; required: boolean; evidence_requirement: string; result: string; environment_ref?: string; evidence_ref?: string; executed_at?: string; }
export interface CoreRuntimeAcceptanceSummary { gate: { tenant_id?: string; required_count: number; passed_count: number; blocking_count: number; gate_status: string }; scenarios: CoreRuntimeAcceptanceScenario[]; productionReady: boolean; authorityExpansion: boolean; }

// Stage 10 — A0 Release / Production Foundation Cutover Gate.
export interface CoreProductionFoundationGate { tenant_id?: string; r8_gate_status: string; r8_required_count: number; r8_passed_count: number; r8_blocking_count: number; cutover_gate_status: string; cutover_required_count: number; cutover_passed_count: number; cutover_blocking_count: number; active_release_candidate_id?: string; active_artifact_sha256?: string; active_environment_ref?: string; controlled_live_unbound_flows: number; production_bound_flows: number; invalid_production_bound_flows: number; gate_status: string; production_foundation_ready: boolean; }
export interface CoreProductionFoundationScenario { scenario_code: string; category: string; required: boolean; evidence_requirement: string; result: string; environment_ref?: string; evidence_ref?: string; executed_by?: string; executed_at?: string; }
export interface CoreProductionReleaseCandidate { tenant_id?: string; release_candidate_id: string; artifact_name: string; artifact_sha256: string; product_snapshot: string; migration_version: string; source_gate_status: string; source_gate_ref: string; build_status: string; build_ref: string; environment_ref: string; certification_evidence_ref?: string; status: 'DRAFT' | 'CERTIFIED' | 'ACTIVE' | 'REVOKED' | string; r8_gate_status_snapshot?: string; r8_required_count?: number; r8_passed_count?: number; cutover_gate_status_snapshot?: string; cutover_required_count?: number; cutover_passed_count?: number; certification_reason?: string; created_by: string; created_at: string; certified_by?: string; certified_at?: string; activated_by?: string; activated_at?: string; revoked_by?: string; revoked_at?: string; revoke_reason?: string; }
export interface CoreProductionFoundationFlow { flow_id: string; migration_state: string; version: number; change_reason?: string; changed_by?: string; changed_at?: string; production_release_candidate_id?: string; release_candidate_status?: string; artifact_sha256?: string; }
export interface CoreProductionFoundationSummary { gate: CoreProductionFoundationGate; scenarios: CoreProductionFoundationScenario[]; candidates: CoreProductionReleaseCandidate[]; flows: CoreProductionFoundationFlow[]; globalCutoverAllowed: boolean; productionReady: boolean; }
