package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;

import com.opensocket.aievent.core.decision.DecisionAction;
import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.core.routing.RoutingProperties;

@Service
public class TaskDecisionService {
    private static final Logger log = LoggerFactory.getLogger(TaskDecisionService.class);

    private final TaskRepository taskRepository;
    private final IncidentFacade incidentFacade;
    private final TaskOrchestrationProperties properties;
    private final TaskAssignmentService taskAssignmentService;
    private final FlowRuleRoutingService flowRuleRoutingService;
    private final RoutingProperties routingProperties;
    private final IncidentTaskSuppressionPolicy suppressionPolicy;

    public TaskDecisionService(TaskRepository taskRepository, IncidentFacade incidentFacade, TaskOrchestrationProperties properties, TaskAssignmentService taskAssignmentService) {
        this(taskRepository, incidentFacade, properties, taskAssignmentService, new IncidentTaskSuppressionPolicy(), (FlowRuleRoutingService) null, (RoutingProperties) null);
    }

    public TaskDecisionService(TaskRepository taskRepository,
                               IncidentFacade incidentFacade,
                               TaskOrchestrationProperties properties,
                               TaskAssignmentService taskAssignmentService,
                               IncidentTaskSuppressionPolicy suppressionPolicy) {
        this(taskRepository, incidentFacade, properties, taskAssignmentService, suppressionPolicy, (FlowRuleRoutingService) null, (RoutingProperties) null);
    }

    @Autowired
    public TaskDecisionService(TaskRepository taskRepository,
                               IncidentFacade incidentFacade,
                               TaskOrchestrationProperties properties,
                               TaskAssignmentService taskAssignmentService,
                               IncidentTaskSuppressionPolicy suppressionPolicy,
                               ObjectProvider<FlowRuleRoutingService> flowRuleRoutingService,
                               ObjectProvider<RoutingProperties> routingProperties) {
        this(taskRepository, incidentFacade, properties, taskAssignmentService, suppressionPolicy,
                flowRuleRoutingService == null ? null : flowRuleRoutingService.getIfAvailable(),
                routingProperties == null ? null : routingProperties.getIfAvailable());
    }

    public TaskDecisionService(TaskRepository taskRepository,
                               IncidentFacade incidentFacade,
                               TaskOrchestrationProperties properties,
                               TaskAssignmentService taskAssignmentService,
                               IncidentTaskSuppressionPolicy suppressionPolicy,
                               FlowRuleRoutingService flowRuleRoutingService) {
        this(taskRepository, incidentFacade, properties, taskAssignmentService, suppressionPolicy,
                flowRuleRoutingService, null);
    }

    public TaskDecisionService(TaskRepository taskRepository,
                               IncidentFacade incidentFacade,
                               TaskOrchestrationProperties properties,
                               TaskAssignmentService taskAssignmentService,
                               IncidentTaskSuppressionPolicy suppressionPolicy,
                               FlowRuleRoutingService flowRuleRoutingService,
                               RoutingProperties routingProperties) {
        this.taskRepository = taskRepository;
        this.incidentFacade = incidentFacade;
        this.properties = properties == null ? new TaskOrchestrationProperties() : properties;
        this.taskAssignmentService = taskAssignmentService;
        this.flowRuleRoutingService = flowRuleRoutingService;
        this.routingProperties = routingProperties == null ? new RoutingProperties() : routingProperties;
        this.suppressionPolicy = suppressionPolicy == null ? new IncidentTaskSuppressionPolicy() : suppressionPolicy;
    }

