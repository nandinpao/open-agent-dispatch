package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.capability.CapabilityDelegationPersistencePort.ExistingDelegation;
import com.opensocket.aievent.core.capability.CapabilityDelegationRuntimeQueryPort.AssignmentDispatchReference;
import com.opensocket.aievent.core.capability.CapabilityDelegationRuntimeQueryPort.ProviderCandidate;
import com.opensocket.aievent.core.capability.CapabilityDelegationRuntimeQueryPort.ProviderMetrics;
import com.opensocket.aievent.core.capability.CapabilityDelegationRuntimeQueryPort.RequesterContext;
import com.opensocket.aievent.core.capability.CapabilityDelegationRuntimeQueryPort.RuntimePolicySnapshot;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Stage 6 canonical capability-delegation workflow authority.
 *
 * <p>This service owns the WHAT -> WHO CAN -> WHO MAY -> WHO SHOULD -> HOW workflow only.
 * Database layout and SQL are behind runtime query/persistence ports. Execution adapters remain
 * HOW-only and cannot select providers or target Agents.</p>
 */
@Service
public class ManagedCapabilityDelegationRuntimeService {
    private final ObjectMapper json;
    private final TaskOperationalQuery tasks;
    private final PlanChildTaskPort childTasks;
    private final DelegationGovernanceService governance;
    private final ProviderRoutingService routing;
    private final ExecutionAdapterService adapterService;
    private final List<ExecutionAdapterPort> adapters;
    private final CapabilityRuntimeAuthorizationEnvelopeService authorizationEnvelopes;
    private final CapabilityDelegationRuntimeQueryPort runtimeQueries;
    private final CapabilityDelegationPersistencePort persistence;

    public ManagedCapabilityDelegationRuntimeService(ObjectMapper json, TaskOperationalQuery tasks,
            PlanChildTaskPort childTasks, DelegationGovernanceService governance,
            ProviderRoutingService routing, ExecutionAdapterService adapterService,
            List<ExecutionAdapterPort> adapters,
            CapabilityRuntimeAuthorizationEnvelopeService authorizationEnvelopes,
            CapabilityDelegationRuntimeQueryPort runtimeQueries,
            CapabilityDelegationPersistencePort persistence) {
        this.json = json;
        this.tasks = tasks;
        this.childTasks = childTasks;
        this.governance = governance;
        this.routing = routing;
        this.adapterService = adapterService;
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
        this.authorizationEnvelopes = authorizationEnvelopes;
        this.runtimeQueries = runtimeQueries;
        this.persistence = persistence;
    }

