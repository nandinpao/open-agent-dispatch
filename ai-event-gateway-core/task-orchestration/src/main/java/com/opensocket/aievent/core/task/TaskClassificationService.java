package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.event.EventSeverity;

/**
 * Phase 32-E: applies TRIAGE Agent classification results and creates a
 * RESOLUTION child task that re-enters Source Flow / Agent Pool routing.
 */
@Service
public class TaskClassificationService {
    private static final Logger log = LoggerFactory.getLogger(TaskClassificationService.class);
    private static final String CLASSIFIED = "CLASSIFIED";
    private static final String UNCLASSIFIED = "UNCLASSIFIED";
    private static final String UNKNOWN = "UNKNOWN";

    private final TaskRepository taskRepository;
    private final TaskAssignmentService taskAssignmentService;
    private final TaskAssignmentRepository assignmentRepository;
    private final FlowRuleRoutingService flowRuleRoutingService;

    public TaskClassificationService(TaskRepository taskRepository,
                                     TaskAssignmentService taskAssignmentService,
                                     TaskAssignmentRepository assignmentRepository,
                                     FlowRuleRoutingService flowRuleRoutingService) {
        this.taskRepository = taskRepository;
        this.taskAssignmentService = taskAssignmentService;
        this.assignmentRepository = assignmentRepository;
        this.flowRuleRoutingService = flowRuleRoutingService;
    }

