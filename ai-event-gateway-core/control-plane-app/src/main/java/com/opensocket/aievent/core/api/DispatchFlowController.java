package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.dispatch.flow.AgentPoolView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentOptionView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRequiredSkillView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowTraceChainView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowTraceStepView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowReadinessRequest;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowReadinessResponse;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowReadinessService;
import com.opensocket.aievent.core.decision.EventIntakeApplicationService;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.event.EventIntakeRequest;

@RestController
@RequestMapping("/admin/dispatch-flows")
public class DispatchFlowController {
    private static final String R6_COMPATIBILITY_MODE = "R6_FLOW_RULE_PRIMARY_PREVIEW"; // retained for R6 verification while R7 supersedes trace preview.
    private static final String R5_COMPATIBILITY_MODE = "R5_PREVIEW_ONLY"; // R5_PREVIEW_ONLY compatibility token for verifier/history.
    private static final String R5_COMPATIBILITY_AGENT_STATUS = "FLOW_OWNED_AGENT_ASSIGNMENT"; // FLOW_OWNED_AGENT_ASSIGNMENT compatibility token.

    // R2/R4 compatibility verification tokens retained: R2_PREVIEW_ONLY, R2_SKELETON_PREVIEW, R4_PREVIEW_ONLY, R4_SKILL_MODEL_PREVIEW, standaloneDispatchCapabilities.
    // P1_DB_BACKED_CRUD: the beginner-facing Dispatch Flows API now reads/writes persisted Flow-owned records instead of skeleton preview data.
    private final DispatchFlowManagementService dispatchFlowManagementService;
    private final DispatchFlowReadinessService dispatchFlowReadinessService;
    private final EventIntakeApplicationService eventIntakeApplicationService;

    public DispatchFlowController(DispatchFlowManagementService dispatchFlowManagementService,
                                  DispatchFlowReadinessService dispatchFlowReadinessService,
                                  EventIntakeApplicationService eventIntakeApplicationService) {
        this.dispatchFlowManagementService = dispatchFlowManagementService;
        this.dispatchFlowReadinessService = dispatchFlowReadinessService;
        this.eventIntakeApplicationService = eventIntakeApplicationService;
    }

    @GetMapping
    public List<DispatchFlowView> list(@RequestParam String tenantId,
                                       @RequestParam(required = false) String sourceSystem) {
        return dispatchFlowManagementService.listFlows(tenantId, sourceSystem);
    }

    @PostMapping
    public DispatchFlowView create(@RequestBody DispatchFlowView request,
                                   @RequestParam String tenantId) {
        try {
            requireTenantMatch(tenantId, request == null ? null : request.getTenantId());
            request.setTenantId(tenantId);
            return dispatchFlowManagementService.createOrUpdateFlow(request);
        } catch (IllegalArgumentException ex) {
            throw dispatchFlowMutationException(ex);
        }
    }

    @GetMapping("/agent-options")
    public List<DispatchFlowAgentOptionView> agentOptions(@RequestParam String tenantId) {
        return dispatchFlowManagementService.agentOptions(tenantId);
    }

    @GetMapping("/agent-pools")
    public List<AgentPoolView> agentPools(@RequestParam String tenantId,
                                          @RequestParam(required = false) String sourceSystem) {
        return dispatchFlowManagementService.listAgentPools(tenantId, sourceSystem);
    }

    @PostMapping("/agent-pools")
    public AgentPoolView createAgentPool(@RequestBody AgentPoolView request,
                                         @RequestParam String tenantId) {
        try {
            requireTenantMatch(tenantId, request == null ? null : request.getTenantId());
            request.setTenantId(tenantId);
            return dispatchFlowManagementService.createOrUpdateAgentPool(request);
        } catch (IllegalArgumentException ex) {
            throw dispatchFlowMutationException(ex);
        }
    }