    @Transactional
    public CapabilityDelegationReceipt delegate(String tenantId, String parentTaskId, String requestingAgentId,
            String requestingAgentSessionId, String gatewayNodeId, String idempotencyKey, String correlationId,
            CapabilityDelegationRequest request) {
        String tenant = required(tenantId, "tenantId");
        String parentId = required(parentTaskId, "parentTaskId");
        String agentId = required(requestingAgentId, "requestingAgentId");
        String idem = required(idempotencyKey, "idempotencyKey");
        String cid = blank(correlationId) ? UUID.randomUUID().toString() : correlationId.trim();
        if (request == null || request.requiredCapability() == null) throw new IllegalArgumentException("requiredCapability is required");

        CapabilityRequirement requirement = normalizeRequirement(request.requiredCapability());
        runtimeQueries.requireCurrentAssignment(tenant, parentId, agentId, requestingAgentSessionId);
        TaskRecord parent = tasks.findTask(tenant, parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent Task not found: " + parentId));
        String requestDigest = digest(requirement, request);
        ExistingDelegation existing = persistence.findExisting(tenant, parentId, agentId, idem);
        if (existing != null) {
            if (!requestDigest.equals(existing.requestDigest())) throw new IllegalArgumentException("IDEMPOTENCY_KEY_REUSE_CONFLICT");
            return persistence.receipt(tenant, existing.delegationId());
        }

        String delegationId = "cap-delegation-" + UUID.randomUUID();
        String sensitivity = first(trim(request.sensitivityLevel()), first(requirement.dataClassification(), parent.getSensitivityLevel()));
        OffsetDateTime receivedAt = OffsetDateTime.now();
        persistence.createReceived(tenant, delegationId, parentId, agentId, requestingAgentSessionId, gatewayNodeId,
                idem, requestDigest, requirement, required(request.reason(), "reason"), request.inputPayloadRef(),
                sensitivity, cid, receivedAt);
        event(tenant, delegationId, "RECEIVED", null, "RECEIVED",
                List.of("CAPABILITY_REQUEST_ACCEPTED_PROVIDER_NEUTRAL"),
                Map.of("parentTaskId", parentId, "requestingAgentId", agentId));

        RuntimePolicySnapshot policy = runtimeQueries.activePolicy(tenant);
        RequesterContext requester = runtimeQueries.requester(tenant, agentId);
        List<ProviderCandidate> candidates = runtimeQueries.whoCan(tenant, requirement, agentId);
        if (candidates.isEmpty()) return stop(tenant, delegationId, "NO_CANDIDATE", List.of("NO_DISTINCT_APPROVED_PROVIDER"));
        if (candidates.size() > policy.maxCandidates()) return stop(tenant, delegationId, "NO_CANDIDATE", List.of("WHO_CAN_CANDIDATE_SET_EXCEEDS_POLICY"));

        String accessMode = policy.operationModes().getOrDefault(requirement.operation(), policy.defaultAccessMode());
        List<String> passedAuthorizationDecisionIds = new ArrayList<>();
        int waitingApproval = 0;
        for (ProviderCandidate candidate : candidates) {
            ProviderMetrics metrics = runtimeQueries.metrics(tenant, candidate.bindingId());
            DelegationAuthorizationDecision decision = governance.evaluateRuntime(tenant,
                    new DelegationAuthorizationRequest(requirement, candidate.bindingId(), "AGENT",
                            requester.departmentId(), requester.groupIds(), List.of(), accessMode, sensitivity,
                            metrics.estimatedCost(), parent.getHopCount() + 1, 1, metrics.p95LatencyMs(), 0));
            if ("PASS".equals(decision.result())) passedAuthorizationDecisionIds.add(decision.decisionId());
            else if ("WAITING_APPROVAL".equals(decision.result())) waitingApproval++;
        }
        if (passedAuthorizationDecisionIds.isEmpty()) {
            return stop(tenant, delegationId, waitingApproval > 0 ? "WAITING_APPROVAL" : "NO_CANDIDATE",
                    waitingApproval > 0 ? List.of("WHO_MAY_REQUIRES_HUMAN_APPROVAL") : List.of("NO_WHO_MAY_PASS_CANDIDATE"));
        }

        ProviderRoutingDecision routingDecision = routing.evaluateRuntime(tenant,
                new ProviderRoutingPreviewRequest(requirement.capabilityCode(), requirement.operation(),
                        policy.routingProfileId(), passedAuthorizationDecisionIds));
        if (!"SELECTED".equals(routingDecision.result())) {
            return stopWithEvidence(tenant, delegationId, "ROUTING_UNAVAILABLE", routingDecision.reasonCodes(),
                    null, routingDecision.decisionId(), null, null, null);
        }

        ExecutionAdapterResolution adapterResolution = adapterService.resolveRuntime(tenant,
                new ExecutionAdapterResolutionRequest(routingDecision.decisionId()));
        if (!"SELECTED".equals(adapterResolution.result())) {
            return stopWithEvidence(tenant, delegationId, "HOW_UNAVAILABLE", adapterResolution.reasonCodes(), null,
                    routingDecision.decisionId(), adapterResolution.resolutionId(),
                    routingDecision.selectedBindingId(), routingDecision.selectedProviderId());
        }

        String providerType = required(adapterResolution.providerType(), "selectedProviderType");
        String adapterType = required(adapterResolution.adapterType(), "selectedAdapterType");
        boolean managed = "MANAGED_AGENT".equals(providerType) && "MANAGED_AGENT_NETTY".equals(adapterType);
        boolean mcp = "MCP_TOOL".equals(providerType) && "MCP_TOOL".equals(adapterType) && "READ".equals(requirement.operation());
        boolean remote = "REMOTE_A2A_AGENT".equals(providerType) && "REMOTE_A2A".equals(adapterType) && "READ".equals(requirement.operation());
        if (!managed && !mcp && !remote) {
            return stopWithEvidence(tenant, delegationId, "HOW_UNAVAILABLE",
                    merge(List.of("PROVIDER_ADAPTER_NOT_ALLOWED_BY_CURRENT_STAGE"), adapterResolution.reasonCodes()),
                    null, routingDecision.decisionId(), adapterResolution.resolutionId(),
                    routingDecision.selectedBindingId(), routingDecision.selectedProviderId());
        }

        String authorizationDecisionId = selectedAuthorization(routingDecision, passedAuthorizationDecisionIds);
        String executionKind = managed ? "MANAGED_AGENT" : mcp ? "MCP_READ" : "REMOTE_A2A_READ";
        persistence.markAuthorized(tenant, delegationId, authorizationDecisionId, routingDecision.decisionId(),
                adapterResolution.resolutionId(), routingDecision.selectedBindingId(), routingDecision.selectedProviderId(),
                providerType, executionKind, OffsetDateTime.now());
        event(tenant, delegationId, "AUTHORIZED", "RECEIVED", "AUTHORIZED",
                List.of("WHO_CAN_SERVER_SIDE", "WHO_MAY_PASS", "WHO_SHOULD_SELECTED", "HOW_" + adapterType),
                Map.of("bindingId", routingDecision.selectedBindingId(), "providerId", routingDecision.selectedProviderId(),
                        "providerType", providerType));

        PlanChildTaskReference child = childTasks.createChildTask(new PlanChildTaskRequest(tenant, delegationId,
                "delegated-capability", 1, requirement, List.of(), parentId, agentId));
        if (child == null || !child.authoritative() || blank(child.taskId())) {
            return stop(tenant, delegationId, "FAILED", List.of("AUTHORITATIVE_CHILD_TASK_REQUIRED"));
        }
        persistence.markChildCreated(tenant, delegationId, child.taskId(), OffsetDateTime.now());
        event(tenant, delegationId, "CHILD_TASK_CREATED", "AUTHORIZED", "CHILD_CREATED",
                List.of("AUTHORITATIVE_CHILD_TASK_CREATED"), Map.of("childTaskId", child.taskId()));

        ExecutionAdapterRegistration registration = adapterService.findAdapter(tenant, adapterResolution.adapterId())
                .orElseThrow(() -> new IllegalArgumentException("Selected adapter registration not found"));
        ExecutionAdapterPort executionAdapter = adapters.stream()
                .filter(candidate -> adapterResolution.adapterType().equals(candidate.adapterType())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("EXECUTION_ADAPTER_RUNTIME_NOT_AVAILABLE:" + adapterResolution.adapterType()));
        Map<String, Object> input = new LinkedHashMap<>(requirement.inputContext());
        input.put("authorizationDecisionId", authorizationDecisionId);
        input.put("routingDecisionId", routingDecision.decisionId());
        input.put("bindingId", routingDecision.selectedBindingId());
        input.put("requestingAgentId", agentId);
        input.put("delegationId", delegationId);
        input.put("executionContextType", "DELEGATION");
        if (!blank(request.inputPayloadRef())) input.put("inputPayloadRef", request.inputPayloadRef().trim());

        ExecutionAdapterResult execution = executionAdapter.submit(registration,
                new ExecutionAdapterCommand(tenant, child.taskId(), adapterResolution.resolutionId(),
                        requirement.capabilityCode(), requirement.operation(), input, List.of(), requirement.deadline()));
        if (!execution.accepted()) {
            return stopWithEvidence(tenant, delegationId, "FAILED", execution.reasonCodes(), authorizationDecisionId,
                    routingDecision.decisionId(), adapterResolution.resolutionId(), routingDecision.selectedBindingId(),
                    routingDecision.selectedProviderId());
        }

        AssignmentDispatchReference assignment = runtimeQueries.currentAssignmentDispatch(tenant, child.taskId());
        authorizationEnvelopes.issue(tenant, delegationId, assignment.assignmentId(),
                routingDecision.selectedBindingId(), authorizationDecisionId, routingDecision.decisionId());
        persistence.markDispatchQueued(tenant, delegationId, assignment.assignmentId(), assignment.dispatchRequestId(),
                execution.reasonCodes(), OffsetDateTime.now());
        Map<String, Object> queuedEvidence = new LinkedHashMap<>();
        queuedEvidence.put("childTaskId", child.taskId());
        queuedEvidence.put("assignmentId", assignment.assignmentId());
        queuedEvidence.put("providerType", providerType);
        queuedEvidence.put("externalExecutionRef", execution.externalExecutionRef());
        if (assignment.dispatchRequestId() != null) queuedEvidence.put("dispatchRequestId", assignment.dispatchRequestId());
        event(tenant, delegationId, "EXECUTION_QUEUED", "CHILD_CREATED", "DISPATCH_QUEUED", execution.reasonCodes(), queuedEvidence);
        return persistence.receipt(tenant, delegationId);
    }

    private CapabilityRequirement normalizeRequirement(CapabilityRequirement requirement) {
        return new CapabilityRequirement(required(requirement.capabilityCode(), "capabilityCode").toLowerCase(Locale.ROOT),
                required(first(requirement.operation(), "EXECUTE"), "operation").toUpperCase(Locale.ROOT),
                requirement.inputContext(), requirement.resourceConstraints(), requirement.dataClassification(),
                requirement.requiredAssurance(), requirement.deadline(), requirement.qualityPreference());
    }

    private String selectedAuthorization(ProviderRoutingDecision decision, List<String> passed) {
        return decision.candidates().stream()
                .filter(candidate -> decision.selectedBindingId().equals(candidate.bindingId())
                        && decision.selectedProviderId().equals(candidate.providerId()))
                .map(ProviderRoutingCandidateScore::authorizationDecisionId).filter(passed::contains).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("SELECTED_PROVIDER_NOT_BACKED_BY_WHO_MAY_PASS"));
    }

