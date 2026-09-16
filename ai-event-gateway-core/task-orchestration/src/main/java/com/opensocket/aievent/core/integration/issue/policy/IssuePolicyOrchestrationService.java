package com.opensocket.aievent.core.integration.issue.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationConnectionStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityRepository;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;
import com.opensocket.aievent.core.integration.identity.MappingResolutionRequest;
import com.opensocket.aievent.core.integration.identity.ProjectMappingRuntimeReadiness;
import com.opensocket.aievent.core.integration.identity.ProviderFieldMetadata;
import com.opensocket.aievent.core.integration.identity.ProviderIssueTypeMetadata;
import com.opensocket.aievent.core.integration.identity.ProviderMetadataSnapshot;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionPurpose;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationActionCommand;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationActionPort;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationActionResult;
import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationOperation;
import com.opensocket.aievent.core.task.v53.TaskLifecycle;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskIssueBindingContext;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.domain.TaskIssueSyncPolicy;

/**
 * Canonical terminal Task -> external Issue orchestration authority.
 *
 * <p>Policy is decided durably before any provider work. Mapping resolution fails closed: automation
 * proceeds only when one ACTIVE governed Project Mapping can be selected deterministically. This
 * service never guesses a provider/project binding.</p>
 */
@Service
public class IssuePolicyOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(IssuePolicyOrchestrationService.class);
    private static final String PURPOSE = ProjectionPurpose.PRIMARY_ISSUE.name();
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}" );

    private final TaskRepository tasks;
    private final IntegrationIdentityRepository identities;
    private final IssuePolicyDecisionRepository decisions;
    private final TaskIssueLinkRepository taskIssueLinks;
    private final IssueAutomationActionPort issueActions;
    private final IssuePolicyOrchestrationProperties properties;

    public IssuePolicyOrchestrationService(TaskRepository tasks,
            IntegrationIdentityRepository identities,
            IssuePolicyDecisionRepository decisions,
            TaskIssueLinkRepository taskIssueLinks,
            IssueAutomationActionPort issueActions,
            IssuePolicyOrchestrationProperties properties) {
        this.tasks = tasks;
        this.identities = identities;
        this.decisions = decisions;
        this.taskIssueLinks = taskIssueLinks;
        this.issueActions = issueActions;
        this.properties = properties;
    }

    @Transactional
    public IssuePolicyDecision onTerminalEvent(TaskTerminalEvent event) {
        Objects.requireNonNull(event, "event");
        String tenant = required(event.tenantId(), "tenantId");
        String taskId = required(event.taskId(), "taskId");
        TaskRecord task = tasks.findByTenantAndId(tenant, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        // A0-R2: a legacy terminal status is only a terminalization signal. Issue materialization
        // must remain behind canonical finalization so Evidence/Case/Budget/lease obligations cannot
        // be bypassed by an early TaskTerminalEvent. The existing durable event outbox retries later.
        if (task.getTaskLifecycle() != TaskLifecycle.CLOSED) {
            throw new IllegalStateException("TASK_FINALIZATION_NOT_COMPLETE:" + taskId + ":" + task.getTaskLifecycle());
        }

        log.info("issue_policy_evaluation_started taskId={} sourceEventId={} taskStatus={} taskIssueSyncPolicy={} taskIssueSyncPolicySource={} issueSyncPolicyInheritanceMode={} issueSyncPolicyInheritedFromTaskId={}",
                task.getTaskId(), event.eventId(), enumName(task.getStatus()), task.getIssueSyncPolicy(), task.getIssueSyncPolicySource(),
                task.getIssueSyncPolicyInheritanceMode(), task.getIssueSyncPolicyInheritedFromTaskId());
        IssuePolicyDecision decision = decisions.findByTaskAndPurpose(tenant, taskId, PURPOSE)
                .orElseGet(() -> decisions.save(evaluate(task, event)));
        log.info("issue_policy_evaluation_completed taskId={} decisionId={} decision={} reasonCode={} bindingStatus={} automationStatus={} reusedDecision={}",
                task.getTaskId(), decision.decisionId(), decision.decision(), decision.reasonCode(), decision.bindingStatus(), decision.automationStatus(),
                !event.eventId().equals(decision.sourceEventId()));
        log.info("issue_policy_decision taskId={} matchedFlowId={} matchedRuleId={} taskStatus={} taskIssueSyncPolicy={} taskIssueSyncPolicySource={} issueSyncPolicyInheritanceMode={} issueSyncPolicyInheritedFromTaskId={} decision={} reasonCode={} bindingStatus={} automationStatus={} adapterActionId={} sourceEventId={}",
                task.getTaskId(), task.getMatchedFlowId(), task.getMatchedRuleId(), enumName(task.getStatus()), decision.taskIssueSyncPolicy(), task.getIssueSyncPolicySource(),
                task.getIssueSyncPolicyInheritanceMode(), task.getIssueSyncPolicyInheritedFromTaskId(), decision.decision(), decision.reasonCode(),
                decision.bindingStatus(), decision.automationStatus(), decision.adapterActionId(), decision.sourceEventId());
        boolean routeBRequired = decision.decision() == IssuePolicyDecisionOutcome.REQUIRED;
        boolean alreadyEntered = decision.automationStatus() == IssuePolicyAutomationStatus.ACTION_REQUESTED
                || decision.automationStatus() == IssuePolicyAutomationStatus.MATERIALIZED;
        boolean automaticEnabled = properties.isAutomaticMaterializationEnabled();
        boolean routeBWillEnterNow = routeBRequired && !alreadyEntered && automaticEnabled;
        log.info("issue_route_b_gate_evaluated taskId={} decisionId={} decision={} reasonCode={} routeBEligible={} automaticMaterializationEnabled={} alreadyEntered={} routeBWillEnterNow={} nextBoundary={} bindingStatus={} automationStatus={} adapterActionId={}",
                task.getTaskId(), decision.decisionId(), decision.decision(), decision.reasonCode(), routeBRequired, automaticEnabled, alreadyEntered, routeBWillEnterNow,
                routeBWillEnterNow ? "BINDING" : "NONE", decision.bindingStatus(), decision.automationStatus(), decision.adapterActionId());

        if (!routeBRequired) {
            log.info("issue_route_b_not_entered taskId={} decisionId={} stopBoundary=POLICY decision={} reasonCode={} taskIssueSyncPolicy={} taskIssueSyncPolicySource={} taskStatus={}",
                    task.getTaskId(), decision.decisionId(), decision.decision(), decision.reasonCode(), decision.taskIssueSyncPolicy(),
                    task.getIssueSyncPolicySource(), enumName(task.getStatus()));
            return decision;
        }
        if (alreadyEntered) {
            log.info("issue_route_b_reentry_skipped taskId={} decisionId={} reason=ALREADY_ACTION_REQUESTED_OR_MATERIALIZED automationStatus={} adapterActionId={}",
                    task.getTaskId(), decision.decisionId(), decision.automationStatus(), decision.adapterActionId());
            return decision;
        }
        if (!automaticEnabled) {
            log.info("issue_route_b_not_entered taskId={} decisionId={} stopBoundary=AUTOMATION_GATE decision={} reasonCode=ISSUE_AUTOMATION_DISABLED taskIssueSyncPolicy={} taskIssueSyncPolicySource={} taskStatus={}",
                    task.getTaskId(), decision.decisionId(), decision.decision(), decision.taskIssueSyncPolicy(), task.getIssueSyncPolicySource(), enumName(task.getStatus()));
            return saveState(decision, decision.bindingStatus(), decision.connectionId(), decision.projectMappingId(),
                    decision.projectMappingVersion(), decision.projectMappingSchemaHash(), decision.projectionId(),
                    decision.taskIssueLinkId(), decision.outboxId(), decision.issueOperation(), decision.adapterActionId(),
                    decision.actionIdempotencyKey(), decision.terminalGeneration(), IssuePolicyAutomationStatus.WAITING_MANUAL_DECISION,
                    "ISSUE_AUTOMATION_DISABLED", "Automatic Task Issue action creation is disabled.", event);
        }
        return automate(task, event, decision);
    }

    private IssuePolicyDecision evaluate(TaskRecord task, TaskTerminalEvent event) {
        TaskIssueSyncPolicy policy = task.getIssueSyncPolicy() == null ? TaskIssueSyncPolicy.OPTIONAL : task.getIssueSyncPolicy();
        IssuePolicyDecisionOutcome outcome;
        String reason;
        IssuePolicyBindingStatus binding;
        IssuePolicyAutomationStatus automation;
        switch (policy) {
            case NONE -> {
                outcome = IssuePolicyDecisionOutcome.NOT_REQUIRED;
                reason = "TASK_ISSUE_SYNC_POLICY_NONE";
                binding = IssuePolicyBindingStatus.NOT_REQUIRED;
                automation = IssuePolicyAutomationStatus.NOT_REQUIRED;
            }
            case REQUIRED -> {
                outcome = IssuePolicyDecisionOutcome.REQUIRED;
                reason = "TASK_ISSUE_SYNC_POLICY_REQUIRED";
                binding = IssuePolicyBindingStatus.NOT_EVALUATED;
                automation = IssuePolicyAutomationStatus.INTENT_READY;
            }
            case MANUAL -> {
                outcome = IssuePolicyDecisionOutcome.MANUAL_DECISION;
                reason = "TASK_ISSUE_SYNC_POLICY_MANUAL";
                binding = IssuePolicyBindingStatus.NOT_EVALUATED;
                automation = IssuePolicyAutomationStatus.WAITING_MANUAL_DECISION;
            }
            case OPTIONAL -> {
                if (isFailed(task) && properties.isOptionalFailedTaskRequiresIssue()) {
                    outcome = IssuePolicyDecisionOutcome.REQUIRED;
                    reason = "OPTIONAL_FAILED_TASK_POLICY_REQUIRED";
                    binding = IssuePolicyBindingStatus.NOT_EVALUATED;
                    automation = IssuePolicyAutomationStatus.INTENT_READY;
                } else if (isFailed(task)) {
                    outcome = IssuePolicyDecisionOutcome.MANUAL_DECISION;
                    reason = "OPTIONAL_FAILED_TASK_REQUIRES_DECISION";
                    binding = IssuePolicyBindingStatus.NOT_EVALUATED;
                    automation = IssuePolicyAutomationStatus.WAITING_MANUAL_DECISION;
                } else {
                    outcome = IssuePolicyDecisionOutcome.NOT_REQUIRED;
                    reason = "OPTIONAL_NON_FAILED_TASK_NOT_REQUIRED";
                    binding = IssuePolicyBindingStatus.NOT_REQUIRED;
                    automation = IssuePolicyAutomationStatus.NOT_REQUIRED;
                }
            }
            default -> throw new IllegalStateException("Unsupported Task Issue Sync Policy: " + policy);
        }
        OffsetDateTime now = now();
        return new IssuePolicyDecision(task.getTenantId(), stableDecisionId(task.getTenantId(), task.getTaskId()),
                task.getTaskId(), PURPOSE, normalizedPolicyId(), properties.getPolicyVersion(), policy.name(), outcome,
                reason, required(event.eventId(), "sourceEventId"), event.eventType(), enumName(task.getStatus()), binding,
                null, null, null, null, null, null, null, null, null, null, null, automation, null, null,
                first(event.correlationId(), task.getCorrelationId(), event.eventId()), event.causationId(),
                first(event.traceId(), task.getTraceId()), event.actorType(), event.actorId(), 1L, now, now);
    }

    private IssuePolicyDecision automate(TaskRecord task, TaskTerminalEvent event, IssuePolicyDecision decision) {
        TaskRecord issueOwnerTask = resolveIssueOwnerTask(task);
        Binding binding;
        TaskIssueBindingContext bindingContext = TaskIssueBindingContext.from(issueOwnerTask);
        log.info("issue_binding_resolution_started taskId={} issueOwnerTaskId={} decisionId={} departmentId={} groupId={} serviceDomainId={} sourceSystem={} taskType={} contextAuthority=CANONICAL_TASK_ISSUE_BINDING",
                task.getTaskId(), issueOwnerTask.getTaskId(), decision.decisionId(), bindingContext.departmentId(), bindingContext.groupId(),
                bindingContext.serviceDomainId(), bindingContext.sourceSystemId(), bindingContext.taskType());
        try {
            binding = resolveBinding(issueOwnerTask);
            log.info("issue_binding_resolution_completed taskId={} decisionId={} connectionId={} projectMappingId={} projectMappingVersion={} schemaHashPresent={}",
                    task.getTaskId(), decision.decisionId(), binding.connection().connectionId(), binding.mapping().mappingId(),
                    binding.mapping().mappingVersion(), binding.schemaHash() != null && !binding.schemaHash().isBlank());
        } catch (BindingException ex) {
            log.warn("issue_binding_resolution_failed taskId={} decisionId={} taskIssueSyncPolicy={} decision={} bindingStatus={} errorCode={} errorMessage={}",
                    task.getTaskId(), decision.decisionId(), decision.taskIssueSyncPolicy(), decision.decision(), ex.status, ex.code, safe(ex.getMessage()));
            log.warn("issue_policy_binding_blocked taskId={} taskIssueSyncPolicy={} decision={} bindingStatus={} errorCode={} errorMessage={}",
                    task.getTaskId(), decision.taskIssueSyncPolicy(), decision.decision(), ex.status, ex.code, safe(ex.getMessage()));
            return saveState(decision, ex.status, null, null, null, null, null, null, null,
                    IssueAutomationOperation.CREATE_ISSUE.name(), null, null, terminalGeneration(task),
                    IssuePolicyAutomationStatus.BINDING_BLOCKED, ex.code, safe(ex.getMessage()), event);
        }

        ExistingIssueTarget existingIssue;
        try {
            existingIssue = resolveExistingIssueTarget(task, issueOwnerTask, binding);
        } catch (ExistingIssueException ex) {
            log.warn("issue_existing_target_resolution_failed taskId={} decisionId={} connectionId={} projectMappingId={} errorCode={} errorMessage={}",
                    task.getTaskId(), decision.decisionId(), binding.connection().connectionId(), binding.mapping().mappingId(), ex.code, safe(ex.getMessage()));
            return saveState(decision, IssuePolicyBindingStatus.RESOLVED, binding.connection().connectionId(),
                    binding.mapping().mappingId(), binding.mapping().mappingVersion(), binding.schemaHash(),
                    null, null, null, null, null, null, terminalGeneration(task), IssuePolicyAutomationStatus.FAILED,
                    ex.code, safe(ex.getMessage()), event);
        }

        IssueAutomationOperation operation;
        try {
            operation = selectOperation(binding, existingIssue);
        } catch (ExistingIssueException ex) {
            log.warn("issue_existing_target_operation_blocked taskId={} decisionId={} connectionId={} projectMappingId={} commentPolicy={} errorCode={} errorMessage={}",
                    task.getTaskId(), decision.decisionId(), binding.connection().connectionId(), binding.mapping().mappingId(),
                    binding.mapping().commentPolicy(), ex.code, safe(ex.getMessage()));
            return saveState(decision, IssuePolicyBindingStatus.RESOLVED, binding.connection().connectionId(),
                    binding.mapping().mappingId(), binding.mapping().mappingVersion(), binding.schemaHash(),
                    null, null, null, null, null, null, terminalGeneration(task), IssuePolicyAutomationStatus.FAILED,
                    ex.code, safe(ex.getMessage()), event);
        }
        log.info("issue_existing_target_resolved taskId={} issueOwnerTaskId={} decisionId={} operation={} existingIssue={} taskIssueLinkId={} externalIssueId={} connectionId={} projectMappingId={}",
                task.getTaskId(), issueOwnerTask.getTaskId(), decision.decisionId(), operation, existingIssue != null,
                existingIssue == null ? null : existingIssue.linkId(), existingIssue == null ? null : existingIssue.externalIssueId(),
                binding.connection().connectionId(), binding.mapping().mappingId());

        MaterializedIssue materialized;
        try {
            materialized = operation == IssueAutomationOperation.CREATE_ISSUE
                    ? materializeCreate(task, event, binding)
                    : new MaterializedIssue(first(task.getTitle(), "Task " + task.getTaskId()),
                            existingIssueComment(task, event), null, Map.of());
        } catch (PayloadMaterializationException ex) {
            log.warn("issue_payload_materialization_failed taskId={} issueOwnerTaskId={} decisionId={} mappingId={} errorCode={} errorMessage={}",
                    task.getTaskId(), issueOwnerTask.getTaskId(), decision.decisionId(), binding.mapping().mappingId(), ex.code, safe(ex.getMessage()));
            return saveState(decision, IssuePolicyBindingStatus.RESOLVED, binding.connection().connectionId(),
                    binding.mapping().mappingId(), binding.mapping().mappingVersion(), binding.schemaHash(),
                    null, null, null, operation.name(), null, null, terminalGeneration(task), IssuePolicyAutomationStatus.FAILED,
                    ex.code, safe(ex.getMessage()), event);
        }

        long generation = terminalGeneration(task);
        TaskRecord actionAuthorityTask = operation == IssueAutomationOperation.CREATE_ISSUE ? issueOwnerTask : task;
        String actionKey = routeBIdempotencyKey(actionAuthorityTask, decision, binding, operation, generation,
                existingIssue == null ? null : existingIssue.externalIssueId());
        Map<String,Object> approved = new LinkedHashMap<>();
        put(approved, "taskStatus", enumName(task.getStatus()));
        put(approved, "taskErrorCode", task.getErrorCode());
        put(approved, "callbackType", event.callbackType());
        put(approved, "callbackResultStatus", event.resultStatus());
        put(approved, "callbackErrorCode", event.callbackErrorCode());
        put(approved, "issueOwnerTaskId", issueOwnerTask.getTaskId());
        if (!materialized.providerFields().isEmpty()) approved.put("providerFields", materialized.providerFields());
        Map<String,String> evidence = new LinkedHashMap<>();
        putString(evidence, "taskId", task.getTaskId());
        putString(evidence, "terminalEventId", event.eventId());
        putString(evidence, "callbackId", event.callbackId());
        putString(evidence, "correlationId", first(event.correlationId(), task.getCorrelationId(), event.eventId()));
        putString(evidence, "issueOwnerTaskId", issueOwnerTask.getTaskId());

        log.info("issue_adapter_action_request_boundary_started taskId={} decisionId={} operation={} connectionId={} projectMappingId={} generation={} idempotencyKey={}",
                task.getTaskId(), decision.decisionId(), operation, binding.connection().connectionId(), binding.mapping().mappingId(), generation, actionKey);
        try {
            IssueAutomationActionResult requested = issueActions.request(new IssueAutomationActionCommand(
                    task.getTenantId(), actionAuthorityTask.getTaskId(), task.getIncidentId(), operation,
                    binding.connection().connectionId(), binding.mapping().mappingId(), binding.mapping().mappingVersion(),
                    binding.schemaHash(), decision.policyId(), decision.policyVersion(), generation, actionKey,
                    materialized.title(),
                    materialized.description(),
                    materialized.priorityId(),
                    existingIssue == null ? null : existingIssue.linkId(),
                    existingIssue == null ? null : existingIssue.externalIssueId(),
                    existingIssue == null ? null : existingIssue.externalIssueUrl(),
                    first(event.correlationId(), task.getCorrelationId(), event.eventId()), event.causationId(),
                    first(event.traceId(), task.getTraceId()), first(event.actorType(), "SYSTEM"),
                    first(event.actorId(), "opendispatch"), Map.copyOf(approved), Map.copyOf(evidence)));
            log.info("issue_adapter_action_request_boundary_completed taskId={} decisionId={} actionId={} created={} actionStatus={} operation={} mappingId={} generation={} idempotencyKey={}",
                    task.getTaskId(), decision.decisionId(), requested.actionId(), requested.created(), requested.status(), operation, binding.mapping().mappingId(), generation, requested.idempotencyKey());
            log.info("issue_route_b_action_requested taskId={} actionId={} created={} operation={} mappingId={} generation={}",
                    task.getTaskId(), requested.actionId(), requested.created(), operation, binding.mapping().mappingId(), generation);
            return saveState(decision, IssuePolicyBindingStatus.RESOLVED, binding.connection().connectionId(),
                    binding.mapping().mappingId(), binding.mapping().mappingVersion(), binding.schemaHash(),
                    null, existingIssue == null ? null : existingIssue.linkId(), null, operation.name(), requested.actionId(), requested.idempotencyKey(), generation,
                    IssuePolicyAutomationStatus.ACTION_REQUESTED, null, null, event);
        } catch (RuntimeException ex) {
            log.error("issue_adapter_action_request_boundary_failed taskId={} decisionId={} operation={} connectionId={} projectMappingId={} generation={} idempotencyKey={} exceptionClass={} error={}",
                    task.getTaskId(), decision.decisionId(), operation, binding.connection().connectionId(), binding.mapping().mappingId(), generation, actionKey,
                    ex.getClass().getName(), safe(ex.getMessage()));
            return saveState(decision, IssuePolicyBindingStatus.RESOLVED, binding.connection().connectionId(),
                    binding.mapping().mappingId(), binding.mapping().mappingVersion(), binding.schemaHash(),
                    null, existingIssue == null ? null : existingIssue.linkId(), null, operation.name(), null, actionKey, generation, IssuePolicyAutomationStatus.FAILED,
                    "ISSUE_AUTOMATION_ACTION_REQUEST_FAILED", safe(ex.getMessage()), event);
        }
    }

    private Binding resolveBinding(TaskRecord task) {
        TaskIssueBindingContext context = TaskIssueBindingContext.from(task);
        MappingResolutionRequest request = new MappingResolutionRequest(context.tenantId(), null,
                context.departmentId(), context.groupId(), context.serviceDomainId(),
                context.sourceSystemId(), context.taskType());

        List<IntegrationProjectMapping> configured = identities.listMappings(context.tenantId(), null, 1000);
        int sourceCovering = 0;
        int runtimeReady = 0;
        int runtimeReadyAndMatching = 0;
        Map<String, Integer> blockerCounts = new LinkedHashMap<>();
        for (IntegrationProjectMapping mapping : configured) {
            boolean sourceMatches = match(mapping.sourceSystemId(), request.sourceSystemId());
            if (sourceMatches) sourceCovering++;
            List<String> blockers = ProjectMappingRuntimeReadiness.blockers(mapping);
            if (blockers.isEmpty()) {
                runtimeReady++;
                if (matches(mapping, request)) runtimeReadyAndMatching++;
            } else if (sourceMatches) {
                for (String blocker : blockers) blockerCounts.merge(blocker, 1, Integer::sum);
            }
        }
        log.info("issue_binding_candidate_summary taskId={} tenantId={} totalMappings={} sourceCoveringMappings={} runtimeReadyMappings={} runtimeReadyMatchingMappings={} sourceSystem={} taskType={} departmentId={} groupId={} serviceDomainId={} sourceCoveringBlockers={}",
                task.getTaskId(), context.tenantId(), configured.size(), sourceCovering, runtimeReady,
                runtimeReadyAndMatching, context.sourceSystemId(), context.taskType(), context.departmentId(),
                context.groupId(), context.serviceDomainId(), blockerCounts);

        List<ScoredMapping> candidates = new ArrayList<>();
        for (IntegrationProjectMapping mapping : identities.resolutionCandidates(request)) {
            if (!eligible(mapping) || !matches(mapping, request)) continue;
            candidates.add(new ScoredMapping(mapping, specificity(mapping, request)));
        }
        candidates.sort(Comparator.comparingInt(ScoredMapping::specificity).reversed()
                .thenComparingInt(x -> x.mapping().resolutionPriority())
                .thenComparing(x -> x.mapping().mappingId()));
        if (candidates.isEmpty()) {
            String detail;
            if (configured.isEmpty()) {
                detail = "No Project Mapping is configured for this Tenant.";
            } else if (sourceCovering == 0) {
                detail = "Project Mappings exist, but none covers sourceSystem=" + context.sourceSystemId() + ".";
            } else if (!blockerCounts.isEmpty()) {
                detail = "A Source-covering Project Mapping exists but is not runtime-ready. Required: enabled=true, lifecycleStatus=ACTIVE, mappingStatus=VALID, metadata snapshot/schema present. blockers=" + blockerCounts + ".";
            } else {
                detail = "Runtime-ready Project Mappings exist, but none matches the Task scope (department/group/serviceDomain/sourceSystem/taskType).";
            }
            throw new BindingException(IssuePolicyBindingStatus.MAPPING_NOT_FOUND,
                    "ISSUE_MAPPING_NOT_FOUND", detail);
        }
        ScoredMapping selected = candidates.get(0);
        if (candidates.size() > 1) {
            ScoredMapping second = candidates.get(1);
            if (selected.specificity() == second.specificity()
                    && selected.mapping().resolutionPriority() == second.mapping().resolutionPriority()) {
                throw new BindingException(IssuePolicyBindingStatus.MAPPING_AMBIGUOUS,
                        "ISSUE_MAPPING_AMBIGUOUS", "Multiple Project Mappings have the same authority rank.");
            }
        }
        IntegrationProjectMapping mapping = selected.mapping();
        IntegrationConnection connection = identities.findConnection(task.getTenantId(), mapping.connectionId())
                .orElseThrow(() -> new BindingException(IssuePolicyBindingStatus.CONNECTION_UNAVAILABLE,
                        "ISSUE_CONNECTION_NOT_FOUND", "Project Mapping connection is unavailable."));
        if (!connection.enabled() || connection.status() != IntegrationConnectionStatus.ACTIVE) {
            throw new BindingException(IssuePolicyBindingStatus.CONNECTION_UNAVAILABLE,
                    "ISSUE_CONNECTION_NOT_ACTIVE", "Project Mapping connection is not ACTIVE.");
        }
        String schemaHash = clean(mapping.metadataSchemaHash());
        if (schemaHash == null) {
            var version = identities.findMappingVersion(task.getTenantId(), mapping.mappingId(), mapping.mappingVersion()).orElse(null);
            if (version != null) schemaHash = clean(first(version.metadataSchemaHash(), version.configurationHash()));
        }
        if (schemaHash == null) throw new BindingException(IssuePolicyBindingStatus.MAPPING_SCHEMA_UNAVAILABLE,
                "ISSUE_MAPPING_SCHEMA_UNAVAILABLE", "Project Mapping has no governed metadata schema hash.");
        return new Binding(connection, mapping, schemaHash);
    }

    private boolean eligible(IntegrationProjectMapping mapping) {
        return ProjectMappingRuntimeReadiness.isReady(mapping);
    }

    private boolean matches(IntegrationProjectMapping m, MappingResolutionRequest r) {
        return match(m.departmentId(), r.departmentId()) && match(m.groupId(), r.groupId())
                && match(m.serviceDomainId(), r.serviceDomainId()) && match(m.sourceSystemId(), r.sourceSystemId())
                && match(m.taskType(), r.taskType());
    }

    private boolean match(String configured, String actual) {
        String c = clean(configured);
        return c == null || "*".equals(c) || Objects.equals(c, clean(actual));
    }

    private int specificity(IntegrationProjectMapping m, MappingResolutionRequest r) {
        int score = 0;
        if (specificMatch(m.groupId(), r.groupId())) score += 32;
        if (specificMatch(m.departmentId(), r.departmentId())) score += 16;
        if (specificMatch(m.serviceDomainId(), r.serviceDomainId())) score += 8;
        if (specificMatch(m.taskType(), r.taskType())) score += 4;
        if (specificMatch(m.sourceSystemId(), r.sourceSystemId())) score += 2;
        if (m.defaultMapping()) score += 1;
        return score;
    }

    private boolean specificMatch(String configured, String actual) {
        String c = clean(configured);
        return c != null && !"*".equals(c) && Objects.equals(c, clean(actual));
    }

    private IssuePolicyDecision saveState(IssuePolicyDecision current, IssuePolicyBindingStatus bindingStatus,
            String connectionId, String projectMappingId, Integer mappingVersion, String schemaHash,
            String projectionId, String linkId, String outboxId, String issueOperation, String adapterActionId,
            String actionIdempotencyKey, Long terminalGeneration, IssuePolicyAutomationStatus automationStatus,
            String errorCode, String errorMessage, TaskTerminalEvent event) {
        IssuePolicyDecision next = new IssuePolicyDecision(current.tenantId(), current.decisionId(), current.taskId(),
                current.projectionPurpose(), current.policyId(), current.policyVersion(), current.taskIssueSyncPolicy(),
                current.decision(), current.reasonCode(), current.sourceEventId(), current.sourceEventType(), current.taskStatus(),
                bindingStatus, connectionId, projectMappingId, mappingVersion, schemaHash, projectionId, linkId, outboxId,
                issueOperation, adapterActionId, actionIdempotencyKey, terminalGeneration, automationStatus, errorCode, errorMessage, first(event.correlationId(), current.correlationId(), event.eventId()),
                first(event.causationId(), current.causationId()), first(event.traceId(), current.traceId()),
                first(event.actorType(), current.actorType()), first(event.actorId(), current.actorId()), current.version() + 1,
                current.createdAt(), now());
        return decisions.save(next);
    }

    private long terminalGeneration(TaskRecord task) {
        if (task.getFinalizationCompletedAt() != null) {
            return Math.max(1L, task.getFinalizationCompletedAt().toInstant().toEpochMilli());
        }
        return Math.max(1L, task.getVersion());
    }

    private String routeBIdempotencyKey(TaskRecord actionAuthorityTask, IssuePolicyDecision decision, Binding binding,
            IssueAutomationOperation operation, long terminalGeneration, String externalIssueId) {
        // CREATE is aggregate/owner-scoped. An inherited child may be the first terminal participant,
        // but it must request the same CREATE key the owner/root Task would request later. Mutations of
        // an already confirmed Issue remain terminal-event scoped to preserve append/comment semantics.
        String generationKey = operation == IssueAutomationOperation.CREATE_ISSUE
                ? "OWNER_CREATE"
                : Long.toString(terminalGeneration);
        return sha256("ISSUE_AUTOMATION_ROUTE_B|" + actionAuthorityTask.getTenantId() + "|" + actionAuthorityTask.getTaskId() + "|"
                + generationKey + "|" + decision.policyId() + "|" + decision.policyVersion() + "|"
                + operation.name() + "|" + binding.mapping().mappingId() + "|" + binding.mapping().mappingVersion() + "|"
                + first(externalIssueId, "NEW"));
    }

    /**
     * Resolve an existing external Issue only from the canonical TaskIssueLink read model.
     * Client payloads and legacy Incident.linkedIssueId are deliberately not consulted.
     */
    private ExistingIssueTarget resolveExistingIssueTarget(TaskRecord task, TaskRecord issueOwnerTask, Binding binding) {
        boolean inheritedOwner = !Objects.equals(task.getTaskId(), issueOwnerTask.getTaskId());
        List<TaskIssueLink> authorityLinks = inheritedOwner
                ? taskIssueLinks.findAllByTenantAndTaskId(task.getTenantId(), issueOwnerTask.getTaskId())
                : taskIssueLinks.findAllByTenantAndTaskId(task.getTenantId(), task.getTaskId());
        List<TaskIssueLink> primary = authorityLinks.stream()
                .filter(Objects::nonNull)
                .filter(link -> link.getLinkRole() == null || link.getLinkRole().isBlank()
                        || "PRIMARY".equalsIgnoreCase(link.getLinkRole()))
                .toList();
        if (primary.isEmpty()) {
            // HF18 aggregate ownership: the first terminal participant may bootstrap the owner Task's
            // external Issue. The CREATE action is persisted against issueOwnerTask and uses an owner-
            // scoped idempotency key, so concurrent/sibling inherited Tasks converge on one CREATE.
            return null;
        }

        List<TaskIssueLink> confirmed = primary.stream()
                .filter(this::hasConfirmedExternalIssue)
                .sorted(Comparator.comparing(TaskIssueLink::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (confirmed.isEmpty()) {
            boolean unfinished = primary.stream().anyMatch(this::mayRepresentUnfinishedIssueWrite);
            if (unfinished) {
                throw new ExistingIssueException("ISSUE_EXISTING_LINK_NOT_CONFIRMED",
                        "A PRIMARY owner TaskIssueLink already exists but the external Issue is not confirmed. Retry/reconcile that action instead of creating a duplicate Issue.");
            }
            return null;
        }

        Map<String, TaskIssueLink> uniqueTargets = new LinkedHashMap<>();
        for (TaskIssueLink link : confirmed) {
            String externalId = externalIssueId(link);
            String key = first(link.getConnectionId(), "?") + "|" + first(link.getProjectMappingId(), "?") + "|" + externalId;
            uniqueTargets.putIfAbsent(key, link);
        }
        if (uniqueTargets.size() > 1) {
            throw new ExistingIssueException("ISSUE_EXISTING_LINK_AMBIGUOUS",
                    "Multiple PRIMARY external Issue targets exist for the Task; canonical sync cannot choose one safely.");
        }

        TaskIssueLink link = uniqueTargets.values().iterator().next();
        String externalId = externalIssueId(link);
        if (clean(link.getConnectionId()) == null || clean(link.getProjectMappingId()) == null) {
            throw new ExistingIssueException("ISSUE_EXISTING_LINK_BINDING_EVIDENCE_REQUIRED",
                    "Existing TaskIssueLink is missing connection/project mapping evidence; refusing to guess provider authority.");
        }
        if (!Objects.equals(binding.connection().connectionId(), clean(link.getConnectionId()))
                || !Objects.equals(binding.mapping().mappingId(), clean(link.getProjectMappingId()))) {
            throw new ExistingIssueException("ISSUE_EXISTING_LINK_BINDING_MISMATCH",
                    "Existing TaskIssueLink provider binding differs from the current governed Project Mapping; refusing cross-binding Issue mutation.");
        }
        return new ExistingIssueTarget(inheritedOwner ? null : link.getLinkId(), externalId,
                first(link.getExternalIssueUrl(), link.getIssueUrl()), issueOwnerTask.getTaskId());
    }

    private TaskRecord resolveIssueOwnerTask(TaskRecord task) {
        String mode = first(task.getIssueSyncPolicyInheritanceMode(), "NONE");
        if ("NONE".equalsIgnoreCase(mode)) return task;
        String ownerId = first(task.getRootTaskId(), task.getIssueSyncPolicyInheritedFromTaskId(), task.getParentTaskId());
        if (ownerId == null || ownerId.equals(task.getTaskId())) return task;
        return tasks.findByTenantAndId(task.getTenantId(), ownerId)
                .orElseThrow(() -> new IssueOwnerLinkNotReadyException(ownerId,
                        "Issue-policy owner Task is not available for inherited Task " + task.getTaskId()));
    }

    private MaterializedIssue materializeCreate(TaskRecord task, TaskTerminalEvent event, Binding binding) {
        Map<String,Object> context = materializationContext(task, event);
        String title = render(first(binding.mapping().summaryTemplate(), "{{task.title}}"), context);
        String description = render(first(binding.mapping().descriptionTemplate(), "{{task.description}}"), context);
        if (title == null || title.isBlank()) title = first(task.getTitle(), "Task " + task.getTaskId());
        if (description == null || description.isBlank()) description = first(task.getDescription(), event.callbackMessage(), "OpenDispatch Task Issue automation.");

        Map<String,Object> providerFields = new LinkedHashMap<>();
        binding.mapping().customFieldMappings().forEach((target, source) -> {
            Object value = materializedValue(context, source);
            if (value != null) providerFields.put(target, value);
        });

        ProviderMetadataSnapshot metadata = clean(binding.mapping().metadataSnapshotId()) == null ? null
                : identities.findMetadataSnapshot(task.getTenantId(), binding.mapping().metadataSnapshotId()).orElse(null);
        String priorityId = textValue(providerFields.remove("priority_id"));
        if (priorityId == null && metadata != null) {
            priorityId = metadata.fields().stream()
                    .filter(field -> "priority_id".equals(field.fieldId()) || "priority_id".equals(field.fieldKey()))
                    .flatMap(field -> field.allowedValues().stream())
                    .map(this::clean).filter(Objects::nonNull).findFirst().orElse(null);
        }

        Set<String> requiredFields = new LinkedHashSet<>(binding.mapping().requiredFields());
        if (metadata != null) {
            ProviderIssueTypeMetadata issueType = metadata.issueTypes().stream()
                    .filter(value -> Objects.equals(value.issueTypeId(), binding.mapping().externalIssueType())
                            || Objects.equals(value.issueTypeKey(), binding.mapping().externalIssueType())
                            || Objects.equals(value.issueTypeId(), binding.mapping().externalTrackerId())
                            || Objects.equals(value.issueTypeKey(), binding.mapping().externalTrackerId()))
                    .findFirst().orElse(null);
            if (issueType != null) requiredFields.addAll(issueType.requiredFieldIds());
        }
        for (String requiredField : requiredFields) {
            String field = clean(requiredField);
            if (field == null || Set.of("summary", "subject", "description").contains(field)) continue;
            if ("priority_id".equals(field)) {
                // Existing pre-HF17 snapshots have no priority metadata. Redmine executor performs
                // one live provider lookup before CREATE, so absence here is not guessed or hard-coded.
                if (priorityId == null && binding.connection().providerType() != null
                        && !"REDMINE".equalsIgnoreCase(binding.connection().providerType().name())) {
                    throw new PayloadMaterializationException("ISSUE_PROVIDER_REQUIRED_FIELD_UNMAPPED",
                            "Required provider field is not mapped: priority_id");
                }
                continue;
            }
            if (!providerFields.containsKey(field) || providerFields.get(field) == null) {
                throw new PayloadMaterializationException("ISSUE_PROVIDER_REQUIRED_FIELD_UNMAPPED",
                        "Required provider field is not mapped: " + field);
            }
        }
        log.info("issue_payload_materialized taskId={} mappingId={} priorityMaterialized={} providerFieldCount={} requiredFieldCount={}",
                task.getTaskId(), binding.mapping().mappingId(), priorityId != null, providerFields.size(), requiredFields.size());
        return new MaterializedIssue(title, description, priorityId, Map.copyOf(providerFields));
    }

    private Map<String,Object> materializationContext(TaskRecord task, TaskTerminalEvent event) {
        Map<String,Object> taskMap = new LinkedHashMap<>();
        put(taskMap, "id", task.getTaskId());
        put(taskMap, "taskId", task.getTaskId());
        put(taskMap, "title", task.getTitle());
        put(taskMap, "description", task.getDescription());
        put(taskMap, "status", enumName(task.getStatus()));
        put(taskMap, "priority", task.getPriority() == null ? null : task.getPriority().name());
        put(taskMap, "severity", task.getSeverity() == null ? null : task.getSeverity().name());
        put(taskMap, "errorCode", task.getErrorCode());
        put(taskMap, "incidentId", task.getIncidentId());
        put(taskMap, "sourceSystem", first(task.getSourceSystem(), task.getOriginSourceSystem()));
        put(taskMap, "taskType", task.getTaskTypeCode());
        Map<String,Object> eventMap = new LinkedHashMap<>();
        put(eventMap, "callbackMessage", event.callbackMessage());
        put(eventMap, "callbackType", event.callbackType());
        put(eventMap, "resultStatus", event.resultStatus());
        put(eventMap, "callbackErrorCode", event.callbackErrorCode());
        Map<String,Object> root = new LinkedHashMap<>();
        root.put("task", taskMap);
        root.put("event", eventMap);
        taskMap.forEach(root::putIfAbsent);
        return root;
    }

    private String render(String template, Map<String,Object> context) {
        if (template == null) return null;
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object value = materializedValue(context, matcher.group(1).trim());
            matcher.appendReplacement(output, Matcher.quoteReplacement(value == null ? matcher.group() : String.valueOf(value)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Object materializedValue(Map<String,Object> context, String path) {
        if (path == null || path.isBlank()) return null;
        if (path.startsWith("literal:")) return path.substring("literal:".length());
        if (context.containsKey(path)) return context.get(path);
        Object current = context;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?,?> values)) return null;
            current = values.get(segment);
        }
        return current;
    }

    private String textValue(Object value) {
        return value == null ? null : clean(String.valueOf(value));
    }

    private IssueAutomationOperation selectOperation(Binding binding, ExistingIssueTarget existingIssue) {
        if (existingIssue == null) return IssueAutomationOperation.CREATE_ISSUE;
        String commentPolicy = first(binding.mapping().commentPolicy(), "APPEND_ONLY");
        if ("APPEND_ONLY".equalsIgnoreCase(commentPolicy) || "STATUS_CHANGES_ONLY".equalsIgnoreCase(commentPolicy)) {
            // A terminal Task is itself a lifecycle status change. Existing Issue synchronization is append-only;
            // field mutation/transition requires a separately governed mapping contract and is never guessed here.
            return IssueAutomationOperation.ADD_COMMENT;
        }
        if ("DISABLED".equalsIgnoreCase(commentPolicy)) {
            throw new ExistingIssueException("ISSUE_EXISTING_LINK_COMMENT_DISABLED",
                    "Project Mapping commentPolicy=DISABLED; refusing to mutate the existing Issue through an ungoverned UPDATE fallback.");
        }
        throw new ExistingIssueException("ISSUE_EXISTING_LINK_COMMENT_POLICY_UNSUPPORTED",
                "Unsupported Project Mapping commentPolicy for canonical existing-Issue synchronization: " + commentPolicy);
    }

    private boolean hasConfirmedExternalIssue(TaskIssueLink link) {
        String id = externalIssueId(link);
        if (id == null) return false;
        return TaskIssueLink.LINK_EXTERNAL_CONFIRMED.equalsIgnoreCase(link.getLinkState())
                || TaskIssueLink.SYNCED.equalsIgnoreCase(link.getSyncStatus());
    }

    private boolean mayRepresentUnfinishedIssueWrite(TaskIssueLink link) {
        String sync = clean(link.getSyncStatus());
        if (sync == null) return false;
        return TaskIssueLink.SYNC_PENDING.equalsIgnoreCase(sync)
                || TaskIssueLink.SYNC_IN_PROGRESS.equalsIgnoreCase(sync)
                || link.isIssueRetryable()
                || "ISSUE_CREATE".equalsIgnoreCase(clean(link.getIssueActionType()));
    }

    private String externalIssueId(TaskIssueLink link) {
        return first(link == null ? null : link.getExternalIssueId(), link == null ? null : link.getIssueId());
    }

    private String existingIssueComment(TaskRecord task, TaskTerminalEvent event) {
        String status = first(enumName(task.getStatus()), "UNKNOWN");
        String summary = first(event.callbackMessage(), task.getDescription(), task.getErrorCode(), "Task terminal result recorded by OpenDispatch.");
        return "OpenDispatch Task " + task.getTaskId() + " closed with status " + status + ".\n\n" + summary;
    }

    private boolean isFailed(TaskRecord task) {
        TaskStatus status = task.getStatus();
        return status != null && status.isFailed();
    }

    private String stableDecisionId(String tenant, String task) {
        return "issue-policy-" + sha256(tenant + "|" + task + "|" + PURPOSE).substring(0, 32);
    }

    private String normalizedPolicyId() {
        String value = clean(properties.getPolicyId());
        return value == null ? "task-issue-sync-policy-v1" : value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private String enumName(Enum<?> value) { return value == null ? null : value.name(); }
    private String required(String value, String name) {
        String v = clean(value); if (v == null) throw new IllegalArgumentException(name + " is required"); return v;
    }
    private String clean(String value) {
        if (value == null || value.isBlank() || "UNASSIGNED".equalsIgnoreCase(value.trim())) return null;
        return value.trim();
    }
    private String first(String... values) {
        if (values == null) return null;
        for (String value : values) { String v = clean(value); if (v != null) return v; }
        return null;
    }
    private String safe(String value) {
        if (value == null || value.isBlank()) return "Issue orchestration failed.";
        String v = value.replaceAll("(?i)(authorization|token|secret|password|cookie)\\s*[:=]\\s*[^,;\\s]+", "$1=[REDACTED]");
        return v.length() > 512 ? v.substring(0, 512) : v;
    }
    private void put(Map<String,Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private void putString(Map<String,String> map, String key, String value) { if (value != null && !value.isBlank()) map.put(key, value); }

    private record Binding(IntegrationConnection connection, IntegrationProjectMapping mapping, String schemaHash) {}
    private record ScoredMapping(IntegrationProjectMapping mapping, int specificity) {}
    private record ExistingIssueTarget(String linkId, String externalIssueId, String externalIssueUrl, String issueOwnerTaskId) {}
    private record MaterializedIssue(String title, String description, String priorityId, Map<String,Object> providerFields) {}
    private static final class IssueOwnerLinkNotReadyException extends RuntimeException {
        private final String ownerTaskId;
        private IssueOwnerLinkNotReadyException(String ownerTaskId, String message) { super(message); this.ownerTaskId = ownerTaskId; }
    }
    private static final class PayloadMaterializationException extends RuntimeException {
        private final String code;
        private PayloadMaterializationException(String code, String message) { super(message); this.code = code; }
    }
    private static final class ExistingIssueException extends RuntimeException {
        private final String code;
        private ExistingIssueException(String code, String message) { super(message); this.code = code; }
    }
    private static final class BindingException extends RuntimeException {
        private final IssuePolicyBindingStatus status;
        private final String code;
        private BindingException(IssuePolicyBindingStatus status, String code, String message) {
            super(message); this.status = status; this.code = code;
        }
    }
}
