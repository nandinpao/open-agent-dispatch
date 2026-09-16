import type {
  CoreAdapterAction,
  CoreAgentAssignmentProfile,
  CoreAgentCapabilityAssignment,
  CoreAgentCapabilityCatalog,
  CoreAgentQualification,
  CoreAgentSkillDefinition,
  CoreAgentSkillEvaluationResult,
  CoreAssignmentProfileCapabilityBinding,
  CoreAssignmentProfilePolicyBinding,
  CoreDispatchPolicy,
  CoreDispatchTaskDefinition,
  CoreRecoveryApprovalRequest,
} from '@/lib/types/core';
import type {
  CoreDispatchEventStage,
  CoreDispatchRequest,
  CoreDispatchUserFacingError,
  CoreRoutingDecisionRecord,
  CoreTaskIssueTracking,
  CoreTaskRecord,
  CoreTaskRuntimeView,
  CoreTaskStatus,
} from '@/lib/types/domains/taskModel';
import type { CoreTaskEligibleAgentsResponse } from '@/lib/types/domains/taskEligibility';

/**
 * Canonical Task command, recovery, timeline, evidence and A2A classification types.
 */
export type CoreTaskRemediationCommandType =
  | 'REEVALUATE_ROUTING'
  | 'ASSIGN_AGENT'
  | 'CHANGE_POOL'
  | 'MOVE_TO_MANUAL_QUEUE'
  | 'RETRY_DELIVERY'
  | 'RETRY_TASK'
  | 'CANCEL_TASK'
  | 'IGNORE_TASK';

export interface CoreTaskRemediationCommandRequest {
  commandType: CoreTaskRemediationCommandType;
  expectedTaskVersion?: number;
  idempotencyKey: string;
  reason: string;
  operatorId?: string;
  payload?: Record<string, unknown>;
}

export interface CoreTaskCommandSnapshot {
  taskId?: string;
  status?: string;
  matchedFlowId?: string;
  matchedRuleId?: string;
  targetPoolId?: string;
  lifecycleReason?: string;
  dispatchRetryReason?: string;
  updatedAt?: string;
  version: number;
}

export interface CoreTaskCommandAudit {
  operatorId: string;
  timestamp: string;
  commandType: CoreTaskRemediationCommandType;
  reason: string;
  beforeState: CoreTaskCommandSnapshot;
  afterState: CoreTaskCommandSnapshot;
  idempotencyKey: string;
  expectedTaskVersion?: number;
  resultingTaskVersion: number;
  evidencePolicy: string;
}