    public TaskDecisionResult decide(Incident incident, NormalizedEvent event, DedupDecision dedup) {
        if (!properties.isTaskCreationEnabled()) {
            log.info("task_decision_suppressed reason={} eventId={} tenantId={} sourceSystem={} eventStage={} eventType={} correlationId={}",
                    "TASK_CREATION_DISABLED", event == null ? null : event.eventId(), event == null ? null : event.tenantId(),
                    event == null ? null : event.sourceSystem(), event == null ? null : event.eventStage(),
                    event == null ? null : event.eventType(), event == null ? null : event.correlationId());
            return TaskDecisionResult.suppressed(
                    "Task creation is disabled by CORE_TASK_CREATION_ENABLED=false",
                    List.of(DecisionAction.TASK_RECOMMENDED_BUT_DISABLED, DecisionAction.SUPPRESS_NEW_TASK));
        }

        boolean unclassifiedEvent = isUnclassifiedEvent(event);
        if (!unclassifiedEvent && incident.getStatus() == IncidentStatus.ESCALATED && properties.isTaskEscalationEnabled()) {
            TaskDecisionResult escalation = maybeCreateEscalationTask(incident, event, dedup);
            if (escalation.taskCreated() || escalation.taskSuppressed()) {
                return escalation;
            }
        }

        boolean immediateSeverity = properties.getImmediateTaskSeverities().contains(event.severity().name());
        boolean reachedThreshold = dedup.state().getOccurrenceCount() >= properties.getTaskMinOccurrences();
        TaskType responseTaskType = unclassifiedEvent ? TaskType.TRIAGE : TaskType.INCIDENT_RESPONSE;
        Optional<TaskRecord> existingOpenResponseTask = taskRepository.findOpenByIncidentAndType(
                incident.getIncidentId(), responseTaskType);
        TaskSuppressionDecision suppression = suppressionPolicy.evaluateResponseTask(
                incident,
                event,
                dedup,
                immediateSeverity,
                reachedThreshold,
                existingOpenResponseTask,
                properties.getTaskMinOccurrences());
        if (suppression.suppressed()) {
            log.info("task_decision_suppressed reason={} incidentId={} eventId={} sourceSystem={} eventStage={} eventType={} occurrenceCount={}",
                    suppression.reason(), incident.getIncidentId(), event.eventId(), event.sourceSystem(), event.eventStage(), event.eventType(),
                    dedup.state().getOccurrenceCount());
            return TaskDecisionResult.suppressed(suppression.reason(), suppression.actions());
        }

        return createTask(incident, event, dedup, responseTaskType,
                unclassifiedEvent
                        ? "Source-system-only intake accepted: missing classification normalized to UNKNOWN; TRIAGE task created"
                        : (immediateSeverity ? "Immediate severity matched: " + event.severity() : "Occurrence threshold reached: " + dedup.state().getOccurrenceCount()),
                List.of(DecisionAction.CREATE_TASK));
    }

    private TaskDecisionResult maybeCreateEscalationTask(Incident incident, NormalizedEvent event, DedupDecision dedup) {
        TaskSuppressionDecision suppression = suppressionPolicy.evaluateEscalationTask(
                incident,
                event,
                taskRepository.findOpenByIncidentAndType(incident.getIncidentId(), TaskType.INCIDENT_ESCALATION));
        if (suppression.suppressed()) {
            log.info("task_escalation_suppressed reason={} incidentId={} eventId={} sourceSystem={} eventStage={} eventType={}",
                    suppression.reason(), incident.getIncidentId(), event.eventId(), event.sourceSystem(), event.eventStage(), event.eventType());
            return TaskDecisionResult.suppressed(suppression.reason(), suppression.actions());
        }
        return createTask(incident, event, dedup, TaskType.INCIDENT_ESCALATION,
                "Severity escalation detected for incident " + incident.getIncidentId(),
                List.of(DecisionAction.CREATE_ESCALATION_TASK, DecisionAction.CREATE_TASK));
    }

