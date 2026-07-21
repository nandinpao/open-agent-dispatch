package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * Phase 9F A2A Classification Flow service.
 *
 * <p>Classification Agents submit results only. Core validates parent / root /
 * correlation / depth / cycle / idempotency guardrails and remains the only
 * authority allowed to create child or continuation Tasks.</p>
 */
@Service
public class TaskClassificationService {
    private static final Logger log = LoggerFactory.getLogger(TaskClassificationService.class);
    private static final String CLASSIFIED = "CLASSIFIED";
    private static final String UNCLASSIFIED = "UNCLASSIFIED";
    private static final String CLASSIFICATION_FAILED = "CLASSIFICATION_FAILED";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String DEFAULT_CLASSIFICATION_VERSION = "A2A_CLASSIFICATION_V1";
    private static final int DEFAULT_MAX_A2A_DEPTH = 3;

    private final TaskRepository taskRepository;
    private final TaskAssignmentService taskAssignmentService;
    private final TaskAssignmentRepository assignmentRepository;
    private final FlowRuleRoutingService flowRuleRoutingService;
    private final Map<String, String> a2aClassificationIdempotencyIndex = new ConcurrentHashMap<>();

    public TaskClassificationService(TaskRepository taskRepository,
                                     TaskAssignmentService taskAssignmentService,
                                     TaskAssignmentRepository assignmentRepository,
                                     FlowRuleRoutingService flowRuleRoutingService) {
        this.taskRepository = taskRepository;
        this.taskAssignmentService = taskAssignmentService;
        this.assignmentRepository = assignmentRepository;
        this.flowRuleRoutingService = flowRuleRoutingService;
    }