    @Transactional
    public TaskClassificationResult submitClassificationResult(String parentTaskId, TaskClassificationRequest request) {
        if (parentTaskId == null || parentTaskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        TaskRecord parent = taskRepository.findById(parentTaskId)
                .orElseThrow(() -> new IllegalArgumentException("TRIAGE task not found: " + parentTaskId));
        if (parent.getTaskType() != TaskType.TRIAGE) {
            throw new IllegalArgumentException("classification-result is only accepted for TRIAGE tasks: " + parentTaskId);
        }
        TaskClassificationRequest safe = request == null ? new TaskClassificationRequest() : request;
        String eventType = normalizeCode(firstNonBlank(safe.getEventType(), UNKNOWN));
        String classificationStatus = normalizeCode(firstNonBlank(safe.getClassificationStatus(), CLASSIFIED));
        if (CLASSIFIED.equals(classificationStatus) && UNKNOWN.equals(eventType)) {
            throw new IllegalArgumentException("eventType is required when classificationStatus=CLASSIFIED");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        parent.setClassificationStatus(classificationStatus);
        parent.setClassificationResultJson(classificationJson(parent, safe, eventType, classificationStatus));
        parent.setObjectType(normalizeCode(firstNonBlank(safe.getObjectType(), parent.getObjectType(), UNKNOWN)));
        parent.setEventType(eventType);
        parent.setErrorCode(normalizeCode(firstNonBlank(safe.getErrorCode(), parent.getErrorCode(), UNKNOWN)));
        parent.setUpdatedAt(now);
        parent.setTerminalAt(CLASSIFIED.equals(classificationStatus) ? now : parent.getTerminalAt());
        parent.setStatus(CLASSIFIED.equals(classificationStatus) ? TaskStatus.SUCCEEDED : TaskStatus.RETRY_WAIT);
        parent.setLifecycleReason(CLASSIFIED.equals(classificationStatus)
                ? "TRIAGE classification completed; resolution task will be created if requested"
                : "TRIAGE classification remains " + classificationStatus);
        taskRepository.save(parent);
        releaseParentTriageCapacity(parent);

        if (!CLASSIFIED.equals(classificationStatus) || !safe.shouldCreateResolutionTask()) {
            log.info("task_classification_result_applied parentTaskId={} classificationStatus={} resolutionCreated=false eventType={}",
                    parent.getTaskId(), classificationStatus, eventType);
            return TaskClassificationResult.of(parent, null, false, AssignmentDecisionResult.none("Resolution task creation skipped"));
        }

        ExistingResolution existing = findExistingResolutionChild(parent);
        if (existing.task() != null) {
            log.info("task_classification_result_idempotent parentTaskId={} existingResolutionTaskId={} eventType={} targetPoolId={}",
                    parent.getTaskId(), existing.task().getTaskId(), existing.task().getEventType(), existing.task().getTargetPoolId());
            return TaskClassificationResult.of(parent, existing.task(), false, AssignmentDecisionResult.none("Resolution child task already exists"));
        }

        TaskRecord resolution = createResolutionTask(parent, safe, eventType, now);
        FlowRuleRoutingPlan plan = resolveAndApplyFlowRulePlan(resolution);
        resolution.setCreatedReason(appendRoutingReason(resolution.getCreatedReason(), plan));
        resolution.setLifecycleReason(resolution.getCreatedReason());
        TaskRecord savedResolution = taskRepository.save(resolution);
        AssignmentDecisionResult assignment = taskAssignmentService == null
                ? AssignmentDecisionResult.none("Assignment service unavailable")
                : taskAssignmentService.assignIfPossible(savedResolution);
        log.info("task_classification_result_resolution_created parentTaskId={} resolutionTaskId={} eventType={} matchedFlowId={} matchedRuleId={} targetPoolId={} assignmentCreated={} assignmentId={} selectedAgentId={}",
                parent.getTaskId(), savedResolution.getTaskId(), savedResolution.getEventType(), savedResolution.getMatchedFlowId(),
                savedResolution.getMatchedRuleId(), savedResolution.getTargetPoolId(), assignment.assignmentCreated(), assignment.assignmentId(), assignment.selectedAgentId());
        return TaskClassificationResult.of(parent, savedResolution, true, assignment);
    }


    private void releaseParentTriageCapacity(TaskRecord parent) {
        if (parent == null || assignmentRepository == null || taskAssignmentService == null) {
            return;
        }
        assignmentRepository.findOpenByTaskId(parent.getTaskId()).ifPresent(assignment -> {
            boolean released = taskAssignmentService.releaseCapacityReservation(assignment.getAssignmentId());
            log.info("task_classification_parent_capacity_release parentTaskId={} assignmentId={} released={}",
                    parent.getTaskId(), assignment.getAssignmentId(), released);
        });
    }

    private TaskRecord createResolutionTask(TaskRecord parent, TaskClassificationRequest request, String eventType, OffsetDateTime now) {
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-" + UUID.randomUUID());
        task.setIncidentId(parent.getIncidentId());
        task.setSourceEventId(parent.getSourceEventId());
        task.setSourceSystem(normalizeCode(firstNonBlank(request.getSourceSystem(), parent.getSourceSystem())));
        task.setEventStage(parent.getEventStage());
        task.setOriginSourceSystem(parent.getOriginSourceSystem());
        task.setTargetSystem(parent.getTargetSystem());
        task.setTaskType(TaskType.RESOLUTION);
        task.setTaskTypeCode("RESOLUTION");
        task.setStatus(TaskStatus.QUEUED);
        task.setPriority(TaskPriority.fromSeverity(parseSeverity(firstNonBlank(request.getSeverity(), parent.getPriority() == null ? null : parent.getPriority().name()))));
        task.setTenantId(parent.getTenantId());
        task.setSiteId(parent.getSiteId());
        task.setPlantId(parent.getPlantId());
        task.setObjectType(normalizeCode(firstNonBlank(request.getObjectType(), parent.getObjectType(), UNKNOWN)));
        task.setObjectId(parent.getObjectId());
        task.setEventType(eventType);
        task.setErrorCode(normalizeCode(firstNonBlank(request.getErrorCode(), parent.getErrorCode(), UNKNOWN)));
        task.setRequestedSkill(null);
        task.setHandoffMode(parent.getHandoffMode());
        task.setCorrelationId(parent.getCorrelationId());
        task.setParentTaskId(parent.getTaskId());
        task.setClassificationStatus(CLASSIFIED);
        task.setClassificationResultJson(parent.getClassificationResultJson());
        task.setRoutingPolicy("SOURCE_FLOW");
        task.setRoutingPath("SOURCE_FLOW_RESOLUTION_PENDING");
        task.setRequiredCapabilities(List.of());
        task.setCreatedReason("Resolution child task created from TRIAGE classification result parentTaskId=" + parent.getTaskId());
        task.setLifecycleReason(task.getCreatedReason());
        task.setOccurrenceCountAtCreation(Math.max(1, parent.getOccurrenceCountAtCreation()));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
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

    private String appendRoutingReason(String reason, FlowRuleRoutingPlan plan) {
        if (plan == null) {
            return reason;
        }
        if (!plan.isMatched()) {
            return firstNonBlank(reason, "Resolution task created")
                    + " | Source Flow routing failed: " + firstNonBlank(plan.getReason(), "SOURCE_FLOW_NOT_FOUND");
        }
        return firstNonBlank(reason, "Resolution task created")
                + " | Source Flow routed: flowId=" + plan.getFlowId()
                + "; ruleId=" + plan.getRuleId()
                + "; targetPoolId=" + plan.getTargetPoolId()
                + (plan.isSourceDefaultPool() ? "; sourceDefaultPool=true" : "");
    }

    private ExistingResolution findExistingResolutionChild(TaskRecord parent) {
        if (parent == null || parent.getIncidentId() == null || parent.getIncidentId().isBlank()) {
            return new ExistingResolution(null);
        }
        return taskRepository.findByIncidentId(parent.getIncidentId(), 1000).stream()
                .filter(task -> parent.getTaskId().equals(task.getParentTaskId()))
                .filter(task -> task.getTaskType() == TaskType.RESOLUTION)
                .findFirst()
                .map(ExistingResolution::new)
                .orElseGet(() -> new ExistingResolution(null));
    }

    private String classificationJson(TaskRecord parent, TaskClassificationRequest request, String eventType, String classificationStatus) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendJson(json, "phase", "32-E");
        appendJson(json, "parentTaskId", parent.getTaskId());
        appendJson(json, "classificationStatus", classificationStatus);
        appendJson(json, "sourceSystem", firstNonBlank(request.getSourceSystem(), parent.getSourceSystem()));
        appendJson(json, "objectType", firstNonBlank(request.getObjectType(), parent.getObjectType(), UNKNOWN));
        appendJson(json, "eventType", eventType);
        appendJson(json, "errorCode", firstNonBlank(request.getErrorCode(), parent.getErrorCode(), UNKNOWN));
        appendJson(json, "severity", request.getSeverity());
        appendJson(json, "recommendedPoolCode", request.getRecommendedPoolCode());
        appendJson(json, "reason", request.getReason());
        if (request.getConfidence() != null) {
            appendCommaIfNeeded(json);
            json.append('"').append("confidence").append('"').append(':').append(Math.max(0.0, Math.min(1.0, request.getConfidence())));
        }
        appendCommaIfNeeded(json);
        json.append('"').append("createResolutionTask").append('"').append(':').append(request.shouldCreateResolutionTask());
        json.append('}');
        return json.toString();
    }

    private void appendJson(StringBuilder json, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        appendCommaIfNeeded(json);
        json.append('"').append(escape(key)).append('"').append(':').append('"').append(escape(value)).append('"');
    }

    private void appendCommaIfNeeded(StringBuilder json) {
        if (json.length() > 1 && json.charAt(json.length() - 1) != '{') {
            json.append(',');
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private EventSeverity parseSeverity(String value) {
        return EventSeverity.parse(value);
    }

    private String normalizeCode(String value) {
        return value == null || value.isBlank() ? "" : value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }


    private record ExistingResolution(TaskRecord task) {}
}
