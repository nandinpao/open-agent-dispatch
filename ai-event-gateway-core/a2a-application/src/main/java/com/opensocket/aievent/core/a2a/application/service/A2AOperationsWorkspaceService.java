package com.opensocket.aievent.core.a2a.application.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.a2a.A2AOperationsDetail.AuthorityEvidence;
import com.opensocket.aievent.core.a2a.A2AOperationsDetail.BlockerDiagnosis;
import com.opensocket.aievent.core.a2a.A2AOperationsDetail.GovernedAction;
import com.opensocket.aievent.core.a2a.A2AOperationsDetail.StageStatus;
import com.opensocket.aievent.core.a2a.A2AOperationsDetail.TimelineEntry;
import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.in.A2AOperationsWorkspaceUseCase;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;

/**
 * Read model for operator diagnosis. It composes canonical authority records and never mutates them.
 */
public class A2AOperationsWorkspaceService implements A2AOperationsWorkspaceUseCase {
    private final A2ARequestRepository requests;
    private final A2AStateHistoryRepository stateHistory;
    private final A2AResultRepository results;
    private final A2AResultQuarantineRepository quarantines;
    private final A2ACancellationRepository cancellations;
    private final A2ACancellationEvidenceRepository cancellationEvidence;
    private final A2AReconciliationCaseRepository reconciliationCases;
    private final A2AParentAggregationRepository aggregations;
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final DispatchRequestRepository dispatchRequests;
    private final ObjectProvider<A2AOperationsReadRepository> optimizedReadModels;

    public A2AOperationsWorkspaceService(
            A2ARequestRepository requests,
            A2AStateHistoryRepository stateHistory,
            A2AResultRepository results,
            A2AResultQuarantineRepository quarantines,
            A2ACancellationRepository cancellations,
            A2ACancellationEvidenceRepository cancellationEvidence,
            A2AReconciliationCaseRepository reconciliationCases,
            A2AParentAggregationRepository aggregations,
            TaskRepository tasks,
            TaskAssignmentRepository assignments,
            DispatchRequestRepository dispatchRequests,
            ObjectProvider<A2AOperationsReadRepository> optimizedReadModels) {
        this.requests = requests;
        this.stateHistory = stateHistory;
        this.results = results;
        this.quarantines = quarantines;
        this.cancellations = cancellations;
        this.cancellationEvidence = cancellationEvidence;
        this.reconciliationCases = reconciliationCases;
        this.aggregations = aggregations;
        this.tasks = tasks;
        this.assignments = assignments;
        this.dispatchRequests = dispatchRequests;
        this.optimizedReadModels = optimizedReadModels;
    }

    @Override
    @Transactional(readOnly = true)
    public A2AOperationsPage searchPage(A2AOperationsSearchQuery query) {
        if (query == null || blank(query.tenantId())) {
            throw new IllegalArgumentException("tenantId is required");
        }
        A2AOperationsSearchQuery normalized = new A2AOperationsSearchQuery(
                query.tenantId().trim(), trim(query.requestStatus()), trim(query.blockerCode()),
                trim(query.operationalStage()), trim(query.sourceDomainId()), trim(query.targetDomainId()),
                trim(query.text()), query.blockerOnly(), query.normalizedOffset(), query.cappedLimit(),
                query.normalizedSortBy(), query.normalizedSortDirection());
        A2AOperationsReadRepository optimized = optimizedReadModels.getIfAvailable();
        if (optimized != null) {
            return optimized.searchPage(normalized);
        }
        Comparator<A2AOperationsListItem> fallbackOrder = Comparator.comparing(
                item -> firstTime(item.updatedAt(), item.createdAt()),
                Comparator.nullsLast(Comparator.naturalOrder()));
        if ("DESC".equals(normalized.normalizedSortDirection())) {
            fallbackOrder = fallbackOrder.reversed();
        }
        List<A2AOperationsListItem> fallback = requests.search(normalized.tenantId(), normalized.requestStatus(), normalized.text(),
                        normalized.cappedLimit() + normalized.normalizedOffset()).stream().map(this::context)
                .filter(ctx -> blank(normalized.blockerCode()) || ctx.blocker().code().equalsIgnoreCase(normalized.blockerCode()))
                .filter(ctx -> !normalized.blockerOnly() || !"NONE".equals(ctx.blocker().code()))
                .filter(ctx -> blank(normalized.operationalStage()) || normalized.operationalStage().equalsIgnoreCase(ctx.request().getOperationalStage().name()))
                .filter(ctx -> blank(normalized.sourceDomainId()) || normalized.sourceDomainId().equalsIgnoreCase(ctx.request().getSourceDomainId()))
                .filter(ctx -> blank(normalized.targetDomainId()) || normalized.targetDomainId().equalsIgnoreCase(ctx.request().getTargetDomainId()))
                .map(OperationContext::summary)
                .sorted(fallbackOrder)
                .toList();
        int from = Math.min(normalized.normalizedOffset(), fallback.size());
        int to = Math.min(from + normalized.cappedLimit(), fallback.size());
        return new A2AOperationsPage(fallback.subList(from, to), fallback.size(), from, normalized.cappedLimit(),
                to < fallback.size() ? String.valueOf(to) : null);
    }

