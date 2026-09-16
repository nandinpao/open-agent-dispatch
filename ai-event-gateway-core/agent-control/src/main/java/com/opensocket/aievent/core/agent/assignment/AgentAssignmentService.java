package com.opensocket.aievent.core.agent.assignment;


import java.time.OffsetDateTime;
import java.time.ZoneOffset;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractChainInspectionRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractChainInspectionResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessCheck;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessResponse;
import com.opensocket.aievent.core.agent.contract.DispatchSourceSystemOption;

/**
 * Phase 11 clean service: this class now exposes only the direct Dispatch Flow support
 * objects that remain part of the standard runtime chain: capability catalog,
 * agent capability assignments, runtime resources, runtime bindings, dispatch policy
 * projections owned by Dispatch Flow, and runtime feature trust observations.
 */
@Service
public class AgentAssignmentService {
    private final AgentAssignmentPersistence persistence;
    private final AgentAdvisoryRecommendationCoordinator advisoryRecommendationCoordinator;

    public AgentAssignmentService(AgentAssignmentRepository repository) {
        this.persistence = new AgentAssignmentPersistence(repository);
        this.advisoryRecommendationCoordinator = new AgentAdvisoryRecommendationCoordinator(this.persistence);
    }

    /**
     * Phase 9E Advanced Selection Strategy contracts.
     *
     * These entries are explicit future-readiness contracts only. They are not
     * registered in SelectionStrategyRegistry.SUPPORTED_STRATEGIES and therefore
     * cannot affect Current Routing, Runtime Eligibility or Assignments.
     */
    public List<AgentAdvancedSelectionStrategyContract> advancedSelectionStrategyContracts() {
        return AgentAdvancedSelectionStrategyCatalog.contracts();
    }


    public List<DispatchPolicy> searchDispatchPolicies(String tenantId, String status, int limit) {
        return persistence.searchDispatchPolicies(defaultTenant(tenantId), normalizeOptional(status), normalizeLimit(limit));
    }

    public List<DispatchPolicyScope> findDispatchPolicyScopes(String tenantId, String policyCode, Boolean active) {
        if (blank(policyCode)) return List.of();
        return persistence.findDispatchPolicyScopes(defaultTenant(tenantId), normalizeCode(policyCode), active);
    }

    public List<DispatchPolicyRequiredCapability> findDispatchPolicyRequiredCapabilities(String tenantId, String policyCode, Boolean blocking) {
        if (blank(policyCode)) return List.of();
        return persistence.findDispatchPolicyRequiredCapabilities(defaultTenant(tenantId), normalizeCode(policyCode), blocking);
    }

    public DispatchPolicy getDispatchPolicy(String tenantId, String policyCode) {
        if (blank(policyCode)) throw new IllegalArgumentException("policyCode is required");
        return persistence.findDispatchPolicyByCode(defaultTenant(tenantId), normalizeCode(policyCode))
                .orElseThrow(() -> new IllegalArgumentException("Dispatch Flow policy projection not found: " + policyCode));
    }

    @Transactional
    public DispatchPolicy upsertDispatchPolicy(DispatchPolicy request) {
        if (request == null) throw new IllegalArgumentException("dispatch policy body is required");
        if (blank(request.getPolicyCode())) throw new IllegalArgumentException("policyCode is required");
        OffsetDateTime now = now();
        String tenantId = defaultTenant(request.getTenantId());
        String policyCode = normalizeCode(request.getPolicyCode());
        DispatchPolicy policy = persistence.findDispatchPolicyByCode(tenantId, policyCode).orElseGet(DispatchPolicy::new);
        if (blank(policy.getPolicyId())) {
            policy.setPolicyId("dispatch-policy-" + UUID.randomUUID());
            policy.setCreatedAt(now);
        }
        policy.setTenantId(tenantId);
        policy.setPolicyCode(policyCode);
        policy.setPolicyName(firstNonBlank(request.getPolicyName(), policyCode));
        policy.setDescription(request.getDescription());
        policy.setOwnerTeam(request.getOwnerTeam());
        policy.setRiskLevel(firstNonBlank(request.getRiskLevel(), "MIDDLE").trim().toUpperCase());
        policy.setStatus(normalizeDispatchPolicyStatus(request.getStatus()));
        policy.setVersion(Math.max(1, request.getVersion() <= 0 ? policy.getVersion() : request.getVersion()));
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setRetiredAt(request.getRetiredAt());
        Map<String, Object> metadata = new LinkedHashMap<>(request.getMetadata());
        metadata.put("dispatchFlowProjection", true);
        policy.setMetadata(metadata);
        policy.setUpdatedAt(now);
        return persistence.saveDispatchPolicy(policy);
    }