    public TaskA2AClassificationFlowContract a2aClassificationFlowContract() {
        return new TaskA2AClassificationFlowContract();
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
        A2AGuard guard = validateA2AGuard(parent, safe);
        String eventType = normalizeCode(firstNonBlank(safe.getEventType(), UNKNOWN));
        String classificationStatus = normalizeCode(firstNonBlank(safe.getClassificationStatus(), CLASSIFIED));
        if (CLASSIFIED.equals(classificationStatus) && UNKNOWN.equals(eventType)) {
            throw new IllegalArgumentException("eventType is required when classificationStatus=CLASSIFIED");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        parent.setClassificationStatus(classificationStatus);
        parent.setClassificationResultJson(classificationJson(parent, safe, eventType, classificationStatus, guard));
        parent.setObjectType(normalizeCode(firstNonBlank(safe.getObjectType(), parent.getObjectType(), UNKNOWN)));
        parent.setEventType(eventType);
        parent.setErrorCode(normalizeCode(firstNonBlank(safe.getErrorCode(), parent.getErrorCode(), UNKNOWN)));
        parent.setUpdatedAt(now);
        parent.setTerminalAt(CLASSIFIED.equals(classificationStatus) ? now : parent.getTerminalAt());
        parent.setStatus(CLASSIFIED.equals(classificationStatus) ? TaskStatus.SUCCEEDED : TaskStatus.RETRY_WAIT);
        parent.setLifecycleReason(CLASSIFIED.equals(classificationStatus)
                ? "A2A classification completed; Core will create child task if requested and guardrails pass"
                : "A2A classification requires manual review: " + classificationStatus);
        taskRepository.save(parent);
        releaseParentTriageCapacity(parent);

        if (!CLASSIFIED.equals(classificationStatus) || !safe.shouldCreateResolutionTask()) {
            log.info("task_a2a_classification_result_applied parentTaskId={} rootTaskId={} classificationStatus={} childCreated=false eventType={} nextAction={}",
                    parent.getTaskId(), guard.rootTaskId(), classificationStatus, eventType, guard.nextActionFor(classificationStatus, false));
            return TaskClassificationResult.of(parent, null, false, AssignmentDecisionResult.none("Child task creation skipped"),
                    guard.rootTaskId(), guard.correlationId(), guard.classificationVersion(), guard.idempotencyKey(), guard.depth(), guard.maxDepth(),
                    guard.cycleDetected(), true, "MANUAL_REVIEW", "Classification status is " + classificationStatus + " or child creation disabled");
        }

        ExistingResolution existing = findExistingResolutionChild(parent, guard);
        if (existing.task() != null) {
            log.info("task_a2a_classification_result_idempotent parentTaskId={} rootTaskId={} existingChildTaskId={} idempotencyKey={} eventType={} targetPoolId={}",
                    parent.getTaskId(), guard.rootTaskId(), existing.task().getTaskId(), guard.idempotencyKey(), existing.task().getEventType(), existing.task().getTargetPoolId());
            return TaskClassificationResult.of(parent, existing.task(), false, AssignmentDecisionResult.none("A2A child task already exists for idempotency guard"),
                    guard.rootTaskId(), guard.correlationId(), guard.classificationVersion(), guard.idempotencyKey(), guard.depth(), guard.maxDepth(),
                    false, false, "RETURN_EXISTING_CHILD_TASK", "Idempotent classification result; Core did not create a duplicate child task");
        }

        TaskRecord resolution = createResolutionTask(parent, safe, eventType, now, guard);
        FlowRuleRoutingPlan plan = resolveAndApplyFlowRulePlan(resolution);
        resolution.setCreatedReason(appendRoutingReason(resolution.getCreatedReason(), plan));
        resolution.setLifecycleReason(resolution.getCreatedReason());
        TaskRecord savedResolution = taskRepository.save(resolution);
        rememberIdempotency(parent, guard, savedResolution);
        AssignmentDecisionResult assignment = taskAssignmentService == null
                ? AssignmentDecisionResult.none("Assignment service unavailable")
                : taskAssignmentService.assignIfPossible(savedResolution);
        log.info("task_a2a_classification_child_created parentTaskId={} rootTaskId={} childTaskId={} classificationVersion={} idempotencyKey={} eventType={} matchedFlowId={} matchedRuleId={} targetPoolId={} assignmentCreated={} assignmentId={} selectedAgentId={}",
                parent.getTaskId(), guard.rootTaskId(), savedResolution.getTaskId(), guard.classificationVersion(), guard.idempotencyKey(), savedResolution.getEventType(), savedResolution.getMatchedFlowId(),
                savedResolution.getMatchedRuleId(), savedResolution.getTargetPoolId(), assignment.assignmentCreated(), assignment.assignmentId(), assignment.selectedAgentId());
        return TaskClassificationResult.of(parent, savedResolution, true, assignment,
                guard.rootTaskId(), guard.correlationId(), guard.classificationVersion(), guard.idempotencyKey(), guard.depth(), guard.maxDepth(),
                false, false, "ASSIGN_CHILD_TASK", "Core validated classification result and created the child task");
    }

    private A2AGuard validateA2AGuard(TaskRecord parent, TaskClassificationRequest request) {
        String requestParentTaskId = trimOptional(request.getParentTaskId());
        if (!blank(requestParentTaskId) && !parent.getTaskId().equals(requestParentTaskId)) {
            throw new IllegalArgumentException("parentTaskId in request must match path taskId");
        }
        String rootTaskId = firstNonBlank(trimOptional(request.getRootTaskId()), resolveRootTaskId(parent));
        String correlationId = firstNonBlank(trimOptional(request.getCorrelationId()), parent.getCorrelationId(), "a2a-" + rootTaskId);
        String classificationVersion = firstNonBlank(trimOptional(request.getClassificationVersion()), DEFAULT_CLASSIFICATION_VERSION);
        String idempotencyKey = firstNonBlank(trimOptional(request.getIdempotencyKey()),
                rootTaskId + ":" + parent.getTaskId() + ":" + classificationVersion + ":" + firstNonBlank(request.getEventType(), UNKNOWN));
        int maxDepth = request.getMaxA2ADepth() == null ? DEFAULT_MAX_A2A_DEPTH : Math.max(1, Math.min(10, request.getMaxA2ADepth()));
        DepthResult depth = computeDepthAndDetectCycle(parent, maxDepth);
        if (depth.cycleDetected()) {
            throw new IllegalArgumentException("A2A cycle detected for parentTaskId=" + parent.getTaskId());
        }
        if (depth.depth() >= maxDepth) {
            throw new IllegalArgumentException("maxA2ADepth exceeded for parentTaskId=" + parent.getTaskId() + "; depth=" + depth.depth() + "; max=" + maxDepth);
        }
        if (!blank(parent.getCorrelationId()) && !blank(request.getCorrelationId()) && !parent.getCorrelationId().equals(request.getCorrelationId())) {
            throw new IllegalArgumentException("correlationId in request must match parent task correlationId");
        }
        return new A2AGuard(rootTaskId, correlationId, classificationVersion, idempotencyKey, depth.depth(), maxDepth, false);
    }

    private DepthResult computeDepthAndDetectCycle(TaskRecord parent, int maxDepth) {
        int depth = 0;
        Set<String> visited = new HashSet<>();
        TaskRecord cursor = parent;
        while (cursor != null && !blank(cursor.getParentTaskId())) {
            if (!visited.add(cursor.getTaskId())) {
                return new DepthResult(depth, true);
            }
            if (cursor.getParentTaskId().equals(parent.getTaskId())) {
                return new DepthResult(depth, true);
            }
            depth++;
            if (depth > maxDepth + 1) {
                return new DepthResult(depth, true);
            }
            cursor = taskRepository.findById(cursor.getParentTaskId()).orElse(null);
        }
        return new DepthResult(depth, false);
    }

    private String resolveRootTaskId(TaskRecord parent) {
        TaskRecord cursor = parent;
        Set<String> visited = new HashSet<>();
        while (cursor != null && !blank(cursor.getParentTaskId()) && visited.add(cursor.getTaskId())) {
            cursor = taskRepository.findById(cursor.getParentTaskId()).orElse(null);
        }
        return cursor == null || blank(cursor.getTaskId()) ? parent.getTaskId() : cursor.getTaskId();
    }

    private void releaseParentTriageCapacity(TaskRecord parent) {
        if (parent == null || assignmentRepository == null || taskAssignmentService == null) {
            return;
        }
        assignmentRepository.findOpenByTaskId(parent.getTaskId()).ifPresent(assignment -> {
            boolean released = taskAssignmentService.releaseCapacityReservation(assignment.getAssignmentId());
            log.info("task_a2a_classification_parent_capacity_release parentTaskId={} assignmentId={} released={}",
                    parent.getTaskId(), assignment.getAssignmentId(), released);
        });
    }

    private TaskRecord createResolutionTask(TaskRecord parent, TaskClassificationRequest request, String eventType, OffsetDateTime now, A2AGuard guard) {
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
        task.setCorrelationId(guard.correlationId());
        task.setParentTaskId(parent.getTaskId());
        task.setClassificationStatus(CLASSIFIED);
        task.setClassificationResultJson(parent.getClassificationResultJson());
        task.setRoutingPolicy("SOURCE_FLOW");
        task.setRoutingPath("SOURCE_FLOW_RESOLUTION_PENDING");
        task.setRequiredCapabilities(List.of());
        task.setCreatedReason("A2A child task created by Core from classification result parentTaskId=" + parent.getTaskId()
                + "; rootTaskId=" + guard.rootTaskId()
                + "; classificationVersion=" + guard.classificationVersion()
                + "; idempotencyKey=" + guard.idempotencyKey()
                + "; agentCreatedTask=false; coreOwnedTaskCreation=true");
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
            return firstNonBlank(reason, "A2A child task created")
                    + " | Source Flow routing failed: " + firstNonBlank(plan.getReason(), "SOURCE_FLOW_NOT_FOUND");
        }
        return firstNonBlank(reason, "A2A child task created")
                + " | Source Flow routed: flowId=" + plan.getFlowId()
                + "; ruleId=" + plan.getRuleId()
                + "; targetPoolId=" + plan.getTargetPoolId()
                + (plan.isSourceDefaultPool() ? "; sourceDefaultPool=true" : "");
    }

