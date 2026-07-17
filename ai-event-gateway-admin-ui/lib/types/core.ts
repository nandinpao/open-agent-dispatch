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
export type CoreTaskStatus =
  | "CREATED"
  | "WAITING_APPROVAL"
  | "ASSIGNED"
  | "DISPATCH_REQUESTED"
  | "DISPATCHED"
  | "ACKED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "TIMEOUT"
  | "REASSIGNING"
  | string;
export type CoreDispatchStatus =
  | "PENDING"
  | "APPROVED"
  | "CLAIMED"
  | "DELIVERING"
  | "DELIVERED"
  | "DELIVERY_FAILED"
  | "CALLBACK_RECEIVED"
  | "COMPLETED"
  | "RETRY_PENDING"
  | "DEAD_LETTER"
  | string;


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

export type CoreAgentCertificationRunStatus =
  "RUNNING" | "PASSED" | "FAILED" | "TIMEOUT" | "CANCELLED" | string;

export interface CoreAgentCertificationProfile {
  tenantId?: string;
  certificationProfileId: string;
  profileCode: string;
  certificationName?: string;
  description?: string;
  testTaskType?: string;
  testPayloadTemplate?: Record<string, unknown>;
  expectedResultSchema?: Record<string, unknown>;
  timeoutSeconds?: number;
  requiredEvents?: string[];
  passCriteria?: Record<string, unknown>;
  active?: boolean;
  certificationVersion?: number;
  renewalIntervalDays?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentCertificationRun {
  tenantId?: string;
  certificationRunId: string;
  certificationProfileId?: string;
  agentId: string;
  profileCode: string;
  profilePolicyVersion?: number;
  certificationVersion?: number;
  status: CoreAgentCertificationRunStatus;
  taskId?: string;
  startedAt?: string;
  completedAt?: string;
  resultSummary?: string;
  failureReason?: string;
  evidenceRef?: string;
  requestedBy?: string;
  approvedQualificationId?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAgentCertificationRunCommand {
  tenantId?: string;
  profileCode: string;
  certificationProfileId?: string;
  operatorId?: string;
  reason?: string;
  autoApprove?: boolean;
  metadata?: Record<string, unknown>;
}

export interface CoreAgentCertificationDecisionCommand {
  operatorId?: string;
  resultSummary?: string;
  failureReason?: string;
  reason?: string;
  grantQualification?: boolean;
  metadata?: Record<string, unknown>;
}


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


export type CoreAgentCapabilityCatalogStatus = "DRAFT" | "ACTIVE" | "DISABLED" | "RETIRED" | "NEEDS_REVIEW" | string;

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

export interface CoreAgentCapabilityAssignment {
  tenantId?: string;
  assignmentId?: string;
  agentId: string;
  capabilityCode: string;
  capabilityName?: string;
  status?: CoreAgentCapabilityAssignmentStatus;
  source?: string;
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

export interface CoreTaskDispatchRequirementProfile {
  profileCode: string;
  profileName?: string;
  agentType?: string;
  allowedTaskTypes?: string[];
  allowedIssueProviders?: string[];
  requiredRuntimeFeatures?: string[];
  requiredCapabilities?: string[];
  requiredPolicyCodes?: string[];
  toolPolicy?: string;
  riskLevelLimit?: string;
  requiresCertification?: boolean;
  requiresHumanApproval?: boolean;
}

export interface CoreTaskDispatchRequirements {
  taskId?: string;
  taskType?: string;
  sourceSystem?: string;
  tenantId?: string;
  requiredProfiles?: string[];
  profiles?: CoreTaskDispatchRequirementProfile[];
  requiredRuntimeFeatures?: string[];
  requiredCapabilities?: string[];
  requiredPolicyCodes?: string[];
  taskDefinitionIds?: string[];
  allowedIssueProviders?: string[];
  toolPolicies?: string[];
  riskLevelLimit?: string;
  requirementSource?: string;
  generatedAt?: string;
}

export interface CoreEligibleAgentCandidate {
  agentId: string;
  agentType?: string;
  profileCode?: string;
  matchedProfiles?: string[];
  score?: number;
  eligible?: boolean;
  dispatchStatus?: string;
  reason?: string;
  checks?: CoreDispatchEligibilityCheck[];
}

export interface CoreTaskEligibleAgentsResponse {
  taskId?: string;
  requirements?: CoreTaskDispatchRequirements;
  eligibleAgents?: CoreEligibleAgentCandidate[];
  blockedAgents?: CoreEligibleAgentCandidate[];
  generatedAt?: string;
}



export interface CoreDispatchEligibilityV2BlockingReason {
  code?: string;
  message?: string;
  severity?: string;
  policyCode?: string;
  ruleId?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchEligibilityV2ScoreBreakdown {
  factorName?: string;
  weight?: number;
  contribution?: number;
  direction?: string;
  message?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchEligibilityV2PolicyMatch {
  policyCode?: string;
  policyName?: string;
  policyStatus?: string;
  matchedScopes?: string[];
  requiredCapabilities?: string[];
  requiredRuntimeFeatures?: string[];
  qualityRules?: string[];
  scoringRules?: string[];
}

export interface CoreDispatchEligibilityV2Candidate {
  agentId?: string;
  runtimeId?: string;
  bindingId?: string;
  supplyProfileId?: string;
  supplyProfileCode?: string;
  serviceRole?: string;
  serviceLevel?: string;
  qualityGrade?: string;
  eligible?: boolean;
  dispatchStatus?: string;
  score?: number;
  matchedPolicyCodes?: string[];
  matchedCapabilities?: string[];
  matchedRuntimeFeatures?: string[];
  blockingReasons?: CoreDispatchEligibilityV2BlockingReason[];
  scoreBreakdown?: CoreDispatchEligibilityV2ScoreBreakdown[];
}

export interface CoreDispatchEligibilityV2Response {
  taskId?: string;
  tenantId?: string;
  sourceSystem?: string;
  taskType?: string;
  engineMode?: string;
  requirementSource?: string;
  applicablePolicies?: CoreDispatchEligibilityV2PolicyMatch[];
  eligibleCandidates?: CoreDispatchEligibilityV2Candidate[];
  blockedCandidates?: CoreDispatchEligibilityV2Candidate[];
  globalBlockingReasons?: CoreDispatchEligibilityV2BlockingReason[];
  generatedAt?: string;
}

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

export interface CoreTaskRecord {
  taskId: string;
  traceId?: string;
  incidentId?: string;
  sourceEventId?: string;
  sourceSystem?: string;
  taskType?: string;
  taskTypeCode?: string;
  effectiveTaskTypeCode?: string;
  status: CoreTaskStatus;
  priority?: string;
  tenantId?: string;
  siteId?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  eventType?: string;
  errorCode?: string;
  eventStage?: CoreDispatchEventStage;
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
  matchedFlowId?: string;
  matchedRuleId?: string;
  assignedPoolId?: string;
  targetPoolId?: string;
  targetPoolCode?: string;
  sourceDefaultPool?: boolean;
  poolMemberCount?: number;
  eligibleAgentCount?: number;
  blockerCode?: string;
  blockerReason?: string;
  classificationStatus?: 'UNCLASSIFIED' | 'CLASSIFIED' | 'CLASSIFYING' | 'RECLASSIFIED' | 'CLASSIFICATION_FAILED' | string;
  classificationResultJson?: unknown;
  routingPath?: string;
  routingPolicy?: string;
  assignedAgentId?: string;
  requiredCapabilities?: string[];
  createdReason?: string;
  occurrenceCountAtCreation?: number;
  createdAt?: string;
  updatedAt?: string;
  timeoutAt?: string;
  terminalAt?: string;
  reassignmentCount?: number;
  nextDispatchAttemptAt?: string;
  dispatchAttemptCount?: number;
  dispatchRetryReason?: string;
  dispatchRecoveryClaimedBy?: string;
  dispatchRecoveryClaimUntil?: string;
  lifecycleReason?: string;
}

export interface CoreDispatchRequest {
  dispatchRequestId: string;
  assignmentId?: string;
  taskId: string;
  incidentId?: string;
  agentId?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  siteId?: string;
  status?: CoreDispatchStatus;
  reviewMode?: string;
  eligibilityStatus?: string;
  dispatchMethod?: string;
  gatewayDispatchPath?: string;
  reason?: string;
  createdAt?: string;
  updatedAt?: string;
  approvedAt?: string;
  dispatchedAt?: string;
  failedAt?: string;
  completedAt?: string;
  timedOutAt?: string;
  retryWaitingAt?: string;
  nextRetryAt?: string;
  deadLetterAt?: string;
  attemptCount?: number;
  lastError?: string;
  lastCallbackId?: string;
}

export interface CoreTaskIssueTracking {
  taskId?: string;
  incidentId?: string;
  dispatchRequestId?: string;
  assignmentId?: string;
  agentId?: string;
  issueVendor?: string;
  issueId?: string;
  issueUrl?: string;
  issueStatus?: string;
  syncStatus?:
    "NOT_LINKED" | "SYNC_PENDING" | "SYNCED" | "SYNC_FAILED" | string;
  issueActionId?: string;
  issueActionType?: string;
  issueActionStatus?: string;
  issueRetryable?: boolean;
  issueCommentMode?: string;
  agentSummary?: string;
  issueCommentPreview?: string;
  lastSyncedAt?: string;
  syncError?: string;
  message?: string;
  lastAdapterActionAt?: string;
  createdAt?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

export interface CoreTaskRuntimeSnapshot {
  tasks: CoreTaskRecord[];
  dispatchRequests: CoreDispatchRequest[];
  callbacks?: unknown[];
  latestRoutingDecisions?:
    Record<string, CoreRoutingDecisionRecord> | CoreRoutingDecisionRecord[];
  routingDecisionsByTask?:
    Record<string, CoreRoutingDecisionRecord> | CoreRoutingDecisionRecord[];
  taskIssueLinks?:
    Record<string, CoreTaskIssueTracking> | CoreTaskIssueTracking[];
  issueTrackingByTask?:
    Record<string, CoreTaskIssueTracking> | CoreTaskIssueTracking[];
  tasksByStatus?: Record<string, number>;
  dispatchByStatus?: Record<string, number>;
  generatedAt?: string;
}

export interface CoreTaskRuntimeView {
  taskId: string;
  traceId?: string;
  incidentId?: string;
  sourceEventId?: string;
  sourceSystem?: string;
  taskType?: string;
  taskTypeCode?: string;
  effectiveTaskTypeCode?: string;
  status: CoreTaskStatus;
  priority?: string;
  tenantId?: string;
  siteId?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  eventType?: string;
  errorCode?: string;
  eventStage?: CoreDispatchEventStage;
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
  matchedFlowId?: string;
  matchedRuleId?: string;
  assignedPoolId?: string;
  targetPoolId?: string;
  targetPoolCode?: string;
  sourceDefaultPool?: boolean;
  poolMemberCount?: number;
  eligibleAgentCount?: number;
  blockerCode?: string;
  blockerReason?: string;
  classificationStatus?: 'UNCLASSIFIED' | 'CLASSIFIED' | 'CLASSIFYING' | 'RECLASSIFIED' | 'CLASSIFICATION_FAILED' | string;
  classificationResultJson?: unknown;
  routingPath?: string;
  routingPolicy?: string;
  createdReason?: string;
  occurrenceCountAtCreation?: number;
  assignedAgentId?: string;
  requiredCapabilities?: string[];
  createdAt?: string;
  updatedAt?: string;
  dispatchRequestId?: string;
  dispatchStatus?: CoreDispatchStatus;
  dispatchExecutionStatus?: string;
  dispatchDeliveryStatus?: string;
  blockedReason?: string;
  nextAction?: string;
  callbackStatus?: string;
  lifecycleReason?: string;
  failureReason?: string;
  dispatchWaitReason?: string;
  reasonCategory?: string;
  nextDispatchAttemptAt?: string;
  dispatchAttemptCount?: number;
  dispatchRetryReason?: string;
  dispatchRecoveryClaimedBy?: string;
  dispatchRecoveryClaimUntil?: string;
  latestRoutingDecision?: CoreRoutingDecisionRecord;
  userFacingDispatchError?: CoreDispatchUserFacingError;
  issueTracking?: CoreTaskIssueTracking;
  payload?: unknown;
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
  redmineExecutorEnabled?: boolean;
  redmineEndpointConfigured?: boolean;
  gitlabMockEnabled?: boolean;
  gitlabExecutorEnabled?: boolean;
  gitlabEndpointConfigured?: boolean;
  [key: string]: unknown;
}

export interface CoreAgentCandidateScore {
  agentId: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  siteId?: string;
  status?: string;
  score: number;
  matchedCapabilities?: string[];
  missingCapabilities?: string[];
  reason?: string;
  scoreBreakdown?: Record<string, unknown>;
}

export interface CoreDispatchUserFacingError {
  code?: string;
  severity?: string;
  message?: string;
  nextAction?: string;
  runbookRef?: string;
  context?: Record<string, unknown>;
  technicalDetails?: Record<string, unknown> | string;
}

export interface CoreRoutingDecisionRecord {
  decisionId: string;
  taskId: string;
  incidentId?: string;
  routingPolicy?: string;
  status?:
    | "SELECTED"
    | "NO_CANDIDATE"
    | "SUPPRESSED"
    | "MANUAL_REVIEW_REQUIRED"
    | string;
  selectedAgentId?: string;
  selectedGatewayNodeId?: string;
  selectedAgentSessionId?: string;
  selectedSiteId?: string;
  selectedScore?: number;
  decisionReason?: string;
  userFacingError?: CoreDispatchUserFacingError;
  candidates?: CoreAgentCandidateScore[];
  createdAt?: string;
}

export interface CoreCallbackInboxEntry {
  callbackId?: string;
  taskId?: string;
  dispatchRequestId?: string;
  assignmentId?: string;
  agentId?: string;
  receivedByGatewayNodeId?: string;
  receivedAgentSessionId?: string;
  attemptNo?: number;
  callbackType?: string;
  processStatus?: string;
  accepted?: boolean;
  duplicate?: boolean;
  replayDetected?: boolean;
  idempotencyKey?: string;
  callbackFingerprint?: string;
  ignoredReason?: string;
  message?: string;
  errorCode?: string;
  errorMessage?: string;
  previousTaskStatus?: string;
  newTaskStatus?: string;
  previousDispatchStatus?: string;
  newDispatchStatus?: string;
  payload?: Record<string, unknown>;
  occurredAt?: string;
  receivedAt?: string;
  processedAt?: string;
  authoritative?: boolean;
}

export interface CoreCallbackInboxSummary {
  taskId?: string;
  dispatchRequestId?: string;
  totalCallbacks?: number;
  acceptedCallbacks?: number;
  rejectedCallbacks?: number;
  duplicateCallbacks?: number;
  replayRejectedCallbacks?: number;
  latestCallbackId?: string;
  latestCallbackType?: string;
  latestProcessStatus?: string;
  terminalCallbackReceived?: boolean;
  recoveryRequired?: boolean;
  nextAction?: string;
}

export interface CoreDispatchAttemptLedgerEvent {
  eventId?: string;
  eventType: string;
  source?: string;
  status?: string;
  taskId?: string;
  dispatchRequestId?: string;
  callbackId?: string;
  agentId?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  attemptNo?: number;
  idempotencyKey?: string;
  reason?: string;
  errorCode?: string;
  errorMessage?: string;
  authoritative?: boolean;
  occurredAt?: string;
}

export interface CoreDispatchAttemptLedger {
  dispatchRequestId: string;
  taskId: string;
  incidentId?: string;
  assignmentId?: string;
  agentId?: string;
  lastKnownGatewayNodeId?: string;
  lastKnownAgentSessionId?: string;
  dispatchStatus?: string;
  deliveryState?: string;
  callbackState?: string;
  resultState?: string;
  attemptNo?: number;
  lastCallbackId?: string;
  dispatchTokenPresent?: boolean;
  authoritative?: boolean;
  recoveryRequired?: boolean;
  nextAction?: string;
  lastError?: string;
  createdAt?: string;
  updatedAt?: string;
  dispatchedAt?: string;
  ackReceivedAt?: string;
  progressReceivedAt?: string;
  resultReceivedAt?: string;
  errorReceivedAt?: string;
  terminalAt?: string;
  leaseExpiresAt?: string;
  callbackDeadlineAt?: string;
  events?: CoreDispatchAttemptLedgerEvent[];
}

export interface CoreDispatchAttemptHistoryRecord {
  historyId: string;
  taskId: string;
  incidentId?: string;
  assignmentId?: string;
  dispatchRequestId?: string;
  agentId?: string;
  ownerGatewayNodeId?: string;
  agentSessionId?: string;
  siteId?: string;
  routingDecisionId?: string;
  eventType: string;
  status?: string;
  attemptNo?: number;
  taskDispatchAttemptNo?: number;
  reason?: string;
  errorCode?: string;
  errorMessage?: string;
  nextAttemptAt?: string;
  runtimeBackoffUntil?: string;
  workerId?: string;
  claimUntil?: string;
  payloadJson?: string;
  occurredAt?: string;
  createdAt?: string;
}

export interface CoreRecoveryGovernanceActionRequest {
  operatorId?: string;
  reason?: string;
  resetAttempts?: boolean;
  immediate?: boolean;
  riskAcknowledged?: boolean;
  confirmationPhrase?: string;
  requestId?: string;
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

export interface CoreRecoveryGovernanceAuditSummary {
  operatorId?: string;
  requiredRole?: string;
  riskLevel?: string;
  requestId?: string;
}

export interface CoreRecoveryGovernanceActionResult<T = unknown> {
  success: boolean;
  action: string;
  message: string;
  timestamp: string;
  payload?: T;
  audit?: CoreRecoveryGovernanceAuditSummary;
  nextActions?: string[];
  runbookRef?: string;
  approvalRequired?: boolean;
  approval?: CoreRecoveryApprovalRequest;
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

export interface CoreTaskDispatchContractResolveRequest {
  taskId?: string;
  taskType?: string;
  sourceSystem?: string;
  domain?: string;
  provider?: string;
  siteCode?: string;
  plantId?: string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  operation?: string;
  requiredToolPolicy?: string;
  requiredCapabilities?: string[];
  dataClasses?: string[];
  payloadMetadata?: Record<string, unknown>;
}

export interface CoreTaskDispatchContractResolveResult {
  taxonomyVersion?: string;
  taskType?: string;
  sourceSystem?: string;
  domain?: string;
  provider?: string;
  siteCode?: string;
  operation?: string;
  requiredToolPolicy?: string;
  requiredCapabilities?: string[];
  dataClasses?: string[];
  matchedSkillCodes?: string[];
  resolutionReasons?: string[];
  resolved?: boolean;
  resolvedAt?: string;
}

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

export interface CoreDispatchRecipe {
  recipeCode?: string;
  displayName?: string;
  description?: string;
  domain?: string;
  provider?: string;
  skillCode?: string;
  taskType?: string;
  operation?: string;
  requiredToolPolicy?: string;
  requiredCapabilities?: string[];
  dataClasses?: string[];
  riskLevel?: string;
  requiresHumanApproval?: boolean;
  issueSyncOptional?: boolean;
  enabled?: boolean;
  systemDefault?: boolean;
  beginnerDescription?: string;
  expectedSource?: string;
  successCondition?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreDispatchRecipeEvaluationRequest {
  recipeCode?: string;
  tenantId?: string;
  agentId?: string;
  siteCode?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  eventType?: string;
  errorCode?: string;
  payloadMetadata?: Record<string, unknown>;
}

export interface CoreTaskCapabilityResolveRequest {
  sourceSystem?: string;
  domain?: string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  message?: string;
  attributes?: Record<string, unknown>;
}

export interface CoreTaskCapabilityResolveResult {
  primaryCapability?: string;
  requiredCapabilities?: string[];
  taskType?: string;
  sourceSystem?: string;
  domain?: string;
  provider?: string;
  operation?: string;
  requiredToolPolicy?: string;
  matchedRecipeCode?: string;
  matchedSkillCodes?: string[];
  resolutionReasons?: string[];
  fallback?: boolean;
  resolvedAt?: string;
}

export interface CoreDispatchRecipeEvaluationResult {
  recipe?: CoreDispatchRecipe;
  readiness?: CoreDispatchReadinessEvaluationResult;
  dispatchContract?: CoreTaskDispatchContractResolveResult;
  capabilityResolution?: CoreTaskCapabilityResolveResult;
  testEventPayload?: Record<string, unknown>;
  ready?: boolean;
  beginnerSummary?: string;
  nextAction?: string;
  evaluatedAt?: string;
}

export type CoreDispatchReadinessStatus = "PASS" | "FAIL" | "WARN" | "INFO";

export interface CoreDispatchReadinessFixAction {
  label?: string;
  actionType?: string;
  targetPath?: string;
  payload?: Record<string, unknown>;
}

export interface CoreDispatchReadinessCheck {
  key?: string;
  label?: string;
  status?: CoreDispatchReadinessStatus | string;
  message?: string;
  beginnerHint?: string;
  evidence?: string[];
  fixAction?: CoreDispatchReadinessFixAction;
  details?: Record<string, unknown>;
}

export interface CoreDispatchReadinessEvaluationRequest {
  tenantId?: string;
  agentId?: string;
  taskId?: string;
  taskType?: string;
  sourceSystem?: string;
  domain?: string;
  provider?: string;
  siteCode?: string;
  plantId?: string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  operation?: string;
  requiredToolPolicy?: string;
  requiredCapabilities?: string[];
  dataClasses?: string[];
  payloadMetadata?: Record<string, unknown>;
}

export interface CoreDispatchReadinessEvaluationResult {
  ready?: boolean;
  summary?: string;
  beginnerSummary?: string;
  agentId?: string;
  requiredCapabilities?: string[];
  rawTaskRequirements?: string[];
  effectiveDispatchCapabilities?: string[];
  legacyTaskAliases?: string[];
  matchedSkillCodes?: string[];
  missingRequirements?: string[];
  checks?: CoreDispatchReadinessCheck[];
  recommendedActions?: CoreDispatchReadinessFixAction[];
  skillEvaluation?: CoreAgentSkillEvaluationResult;
  contractResolution?: CoreTaskDispatchContractResolveResult;
  labels?: Record<string, unknown>;
  evaluatedAt?: string;
}

export interface CoreDispatchReadinessScenarioTemplate {
  scenarioId?: string;
  label?: string;
  skillCode?: string;
  domain?: string;
  taskType?: string;
  provider?: string;
  operation?: string;
  requiredToolPolicy?: string;
  requiredCapabilities?: string[];
  dataClasses?: string[];
  beginnerDescription?: string;
}

export interface CoreDispatchReadinessTemplates {
  recommendedDefault?: string;
  scenarios?: CoreDispatchReadinessScenarioTemplate[];
}

export interface CoreDispatchTimelineEvent {
  sequence: number;
  occurredAt?: string;
  stage: string;
  action: string;
  status?: string;
  severity?: string;
  source?: string;
  message?: string;
  references?: Record<string, string>;
  details?: Record<string, unknown>;
}

export interface CoreDispatchTimelineResponse {
  taskId: string;
  task?: CoreTaskRecord;
  generatedAt?: string;
  counts?: Record<string, number>;
  events: CoreDispatchTimelineEvent[];
}

export interface CoreTaskCaseTimelineStepView {
  sequence: number;
  stepCode: string;
  eventStage?: CoreDispatchEventStage | string;
  eventType?: string;
  sourceSystem?: string;
  targetSystem?: string;
  matchedFlowId?: string;
  matchedRuleId?: string;
  requestedSkill?: string;
  routingPath?: string;
  selectedAgentId?: string;
  status?: string;
  failureStage?: string;
  fixAction?: string;
  taskId?: string;
  parentTaskId?: string;
  childTaskId?: string;
  correlationId?: string;
  message?: string;
  occurredAt?: string;
  references?: Record<string, string>;
  details?: Record<string, unknown>;
}

export interface CoreTaskCaseTimelineView {
  taskId: string;
  parentTaskId?: string;
  childTaskIds?: string[];
  correlationId?: string;
  matchedFlowId?: string;
  matchedRuleId?: string;
  requestedSkill?: string;
  eventStage?: CoreDispatchEventStage | string;
  routingPath?: string;
  failureStage?: string;
  fixAction?: string;
  steps: CoreTaskCaseTimelineStepView[];
  generatedAt?: string;
}


export interface CoreTaskDispatchEvidenceStage {
  stage: string;
  status: string;
  title?: string;
  summary?: string;
  blockingCode?: string;
  nextAction?: string;
  details?: Record<string, unknown>;
}

export interface CoreTaskDispatchRecoveryAction {
  action: string;
  label?: string;
  description?: string;
  endpoint?: string;
  method?: string;
  riskLevel?: string;
  enabled?: boolean;
  payload?: Record<string, unknown>;
}

export interface CoreTaskDispatchContractRepairRequest {
  agentId?: string;
  capabilityCode?: string;
  profileCode?: string;
  policyCode?: string;
  operatorId?: string;
  assignAgent?: boolean;
  approveAgentQualification?: boolean;
  approveAgentCapability?: boolean;
  activate?: boolean;
}

export interface CoreTaskDispatchEvidenceView {
  taskId: string;
  status?: string;
  summary?: string;
  firstBlockingStage?: string;
  firstBlockingCode?: string;
  firstBlockingReason?: string;
  task?: CoreTaskRecord;
  contractReadiness?: CoreDispatchContractReadinessResponse;
  eligibleAgents?: CoreTaskEligibleAgentsResponse;
  latestRoutingDecision?: CoreRoutingDecisionRecord;
  dispatchRequests?: CoreDispatchRequest[];
  timeline?: CoreDispatchTimelineResponse;
  issueTracking?: CoreTaskIssueTracking;
  stages?: CoreTaskDispatchEvidenceStage[];
  suggestedActions?: CoreTaskDispatchRecoveryAction[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}


export interface CoreTaskRuntimeVerificationStep {
  step: string;
  status: string;
  title?: string;
  summary?: string;
  blockingCode?: string;
  nextAction?: string;
  observedAt?: string;
  details?: Record<string, unknown>;
}

export interface CoreTaskRuntimeVerificationView {
  taskId: string;
  status?: string;
  summary?: string;
  currentStep?: string;
  firstBlockingStep?: string;
  firstBlockingCode?: string;
  firstBlockingReason?: string;
  timedOut?: boolean;
  timeoutSeconds?: number;
  elapsedSeconds?: number;
  selectedAgentId?: string;
  dispatchRequestId?: string;
  steps?: CoreTaskRuntimeVerificationStep[];
  evidence?: CoreTaskDispatchEvidenceView;
  suggestedActions?: CoreTaskDispatchRecoveryAction[];
  diagnostics?: Record<string, unknown>;
  startedAt?: string;
  generatedAt?: string;
}

export interface CoreAdminFailureQueueItem {
  taskId: string;
  incidentId?: string;
  taskType?: string;
  status?: CoreTaskStatus | string;
  priority?: string;
  tenantId?: string;
  siteId?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  errorCode?: string;
  reasonCategory?: string;
  blockedReason?: string;
  failureReason?: string;
  dispatchWaitReason?: string;
  lifecycleReason?: string;
  dispatchRetryReason?: string;
  dispatchAttemptCount?: number;
  nextDispatchAttemptAt?: string;
  terminalAt?: string;
  updatedAt?: string;
  latestRoutingDecision?: CoreRoutingDecisionRecord;
  userFacingDispatchError?: CoreDispatchUserFacingError;
  latestTimelineEvent?: CoreDispatchTimelineEvent;
  actions?: Record<string, unknown>;
}

export interface CoreAdminFailureQueueResponse {
  generatedAt?: string;
  total: number;
  counts?: Record<string, number>;
  reasonCategoryCounts?: Record<string, number>;
  dispatchErrorCounts?: Record<string, number>;
  items: CoreAdminFailureQueueItem[];
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

export interface CoreIssueTrackingRedmineCollectionItem {
  id?: string;
  identifier?: string;
  name?: string;
  [key: string]: unknown;
}

export interface CoreIssueTrackingRedmineDiagnostics {
  provider?: string;
  issueActionEnabled?: boolean;
  executorEnabled?: boolean;
  executorMode?: string;
  executorAutoExecutePending?: boolean;
  defaultVendor?: string;
  redmineExecutorEnabled?: boolean;
  baseUrlConfigured?: boolean;
  apiKeyConfigured?: boolean;
  projectIdConfigured?: boolean;
  trackerIdConfigured?: boolean;
  issueUrlTemplateConfigured?: boolean;
  configuredProjectId?: string;
  configuredTrackerId?: string;
  configuredBaseUrl?: string;
  priorityMapping?: Record<string, string>;
  ready?: boolean;
  checkedAt?: string;
  [key: string]: unknown;
}

export interface CoreIssueTrackingRedmineConnectionResult extends CoreIssueTrackingRedmineDiagnostics {
  ok?: boolean;
  operation?: string;
  message?: string;
  projects?: CoreIssueTrackingRedmineCollectionItem[];
  trackers?: CoreIssueTrackingRedmineCollectionItem[];
  checks?: Array<{
    label?: string;
    ok?: boolean;
    httpStatus?: number;
    message?: string;
    [key: string]: unknown;
  }>;
  httpStatus?: number;
  error?: string;
}

export interface CoreIssueTrackingRedmineCollectionResult extends CoreIssueTrackingRedmineDiagnostics {
  ok?: boolean;
  operation?: string;
  message?: string;
  projects?: CoreIssueTrackingRedmineCollectionItem[];
  trackers?: CoreIssueTrackingRedmineCollectionItem[];
  httpStatus?: number;
  error?: string;
}

export interface CoreIssueTrackingRedmineTestIssueRequest {
  projectId?: string;
  trackerId?: string;
  subject?: string;
  description?: string;
  severity?: string;
  message?: string;
  priorityId?: string;
  objectId?: string;
  eventType?: string;
  errorCode?: string;
}

export interface CoreIssueTrackingRedmineTestIssueResult extends CoreIssueTrackingRedmineDiagnostics {
  ok?: boolean;
  operation?: string;
  message?: string;
  issueId?: string;
  issueUrl?: string;
  httpStatus?: number;
  response?: Record<string, unknown>;
  error?: string;
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

export interface CoreDispatchContractReadinessCheck {
  code: string;
  status: string;
  message: string;
  blocking?: boolean;
  nextAction?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchContractReadinessRequest {
  tenantId?: string;
  sourceSystem: string;
  taskType: string;
  agentId?: string;
  requiredCapabilities?: string[];
  metadata?: Record<string, unknown>;
}

export interface CoreDispatchContractReadinessResponse {
  tenantId: string;
  sourceSystem: string;
  taskType: string;
  agentId?: string;
  ready?: boolean;
  status?: string;
  summary?: string;
  firstBlockingCode?: string;
  firstBlockingReason?: string;
  taskDefinition?: CoreDispatchTaskDefinition;
  profiles?: CoreAgentAssignmentProfile[];
  requiredProfiles?: string[];
  requiredCapabilities?: string[];
  requiredPolicyCodes?: string[];
  dispatchRulePolicyCodes?: string[];
  dispatchPolicies?: CoreDispatchPolicy[];
  checks?: CoreDispatchContractReadinessCheck[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}


export interface CoreSourceSystem {
  tenantId?: string;
  sourceSystemId: string;
  displayName: string;
  description?: string;
  status: "ACTIVE" | "DISABLED" | "RETIRED" | string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreSourceSystemCommand {
  tenantId?: string;
  sourceSystemId: string;
  displayName: string;
  description?: string;
  status?: "ACTIVE" | "DISABLED" | "RETIRED" | string;
}

export interface CoreDispatchSourceSystemOption {
  tenantId?: string;
  sourceSystem: string;
  displayName?: string;
  domain?: string;
  active?: boolean;
  taskDefinitionCount?: number;
  activeTaskDefinitionCount?: number;
  profileCount?: number;
  capabilityCount?: number;
  metadata?: Record<string, unknown>;
}

export interface CoreDispatchContractBootstrapRequest {
  tenantId?: string;
  sourceSystem: string;
  sourceSystemName?: string;
  taskType: string;
  displayName?: string;
  description?: string;
  domain?: string;
  riskLevel?: string;
  defaultSeverity?: string;
  ownerTeam?: string;
  capabilityCode?: string;
  capabilityName?: string;
  profileCode?: string;
  profileName?: string;
  policyCode?: string;
  policyName?: string;
  toolPolicy?: string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  messagePattern?: string;
  eventMappingPriority?: number;
  requiredRuntimeFeatures?: string[];
  agentId?: string;
  assignAgent?: boolean;
  approveAgentQualification?: boolean;
  approveAgentCapability?: boolean;
  activate?: boolean;
  operatorId?: string;
  metadata?: Record<string, unknown>;
}



export interface CoreDispatchContractChainInspectionRequest {
  tenantId?: string;
  sourceSystem: string;
  taskType: string;
  capabilityCode?: string;
  profileCode?: string;
  policyCode?: string;
  agentId?: string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  message?: string;
}

export interface CoreDispatchContractChainInspectionItem {
  code?: string;
  tableName?: string;
  label?: string;
  expected?: string;
  actual?: string;
  status?: string;
  present?: boolean;
  healthy?: boolean;
  blocking?: boolean;
  recordId?: string;
  summary?: string;
  nextAction?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchContractChainInspectionResponse {
  tenantId?: string;
  sourceSystem?: string;
  taskType?: string;
  capabilityCode?: string;
  profileCode?: string;
  policyCode?: string;
  agentId?: string;
  healthy?: boolean;
  status?: string;
  summary?: string;
  firstBlockingCode?: string;
  firstBlockingReason?: string;
  recommendedFix?: string;
  items?: CoreDispatchContractChainInspectionItem[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}


export interface CoreDispatchContractTraceRequest {
  tenantId?: string;
  taskId?: string;
  sourceSystem?: string;
  taskType?: string;
  agentId?: string;
  domain?: string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  message?: string;
  requiredCapabilities?: string[];
  attributes?: Record<string, unknown>;
}

export interface CoreDispatchContractTraceResponse {
  tenantId?: string;
  taskId?: string;
  sourceSystem?: string;
  taskType?: string;
  agentId?: string;
  status?: string;
  ready?: boolean;
  summary?: string;
  firstBlockingCode?: string;
  firstBlockingReason?: string;
  capabilityResolution?: CoreTaskCapabilityResolveResult;
  readiness?: CoreDispatchContractReadinessResponse;
  chainInspection?: CoreDispatchContractChainInspectionResponse;
  requiredCapabilities?: string[];
  checks?: CoreDispatchContractReadinessCheck[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreDispatchContractTestTaskRequest {
  tenantId?: string;
  sourceSystem: string;
  sourceSystemName?: string;
  taskType: string;
  severity?: string;
  siteId?: string;
  plantId?: string;
  objectType?: string;
  objectId?: string;
  eventType?: string;
  errorCode?: string;
  message?: string;
  agentId?: string;
  requiredCapabilities?: string[];
  ensureContract?: boolean;
  assignAgent?: boolean;
  approveAgentQualification?: boolean;
  approveAgentCapability?: boolean;
  activate?: boolean;
  operatorId?: string;
  attributes?: Record<string, unknown>;
}

export interface CoreDispatchContractTestTaskResponse {
  tenantId?: string;
  sourceSystem?: string;
  taskType?: string;
  agentId?: string;
  status?: string;
  summary?: string;
  readinessBefore?: CoreDispatchContractReadinessResponse;
  bootstrap?: CoreDispatchContractBootstrapResponse;
  readinessAfter?: CoreDispatchContractReadinessResponse;
  eventDecision?: Record<string, unknown>;
  taskId?: string;
  taskCreated?: boolean;
  assignmentCreated?: boolean;
  dispatchRequestCreated?: boolean;
  selectedAgentId?: string;
  evidence?: CoreTaskDispatchEvidenceView;
  nextActions?: string[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}

export interface CoreDispatchContractBootstrapResponse {
  tenantId?: string;
  sourceSystem?: string;
  taskType?: string;
  taskDefinition?: CoreDispatchTaskDefinition;
  capability?: CoreAgentCapabilityCatalog;
  profile?: CoreAgentAssignmentProfile;
  policyDefinition?: CoreAgentSkillDefinition;
  dispatchPolicy?: CoreDispatchPolicy;
  policyBinding?: CoreAssignmentProfilePolicyBinding;
  capabilityBinding?: CoreAssignmentProfileCapabilityBinding;
  qualification?: CoreAgentQualification;
  capabilityAssignment?: CoreAgentCapabilityAssignment;
  readiness?: CoreDispatchContractReadinessResponse;
  chainInspection?: CoreDispatchContractChainInspectionResponse;
  createdOrUpdated?: string[];
  diagnostics?: Record<string, unknown>;
  generatedAt?: string;
}

export type CoreDispatchEventStage = 'EXTERNAL' | 'A2A' | 'RESULT' | 'ISSUE' | 'CALLBACK';
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

export interface CoreDispatchFlowRuleView {
  tenantId?: string;
  ruleId?: string;
  flowId?: string;
  ruleCode?: string;
  ruleName?: string;
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
  priority?: number;
  enabled?: boolean;
  legacyStatus?: string;
  updatedAt?: string;
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
  memberCount?: number;
  availableAgentCount?: number;
  members?: CoreAgentPoolMemberView[];
  metadata?: Record<string, unknown>;
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
}



export interface CoreTaskClassificationRequest {
  classificationStatus?: 'CLASSIFIED' | 'UNCLASSIFIED' | 'CLASSIFICATION_FAILED' | string;
  sourceSystem?: string;
  objectType?: string;
  eventType?: string;
  errorCode?: string;
  severity?: string;
  confidence?: number;
  reason?: string;
  recommendedPoolCode?: string;
  createResolutionTask?: boolean;
}

export interface CoreTaskClassificationResult {
  parentTaskId?: string;
  parentStatus?: string;
  classificationStatus?: string;
  classificationResultJson?: string;
  resolutionTaskCreated?: boolean;
  resolutionTaskId?: string;
  resolutionTaskType?: string;
  resolutionEventType?: string;
  resolutionObjectType?: string;
  resolutionErrorCode?: string;
  matchedFlowId?: string;
  matchedRuleId?: string;
  targetPoolId?: string;
  routingPath?: string;
  assignmentCreated?: boolean;
  assignmentId?: string;
  selectedAgentId?: string;
  assignmentStatus?: string;
  assignmentReason?: string;
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
  status?: string;
  description?: string;
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
  updatedAt?: string;
}