    @Transactional
    public DispatchPolicyScope upsertDispatchPolicyScope(String policyCode, DispatchPolicyScope request) {
        DispatchPolicy policy = getDispatchPolicy(request == null ? null : request.getTenantId(), policyCode);
        DispatchPolicyScope body = request == null ? new DispatchPolicyScope() : request;
        OffsetDateTime now = now();
        DispatchPolicyScope scope = new DispatchPolicyScope();
        scope.setTenantId(policy.getTenantId());
        scope.setScopeId(firstNonBlank(body.getScopeId(), "dispatch-policy-scope-" + UUID.randomUUID()));
        scope.setPolicyCode(policy.getPolicyCode());
        scope.setSourceSystem(blank(body.getSourceSystem()) ? null : normalizeCode(body.getSourceSystem()));
        scope.setTaskType(blank(body.getTaskType()) ? null : normalizeCode(body.getTaskType()));
        scope.setTaskDefinitionId(body.getTaskDefinitionId());
        scope.setRiskLevel(blank(body.getRiskLevel()) ? null : body.getRiskLevel().trim().toUpperCase());
        scope.setPriority(body.getPriority());
        scope.setConditionExpr(body.getConditionExpr());
        scope.setActive(body.isActive());
        scope.setPriorityOrder(body.getPriorityOrder() <= 0 ? 100 : body.getPriorityOrder());
        scope.setMetadata(body.getMetadata());
        scope.setCreatedAt(firstNonNull(body.getCreatedAt(), now));
        scope.setUpdatedAt(now);
        return persistence.saveDispatchPolicyScope(scope);
    }

    @Transactional
    public DispatchPolicyRequiredCapability upsertDispatchPolicyRequiredCapability(String policyCode, DispatchPolicyRequiredCapability request) {
        DispatchPolicy policy = getDispatchPolicy(request == null ? null : request.getTenantId(), policyCode);
        if (request == null || blank(request.getCapabilityCode())) throw new IllegalArgumentException("capabilityCode is required");
        String capabilityCode = normalizeCapabilityCode(request.getCapabilityCode());
        AgentCapabilityCatalog capability = persistence.findCanonicalCapabilityByCode(policy.getTenantId(), capabilityCode)
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Required capability must reference an ACTIVE Canonical Capability: " + capabilityCode));
        OffsetDateTime now = now();
        DispatchPolicyRequiredCapability rule = new DispatchPolicyRequiredCapability();
        rule.setTenantId(policy.getTenantId());
        rule.setRuleId(firstNonBlank(request.getRuleId(), "dispatch-policy-capability-" + UUID.randomUUID()));
        rule.setPolicyCode(policy.getPolicyCode());
        rule.setCapabilityCode(capabilityCode);
        rule.setCapabilityName(firstNonBlank(request.getCapabilityName(), capability.getCapabilityName(), capabilityCode));
        rule.setRequiredMode(firstNonBlank(request.getRequiredMode(), "REQUIRED").trim().toUpperCase());
        rule.setMinVersion(request.getMinVersion());
        rule.setConditionExpr(request.getConditionExpr());
        rule.setBlocking(request.isBlocking());
        rule.setPriority(request.getPriority() <= 0 ? 100 : request.getPriority());
        rule.setMetadata(request.getMetadata());
        rule.setCreatedAt(firstNonNull(request.getCreatedAt(), now));
        rule.setUpdatedAt(now);
        return persistence.saveDispatchPolicyRequiredCapability(rule);
    }

    @Transactional
    public DispatchPolicyRequiredRuntimeFeature upsertDispatchPolicyRequiredRuntimeFeature(String policyCode, DispatchPolicyRequiredRuntimeFeature request) {
        DispatchPolicy policy = getDispatchPolicy(request == null ? null : request.getTenantId(), policyCode);
        if (request == null || blank(request.getFeatureCode())) throw new IllegalArgumentException("featureCode is required");
        String featureCode = normalizeCode(request.getFeatureCode());
        RuntimeFeatureCatalog feature = persistence.findRuntimeFeatureByCode(policy.getTenantId(), featureCode)
                .orElseThrow(() -> new IllegalArgumentException("Runtime feature must reference Runtime Feature Catalog: " + featureCode));
        OffsetDateTime now = now();
        DispatchPolicyRequiredRuntimeFeature rule = new DispatchPolicyRequiredRuntimeFeature();
        rule.setTenantId(policy.getTenantId());
        rule.setRuleId(firstNonBlank(request.getRuleId(), "dispatch-policy-runtime-feature-" + UUID.randomUUID()));
        rule.setPolicyCode(policy.getPolicyCode());
        rule.setFeatureCode(featureCode);
        rule.setFeatureName(firstNonBlank(request.getFeatureName(), feature.getFeatureName(), featureCode));
        rule.setTrustStatus(firstNonBlank(request.getTrustStatus(), "TRUSTED").trim().toUpperCase());
        rule.setConditionExpr(request.getConditionExpr());
        rule.setBlocking(request.isBlocking());
        rule.setPriority(request.getPriority() <= 0 ? 100 : request.getPriority());
        rule.setMetadata(request.getMetadata());
        rule.setCreatedAt(firstNonNull(request.getCreatedAt(), now));
        rule.setUpdatedAt(now);
        return persistence.saveDispatchPolicyRequiredRuntimeFeature(rule);
    }

    @Transactional
    public DispatchPolicyQualityRule upsertDispatchPolicyQualityRule(String policyCode, DispatchPolicyQualityRule request) {
        DispatchPolicy policy = getDispatchPolicy(request == null ? null : request.getTenantId(), policyCode);
        DispatchPolicyQualityRule rule = request == null ? new DispatchPolicyQualityRule() : request;
        rule.setTenantId(policy.getTenantId());
        rule.setPolicyCode(policy.getPolicyCode());
        rule.setRuleId(firstNonBlank(rule.getRuleId(), "dispatch-policy-quality-" + UUID.randomUUID()));
        rule.setCreatedAt(firstNonNull(rule.getCreatedAt(), now()));
        rule.setUpdatedAt(now());
        return persistence.saveDispatchPolicyQualityRule(rule);
    }