export interface CoreTaskRemediationCommandResult {
  success: boolean;
  message: string;
  timestamp: string;
  taskId: string;
  commandType: CoreTaskRemediationCommandType;
  allowedCommandsAfter: CoreTaskRemediationCommandType[];
  audit: CoreTaskCommandAudit;
  task: CoreTaskRuntimeView;
  effect?: unknown;
  idempotentReplay: boolean;
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
  rootTaskId?: string;
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

export interface CoreTaskA2AClassificationFlowContract {
  modelVersion?: string;
  currentAuthorityModel?: string;
  a2aDelegationAuthority?: boolean;
  continuationRoutingMode?: string;
  recommendedPoolRoutingAuthority?: boolean;
  canonicalA2ADelegationAuthority?: string;
  coreOwnedTaskCreation?: boolean;
  agentCanCreateTask?: boolean;
  cycleDetectionRequired?: boolean;
  idempotencyRequired?: boolean;
  defaultMaxA2ADepth?: number;
  childTaskCreationAuthority?: string;
  unclearClassificationFallback?: string;
  requiredRequestFields?: string[];
  governanceFields?: string[];
  childTaskRules?: string[];
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
  parentTaskId?: string;
  rootTaskId?: string;
  correlationId?: string;
  classificationVersion?: string;
  maxA2ADepth?: number;
  idempotencyKey?: string;
}

export interface CoreTaskClassificationResult {
  parentTaskId?: string;
  rootTaskId?: string;
  correlationId?: string;
  classificationVersion?: string;
  idempotencyKey?: string;
  a2aDepth?: number;
  maxA2ADepth?: number;
  cycleDetected?: boolean;
  coreOwnedTaskCreation?: boolean;
  agentCreatedTask?: boolean;
  manualReviewRequired?: boolean;
  nextAction?: string;
  governanceReason?: string;
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

export type CoreTaskFailureDomain =
  | 'NONE' | 'CALLER_INPUT' | 'AUTHENTICATION' | 'AUTHORIZATION' | 'ROUTING'
  | 'AGENT_RUNTIME' | 'A2A' | 'EXTERNAL_SYSTEM' | 'PLATFORM' | 'UNKNOWN';

export type CoreTaskLineageEventType =
  | 'TASK_CREATED' | 'A2A_DELEGATED' | 'EXECUTOR_ASSIGNED' | 'EXECUTOR_REASSIGNED'
  | 'EXECUTION_FAILED' | 'EXECUTION_COMPLETED';

export interface CoreTaskLineageEvidence {
  tenantId: string; evidenceId: string; rootTaskId: string; taskId: string; parentTaskId?: string;
  eventType: CoreTaskLineageEventType; originPrincipalType: string; originPrincipalId?: string;
  actorPrincipalType: string; actorPrincipalId?: string; executorAgentId?: string; assignmentId?: string;
  departmentId?: string; groupId?: string; credentialId?: string; oauthClientId?: string; sourceSystem?: string;
  correlationId?: string; traceId?: string; failureDomain: CoreTaskFailureDomain; failureCode?: string; reason?: string; occurredAt?: string;
}

export type CoreTaskOperationsSectionStatus = 'READY' | 'OMITTED' | 'UNAVAILABLE';

export interface CoreTaskOperationsSection<T = unknown> {
  name: 'overview' | 'execution' | 'issue' | 'relationships' | string;
  status: CoreTaskOperationsSectionStatus;
  revision: number;
  errorCode?: string;
  errorMessage?: string;
  payload?: T;
}

export interface CoreTaskOperationsOverviewPayload {
  runtimeView: unknown;
  revision: number;
  executionRevision: number;
  issueRevision: number;
  relationshipsRevision: number;
}

export interface CoreTaskOperationsExecutionPayload {
  journey?: unknown;
  dispatchRequests?: CoreDispatchRequest[];
  attemptHistory?: import('@/lib/types/domains/taskRuntime').CoreDispatchAttemptHistoryRecord[];
  dispatchLedger?: import('@/lib/types/domains/taskRuntime').CoreDispatchAttemptLedger[];
  callbackInbox?: import('@/lib/types/domains/taskRuntime').CoreCallbackInboxEntry[];
  callbackInboxSummary?: import('@/lib/types/domains/taskRuntime').CoreCallbackInboxSummary;
  timeline?: CoreDispatchTimelineResponse;
  caseTimeline?: CoreTaskCaseTimelineView;
  routingDecisions?: CoreRoutingDecisionRecord[];
  dispatchEvidence?: CoreTaskDispatchEvidenceView;
  runtimeVerification?: CoreTaskRuntimeVerificationView;
  revision: number;
}

export interface CoreIssuePolicyDecisionView {
  tenantId?: string;
  decisionId: string;
  taskId: string;
  projectionPurpose?: string;
  policyId?: string;
  policyVersion?: number;
  taskIssueSyncPolicy?: string;
  decision?: 'NOT_REQUIRED' | 'REQUIRED' | 'MANUAL_DECISION' | string;
  reasonCode?: string;
  taskStatus?: string;
  bindingStatus?: string;
  connectionId?: string;
  projectMappingId?: string;
  projectMappingVersion?: number;
  issueOperation?: string;
  adapterActionId?: string;
  actionIdempotencyKey?: string;
  automationStatus?: string;
  lastErrorCode?: string;
  lastErrorMessage?: string;
  correlationId?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoreAdapterExecutorAuditView {
  auditId: string;
  actionId?: string;
  taskId?: string;
  incidentId?: string;
  tenantId?: string;
  correlationId?: string;
  a2aRequestId?: string;
  sourceSystemId?: string;
  connectionId?: string;
  projectMappingId?: string;
  externalProjectId?: string;
  externalIssueId?: string;
  providerStatusCode?: number;
  providerFailureCode?: string;
  providerHealthImpact?: string;
  providerOutcomeCertainty?: string;
  idempotencyKey?: string;
  operationFingerprint?: string;
  technicalPrincipalId?: string;
  credentialId?: string;
  credentialVersion?: string;
  adapterType?: string;
  actionType?: string;
  executorName?: string;
  beforeStatus?: string;
  afterStatus?: string;
  outcome?: string;
  message?: string;
  attemptCount?: number;
  createdAt?: string;
  payloadSnapshot?: Record<string, unknown>;
}

export interface CoreTaskOperationsIssuePayload {
  issueTracking?: CoreTaskIssueTracking;
  issueDedup?: import('@/lib/types/domains/taskModel').CoreTaskIssueDedupSummary;
  issuePolicyDecision?: CoreIssuePolicyDecisionView;
  adapterActions?: CoreAdapterAction[];
  providerExecutions?: CoreAdapterExecutorAuditView[];
  revision: number;
}

export interface CoreTaskOperationsRelationshipsPayload {
  rootTaskId: string;
  parentTask?: CoreTaskRecord;
  childTasks: CoreTaskRecord[];
  directChildCount: number;
  revision: number;
}

export interface CoreTaskOperationsView {
  taskId: string;
  tenantId?: string;
  correlationId?: string;
  snapshotRevision: number;
  generatedAt?: string;
  overview: CoreTaskOperationsSection<CoreTaskOperationsOverviewPayload>;
  execution: CoreTaskOperationsSection<CoreTaskOperationsExecutionPayload>;
  issue: CoreTaskOperationsSection<CoreTaskOperationsIssuePayload>;
  relationships: CoreTaskOperationsSection<CoreTaskOperationsRelationshipsPayload>;
}

export interface CoreTaskFinalizationStep {
  name: string;
  order: number;
  status: string;
  attempts: number;
  lastErrorCode?: string;
  lastErrorMessage?: string;
  completedAt?: string;
  updatedAt?: string;
}

export interface CoreTaskConditionAuthority {
  type: string;
  state: string;
  blocking: boolean;
  resolution: string;
  timeoutAt?: string;
  reasonCode?: string;
  reason?: string;
  raisedAt?: string;
  resolvedAt?: string;
}

export interface CoreTaskCanonicalStateAuthority {
  taskId: string;
  legacyStatus?: string;
  lifecycle: string;
  phase: string;
  outcome: string;
  terminalizationReason?: string;
  terminalizationRequestedAt?: string;
  outcomeResolverVersion?: string;
  cancellationState?: string;
  finalizationState: string;
  checkpoint?: string;
  attemptCount: number;
  nextAttemptAt?: string;
  lastErrorCode?: string;
  lastErrorMessage?: string;
  completedAt?: string;
  evidenceRequirement?: string;
}

export interface CoreTaskFinalizationAuthorityView {
  tenantId: string;
  task: CoreTaskCanonicalStateAuthority;
  steps: CoreTaskFinalizationStep[];
  conditions: CoreTaskConditionAuthority[];
}

export interface CoreTaskFinalizationRecoveryItem {
  taskId: string;
  legacyStatus?: string;
  terminalizationReason?: string;
  finalizationState: string;
  checkpoint?: string;
  attemptCount: number;
  nextAttemptAt?: string;
  lastErrorCode?: string;
  lastErrorMessage?: string;
  terminalizationRequestedAt?: string;
}


export interface CoreFlowRuleEvaluationEvidence {
  ruleId?: string;
  ruleCode?: string;
  flowId?: string;
  priority?: number;
  matched?: boolean;
  failedCriterion?: string;
  failedCriteria?: string[];
  matchedCriteriaCount?: number;
  totalCriteriaCount?: number;
  matchRatio?: number;
}

export interface CoreTaskFlowMatchDecisionAuthority {
  decisionId: string;
  taskId: string;
  decisionSource: string;
  flowId?: string;
  flowVersion?: string;
  evaluatedRuleCount: number;
  matchedRuleId?: string;
  matchedRulePriority?: number;
  evaluatedRuleSummary: CoreFlowRuleEvaluationEvidence[];
  outputServiceCode?: string;
  outputCapabilityRequirements: string[];
  matchResult: 'MATCHED' | 'NO_MATCH' | 'AMBIGUOUS' | string;
  evaluationSetRef?: string;
  evaluationSetDigest?: string;
  closestRuleId?: string;
  closestRulePriority?: number;
  closestRuleFailedCriterion?: string;
  closestRuleMatchRatio?: number;
  decisionAuthorityVersion?: string;
  decidedAt?: string;
  evaluatorVersion?: string;
}

export interface CoreTaskFlowEvaluationSetAuthority {
  evaluationSetId: string;
  flowId?: string;
  flowVersion?: string;
  inputSnapshot: Record<string, unknown>;
  evaluatedRules: CoreFlowRuleEvaluationEvidence[];
  closestRules: CoreFlowRuleEvaluationEvidence[];
  evaluatedRuleCount: number;
  matchResult: string;
  minimumMatchedPriority?: number;
  evaluationDigest?: string;
  evaluatorVersion?: string;
  createdAt?: string;
}

export interface CoreTaskIssuePolicyAuthority {
  ruleIssueSyncPolicy?: string;
  flowIssueSyncPolicy?: string;
  issueSyncPolicySource?: string;
  effectiveIssueSyncPolicy?: string;
}

export interface CoreTaskFlowMatchAuthorityView {
  tenantId: string;
  decision: CoreTaskFlowMatchDecisionAuthority;
  evaluationSet?: CoreTaskFlowEvaluationSetAuthority | null;
  issuePolicyAuthority?: CoreTaskIssuePolicyAuthority | null;
}


/**
 * Canonical Task dispatch-contract, readiness and compatibility read models.
 *
 * These types are physically owned by the Task domain; core.ts only re-exports
 * them for compatibility with older all-domain consumers.
 */
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