    private TaskDecisionResult createTask(Incident incident,
                                          NormalizedEvent event,
                                          DedupDecision dedup,
                                          TaskType taskType,
                                          String reason,
                                          List<DecisionAction> actions) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskContractResolution resolution = resolveTaskContract(event, taskType);
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-" + UUID.randomUUID());
        task.setIncidentId(incident.getIncidentId());
        task.setSourceEventId(event.eventId());
        task.setSourceSystem(firstNonBlank(resolution.sourceSystem(), event.sourceSystem()));
        task.setEventStage(event.eventStage());
        task.setOriginSourceSystem(event.originSourceSystem());
        task.setTargetSystem(event.targetSystem());
        task.setTaskType(taskType);
        task.setTaskTypeCode(firstNonBlank(resolution.taskTypeCode(), taskType == TaskType.TRIAGE ? "TRIAGE" : null));
        task.setStatus(TaskStatus.QUEUED);
        task.setPriority(TaskPriority.fromSeverity(event.severity()));
        task.setTenantId(event.tenantId());
        task.setSiteId(event.siteId());
        task.setPlantId(event.plantId());
        task.setObjectType(event.objectType());
        task.setObjectId(event.objectId());
        task.setEventType(event.eventType());
        task.setErrorCode(event.errorCode());
        task.setRequestedSkill(event.requestedSkill());
        task.setHandoffMode(event.handoffMode());
        task.setCorrelationId(event.correlationId());
        task.setParentTaskId(event.parentTaskId());
        task.setClassificationStatus(classificationStatusFor(event));
        task.setClassificationResultJson("{}");
        task.setRoutingPolicy(routingPolicyForEvent(event));
        task.setRoutingPath(taskType == TaskType.TRIAGE ? "SOURCE_FLOW_TRIAGE_PENDING" : "LEGACY_ROUTING_R3_ENVELOPE_ONLY");
        task.setRequiredCapabilities(resolution.requiredCapabilities());
        FlowRuleRoutingPlan flowRulePlan = resolveAndApplyFlowRulePlan(task);
        applyR9FormalRoutingGate(task, flowRulePlan);
        String resolvedReason = appendFlowRuleReason(appendResolutionReason(reason, resolution), flowRulePlan);
        task.setCreatedReason(resolvedReason);
        task.setLifecycleReason(resolvedReason);
        task.setOccurrenceCountAtCreation(dedup.state().getOccurrenceCount());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        log.info("task_create_attempt taskId={} incidentId={} sourceEventId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} taskType={} taskTypeCode={} classificationStatus={} requestedSkill={} matchedFlowId={} matchedRuleId={} targetPoolId={} routingPath={} correlationId={} parentTaskId={} requiredCapabilities={}",
                task.getTaskId(), task.getIncidentId(), task.getSourceEventId(), task.getSourceSystem(), task.getEventStage(), task.getEventType(),
                task.getObjectType(), task.getErrorCode(), task.getTaskType(), task.getTaskTypeCode(), task.getClassificationStatus(),
                task.getRequestedSkill(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getTargetPoolId(), task.getRoutingPath(), task.getCorrelationId(), task.getParentTaskId(), task.getRequiredCapabilities());
        TaskRecord saved = taskRepository.saveNewOrGetOpen(task);
        if (!saved.getTaskId().equals(task.getTaskId())) {
            log.info("task_create_idempotent_suppressed existingTaskId={} attemptedTaskId={} incidentId={} sourceEventId={} routingPath={} matchedFlowId={} matchedRuleId={}",
                    saved.getTaskId(), task.getTaskId(), incident.getIncidentId(), event.eventId(), saved.getRoutingPath(), saved.getMatchedFlowId(), saved.getMatchedRuleId());
            return TaskDecisionResult.suppressed(
                    "Incident already has open " + taskType + " task " + saved.getTaskId() + " (detected by repository idempotency guard)",
                    List.of(DecisionAction.SUPPRESS_DUPLICATE_TASK, DecisionAction.SUPPRESS_OPEN_INCIDENT_TASK, DecisionAction.SUPPRESS_NEW_TASK));
        }
        AssignmentDecisionResult assignment = decideDirectAssignment(saved);
        log.info("task_created taskId={} incidentId={} sourceEventId={} taskType={} taskTypeCode={} sourceSystem={} eventStage={} classificationStatus={} requestedSkill={} matchedFlowId={} matchedRuleId={} targetPoolId={} routingPath={} assignmentCreated={} assignmentId={} selectedAgentId={} assignmentStatus={}",
                saved.getTaskId(), saved.getIncidentId(), saved.getSourceEventId(), saved.getTaskType(), saved.getTaskTypeCode(), saved.getSourceSystem(),
                saved.getEventStage(), saved.getClassificationStatus(), saved.getRequestedSkill(), saved.getMatchedFlowId(), saved.getMatchedRuleId(), saved.getTargetPoolId(), saved.getRoutingPath(),
                assignment != null && assignment.assignmentCreated(), assignment == null ? null : assignment.assignmentId(),
                assignment == null ? null : assignment.selectedAgentId(), assignment == null ? null : assignment.assignmentStatus());
        if (taskType == TaskType.INCIDENT_RESPONSE && (incident.getLinkedTaskId() == null || incident.getLinkedTaskId().isBlank())) {
            incidentFacade.linkTaskIfAbsent(incident.getIncidentId(), saved.getTaskId());
        }
        return TaskDecisionResult.created(saved, saved.getCreatedReason(), actions, assignment);
    }