    @Transactional
    public DispatchPolicyScoringRule upsertDispatchPolicyScoringRule(String policyCode, DispatchPolicyScoringRule request) {
        DispatchPolicy policy = getDispatchPolicy(request == null ? null : request.getTenantId(), policyCode);
        DispatchPolicyScoringRule rule = request == null ? new DispatchPolicyScoringRule() : request;
        rule.setTenantId(policy.getTenantId());
        rule.setPolicyCode(policy.getPolicyCode());
        rule.setRuleId(firstNonBlank(rule.getRuleId(), "dispatch-policy-score-" + UUID.randomUUID()));
        rule.setCreatedAt(firstNonNull(rule.getCreatedAt(), now()));
        rule.setUpdatedAt(now());
        return persistence.saveDispatchPolicyScoringRule(rule);
    }


    /**
     * Phase 9B Agent Quality Observation.
     *
     * These metrics are observation-only evidence for operators. They must not feed
     * Selection Strategy, Runtime Eligibility, capability gate checks, or hidden routing gates.
     */
    public List<AgentQualityMetricsDaily> findAgentQualityDaily(String tenantId, String agentId, int limit) {
        if (blank(agentId)) return List.of();
        return persistence.findAgentQualityMetricsDaily(defaultTenant(tenantId), agentId, normalizeLimit(limit));
    }

    public List<AgentQualityMetricsWindow> findAgentQualityWindows(String tenantId, String agentId, String metricWindow, int limit) {
        if (blank(agentId)) return List.of();
        return persistence.findAgentQualityMetricsWindow(defaultTenant(tenantId), agentId, firstNonBlank(metricWindow, "24h"), normalizeLimit(limit));
    }

    @Transactional
    public AgentQualityMetricsWindow upsertAgentQualityWindow(AgentQualityMetricsWindow request) {
        if (request == null) throw new IllegalArgumentException("agent quality metrics body is required");
        if (blank(request.getAgentId())) throw new IllegalArgumentException("agentId is required");
        OffsetDateTime now = now();
        request.setTenantId(defaultTenant(request.getTenantId()));
        request.setMetricId(firstNonBlank(request.getMetricId(), "agent-quality-window-" + UUID.randomUUID()));
        request.setMetricWindow(firstNonBlank(request.getMetricWindow(), "24h"));
        request.setSource(firstNonBlank(request.getSource(), "QUALITY_OBSERVATION"));
        request.setCalculatedAt(firstNonNull(request.getCalculatedAt(), now));
        request.setCreatedAt(firstNonNull(request.getCreatedAt(), now));
        request.setUpdatedAt(now);
        Map<String, Object> metadata = new LinkedHashMap<>(request.getMetadata());
        metadata.put("qualityObservationVersion", "9B");
        metadata.put("observationOnly", true);
        metadata.put("selectionImpact", "NONE");
        metadata.putIfAbsent("minimumSample", 30);
        metadata.putIfAbsent("decayWindow", "7d");
        metadata.putIfAbsent("responsibilityScope", "UNKNOWN");
        request.setMetadata(metadata);
        return persistence.saveAgentQualityMetricsWindow(request);
    }

    public List<RuntimeQualityMetricsDaily> findRuntimeQualityDaily(String tenantId, String runtimeId, int limit) {
        if (blank(runtimeId)) return List.of();
        return persistence.findRuntimeQualityMetricsDaily(defaultTenant(tenantId), runtimeId, normalizeLimit(limit));
    }

    public List<SupplyProfileQualitySnapshot> searchSupplyProfileQualitySnapshots(String tenantId, String agentId, String runtimeId, String metricWindow, int limit) {
        return persistence.searchSupplyProfileQualitySnapshots(defaultTenant(tenantId), normalizeOptional(agentId), normalizeOptional(runtimeId), firstNonBlank(metricWindow, "24h"), normalizeLimit(limit));
    }

    public SupplyProfileQualitySnapshot getSupplyProfileQualitySnapshot(String tenantId, String profileCode, String metricWindow) {
        if (blank(profileCode)) throw new IllegalArgumentException("profileCode is required");
        return persistence.findSupplyProfileQualitySnapshot(defaultTenant(tenantId), normalizeCode(profileCode), firstNonBlank(metricWindow, "24h"))
                .orElseThrow(() -> new IllegalArgumentException("Supply profile quality snapshot not found: " + profileCode));
    }


    /**
     * Phase 9C Advisory Recommendation.
     *
     * Recommendations are generated from Capability Registry and Agent Quality Observation evidence.
     * They are advisory-only: accepting a recommendation records operator intent and audit evidence,
     * but it must not directly mutate Source Flow, Agent Pool, Pool Member, Runtime Eligibility, or Selection Strategy.
     */
    public List<AgentAdvisoryRecommendation> searchAdvisoryRecommendations(String tenantId,
                                                                           String targetPoolId,
                                                                           String agentId,
                                                                           String status,
                                                                           String evidenceWindow,
                                                                           int limit) {
        return advisoryRecommendationCoordinator.search(tenantId, targetPoolId, agentId, status, evidenceWindow, limit);
    }

