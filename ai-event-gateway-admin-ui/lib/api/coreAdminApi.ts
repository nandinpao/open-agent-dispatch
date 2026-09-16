import {
  coreApiDelete,
  coreApiGet,
  coreApiPost,
  coreApiPut,
  coreTenantApiGet,
  requireCoreTenantContext,
} from "@/lib/api/coreClient";
import { taskAdminApi } from "@/lib/api/domains/taskAdminApi";
import { sourceSystemsAdminApi } from "@/lib/api/domains/sourceSystemsAdminApi";
import { normalizeCoreAgentRuntimeViewPayload } from "@/lib/api/domains/agentRuntimeNormalizer";
import { coreAdminEndpoints } from "@/lib/api/endpoints";
import type {
  AgentCredentialIssueRequest,
  AgentProfileApprovalRequest,
  AgentEnrollmentApprovalRequest,
  AgentEnrollmentCreateRequest,
  AgentEnrollmentRequest,
  AgentGovernanceTableDiagnostic,
  AgentProfileUpdateRequest,
  AgentSecurityEvent,
  CoreAgentProfile,
  CoreAgentRuntimeCapabilityItem,
  CoreAgentRuntimeCapabilityProfile,
  CoreAgentRuntimeDescriptor,
  CoreAgentPoolView,
  CoreDispatchFlowAgentOptionView,
  CoreDispatchFlowAgentView,
  CoreDispatchFlowReadinessRequest,
  CoreDispatchFlowReadinessResponse,
  CoreDispatchSimulationRequest,
  CoreDispatchSimulationResponse,
  CoreDispatchFlowRuleView,
  CoreDispatchFlowRuleConflictView,
  CoreFlowDirectAgentCompatibilityBinding,
  CoreFlowCapabilityBridgeRefresh,
  CoreFlowCapabilityLegacyEquivalenceEvidence,
  CoreFlowCapabilityEquivalenceReadiness,
  CoreDispatchFlowRequiredSkillView,
  CoreDispatchFlowView,
  CoreDispatchFlowTraceChainView,
  CoreEventIntakeEnvelope,
  CoreEventIntakeDecisionResponse,
  CoreDispatchPolicy,
  CoreDispatchPolicyScope,
  CoreDispatchPolicyRequiredCapability,
  CoreDispatchPolicyRequiredRuntimeFeature,
  CoreDispatchPolicyQualityRule,
  CoreDispatchPolicyScoringRule,
  CoreAgentAssignmentProfile,
  CoreAssignmentProfileImpactAction,
  CoreAssignmentProfileImpactPreview,
  CoreAssignmentProfileRelationshipMap,
  CoreAssignmentProfilePolicyBinding,
  CoreAssignmentProfileCapabilityBinding,
  CoreAgentCapabilityCatalog,
  CoreCanonicalCapabilityDefinition,
  CoreCapabilityRequirement,
  CoreCapabilityProvider,
  CoreCapabilityBinding,
  CoreCapabilityBindingTrustEvent,
  CoreDelegationPolicy,
  CoreDelegationPolicyAuditEvent,
  CoreDelegationPolicyVersion,
  CoreDelegationAuthorizationRequest,
  CoreDelegationAuthorizationDecision,
  CoreRoutingProfile,
  CoreRoutingProfileVersion,
  CoreProviderEligibilityObservation,
  CoreProviderRoutingPreviewRequest,
  CoreProviderRoutingDecision,
  CoreRoutingAuthorityShadowRequest,
  CoreRoutingAuthorityShadowResult,
  CoreRebindingAdmissionRequest,
  CoreExecutionSafetyActivationRequest,
  CoreExecutionSafetyPrepareRequest,
  CoreExecutionSafetyPrepareResult,
  CoreRuntimeAcceptanceSummary,
  CoreProductionFoundationSummary,
  CoreRebindingAdmissionDecision,
  CoreFlowRoutingMigrationState,
  CoreExecutionAdapterRegistration,
  CoreExecutionAdapterVersion,
  CoreExecutionAdapterResolutionRequest,
  CoreExecutionAdapterResolution,
  CoreTriagePolicy,
  CoreTriagePolicyVersion,
  CoreTriagePreviewRequest,
  CoreTriageRequest,
  CoreTriageProposal,
  CoreTriageDecision,
  CoreExecutionPlanPolicy,
  CorePlanAdmissionPolicy,
  CorePlanAdmissionDecision,
  CoreBindingAuthorizationEnvelope,
  CorePlanAdmissionBindingEvaluation,
  CorePlanAdmissionResult,
  CoreExecutionPlanPolicyVersion,
  CoreExecutionPlanPreviewRequest,
  CoreExecutionPlanRequest,
  CoreExecutionPlanProposal,
  CoreExecutionPlanAmendmentRequest,
  CoreExecutionPlan,
  CoreExecutionPlanRevision,
  CoreExecutionPlanDecision,
  CorePlanExecutionPolicy,
  CorePlanExecutionPolicyVersion,
  CorePlanExecutionStartRequest,
  CorePlanExecutionRun,
  CoreGovernedPlanExecutionStep,
  CorePlanStepAuthorityRequest,
  CorePlanStepSubmitRequest,
  CorePlanStepCompletionRequest,
  CorePlanExecutionAttempt,
  CorePlanExecutionArtifact,
  CorePlanExecutionEvent,
  CoreAggregationDefinition,
  CoreAggregationDefinitionVersion,
  CoreAggregationPreparationRequest,
  CoreAggregationPreparationDecision,
  CoreCaseConvergenceRequest,
  CoreEnterpriseCaseRecord,
  CoreCaseArtifactLink,
  CoreCaseReviewRequest,
  CoreCaseIssueProjectionRequest,
  CoreCaseIssueProjection,
  CoreCaseEvent,
  CoreLearningPolicy,
  CoreLearningPolicyVersion,
  CoreExecutionOutcomeEvidence,
  CoreRoutingPatternCandidate,
  CoreRoutingPattern,
  CoreRoutingPatternVersion,
  CorePatternPromotionRequest,
  CorePatternPromotionDecision,
  CorePatternQualitySnapshot,
  CoreDriftObservation,
  CoreFastPathResolutionRequest,
  CoreFastPathResolutionDecision,
  CoreFastPathRuntimePolicy,
  CoreFastPathRuntimePolicyVersion,
  CoreFastPathShadowComparisonRequest,
  CoreFastPathShadowComparison,
  CoreFastPathCertificationRequest,
  CoreFastPathRuntimeCertification,
  CoreFastPathRuntimeDecision,
  CoreRuntimeStepAuthorityPolicy,
  CoreRuntimeStepAuthorityPolicyVersion,
  CoreRuntimeStepAuthorityDecision,
  CoreRuntimeStepAuthorityAutomationResult,
  CoreAgentCapabilityAssignment,
  CoreAgentCapabilityCommand,
  CoreAgentAdvisoryRecommendation,
  CoreAdvancedSelectionStrategyContract,
  CoreAgentPoolCapabilityPolicy,
  CoreAgentAdvisoryRecommendationDecisionCommand,
  CoreRuntimeFeatureCatalog,
  CoreRuntimeResource,
  CoreSupplyProfile,
  CoreAgentQualityMetricsDaily,
  CoreAgentQualityMetricsWindow,
  CoreRuntimeQualityMetricsDaily,
  CoreSupplyProfileQualitySnapshot,
  CoreAgentRuntimeBinding,
  CoreAgentRuntimeFeatureObservation,
  CoreAgentRuntimeFeatureTrust,
  CoreAgentRuntimeFeatureCommand,
  CoreAgentQualification,
  CoreAgentQualificationCommand,
  CoreAgentDispatchEligibility,
  CoreAgentEnterpriseGovernanceSummary,
  CoreAgentRuntimeLoadSnapshot,
  CoreAgentSetupRequest,
  CoreAgentSetupReadinessResponse,
  CoreAgentOperationalView,
  CoreAgentLatestAuthFailureResponse,
  CoreAgentConnectionRepairActionRequest,
  CoreAgentConnectionRepairActionResult,
  CoreAgentConnectionRepairActionsResponse,
  CoreAgentSetupResponse,
  CoreDashboardSnapshot,
  CoreRecoveryOperationMetricsSnapshot,
  CoreRecoveryApprovalRequest,
  CoreRecoveryGovernanceActionRequest,
  CoreRecoveryGovernanceActionResult,
  CoreRecoveryOperatorRunbook,
  CoreAdapterAction,
  CoreAdapterActionMetadata,
  CoreRuntimeDisconnectResult,
  CoreRuntimeDisconnectAllRequest,
  CoreRuntimeDisconnectReconcileRequest,
  CoreRuntimeDisconnectReconcileReport,
  CoreDuplicateRuntimeSecurityRequest,
  CoreDuplicateRuntimeResolveRequest,
  CoreAgentSecurityEnforcementResponse,
  CoreAgentSecurityEnforcementPolicy,
  CoreAgentSecurityEnforcementPolicyUpdateRequest,
  CoreAgentSkillDefinition,
  CoreAgentSkillEvaluationRequest,
  CoreAgentSkillEvaluationResult,
  CoreAgentApprovedSkill,
  CoreAgentApprovedSkillSyncCommand,
  CoreAgentApprovedSkillSyncResult,
  CoreAgentSkillRegistryMetadata,
  CoreAgentSkillVersion,
  CoreAgentSkillAuditEntry,
  CoreAgentSkillWorkflowCommand,
  CoreAgentSkillWorkflowResult,
  CoreAgentSkillDiffResult,
  CoreAgentSkillImpactAnalysisResult,
  CoreAgentSkillApprovalPolicy,
  CoreAgentCapabilityDriftReport,
  CoreSkillDriftPolicyEvaluationRequest,
  CoreSkillDriftPolicyEvaluationResponse,
  CoreAgentSkillDeprecationCommand,
  CoreAgentSkillDeprecationMigrationPlan,
  CoreAgentSkillDeprecationPlan,
  CoreAgentSkillDependencyCommand,
  CoreAgentSkillDependencyEdge,
  CoreAgentSkillDependencyGraph,
  CoreAgentSkillRemediationProposal,
  CoreAgentRemediationProposal,
  CoreAgentRemediationProposalRequest,
  CoreAgentRemediationWorkflow,
  CoreAgentRemediationStaleLeaseQueue,
  CoreAgentRemediationRecoveredLeaseQueue,
  CoreAgentRemediationStaleLeaseRecoveryRun,
  CoreAgentRemediationWorkflowCreateRequest,
  CoreAgentRemediationWorkflowDecisionRequest,
  CoreAdapterExecutorAuditRecord,
  CoreEnforceObservabilitySnapshot,
  CoreEnforceRoutingAuditRecord,
  CoreEnforceOperatorIncidentRequest,
  CoreEnforceOperatorIncidentResult,
  CoreEnforceLegacyFinalReportItem,
  CoreEnforceArtifactRetentionRecord,
} from "@/lib/types/core";
export { normalizeCoreAgentRuntimeViewPayload } from "@/lib/api/domains/agentRuntimeNormalizer";
export { normalizeCoreTaskRecord, normalizeCoreTaskRuntimeViewPayload } from "@/lib/api/domains/taskRuntimeNormalizer";
type PageLike<T> =
  | T[]
  | {
      content?: T[];
      items?: T[];
      records?: T[];
      rows?: T[];
      data?: T[];
    };
function toList<T>(value: PageLike<T>): T[] {
  if (Array.isArray(value)) return value;
  return (
    value.content ??
    value.items ??
    value.records ??
    value.rows ??
    value.data ??
    []
  );
}

async function coreGetList<T>(path: string): Promise<T[]> {
  return toList(await coreApiGet<PageLike<T>>(path));
}

function requireTenantId(tenantId: string | undefined, operation: string): string {
  void operation;
  return requireCoreTenantContext(tenantId);
}

function optimisticLockHeaders(version?: number | null): Record<string, string> | undefined {
  return typeof version === 'number' && Number.isFinite(version) && version > 0
    ? { 'If-Match': String(Math.trunc(version)) }
    : undefined;
}

function legacySkillMutationRetired<T>(operation: string): Promise<T> {
  return Promise.reject(
    new Error(
      `LEGACY_SKILL_AUTHORITY_RETIRED: ${operation} is historical/read-only. Use Canonical Capability definitions and Agent Capability Assignment.`,
    ),
  );
}