    private boolean isUnclassifiedEvent(NormalizedEvent event) {
        if (event == null) {
            return true;
        }
        return "UNKNOWN".equalsIgnoreCase(firstNonBlank(event.eventType(), "UNKNOWN"));
    }

    private String classificationStatusFor(NormalizedEvent event) {
        return isUnclassifiedEvent(event) ? "UNCLASSIFIED" : "CLASSIFIED";
    }

    private TaskContractResolution resolveTaskContract(NormalizedEvent event, TaskType baseTaskType) {
        LinkedHashSet<String> capabilityOverrides = new LinkedHashSet<>();
        addCapabilityOverrides(capabilityOverrides, attribute(event, "requiredCapabilities"));
        if (capabilityOverrides.isEmpty()) {
            addCapabilityOverrides(capabilityOverrides, attribute(event, "requiredCapability"));
        }
        String explicitTaskType = firstNonBlank(
                normalizedAttribute(event, "taskType"),
                normalizedAttribute(event, "taskTypeCode"),
                normalizedAttribute(event, "requestedTaskType"),
                normalizedAttribute(event, "dispatchTaskType"));

        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (!capabilityOverrides.isEmpty()) {
            capabilities.addAll(capabilityOverrides);
        }
        // Phase 11: task contract resolution no longer calls a side resolver. Dispatch Flow
        // owns matching and Required Capability selection. Event-supplied capability overrides
        // are preserved only as optional metadata until the Flow rule evaluates them.

        String resolvedTaskType = firstNonBlank(explicitTaskType, firstOf(capabilities));
        String resolvedSourceSystem = normalizeCode(event == null ? null : event.sourceSystem());
        List<String> reasons = new ArrayList<>();
        if (!capabilityOverrides.isEmpty()) {
            reasons.add("eventCapabilityOverride=" + capabilityOverrides);
        }
        if (!blank(explicitTaskType)) {
            reasons.add("eventTaskTypeOverride=" + explicitTaskType);
        }
        return new TaskContractResolution(resolvedSourceSystem, resolvedTaskType, new ArrayList<>(capabilities), reasons);
    }

    private FlowRuleRoutingPlan resolveAndApplyFlowRulePlan(TaskRecord task) {
        if (flowRuleRoutingService == null || task == null) {
            return null;
        }
        FlowRuleRoutingPlan plan = flowRuleRoutingService.resolve(task);
        if (plan != null && plan.isMatched()) {
            flowRuleRoutingService.applyToTask(task, plan);
        }
        return plan;
    }

