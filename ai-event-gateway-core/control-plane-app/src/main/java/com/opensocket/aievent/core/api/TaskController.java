package com.opensocket.aievent.core.api;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotService;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotResponse;
import com.opensocket.aievent.core.enforcement.activation.runtime.TaskRecordVisibilityProjector;
import com.opensocket.aievent.core.enforcement.activation.runtime.TaskSearchCursorCodec;
import com.opensocket.aievent.core.enforcement.activation.runtime.Wave0TaskListSearchPilotAdapter;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;
import com.opensocket.aievent.core.lifecycle.TaskLifecycleService;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecisionMode;
import com.opensocket.aievent.core.resourceaccess.contract.OperationPhase;
import com.opensocket.aievent.core.resourceaccess.contract.RequestChannel;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementMode;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceEnforcementCommand;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.SecurityEpoch;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlanPort;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryAuditPort;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

/** Current Task operational API with P4RA-E shadow/read enforcement. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private final TaskOperationalQuery queryService;
    private final TaskLifecycleService lifecycleService;

    @Autowired(required = false)
    private ResourceAccessEnforcementPort resourceAccess;
    @Autowired(required = false)
    private TaskScopeQueryPlanPort taskScopePlans;
    @Autowired(required = false)
    private TaskScopeQueryAuditPort taskScopeAudit;
    @Autowired(required = false)
    private Wave0TaskListSearchPilotAdapter taskListPilot;
    @Autowired(required = false)
    private TaskRecordVisibilityProjector taskProjector;
    private final TaskSearchCursorCodec cursorCodec = new TaskSearchCursorCodec();
    @Value("${resource-access.task-enabled:false}")
    private boolean taskResourceAccessEnabled;
    @Value("${resource-access.enforcement-mode:OFF}")
    private ResourceAccessEnforcementMode enforcementMode = ResourceAccessEnforcementMode.OFF;

    public TaskController(TaskOperationalQuery queryService, TaskLifecycleService lifecycleService) {
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    public List<TaskRecord> search(@RequestParam(required = false) String incidentId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) TaskType taskType,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletResponse response) {
        String requestTenantId = tenantId();
        if (tenantId != null && !tenantId.isBlank() && !requestTenantId.equals(tenantId.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The requested tenant does not match the authenticated Tenant context.");
        }
        TaskQuery query = query(incidentId, requestTenantId, siteId, plantId, taskType, status, limit);
        applyCursor(cursor, query);
        return pilotOrScopedSearch(query, "TASK_LIST", response);
    }

    @GetMapping("/{taskId}")
    public TaskRecord get(@PathVariable String taskId) {
        TaskRecord task = requireTenantTask(taskId);
        AuthorizationDecision decision = authorize(taskId, "task.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.SUMMARY, "TASK_DETAIL_READ");
        return shouldApplyReadDecision(decision) ? redact(task, decision.grantedVisibility()) : task;
    }

    @GetMapping("/incident/{incidentId}")
    public List<TaskRecord> byIncident(@PathVariable String incidentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletResponse response) {
        TaskQuery query = query(incidentId, tenantId(), null, null, null, null, limit);
        applyCursor(cursor, query);
        return pilotOrScopedSearch(query, "TASK_INCIDENT_LIST", response);
    }

    @PostMapping("/{taskId}/timeout")
    public TaskRecord timeout(@PathVariable String taskId, @RequestBody(required = false) LifecycleRequest request) {
        requireTenantTask(taskId);
        authorize(taskId, "task.update", ResourceAction.ActionKind.UPDATE, true,
                VisibilityLevel.STANDARD, "TASK_TIMEOUT");
        return lifecycleService.timeout(taskId, request == null ? null : request.reason());
    }

    @PostMapping("/{taskId}/cancel")
    public TaskRecord cancel(@PathVariable String taskId, @RequestBody(required = false) LifecycleRequest request) {
        requireTenantTask(taskId);
        authorize(taskId, "task.update", ResourceAction.ActionKind.UPDATE, true,
                VisibilityLevel.STANDARD, "TASK_CANCEL");
        return lifecycleService.cancel(taskId, request == null ? null : request.reason());
    }

    @PostMapping("/{taskId}/reassign")
    public TaskRecord reassign(@PathVariable String taskId, @RequestBody(required = false) LifecycleRequest request) {
        requireTenantTask(taskId);
        authorize(taskId, "task.update", ResourceAction.ActionKind.UPDATE, true,
                VisibilityLevel.SENSITIVE, "TASK_REASSIGN");
        return lifecycleService.reassign(taskId, request == null ? null : request.reason());
    }

    /** Background enforcement is deliberately deferred to P4RA-G. */
    @PostMapping("/lifecycle/process-timeouts")
    public LifecycleScanResult processTimeouts() {
        return lifecycleService.processTimeoutsAndReassignments();
    }

    @GetMapping("/metadata")
    public TaskMetadata metadata() {
        return new TaskMetadata(queryService.taskStoreMode(), TaskType.values(), TaskStatus.values(),
                TaskPriority.values());
    }

    private List<TaskRecord> pilotOrScopedSearch(TaskQuery query, String purpose, HttpServletResponse response) {
        if (taskListPilot == null) {
            List<TaskRecord> values = scopedSearch(query, purpose);
            setPaginationHeader(response, values, query.getLimit());
            return values;
        }
        Wave0ReadPilotResponse<Wave0TaskListSearchPilotAdapter.TaskListPayload> result =
                taskListPilot.search(query, pilotActor());
        response.setHeader("X-OpenDispatch-Authority-Revision", Long.toString(result.authorityDecision().revision()));
        response.setHeader("X-OpenDispatch-Authority-Mode", result.authorityDecision().mode().name());
        response.setHeader("X-OpenDispatch-Task-Read-Source", result.servedBy().name());
        response.setHeader("X-OpenDispatch-Task-Fallback", Boolean.toString(result.fallbackUsed()));
        if (result.observationId() != null) {
            response.setHeader("X-OpenDispatch-Pilot-Observation", result.observationId().toString());
        }
        List<TaskRecord> values = result.payload().tasks();
        setPaginationHeader(response, values, query.getLimit());
        return values;
    }

    private Wave0ReadPilotService.ActorContext pilotActor() {
        OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request context is required."));
        String actor = context.operatorId() == null || context.operatorId().isBlank() ? "unknown" : context.operatorId();
        String correlation = context.correlationId() == null || context.correlationId().isBlank()
                ? context.requestId() : context.correlationId();
        return new Wave0ReadPilotService.ActorContext(tenantId(), actor, actor, correlation);
    }

    private void applyCursor(String cursor, TaskQuery query) {
        try {
            cursorCodec.apply(cursor, query);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void setPaginationHeader(HttpServletResponse response, List<TaskRecord> values, int limit) {
        String next = cursorCodec.nextCursor(values, limit);
        if (!next.isBlank()) response.setHeader("X-Next-Cursor", next);
    }

    private List<TaskRecord> scopedSearch(TaskQuery query, String purpose) {
        if (!taskResourceAccessEnabled || enforcementMode == ResourceAccessEnforcementMode.OFF) {
            return queryService.searchTasks(query);
        }
        if (taskScopePlans == null) {
            if (enforcementMode == ResourceAccessEnforcementMode.SHADOW
                    || enforcementMode == ResourceAccessEnforcementMode.WRITE_ENFORCE) {
                log.error("resource_access_task_scope_planner_unavailable mode={} purpose={}", enforcementMode, purpose);
                return queryService.searchTasks(query);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resource Access Task scope planner is unavailable.");
        }
        TaskScopeQueryPlan plan = taskScopePlans.build("task.read", VisibilityLevel.SUMMARY, purpose);
        List<TaskRecord> scoped = queryService.searchTasks(query, plan);
        if (enforcementMode == ResourceAccessEnforcementMode.READ_ENFORCE
                || enforcementMode == ResourceAccessEnforcementMode.FULL_ENFORCE) {
            // Enforced reads execute only the scope-aware SQL query; no Tenant-wide legacy read occurs.
            return scoped.stream().map(task -> redact(task, plan.maximumVisibility())).toList();
        }
        // SHADOW and WRITE_ENFORCE preserve legacy read behavior while recording the scoped delta.
        List<TaskRecord> legacy = queryService.searchTasks(query);
        logShadowListMismatch(purpose, legacy, scoped, plan);
        return legacy;
    }

    private void logShadowListMismatch(String purpose, List<TaskRecord> legacy, List<TaskRecord> scoped,
            TaskScopeQueryPlan plan) {
        Set<String> legacyIds = legacy.stream().map(TaskRecord::getTaskId).collect(Collectors.toSet());
        Set<String> scopedIds = scoped.stream().map(TaskRecord::getTaskId).collect(Collectors.toSet());
        if (!legacyIds.equals(scopedIds)) {
            Set<String> legacyOnly = difference(legacyIds, scopedIds);
            Set<String> scopedOnly = difference(scopedIds, legacyIds);
            log.warn("resource_access_task_list_shadow_mismatch purpose={} tenant={} principal={} planHash={} legacyCount={} scopedCount={} legacyOnly={} scopedOnly={}",
                    purpose, plan.tenantId(), plan.principalId(), plan.planHash(), legacyIds.size(), scopedIds.size(),
                    legacyOnly, scopedOnly);
            if (taskScopeAudit != null) taskScopeAudit.recordShadowMismatch(plan, purpose, legacyOnly, scopedOnly, Instant.now());
        }
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(value -> !right.contains(value)).limit(50).collect(Collectors.toSet());
    }

    private AuthorizationDecision authorize(String taskId, String permission, ResourceAction.ActionKind kind,
            boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        if (!taskResourceAccessEnabled) return null;
        if (resourceAccess == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resource Access enforcement guard is unavailable.");
        }
        return resourceAccess.authorize(new ResourceEnforcementCommand(
                new ResourceAction(permission, kind, sideEffecting),
                new ResourceRef(tenantId(), ResourceType.TASK, taskId), visibility,
                RequestChannel.REST, purpose, OperationPhase.START, SecurityEpoch.ZERO, Map.of()));
    }

    private boolean shouldApplyReadDecision(AuthorizationDecision decision) {
        return decision != null && decision.mode() == AuthorizationDecisionMode.FORMAL
                && (enforcementMode == ResourceAccessEnforcementMode.READ_ENFORCE
                    || enforcementMode == ResourceAccessEnforcementMode.FULL_ENFORCE);
    }

    private TaskRecord redact(TaskRecord source, VisibilityLevel visibility) {
        TaskRecordVisibilityProjector projector = taskProjector == null
                ? new TaskRecordVisibilityProjector() : taskProjector;
        return projector.project(source, visibility);
    }

    private TaskQuery query(String incidentId, String tenantId, String siteId, String plantId,
            TaskType taskType, TaskStatus status, int limit) {
        TaskQuery query = new TaskQuery();
        query.setIncidentId(incidentId);
        query.setTenantId(tenantId);
        query.setSiteId(siteId);
        query.setPlantId(plantId);
        query.setTaskType(taskType);
        query.setStatus(status);
        query.setLimit(limit);
        return query;
    }

    private TaskRecord requireTenantTask(String taskId) {
        return queryService.findTask(tenantId(), taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Task not found in the current Tenant context: " + taskId));
    }

    private String tenantId() {
        OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Request context is required."));
        String tenantId = context.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required.");
        }
        return tenantId.trim();
    }

    public record LifecycleRequest(String reason) {}
    public record TaskMetadata(String storeMode, TaskType[] taskTypes, TaskStatus[] statuses,
            TaskPriority[] priorities) {}
}