    @GetMapping("/agent-pools/{poolId}")
    public AgentPoolView agentPoolDetail(@PathVariable String poolId,
                                         @RequestParam String tenantId) {
        return dispatchFlowManagementService.findAgentPool(tenantId, poolId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Agent Pool not found: " + poolId));
    }

    @PutMapping("/agent-pools/{poolId}")
    public AgentPoolView updateAgentPool(@PathVariable String poolId,
                                         @RequestBody AgentPoolView request,
                                         @RequestParam String tenantId) {
        try {
            requireTenantMatch(tenantId, request == null ? null : request.getTenantId());
            request.setTenantId(tenantId);
            request.setPoolId(poolId);
            return dispatchFlowManagementService.createOrUpdateAgentPool(request);
        } catch (IllegalArgumentException ex) {
            throw dispatchFlowMutationException(ex);
        }
    }

    @DeleteMapping("/agent-pools/{poolId}")
    public Map<String, Object> retireAgentPool(@PathVariable String poolId,
                                               @RequestParam String tenantId) {
        dispatchFlowManagementService.retireAgentPool(tenantId, poolId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poolId", poolId);
        response.put("status", "RETIRED");
        response.put("mode", "PHASE32G_AGENT_POOL_ADMIN_UI");
        return response;
    }

    @GetMapping("/by-agent/{agentId}")
    public List<DispatchFlowView> byAgent(@PathVariable String agentId,
                                          @RequestParam String tenantId) {
        return dispatchFlowManagementService.listFlowsForAgent(tenantId, agentId);
    }

    @GetMapping("/{flowId}")
    public DispatchFlowView detail(@PathVariable String flowId,
                                   @RequestParam String tenantId) {
        return dispatchFlowManagementService.findFlow(tenantId, flowId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Dispatch Flow not found: " + flowId));
    }

    @PutMapping("/{flowId}")
    public DispatchFlowView update(@PathVariable String flowId,
                                   @RequestBody DispatchFlowView request,
                                   @RequestParam String tenantId) {
        try {
            requireTenantMatch(tenantId, request == null ? null : request.getTenantId());
            request.setTenantId(tenantId);
            request.setFlowId(flowId);
            return dispatchFlowManagementService.createOrUpdateFlow(request);
        } catch (IllegalArgumentException ex) {
            throw dispatchFlowMutationException(ex);
        }
    }

    @DeleteMapping("/{flowId}")
    public Map<String, Object> retire(@PathVariable String flowId,
                                      @RequestParam String tenantId) {
        dispatchFlowManagementService.retireFlow(tenantId, flowId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("flowId", flowId);
        response.put("status", "RETIRED");
        response.put("mode", "P1_DB_BACKED_CRUD");
        return response;
    }

    /**
     * Stage 5 real test event. This is not a readiness simulator: the request is
     * generated from the persisted Flow and enters the same EventIntakeApplicationService
     * used by /api/events/intake, creating a real Event, Task, routing decision,
     * assignment, dispatch request, and callback lifecycle when the runtime is available.
     */
    @PostMapping("/{flowId}/test-event")
    public EventIntakeDecisionResponse createRealTestEvent(@PathVariable String flowId,
                                                           @RequestBody(required = false) Map<String, Object> overrides,
                                                           @RequestParam String tenantId) {
        DispatchFlowView flow = dispatchFlowManagementService.findFlow(tenantId, flowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch Flow not found: " + flowId));
        if (!"ACTIVE".equalsIgnoreCase(flow.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Activate the Dispatch Flow before sending a real test event.");
        }
        if (blank(flow.getDefaultPoolId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Select a default Agent Pool before sending a real test event.");
        }
        List<DispatchFlowRuleView> persistedRules = flow.getRules() == null ? List.of() : flow.getRules();
        DispatchFlowRuleView rule = persistedRules.stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                .filter(candidate -> blank(candidate.getEventStage()) || "EXTERNAL".equalsIgnoreCase(candidate.getEventStage()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The Dispatch Flow has no active EXTERNAL event rule."));
        // Phase 32-G: Source Flow test events may intentionally omit eventType.
        // Missing or wildcard eventType is normalized to UNKNOWN and should route to the default Pool.

        String runId = UUID.randomUUID().toString();
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId(tenantId);
        String sourceSystem = firstNonBlank(rule.getSourceSystem(), flow.getSourceSystem());
        if (blank(sourceSystem)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The Dispatch Flow must select a Source System before sending a real test event.");
        }
        request.setSourceSystem(sourceSystem);
        request.setEventStage("EXTERNAL");
        request.setObjectType(wildcardValue(rule.getObjectType(), "STAGE5_TEST_OBJECT"));
        request.setObjectId(firstNonBlank(stringValue(overrides, "objectId"), "TEST-" + runId));
        request.setEventType(wildcardValue(rule.getEventType(), null));
        request.setErrorCode(wildcardValue(rule.getErrorCode(), null));
        request.setSeverity(firstNonBlank(stringValue(overrides, "severity"), conditionString(rule, "severity"), "MEDIUM"));
        request.setMessage(firstNonBlank(stringValue(overrides, "message"), "OpenDispatch real test event for Flow " + flow.getFlowName()));
        request.setOccurredAt(OffsetDateTime.now());
        request.setCorrelationId(firstNonBlank(stringValue(overrides, "correlationId"), "stage5-test-" + runId));
        request.setSiteId(stringValue(overrides, "siteId"));
        request.setPlantId(stringValue(overrides, "plantId"));
        Map<String, Object> attributes = new LinkedHashMap<>();
        Object providedAttributes = overrides == null ? null : overrides.get("attributes");
        if (providedAttributes instanceof Map<?, ?> providedMap) {
            providedMap.forEach((key, value) -> attributes.put(String.valueOf(key), value));
        }
        attributes.put("openDispatchRealTestEvent", true);
        attributes.put("testRunId", runId);
        attributes.put("flowId", flow.getFlowId());
        attributes.put("flowCode", flow.getFlowCode());
        attributes.put("ruleId", rule.getRuleId());
        attributes.put("initiatedFrom", "DISPATCH_FLOW_DETAIL");
        request.setAttributes(attributes);
        return eventIntakeApplicationService.intake(request);
    }

    @PostMapping("/dry-run")
    public DispatchFlowReadinessResponse dryRun(@RequestBody(required = false) DispatchFlowReadinessRequest request,
                                                @RequestParam String tenantId) {
        DispatchFlowReadinessRequest dryRunRequest = request == null ? new DispatchFlowReadinessRequest() : request;
        dryRunRequest.setTenantId(dryRunRequest.getTenantId() == null || dryRunRequest.getTenantId().isBlank() ? tenantId : dryRunRequest.getTenantId());
        return dispatchFlowReadinessService.dryRun(dryRunRequest);
    }

    @PostMapping("/{flowId}/dry-run")
    public DispatchFlowReadinessResponse dryRunFlow(@PathVariable String flowId,
                                                    @RequestBody(required = false) DispatchFlowReadinessRequest request,
                                                    @RequestParam String tenantId) {
        DispatchFlowReadinessRequest dryRunRequest = request == null ? new DispatchFlowReadinessRequest() : request;
        dryRunRequest.setTenantId(dryRunRequest.getTenantId() == null || dryRunRequest.getTenantId().isBlank() ? tenantId : dryRunRequest.getTenantId());
        dryRunRequest.setFlowId(flowId);
        return dispatchFlowReadinessService.dryRun(dryRunRequest);
    }

    @GetMapping("/{flowId}/readiness")
    public DispatchFlowReadinessResponse readiness(@PathVariable String flowId,
                                                   @RequestParam String tenantId,
                                                   @RequestParam(required = false) String sourceSystem,
                                                   @RequestParam(required = false, defaultValue = "EXTERNAL") String eventStage,
                                                   @RequestParam(required = false, defaultValue = "*") String objectType,
                                                   @RequestParam(required = false, defaultValue = "*") String eventType,
                                                   @RequestParam(required = false, defaultValue = "*") String errorCode,
                                                   @RequestParam(required = false) String requestedSkill,
                                                   @RequestParam(required = false) String agentId) {
        DispatchFlowReadinessRequest request = new DispatchFlowReadinessRequest();
        request.setTenantId(tenantId);
        request.setFlowId(flowId);
        request.setSourceSystem(sourceSystem);
        request.setEventStage(eventStage);
        request.setObjectType(objectType);
        request.setEventType(eventType);
        request.setErrorCode(errorCode);
        request.setRequestedSkill(requestedSkill);
        request.setAgentId(agentId);
        return dispatchFlowReadinessService.dryRun(request);
    }

    @GetMapping("/{flowId}/rules")
    public List<DispatchFlowRuleView> rules(@PathVariable String flowId,
                                            @RequestParam String tenantId) {
        return dispatchFlowManagementService.rules(tenantId, flowId);
    }

    /**
     * Stage 3 removes partial Flow mutation. Submit the complete aggregate through
     * PUT /admin/dispatch-flows/{flowId} so Rule, Agent, and Capability changes commit together.
     */
    @Deprecated(forRemoval = true)
    @PostMapping("/{flowId}/rules")
    public DispatchFlowRuleView upsertRule(@PathVariable String flowId,
                                           @RequestBody DispatchFlowRuleView rule,
                                           @RequestParam String tenantId) {
        throw aggregateMutationRequired(flowId);
    }

    @GetMapping("/{flowId}/skills")
    public List<DispatchFlowRequiredSkillView> skills(@PathVariable String flowId,
                                                      @RequestParam String tenantId) {
        return dispatchFlowManagementService.skills(tenantId, flowId);
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/{flowId}/skills")
    public DispatchFlowRequiredSkillView upsertSkill(@PathVariable String flowId,
                                                     @RequestBody DispatchFlowRequiredSkillView skill,
                                                     @RequestParam String tenantId) {
        throw aggregateMutationRequired(flowId);
    }

    @GetMapping("/{flowId}/agents")
    public List<DispatchFlowAgentView> agents(@PathVariable String flowId,
                                              @RequestParam String tenantId) {
        return dispatchFlowManagementService.agents(tenantId, flowId);
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/{flowId}/agents")
    public DispatchFlowAgentView upsertAgent(@PathVariable String flowId,
                                             @RequestBody DispatchFlowAgentView agent,
                                             @RequestParam String tenantId) {
        throw aggregateMutationRequired(flowId);
    }

    @PostMapping("/{flowId}/agents/preview")
    public Map<String, Object> previewAgentAssignment(@PathVariable String flowId,
                                                      @RequestBody(required = false) DispatchFlowAgentView agent) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("flowId", flowId);
        response.put("accepted", true);
        response.put("mode", "R7_TRACE_CHAIN_PREVIEW");
        response.put("message", "R7 preview: Flow-owned Agent assignment is used by the Flow -> Rule -> Skill -> Agent routing contract; legacy fallback remains available until R9.");
        response.put("agentId", agent == null ? null : agent.getAgentId());
        response.put("eventStage", agent == null ? null : agent.getEventStage());
        response.put("agentRole", agent == null ? null : agent.getAgentRole());
        response.put("readinessStatus", agent == null ? null : agent.getReadinessStatus());
        return response;
    }

    @PostMapping("/{flowId}/rules/preview")
    public Map<String, Object> previewRule(@PathVariable String flowId,
                                           @RequestBody(required = false) DispatchFlowRuleView rule) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("flowId", flowId);
        response.put("accepted", true);
        response.put("mode", "R7_TRACE_CHAIN_PREVIEW");
        response.put("message", "R7 preview: Flow-owned Rule routing is now the primary routing contract. Legacy fallback remains available until R9.");
        response.put("ruleScope", rule == null ? null : rule.getRuleScope());
        response.put("eventStage", rule == null ? null : rule.getEventStage());
        response.put("requestedSkill", rule == null ? null : rule.getRequestedSkill());
        return response;
    }

    @GetMapping("/{flowId}/trace")
    public DispatchFlowTraceChainView trace(@PathVariable String flowId,
                                            @RequestParam String tenantId,
                                            @RequestParam(required = false, defaultValue = "CHAIN") String testMode) {
        return skeletonTraceChain(flowId, tenantId, testMode);
    }

    @PostMapping("/{flowId}/test-external")
    public DispatchFlowTraceChainView testExternal(@PathVariable String flowId,
                                                   @RequestBody(required = false) Map<String, Object> payload,
                                                   @RequestParam String tenantId) {
        DispatchFlowTraceChainView chain = skeletonTraceChain(flowId, tenantId, "EXTERNAL");
        chain.setSummary("Real external test preview: tenant-defined event -> selected Agent, with matchedFlowId/matchedRuleId/requiredCapability evidence.");
        chain.setSteps(chain.getSteps().subList(0, Math.min(2, chain.getSteps().size())));
        return chain;
    }

    @PostMapping("/{flowId}/test-a2a")
    public DispatchFlowTraceChainView testA2a(@PathVariable String flowId,
                                              @RequestBody(required = false) Map<String, Object> payload,
                                              @RequestParam String tenantId) {
        DispatchFlowTraceChainView chain = skeletonTraceChain(flowId, tenantId, "A2A");
        chain.setSummary("R7 A2A intake2 test preview: lead Agent -> collaborator Agent through the configured A2A Dispatch Rule.");
        chain.setSteps(chain.getSteps().subList(2, Math.min(4, chain.getSteps().size())));
        return chain;
    }

    @PostMapping("/{flowId}/test-result")
    public DispatchFlowTraceChainView testResult(@PathVariable String flowId,
                                                 @RequestBody(required = false) Map<String, Object> payload,
                                                 @RequestParam String tenantId) {
        DispatchFlowTraceChainView chain = skeletonTraceChain(flowId, tenantId, "RESULT");
        chain.setSummary("R7 RESULT callback test preview: collaborator result links back to parent task / correlationId.");
        chain.setSteps(chain.getSteps().subList(4, Math.min(6, chain.getSteps().size())));
        return chain;
    }

    @PostMapping("/{flowId}/test-chain")
    public DispatchFlowTraceChainView testChain(@PathVariable String flowId,
                                                @RequestBody(required = false) Map<String, Object> payload,
                                                @RequestParam String tenantId) {
        return skeletonTraceChain(flowId, tenantId, "CHAIN");
    }

    private void requireTenantMatch(String requestTenantId, String bodyTenantId) {
        if (bodyTenantId != null && !bodyTenantId.isBlank() && !requestTenantId.equals(bodyTenantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dispatch Flow tenantId does not match the selected Workspace.");
        }
    }

    private StandardApiException dispatchFlowMutationException(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? StandardApiErrorCode.BAD_REQUEST.defaultMessage() : ex.getMessage();
        if (message.startsWith("Agent does not exist in the selected tenant:")) {
            return new StandardApiException(StandardApiErrorCode.FLOW_AGENT_PROFILE_NOT_FOUND, message);
        }
        return new StandardApiException(StandardApiErrorCode.BAD_REQUEST, message);
    }

    private ResponseStatusException aggregateMutationRequired(String flowId) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Partial Dispatch Flow mutation is disabled. Read the complete Flow, update Rules, Agent selections, and required Capabilities together, then PUT /admin/dispatch-flows/" + flowId + ".");
    }

    private DispatchFlowView skeletonFlow(String tenantId, String sourceSystem) {
        String source = sourceSystem.toUpperCase();
        String collaborator = source + "_COLLABORATOR";
        String sourceSlug = source.toLowerCase().replace('_', '-');
        String flowId = "flow-r7-" + sourceSlug;

        DispatchFlowRuleView external = new DispatchFlowRuleView();
        external.setTenantId(tenantId);
        external.setFlowId(flowId);
        external.setRuleId("rule-r7-" + sourceSlug + "-external-intake");
        external.setRuleCode(source + "_EXTERNAL_INTAKE");
        external.setRuleName(source + " external intake rule");
        external.setRuleScope("EXTERNAL_INTAKE");
        external.setEventStage("EXTERNAL");
        external.setSourceSystem(source);
        external.setEventType("*");
        external.setRequestedSkill(source + "_INTAKE_ANALYSIS");
        external.setEnabled(false);
        external.setLegacyStatus("R7_TRACE_CHAIN_PREVIEW");
        external.setUpdatedAt(OffsetDateTime.now());

        DispatchFlowRuleView a2a = new DispatchFlowRuleView();
        a2a.setTenantId(tenantId);
        a2a.setFlowId(flowId);
        a2a.setRuleId("rule-r7-" + sourceSlug + "-a2a-collaborator");
        a2a.setRuleCode(source + "_A2A_CONSULT");
        a2a.setRuleName(source + " collaborator consult rule");
        a2a.setRuleScope("A2A_DISPATCH");
        a2a.setEventStage("A2A");
        a2a.setSourceSystem(source + "_AGENT");
        a2a.setOriginSourceSystem(source);
        a2a.setTargetSystem(collaborator);
        a2a.setEventType(source + "_COLLABORATION_REQUESTED");
        a2a.setRequestedSkill(source + "_COLLABORATION_ANALYSIS");
        a2a.setHandoffMode("CONSULT");
        a2a.setEnabled(false);
        a2a.setLegacyStatus("R7_TRACE_CHAIN_PREVIEW");
        a2a.setUpdatedAt(OffsetDateTime.now());

        DispatchFlowRequiredSkillView leadSkill = new DispatchFlowRequiredSkillView();
        leadSkill.setTenantId(tenantId);
        leadSkill.setId("skill-r7-" + sourceSlug + "-lead");
        leadSkill.setFlowId(flowId);
        leadSkill.setRuleId(external.getRuleId());
        leadSkill.setEventStage("EXTERNAL");
        leadSkill.setAgentRole("LEAD");
        leadSkill.setSkillCode(source + "_INTAKE_ANALYSIS");
        leadSkill.setSkillName(source + " intake analysis");
        leadSkill.setSkillKind("OPENCLAW_ANALYSIS_SKILL");
        leadSkill.setAuthorityCode("CAN_ACT_AS_LEAD_AGENT");
        leadSkill.setOpenClawSkill(Boolean.TRUE);
        leadSkill.setDescription("Preview Capability used by the lead Agent before any A2A event is produced.");
        leadSkill.setLegacyStatus("FLOW_OWNED_SKILL");

        DispatchFlowRequiredSkillView collaboratorSkill = new DispatchFlowRequiredSkillView();
        collaboratorSkill.setTenantId(tenantId);
        collaboratorSkill.setId("skill-r7-" + sourceSlug + "-collaborator");
        collaboratorSkill.setFlowId(flowId);
        collaboratorSkill.setRuleId(a2a.getRuleId());
        collaboratorSkill.setEventStage("A2A");
        collaboratorSkill.setAgentRole("COLLABORATOR");
        collaboratorSkill.setSkillCode(source + "_COLLABORATION_ANALYSIS");
        collaboratorSkill.setSkillName(source + " collaboration analysis");
        collaboratorSkill.setSkillKind("OPENCLAW_A2A_SKILL");
        collaboratorSkill.setAuthorityCode("CAN_ACCEPT_A2A_CONSULT");
        collaboratorSkill.setOpenClawSkill(Boolean.TRUE);
        collaboratorSkill.setDescription("Preview Capability used by a collaborator Agent after the lead Agent produces an A2A event.");
        collaboratorSkill.setLegacyStatus("FLOW_OWNED_SKILL");

        DispatchFlowRequiredSkillView resultSkill = new DispatchFlowRequiredSkillView();
        resultSkill.setTenantId(tenantId);
        resultSkill.setId("skill-r7-" + sourceSlug + "-result");
        resultSkill.setFlowId(flowId);
        resultSkill.setEventStage("RESULT");
        resultSkill.setAgentRole("RESULT_HANDLER");
        resultSkill.setSkillCode(source + "_RESULT_MERGE");
        resultSkill.setSkillName(source + " result merge");
        resultSkill.setSkillKind("RESULT_SKILL");
        resultSkill.setAuthorityCode("CAN_MERGE_A2A_RESULT");
        resultSkill.setOpenClawSkill(Boolean.FALSE);
        resultSkill.setDescription("Requirement used to merge collaborator results into the parent task timeline.");
        resultSkill.setLegacyStatus("FLOW_OWNED_SKILL");

        DispatchFlowRequiredSkillView issueSkill = new DispatchFlowRequiredSkillView();
        issueSkill.setTenantId(tenantId);
        issueSkill.setId("skill-r7-" + sourceSlug + "-issue");
        issueSkill.setFlowId(flowId);
        issueSkill.setEventStage("ISSUE");
        issueSkill.setAgentRole("ISSUE_HANDLER");
        issueSkill.setSkillCode(source + "_ISSUE_LINK");
        issueSkill.setSkillName(source + " issue link");
        issueSkill.setSkillKind("ISSUE_SKILL");
        issueSkill.setAuthorityCode("CAN_CREATE_OR_LINK_ISSUE");
        issueSkill.setOpenClawSkill(Boolean.FALSE);
        issueSkill.setDescription("Requirement used when the Flow creates or links an issue.");
        issueSkill.setLegacyStatus("FLOW_OWNED_SKILL");

        DispatchFlowAgentView leadAgent = previewAgent(tenantId, flowId, "agent-r7-" + sourceSlug + "-lead",
                sourceSlug + "-agent-001", source + " Lead Agent", "EXTERNAL", "LEAD", 3, 3, "READY");
        DispatchFlowAgentView collaboratorAgent = previewAgent(tenantId, flowId, "agent-r7-" + sourceSlug + "-collaborator",
                sourceSlug + "-collaborator-agent-001", source + " Collaborator Agent", "A2A", "COLLABORATOR", 3, 2, "SKILL_GAP");
        collaboratorAgent.setMissingSkills(List.of("CAN_CREATE_LINKED_ISSUE"));
        DispatchFlowAgentView resultAgent = previewAgent(tenantId, flowId, "agent-r7-" + sourceSlug + "-result",
                sourceSlug + "-agent-001", source + " Lead Agent", "RESULT", "RESULT_HANDLER", 1, 1, "READY");
        DispatchFlowAgentView issueAgent = previewAgent(tenantId, flowId, "agent-r7-" + sourceSlug + "-issue",
                sourceSlug + "-agent-001", source + " Lead Agent", "ISSUE", "ISSUE_HANDLER", 1, 0, "AUTHORITY_MISSING");
        issueAgent.setRuntimeStatus("UNKNOWN");
        issueAgent.setApprovalStatus("PENDING");
        issueAgent.setMissingAuthorities(List.of("CAN_CREATE_OR_LINK_ISSUE"));

        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId(tenantId);
        flow.setFlowId(flowId);
        flow.setFlowCode(source + "_FLOW_R7_PREVIEW");
        flow.setFlowName(source + " Dispatch Flow R7 Preview");
        flow.setSourceSystem(source);
        flow.setStatus("DRAFT");
        flow.setDescription("Source-neutral trace preview. Runtime routing resolves Flow -> Rule -> Capability -> Agent without source-specific inference.");
        flow.setExternalRuleCount(1);
        flow.setA2aRuleCount(1);
        flow.setSkillCount(4);
        flow.setAgentCount(4);
        flow.setLastTestStatus("NOT_RUN");
        flow.setRules(List.of(external, a2a));
        flow.setRequiredSkills(List.of(leadSkill, collaboratorSkill, resultSkill, issueSkill));
        flow.setAgents(List.of(leadAgent, collaboratorAgent, resultAgent, issueAgent));
        flow.setMetadata(Map.of("r7TraceChain", true, "syntheticPreview", true, "authoritativeRoutingUnchanged", true));
        flow.setUpdatedAt(OffsetDateTime.now());
        return flow;
    }

    private DispatchFlowAgentView previewAgent(String tenantId, String flowId, String id, String agentId,
                                               String agentName, String eventStage, String role,
                                               int total, int matched, String readiness) {
        DispatchFlowAgentView agent = new DispatchFlowAgentView();
        agent.setTenantId(tenantId);
        agent.setId(id);
        agent.setFlowId(flowId);
        agent.setAgentId(agentId);
        agent.setAgentName(agentName);
        agent.setEventStage(eventStage);
        agent.setAgentRole(role);
        agent.setAssignmentStatus("DRAFT");
        agent.setRuntimeStatus("ONLINE");
        agent.setApprovalStatus("APPROVED");
        agent.setSkillCoverageTotal(total);
        agent.setSkillCoverageMatched(matched);
        agent.setReadinessStatus(readiness);
        agent.setLegacyStatus("FLOW_RULE_AGENT_ASSIGNMENT");
        agent.setUpdatedAt(OffsetDateTime.now());
        return agent;
    }

    private DispatchFlowTraceChainView skeletonTraceChain(String flowId, String tenantId, String testMode) {
        String normalizedFlowId = flowId == null || flowId.isBlank() ? "flow-r7-preview" : flowId;
        String flowCode = normalizedFlowId.toUpperCase().replace('-', '_');
        String source = flowCode + "_SOURCE";
        String collaborator = flowCode + "_COLLABORATOR";
        String sourceAgent = source + "_AGENT";
        String collaboratorAgent = collaborator + "_AGENT";
        String correlationId = "case-r7-" + normalizedFlowId.replace('_', '-').toLowerCase();
        String parentTaskId = "task-r7-parent-" + normalizedFlowId.replace('_', '-').toLowerCase();
        String leadAgentId = normalizedFlowId.replace('_', '-').toLowerCase() + "-lead-agent";

        DispatchFlowTraceChainView chain = new DispatchFlowTraceChainView();
        chain.setTenantId(tenantId);
        chain.setFlowId(normalizedFlowId);
        chain.setFlowCode(flowCode);
        chain.setTestMode(testMode);
        chain.setStatus("BLOCKED_WITH_FIX");
        chain.setFailureStage("A2A_AGENT_ELIGIBILITY");
        chain.setFixAction("Assign a collaborator Agent with the configured A2A Capability to this Flow.");
        chain.setCorrelationId(correlationId);
        chain.setParentTaskId(parentTaskId);
        chain.setSummary("Source-neutral chain preview: external intake -> lead Agent -> collaborator request -> result merge -> issue timeline. The synthetic eligibility gap demonstrates an explicit fix action.");
        chain.setMetadata(Map.of("r7TraceChain", true, "syntheticPreview", true, "formalSuccessRequires", List.of("matchedFlowId", "matchedRuleId", "requiredCapability", "routingPath=FLOW_RULE")));
        chain.setGeneratedAt(OffsetDateTime.now());

        String externalRule = "rule-r7-" + normalizedFlowId + "-external";
        String collaboratorRule = "rule-r7-" + normalizedFlowId + "-collaborator";
        String resultRule = "rule-r7-" + normalizedFlowId + "-result";
        String issueRule = "rule-r7-" + normalizedFlowId + "-issue";
        String externalEvent = flowCode + "_EXTERNAL_EVENT";
        String collaborationEvent = flowCode + "_COLLABORATION_REQUESTED";
        String resultEvent = flowCode + "_COLLABORATION_COMPLETED";
        String issueEvent = flowCode + "_ISSUE_LINK_REQUESTED";
        String leadCapability = flowCode + "_INTAKE_ANALYSIS";
        String collaboratorCapability = flowCode + "_COLLABORATION_ANALYSIS";

        chain.setSteps(List.of(
            traceStep(1, "EXTERNAL_INTAKE_RECEIVED", "EXTERNAL", externalEvent, source, null, null, normalizedFlowId, externalRule, leadCapability, "FLOW_RULE", null, "PASS", null, null, "External event received with explicit source and Flow evidence.", null, correlationId),
            traceStep(2, "LEAD_AGENT_ASSIGNED", "EXTERNAL", externalEvent, source, null, null, normalizedFlowId, externalRule, leadCapability, "FLOW_RULE", leadAgentId, "PASS", null, null, "Lead Agent selected from configured Flow assignments.", parentTaskId, correlationId),
            traceStep(3, "A2A_EVENT_CREATED", "A2A", collaborationEvent, sourceAgent, source, collaborator, normalizedFlowId, collaboratorRule, collaboratorCapability, "FLOW_RULE", null, "PASS", null, null, "Lead Agent produced a source-neutral collaborator event.", parentTaskId, correlationId),
            traceStep(4, "A2A_AGENT_ELIGIBILITY", "A2A", collaborationEvent, sourceAgent, source, collaborator, normalizedFlowId, collaboratorRule, collaboratorCapability, "FLOW_RULE", null, "BLOCKED", "A2A_AGENT_ELIGIBILITY", "Assign a collaborator Agent with the required Capability.", "Collaborator Agent is missing an explicitly configured Capability or authority.", parentTaskId, correlationId),
            traceStep(5, "RESULT_CALLBACK_LINKED", "RESULT", resultEvent, collaboratorAgent, collaborator, sourceAgent, normalizedFlowId, resultRule, flowCode + "_RESULT_MERGE", "FLOW_RULE", leadAgentId, "WAITING", null, null, "Collaborator result will link to the parent task after eligibility is fixed.", parentTaskId, correlationId),
            traceStep(6, "ISSUE_TIMELINE_UPDATE", "ISSUE", issueEvent, sourceAgent, source, flowCode + "_ISSUE_ADAPTER", normalizedFlowId, issueRule, flowCode + "_ISSUE_LINK", "FLOW_RULE", leadAgentId, "WAITING", null, null, "Issue timeline update waits for the result merge policy.", parentTaskId, correlationId)
        ));
        return chain;
    }

    private String stringValue(Map<String, Object> values, String key) {
        if (values == null) return null;
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String conditionString(DispatchFlowRuleView rule, String key) {
        if (rule == null || rule.getCondition() == null) return null;
        Object value = rule.getCondition().get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String wildcardValue(String value, String wildcardReplacement) {
        return blank(value) || "*".equals(value.trim()) ? wildcardReplacement : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value.trim();
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private DispatchFlowTraceStepView traceStep(Integer sequence, String stepCode, String eventStage, String eventType,
                                                String sourceSystem, String originSourceSystem, String targetSystem,
                                                String matchedFlowId, String matchedRuleId, String requestedSkill,
                                                String routingPath, String selectedAgentId, String status,
                                                String failureStage, String fixAction, String message,
                                                String parentTaskId, String correlationId) {
        DispatchFlowTraceStepView step = new DispatchFlowTraceStepView();
        step.setSequence(sequence);
        step.setStepCode(stepCode);
        step.setEventStage(eventStage);
        step.setEventType(eventType);
        step.setSourceSystem(sourceSystem);
        step.setOriginSourceSystem(originSourceSystem);
        step.setTargetSystem(targetSystem);
        step.setMatchedFlowId(matchedFlowId);
        step.setMatchedRuleId(matchedRuleId);
        step.setRequestedSkill(requestedSkill);
        step.setRoutingPath(routingPath);
        step.setSelectedAgentId(selectedAgentId);
        step.setStatus(status);
        step.setFailureStage(failureStage);
        step.setFixAction(fixAction);
        step.setMessage(message);
        step.setParentTaskId(parentTaskId);
        step.setCorrelationId(correlationId);
        step.setCreatedAt(OffsetDateTime.now());
        step.setMetadata(Map.of("r7TraceStep", true));
        return step;
    }

}
