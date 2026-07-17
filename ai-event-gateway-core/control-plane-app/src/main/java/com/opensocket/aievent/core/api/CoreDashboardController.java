package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.AgentControlOperationalQuery;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentOperationalQuery;
import com.opensocket.aievent.core.incident.IncidentQuery;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.observability.OperationalSummaryService;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * P3A Core control-plane dashboard API.
 *
 * <p>These endpoints expose authoritative business/governance snapshots for Admin UI. They are
 * intentionally separate from Netty runtime endpoints: Core owns Agent trust, Task/Dispatch truth,
 * Incident state, and persisted security events, while Netty owns realtime connection data.</p>
 */
@RestController
@RequestMapping("/admin")
public class CoreDashboardController {
    private static final Logger log = LoggerFactory.getLogger(CoreDashboardController.class);

    private final IncidentOperationalQuery incidentQuery;
    private final TaskOperationalQuery taskQuery;
    private final ExecutionOperationalQuery executionQuery;
    private final AgentControlOperationalQuery agentQuery;
    private final AgentGovernanceService agentGovernanceService;
    private final OperationalSummaryService operationalSummaryService;

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

    public CoreDashboardController(
            IncidentOperationalQuery incidentQuery,
            TaskOperationalQuery taskQuery,
            ExecutionOperationalQuery executionQuery,
            AgentControlOperationalQuery agentQuery,
            AgentGovernanceService agentGovernanceService,
            OperationalSummaryService operationalSummaryService
    ) {
        this.incidentQuery = incidentQuery;
        this.taskQuery = taskQuery;
        this.executionQuery = executionQuery;
        this.agentQuery = agentQuery;
        this.agentGovernanceService = agentGovernanceService;
        this.operationalSummaryService = operationalSummaryService;
    }