    private ExistingResolution findExistingResolutionChild(TaskRecord parent, A2AGuard guard) {
        String cachedTaskId = a2aClassificationIdempotencyIndex.get(idempotencyIndexKey(parent, guard));
        if (!blank(cachedTaskId)) {
            TaskRecord cached = taskRepository.findById(cachedTaskId).orElse(null);
            if (cached != null) {
                return new ExistingResolution(cached);
            }
        }
        if (parent == null || parent.getIncidentId() == null || parent.getIncidentId().isBlank()) {
            return new ExistingResolution(null);
        }
        return taskRepository.findByIncidentId(parent.getIncidentId(), 1000).stream()
                .filter(task -> parent.getTaskId().equals(task.getParentTaskId()))
                .filter(task -> task.getTaskType() == TaskType.RESOLUTION)
                .filter(task -> blank(guard.idempotencyKey()) || task.getClassificationResultJson() != null && task.getClassificationResultJson().contains(escape(guard.idempotencyKey())))
                .findFirst()
                .map(ExistingResolution::new)
                .orElseGet(() -> new ExistingResolution(null));
    }

    private void rememberIdempotency(TaskRecord parent, A2AGuard guard, TaskRecord child) {
        if (parent == null || child == null || blank(guard.idempotencyKey())) {
            return;
        }
        a2aClassificationIdempotencyIndex.put(idempotencyIndexKey(parent, guard), child.getTaskId());
    }