    private CapabilityDelegationReceipt stop(String tenantId, String delegationId, String status, List<String> reasons) {
        return stopWithEvidence(tenantId, delegationId, status, reasons, null, null, null, null, null);
    }

    private CapabilityDelegationReceipt stopWithEvidence(String tenantId, String delegationId, String status,
            List<String> reasons, String authorizationDecisionId, String routingDecisionId,
            String adapterResolutionId, String bindingId, String providerId) {
        persistence.stop(tenantId, delegationId, status, reasons, authorizationDecisionId, routingDecisionId,
                adapterResolutionId, bindingId, providerId, OffsetDateTime.now());
        event(tenantId, delegationId, "STOPPED", null, status, reasons, Map.of());
        return persistence.receipt(tenantId, delegationId);
    }

    private void event(String tenantId, String delegationId, String type, String from, String to,
            List<String> reasons, Map<String, Object> evidence) {
        persistence.appendEvent(tenantId, delegationId, type, from, to, reasons, evidence, OffsetDateTime.now());
    }

    private String digest(CapabilityRequirement requirement, CapabilityDelegationRequest request) {
        try {
            Map<String, Object> canonical = new java.util.TreeMap<>();
            canonical.put("capabilityCode", requirement.capabilityCode());
            canonical.put("operation", requirement.operation());
            canonical.put("inputContext", new java.util.TreeMap<>(requirement.inputContext()));
            canonical.put("resourceConstraints", new java.util.TreeMap<>(requirement.resourceConstraints()));
            canonical.put("reason", request.reason());
            canonical.put("inputPayloadRef", request.inputPayloadRef());
            canonical.put("sensitivityLevel", request.sensitivityLevel());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Capability delegation digest failed", ex);
        }
    }

    private static List<String> merge(List<String> first, List<String> second) {
        ArrayList<String> merged = new ArrayList<>(first);
        if (second != null) merged.addAll(second);
        return List.copyOf(merged);
    }
    private static String first(String first, String second) { return !blank(first) ? first : second; }
    private static String required(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private static String trim(String value) { return blank(value) ? null : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