    public List<AgentAdvisoryRecommendation> generateAdvisoryRecommendations(String tenantId,
                                                                             String targetPoolId,
                                                                             String agentId,
                                                                             String evidenceWindow,
                                                                             int limit) {
        return advisoryRecommendationCoordinator.generate(tenantId, targetPoolId, agentId, evidenceWindow, limit);
    }

    @Transactional
    public AgentAdvisoryRecommendation acceptAdvisoryRecommendation(String tenantId, String recommendationId, AgentAdvisoryRecommendationDecisionCommand command) {
        return advisoryRecommendationCoordinator.accept(tenantId, recommendationId, command);
    }

    @Transactional
    public AgentAdvisoryRecommendation rejectAdvisoryRecommendation(String tenantId, String recommendationId, AgentAdvisoryRecommendationDecisionCommand command) {
        return advisoryRecommendationCoordinator.reject(tenantId, recommendationId, command);
    }

    /**
     * Phase 9A Capability Registry 2.0 query.
     *
     * This returns reference/search/governance metadata only. The catalog itself is not a routing selector;
     * Current routing uses Source Flow -> Agent Pool -> Task Required Capability -> Runtime Eligibility -> Routing Score.
     */
    @Transactional(readOnly = true)
    public List<AgentCapabilityCatalog> searchCapabilities(String tenantId, String status, String ignoredTaskDefinitionId, int limit) {
        // Stage 2 canonical closure: public/admin capability lookup projects capability_definitions.
        // agent_capability_catalog remains migration storage only and is not an operator authority.
        return persistence.searchCanonicalCapabilities(defaultTenant(tenantId), blank(status) ? null : status.trim().toUpperCase(), normalizeLimit(limit));
    }

    @Transactional
    public AgentCapabilityCatalog upsertCapability(AgentCapabilityCatalog request) {
        if (request == null) throw new IllegalArgumentException("capability body is required");
        if (blank(request.getCapabilityCode())) throw new IllegalArgumentException("capabilityCode is required");
        OffsetDateTime now = now();
        String tenantId = defaultTenant(request.getTenantId());
        String capabilityCode = normalizeCode(request.getCapabilityCode());
        AgentCapabilityCatalog capability = persistence.findCapabilityByCode(tenantId, capabilityCode).orElseGet(AgentCapabilityCatalog::new);
        if (blank(capability.getCapabilityId())) {
            capability.setCapabilityId("capability-" + UUID.randomUUID());
            capability.setCreatedAt(now);
        }
        capability.setTenantId(tenantId);
        capability.setCapabilityCode(capabilityCode);
        capability.setCapabilityName(firstNonBlank(request.getCapabilityName(), capabilityCode));
        capability.setCategory(firstNonBlank(request.getCategory(), "GENERAL"));
        capability.setDescription(request.getDescription());
        capability.setStatus(normalizeCapabilityStatus(request.getStatus()));
        capability.setVersion(Math.max(1, request.getVersion() <= 0 ? capability.getVersion() : request.getVersion()));
        capability.setRequiresApproval(request.isRequiresApproval());
        capability.setRequiresCertification(request.isRequiresCertification());
        capability.setRequiresRuntimeProbe(request.isRequiresRuntimeProbe());
        // Phase 9A: Capability Registry is explicitly reference-only for Current dispatch.
        // Keep legacy is_dispatch_eligible false so operators cannot interpret this as a routing gate.
        capability.setDispatchEligible(false);
        Map<String, Object> metadata = new LinkedHashMap<>(request.getMetadata());
        metadata.put("capabilityRegistryVersion", "2.0");
        metadata.put("referenceOnly", true);
        metadata.put("routingGate", false);
        metadata.putIfAbsent("capabilitySource", firstNonBlank(String.valueOf(metadata.getOrDefault("capabilitySource", "")), "ADMIN_REGISTRY"));
        capability.setMetadata(metadata);
        capability.setUpdatedAt(now);
        return persistence.saveCapabilityCatalog(capability);
    }

    public List<AgentCapabilityAssignment> findAgentCapabilities(String agentId) {
        if (blank(agentId)) return List.of();
        return persistence.findAgentCapabilityAssignmentsByAgent(agentId);
    }