export const coreAdminApi = {
  ...taskAdminApi,
  ...sourceSystemsAdminApi,

  getEnforceObservabilitySnapshot(): Promise<CoreEnforceObservabilitySnapshot> {
    return coreApiGet<CoreEnforceObservabilitySnapshot>(
      coreAdminEndpoints.enforceObservabilitySnapshot,
    );
  },

  searchEnforceRoutingAudit(params?: { taskId?: string; agentId?: string; blockingCode?: string; policyCode?: string; window?: string; limit?: number }): Promise<CoreEnforceRoutingAuditRecord[]> {
    const query = new URLSearchParams();
    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && String(value).trim()) query.set(key, String(value));
    });
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return coreGetList<CoreEnforceRoutingAuditRecord>(
      `${coreAdminEndpoints.enforceRoutingAuditSearch}${suffix}`,
    );
  },

  createEnforceOperatorIncident(body: CoreEnforceOperatorIncidentRequest): Promise<CoreEnforceOperatorIncidentResult> {
    return coreApiPost<CoreEnforceOperatorIncidentResult>(
      coreAdminEndpoints.enforceOperatorIncidents,
      body,
    );
  },

  getEnforceLegacyFinalReport(): Promise<CoreEnforceLegacyFinalReportItem[]> {
    return coreGetList<CoreEnforceLegacyFinalReportItem>(
      coreAdminEndpoints.enforceLegacyFinalReport,
    );
  },

  getEnforceArtifactRetention(): Promise<CoreEnforceArtifactRetentionRecord[]> {
    return coreGetList<CoreEnforceArtifactRetentionRecord>(
      coreAdminEndpoints.enforceArtifactRetention,
    );
  },

  getDashboardSnapshot(): Promise<CoreDashboardSnapshot> {
    return coreApiGet<CoreDashboardSnapshot>(
      coreAdminEndpoints.dashboardSnapshot(requireCoreTenantContext()),
    );
  },

  getAgentGovernanceSummary(): Promise<Record<string, unknown>> {
    return coreApiGet<Record<string, unknown>>(
      coreAdminEndpoints.agentGovernanceSummary(requireCoreTenantContext()),
    );
  },

  getAgentGovernanceMetadata(): Promise<Record<string, unknown>> {
    return coreApiGet<Record<string, unknown>>(
      coreAdminEndpoints.agentGovernanceMetadata,
    );
  },

  getAgentGovernanceTableDiagnostics(): Promise<
    AgentGovernanceTableDiagnostic[]
  > {
    return coreGetList<AgentGovernanceTableDiagnostic>(
      coreAdminEndpoints.agentGovernanceTableDiagnostics,
    );
  },

  getAgentEnrollments(): Promise<AgentEnrollmentRequest[]> {
    return coreGetList<AgentEnrollmentRequest>(
      coreAdminEndpoints.agentEnrollments,
    );
  },

  createAgentEnrollment(
    body: AgentEnrollmentCreateRequest,
  ): Promise<AgentEnrollmentRequest> {
    return coreApiPost<AgentEnrollmentRequest>(
      coreAdminEndpoints.agentEnrollments,
      body,
    );
  },

  setupAgent(body: CoreAgentSetupRequest): Promise<CoreAgentSetupResponse> {
    return coreApiPost<CoreAgentSetupResponse>(
      coreAdminEndpoints.agentSetup,
      body,
    );
  },

  getAgentSetupReadiness(agentId: string): Promise<CoreAgentSetupReadinessResponse> {
    return coreApiGet<CoreAgentSetupReadinessResponse>(
      coreAdminEndpoints.agentSetupReadiness(agentId),
    );
  },

  getAgentOperationalView(agentId: string): Promise<CoreAgentOperationalView> {
    return coreApiGet<CoreAgentOperationalView>(
      coreAdminEndpoints.agentOperationalView(agentId),
    );
  },

  approveAgentEnrollment(
    enrollmentId: string,
    body?: AgentEnrollmentApprovalRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentEnrollmentApprove(enrollmentId),
      body ?? {},
    );
  },

  rejectAgentEnrollment(
    enrollmentId: string,
    body?: unknown,
  ): Promise<AgentEnrollmentRequest> {
    return coreApiPost<AgentEnrollmentRequest>(
      coreAdminEndpoints.agentEnrollmentReject(enrollmentId),
      body ?? {},
    );
  },

  getAgents(): Promise<CoreAgentProfile[]> {
    return coreGetList<CoreAgentProfile>(coreAdminEndpoints.agents);
  },

  async getAgentsRuntimeView(): Promise<CoreAgentProfile[]> {
    return normalizeCoreAgentRuntimeViewPayload(
      await coreApiGet<unknown>(coreAdminEndpoints.agentsRuntimeView(requireCoreTenantContext())),
    );
  },

  getAgent(agentId: string): Promise<CoreAgentProfile> {
    return coreApiGet<CoreAgentProfile>(
      coreAdminEndpoints.agentDetail(agentId),
    );
  },

  updateAgentProfile(
    agentId: string,
    body: AgentProfileUpdateRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPut<CoreAgentProfile>(
      coreAdminEndpoints.agentUpdate(agentId),
      body,
    );
  },

  updateAgentDispatchAccess(
    agentId: string,
    body: Pick<AgentProfileUpdateRequest, "tenantId" | "scopes" | "reason">,
  ): Promise<CoreAgentProfile> {
    return coreApiPut<CoreAgentProfile>(
      coreAdminEndpoints.agentUpdate(agentId),
      { ...body, reason: body.reason ?? "Updated Dispatch Access from the Agent workspace" },
    );
  },

  enableAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentEnable(agentId),
      body ?? {},
    );
  },

  disableAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentDisable(agentId),
      body ?? {},
    );
  },

  suspendAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentSuspend(agentId),
      body ?? {},
    );
  },

  revokeAgent(agentId: string, body?: unknown): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentRevoke(agentId),
      body ?? {},
    );
  },

  approveAgent(
    agentId: string,
    body?: AgentProfileApprovalRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentApprove(agentId),
      body ?? {},
    );
  },

  issueAgentCredential(
    agentId: string,
    body: AgentCredentialIssueRequest,
  ): Promise<CoreAgentProfile> {
    return coreApiPost<CoreAgentProfile>(
      coreAdminEndpoints.agentCredentialIssue(agentId),
      body,
    );
  },

  getDispatchPoliciesV2(status?: string | null, tenantId = ""): Promise<CoreDispatchPolicy[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch policy lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchPolicy>(`${coreAdminEndpoints.dispatchPoliciesV2}${suffix}`);
  },

  upsertDispatchPolicyV2(policyCode: string, body: CoreDispatchPolicy, tenantId = ""): Promise<CoreDispatchPolicy> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch policy update");
    const suffix = `?tenantId=${encodeURIComponent(scopedTenantId)}`;
    return coreApiPut<CoreDispatchPolicy>(`${coreAdminEndpoints.dispatchPolicyV2(policyCode)}${suffix}`, body);
  },

  addDispatchPolicyScope(policyCode: string, body: CoreDispatchPolicyScope): Promise<CoreDispatchPolicyScope> {
    return coreApiPost<CoreDispatchPolicyScope>(coreAdminEndpoints.dispatchPolicyScopes(policyCode), body);
  },

  addDispatchPolicyRequiredCapability(policyCode: string, body: CoreDispatchPolicyRequiredCapability): Promise<CoreDispatchPolicyRequiredCapability> {
    return coreApiPost<CoreDispatchPolicyRequiredCapability>(coreAdminEndpoints.dispatchPolicyRequiredCapabilities(policyCode), body);
  },

  addDispatchPolicyRequiredRuntimeFeature(policyCode: string, body: CoreDispatchPolicyRequiredRuntimeFeature): Promise<CoreDispatchPolicyRequiredRuntimeFeature> {
    return coreApiPost<CoreDispatchPolicyRequiredRuntimeFeature>(coreAdminEndpoints.dispatchPolicyRequiredRuntimeFeatures(policyCode), body);
  },

  addDispatchPolicyQualityRule(policyCode: string, body: CoreDispatchPolicyQualityRule): Promise<CoreDispatchPolicyQualityRule> {
    return coreApiPost<CoreDispatchPolicyQualityRule>(coreAdminEndpoints.dispatchPolicyQualityRules(policyCode), body);
  },

  addDispatchPolicyScoringRule(policyCode: string, body: CoreDispatchPolicyScoringRule): Promise<CoreDispatchPolicyScoringRule> {
    return coreApiPost<CoreDispatchPolicyScoringRule>(coreAdminEndpoints.dispatchPolicyScoringRules(policyCode), body);
  },

  getDispatchFlowAgentOptions(tenantId = ""): Promise<CoreDispatchFlowAgentOptionView[]> {
    const scopedTenantId = requireTenantId(tenantId, "Dispatch Flow Agent options lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreGetList<CoreDispatchFlowAgentOptionView>(`${coreAdminEndpoints.dispatchFlowAgentOptions}?${query.toString()}`);
  },

  getAgentPools(tenantId = "", sourceSystem?: string | null): Promise<CoreAgentPoolView[]> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    if (sourceSystem) query.set("sourceSystem", sourceSystem);
    return coreGetList<CoreAgentPoolView>(`${coreAdminEndpoints.agentPools}?${query.toString()}`);
  },

  getAgentPool(poolId: string, tenantId = ""): Promise<CoreAgentPoolView> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool detail lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiGet<CoreAgentPoolView>(`${coreAdminEndpoints.agentPool(poolId)}?${query.toString()}`);
  },

  createAgentPool(body: CoreAgentPoolView, tenantId = ""): Promise<CoreAgentPoolView> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool creation");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPost<CoreAgentPoolView>(`${coreAdminEndpoints.agentPools}?${query.toString()}`, { ...body, tenantId: scopedTenantId });
  },

  updateAgentPool(poolId: string, body: CoreAgentPoolView, tenantId = ""): Promise<CoreAgentPoolView> {
    const scopedTenantId = requireTenantId(tenantId, "Agent Pool update");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPut<CoreAgentPoolView>(`${coreAdminEndpoints.agentPool(poolId)}?${query.toString()}`, { ...body, tenantId: scopedTenantId, poolId }, { headers: optimisticLockHeaders(body.version) });
  },

  getDispatchFlow(flowId: string, tenantId = ""): Promise<CoreDispatchFlowView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow detail lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlow(flowId)}${params}`);
  },

  createDispatchFlow(body: CoreDispatchFlowView, tenantId = ""): Promise<CoreDispatchFlowView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow creation");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlows}${params}`, body);
  },

  updateDispatchFlow(flowId: string, body: CoreDispatchFlowView, tenantId = ""): Promise<CoreDispatchFlowView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow update");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPut<CoreDispatchFlowView>(`${coreAdminEndpoints.dispatchFlow(flowId)}${params}`, { ...body, tenantId: scopedTenantId, flowId }, { headers: optimisticLockHeaders(body.version) });
  },

  createDispatchFlowRealTestEvent(
    flowId: string,
    body: { message?: string; severity?: string; eventType?: string; objectType?: string; errorCode?: string; objectId?: string; correlationId?: string; siteId?: string; plantId?: string; expectedIssueSyncPolicy?: 'NONE' | 'OPTIONAL' | 'REQUIRED' | 'MANUAL' | string; expectExternalIssueOnSuccess?: boolean; attributes?: Record<string, unknown> } = {},
    tenantId = "",
  ): Promise<CoreEventIntakeDecisionResponse> {
    const scopedTenantId = requireTenantId(tenantId, "real Dispatch Flow test event");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPost<CoreEventIntakeDecisionResponse>(
      `${coreAdminEndpoints.dispatchFlowRealTestEvent(flowId)}?${query.toString()}`,
      body,
    );
  },

  simulateDispatch(body: CoreDispatchSimulationRequest, tenantId = ""): Promise<CoreDispatchSimulationResponse> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch simulation");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreApiPost<CoreDispatchSimulationResponse>(`${coreAdminEndpoints.dispatchSimulation}?${query.toString()}`, { ...body, tenantId: scopedTenantId });
  },

  dryRunDispatchFlow(body: CoreDispatchFlowReadinessRequest, tenantId = ""): Promise<CoreDispatchFlowReadinessResponse> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow dry-run");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowReadinessResponse>(`${coreAdminEndpoints.dispatchFlowDryRun}${params}`, body);
  },

  dryRunDispatchFlowById(flowId: string, body: CoreDispatchFlowReadinessRequest, tenantId = ""): Promise<CoreDispatchFlowReadinessResponse> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow scoped dry-run");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowReadinessResponse>(`${coreAdminEndpoints.dispatchFlowScopedDryRun(flowId)}${params}`, body);
  },

  getDispatchFlowRules(flowId: string, tenantId = ""): Promise<CoreDispatchFlowRuleView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow rule lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchFlowRuleView>(`${coreAdminEndpoints.dispatchFlowRules(flowId)}${params}`);
  },

  getDispatchFlowRuleConflicts(flowId: string, tenantId = ""): Promise<CoreDispatchFlowRuleConflictView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow rule conflict lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    return coreGetList<CoreDispatchFlowRuleConflictView>(`${coreAdminEndpoints.dispatchFlowRuleConflicts(flowId)}?${query.toString()}`);
  },

  getDispatchFlowCapabilityBridge(flowId: string, tenantId = ""): Promise<CoreFlowDirectAgentCompatibilityBinding[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow capability bridge lookup");
    return coreGetList<CoreFlowDirectAgentCompatibilityBinding>(`${coreAdminEndpoints.dispatchFlowCapabilityBridge(flowId)}?tenantId=${encodeURIComponent(scopedTenantId)}`);
  },

  refreshDispatchFlowCapabilityBridge(flowId: string, tenantId = ""): Promise<CoreFlowCapabilityBridgeRefresh> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow capability bridge refresh");
    return coreApiPost<CoreFlowCapabilityBridgeRefresh>(`${coreAdminEndpoints.dispatchFlowCapabilityBridgeRefresh(flowId)}?tenantId=${encodeURIComponent(scopedTenantId)}`, {});
  },

  getDispatchFlowLegacyEquivalence(flowId: string, tenantId = "", limit = 100): Promise<CoreFlowCapabilityLegacyEquivalenceEvidence[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow legacy equivalence lookup");
    return coreGetList<CoreFlowCapabilityLegacyEquivalenceEvidence>(`${coreAdminEndpoints.dispatchFlowLegacyEquivalence(flowId)}?tenantId=${encodeURIComponent(scopedTenantId)}&limit=${Math.max(1, Math.min(limit, 1000))}`);
  },

  getDispatchFlowLegacyEquivalenceReadiness(flowId: string, tenantId = ""): Promise<CoreFlowCapabilityEquivalenceReadiness> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow legacy equivalence readiness");
    return coreApiGet<CoreFlowCapabilityEquivalenceReadiness>(`${coreAdminEndpoints.dispatchFlowLegacyEquivalenceReadiness(flowId)}?tenantId=${encodeURIComponent(scopedTenantId)}`);
  },

  backfillDispatchFlowLegacyEquivalence(flowId: string, tenantId = "", limit = 100): Promise<Record<string, unknown>> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow legacy equivalence backfill");
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.dispatchFlowLegacyEquivalenceBackfill(flowId)}?tenantId=${encodeURIComponent(scopedTenantId)}&limit=${Math.max(1, Math.min(limit, 5000))}`, {});
  },

  getDispatchFlowSkills(flowId: string, tenantId = ""): Promise<CoreDispatchFlowRequiredSkillView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow skill lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchFlowRequiredSkillView>(`${coreAdminEndpoints.dispatchFlowSkills(flowId)}${params}`);
  },

  getDispatchFlowAgents(flowId: string, tenantId = ""): Promise<CoreDispatchFlowAgentView[]> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow agent lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDispatchFlowAgentView>(`${coreAdminEndpoints.dispatchFlowAgents(flowId)}${params}`);
  },

  previewDispatchFlowAgentAssignment(flowId: string, body: CoreDispatchFlowAgentView): Promise<Record<string, unknown>> {
    return coreApiPost<Record<string, unknown>>(coreAdminEndpoints.dispatchFlowAgentPreview(flowId), body);
  },

  previewDispatchFlowRule(flowId: string, body: CoreDispatchFlowRuleView): Promise<Record<string, unknown>> {
    return coreApiPost<Record<string, unknown>>(coreAdminEndpoints.dispatchFlowRulePreview(flowId), body);
  },

  getDispatchFlowTrace(flowId: string, tenantId = "", testMode = "CHAIN"): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow trace lookup");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    if (testMode) query.set("testMode", testMode);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTrace(flowId)}${params}`);
  },

  testDispatchFlowExternal(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow external test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestExternal(flowId)}${params}`, body);
  },

  testDispatchFlowA2a(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow A2A test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestA2a(flowId)}${params}`, body);
  },

  testDispatchFlowResult(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow result test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestResult(flowId)}${params}`, body);
  },

  testDispatchFlowChain(flowId: string, body: CoreEventIntakeEnvelope, tenantId = ""): Promise<CoreDispatchFlowTraceChainView> {
    const scopedTenantId = requireTenantId(tenantId, "dispatch flow chain test");
    const query = new URLSearchParams();
    query.set("tenantId", scopedTenantId);
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreDispatchFlowTraceChainView>(`${coreAdminEndpoints.dispatchFlowTestChain(flowId)}${params}`, body);
  },

  getAdvancedSelectionStrategyContracts(): Promise<CoreAdvancedSelectionStrategyContract[]> {
    return coreGetList<CoreAdvancedSelectionStrategyContract>(coreAdminEndpoints.advancedSelectionStrategyContracts);
  },

  getAgentPoolCapabilityPolicies(targetPoolId = "", tenantId = "", limit = 200): Promise<CoreAgentPoolCapabilityPolicy[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (targetPoolId) query.set("targetPoolId", targetPoolId);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentPoolCapabilityPolicy>(`${coreAdminEndpoints.agentPoolCapabilityPolicies}${suffix}`);
  },

  upsertAgentPoolCapabilityPolicy(targetPoolId: string, body: CoreAgentPoolCapabilityPolicy, tenantId = ""): Promise<CoreAgentPoolCapabilityPolicy> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAgentPoolCapabilityPolicy>(`${coreAdminEndpoints.agentPoolCapabilityPolicy(targetPoolId)}${suffix}`, body);
  },

  getAdvisoryRecommendations(
    targetPoolId = "",
    agentId = "",
    status = "",
    evidenceWindow = "24h",
    tenantId = "",
    limit = 100,
  ): Promise<CoreAgentAdvisoryRecommendation[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (targetPoolId) query.set("targetPoolId", targetPoolId);
    if (agentId) query.set("agentId", agentId);
    if (status) query.set("status", status);
    if (evidenceWindow) query.set("evidenceWindow", evidenceWindow);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentAdvisoryRecommendation>(`${coreAdminEndpoints.advisoryRecommendations}${suffix}`);
  },

  generateAdvisoryRecommendations(
    targetPoolId = "",
    agentId = "",
    evidenceWindow = "24h",
    tenantId = "",
    limit = 100,
  ): Promise<CoreAgentAdvisoryRecommendation[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (targetPoolId) query.set("targetPoolId", targetPoolId);
    if (agentId) query.set("agentId", agentId);
    if (evidenceWindow) query.set("evidenceWindow", evidenceWindow);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreApiPost<CoreAgentAdvisoryRecommendation[]>(`${coreAdminEndpoints.advisoryRecommendationsGenerate}${suffix}`, {});
  },

  acceptAdvisoryRecommendation(
    recommendationId: string,
    body: CoreAgentAdvisoryRecommendationDecisionCommand,
    tenantId = "",
  ): Promise<CoreAgentAdvisoryRecommendation> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreAgentAdvisoryRecommendation>(`${coreAdminEndpoints.advisoryRecommendationAccept(recommendationId)}${suffix}`, body);
  },

  rejectAdvisoryRecommendation(
    recommendationId: string,
    body: CoreAgentAdvisoryRecommendationDecisionCommand,
    tenantId = "",
  ): Promise<CoreAgentAdvisoryRecommendation> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreAgentAdvisoryRecommendation>(`${coreAdminEndpoints.advisoryRecommendationReject(recommendationId)}${suffix}`, body);
  },

  getAgentQualityDaily(agentId: string, tenantId = "", limit = 90): Promise<CoreAgentQualityMetricsDaily[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentQualityMetricsDaily>(`${coreAdminEndpoints.agentQualityDaily(agentId)}${suffix}`);
  },

  getAgentQualityWindows(agentId: string, metricWindow = "24h", tenantId = "", limit = 30): Promise<CoreAgentQualityMetricsWindow[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (metricWindow) query.set("metricWindow", metricWindow);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentQualityMetricsWindow>(`${coreAdminEndpoints.agentQualityWindows(agentId)}${suffix}`);
  },

  upsertAgentQualityWindow(agentId: string, body: CoreAgentQualityMetricsWindow, tenantId = ""): Promise<CoreAgentQualityMetricsWindow> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreAgentQualityMetricsWindow>(`${coreAdminEndpoints.agentQualityWindows(agentId)}${suffix}`, body);
  },

  getRuntimeQualityDaily(runtimeId: string, tenantId = "", limit = 90): Promise<CoreRuntimeQualityMetricsDaily[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreRuntimeQualityMetricsDaily>(`${coreAdminEndpoints.runtimeQualityDaily(runtimeId)}${suffix}`);
  },

  getSupplyProfileQualitySnapshots(metricWindow = "24h", agentId?: string, runtimeId?: string, tenantId = "", limit = 500): Promise<CoreSupplyProfileQualitySnapshot[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (metricWindow) query.set("metricWindow", metricWindow);
    if (agentId) query.set("agentId", agentId);
    if (runtimeId) query.set("runtimeId", runtimeId);
    if (limit) query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreSupplyProfileQualitySnapshot>(`${coreAdminEndpoints.supplyProfileQualitySnapshots}${suffix}`);
  },

  getSupplyProfileQualitySnapshot(profileCode: string, metricWindow = "24h", tenantId = ""): Promise<CoreSupplyProfileQualitySnapshot> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (metricWindow) query.set("metricWindow", metricWindow);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreSupplyProfileQualitySnapshot>(`${coreAdminEndpoints.supplyProfileQualitySnapshot(profileCode)}${suffix}`);
  },

  getSupplyProfiles(status?: string, agentId?: string, runtimeBindingId?: string, tenantId = ""): Promise<CoreSupplyProfile[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (agentId) query.set("agentId", agentId);
    if (runtimeBindingId) query.set("runtimeBindingId", runtimeBindingId);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreSupplyProfile>(`${coreAdminEndpoints.supplyProfiles}${suffix}`);
  },

  getAgentSupplyProfiles(agentId: string, status?: string, tenantId = ""): Promise<CoreSupplyProfile[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreSupplyProfile>(`${coreAdminEndpoints.agentSupplyProfiles(agentId)}${suffix}`);
  },

  upsertSupplyProfile(profileCode: string, body: CoreSupplyProfile, tenantId = ""): Promise<CoreSupplyProfile> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreSupplyProfile>(`${coreAdminEndpoints.supplyProfile(profileCode)}${suffix}`, body);
  },

  getRuntimeResources(status?: string, trustStatus?: string, tenantId = ""): Promise<CoreRuntimeResource[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (trustStatus) query.set("trustStatus", trustStatus);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreRuntimeResource>(`${coreAdminEndpoints.runtimeResources}${suffix}`);
  },

  upsertRuntimeResource(runtimeId: string, body: CoreRuntimeResource, tenantId = ""): Promise<CoreRuntimeResource> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreRuntimeResource>(`${coreAdminEndpoints.runtimeResource(runtimeId)}${suffix}`, body);
  },

  getAgentRuntimeBindings(agentId: string, status?: string): Promise<CoreAgentRuntimeBinding[]> {
    const query = new URLSearchParams();
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentRuntimeBinding>(`${coreAdminEndpoints.agentRuntimeBindings(agentId)}${suffix}`);
  },

  upsertAgentRuntimeBinding(agentId: string, body: CoreAgentRuntimeBinding): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindings(agentId), body);
  },

  activateAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingActivate(agentId, bindingId), body);
  },

  pauseAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingPause(agentId, bindingId), body);
  },

  resumeAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingResume(agentId, bindingId), body);
  },

  revokeAgentRuntimeBinding(agentId: string, bindingId: string, body: Partial<CoreAgentRuntimeBinding> = {}): Promise<CoreAgentRuntimeBinding> {
    return coreApiPost<CoreAgentRuntimeBinding>(coreAdminEndpoints.agentRuntimeBindingRevoke(agentId, bindingId), body);
  },

  getRuntimeFeatures(status?: string, tenantId = ""): Promise<CoreRuntimeFeatureCatalog[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreRuntimeFeatureCatalog>(`${coreAdminEndpoints.runtimeFeatures}${suffix}`);
  },

  upsertRuntimeFeature(featureCode: string, body: CoreRuntimeFeatureCatalog, tenantId = ""): Promise<CoreRuntimeFeatureCatalog> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreRuntimeFeatureCatalog>(`${coreAdminEndpoints.runtimeFeature(featureCode)}${suffix}`, body);
  },

  // Phase 1: canonical semantic WHAT catalog. Provider discovery/routing is intentionally not part of these APIs.
  getCanonicalCapabilities(
    status?: string,
    search?: string,
    serviceCode?: string,
    tenantId = "",
    limit = 200,
  ): Promise<CoreCanonicalCapabilityDefinition[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (search) query.set("search", search);
    if (serviceCode) query.set("serviceCode", serviceCode);
    query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreCanonicalCapabilityDefinition>(`${coreAdminEndpoints.canonicalCapabilities}${suffix}`);
  },

  getCanonicalCapability(capabilityCode: string, tenantId = ""): Promise<CoreCanonicalCapabilityDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiGet<CoreCanonicalCapabilityDefinition>(`${coreAdminEndpoints.canonicalCapability(capabilityCode)}${suffix}`);
  },

  upsertCanonicalCapability(
    capabilityCode: string,
    body: CoreCanonicalCapabilityDefinition,
    tenantId = "",
  ): Promise<CoreCanonicalCapabilityDefinition> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreCanonicalCapabilityDefinition>(`${coreAdminEndpoints.canonicalCapability(capabilityCode)}${suffix}`, body);
  },

  getCapabilityRequirementsByServiceCode(serviceCode: string, tenantId = ""): Promise<CoreCapabilityRequirement[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreCapabilityRequirement>(`${coreAdminEndpoints.capabilityRequirementsByServiceCode(serviceCode)}${suffix}`);
  },

  // Phase 2: WHO CAN registry. These APIs do not authorize, score, select or execute providers.
  getCapabilityProviders(providerType?: string, catalogStatus?: string, search?: string, tenantId = "", afterProviderId?: string, limit = 100): Promise<CoreCapabilityProvider[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (providerType) query.set("providerType", providerType);
    if (catalogStatus) query.set("catalogStatus", catalogStatus);
    if (search) query.set("search", search);
    if (afterProviderId) query.set("afterProviderId", afterProviderId);
    query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreCapabilityProvider>(`${coreAdminEndpoints.capabilityProviders}${suffix}`);
  },

  upsertCapabilityProvider(providerId: string, body: CoreCapabilityProvider, tenantId = ""): Promise<CoreCapabilityProvider> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreCapabilityProvider>(`${coreAdminEndpoints.capabilityProvider(providerId)}${suffix}`, body);
  },

  getCapabilityBindings(capabilityCode?: string, providerId?: string, trustStatus?: string, tenantId = "", afterBindingId?: string, limit = 100): Promise<CoreCapabilityBinding[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (capabilityCode) query.set("capabilityCode", capabilityCode);
    if (providerId) query.set("providerId", providerId);
    if (trustStatus) query.set("trustStatus", trustStatus);
    if (afterBindingId) query.set("afterBindingId", afterBindingId);
    query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreCapabilityBinding>(`${coreAdminEndpoints.capabilityBindings}${suffix}`);
  },

  upsertCapabilityBinding(bindingId: string, body: CoreCapabilityBinding, tenantId = "", changeReason?: string): Promise<CoreCapabilityBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreCapabilityBinding>(`${coreAdminEndpoints.capabilityBinding(bindingId)}${suffix}`, body, changeReason ? { headers: { "X-Change-Reason": changeReason } } : undefined);
  },

  getCapabilityBindingTrustEvents(bindingId: string, tenantId = ""): Promise<CoreCapabilityBindingTrustEvent[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreCapabilityBindingTrustEvent>(`${coreAdminEndpoints.capabilityBindingTrustEvents(bindingId)}${suffix}`);
  },

  // Phase 3: WHO MAY governance. Authorization is a hard gate and never ranks/selects providers.
  getDelegationPolicies(status?: string, effect?: string, capabilityCode?: string, search?: string, tenantId = "", afterPolicyId?: string, limit = 100): Promise<CoreDelegationPolicy[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (effect) query.set("effect", effect);
    if (capabilityCode) query.set("capabilityCode", capabilityCode);
    if (search) query.set("search", search);
    if (afterPolicyId) query.set("afterPolicyId", afterPolicyId);
    query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreDelegationPolicy>(`${coreAdminEndpoints.delegationPolicies}${suffix}`);
  },

  upsertDelegationPolicy(policyId: string, body: CoreDelegationPolicy, tenantId = "", changeReason?: string): Promise<CoreDelegationPolicy> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreDelegationPolicy>(`${coreAdminEndpoints.delegationPolicy(policyId)}${suffix}`, body, changeReason ? { headers: { "X-Change-Reason": changeReason } } : undefined);
  },

  getDelegationPolicyVersions(policyId: string, tenantId = ""): Promise<CoreDelegationPolicyVersion[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreDelegationPolicyVersion>(`${coreAdminEndpoints.delegationPolicyVersions(policyId)}${suffix}`);
  },

  getDelegationPolicyAuditEvents(policyId: string, tenantId = ""): Promise<CoreDelegationPolicyAuditEvent[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreDelegationPolicyAuditEvent>(`${coreAdminEndpoints.delegationPolicyAuditEvents(policyId)}${suffix}`);
  },

  evaluateDelegationAuthorizationPreview(body: CoreDelegationAuthorizationRequest, tenantId = ""): Promise<CoreDelegationAuthorizationDecision> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreDelegationAuthorizationDecision>(`${coreAdminEndpoints.delegationAuthorizationPreview}${suffix}`, body);
  },

  // Phase 4: WHO SHOULD preview. Only persisted Phase 3 PASS evidence may enter ranking; no transport is selected.
  getProviderRoutingProfiles(status?: string, search?: string, tenantId = "", afterProfileId?: string, limit = 100): Promise<CoreRoutingProfile[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (search) query.set("search", search);
    if (afterProfileId) query.set("afterProfileId", afterProfileId);
    query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreRoutingProfile>(`${coreAdminEndpoints.providerRoutingProfiles}${suffix}`);
  },

  upsertProviderRoutingProfile(profileId: string, body: CoreRoutingProfile, tenantId = "", changeReason?: string): Promise<CoreRoutingProfile> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreRoutingProfile>(`${coreAdminEndpoints.providerRoutingProfile(profileId)}${suffix}`, body, changeReason ? { headers: { "X-Change-Reason": changeReason } } : undefined);
  },

  getProviderRoutingProfileVersions(profileId: string, tenantId = ""): Promise<CoreRoutingProfileVersion[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreRoutingProfileVersion>(`${coreAdminEndpoints.providerRoutingProfileVersions(profileId)}${suffix}`);
  },

  recordProviderEligibilityObservation(body: CoreProviderEligibilityObservation, tenantId = ""): Promise<CoreProviderEligibilityObservation> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreProviderEligibilityObservation>(`${coreAdminEndpoints.providerRoutingEligibilityObservations}${suffix}`, body);
  },

  getProviderEligibilityObservations(bindingId?: string, tenantId = "", limit = 100): Promise<CoreProviderEligibilityObservation[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (bindingId) query.set("bindingId", bindingId);
    query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreProviderEligibilityObservation>(`${coreAdminEndpoints.providerRoutingEligibilityObservations}${suffix}`);
  },

  evaluateProviderRoutingPreview(body: CoreProviderRoutingPreviewRequest, tenantId = ""): Promise<CoreProviderRoutingDecision> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreProviderRoutingDecision>(`${coreAdminEndpoints.providerRoutingPreview}${suffix}`, body);
  },

  getProviderRoutingDecisions(capabilityCode?: string, tenantId = "", limit = 100): Promise<CoreProviderRoutingDecision[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (capabilityCode) query.set("capabilityCode", capabilityCode);
    query.set("limit", String(limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreProviderRoutingDecision>(`${coreAdminEndpoints.providerRoutingDecisions}${suffix}`);
  },

  // MRS A0-R6: shadow-only RoutingDecision -> ExecutionAssignment cutover evidence.
  evaluateRoutingAuthorityShadow(planId: string, body: CoreRoutingAuthorityShadowRequest, tenantId = ""): Promise<CoreRoutingAuthorityShadowResult> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreRoutingAuthorityShadowResult>(`${coreAdminEndpoints.routingAuthorityShadowEvaluate(planId)}${suffix}`, body);
  },
  evaluateRebindingAdmission(planId: string, body: CoreRebindingAdmissionRequest, tenantId = ""): Promise<CoreRebindingAdmissionDecision> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreRebindingAdmissionDecision>(`${coreAdminEndpoints.routingAuthorityRebindingAdmission(planId)}${suffix}`, body);
  },
  getRoutingAuthorityTrace(planId: string, tenantId = "", limit = 100): Promise<Record<string, unknown>[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); query.set("limit", String(limit));
    return coreGetList<Record<string, unknown>>(`${coreAdminEndpoints.routingAuthorityTrace(planId)}?${query.toString()}`);
  },
  getFlowRoutingMigrationStates(tenantId = "", limit = 200): Promise<CoreFlowRoutingMigrationState[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); query.set("limit", String(limit));
    return coreGetList<CoreFlowRoutingMigrationState>(`${coreAdminEndpoints.routingAuthorityFlowMigrationStates}?${query.toString()}`);
  },
  setFlowRoutingMigrationState(flowId: string, state: 'LEGACY_AUTHORITATIVE' | 'SHADOW', tenantId = "", changeReason?: string): Promise<CoreFlowRoutingMigrationState> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); query.set("state", state);
    return coreApiPut<CoreFlowRoutingMigrationState>(`${coreAdminEndpoints.routingAuthorityFlowMigrationState(flowId)}?${query.toString()}`, {}, changeReason ? { headers: { "X-Change-Reason": changeReason } } : undefined);
  },

  // MRS A0-R7: per-Flow controlled execution safety. No global cutover endpoint exists.
  setExecutionSafetyFlowAuthority(flowId: string, body: CoreExecutionSafetyActivationRequest, tenantId = ""): Promise<CoreFlowRoutingMigrationState> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreFlowRoutingMigrationState>(`${coreAdminEndpoints.executionSafetyFlowAuthority(flowId)}${suffix}`, body);
  },
  prepareExecutionSafety(planId: string, body: CoreExecutionSafetyPrepareRequest, tenantId = ""): Promise<CoreExecutionSafetyPrepareResult> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPost<CoreExecutionSafetyPrepareResult>(`${coreAdminEndpoints.executionSafetyPrepare(planId)}${suffix}`, body);
  },
  handoffNextExecutionSafetyIntent(tenantId = "", workerId = "a0-r7-admin-worker"): Promise<Record<string, unknown>> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); query.set("workerId", workerId);
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.executionSafetyHandoffNext}?${query.toString()}`, {});
  },
  getExecutionSafetyRecentIntents(tenantId = "", limit = 100): Promise<Record<string, unknown>[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); query.set("limit", String(limit));
    return coreGetList<Record<string, unknown>>(`${coreAdminEndpoints.executionSafetyRecentIntents}?${query.toString()}`);
  },
  getExecutionSafetyTrace(planId: string, tenantId = "", limit = 100): Promise<Record<string, unknown>[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); query.set("limit", String(limit));
    return coreGetList<Record<string, unknown>>(`${coreAdminEndpoints.executionSafetyTrace(planId)}?${query.toString()}`);
  },

  // MRS A0-R8: evidence/isolation/runtime acceptance. This surface cannot expand execution authority.
  getRuntimeAcceptanceSummary(tenantId: string): Promise<CoreRuntimeAcceptanceSummary> {
    const query = new URLSearchParams({ tenantId });
    return coreApiGet<CoreRuntimeAcceptanceSummary>(`${coreAdminEndpoints.runtimeAcceptanceSummary}?${query.toString()}`);
  },
  recordRuntimeAcceptanceRun(tenantId: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    const query = new URLSearchParams({ tenantId });
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.runtimeAcceptanceRuns}?${query.toString()}`, body);
  },

  // Stage 10: A0 Release / Production Foundation. No global cutover API exists.
  getProductionFoundationSummary(tenantId: string): Promise<CoreProductionFoundationSummary> {
    const query = new URLSearchParams({ tenantId });
    return coreApiGet<CoreProductionFoundationSummary>(`${coreAdminEndpoints.productionFoundationSummary}?${query.toString()}`);
  },
  recordProductionFoundationCutoverRun(tenantId: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    const query = new URLSearchParams({ tenantId });
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.productionFoundationCutoverRuns}?${query.toString()}`, body);
  },
  createProductionReleaseCandidate(tenantId: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    const query = new URLSearchParams({ tenantId });
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.productionFoundationCandidates}?${query.toString()}`, body);
  },
  certifyProductionReleaseCandidate(candidateId: string, tenantId: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    const query = new URLSearchParams({ tenantId });
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.productionFoundationCandidateCertify(candidateId)}?${query.toString()}`, body);
  },
  activateProductionReleaseCandidate(candidateId: string, tenantId: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    const query = new URLSearchParams({ tenantId });
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.productionFoundationCandidateActivate(candidateId)}?${query.toString()}`, body);
  },
  revokeProductionReleaseCandidate(candidateId: string, tenantId: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    const query = new URLSearchParams({ tenantId });
    return coreApiPost<Record<string, unknown>>(`${coreAdminEndpoints.productionFoundationCandidateRevoke(candidateId)}?${query.toString()}`, body);
  },
  setProductionFoundationFlowAuthority(flowId: string, tenantId: string, body: CoreExecutionSafetyActivationRequest): Promise<CoreFlowRoutingMigrationState> {
    const query = new URLSearchParams({ tenantId });
    return coreApiPut<CoreFlowRoutingMigrationState>(`${coreAdminEndpoints.productionFoundationFlowAuthority(flowId)}?${query.toString()}`, body);
  },

  // Phase 5: HOW resolution. It consumes a persisted Phase 4 selection and never re-ranks or re-authorizes providers.
  getExecutionAdapters(providerId?: string, status?: string, search?: string, tenantId = "", afterAdapterId?: string, limit = 100): Promise<CoreExecutionAdapterRegistration[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (providerId) query.set("providerId", providerId); if (status) query.set("status", status); if (search) query.set("search", search); if (afterAdapterId) query.set("afterAdapterId", afterAdapterId); query.set("limit", String(limit));
    return coreGetList<CoreExecutionAdapterRegistration>(`${coreAdminEndpoints.executionAdapters}?${query.toString()}`);
  },
  upsertExecutionAdapter(adapterId: string, body: CoreExecutionAdapterRegistration, tenantId = "", changeReason?: string): Promise<CoreExecutionAdapterRegistration> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreExecutionAdapterRegistration>(`${coreAdminEndpoints.executionAdapter(adapterId)}${suffix}`, body, changeReason ? { headers: { "X-Change-Reason": changeReason } } : undefined);
  },
  getExecutionAdapterVersions(adapterId: string, tenantId = ""): Promise<CoreExecutionAdapterVersion[]> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreGetList<CoreExecutionAdapterVersion>(`${coreAdminEndpoints.executionAdapterVersions(adapterId)}${suffix}`); },
  resolveExecutionAdapterPreview(body: CoreExecutionAdapterResolutionRequest, tenantId = ""): Promise<CoreExecutionAdapterResolution> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreApiPost<CoreExecutionAdapterResolution>(`${coreAdminEndpoints.executionAdapterResolvePreview}${suffix}`, body); },
  getExecutionAdapterResolutions(routingDecisionId?: string, tenantId = "", limit = 100): Promise<CoreExecutionAdapterResolution[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (routingDecisionId) query.set("routingDecisionId", routingDecisionId); query.set("limit", String(limit));
    return coreGetList<CoreExecutionAdapterResolution>(`${coreAdminEndpoints.executionAdapterResolutions}?${query.toString()}`);
  },

  // Phase 6: UNKNOWN problem / Semantic Triage. Proposes WHAT only; no provider selection or execution.
  getSemanticTriagePolicies(status?: string, tenantId = "", limit = 100): Promise<CoreTriagePolicy[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (status) query.set("status", status); query.set("limit", String(limit));
    return coreGetList<CoreTriagePolicy>(`${coreAdminEndpoints.semanticTriagePolicies}?${query.toString()}`);
  },
  upsertSemanticTriagePolicy(policyId: string, body: CoreTriagePolicy, tenantId = "", changeReason?: string): Promise<CoreTriagePolicy> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreTriagePolicy>(`${coreAdminEndpoints.semanticTriagePolicy(policyId)}${suffix}`, body, changeReason ? { headers: { "X-Change-Reason": changeReason } } : undefined);
  },
  getSemanticTriagePolicyVersions(policyId: string, tenantId = ""): Promise<CoreTriagePolicyVersion[]> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreGetList<CoreTriagePolicyVersion>(`${coreAdminEndpoints.semanticTriagePolicyVersions(policyId)}${suffix}`); },
  resolveSemanticTriagePreview(body: CoreTriagePreviewRequest, tenantId = ""): Promise<CoreTriageDecision> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreApiPost<CoreTriageDecision>(`${coreAdminEndpoints.semanticTriageResolvePreview}${suffix}`, body); },
  submitSemanticTriageProposal(requestId: string, body: CoreTriageProposal, tenantId = ""): Promise<CoreTriageDecision> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreApiPost<CoreTriageDecision>(`${coreAdminEndpoints.semanticTriageProposal(requestId)}${suffix}`, body); },
  getSemanticTriageRequests(status?: string, tenantId = "", limit = 100): Promise<CoreTriageRequest[]> { const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (status) query.set("status", status); query.set("limit", String(limit)); return coreGetList<CoreTriageRequest>(`${coreAdminEndpoints.semanticTriageRequests}?${query.toString()}`); },
  getSemanticTriageProposals(requestId?: string, tenantId = "", limit = 100): Promise<CoreTriageProposal[]> { const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (requestId) query.set("requestId", requestId); query.set("limit", String(limit)); return coreGetList<CoreTriageProposal>(`${coreAdminEndpoints.semanticTriageProposals}?${query.toString()}`); },
  getSemanticTriageDecisions(requestId?: string, tenantId = "", limit = 100): Promise<CoreTriageDecision[]> { const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (requestId) query.set("requestId", requestId); query.set("limit", String(limit)); return coreGetList<CoreTriageDecision>(`${coreAdminEndpoints.semanticTriageDecisions}?${query.toString()}`); },

  // Phase 7: semantic Execution Plan. Planner proposes Canonical WHAT + dependencies only; no Provider selection or execution.
  getExecutionPlanPolicies(status?: string, tenantId = "", limit = 100): Promise<CoreExecutionPlanPolicy[]> {
    const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (status) query.set("status", status); query.set("limit", String(limit));
    return coreGetList<CoreExecutionPlanPolicy>(`${coreAdminEndpoints.executionPlanPolicies}?${query.toString()}`);
  },
  upsertExecutionPlanPolicy(policyId: string, body: CoreExecutionPlanPolicy, tenantId = "", changeReason?: string): Promise<CoreExecutionPlanPolicy> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreExecutionPlanPolicy>(`${coreAdminEndpoints.executionPlanPolicy(policyId)}${suffix}`, body, changeReason ? { headers: { "X-Change-Reason": changeReason } } : undefined);
  },
  getExecutionPlanPolicyVersions(policyId: string, tenantId = ""): Promise<CoreExecutionPlanPolicyVersion[]> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreGetList<CoreExecutionPlanPolicyVersion>(`${coreAdminEndpoints.executionPlanPolicyVersions(policyId)}${suffix}`); },
  resolveExecutionPlanPreview(body: CoreExecutionPlanPreviewRequest, tenantId = ""): Promise<CoreExecutionPlanDecision> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreApiPost<CoreExecutionPlanDecision>(`${coreAdminEndpoints.executionPlanResolvePreview}${suffix}`, body); },
  submitExecutionPlanProposal(requestId: string, body: CoreExecutionPlanProposal, tenantId = ""): Promise<CoreExecutionPlanDecision> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreApiPost<CoreExecutionPlanDecision>(`${coreAdminEndpoints.executionPlanProposal(requestId)}${suffix}`, body); },
  amendExecutionPlan(planId: string, body: CoreExecutionPlanAmendmentRequest, tenantId = ""): Promise<CoreExecutionPlanDecision> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreApiPost<CoreExecutionPlanDecision>(`${coreAdminEndpoints.executionPlanAmendment(planId)}${suffix}`, body); },
  getExecutionPlans(status?: string, tenantId = "", afterPlanId?: string, limit = 100): Promise<CoreExecutionPlan[]> { const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (status) query.set("status", status); if (afterPlanId) query.set("afterPlanId", afterPlanId); query.set("limit", String(limit)); return coreGetList<CoreExecutionPlan>(`${coreAdminEndpoints.executionPlans}?${query.toString()}`); },
  getExecutionPlan(planId: string, tenantId = ""): Promise<CoreExecutionPlan> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreApiGet<CoreExecutionPlan>(`${coreAdminEndpoints.executionPlan(planId)}${suffix}`); },
  getExecutionPlanRevisions(planId: string, tenantId = ""): Promise<CoreExecutionPlanRevision[]> { const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ""; return coreGetList<CoreExecutionPlanRevision>(`${coreAdminEndpoints.executionPlanRevisions(planId)}${suffix}`); },
  getExecutionPlanRequests(status?: string, tenantId = "", limit = 100): Promise<CoreExecutionPlanRequest[]> { const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (status) query.set("status", status); query.set("limit", String(limit)); return coreGetList<CoreExecutionPlanRequest>(`${coreAdminEndpoints.executionPlanRequests}?${query.toString()}`); },
  getExecutionPlanProposals(requestId?: string, tenantId = "", limit = 100): Promise<CoreExecutionPlanProposal[]> { const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (requestId) query.set("requestId", requestId); query.set("limit", String(limit)); return coreGetList<CoreExecutionPlanProposal>(`${coreAdminEndpoints.executionPlanProposals}?${query.toString()}`); },
  getExecutionPlanDecisions(requestId?: string, tenantId = "", limit = 100): Promise<CoreExecutionPlanDecision[]> { const query = new URLSearchParams(); if (tenantId) query.set("tenantId", tenantId); if (requestId) query.set("requestId", requestId); query.set("limit", String(limit)); return coreGetList<CoreExecutionPlanDecision>(`${coreAdminEndpoints.executionPlanDecisions}?${query.toString()}`); },

  // MRS A0-R5: formal Plan Admission. It authorizes only the Router candidate boundary.
  getPlanAdmissionPolicies(status?: string, tenantId = "", limit = 100): Promise<CorePlanAdmissionPolicy[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CorePlanAdmissionPolicy>(`/admin/plan-admission/policies?${q.toString()}`); },
  upsertPlanAdmissionPolicy(policyId: string, body: CorePlanAdmissionPolicy, tenantId = "", changeReason?: string): Promise<CorePlanAdmissionPolicy> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPut<CorePlanAdmissionPolicy>(`/admin/plan-admission/policies/${encodeURIComponent(policyId)}${suffix}`,body,changeReason?{headers:{"X-Change-Reason":changeReason}}:undefined); },
  admitExecutionPlan(planId: string, revision?: number, tenantId = ""): Promise<CorePlanAdmissionResult> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(revision!=null)q.set("revision",String(revision)); const suffix=q.toString()?`?${q.toString()}`:""; return coreApiPost<CorePlanAdmissionResult>(`/admin/plan-admission/plans/${encodeURIComponent(planId)}/admit${suffix}`,{}); },
  getPlanAdmissionDecisions(planId: string, tenantId = "", limit = 100): Promise<CorePlanAdmissionDecision[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); q.set("limit",String(limit)); return coreGetList<CorePlanAdmissionDecision>(`/admin/plan-admission/plans/${encodeURIComponent(planId)}/decisions?${q.toString()}`); },
  getBindingAuthorizationEnvelopes(planId: string, revision?: number, tenantId = ""): Promise<CoreBindingAuthorizationEnvelope[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(revision!=null)q.set("revision",String(revision)); const suffix=q.toString()?`?${q.toString()}`:""; return coreGetList<CoreBindingAuthorizationEnvelope>(`/admin/plan-admission/plans/${encodeURIComponent(planId)}/envelopes${suffix}`); },
  getPlanAdmissionBindingEvaluations(decisionId: string, stepId?: string, tenantId = "", limit = 200): Promise<CorePlanAdmissionBindingEvaluation[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(stepId)q.set("stepId",stepId); q.set("limit",String(limit)); return coreGetList<CorePlanAdmissionBindingEvaluation>(`/admin/plan-admission/decisions/${encodeURIComponent(decisionId)}/evaluations?${q.toString()}`); },
  revokeBindingAuthorizationEnvelope(envelopeId: string, reason: string, tenantId = ""): Promise<CoreBindingAuthorizationEnvelope> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); const suffix=q.toString()?`?${q.toString()}`:""; return coreApiPost<CoreBindingAuthorizationEnvelope>(`/admin/plan-admission/envelopes/${encodeURIComponent(envelopeId)}/revoke${suffix}`,{}, { headers: { "X-Change-Reason": reason } }); },

  // Phase 8: execute one frozen Plan revision. READY is dependency readiness only; each attempt still requires WHO MAY -> WHO SHOULD -> HOW.
  getPlanExecutionPolicies(status?: string, tenantId = "", limit = 100): Promise<CorePlanExecutionPolicy[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CorePlanExecutionPolicy>(`${coreAdminEndpoints.planExecutionPolicies}?${q.toString()}`); },
  upsertPlanExecutionPolicy(policyId: string, body: CorePlanExecutionPolicy, tenantId = "", changeReason?: string): Promise<CorePlanExecutionPolicy> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPut<CorePlanExecutionPolicy>(`${coreAdminEndpoints.planExecutionPolicy(policyId)}${suffix}`,body,changeReason?{headers:{"X-Change-Reason":changeReason}}:undefined); },
  getPlanExecutionPolicyVersions(policyId: string, tenantId = ""): Promise<CorePlanExecutionPolicyVersion[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CorePlanExecutionPolicyVersion>(`${coreAdminEndpoints.planExecutionPolicyVersions(policyId)}${suffix}`); },
  startPlanExecution(body: CorePlanExecutionStartRequest, tenantId = ""): Promise<CorePlanExecutionRun> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CorePlanExecutionRun>(`${coreAdminEndpoints.planExecutionRuns}${suffix}`,body); },
  getPlanExecutionRuns(status?: string, tenantId = "", afterRunId?: string, limit = 100): Promise<CorePlanExecutionRun[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); if(afterRunId)q.set("afterRunId",afterRunId); q.set("limit",String(limit)); return coreGetList<CorePlanExecutionRun>(`${coreAdminEndpoints.planExecutionRuns}?${q.toString()}`); },
  getPlanExecutionSteps(runId: string, tenantId = ""): Promise<CoreGovernedPlanExecutionStep[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreGovernedPlanExecutionStep>(`${coreAdminEndpoints.planExecutionSteps(runId)}${suffix}`); },
  attachPlanStepAuthority(runId: string, stepId: string, body: CorePlanStepAuthorityRequest, tenantId = ""): Promise<CoreGovernedPlanExecutionStep> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreGovernedPlanExecutionStep>(`${coreAdminEndpoints.planExecutionStepAuthority(runId,stepId)}${suffix}`,body); },
  submitPlanStep(runId: string, stepId: string, body: CorePlanStepSubmitRequest, tenantId = ""): Promise<CorePlanExecutionAttempt> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CorePlanExecutionAttempt>(`${coreAdminEndpoints.planExecutionStepSubmit(runId,stepId)}${suffix}`,body); },
  completePlanStep(runId: string, stepId: string, body: CorePlanStepCompletionRequest, tenantId = ""): Promise<CoreGovernedPlanExecutionStep> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreGovernedPlanExecutionStep>(`${coreAdminEndpoints.planExecutionStepComplete(runId,stepId)}${suffix}`,body); },
  retryPlanStep(runId: string, stepId: string, reason = "Operator retry", tenantId = ""): Promise<CoreGovernedPlanExecutionStep> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreGovernedPlanExecutionStep>(`${coreAdminEndpoints.planExecutionStepRetry(runId,stepId)}${suffix}`,{reason}); },
  cancelPlanExecution(runId: string, reason = "Operator cancellation", tenantId = ""): Promise<CorePlanExecutionRun> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CorePlanExecutionRun>(`${coreAdminEndpoints.planExecutionCancel(runId)}${suffix}`,{reason}); },
  getPlanExecutionAttempts(runId: string, stepId?: string, tenantId = "", limit = 100): Promise<CorePlanExecutionAttempt[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(stepId)q.set("stepId",stepId); q.set("limit",String(limit)); return coreGetList<CorePlanExecutionAttempt>(`${coreAdminEndpoints.planExecutionAttempts(runId)}?${q.toString()}`); },
  getPlanExecutionArtifacts(runId: string, stepId?: string, tenantId = "", limit = 100): Promise<CorePlanExecutionArtifact[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(stepId)q.set("stepId",stepId); q.set("limit",String(limit)); return coreGetList<CorePlanExecutionArtifact>(`${coreAdminEndpoints.planExecutionArtifacts(runId)}?${q.toString()}`); },
  getPlanExecutionEvents(runId: string, tenantId = "", limit = 200): Promise<CorePlanExecutionEvent[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); q.set("limit",String(limit)); return coreGetList<CorePlanExecutionEvent>(`${coreAdminEndpoints.planExecutionEvents(runId)}?${q.toString()}`); },
  sweepPlanExecutionTimeouts(tenantId = ""): Promise<number> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<number>(`${coreAdminEndpoints.planExecutionSweepTimeouts}${suffix}`,{}); },

  // Phase 9: aggregate normalized Artifacts into a canonical enterprise Case. External issue projection is intent-only here.
  getAggregationDefinitions(status?: string, tenantId = "", limit = 100): Promise<CoreAggregationDefinition[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CoreAggregationDefinition>(`${coreAdminEndpoints.caseConvergenceAggregationDefinitions}?${q.toString()}`); },
  upsertAggregationDefinition(aggregationId: string, body: CoreAggregationDefinition, tenantId = "", changeReason?: string): Promise<CoreAggregationDefinition> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPut<CoreAggregationDefinition>(`${coreAdminEndpoints.caseConvergenceAggregationDefinition(aggregationId)}${suffix}`,body,changeReason?{headers:{"X-Change-Reason":changeReason}}:undefined); },
  getAggregationDefinitionVersions(aggregationId: string, tenantId = ""): Promise<CoreAggregationDefinitionVersion[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreAggregationDefinitionVersion>(`${coreAdminEndpoints.caseConvergenceAggregationDefinitionVersions(aggregationId)}${suffix}`); },
  prepareAggregation(body: CoreAggregationPreparationRequest, tenantId = ""): Promise<CoreAggregationPreparationDecision> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreAggregationPreparationDecision>(`${coreAdminEndpoints.caseConvergenceAggregationPrepare}${suffix}`,body); },
  getAggregationPreparationDecisions(runId?: string, tenantId = "", limit = 100): Promise<CoreAggregationPreparationDecision[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(runId)q.set("runId",runId); q.set("limit",String(limit)); return coreGetList<CoreAggregationPreparationDecision>(`${coreAdminEndpoints.caseConvergenceAggregationDecisions}?${q.toString()}`); },
  convergeEnterpriseCase(body: CoreCaseConvergenceRequest, tenantId = ""): Promise<CoreEnterpriseCaseRecord> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreEnterpriseCaseRecord>(`${coreAdminEndpoints.caseConvergenceCases}${suffix}`,body); },
  getEnterpriseCases(status?: string, tenantId = "", afterCaseId?: string, limit = 100): Promise<CoreEnterpriseCaseRecord[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); if(afterCaseId)q.set("afterCaseId",afterCaseId); q.set("limit",String(limit)); return coreGetList<CoreEnterpriseCaseRecord>(`${coreAdminEndpoints.caseConvergenceCases}?${q.toString()}`); },
  getEnterpriseCase(caseId: string, tenantId = ""): Promise<CoreEnterpriseCaseRecord> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiGet<CoreEnterpriseCaseRecord>(`${coreAdminEndpoints.caseConvergenceCase(caseId)}${suffix}`); },
  reviewEnterpriseCase(caseId: string, body: CoreCaseReviewRequest, tenantId = ""): Promise<CoreEnterpriseCaseRecord> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreEnterpriseCaseRecord>(`${coreAdminEndpoints.caseConvergenceCaseReview(caseId)}${suffix}`,body); },
  getEnterpriseCaseArtifacts(caseId: string, tenantId = ""): Promise<CoreCaseArtifactLink[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreCaseArtifactLink>(`${coreAdminEndpoints.caseConvergenceCaseArtifacts(caseId)}${suffix}`); },
  recordCaseIssueProjection(caseId: string, body: CoreCaseIssueProjectionRequest, tenantId = ""): Promise<CoreCaseIssueProjection> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreCaseIssueProjection>(`${coreAdminEndpoints.caseConvergenceCaseProjections(caseId)}${suffix}`,body); },
  getCaseIssueProjections(caseId: string, tenantId = ""): Promise<CoreCaseIssueProjection[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreCaseIssueProjection>(`${coreAdminEndpoints.caseConvergenceCaseProjections(caseId)}${suffix}`); },
  getEnterpriseCaseEvents(caseId: string, tenantId = "", limit = 200): Promise<CoreCaseEvent[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); q.set("limit",String(limit)); return coreGetList<CoreCaseEvent>(`${coreAdminEndpoints.caseConvergenceCaseEvents(caseId)}?${q.toString()}`); },

  // Phase 10: accepted Case outcomes may propose semantic Fast Path patterns. Promotion is human-governed and never bypasses WHO MAY/SHOULD/HOW.
  getLearningPolicies(status?: string, tenantId = "", limit = 100): Promise<CoreLearningPolicy[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CoreLearningPolicy>(`${coreAdminEndpoints.learningFastPathPolicies}?${q.toString()}`); },
  upsertLearningPolicy(policyId: string, body: CoreLearningPolicy, tenantId = "", changeReason?: string): Promise<CoreLearningPolicy> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPut<CoreLearningPolicy>(`${coreAdminEndpoints.learningFastPathPolicy(policyId)}${suffix}`,body,changeReason?{headers:{"X-Change-Reason":changeReason}}:undefined); },
  getLearningPolicyVersions(policyId: string, tenantId = ""): Promise<CoreLearningPolicyVersion[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreLearningPolicyVersion>(`${coreAdminEndpoints.learningFastPathPolicyVersions(policyId)}${suffix}`); },
  recordLearningOutcome(caseId: string, tenantId = ""): Promise<CoreExecutionOutcomeEvidence> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreExecutionOutcomeEvidence>(`${coreAdminEndpoints.learningFastPathOutcomeFromCase(caseId)}${suffix}`,{}); },
  getLearningOutcomes(problemSignature?: string, tenantId = "", limit = 100): Promise<CoreExecutionOutcomeEvidence[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(problemSignature)q.set("problemSignature",problemSignature); q.set("limit",String(limit)); return coreGetList<CoreExecutionOutcomeEvidence>(`${coreAdminEndpoints.learningFastPathOutcomes}?${q.toString()}`); },
  getRoutingPatternCandidates(status?: string, tenantId = "", limit = 100): Promise<CoreRoutingPatternCandidate[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CoreRoutingPatternCandidate>(`${coreAdminEndpoints.learningFastPathCandidates}?${q.toString()}`); },
  promoteRoutingCandidateToShadow(candidateId: string, reason: string, tenantId = ""): Promise<CorePatternPromotionDecision> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CorePatternPromotionDecision>(`${coreAdminEndpoints.learningFastPathCandidateShadow(candidateId)}${suffix}`,{reason}); },
  getRoutingPatterns(status?: string, tenantId = "", limit = 100): Promise<CoreRoutingPattern[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CoreRoutingPattern>(`${coreAdminEndpoints.learningFastPathPatterns}?${q.toString()}`); },
  getRoutingPatternVersions(patternId: string, tenantId = ""): Promise<CoreRoutingPatternVersion[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreRoutingPatternVersion>(`${coreAdminEndpoints.learningFastPathPatternVersions(patternId)}${suffix}`); },
  transitionRoutingPattern(patternId: string, body: CorePatternPromotionRequest, tenantId = ""): Promise<CorePatternPromotionDecision> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CorePatternPromotionDecision>(`${coreAdminEndpoints.learningFastPathPatternTransition(patternId)}${suffix}`,body); },
  refreshRoutingPatternQuality(patternId: string, tenantId = ""): Promise<CorePatternQualitySnapshot> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CorePatternQualitySnapshot>(`${coreAdminEndpoints.learningFastPathPatternQualityRefresh(patternId)}${suffix}`,{}); },
  getRoutingPatternQuality(patternId: string, tenantId = "", limit = 100): Promise<CorePatternQualitySnapshot[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); q.set("limit",String(limit)); return coreGetList<CorePatternQualitySnapshot>(`${coreAdminEndpoints.learningFastPathPatternQuality(patternId)}?${q.toString()}`); },
  evaluateRoutingPatternDrift(patternId: string, tenantId = ""): Promise<CoreDriftObservation> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreDriftObservation>(`${coreAdminEndpoints.learningFastPathPatternDriftEvaluate(patternId)}${suffix}`,{}); },
  getRoutingPatternDrift(patternId: string, tenantId = "", limit = 100): Promise<CoreDriftObservation[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); q.set("limit",String(limit)); return coreGetList<CoreDriftObservation>(`${coreAdminEndpoints.learningFastPathPatternDrift(patternId)}?${q.toString()}`); },
  resolveLearningFastPath(body: CoreFastPathResolutionRequest, tenantId = ""): Promise<CoreFastPathResolutionDecision> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreFastPathResolutionDecision>(`${coreAdminEndpoints.learningFastPathResolvePreview}${suffix}`,body); },
  getPatternPromotionDecisions(tenantId = "", limit = 100): Promise<CorePatternPromotionDecision[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); q.set("limit",String(limit)); return coreGetList<CorePatternPromotionDecision>(`${coreAdminEndpoints.learningFastPathPromotionDecisions}?${q.toString()}`); },

  // Phase 11: runtime enablement is separately certified; SHADOW evidence never affects the real Task.
  getFastPathRuntimePolicies(status?: string, tenantId = "", limit = 100): Promise<CoreFastPathRuntimePolicy[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CoreFastPathRuntimePolicy>(`${coreAdminEndpoints.fastPathRuntimePolicies}?${q.toString()}`); },
  upsertFastPathRuntimePolicy(policyId: string, body: CoreFastPathRuntimePolicy, tenantId = "", changeReason?: string): Promise<CoreFastPathRuntimePolicy> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPut<CoreFastPathRuntimePolicy>(`${coreAdminEndpoints.fastPathRuntimePolicy(policyId)}${suffix}`,body,changeReason?{headers:{"X-Change-Reason":changeReason}}:undefined); },
  getFastPathRuntimePolicyVersions(policyId: string, tenantId = ""): Promise<CoreFastPathRuntimePolicyVersion[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreFastPathRuntimePolicyVersion>(`${coreAdminEndpoints.fastPathRuntimePolicyVersions(policyId)}${suffix}`); },
  compareFastPathShadow(body: CoreFastPathShadowComparisonRequest, tenantId = ""): Promise<CoreFastPathShadowComparison> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreFastPathShadowComparison>(`${coreAdminEndpoints.fastPathRuntimeShadowComparisons}${suffix}`,body); },
  getFastPathShadowComparisons(patternId?: string, tenantId = "", limit = 100): Promise<CoreFastPathShadowComparison[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(patternId)q.set("patternId",patternId); q.set("limit",String(limit)); return coreGetList<CoreFastPathShadowComparison>(`${coreAdminEndpoints.fastPathRuntimeShadowComparisons}?${q.toString()}`); },
  certifyFastPathRuntime(patternId: string, body: CoreFastPathCertificationRequest, tenantId = ""): Promise<CoreFastPathRuntimeCertification> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreFastPathRuntimeCertification>(`${coreAdminEndpoints.fastPathRuntimePatternCertifications(patternId)}${suffix}`,body); },
  getFastPathRuntimeCertifications(patternId?: string, tenantId = "", limit = 100): Promise<CoreFastPathRuntimeCertification[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(patternId)q.set("patternId",patternId); q.set("limit",String(limit)); return coreGetList<CoreFastPathRuntimeCertification>(`${coreAdminEndpoints.fastPathRuntimeCertifications}?${q.toString()}`); },
  getFastPathRuntimeDecisions(taskRef?: string, tenantId = "", limit = 100): Promise<CoreFastPathRuntimeDecision[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(taskRef)q.set("taskRef",taskRef); q.set("limit",String(limit)); return coreGetList<CoreFastPathRuntimeDecision>(`${coreAdminEndpoints.fastPathRuntimeDecisions}?${q.toString()}`); },

  // Phase 12: operational policy/evidence only; Provider/Agent/Pool/transport are resolved server-side.
  getRuntimeStepAuthorityPolicies(status?: string, tenantId = "", limit = 100): Promise<CoreRuntimeStepAuthorityPolicy[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(status)q.set("status",status); q.set("limit",String(limit)); return coreGetList<CoreRuntimeStepAuthorityPolicy>(`${coreAdminEndpoints.runtimeStepAuthorityPolicies}?${q.toString()}`); },
  upsertRuntimeStepAuthorityPolicy(policyId: string, body: CoreRuntimeStepAuthorityPolicy, tenantId = "", changeReason?: string): Promise<CoreRuntimeStepAuthorityPolicy> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPut<CoreRuntimeStepAuthorityPolicy>(`${coreAdminEndpoints.runtimeStepAuthorityPolicy(policyId)}${suffix}`,body,changeReason?{headers:{"X-Change-Reason":changeReason}}:undefined); },
  getRuntimeStepAuthorityPolicyVersions(policyId: string, tenantId = ""): Promise<CoreRuntimeStepAuthorityPolicyVersion[]> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreGetList<CoreRuntimeStepAuthorityPolicyVersion>(`${coreAdminEndpoints.runtimeStepAuthorityPolicyVersions(policyId)}${suffix}`); },
  getRuntimeStepAuthorityDecisions(runId?: string, stepId?: string, tenantId = "", limit = 200): Promise<CoreRuntimeStepAuthorityDecision[]> { const q=new URLSearchParams(); if(tenantId)q.set("tenantId",tenantId); if(runId)q.set("runId",runId); if(stepId)q.set("stepId",stepId); q.set("limit",String(limit)); return coreGetList<CoreRuntimeStepAuthorityDecision>(`${coreAdminEndpoints.runtimeStepAuthorityDecisions}?${q.toString()}`); },
  automatePlanExecutionStepAuthority(runId: string, stepId: string, tenantId = ""): Promise<CoreRuntimeStepAuthorityAutomationResult> { const suffix=tenantId?`?tenantId=${encodeURIComponent(tenantId)}`:""; return coreApiPost<CoreRuntimeStepAuthorityAutomationResult>(`${coreAdminEndpoints.planExecutionStepAutomateAuthority(runId,stepId)}${suffix}`,{}); },

  // Capability catalog APIs manage the canonical capability vocabulary.
  // Catalog entries alone do not grant eligibility: routing qualification requires a Task Required Capability and a Core APPROVED Agent Capability assignment inside the selected Agent Pool.
  getCapabilities(
    status?: string,
    taskDefinitionId?: string,
    tenantId = "",
  ): Promise<CoreAgentCapabilityCatalog[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (status) query.set("status", status);
    if (taskDefinitionId) query.set("taskDefinitionId", taskDefinitionId);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentCapabilityCatalog>(
      `${coreAdminEndpoints.capabilities}${suffix}`,
    );
  },

  // Upserting a catalog entry defines capability vocabulary; it does not add Pool membership or approve an Agent capability assignment by itself.
  upsertCapability(
    capabilityCode: string,
    body: CoreAgentCapabilityCatalog,
    tenantId = "",
  ): Promise<CoreAgentCapabilityCatalog> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAgentCapabilityCatalog>(
      `${coreAdminEndpoints.capability(capabilityCode)}${suffix}`,
      body,
    );
  },

  getAssignmentProfiles(
    agentType?: string,
    active: boolean | null = true,
    tenantId = "",
  ): Promise<CoreAgentAssignmentProfile[]> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (agentType) query.set("agentType", agentType);
    if (active !== null) query.set("active", String(active));
    const params = query.toString() ? `?${query.toString()}` : "";
    return coreGetList<CoreAgentAssignmentProfile>(
      `${coreAdminEndpoints.assignmentProfiles}${params}`,
    );
  },

  upsertAssignmentProfile(
    profileCode: string,
    body: CoreAgentAssignmentProfile,
    tenantId = "",
  ): Promise<CoreAgentAssignmentProfile> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAgentAssignmentProfile>(
      `${coreAdminEndpoints.assignmentProfile(profileCode)}${suffix}`,
      body,
    );
  },

  deleteAssignmentProfile(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAgentAssignmentProfile> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiDelete<CoreAgentAssignmentProfile>(
      `${coreAdminEndpoints.assignmentProfile(profileCode)}${suffix}`,
    );
  },

  getAssignmentProfileRelationshipMap(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfileRelationshipMap> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiGet<CoreAssignmentProfileRelationshipMap>(
      `${coreAdminEndpoints.assignmentProfileRelationshipMap(profileCode)}${suffix}`,
    );
  },

  getAssignmentProfileImpactPreview(
    profileCode: string,
    action: CoreAssignmentProfileImpactAction,
    tenantId = "",
  ): Promise<CoreAssignmentProfileImpactPreview> {
    const query = new URLSearchParams();
    if (tenantId) query.set("tenantId", tenantId);
    if (action) query.set("action", action);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return coreApiGet<CoreAssignmentProfileImpactPreview>(
      `${coreAdminEndpoints.assignmentProfileImpactPreview(profileCode)}${suffix}`,
    );
  },

  getAssignmentProfileCapabilities(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfileCapabilityBinding[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreAssignmentProfileCapabilityBinding>(
      `${coreAdminEndpoints.assignmentProfileCapabilities(profileCode)}${suffix}`,
    );
  },

  upsertAssignmentProfileCapability(
    profileCode: string,
    capabilityCode: string,
    body: CoreAssignmentProfileCapabilityBinding,
    tenantId = "",
  ): Promise<CoreAssignmentProfileCapabilityBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAssignmentProfileCapabilityBinding>(
      `${coreAdminEndpoints.assignmentProfileCapability(profileCode, capabilityCode)}${suffix}`,
      body,
    );
  },

  deleteAssignmentProfileCapability(
    profileCode: string,
    capabilityCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfileCapabilityBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiDelete<CoreAssignmentProfileCapabilityBinding>(
      `${coreAdminEndpoints.assignmentProfileCapability(profileCode, capabilityCode)}${suffix}`,
    );
  },

  getAssignmentProfilePolicies(
    profileCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfilePolicyBinding[]> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreGetList<CoreAssignmentProfilePolicyBinding>(
      `${coreAdminEndpoints.assignmentProfilePolicies(profileCode)}${suffix}`,
    );
  },

  upsertAssignmentProfilePolicy(
    profileCode: string,
    policyCode: string,
    body: CoreAssignmentProfilePolicyBinding,
    tenantId = "",
  ): Promise<CoreAssignmentProfilePolicyBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiPut<CoreAssignmentProfilePolicyBinding>(
      `${coreAdminEndpoints.assignmentProfilePolicy(profileCode, policyCode)}${suffix}`,
      body,
    );
  },

  deleteAssignmentProfilePolicy(
    profileCode: string,
    policyCode: string,
    tenantId = "",
  ): Promise<CoreAssignmentProfilePolicyBinding> {
    const suffix = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : "";
    return coreApiDelete<CoreAssignmentProfilePolicyBinding>(
      `${coreAdminEndpoints.assignmentProfilePolicy(profileCode, policyCode)}${suffix}`,
    );
  },

  // Agent certification runs are not a live Core API in v24. Certification evidence is
  // carried by capability assignment/catalog evidenceRef metadata until a first-class
  // certification domain is implemented with its own Controller and generated R3 policy.

  // Core APPROVED Agent capability assignments are the canonical blocking qualification authority for Task Required Capabilities.
  getAgentCapabilities(agentId: string): Promise<CoreAgentCapabilityAssignment[]> {
    return coreGetList<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilities(agentId),
    );
  },

  // Phase 4-4: Requesting a capability documents Agent ability; it does not add the Agent to a Source Flow / Agent Pool.
  requestAgentCapability(
    agentId: string,
    body: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilities(agentId),
      body,
    );
  },

  removeAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityRemove(agentId, assignmentId),
      body ?? {},
    );
  },

  approveAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityApprove(agentId, assignmentId),
      body ?? {},
    );
  },

  suspendAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilitySuspend(agentId, assignmentId),
      body ?? {},
    );
  },

  resumeAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityResume(agentId, assignmentId),
      body ?? {},
    );
  },

  revokeAgentCapability(
    agentId: string,
    assignmentId: string,
    body?: CoreAgentCapabilityCommand,
  ): Promise<CoreAgentCapabilityAssignment> {
    return coreApiPost<CoreAgentCapabilityAssignment>(
      coreAdminEndpoints.agentCapabilityRevoke(agentId, assignmentId),
      body ?? {},
    );
  },

  getAgentRuntimeFeatureObservations(agentId: string): Promise<CoreAgentRuntimeFeatureObservation[]> {
    return coreGetList<CoreAgentRuntimeFeatureObservation>(coreAdminEndpoints.agentRuntimeFeatureObservations(agentId));
  },

  getAgentRuntimeFeatureTrusts(agentId: string): Promise<CoreAgentRuntimeFeatureTrust[]> {
    return coreGetList<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureTrusts(agentId));
  },

  observeAgentRuntimeFeature(agentId: string, body: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureObserve(agentId), body);
  },

  verifyAgentRuntimeFeature(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureVerify(agentId, trustId), body ?? {});
  },

  trustAgentRuntimeFeature(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureTrust(agentId, trustId), body ?? {});
  },

  suspendAgentRuntimeFeatureTrust(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureSuspend(agentId, trustId), body ?? {});
  },

  resumeAgentRuntimeFeatureTrust(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureResume(agentId, trustId), body ?? {});
  },

  revokeAgentRuntimeFeatureTrust(agentId: string, trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CoreAgentRuntimeFeatureTrust> {
    return coreApiPost<CoreAgentRuntimeFeatureTrust>(coreAdminEndpoints.agentRuntimeFeatureRevoke(agentId, trustId), body ?? {});
  },

  getAgentQualifications(agentId: string): Promise<CoreAgentQualification[]> {
    return coreGetList<CoreAgentQualification>(
      coreAdminEndpoints.agentQualifications(agentId),
    );
  },

  assignAgentQualification(
    agentId: string,
    body: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualifications(agentId),
      body,
    );
  },

  removeAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiDelete<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationRemove(agentId, qualificationId),
      body?.operatorId ? { headers: { 'X-Operator-Id': body.operatorId } } : undefined,
    );
  },

  approveAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationApprove(agentId, qualificationId),
      body ?? {},
    );
  },

  suspendAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationSuspend(agentId, qualificationId),
      body ?? {},
    );
  },

  resumeAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationResume(agentId, qualificationId),
      body ?? {},
    );
  },

  revokeAgentQualification(
    agentId: string,
    qualificationId: string,
    body?: CoreAgentQualificationCommand,
  ): Promise<CoreAgentQualification> {
    return coreApiPost<CoreAgentQualification>(
      coreAdminEndpoints.agentQualificationRevoke(agentId, qualificationId),
      body ?? {},
    );
  },

  getAgentEnterpriseGovernance(
    agentId: string,
  ): Promise<CoreAgentEnterpriseGovernanceSummary> {
    return coreApiGet<CoreAgentEnterpriseGovernanceSummary>(
      coreAdminEndpoints.agentEnterpriseGovernance(agentId),
    );
  },

  // Phase 4-4: Legacy/governance eligibility diagnostic. Current setup uses Source Flow -> Agent Pool -> Pool Member Agent.
  getAgentDispatchEligibility(
    agentId: string,
    taskId?: string,
  ): Promise<CoreAgentDispatchEligibility> {
    const endpoint = taskId
      ? coreAdminEndpoints.agentDispatchEligibilityForTask(agentId, taskId)
      : coreAdminEndpoints.agentDispatchEligibility(agentId);
    return coreApiGet<CoreAgentDispatchEligibility>(endpoint);
  },

  // Phase 4-4: Legacy dispatch requirements diagnostic. Do not use as the Current setup API.

  // Phase 4-4: Legacy eligible-agent diagnostic. Current routing evidence should come from Source Flow / Agent Pool decisions.

  // Compatibility governance eligibility surface. Current operator evidence should be read from the canonical Dispatch decision/simulation projection.

  disconnectAgent(
    agentId: string,
    body?: unknown,
  ): Promise<CoreRuntimeDisconnectResult> {
    return coreApiPost<CoreRuntimeDisconnectResult>(
      coreAdminEndpoints.agentDisconnect(agentId),
      body ?? {},
    );
  },

  disconnectAllAgentSessions(
    agentId: string,
    body?: CoreRuntimeDisconnectAllRequest,
  ): Promise<CoreRuntimeDisconnectResult> {
    return coreApiPost<CoreRuntimeDisconnectResult>(
      coreAdminEndpoints.agentDisconnectAll(agentId),
      body ?? {},
    );
  },

  reconcileBlockedAgentRuntimes(
    body?: CoreRuntimeDisconnectReconcileRequest,
  ): Promise<CoreRuntimeDisconnectReconcileReport> {
    return coreApiPost<CoreRuntimeDisconnectReconcileReport>(
      coreAdminEndpoints.agentRuntimeDisconnectReconcile,
      body ?? {},
    );
  },

  enforceDuplicateRuntimeSecurity(
    agentId: string,
    body?: CoreDuplicateRuntimeSecurityRequest,
  ): Promise<CoreAgentSecurityEnforcementResponse> {
    return coreApiPost<CoreAgentSecurityEnforcementResponse>(
      coreAdminEndpoints.agentDuplicateRuntimeEnforce(agentId),
      body ?? {},
    );
  },

  resolveDuplicateRuntimeSecurity(
    agentId: string,
    body?: CoreDuplicateRuntimeResolveRequest,
  ): Promise<CoreAgentSecurityEnforcementResponse> {
    return coreApiPost<CoreAgentSecurityEnforcementResponse>(
      coreAdminEndpoints.agentDuplicateRuntimeResolve(agentId),
      body ?? {},
    );
  },

  getAgentSecurityEnforcementPolicy(
    agentId: string,
  ): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiGet<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.agentSecurityEnforcementPolicy(agentId),
    );
  },

  updateAgentSecurityEnforcementPolicy(
    agentId: string,
    body: CoreAgentSecurityEnforcementPolicyUpdateRequest,
  ): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiPut<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.agentSecurityEnforcementPolicy(agentId),
      body,
    );
  },

  getDefaultSecurityEnforcementPolicy(): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiGet<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.defaultSecurityEnforcementPolicy,
    );
  },

  updateDefaultSecurityEnforcementPolicy(
    body: CoreAgentSecurityEnforcementPolicyUpdateRequest,
  ): Promise<CoreAgentSecurityEnforcementPolicy> {
    return coreApiPut<CoreAgentSecurityEnforcementPolicy>(
      coreAdminEndpoints.defaultSecurityEnforcementPolicy,
      body,
    );
  },

  getAgentSkillRegistryMetadata(): Promise<CoreAgentSkillRegistryMetadata> {
    return coreApiGet<CoreAgentSkillRegistryMetadata>(
      coreAdminEndpoints.agentSkillsMetadata,
    );
  },

  getAgentSkillDefinitions(
    domain?: string,
    enabledOnly = true,
  ): Promise<CoreAgentSkillDefinition[]> {
    const query = new URLSearchParams();
    if (domain) query.set("domain", domain);
    query.set("enabledOnly", String(enabledOnly));
    const suffix = `?${query.toString()}`;
    return coreGetList<CoreAgentSkillDefinition>(
      `${coreAdminEndpoints.agentSkills}${suffix}`,
    );
  },

  upsertAgentSkillDefinition(
    skillCode: string,
    body: CoreAgentSkillDefinition,
  ): Promise<CoreAgentSkillDefinition> {
    void skillCode;
    void body;
    return legacySkillMutationRetired("upsertAgentSkillDefinition");
  },

  deleteAgentSkillDefinition(
    skillCode: string,
  ): Promise<{ skillCode: string; deleted: boolean }> {
    void skillCode;
    return legacySkillMutationRetired("deleteAgentSkillDefinition");
  },

  getAgentSkillVersions(skillCode: string): Promise<CoreAgentSkillVersion[]> {
    return coreGetList<CoreAgentSkillVersion>(
      coreAdminEndpoints.agentSkillVersions(skillCode),
    );
  },

  createAgentSkillDraftVersion(
    skillCode: string,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    void skillCode;
    void body;
    return legacySkillMutationRetired("createAgentSkillDraftVersion");
  },

  submitAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    void skillCode;
    void version;
    void body;
    return legacySkillMutationRetired("submitAgentSkillVersion");
  },

  approveAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    void skillCode;
    void version;
    void body;
    return legacySkillMutationRetired("approveAgentSkillVersion");
  },

  rejectAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    void skillCode;
    void version;
    void body;
    return legacySkillMutationRetired("rejectAgentSkillVersion");
  },

  publishAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    void skillCode;
    void version;
    void body;
    return legacySkillMutationRetired("publishAgentSkillVersion");
  },

  rollbackAgentSkillVersion(
    skillCode: string,
    version: number,
    body: CoreAgentSkillWorkflowCommand,
  ): Promise<CoreAgentSkillWorkflowResult> {
    void skillCode;
    void version;
    void body;
    return legacySkillMutationRetired("rollbackAgentSkillVersion");
  },

  getAgentSkillAuditEntries(
    skillCode: string,
    limit = 100,
  ): Promise<CoreAgentSkillAuditEntry[]> {
    return coreGetList<CoreAgentSkillAuditEntry>(
      `${coreAdminEndpoints.agentSkillAudit(skillCode)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getAgentSkillVersionDiff(
    skillCode: string,
    version: number,
    baseVersion?: number,
  ): Promise<CoreAgentSkillDiffResult> {
    const query = baseVersion
      ? `?baseVersion=${encodeURIComponent(String(baseVersion))}`
      : "";
    return coreApiGet<CoreAgentSkillDiffResult>(
      `${coreAdminEndpoints.agentSkillVersionDiff(skillCode, version)}${query}`,
    );
  },

  getAgentSkillVersionImpact(
    skillCode: string,
    version: number,
  ): Promise<CoreAgentSkillImpactAnalysisResult> {
    return coreApiGet<CoreAgentSkillImpactAnalysisResult>(
      coreAdminEndpoints.agentSkillVersionImpact(skillCode, version),
    );
  },

  getAgentSkillApprovalPolicy(
    skillCode: string,
  ): Promise<CoreAgentSkillApprovalPolicy> {
    return coreApiGet<CoreAgentSkillApprovalPolicy>(
      coreAdminEndpoints.agentSkillApprovalPolicy(skillCode),
    );
  },

  updateAgentSkillApprovalPolicy(
    skillCode: string,
    body: CoreAgentSkillApprovalPolicy,
    operatorId?: string,
  ): Promise<CoreAgentSkillApprovalPolicy> {
    void skillCode;
    void body;
    void operatorId;
    return legacySkillMutationRetired("updateAgentSkillApprovalPolicy");
  },

  getFleetSkillDrift(limit = 500): Promise<CoreAgentCapabilityDriftReport> {
    return coreApiGet<CoreAgentCapabilityDriftReport>(
      `${coreAdminEndpoints.agentSkillsDrift}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getAgentSkillDrift(agentId: string): Promise<CoreAgentCapabilityDriftReport> {
    return coreApiGet<CoreAgentCapabilityDriftReport>(
      coreAdminEndpoints.agentSkillDrift(agentId),
    );
  },

  evaluateSkillDriftPolicy(
    body: CoreSkillDriftPolicyEvaluationRequest,
  ): Promise<CoreSkillDriftPolicyEvaluationResponse> {
    void body;
    return legacySkillMutationRetired("evaluateSkillDriftPolicy");
  },

  getAgentSkillDeprecationPlan(
    skillCode: string,
  ): Promise<CoreAgentSkillDeprecationPlan> {
    return coreApiGet<CoreAgentSkillDeprecationPlan>(
      coreAdminEndpoints.agentSkillDeprecationPlan(skillCode),
    );
  },

  updateAgentSkillDeprecationPlan(
    skillCode: string,
    body: CoreAgentSkillDeprecationCommand,
  ): Promise<CoreAgentSkillDeprecationPlan> {
    void skillCode;
    void body;
    return legacySkillMutationRetired("updateAgentSkillDeprecationPlan");
  },

  analyzeAgentSkillDeprecationMigration(
    skillCode: string,
    limit = 500,
  ): Promise<CoreAgentSkillDeprecationMigrationPlan> {
    void skillCode;
    void limit;
    return legacySkillMutationRetired("analyzeAgentSkillDeprecationMigration");
  },

  getAgentSkillDependencyGraph(
    skillCode: string,
    depth = 2,
  ): Promise<CoreAgentSkillDependencyGraph> {
    return coreApiGet<CoreAgentSkillDependencyGraph>(
      `${coreAdminEndpoints.agentSkillDependencyGraph(skillCode)}?depth=${encodeURIComponent(String(depth))}`,
    );
  },

  replaceAgentSkillDependencies(
    skillCode: string,
    body: CoreAgentSkillDependencyCommand,
  ): Promise<CoreAgentSkillDependencyEdge[]> {
    void skillCode;
    void body;
    return legacySkillMutationRetired("replaceAgentSkillDependencies");
  },

  proposeFleetSkillRemediation(
    limit = 500,
  ): Promise<CoreAgentSkillRemediationProposal> {
    void limit;
    return legacySkillMutationRetired("proposeFleetSkillRemediation");
  },

  proposeAgentSkillRemediation(
    agentId: string,
  ): Promise<CoreAgentSkillRemediationProposal> {
    void agentId;
    return legacySkillMutationRetired("proposeAgentSkillRemediation");
  },

  getAgentRemediationProposal(
    agentId: string,
  ): Promise<CoreAgentRemediationProposal> {
    return coreApiGet<CoreAgentRemediationProposal>(
      coreAdminEndpoints.agentRemediationProposal(agentId),
    );
  },

  createAgentRemediationProposal(
    agentId: string,
    body?: CoreAgentRemediationProposalRequest,
  ): Promise<CoreAgentRemediationProposal> {
    return coreApiPost<CoreAgentRemediationProposal>(
      coreAdminEndpoints.agentRemediationProposal(agentId),
      body ?? {},
    );
  },

  listAgentRemediationWorkflows(
    agentId: string,
  ): Promise<CoreAgentRemediationWorkflow[]> {
    return coreApiGet<CoreAgentRemediationWorkflow[]>(
      coreAdminEndpoints.agentRemediationWorkflows(agentId),
    );
  },

  createAgentRemediationWorkflow(
    agentId: string,
    body?: CoreAgentRemediationWorkflowCreateRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflows(agentId),
      body ?? {},
    );
  },

  approveAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowApprove(agentId, workflowId),
      body ?? {},
    );
  },

  rejectAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowReject(agentId, workflowId),
      body ?? {},
    );
  },

  cancelAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowCancel(agentId, workflowId),
      body ?? {},
    );
  },

  executeAgentRemediationWorkflow(
    agentId: string,
    workflowId: string,
    body?: CoreAgentRemediationWorkflowDecisionRequest,
  ): Promise<CoreAgentRemediationWorkflow> {
    return coreApiPost<CoreAgentRemediationWorkflow>(
      coreAdminEndpoints.agentRemediationWorkflowExecute(agentId, workflowId),
      body ?? {},
    );
  },

  listStaleAgentRemediationWorkflowLeases(
    limit = 50,
  ): Promise<CoreAgentRemediationStaleLeaseQueue> {
    return coreApiGet<CoreAgentRemediationStaleLeaseQueue>(
      `${coreAdminEndpoints.agentRemediationWorkflowStaleLeases}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  listRecoveredAgentRemediationWorkflowLeases(
    limit = 50,
  ): Promise<CoreAgentRemediationRecoveredLeaseQueue> {
    return coreApiGet<CoreAgentRemediationRecoveredLeaseQueue>(
      `${coreAdminEndpoints.agentRemediationWorkflowRecoveredLeases}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  recoverStaleAgentRemediationWorkflowLeases(
    limit = 100,
    reason = "Manual P11 stale workflow execution lease recovery from Admin UI.",
  ): Promise<CoreAgentRemediationStaleLeaseRecoveryRun> {
    return coreApiPost<CoreAgentRemediationStaleLeaseRecoveryRun>(
      `${coreAdminEndpoints.agentRemediationWorkflowRecoverStaleLeases}?limit=${encodeURIComponent(String(limit))}&reason=${encodeURIComponent(reason)}`,
      {},
    );
  },

  evaluateAgentSkillContract(
    agentId: string,
    body: CoreAgentSkillEvaluationRequest,
  ): Promise<CoreAgentSkillEvaluationResult> {
    void agentId;
    void body;
    return legacySkillMutationRetired("evaluateAgentSkillContract");
  },

  // Phase 4-4: Legacy dispatch-contract resolver. Prefer Source Flow / Agent Pool dry-run for Current setup.

  // Compatibility capability resolver. Current qualification authority is Required Capability plus Core APPROVED Agent Capability inside the selected Agent Pool.

  getAgentApprovedSkills(
    agentId: string,
    enabledOnly = true,
  ): Promise<CoreAgentApprovedSkill[]> {
    return coreGetList<CoreAgentApprovedSkill>(
      `${coreAdminEndpoints.agentApprovedSkills(agentId)}?enabledOnly=${String(enabledOnly)}`,
    );
  },

  replaceAgentApprovedSkills(
    agentId: string,
    body: CoreAgentApprovedSkillSyncCommand,
  ): Promise<CoreAgentApprovedSkillSyncResult> {
    void agentId;
    void body;
    return legacySkillMutationRetired("replaceAgentApprovedSkills");
  },

  syncAgentApprovedSkillsAndCapabilities(
    agentId: string,
    body: CoreAgentApprovedSkillSyncCommand,
  ): Promise<CoreAgentApprovedSkillSyncResult> {
    void agentId;
    void body;
    return legacySkillMutationRetired("syncAgentApprovedSkillsAndCapabilities");
  },

  getAgentRuntimeCapabilityProfile(
    agentId: string,
  ): Promise<CoreAgentRuntimeCapabilityProfile> {
    return coreTenantApiGet<CoreAgentRuntimeCapabilityProfile>(
      coreAdminEndpoints.agentRuntimeCapabilityProfile(agentId),
    );
  },

  getAgentRuntimeDescriptor(
    agentId: string,
  ): Promise<CoreAgentRuntimeDescriptor> {
    return coreTenantApiGet<CoreAgentRuntimeDescriptor>(
      coreAdminEndpoints.agentRuntimeDescriptor(agentId),
    );
  },

  async getAgentRuntimeCapabilities(
    agentId: string,
  ): Promise<CoreAgentRuntimeCapabilityItem[]> {
    return toList(await coreTenantApiGet<PageLike<CoreAgentRuntimeCapabilityItem>>(
      coreAdminEndpoints.agentRuntimeCapabilities(agentId),
    ));
  },

  getAgentRuntimeLoad(agentId: string): Promise<CoreAgentRuntimeLoadSnapshot> {
    return coreTenantApiGet<CoreAgentRuntimeLoadSnapshot>(
      coreAdminEndpoints.agentRuntimeLoad(agentId),
    );
  },

  // Phase 4-4: Legacy dispatch-contract readiness repair surface. Keep for diagnostics; Current setup is Source Flow / Agent Pool.

  // Phase 4-4: Legacy dispatch-contract repair surface. Do not use as the primary Current setup flow.

  getAdapterActions(limit = 100): Promise<CoreAdapterAction[]> {
    return coreGetList<CoreAdapterAction>(
      `${coreAdminEndpoints.adapterActions}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  getAdapterActionsByStatus(
    status: string,
    limit = 100,
  ): Promise<CoreAdapterAction[]> {
    return coreGetList<CoreAdapterAction>(
      `${coreAdminEndpoints.adapterActionsByStatus(status)}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  executeAdapterAction(actionId: string): Promise<CoreAdapterAction> {
    return coreApiPost<CoreAdapterAction>(
      coreAdminEndpoints.adapterActionExecute(actionId),
      {},
    );
  },

  executeRetryAdapterAction(actionId: string): Promise<CoreAdapterAction> {
    return coreApiPost<CoreAdapterAction>(
      coreAdminEndpoints.adapterActionExecuteRetry(actionId),
      {},
    );
  },

  getAdapterActionMetadata(): Promise<CoreAdapterActionMetadata> {
    return coreApiGet<CoreAdapterActionMetadata>(
      coreAdminEndpoints.adapterActionsMetadata,
    );
  },

  executePendingAdapterActions(limit = 50): Promise<Record<string, unknown>> {
    return coreApiPost<Record<string, unknown>>(
      `${coreAdminEndpoints.adapterActionExecutePending}?limit=${encodeURIComponent(String(limit))}`,
      {},
    );
  },

  getAdapterExecutorAudit(
    limit = 100,
  ): Promise<CoreAdapterExecutorAuditRecord[]> {
    return coreGetList<CoreAdapterExecutorAuditRecord>(
      `${coreAdminEndpoints.adapterActionExecutorAudit}?limit=${encodeURIComponent(String(limit))}`,
    );
  },

  retryAdapterAction(
    actionId: string,
    reason = "Retry issue tracking sync from Admin UI",
  ): Promise<CoreAdapterAction> {
    return coreApiPost<CoreAdapterAction>(
      coreAdminEndpoints.adapterActionRetry(actionId),
      { reason, resetAttempts: false },
    );
  },

  getSecurityEvents(): Promise<AgentSecurityEvent[]> {
    return coreGetList<AgentSecurityEvent>(coreAdminEndpoints.securityEvents(requireCoreTenantContext()));
  },

  getAgentSecurityEvents(): Promise<AgentSecurityEvent[]> {
    return coreGetList<AgentSecurityEvent>(
      coreAdminEndpoints.agentSecurityEvents,
    );
  },

  getAgentLatestAuthFailure(agentId: string): Promise<CoreAgentLatestAuthFailureResponse> {
    return coreApiGet<CoreAgentLatestAuthFailureResponse>(
      coreAdminEndpoints.agentLatestAuthFailure(agentId),
    );
  },

  getAgentConnectionRepairActions(agentId: string): Promise<CoreAgentConnectionRepairActionsResponse> {
    return coreApiGet<CoreAgentConnectionRepairActionsResponse>(
      coreAdminEndpoints.agentConnectionRepairActions(agentId),
    );
  },

  executeAgentConnectionRepairAction(
    agentId: string,
    actionCode: string,
    body: CoreAgentConnectionRepairActionRequest,
  ): Promise<CoreAgentConnectionRepairActionResult> {
    return coreApiPost<CoreAgentConnectionRepairActionResult>(
      coreAdminEndpoints.agentConnectionRepairActionExecute(agentId, actionCode),
      body,
    );
  },

  getRecoveryMetrics(
    windowMinutes = 15,
    limit = 2000,
  ): Promise<CoreRecoveryOperationMetricsSnapshot> {
    const params = `?windowMinutes=${encodeURIComponent(String(windowMinutes))}&limit=${encodeURIComponent(String(limit))}`;
    return coreApiGet<CoreRecoveryOperationMetricsSnapshot>(
      `${coreAdminEndpoints.recoveryMetrics}${params}`,
    );
  },

  getRecoveryRunbook(): Promise<CoreRecoveryOperatorRunbook> {
    return coreApiGet<CoreRecoveryOperatorRunbook>(
      coreAdminEndpoints.recoveryRunbook,
    );
  },

  getRecoveryApprovalRequests(
    status = "PENDING",
    limit = 100,
  ): Promise<CoreRecoveryApprovalRequest[]> {
    const params = `?status=${encodeURIComponent(status)}&limit=${encodeURIComponent(String(limit))}`;
    return coreGetList<CoreRecoveryApprovalRequest>(
      `${coreAdminEndpoints.recoveryApprovalRequests}${params}`,
    );
  },

  approveRecoveryApprovalRequest(
    approvalId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryApprovalApprove(approvalId),
      body ?? {},
    );
  },

  rejectRecoveryApprovalRequest(
    approvalId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryApprovalReject(approvalId),
      body ?? {},
    );
  },

  cancelRecoveryApprovalRequest(
    approvalId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryApprovalCancel(approvalId),
      body ?? {},
    );
  },

  clearRuntimeBackoff(
    agentId: string,
    body?: CoreRecoveryGovernanceActionRequest,
  ): Promise<CoreRecoveryGovernanceActionResult> {
    return coreApiPost<CoreRecoveryGovernanceActionResult>(
      coreAdminEndpoints.recoveryClearRuntimeBackoff(agentId),
      body ?? {},
    );
  },

} as const;