    private String idempotencyIndexKey(TaskRecord parent, A2AGuard guard) {
        return guard.rootTaskId() + "::" + parent.getTaskId() + "::" + guard.classificationVersion() + "::" + guard.idempotencyKey();
    }

    private String classificationJson(TaskRecord parent, TaskClassificationRequest request, String eventType, String classificationStatus, A2AGuard guard) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendJson(json, "model", "A2A_CLASSIFICATION_FLOW");
        appendJson(json, "classificationVersion", guard.classificationVersion());
        appendJson(json, "parentTaskId", parent.getTaskId());
        appendJson(json, "rootTaskId", guard.rootTaskId());
        appendJson(json, "correlationId", guard.correlationId());
        appendJson(json, "idempotencyKey", guard.idempotencyKey());
        appendJson(json, "classificationStatus", classificationStatus);
        appendJson(json, "sourceSystem", firstNonBlank(request.getSourceSystem(), parent.getSourceSystem()));
        appendJson(json, "objectType", firstNonBlank(request.getObjectType(), parent.getObjectType(), UNKNOWN));
        appendJson(json, "eventType", eventType);
        appendJson(json, "errorCode", firstNonBlank(request.getErrorCode(), parent.getErrorCode(), UNKNOWN));
        appendJson(json, "severity", request.getSeverity());
        appendJson(json, "recommendedPoolCode", request.getRecommendedPoolCode());
        appendJson(json, "reason", request.getReason());
        appendJson(json, "childTaskCreationAuthority", "CORE_ONLY");
        if (request.getConfidence() != null) {
            appendCommaIfNeeded(json);
            json.append('"').append("confidence").append('"').append(':').append(Math.max(0.0, Math.min(1.0, request.getConfidence())));
        }
        appendCommaIfNeeded(json);
        json.append('"').append("createResolutionTask").append('"').append(':').append(request.shouldCreateResolutionTask());
        appendCommaIfNeeded(json);
        json.append('"').append("coreOwnedTaskCreation").append('"').append(':').append(true);
        appendCommaIfNeeded(json);
        json.append('"').append("agentCreatedTask").append('"').append(':').append(false);
        appendCommaIfNeeded(json);
        json.append('"').append("cycleDetection").append('"').append(':').append(!guard.cycleDetected());
        appendCommaIfNeeded(json);
        json.append('"').append("a2aDepth").append('"').append(':').append(guard.depth());
        appendCommaIfNeeded(json);
        json.append('"').append("maxA2ADepth").append('"').append(':').append(guard.maxDepth());
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

    private String trimOptional(String value) {
        return value == null ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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

    private record DepthResult(int depth, boolean cycleDetected) {}
    private record A2AGuard(String rootTaskId,
                            String correlationId,
                            String classificationVersion,
                            String idempotencyKey,
                            int depth,
                            int maxDepth,
                            boolean cycleDetected) {
        String nextActionFor(String classificationStatus, boolean childCreated) {
            if (childCreated) return "ASSIGN_CHILD_TASK";
            return CLASSIFIED.equals(classificationStatus) ? "NO_CHILD_TASK" : "MANUAL_REVIEW";
        }
    }
    private record ExistingResolution(TaskRecord task) {}
}