    @Transactional
    public AgentCapabilityAssignment requestAgentCapability(String agentId, AgentCapabilityCommand command) {
        AgentCapabilityCommand request = command == null ? new AgentCapabilityCommand() : command;
        if (blank(agentId)) throw new IllegalArgumentException("agentId is required");
        if (blank(request.getCapabilityCode())) throw new IllegalArgumentException("capabilityCode is required");
        String tenantId = defaultTenant(blank(request.getTenantId()) ? inferAgentTenantId(agentId) : request.getTenantId());
        String capabilityCode = normalizeCapabilityCode(request.getCapabilityCode());
        AgentCapabilityCatalog catalog = persistence.findCanonicalCapabilityByCode(tenantId, capabilityCode)
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("ACTIVE Canonical Capability not found: " + capabilityCode));
        OffsetDateTime now = now();
        AgentCapabilityAssignment assignment = persistence.findAgentCapabilityAssignmentByAgentAndCapability(agentId, capabilityCode).orElseGet(AgentCapabilityAssignment::new);
        if (!blank(assignment.getAssignmentId()) && !capabilityCode.equals(assignment.getCapabilityCode())) {
            // Stage 2 identity closure: an explicitly reassigned legacy-cased row is rewritten to the canonical code.
            persistence.deleteAgentCapabilityAssignment(agentId, assignment.getAssignmentId());
        }
        if (blank(assignment.getAssignmentId())) {
            assignment.setAssignmentId("agent-capability-" + UUID.randomUUID());
            assignment.setCreatedAt(now);
        }
        assignment.setTenantId(tenantId);
        assignment.setAgentId(agentId);
        assignment.setCapabilityCode(capabilityCode);
        assignment.setCapabilityName(firstNonBlank(catalog.getCapabilityName(), capabilityCode));
        assignment.setStatus(catalog.isRequiresApproval() ? AgentCapabilityAssignmentStatus.PENDING_APPROVAL : AgentCapabilityAssignmentStatus.APPROVED);
        assignment.setSource(firstNonBlank(request.getSource(), "ADMIN_DECLARATION"));
        assignment.setEvidenceRef(request.getEvidenceRef());
        assignment.setRequestedBy(firstNonBlank(request.getOperatorId(), "system"));
        assignment.setRequestedAt(now);
        assignment.setReason(firstNonBlank(request.getReason(), "Canonical Capability assigned by an authorized administrator."));
        Map<String, Object> assignmentMetadata = new LinkedHashMap<>(request.getMetadata());
        assignmentMetadata.put("capabilityAuthority", "CAPABILITY_DEFINITIONS");
        assignmentMetadata.put("canonicalCapability", true);
        assignmentMetadata.put("referenceOnly", false);
        assignmentMetadata.put("routingGate", true);
        assignmentMetadata.putIfAbsent("capabilityVersion", String.valueOf(Math.max(1, catalog.getVersion())));
        assignment.setMetadata(assignmentMetadata);
        if (assignment.getStatus() == AgentCapabilityAssignmentStatus.APPROVED) {
            assignment.setApprovedBy(firstNonBlank(request.getOperatorId(), "system"));
            assignment.setApprovedAt(now);
        }
        assignment.setUpdatedAt(now);
        return persistence.saveAgentCapabilityAssignment(assignment);
    }

    @Transactional public AgentCapabilityAssignment approveAgentCapability(String agentId, String assignmentId, AgentCapabilityCommand command) { return transitionCapability(agentId, assignmentId, AgentCapabilityAssignmentStatus.APPROVED, command); }
    @Transactional public AgentCapabilityAssignment suspendAgentCapability(String agentId, String assignmentId, AgentCapabilityCommand command) { return transitionCapability(agentId, assignmentId, AgentCapabilityAssignmentStatus.SUSPENDED, command); }
    @Transactional public AgentCapabilityAssignment resumeAgentCapability(String agentId, String assignmentId, AgentCapabilityCommand command) { return transitionCapability(agentId, assignmentId, AgentCapabilityAssignmentStatus.APPROVED, command); }
    @Transactional public AgentCapabilityAssignment revokeAgentCapability(String agentId, String assignmentId, AgentCapabilityCommand command) { return transitionCapability(agentId, assignmentId, AgentCapabilityAssignmentStatus.REVOKED, command); }

    @Transactional
    public AgentCapabilityAssignment removeAgentCapability(String agentId, String assignmentId, AgentCapabilityCommand command) {
        AgentCapabilityAssignment assignment = loadCapability(agentId, assignmentId);
        persistence.deleteAgentCapabilityAssignment(agentId, assignmentId);
        return assignment;
    }

    public List<RuntimeResource> searchRuntimeResources(String tenantId, String status, String trustStatus, int limit) {
        return persistence.searchRuntimeResources(defaultTenant(tenantId), normalizeOptional(status), normalizeOptional(trustStatus), normalizeLimit(limit));
    }

    public RuntimeResource getRuntimeResource(String tenantId, String runtimeId) {
        if (blank(runtimeId)) throw new IllegalArgumentException("runtimeId is required");
        return persistence.findRuntimeResourceById(defaultTenant(tenantId), runtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Runtime Resource not found: " + runtimeId));
    }

    @Transactional
    public RuntimeResource upsertRuntimeResource(RuntimeResource request) {
        if (request == null) throw new IllegalArgumentException("runtime resource body is required");
        if (blank(request.getRuntimeId()) && blank(request.getRuntimeCode())) throw new IllegalArgumentException("runtimeId or runtimeCode is required");
        OffsetDateTime now = now();
        String tenantId = defaultTenant(request.getTenantId());
        RuntimeResource resource = !blank(request.getRuntimeId())
                ? persistence.findRuntimeResourceById(tenantId, request.getRuntimeId()).orElseGet(RuntimeResource::new)
                : persistence.findRuntimeResourceByCode(tenantId, normalizeCode(request.getRuntimeCode())).orElseGet(RuntimeResource::new);
        if (blank(resource.getRuntimeId())) {
            resource.setRuntimeId(firstNonBlank(request.getRuntimeId(), "runtime-" + UUID.randomUUID()));
            resource.setCreatedAt(now);
        }
        resource.setTenantId(tenantId);
        resource.setRuntimeCode(firstNonBlank(request.getRuntimeCode(), resource.getRuntimeId()));
        resource.setRuntimeName(firstNonBlank(request.getRuntimeName(), resource.getRuntimeCode()));
        resource.setRuntimeType(firstNonBlank(request.getRuntimeType(), "AGENT"));
        resource.setStatus(firstNonBlank(request.getStatus(), "ACTIVE").trim().toUpperCase());
        resource.setTrustStatus(firstNonBlank(request.getTrustStatus(), "TRUSTED").trim().toUpperCase());
        resource.setCapacityLimit(request.getCapacityLimit());
        resource.setMetadata(request.getMetadata());
        resource.setUpdatedAt(now);
        return persistence.saveRuntimeResource(resource);
    }

    public List<AgentRuntimeBinding> findRuntimeBindingsByAgent(String agentId, String status) {
        if (blank(agentId)) return List.of();
        return persistence.findRuntimeBindingsByAgent(agentId, normalizeOptional(status));
    }

    public AgentRuntimeBinding getActiveRuntimeBinding(String agentId) {
        if (blank(agentId)) return null;
        return persistence.findActiveRuntimeBindingByAgent(agentId).orElse(null);
    }

    public Optional<AgentRuntimeBinding> ensureActiveRuntimeBindingForRuntimeObservation(String tenantId, String agentId, String runtimeId, String runtimeCode, String gatewayNodeId) {
        if (blank(agentId) || blank(runtimeId)) return Optional.empty();
        AgentRuntimeBinding request = new AgentRuntimeBinding();
        request.setTenantId(defaultTenant(tenantId));
        request.setAgentId(agentId);
        request.setRuntimeId(runtimeId);
        request.setRuntimeCode(firstNonBlank(runtimeCode, runtimeId));
        request.setBindingStatus("ACTIVE");
        request.setApprovedBy("runtime-observation");
        request.setApprovedAt(now());
        request.setMetadata(Map.of("gatewayNodeId", firstNonBlank(gatewayNodeId, "unknown")));
        return Optional.of(upsertRuntimeBinding(agentId, request));
    }

    @Transactional
    public AgentRuntimeBinding upsertRuntimeBinding(String agentId, AgentRuntimeBinding request) {
        if (blank(agentId)) throw new IllegalArgumentException("agentId is required");
        AgentRuntimeBinding body = request == null ? new AgentRuntimeBinding() : request;
        if (blank(body.getTenantId())) body.setTenantId(inferAgentTenantId(agentId));
        if (blank(body.getRuntimeId())) throw new IllegalArgumentException("runtimeId is required");
        OffsetDateTime now = now();
        String tenantId = defaultTenant(body.getTenantId());
        AgentRuntimeBinding binding = persistence.findActiveRuntimeBindingByTenantAndAgent(tenantId, agentId).orElseGet(AgentRuntimeBinding::new);
        if (blank(binding.getBindingId())) {
            binding.setBindingId(firstNonBlank(body.getBindingId(), "runtime-binding-" + UUID.randomUUID()));
            binding.setCreatedAt(now);
        }
        binding.setTenantId(tenantId);
        binding.setAgentId(agentId);
        binding.setRuntimeId(body.getRuntimeId());
        binding.setRuntimeCode(firstNonBlank(body.getRuntimeCode(), body.getRuntimeId()));
        binding.setBindingStatus(normalizeBindingStatus(body.getBindingStatus()));
        binding.setApprovedBy(firstNonBlank(body.getApprovedBy(), "system"));
        binding.setApprovedAt(firstNonNull(body.getApprovedAt(), now));
        binding.setEffectiveFrom(firstNonNull(body.getEffectiveFrom(), now));
        binding.setExpiresAt(body.getExpiresAt());
        binding.setCapacityLimit(Math.max(0, body.getCapacityLimit()));
        binding.setRegion(body.getRegion());
        binding.setZone(body.getZone());
        binding.setDataScope(firstNonBlank(body.getDataScope(), "STANDARD"));
        binding.setRiskLimit(firstNonBlank(body.getRiskLimit(), "MIDDLE"));
        binding.setMetadata(body.getMetadata());
        binding.setUpdatedAt(now);
        return persistence.saveRuntimeBinding(binding);
    }

    @Transactional
    public AgentRuntimeBinding transitionRuntimeBinding(String agentId, String bindingId, String targetStatus, AgentRuntimeBinding request) {
        if (blank(agentId)) throw new IllegalArgumentException("agentId is required");
        if (blank(bindingId)) throw new IllegalArgumentException("bindingId is required");
        AgentRuntimeBinding binding = persistence.findRuntimeBinding(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("Runtime Binding not found: " + bindingId));
        if (!agentId.equals(binding.getAgentId())) throw new IllegalArgumentException("Runtime Binding does not belong to Agent " + agentId);
        binding.setBindingStatus(normalizeBindingStatus(targetStatus));
        binding.setUpdatedAt(now());
        return persistence.saveRuntimeBinding(binding);
    }

    public List<RuntimeFeatureCatalog> searchRuntimeFeatures(String tenantId, String status, int limit) {
        return persistence.searchRuntimeFeatures(defaultTenant(tenantId), normalizeOptional(status), normalizeLimit(limit));
    }

    @Transactional
    public RuntimeFeatureCatalog upsertRuntimeFeature(RuntimeFeatureCatalog request) {
        if (request == null || blank(request.getFeatureCode())) throw new IllegalArgumentException("featureCode is required");
        OffsetDateTime now = now();
        String tenantId = defaultTenant(request.getTenantId());
        String featureCode = normalizeCode(request.getFeatureCode());
        RuntimeFeatureCatalog feature = persistence.findRuntimeFeatureByCode(tenantId, featureCode).orElseGet(RuntimeFeatureCatalog::new);
        if (blank(feature.getFeatureId())) {
            feature.setFeatureId("runtime-feature-" + UUID.randomUUID());
            feature.setCreatedAt(now);
        }
        feature.setTenantId(tenantId);
        feature.setFeatureCode(featureCode);
        feature.setFeatureName(firstNonBlank(request.getFeatureName(), featureCode));
        feature.setCategory(firstNonBlank(request.getCategory(), "RUNTIME"));
        feature.setDescription(request.getDescription());
        feature.setStatus(firstNonBlank(request.getStatus(), "ACTIVE").trim().toUpperCase());
        feature.setVersion(Math.max(1, request.getVersion() <= 0 ? feature.getVersion() : request.getVersion()));
        feature.setMetadata(request.getMetadata());
        feature.setUpdatedAt(now);
        return persistence.saveRuntimeFeatureCatalog(feature);
    }

    public List<AgentRuntimeFeatureObservation> findRuntimeFeatureObservations(String agentId) { return blank(agentId) ? List.of() : persistence.findRuntimeFeatureObservationsByAgent(agentId); }
    public List<AgentRuntimeFeatureTrust> findRuntimeFeatureTrusts(String agentId) { return blank(agentId) ? List.of() : persistence.findRuntimeFeatureTrustsByAgent(agentId); }

    @Transactional
    public AgentRuntimeFeatureTrust observeRuntimeFeature(String agentId, AgentRuntimeFeatureCommand command) {
        AgentRuntimeFeatureCommand request = command == null ? new AgentRuntimeFeatureCommand() : command;
        if (blank(agentId)) throw new IllegalArgumentException("agentId is required");
        if (blank(request.getFeatureCode())) throw new IllegalArgumentException("featureCode is required");
        OffsetDateTime now = now();
        String tenantId = defaultTenant(request.getTenantId());
        String featureCode = normalizeCode(request.getFeatureCode());
        AgentRuntimeFeatureTrust trust = persistence.findRuntimeFeatureTrustByAgentAndFeature(agentId, featureCode).orElseGet(AgentRuntimeFeatureTrust::new);
        if (blank(trust.getTrustId())) {
            trust.setTrustId("runtime-feature-trust-" + UUID.randomUUID());
            trust.setCreatedAt(now);
        }
        trust.setTenantId(tenantId);
        trust.setAgentId(agentId);
        trust.setFeatureCode(featureCode);
        trust.setFeatureName(featureCode);
        trust.setTrustStatus(AgentRuntimeFeatureTrustStatus.TRUSTED);
        trust.setObservedAt(now);
        trust.setReason(firstNonBlank(request.getReason(), "Runtime feature observed."));
        trust.setMetadata(request.getMetadata());
        trust.setUpdatedAt(now);
        return persistence.saveRuntimeFeatureTrust(trust);
    }

    @Transactional public AgentRuntimeFeatureTrust verifyRuntimeFeature(String agentId, String trustId, AgentRuntimeFeatureCommand command) { return transitionRuntimeFeatureTrust(agentId, trustId, AgentRuntimeFeatureTrustStatus.VERIFIED); }
    @Transactional public AgentRuntimeFeatureTrust trustRuntimeFeature(String agentId, String trustId, AgentRuntimeFeatureCommand command) { return transitionRuntimeFeatureTrust(agentId, trustId, AgentRuntimeFeatureTrustStatus.TRUSTED); }
    @Transactional public AgentRuntimeFeatureTrust suspendRuntimeFeatureTrust(String agentId, String trustId, AgentRuntimeFeatureCommand command) { return transitionRuntimeFeatureTrust(agentId, trustId, AgentRuntimeFeatureTrustStatus.SUSPENDED); }
    @Transactional public AgentRuntimeFeatureTrust resumeRuntimeFeatureTrust(String agentId, String trustId, AgentRuntimeFeatureCommand command) { return transitionRuntimeFeatureTrust(agentId, trustId, AgentRuntimeFeatureTrustStatus.TRUSTED); }
    @Transactional public AgentRuntimeFeatureTrust revokeRuntimeFeatureTrust(String agentId, String trustId, AgentRuntimeFeatureCommand command) { return transitionRuntimeFeatureTrust(agentId, trustId, AgentRuntimeFeatureTrustStatus.REVOKED); }

    public DispatchContractBootstrapResponse bootstrapDispatchContract(DispatchContractBootstrapRequest request) {
        DispatchContractBootstrapResponse response = new DispatchContractBootstrapResponse();
        response.setTenantId(defaultTenant(request == null ? null : request.getTenantId()));
        response.setSourceSystem(request == null ? null : request.getSourceSystem());
        response.setTaskType(request == null ? null : request.getTaskType());
        response.setCreatedOrUpdated(List.of("DISPATCH_FLOW_DIRECT_ONLY"));
        response.setReadiness(dispatchContractReadiness(new DispatchContractReadinessRequest()));
        return response;
    }

    public DispatchContractChainInspectionResponse inspectDispatchContractChain(DispatchContractChainInspectionRequest request) {
        DispatchContractChainInspectionResponse response = new DispatchContractChainInspectionResponse();
        response.setTenantId(defaultTenant(request == null ? null : request.getTenantId()));
        response.setSourceSystem(request == null ? null : request.getSourceSystem());
        response.setTaskType(request == null ? null : request.getTaskType());
        response.setGeneratedAt(now());
        response.setItems(List.of());
        return response;
    }

    public DispatchContractReadinessResponse dispatchContractReadiness(DispatchContractReadinessRequest request) {
        DispatchContractReadinessResponse response = new DispatchContractReadinessResponse();
        response.setTenantId(defaultTenant(request == null ? null : request.getTenantId()));
        response.setSourceSystem(request == null ? null : request.getSourceSystem());
        response.setTaskType(request == null ? null : request.getTaskType());
        response.setRequiredCapabilities(request == null ? List.of() : request.getRequiredCapabilities());
        response.setReady(true);
        response.setGeneratedAt(now());
        DispatchContractReadinessCheck check = new DispatchContractReadinessCheck();
        check.setCode("DISPATCH_FLOW_DIRECT_ONLY");
        check.setStatus("PASS");
        check.setMessage("Standard readiness is evaluated from Dispatch Flow, selected Agent, optional Required Capability, runtime binding, and capacity only.");
        response.setChecks(List.of(check));
        return response;
    }

    public List<DispatchSourceSystemOption> sourceSystemsFromContracts(String tenantId) {
        return List.of();
    }

    private AgentCapabilityAssignment transitionCapability(String agentId, String assignmentId, AgentCapabilityAssignmentStatus status, AgentCapabilityCommand command) {
        AgentCapabilityAssignment assignment = loadCapability(agentId, assignmentId);
        assignment.setStatus(status);
        assignment.setUpdatedAt(now());
        if (status == AgentCapabilityAssignmentStatus.APPROVED) {
            assignment.setApprovedBy(command == null ? "system" : firstNonBlank(command.getOperatorId(), "system"));
            assignment.setApprovedAt(now());
        }
        if (status == AgentCapabilityAssignmentStatus.REVOKED) {
            assignment.setRevokedBy(command == null ? "system" : firstNonBlank(command.getOperatorId(), "system"));
            assignment.setRevokedAt(now());
        }
        if (command != null && !blank(command.getReason())) assignment.setReason(command.getReason());
        return persistence.saveAgentCapabilityAssignment(assignment);
    }

    private AgentCapabilityAssignment loadCapability(String agentId, String assignmentId) {
        if (blank(agentId)) throw new IllegalArgumentException("agentId is required");
        if (blank(assignmentId)) throw new IllegalArgumentException("assignmentId is required");
        AgentCapabilityAssignment assignment = persistence.findAgentCapabilityAssignment(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent capability assignment not found: " + assignmentId));
        if (!agentId.equals(assignment.getAgentId())) throw new IllegalArgumentException("Capability assignment does not belong to Agent " + agentId);
        return assignment;
    }

    private AgentRuntimeFeatureTrust transitionRuntimeFeatureTrust(String agentId, String trustId, AgentRuntimeFeatureTrustStatus target) {
        if (blank(agentId)) throw new IllegalArgumentException("agentId is required");
        if (blank(trustId)) throw new IllegalArgumentException("trustId is required");
        AgentRuntimeFeatureTrust trust = persistence.findRuntimeFeatureTrust(trustId)
                .orElseThrow(() -> new IllegalArgumentException("Runtime feature trust not found: " + trustId));
        if (!agentId.equals(trust.getAgentId())) throw new IllegalArgumentException("Runtime feature trust does not belong to Agent " + agentId);
        trust.setTrustStatus(target);
        trust.setUpdatedAt(now());
        return persistence.saveRuntimeFeatureTrust(trust);
    }

    private String inferAgentTenantId(String agentId) {
        return persistence.findActiveRuntimeBindingByAgent(agentId)
                .map(AgentRuntimeBinding::getTenantId)
                .orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
    }

    private String normalizeDispatchPolicyStatus(String status) { return firstNonBlank(status, "ACTIVE").trim().toUpperCase(); }
    private String normalizeCapabilityStatus(String status) { return firstNonBlank(status, "ACTIVE").trim().toUpperCase(); }
    private String normalizeBindingStatus(String status) {
        String normalized = firstNonBlank(status, "ACTIVE").trim().toUpperCase();
        return switch (normalized) {
            case "ACTIVATE", "ENABLED", "ENABLE", "RESUME", "RESUMED" -> "ACTIVE";
            case "DEACTIVATE", "DISABLE", "DISABLED", "SUSPEND", "SUSPENDED", "PAUSE", "PAUSED" -> "SUSPENDED";
            case "REVOKE", "REVOKED" -> "REVOKED";
            default -> normalized;
        };
    }
    private String normalizeOptional(String value) { return blank(value) ? null : value.trim().toUpperCase(); }
    private String trimOptional(String value) { return blank(value) ? null : value.trim(); }
    private String normalizeCode(String value) { return blank(value) ? null : value.trim().toUpperCase(); }
    private String normalizeCapabilityCode(String value) { return blank(value) ? null : value.trim().toLowerCase(java.util.Locale.ROOT); }
    private int normalizeLimit(int limit) { return limit <= 0 ? 500 : Math.min(limit, 5000); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private String defaultTenant(String tenantId) { if (blank(tenantId)) throw new IllegalArgumentException("tenantId is required"); return tenantId.trim(); }
    private <T> T firstNonNull(T value, T fallback) { return value == null ? fallback : value; }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (!blank(v)) return v; return null; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