    @GetMapping("/dashboard/snapshot")
    public CoreDashboardSnapshot snapshot(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = safeLimit(limit);
        return new CoreDashboardSnapshot(
                new CoreDashboardCounts(
                        incidentQuery.statusCounts(safeLimit),
                        taskQuery.taskStatusCounts(safeLimit),
                        executionQuery.dispatchStatusCounts(safeLimit),
                        agentQuery.agentStatusCounts(safeLimit),
                        agentQuery.gatewayStatusCounts(safeLimit)
                ),
                agentGovernanceSummary(safeLimit),
                recentIncidents(safeLimit),
                recentTasks(safeLimit),
                executionQuery.recentDispatchRequests(safeLimit),
                agentRuntimeView(safeLimit),
                agentGovernanceService.searchSecurityEvents(null, safeLimit),
                new CoreDashboardStores(
                        incidentQuery.incidentStoreMode(),
                        taskQuery.taskStoreMode(),
                        executionQuery.dispatchStoreMode(),
                        executionQuery.callbackStoreMode(),
                        agentQuery.agentStoreMode(),
                        agentQuery.gatewayStoreMode(),
                        agentGovernanceService.mode()
                ),
                operationalSummaryService.summary().getSloMetrics(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @GetMapping("/agents/runtime-view")
    public List<CoreAgentRuntimeView> agentRuntimeView(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = safeLimit(limit);
        List<AgentProfile> profiles = agentGovernanceService.searchProfiles(null, safeLimit);
        Map<String, AgentSnapshot> directoryByAgentId = agentDirectory(safeLimit);
        return profiles.stream()
                .map(profile -> CoreAgentRuntimeView.from(profile, directoryByAgentId.get(profile.getAgentId())))
                .toList();
    }

    @GetMapping("/tasks/runtime-view")
    public CoreTaskRuntimeView tasksRuntimeView(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = safeLimit(limit);
        List<TaskRecord> tasks = recentTasks(safeLimit);
        Map<String, Integer> taskStatusCounts = taskQuery.taskStatusCounts(safeLimit);
        Map<String, Integer> dispatchStatusCounts = executionQuery.dispatchStatusCounts(safeLimit);
        List<DispatchRequest> dispatchRequests = executionQuery.recentDispatchRequests(safeLimit);
        Map<String, TaskIssueLink> issueLinks = taskIssueLinksByTask(tasks);
        logTaskRuntimeViewDiagnostics(tasks, dispatchRequests, issueLinks, taskStatusCounts, dispatchStatusCounts, safeLimit);
        return new CoreTaskRuntimeView(
                tasks,
                dispatchRequests,
                executionQuery.recentCallbacks(safeLimit),
                taskStatusCounts,
                dispatchStatusCounts,
                latestRoutingDecisionsByTask(tasks),
                issueLinks,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @GetMapping("/security-events")
    public List<AgentSecurityEvent> securityEvents(@RequestParam(required = false) String agentId,
                                                   @RequestParam(defaultValue = "100") int limit) {
        return agentGovernanceService.searchSecurityEvents(agentId, safeLimit(limit));
    }

    @GetMapping("/agent-governance/summary")
    public CoreAgentGovernanceSummary agentGovernanceSummary(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = safeLimit(limit);
        return new CoreAgentGovernanceSummary(
                agentGovernanceService.searchProfiles(null, safeLimit).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                profile -> profile.getApprovalStatus() == null ? "UNKNOWN" : profile.getApprovalStatus().name(),
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.counting()
                        )),
                agentGovernanceService.searchProfiles(AgentApprovalStatus.APPROVED, safeLimit).stream().filter(AgentProfile::isEnabled).count(),
                agentGovernanceService.searchEnrollments(null, safeLimit).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                enrollment -> enrollment.getStatus() == null ? "UNKNOWN" : enrollment.getStatus().name(),
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.counting()
                        )),
                agentGovernanceService.searchSecurityEvents(null, safeLimit).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                event -> event.getEventType() == null ? "UNKNOWN" : event.getEventType().name(),
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.counting()
                        )),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private Map<String, AgentSnapshot> agentDirectory(int limit) {
        AgentQuery query = new AgentQuery();
        query.setLimit(limit);
        return agentQuery.searchAgents(query).stream()
                .collect(java.util.stream.Collectors.toMap(
                        AgentSnapshot::getAgentId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    private List<Incident> recentIncidents(int limit) {
        IncidentQuery query = new IncidentQuery();
        query.setLimit(limit);
        return incidentQuery.search(query);
    }


    private void logTaskRuntimeViewDiagnostics(List<TaskRecord> tasks,
                                               List<DispatchRequest> dispatchRequests,
                                               Map<String, TaskIssueLink> issueLinks,
                                               Map<String, Integer> taskStatusCounts,
                                               Map<String, Integer> dispatchStatusCounts,
                                               int safeLimit) {
        List<TaskRecord> needsAction = tasks.stream().filter(this::looksLikeNeedsActionInTaskQueue).toList();
        List<TaskRecord> waiting = tasks.stream().filter(task -> !looksLikeNeedsActionInTaskQueue(task) && looksLikeWaitingInTaskQueue(task)).toList();
        long done = tasks.stream().filter(this::looksLikeDoneInTaskQueue).count();
        long closed = tasks.stream().filter(this::looksLikeClosedInTaskQueue).count();
        Map<String, DispatchRequest> latestDispatchByTask = latestDispatchByTask(dispatchRequests);
        long terminalWithHistoricalDispatchProblem = tasks.stream()
                .filter(task -> looksLikeTerminalWithHistoricalDispatchProblem(task, latestDispatchByTask.get(task.getTaskId())))
                .count();
        log.info("task_runtime_view_loaded limit={} returned={} needsAction={} waiting={} done={} closed={} terminalWithHistoricalDispatchProblem={} taskStatusCounts={} dispatchStatusCounts={}",
                safeLimit, tasks.size(), needsAction.size(), waiting.size(), done, closed, terminalWithHistoricalDispatchProblem, taskStatusCounts, dispatchStatusCounts);
        tasks.stream()
                .filter(task -> looksLikeTerminalWithHistoricalDispatchProblem(task, latestDispatchByTask.get(task.getTaskId())))
                .limit(25)
                .forEach(task -> {
                    DispatchRequest dispatch = latestDispatchByTask.get(task.getTaskId());
                    log.info(
                            "task_runtime_view_terminal_historical_dispatch taskId={} status={} canonicalStatus={} latestDispatchRequestId={} latestDispatchStatus={} latestDispatchEligibility={} latestDispatchReason={} hasIssueLink={} issueSyncStatus={} lifecycleReason={} dispatchRetryReason={}",
                            task.getTaskId(),
                            task.getStatus(),
                            task.getStatus() == null ? null : task.getStatus().canonical(),
                            dispatch == null ? null : dispatch.getDispatchRequestId(),
                            dispatch == null ? null : dispatch.getStatus(),
                            dispatch == null ? null : dispatch.getEligibilityStatus(),
                            dispatch == null ? null : dispatch.getReason(),
                            issueLinks.containsKey(task.getTaskId()),
                            issueLinks.get(task.getTaskId()) == null ? null : issueLinks.get(task.getTaskId()).getSyncStatus(),
                            task.getLifecycleReason(),
                            task.getDispatchRetryReason()
                    );
                });
        needsAction.stream().limit(25).forEach(task -> log.warn(
                "task_runtime_view_needs_action_candidate taskId={} status={} canonicalStatus={} tenantId={} sourceSystem={} eventType={} requestedSkill={} matchedFlowId={} matchedRuleId={} routingPath={} lifecycleReason={} dispatchRetryReason={} nextDispatchAttemptAt={} terminalAt={} hasIssueLink={} issueSyncStatus={}",
                task.getTaskId(),
                task.getStatus(),
                task.getStatus() == null ? null : task.getStatus().canonical(),
                task.getTenantId(),
                task.getSourceSystem(),
                task.getEventType(),
                task.getRequestedSkill(),
                task.getMatchedFlowId(),
                task.getMatchedRuleId(),
                task.getRoutingPath(),
                task.getLifecycleReason(),
                task.getDispatchRetryReason(),
                task.getNextDispatchAttemptAt(),
                task.getTerminalAt(),
                issueLinks.containsKey(task.getTaskId()),
                issueLinks.get(task.getTaskId()) == null ? null : issueLinks.get(task.getTaskId()).getSyncStatus()
        ));
    }

    private boolean looksLikeNeedsActionInTaskQueue(TaskRecord task) {
        if (task == null || task.getStatus() == null) return false;
        if (task.getStatus().isSucceeded()) return false;
        if (task.getStatus() == com.opensocket.aievent.core.task.TaskStatus.RETRY_WAIT || task.getNextDispatchAttemptAt() != null || hasText(task.getDispatchRetryReason())) return false;
        return task.getStatus().isFailed()
                || task.getStatus() == com.opensocket.aievent.core.task.TaskStatus.ORPHANED
                || task.getStatus() == com.opensocket.aievent.core.task.TaskStatus.RECONCILING
                || hasText(task.getLifecycleReason()) && task.getStatus().isTerminal();
    }

    private boolean looksLikeWaitingInTaskQueue(TaskRecord task) {
        if (task == null || task.getStatus() == null) return true;
        return !task.getStatus().isTerminal();
    }

    private boolean looksLikeDoneInTaskQueue(TaskRecord task) {
        return task != null && task.getStatus() != null && task.getStatus().isSucceeded();
    }

    private boolean looksLikeClosedInTaskQueue(TaskRecord task) {
        if (task == null || task.getStatus() == null) return false;
        return task.getStatus().isSucceeded()
                || task.getStatus() == com.opensocket.aievent.core.task.TaskStatus.CANCELLED;
    }

    private boolean looksLikeTerminalWithHistoricalDispatchProblem(TaskRecord task, DispatchRequest dispatch) {
        if (!looksLikeClosedInTaskQueue(task) || dispatch == null || dispatch.getStatus() == null) return false;
        return dispatch.getStatus() == com.opensocket.aievent.core.dispatch.DispatchRequestStatus.FAILED
                || dispatch.getStatus() == com.opensocket.aievent.core.dispatch.DispatchRequestStatus.SUPPRESSED
                || dispatch.getStatus() == com.opensocket.aievent.core.dispatch.DispatchRequestStatus.DEAD_LETTER
                || dispatch.getStatus() == com.opensocket.aievent.core.dispatch.DispatchRequestStatus.REJECTED;
    }

    private Map<String, DispatchRequest> latestDispatchByTask(List<DispatchRequest> dispatchRequests) {
        if (dispatchRequests == null || dispatchRequests.isEmpty()) return Map.of();
        return dispatchRequests.stream()
                .filter(request -> request.getTaskId() != null && !request.getTaskId().isBlank())
                .sorted(Comparator.comparing(
                        DispatchRequest::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .collect(java.util.stream.Collectors.toMap(
                        DispatchRequest::getTaskId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<TaskRecord> recentTasks(int limit) {
        TaskQuery query = new TaskQuery();
        query.setLimit(limit);
        return taskQuery.searchTasks(query);
    }

    private Map<String, RoutingDecisionRecord> latestRoutingDecisionsByTask(List<TaskRecord> tasks) {
        if (tasks == null || tasks.isEmpty()) return Map.of();
        Map<String, RoutingDecisionRecord> decisions = new LinkedHashMap<>();
        tasks.stream()
                .map(TaskRecord::getTaskId)
                .filter(id -> id != null && !id.isBlank())
                .forEach(taskId -> taskQuery.findRoutingDecisionsByTask(taskId, 1).stream()
                        .findFirst()
                        .ifPresent(decision -> decisions.put(taskId, decision)));
        return decisions;
    }

    private Map<String, TaskIssueLink> taskIssueLinksByTask(List<TaskRecord> tasks) {
        if (tasks == null || tasks.isEmpty()) return Map.of();
        return taskIssueLinkRepository.findByTaskIdsAsMap(tasks.stream()
                .map(TaskRecord::getTaskId)
                .filter(id -> id != null && !id.isBlank())
                .toList());
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }

    public record CoreDashboardSnapshot(
            CoreDashboardCounts counts,
            CoreAgentGovernanceSummary agentGovernance,
            List<Incident> incidents,
            List<TaskRecord> tasks,
            List<DispatchRequest> dispatchRequests,
            List<CoreAgentRuntimeView> agents,
            List<AgentSecurityEvent> securityEvents,
            CoreDashboardStores stores,
            Map<String, Object> operationalSlo,
            OffsetDateTime generatedAt
    ) {}

    public record CoreDashboardCounts(
            Map<String, Integer> incidentsByStatus,
            Map<String, Integer> tasksByStatus,
            Map<String, Integer> dispatchByStatus,
            Map<String, Integer> agentsByDirectoryStatus,
            Map<String, Integer> gatewaysByStatus
    ) {}

    public record CoreDashboardStores(
            String incidentStore,
            String taskStore,
            String dispatchStore,
            String callbackStore,
            String agentDirectoryStore,
            String gatewayDirectoryStore,
            String agentGovernanceStore
    ) {}

    public record CoreAgentGovernanceSummary(
            Map<String, Long> profilesByApprovalStatus,
            long approvedEnabledAgents,
            Map<String, Long> enrollmentsByStatus,
            Map<String, Long> securityEventsByType,
            OffsetDateTime generatedAt
    ) {}

    public record CoreAgentRuntimeView(
            AgentProfile profile,
            AgentSnapshot directory,
            boolean coreGovernanceAssignable,
            boolean coreDirectoryAssignable,
            String sourceOfTruth
    ) {
        static CoreAgentRuntimeView from(AgentProfile profile, AgentSnapshot directory) {
            boolean governanceAssignable = profile != null
                    && profile.getApprovalStatus() == AgentApprovalStatus.APPROVED
                    && profile.isEnabled()
                    && profile.getRiskStatus() != null
                    && "NORMAL".equals(profile.getRiskStatus().name());
            return new CoreAgentRuntimeView(
                    profile,
                    directory,
                    governanceAssignable,
                    directory != null && directory.isAssignable(),
                    "CORE_PROFILE_PLUS_CORE_DIRECTORY"
            );
        }
    }

    public record CoreTaskRuntimeView(
            List<TaskRecord> tasks,
            List<DispatchRequest> dispatchRequests,
            List<?> callbacks,
            Map<String, Integer> tasksByStatus,
            Map<String, Integer> dispatchByStatus,
            Map<String, RoutingDecisionRecord> latestRoutingDecisions,
            Map<String, TaskIssueLink> taskIssueLinks,
            OffsetDateTime generatedAt
    ) {}
}