    private void applyR9FormalRoutingGate(TaskRecord task, FlowRuleRoutingPlan plan) {
        if (task == null || plan == null || plan.isMatched() || routingProperties.isFlowRuleLegacyFallbackEnabled()) {
            return;
        }
        log.warn("task_flow_rule_gate_blocked taskId={} sourceSystem={} eventStage={} eventType={} requestedSkill={} matchedFlowId={} matchedRuleId={} routingPath={} reason={}",
                task.getTaskId(), task.getSourceSystem(), task.getEventStage(), task.getEventType(), task.getRequestedSkill(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRoutingPath(), plan.getReason());
        task.setRoutingPolicy("FLOW_RULE");
        task.setRoutingPath("FLOW_RULE_REQUIRED_BLOCKED");
        task.setRequestedSkill(firstNonBlank(task.getRequestedSkill(), firstOf(task.getRequiredCapabilities())));
        String reason = firstNonBlank(task.getLifecycleReason(), task.getCreatedReason(), "Task created");
        String blocked = reason + " | NO_ACTIVE_FLOW_RULE: an ACTIVE Dispatch Flow Rule and at least one Flow-selected approved Agent are required before assignment. "
                + "Capability is optional unless the matched Rule explicitly requires it.";
        task.setLifecycleReason(blocked);
        task.setCreatedReason(blocked);
    }

    private String appendFlowRuleReason(String reason, FlowRuleRoutingPlan plan) {
        if (plan == null) {
            return reason;
        }
        if (!plan.isMatched()) {
            return firstNonBlank(reason, "Task created")
                    + " | Flow Rule matching failed: " + firstNonBlank(plan.getReason(), "NO_ACTIVE_FLOW_RULE");
        }
        return firstNonBlank(reason, "Task created") + " | Flow Rule matched: flowId=" + plan.getFlowId()
                + "; ruleId=" + plan.getRuleId()
                + (blank(plan.getRequestedSkill()) ? "" : "; requiredCapability=" + plan.getRequestedSkill());
    }

    private String appendResolutionReason(String reason, TaskContractResolution resolution) {
        if (resolution == null) {
            return reason;
        }
        List<String> fragments = new ArrayList<>();
        if (!blank(resolution.taskTypeCode())) {
            fragments.add("taskTypeCode=" + resolution.taskTypeCode());
        }
        if (resolution.requiredCapabilities() != null && !resolution.requiredCapabilities().isEmpty()) {
            fragments.add("requiredCapabilities=" + resolution.requiredCapabilities());
        }
        if (resolution.resolutionReasons() != null && !resolution.resolutionReasons().isEmpty()) {
            List<String> standardReasons = resolution.resolutionReasons().stream()
                    .filter(item -> !contains(item, "NO_ACTIVE_FLOW_RULE"))
                    .toList();
            if (!standardReasons.isEmpty()) {
                fragments.add("resolution=" + standardReasons);
            }
        }
        if (fragments.isEmpty()) {
            return reason;
        }
        return firstNonBlank(reason, "Task created") + " | " + String.join("; ", fragments);
    }


    private String routingPolicyForEvent(NormalizedEvent event) {
        Object override = attribute(event, "routingPolicy");
        String value = override == null ? null : override.toString();
        if (value != null && !value.isBlank()) {
            return normalizeCode(value);
        }
        String configuredDefault = properties.getDefaultRoutingPolicy();
        return configuredDefault == null || configuredDefault.isBlank()
                ? "MANUAL_REVIEW"
                : normalizeCode(configuredDefault);
    }

    private List<String> requiredCapabilitiesForEvent(NormalizedEvent event) {
        return resolveTaskContract(event, TaskType.INCIDENT_RESPONSE).requiredCapabilities();
    }

    private Object attribute(NormalizedEvent event, String key) {
        return event == null || event.attributes() == null ? null : event.attributes().get(key);
    }

    private void addCapabilityOverrides(LinkedHashSet<String> target, Object raw) {
        if (target == null || raw == null) {
            return;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                addCapabilityOverrides(target, item);
            }
            return;
        }
        String text = raw.toString();
        if (text == null || text.isBlank()) {
            return;
        }
        for (String item : text.split(",")) {
            String normalized = normalizeCode(item);
            if (!normalized.isBlank()) {
                target.add(normalized);
            }
        }
    }

    private String normalizedAttribute(NormalizedEvent event, String key) {
        Object value = attribute(event, key);
        return value == null ? null : normalizeCode(value.toString());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstOf(Collection<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean contains(String value, String token) {
        return value != null && token != null && value.toUpperCase(Locale.ROOT).contains(token.toUpperCase(Locale.ROOT));
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private record TaskContractResolution(String sourceSystem, String taskTypeCode, List<String> requiredCapabilities, List<String> resolutionReasons) {}

    private AssignmentDecisionResult decideDirectAssignment(TaskRecord saved) {
        return taskAssignmentService == null
                ? AssignmentDecisionResult.none("Standard Dispatch Flow assignment service is unavailable")
                : taskAssignmentService.assignIfPossible(saved);
    }
}