    @Override
    @Transactional(readOnly = true)
    public A2AOperationsDetail detail(String tenantId, String requestId) {
        A2ARequest request = requests.findById(required(tenantId, "tenantId"), required(requestId, "requestId"))
                .orElseThrow(() -> new IllegalArgumentException("A2A Request not found: " + requestId));
        OperationContext ctx = context(request);
        return new A2AOperationsDetail(
                ctx.summary(), stages(ctx), timeline(ctx), ctx.blocker(), actions(ctx),
                requestEvidence(request),
                ctx.result().map(this::resultEvidence).orElse(null),
                ctx.cancellation().map(this::cancellationEvidence).orElse(null),
                ctx.reconciliation().map(this::reconciliationEvidence).orElse(null),
                ctx.quarantine().map(this::quarantineEvidence).orElse(null),
                ctx.aggregation().map(this::aggregationEvidence).orElse(null), topology(ctx));
    }

    private OperationContext context(A2ARequest request) {
        Optional<TaskRecord> child = blank(request.getChildTaskId())
                ? Optional.empty()
                : tasks.findByTenantAndId(request.getTenantId(), request.getChildTaskId());
        Optional<TaskAssignment> assignment = child.flatMap(task -> assignments.findByTaskId(task.getTaskId(), 25)
                .stream()
                .filter(value -> request.getTenantId().equals(value.getTenantId()))
                .max(Comparator.comparing(value -> firstTime(value.getUpdatedAt(), value.getCreatedAt()),
                        Comparator.nullsFirst(Comparator.naturalOrder()))));
        List<DispatchRequest> dispatchHistory = child
                .map(task -> dispatchRequests.findByTaskId(task.getTaskId(), 25))
                .orElseGet(List::of);
        Optional<DispatchRequest> dispatch = dispatchHistory.stream()
                .filter(value -> request.getTenantId().equals(value.getTenantId()))
                .max(Comparator.comparing(value -> firstTime(value.getUpdatedAt(), value.getCreatedAt()),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        Optional<A2AResult> result = results.findByRequest(request.getTenantId(), request.getRequestId());
        Optional<A2ACancellationRecord> cancellation = cancellations.findByRequest(
                request.getTenantId(), request.getRequestId());
        Optional<A2AReconciliationCase> reconciliation = reconciliationCases.findOpenByRequest(
                request.getTenantId(), request.getRequestId());
        if (reconciliation.isEmpty() && cancellation.isPresent()) {
            reconciliation = reconciliationCases.findOpenByCancellation(
                    request.getTenantId(), cancellation.get().getCancellationId());
        }
        Optional<A2AResultQuarantine> quarantine = quarantines
                .findByRequest(request.getTenantId(), request.getRequestId(), 20).stream()
                .filter(value -> "OPEN".equals(value.getStatus()))
                .findFirst();
        String parentTaskId = result.map(A2AResult::getParentTaskId)
                .orElse(request.getSourceTaskId());
        Optional<A2AParentAggregation> aggregation = blank(parentTaskId)
                ? Optional.empty()
                : aggregations.findByParentTask(request.getTenantId(), parentTaskId);
        BlockerDiagnosis blocker = diagnose(request, child, assignment, dispatch, result,
                cancellation, reconciliation, quarantine);
        A2AOperationsListItem summary = summary(request, child, assignment, dispatch, result,
                cancellation, aggregation, blocker);
        return new OperationContext(request, child, assignment, dispatch, dispatchHistory, result,
                cancellation, reconciliation, quarantine, aggregation, blocker, summary);
    }

    private A2AOperationsListItem summary(A2ARequest request, Optional<TaskRecord> child,
            Optional<TaskAssignment> assignment, Optional<DispatchRequest> dispatch,
            Optional<A2AResult> result, Optional<A2ACancellationRecord> cancellation,
            Optional<A2AParentAggregation> aggregation, BlockerDiagnosis blocker) {
        return new A2AOperationsListItem(
                request.getRequestId(), request.getRootTaskId(), request.getSourceTaskId(),
                request.getChildTaskId(), request.getSourceDomainId(), request.getTargetDomainId(),
                request.getRequestedTaskType(), enumName(request.getRequestStatus()),
                enumName(request.getOperationalStage()),
                child.map(value -> enumName(value.getStatus())).orElse("NOT_CREATED"),
                dispatch.map(value -> enumName(value.getStatus())).orElse("NOT_REQUESTED"),
                runtimeStatus(assignment, dispatch),
                result.map(value -> enumName(value.getResultStatus())).orElse("PENDING"),
                cancellation.map(value -> enumName(value.getStatus())).orElse("NOT_REQUESTED"),
                aggregation.map(A2AParentAggregation::getAggregateStatus).orElse("NOT_COMPUTED"),
                blocker.code(), blocker.explanation(), blocker.recommendedAction(),
                request.getCreatedAt(), request.getUpdatedAt(), request.getVersion());
    }

    private List<StageStatus> stages(OperationContext ctx) {
        List<StageStatus> values = new ArrayList<>();
        A2ARequest request = ctx.request();
        values.add(new StageStatus("REQUEST", enumName(request.getRequestStatus()), "A2A_CORE",
                request.getRequestId(), request.getUpdatedAt()));
        values.add(new StageStatus("CHILD_TASK",
                ctx.child().map(value -> enumName(value.getStatus())).orElse("NOT_CREATED"),
                "TASK_AUTHORITY", request.getChildTaskId(),
                ctx.child().map(TaskRecord::getUpdatedAt).orElse(request.getUpdatedAt())));
        values.add(new StageStatus("HANDOFF", handoffStatus(ctx.child()), "HANDOFF_CORE",
                request.getChildTaskId(), ctx.child().map(TaskRecord::getUpdatedAt).orElse(null)));
        values.add(new StageStatus("ASSIGNMENT",
                ctx.assignment().map(value -> enumName(value.getStatus())).orElse("NOT_ASSIGNED"),
                "DISPATCH_AUTHORITY", ctx.assignment().map(TaskAssignment::getAssignmentId).orElse(null),
                ctx.assignment().map(TaskAssignment::getUpdatedAt).orElse(null)));
        values.add(new StageStatus("DISPATCH",
                ctx.dispatch().map(value -> enumName(value.getStatus())).orElse("NOT_REQUESTED"),
                "DISPATCH_AUTHORITY", ctx.dispatch().map(DispatchRequest::getDispatchRequestId).orElse(null),
                ctx.dispatch().map(DispatchRequest::getUpdatedAt).orElse(null)));
        values.add(new StageStatus("RUNTIME", runtimeStatus(ctx.assignment(), ctx.dispatch()),
                "RUNTIME_AUTHORITY", ctx.assignment().map(TaskAssignment::getAgentSessionId).orElse(null),
                ctx.dispatch().map(DispatchRequest::getUpdatedAt).orElse(null)));
        values.add(new StageStatus("RESULT",
                ctx.quarantine().map(value -> "QUARANTINED").orElseGet(() ->
                        ctx.result().map(value -> enumName(value.getResultStatus())).orElse("PENDING")),
                "A2A_CORE", ctx.result().map(A2AResult::getResultId)
                        .orElseGet(() -> ctx.quarantine().map(A2AResultQuarantine::getQuarantineId).orElse(null)),
                ctx.result().map(A2AResult::getAcceptedAt)
                        .orElseGet(() -> ctx.quarantine().map(A2AResultQuarantine::getQuarantinedAt).orElse(null))));
        values.add(new StageStatus("AGGREGATION",
                ctx.aggregation().map(A2AParentAggregation::getAggregateStatus).orElse("NOT_COMPUTED"),
                "A2A_CORE", ctx.aggregation().map(A2AParentAggregation::getParentTaskId).orElse(null),
                ctx.aggregation().map(A2AParentAggregation::getComputedAt).orElse(null)));
        values.add(new StageStatus("CANCELLATION",
                ctx.cancellation().map(value -> enumName(value.getStatus())).orElse("NOT_REQUESTED"),
                "A2A_CORE", ctx.cancellation().map(A2ACancellationRecord::getCancellationId).orElse(null),
                ctx.cancellation().map(A2ACancellationRecord::getUpdatedAt).orElse(null)));
        return List.copyOf(values);
    }

    private List<TimelineEntry> timeline(OperationContext ctx) {
        List<TimelineEntry> values = new ArrayList<>();
        for (A2AStateHistoryEntry entry : stateHistory.findByRequest(
                ctx.request().getTenantId(), ctx.request().getRequestId(), 250)) {
            values.add(new TimelineEntry(entry.getHistoryId(), "REQUEST", "STATE_TRANSITION",
                    enumName(entry.getToStatus()), entry.getReasonCode(), entry.getReason(),
                    joinActor(entry.getActorType(), entry.getActorId()), entry.getCorrelationId(),
                    entry.getTransitionAt()));
        }
        ctx.child().ifPresent(task -> values.add(new TimelineEntry("task:" + task.getTaskId(),
                "CHILD_TASK", "TASK_STATE", enumName(task.getStatus()), null,
                task.getLifecycleReason(), task.getCreatedById(), task.getTaskId(), task.getUpdatedAt())));
        for (DispatchRequest dispatch : ctx.dispatchHistory()) {
            values.add(new TimelineEntry("dispatch:" + dispatch.getDispatchRequestId(), "DISPATCH",
                    "DISPATCH_STATE", enumName(dispatch.getStatus()), null,
                    first(dispatch.getLastError(), dispatch.getReason()), dispatch.getAgentId(),
                    dispatch.getDispatchRequestId(), dispatch.getUpdatedAt()));
        }
        ctx.result().ifPresent(result -> values.add(new TimelineEntry("result:" + result.getResultId(),
                "RESULT", "CANONICAL_RESULT", enumName(result.getResultStatus()), null,
                result.getResultSummary(), result.getCompletedByAgentId(), result.getCallbackInboxId(),
                firstTime(result.getAcceptedAt(), result.getCompletedAt(), result.getCreatedAt()))));
        ctx.quarantine().ifPresent(value -> values.add(new TimelineEntry(
                "quarantine:" + value.getQuarantineId(), "RESULT", "RESULT_QUARANTINED",
                value.getStatus(), value.getReasonCode(), value.getReason(), null,
                value.getCallbackInboxId(), value.getQuarantinedAt())));
        ctx.cancellation().ifPresent(cancellation -> {
            for (A2ACancellationEvidence evidence : cancellationEvidence.findByCancellation(
                    cancellation.getTenantId(), cancellation.getCancellationId(), 250)) {
                values.add(new TimelineEntry(evidence.getEvidenceId(), "CANCELLATION",
                        evidence.getEvidenceType(), evidence.getDecision(), evidence.getReasonCode(),
                        evidence.getDetails(), null, evidence.getEvidenceReference(), evidence.getOccurredAt()));
            }
        });
        ctx.reconciliation().ifPresent(value -> values.add(new TimelineEntry("case:" + value.getCaseId(),
                "RECONCILIATION", value.getCaseType(), enumName(value.getStatus()), value.getReasonCode(),
                value.getReason(), value.getResolvedBy(), value.getCaseId(),
                firstTime(value.getUpdatedAt(), value.getCreatedAt()))));
        values.sort(Comparator.comparing(TimelineEntry::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(values);
    }

    private A2AOperationsTopology topology(OperationContext ctx) {
        List<A2AOperationsTopology.Node> nodes = new ArrayList<>();
        List<A2AOperationsTopology.Edge> edges = new ArrayList<>();
        String parent = first(ctx.request().getSourceTaskId(), ctx.request().getRootTaskId());
        if (!blank(parent)) nodes.add(new A2AOperationsTopology.Node(parent, "PARENT_TASK", parent, "AUTHORITATIVE", "TASK_AUTHORITY"));
        nodes.add(new A2AOperationsTopology.Node(ctx.request().getRequestId(), "A2A_REQUEST", ctx.request().getRequestId(), enumName(ctx.request().getRequestStatus()), "A2A_CORE"));
        if (!blank(parent)) edges.add(new A2AOperationsTopology.Edge(parent, ctx.request().getRequestId(), "CREATES_A2A", "ACTIVE"));
        ctx.child().ifPresent(v -> { nodes.add(new A2AOperationsTopology.Node(v.getTaskId(), "CHILD_TASK", v.getTaskId(), enumName(v.getStatus()), "TASK_AUTHORITY")); edges.add(new A2AOperationsTopology.Edge(ctx.request().getRequestId(), v.getTaskId(), "CREATES_CHILD", "ACTIVE")); });
        ctx.assignment().ifPresent(v -> { nodes.add(new A2AOperationsTopology.Node(v.getAssignmentId(), "ASSIGNMENT", v.getAgentId(), enumName(v.getStatus()), "DISPATCH_AUTHORITY")); if(!blank(ctx.request().getChildTaskId())) edges.add(new A2AOperationsTopology.Edge(ctx.request().getChildTaskId(), v.getAssignmentId(), "ASSIGNED_TO", "ACTIVE")); });
        ctx.assignment().filter(v -> !blank(v.getAgentId())).ifPresent(v -> {String id="agent:"+v.getAgentId();nodes.add(new A2AOperationsTopology.Node(id,"AGENT",v.getAgentId(),runtimeStatus(ctx.assignment(),ctx.dispatch()),"RUNTIME_AUTHORITY"));edges.add(new A2AOperationsTopology.Edge(v.getAssignmentId(),id,"DELIVERED_TO","ACTIVE"));});
        return new A2AOperationsTopology(nodes, edges);
    }

    private BlockerDiagnosis diagnose(A2ARequest request, Optional<TaskRecord> child,
            Optional<TaskAssignment> assignment, Optional<DispatchRequest> dispatch,
            Optional<A2AResult> result, Optional<A2ACancellationRecord> cancellation,
            Optional<A2AReconciliationCase> reconciliation,
            Optional<A2AResultQuarantine> quarantine) {
        if (reconciliation.isPresent()) {
            A2AReconciliationCase value = reconciliation.get();
            return blocker(first(value.getReasonCode(), "MANUAL_REVIEW_REQUIRED"),
                    "Reconciliation requires an operator decision", value.getReason(),
                    value.getEvidenceSummary(), value.getRecommendedAction(), true);
        }
        if (quarantine.isPresent()) {
            A2AResultQuarantine value = quarantine.get();
            return blocker("RESULT_QUARANTINED", "Result evidence was quarantined", value.getReason(),
                    value.getCallbackInboxId(),
                    "Review the evidence and accept it only as governance evidence or reject it.", true);
        }
        if (request.getRequestStatus() == A2ARequestStatus.WAITING_APPROVAL) {
            return blocker("A2A_APPROVAL_REQUIRED", "A2A approval is required",
                    "The directional policy requires approval before a Child Task can be created.",
                    request.getPolicyId(), "Approve or reject the request using a governed action.", true);
        }
        if (request.getRequestStatus() == A2ARequestStatus.WAIT_HUMAN) {
            return blocker("MANUAL_REVIEW_REQUIRED", "The A2A chain is waiting for a human decision",
                    first(request.getReason(), "Automated recovery could not prove a safe outcome."),
                    request.getCorrelationId(), "Inspect the evidence timeline and resolve the open case.", true);
        }
        if (child.isPresent() && child.get().getStatus() == TaskStatus.WAITING_CONTEXT) {
            return blocker("HANDOFF_REQUIRED", "Handoff context is not approved",
                    "The Child Task is fenced in WAITING_CONTEXT until an immutable Handoff Snapshot is approved.",
                    child.get().getTaskId(), "Approve or reject the Handoff Snapshot before dispatch.", true);
        }
        if (request.getRequestStatus() == A2ARequestStatus.APPROVED && child.isEmpty()) {
            return blocker("CHILD_TASK_CREATION_PENDING", "Child Task creation has not completed",
                    "The A2A request is approved but Task Authority has not produced a Child Task evidence record.",
                    request.getRequestId(), "Run reconciliation; do not create a duplicate Child Task manually.", false);
        }
        if (dispatch.isPresent()) {
            DispatchRequest value = dispatch.get();
            if (value.getStatus() == DispatchRequestStatus.FAILED
                    || value.getStatus() == DispatchRequestStatus.TIMED_OUT
                    || value.getStatus() == DispatchRequestStatus.DEAD_LETTER) {
                return blocker("DISPATCH_" + value.getStatus().name(), "Dispatch did not complete",
                        first(value.getLastError(), value.getReason(), "Dispatch Authority reported a failure."),
                        value.getDispatchRequestId(),
                        "Open the Child Task recovery view and retry only through Dispatch Authority.", false);
            }
            if (value.getStatus() == DispatchRequestStatus.RETRY_WAITING) {
                return blocker("DISPATCH_RETRY_WAITING", "Dispatch is waiting for retry",
                        first(value.getLastError(), value.getReason(), "The next retry is scheduled."),
                        value.getDispatchRequestId(), "Review the retry reason or wait for the scheduled retry.", false);
            }
        }
        if (child.isPresent() && child.get().getStatus() == TaskStatus.WAITING_HUMAN) {
            return blocker("TASK_WAITING_HUMAN", "Child Task needs operator recovery",
                    child.get().getLifecycleReason(), child.get().getTaskId(),
                    "Open the Child Task and apply a governed recovery action.", true);
        }
        if (cancellation.isPresent()
                && cancellation.get().getProcessingStatus()
                    != A2ACancellationProcessingStatus.CONFIRMED) {
            A2ACancellationRecord value = cancellation.get();
            return blocker("CANCELLATION_" + enumName(value.getOutcome()),
                    "Cancellation requires evidence or repair",
                    first(value.getLastError(), value.getReason()), value.getCancellationId(),
                    "Inspect fencing, Runtime ACK, retry and Late Result evidence.",
                    value.getProcessingStatus() == A2ACancellationProcessingStatus.WAIT_HUMAN);
        }
        if (result.isEmpty() && request.getRequestStatus() == A2ARequestStatus.RUNNING
                && !active(assignment)) {
            return blocker("ASSIGNMENT_EVIDENCE_MISSING", "Running request has no active assignment",
                    "A2A is RUNNING but Dispatch Authority has no active Assignment evidence.",
                    request.getChildTaskId(), "Run reconciliation and inspect the Child Task dispatch history.", false);
        }
        return BlockerDiagnosis.none();
    }

    private List<GovernedAction> actions(OperationContext ctx) {
        List<GovernedAction> actions = new ArrayList<>();
        String id = ctx.request().getRequestId();
        if (ctx.request().getRequestStatus() == A2ARequestStatus.WAITING_APPROVAL) {
            actions.add(action("REJECT", "Reject retired request", "POST", "/api/a2a-requests/" + id + "/reject",
                    "a2a.request.approve", true, "Phase 0 retired directional execution. A reason code and operator reason are required to close this historical request."));
        }
        if (!terminal(ctx.request().getRequestStatus())
                && ctx.request().getRequestStatus() != A2ARequestStatus.CANCEL_REQUESTED) {
            actions.add(action("REQUEST_CANCELLATION", "Request cancellation", "POST",
                    "/api/a2a-requests/" + id + "/cancel", "a2a.request.cancel", true,
                    "Cancellation rotates fencing and never force-completes the Child Task."));
        }
        ctx.reconciliation().ifPresent(value -> actions.add(action("RESOLVE_RECONCILIATION",
                "Resolve reconciliation case", "POST", "/api/a2a-reconciliation-cases/"
                        + value.getCaseId() + "/resolve", "a2a.reconciliation.resolve", true,
                "Only RESOLVED or IGNORED decisions are accepted and audit reason is required.")));
        ctx.cancellation().filter(value -> value.getProcessingStatus()
                == A2ACancellationProcessingStatus.FAILED_RETRYABLE
                || value.getProcessingStatus() == A2ACancellationProcessingStatus.RUNTIME_ACK_PENDING)
                .ifPresent(value -> actions.add(action("RECONCILE_CANCELLATION",
                        "Reconcile cancellation", "POST", "/api/a2a-cancellations/"
                                + value.getCancellationId() + "/reconcile",
                        "a2a.reconciliation.resolve", true,
                        "Reconciliation reuses immutable fencing evidence and cannot force-complete a Task.")));
        ctx.quarantine().ifPresent(value -> actions.add(action("RESOLVE_QUARANTINE",
                "Review quarantined result", "POST", "/api/a2a-result-quarantine/"
                        + value.getQuarantineId() + "/resolve", "a2a.result.quarantine.resolve", true,
                "This action cannot replace the canonical Result or bypass fencing.")));
        if (ctx.child().isPresent()) {
            actions.add(action("OPEN_CHILD_TASK", "Open Child Task", "GET",
                    "/tasks/" + ctx.child().get().getTaskId(), "task.read", false,
                    "Task Authority remains the source of truth for recovery and dispatch retry."));
        }
        return List.copyOf(actions);
    }


    private AuthorityEvidence requestEvidence(A2ARequest value) {
        return evidence("A2A_CORE", "A2A_REQUEST", value.getRequestId(), enumName(value.getRequestStatus()),
                value.getVersion(), firstTime(value.getUpdatedAt(), value.getCreatedAt()), facts(
                        "rootTaskId", value.getRootTaskId(),
                        "sourceTaskId", value.getSourceTaskId(),
                        "childTaskId", value.getChildTaskId(),
                        "sourceDomainId", value.getSourceDomainId(),
                        "targetDomainId", value.getTargetDomainId(),
                        "requestedTaskType", value.getRequestedTaskType(),
                        "policyId", value.getPolicyId(),
                        "approvalStatus", enumName(value.getApprovalStatus()),
                        "correlationId", value.getCorrelationId()));
    }

    private AuthorityEvidence resultEvidence(A2AResult value) {
        return evidence("A2A_CORE", "CANONICAL_RESULT", value.getResultId(), enumName(value.getResultStatus()),
                value.getVersion(), firstTime(value.getAcceptedAt(), value.getCompletedAt(), value.getCreatedAt()), facts(
                        "assignmentId", value.getAssignmentId(),
                        "executionAttemptId", value.getExecutionAttemptId(),
                        "attemptNo", value.getAttemptNo(),
                        "dispatchRequestId", value.getDispatchRequestId(),
                        "agentSessionId", value.getAgentSessionId(),
                        "callbackInboxId", value.getCallbackInboxId(),
                        "acceptanceAttemptId", value.getAcceptanceAttemptId()));
    }

    private AuthorityEvidence cancellationEvidence(A2ACancellationRecord value) {
        return evidence("A2A_CORE", "CANCELLATION", value.getCancellationId(), enumName(value.getStatus()),
                value.getVersion(), firstTime(value.getUpdatedAt(), value.getRequestedAt()), facts(
                        "childTaskId", value.getChildTaskId(),
                        "assignmentId", value.getAssignmentId(),
                        "executionAttemptId", value.getExecutionAttemptId(),
                        "attemptNo", value.getAttemptNo(),
                        "dispatchRequestId", value.getDispatchRequestId(),
                        "agentId", value.getAgentId(),
                        "agentSessionId", value.getAgentSessionId(),
                        "outcome", enumName(value.getOutcome()),
                        "processingStatus", enumName(value.getProcessingStatus()),
                        "reconciliationClassification", enumName(value.getReconciliationClassification()),
                        "deliveryStatus", value.getDeliveryStatus(),
                        "retryCount", value.getRetryCount(),
                        "reconciliationCount", value.getReconciliationCount(),
                        "resultCutoffAt", value.getResultCutoffAt(),
                        "deadlineAt", value.getDeadlineAt(),
                        "nextReconcileAt", value.getNextReconcileAt()));
    }

    private AuthorityEvidence reconciliationEvidence(A2AReconciliationCase value) {
        return evidence("A2A_CORE", "RECONCILIATION_CASE", value.getCaseId(), enumName(value.getStatus()),
                value.getVersion(), firstTime(value.getUpdatedAt(), value.getCreatedAt()), facts(
                        "caseType", value.getCaseType(),
                        "authorityOwner", value.getAuthorityOwner(),
                        "diagnosisCode", value.getDiagnosisCode(),
                        "repairAction", value.getRepairAction(),
                        "requiredPermission", value.getRequiredPermission(),
                        "planHash", value.getPlanHash(),
                        "reasonCode", value.getReasonCode(),
                        "recommendedAction", value.getRecommendedAction(),
                        "resolvedBy", value.getResolvedBy()));
    }

    private AuthorityEvidence quarantineEvidence(A2AResultQuarantine value) {
        return evidence("A2A_CORE", "RESULT_QUARANTINE", value.getQuarantineId(), value.getStatus(), null,
                firstTime(value.getResolvedAt(), value.getQuarantinedAt()), facts(
                        "classification", enumName(value.getClassification()),
                        "reasonCode", value.getReasonCode(),
                        "callbackInboxId", value.getCallbackInboxId(),
                        "assignmentId", value.getAssignmentId(),
                        "executionAttemptId", value.getExecutionAttemptId(),
                        "resolvedBy", value.getResolvedBy()));
    }

    private AuthorityEvidence aggregationEvidence(A2AParentAggregation value) {
        return evidence("A2A_CORE", "PARENT_AGGREGATION", value.getParentTaskId(), value.getAggregateStatus(),
                value.getVersion(), value.getComputedAt(), facts(
                        "aggregationPolicy", enumName(value.getAggregationPolicy()),
                        "totalCount", value.getTotalCount(),
                        "pendingCount", value.getPendingCount(),
                        "succeededCount", value.getSucceededCount(),
                        "partialCount", value.getPartialCount(),
                        "failedCount", value.getFailedCount(),
                        "cancelledCount", value.getCancelledCount(),
                        "lastResultId", value.getLastResultId()));
    }

    private AuthorityEvidence evidence(String authority, String type, String id, String status,
            Long version, OffsetDateTime occurredAt, Map<String, String> facts) {
        return new AuthorityEvidence(authority, type, id, status, version, occurredAt, facts);
    }

    private Map<String, String> facts(Object... values) {
        Map<String, String> facts = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            Object key = values[index];
            Object value = values[index + 1];
            if (key != null && value != null && !value.toString().isBlank()) {
                facts.put(key.toString(), value.toString());
            }
        }
        return Map.copyOf(facts);
    }

    private GovernedAction action(String code, String label, String method, String endpoint,
            String permission, boolean confirmation, String guardrail) {
        return new GovernedAction(code, label, method, endpoint, permission, confirmation, guardrail);
    }

    private boolean active(Optional<TaskAssignment> assignment) {
        return assignment.map(TaskAssignment::getStatus)
                .map(status -> status == AssignmentStatus.ASSIGNED || status == AssignmentStatus.AWAITING_REVIEW)
                .orElse(false);
    }

    private String runtimeStatus(Optional<TaskAssignment> assignment, Optional<DispatchRequest> dispatch) {
        if (dispatch.isPresent()) {
            return switch (dispatch.get().getStatus()) {
                case DISPATCHING, DISPATCHED -> "DELIVERING";
                case DELIVERY_UNKNOWN -> "RECONCILING";
                case ACKED -> "ACKNOWLEDGED";
                case RUNNING -> "RUNNING";
                case COMPLETED -> "COMPLETED";
                case FAILED, TIMED_OUT, DEAD_LETTER -> "FAILED";
                default -> assignment.isPresent() ? "SESSION_BOUND" : "NOT_STARTED";
            };
        }
        return assignment.map(value -> blank(value.getAgentSessionId()) ? "ASSIGNED" : "SESSION_BOUND")
                .orElse("NOT_STARTED");
    }

    private String handoffStatus(Optional<TaskRecord> child) {
        if (child.isEmpty()) return "NOT_CREATED";
        TaskRecord value = child.get();
        if (value.getStatus() == TaskStatus.WAITING_CONTEXT) return "WAITING_APPROVAL";
        if (blank(value.getHandoffMode()) || "NONE".equalsIgnoreCase(value.getHandoffMode())) {
            return "NOT_REQUIRED";
        }
        return "RELEASED";
    }

    private BlockerDiagnosis blocker(String code, String title, String explanation, String evidence,
            String action, boolean human) {
        return new BlockerDiagnosis(code, title, first(explanation, title), evidence, action, human);
    }

    private boolean terminal(A2ARequestStatus status) {
        return status == A2ARequestStatus.COMPLETED || status == A2ARequestStatus.FAILED
                || status == A2ARequestStatus.REJECTED || status == A2ARequestStatus.EXPIRED
                || status == A2ARequestStatus.CANCELLED_CONFIRMED
;
    }
    private String enumName(Enum<?> value) { return value == null ? "UNKNOWN" : value.name(); }
    private String trim(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String required(String value, String name) {
        if (blank(value)) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
    private String joinActor(String type, String id) {
        if (blank(type)) return id;
        return blank(id) ? type : type + ":" + id;
    }
    private String first(String... values) {
        for (String value : values) if (!blank(value)) return value.trim();
        return null;
    }
    private OffsetDateTime firstTime(OffsetDateTime... values) {
        for (OffsetDateTime value : values) if (value != null) return value;
        return null;
    }

    private record OperationContext(A2ARequest request, Optional<TaskRecord> child,
            Optional<TaskAssignment> assignment, Optional<DispatchRequest> dispatch,
            List<DispatchRequest> dispatchHistory, Optional<A2AResult> result,
            Optional<A2ACancellationRecord> cancellation,
            Optional<A2AReconciliationCase> reconciliation,
            Optional<A2AResultQuarantine> quarantine,
            Optional<A2AParentAggregation> aggregation, BlockerDiagnosis blocker,
            A2AOperationsListItem summary) {}
}
