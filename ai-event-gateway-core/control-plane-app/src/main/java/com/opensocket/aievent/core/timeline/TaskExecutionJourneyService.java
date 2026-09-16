package com.opensocket.aievent.core.timeline;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionRepository;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRepository;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyAutomationStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyBindingStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecision;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionOutcome;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionRepository;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.journey.TaskExecutionEvidenceRef;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStage;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStageCode;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStatus;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyView;
import com.opensocket.aievent.core.task.journey.TaskExecutionRetryability;

/**
 * Canonical, rebuildable Task execution read model.
 *
 * <p>This service never writes lifecycle state. Every stage is derived from an authoritative
 * operational record and exposes references back to that evidence. It is the read authority for
 * Admin Task journey/timeline status, not a replacement write state machine.</p>
 */
@Service
public class TaskExecutionJourneyService {
    public static final String AUTHORITY = "V24_TASK_EXECUTION_JOURNEY_V1";
    private static final String PRIMARY_PURPOSE = "PRIMARY_ISSUE";

    private final TaskOperationalQuery taskQuery;
    private final ExecutionOperationalQuery executionQuery;
    private final IssuePolicyDecisionRepository policyDecisions;
    private final AdapterActionRepository adapterActions;
    private final AdapterExecutorAuditRepository adapterExecutorAudits;
    private final TaskIssueLinkRepository taskIssueLinks;

    public TaskExecutionJourneyService(
            TaskOperationalQuery taskQuery,
            ExecutionOperationalQuery executionQuery,
            IssuePolicyDecisionRepository policyDecisions,
            AdapterActionRepository adapterActions,
            AdapterExecutorAuditRepository adapterExecutorAudits,
            TaskIssueLinkRepository taskIssueLinks) {
        this.taskQuery = taskQuery;
        this.executionQuery = executionQuery;
        this.policyDecisions = policyDecisions;
        this.adapterActions = adapterActions;
        this.adapterExecutorAudits = adapterExecutorAudits;
        this.taskIssueLinks = taskIssueLinks;
    }

    public TaskExecutionJourneyView journey(String taskId) {
        TaskRecord task = taskQuery.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        String tenantId = task.getTenantId();

        RoutingDecisionRecord routing = latest(taskQuery.findRoutingDecisionsByTask(taskId, 50), RoutingDecisionRecord::getCreatedAt);
        TaskAssignment assignment = latest(taskQuery.findAssignmentsByTask(taskId, 50), a -> first(a.getUpdatedAt(), a.getCreatedAt()));
        DispatchRequest dispatch = latest(executionQuery.findDispatchRequestsByTask(taskId, 100), d -> first(d.getUpdatedAt(), d.getCreatedAt()));
        List<TaskCallbackRecord> callbacks = executionQuery.findCallbacksByTask(taskId, 200);
        TaskCallbackRecord ack = latestAcceptedCallback(callbacks, TaskCallbackType.ACK);
        TaskCallbackRecord result = latestAcceptedTerminalCallback(callbacks);

        IssuePolicyDecision policy = policyDecisions.findByTaskAndPurpose(tenantId, taskId, PRIMARY_PURPOSE).orElse(null);
        // Route B is the canonical PRIMARY_ISSUE execution authority. Legacy IssueProjectionState /
        // IntegrationOutbox evidence is intentionally excluded from the primary Task journey.
        AdapterAction action = primaryIssueAction(taskId, policy);
        List<AdapterExecutorAuditRecord> providerAudits = action == null
                ? List.of()
                : adapterExecutorAudits.findByActionId(action.getActionId(), 100);
        TaskIssueLink link = taskIssueLinks.findAllByTenantAndTaskId(tenantId, taskId).stream()
                .sorted(Comparator.comparing((TaskIssueLink value) -> "PRIMARY".equalsIgnoreCase(value.getLinkRole())).reversed()
                        .thenComparing(TaskIssueLink::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst().orElse(null);

        List<TaskExecutionJourneyStage> stages = new ArrayList<>(13);
        stages.add(intakeStage(task));
        stages.add(routingStage(task, routing));
        stages.add(assignmentStage(routing, assignment));
        stages.add(deliveryStage(assignment, dispatch));
        stages.add(ackStage(dispatch, ack));
        stages.add(resultStage(dispatch, result));
        stages.add(issuePolicyStage(result, task, policy));
        stages.add(issueBindingStage(policy));
        stages.add(issueAdapterActionStage(policy, action));
        stages.add(issueExecutorStage(policy, action));
        stages.add(issueProviderStage(policy, action, providerAudits));
        stages.add(issueProviderCertaintyStage(policy, action, providerAudits));
        stages.add(issueLinkStage(policy, action, providerAudits, link));

        TaskExecutionJourneyStage current = stages.stream()
                .filter(stage -> stage.status() != TaskExecutionJourneyStatus.SUCCEEDED
                        && stage.status() != TaskExecutionJourneyStatus.NOT_APPLICABLE)
                .findFirst().orElse(null);
        TaskExecutionJourneyStatus overall = overallStatus(stages);
        long revision = revisionFor(stages.stream().map(TaskExecutionJourneyStage::revision).toArray());
        return new TaskExecutionJourneyView(
                tenantId,
                taskId,
                firstNonBlank(task.getRootTaskId(), taskId),
                task.getParentTaskId(),
                firstNonBlank(task.getCorrelationId(), task.getOriginCorrelationId(), task.getIncidentId(), taskId),
                overall,
                current == null ? TaskExecutionJourneyStageCode.PROJECTION_SYNCED : current.stage(),
                current == null ? null : current.reasonCode(),
                revision,
                OffsetDateTime.now(ZoneOffset.UTC),
                stages);
    }

    private TaskExecutionJourneyStage intakeStage(TaskRecord task) {
        return stage(TaskExecutionJourneyStageCode.INTAKE, TaskExecutionJourneyStatus.SUCCEEDED, true,
                "TASK_PROVENANCE_CAPTURED", "Event intake and immutable Task provenance are present.",
                first(task.getProvenanceCapturedAt(), task.getCreatedAt()), first(task.getCreatedAt(), task.getProvenanceCapturedAt()),
                first(task.getUpdatedAt(), task.getCreatedAt()),
                refs(ref("TASK", task.getTaskId(), "TaskRecord", task.getCreatedAt(), attrs(
                        "sourceEventId", task.getSourceEventId(), "workloadPurpose", task.getWorkloadPurpose(),
                        "originPrincipalType", task.getOriginPrincipalType(), "originPrincipalId", task.getOriginPrincipalId()))),
                TaskExecutionRetryability.NOT_APPLICABLE, null, "TaskRecord + Immutable WorkloadContext",
                revisionFor(task.getTaskId(), task.getVersion(), task.getCreatedAt(), task.getProvenanceCapturedAt()));
    }

    private TaskExecutionJourneyStage routingStage(TaskRecord task, RoutingDecisionRecord routing) {
        if (routing == null) {
            boolean taskAlreadyBlocked = task.getStatus() != null && task.getStatus().isTerminal();
            return stage(TaskExecutionJourneyStageCode.ROUTING,
                    taskAlreadyBlocked ? TaskExecutionJourneyStatus.FAILED_FINAL : TaskExecutionJourneyStatus.NOT_STARTED,
                    true, taskAlreadyBlocked ? "ROUTING_EVIDENCE_MISSING" : "ROUTING_NOT_EVALUATED",
                    taskAlreadyBlocked ? "Task is terminal but no routing decision evidence exists." : "Waiting for a persisted routing decision.",
                    null, null, task.getUpdatedAt(), List.of(),
                    taskAlreadyBlocked ? TaskExecutionRetryability.MANUAL_RECONCILIATION : TaskExecutionRetryability.UNKNOWN,
                    task.getNextDispatchAttemptAt(), "RoutingDecisionRecord", revisionFor(task.getVersion(), task.getUpdatedAt(), "routing:none"));
        }
        RoutingDecisionStatus status = routing.getStatus();
        TaskExecutionJourneyStatus journeyStatus = switch (status == null ? RoutingDecisionStatus.NO_CANDIDATE : status) {
            case SELECTED -> TaskExecutionJourneyStatus.SUCCEEDED;
            case NO_CANDIDATE -> TaskExecutionJourneyStatus.FAILED_RETRYABLE;
            case MANUAL_REVIEW_REQUIRED, SUPPRESSED -> TaskExecutionJourneyStatus.BLOCKED;
        };
        String reason = switch (status == null ? RoutingDecisionStatus.NO_CANDIDATE : status) {
            case SELECTED -> "ROUTING_AGENT_SELECTED";
            case NO_CANDIDATE -> "ROUTING_NO_CANDIDATE";
            case MANUAL_REVIEW_REQUIRED -> "ROUTING_MANUAL_REVIEW_REQUIRED";
            case SUPPRESSED -> "ROUTING_SUPPRESSED";
        };
        return stage(TaskExecutionJourneyStageCode.ROUTING, journeyStatus, true, reason,
                firstNonBlank(routing.getDecisionReason(), "Routing decision persisted."), routing.getCreatedAt(),
                journeyStatus == TaskExecutionJourneyStatus.SUCCEEDED ? routing.getCreatedAt() : null, routing.getCreatedAt(),
                refs(ref("ROUTING_DECISION", routing.getDecisionId(), "RoutingDecisionRecord", routing.getCreatedAt(), attrs(
                        "status", enumName(routing.getStatus()), "selectedAgentId", routing.getSelectedAgentId(),
                        "routingPolicy", enumName(routing.getRoutingPolicy())))),
                journeyStatus == TaskExecutionJourneyStatus.FAILED_RETRYABLE ? TaskExecutionRetryability.RETRYABLE : TaskExecutionRetryability.NOT_RETRYABLE,
                task.getNextDispatchAttemptAt(), "RoutingDecisionRecord", revisionFor(routing.getDecisionId(), status, routing.getCreatedAt(), routing.getSelectedAgentId()));
    }

    private TaskExecutionJourneyStage assignmentStage(RoutingDecisionRecord routing, TaskAssignment assignment) {
        if (assignment == null) {
            boolean routingSelected = routing != null && routing.getStatus() == RoutingDecisionStatus.SELECTED;
            return stage(TaskExecutionJourneyStageCode.ASSIGNMENT,
                    routingSelected ? TaskExecutionJourneyStatus.PENDING : TaskExecutionJourneyStatus.NOT_STARTED,
                    true, routingSelected ? "ASSIGNMENT_PENDING" : "ASSIGNMENT_WAITING_FOR_ROUTING",
                    routingSelected ? "Routing selected an Agent; waiting for persisted assignment evidence." : "Assignment waits for successful routing.",
                    null, null, routing == null ? null : routing.getCreatedAt(), List.of(), TaskExecutionRetryability.UNKNOWN,
                    null, "TaskAssignment", revisionFor("assignment:none", routing == null ? null : routing.getDecisionId()));
        }
        AssignmentStatus status = assignment.getStatus();
        TaskExecutionJourneyStatus s = switch (status == null ? AssignmentStatus.NO_CANDIDATE : status) {
            case ASSIGNED -> TaskExecutionJourneyStatus.SUCCEEDED;
            case AWAITING_REVIEW -> TaskExecutionJourneyStatus.BLOCKED;
            case NO_CANDIDATE -> TaskExecutionJourneyStatus.FAILED_RETRYABLE;
            case SUPPRESSED, CANCELLED -> TaskExecutionJourneyStatus.FAILED_FINAL;
        };
        return stage(TaskExecutionJourneyStageCode.ASSIGNMENT, s, true, "ASSIGNMENT_" + enumName(status),
                firstNonBlank(assignment.getReason(), "Assignment state is " + enumName(status) + "."),
                assignment.getCreatedAt(), s == TaskExecutionJourneyStatus.SUCCEEDED ? first(assignment.getUpdatedAt(), assignment.getCreatedAt()) : null,
                first(assignment.getUpdatedAt(), assignment.getCreatedAt()),
                refs(ref("TASK_ASSIGNMENT", assignment.getAssignmentId(), "TaskAssignment", assignment.getCreatedAt(), attrs(
                        "agentId", assignment.getAgentId(), "status", enumName(status), "routingDecisionId", assignment.getRoutingDecisionId()))),
                s == TaskExecutionJourneyStatus.FAILED_RETRYABLE ? TaskExecutionRetryability.RETRYABLE : TaskExecutionRetryability.NOT_RETRYABLE,
                null, "TaskAssignment", revisionFor(assignment.getAssignmentId(), status, assignment.getUpdatedAt(), assignment.getLeaseId()));
    }

    private TaskExecutionJourneyStage deliveryStage(TaskAssignment assignment, DispatchRequest dispatch) {
        if (dispatch == null) {
            return stage(TaskExecutionJourneyStageCode.DELIVERY,
                    assignment != null && assignment.getStatus() == AssignmentStatus.ASSIGNED ? TaskExecutionJourneyStatus.PENDING : TaskExecutionJourneyStatus.NOT_STARTED,
                    true, "DISPATCH_REQUEST_NOT_CREATED", "Waiting for a persisted DispatchRequest.", null, null,
                    assignment == null ? null : assignment.getUpdatedAt(), List.of(), TaskExecutionRetryability.UNKNOWN, null,
                    "DispatchRequest", revisionFor("delivery:none", assignment == null ? null : assignment.getAssignmentId()));
        }
        DispatchRequestStatus status = dispatch.getStatus();
        boolean delivered = dispatch.getDispatchedAt() != null || status == DispatchRequestStatus.DISPATCHED || status == DispatchRequestStatus.ACKED
                || status == DispatchRequestStatus.RUNNING || status == DispatchRequestStatus.COMPLETED;
        TaskExecutionJourneyStatus s;
        TaskExecutionRetryability retry;
        String reason;
        if (delivered) {
            s = TaskExecutionJourneyStatus.SUCCEEDED; retry = TaskExecutionRetryability.NOT_APPLICABLE; reason = "RUNTIME_DELIVERY_CONFIRMED";
        } else if (status == DispatchRequestStatus.RETRY_WAITING || dispatch.getNextRetryAt() != null) {
            s = TaskExecutionJourneyStatus.FAILED_RETRYABLE; retry = TaskExecutionRetryability.RETRYABLE; reason = "RUNTIME_DELIVERY_RETRY_WAITING";
        } else if (status == DispatchRequestStatus.FAILED || status == DispatchRequestStatus.TIMED_OUT) {
            s = TaskExecutionJourneyStatus.FAILED_RETRYABLE; retry = TaskExecutionRetryability.RETRYABLE; reason = "RUNTIME_DELIVERY_FAILED";
        } else if (status == DispatchRequestStatus.DEAD_LETTER || status == DispatchRequestStatus.REJECTED || status == DispatchRequestStatus.CANCELLED || status == DispatchRequestStatus.SUPPRESSED) {
            s = TaskExecutionJourneyStatus.FAILED_FINAL; retry = TaskExecutionRetryability.MANUAL_RECONCILIATION; reason = "RUNTIME_DELIVERY_TERMINAL_FAILURE";
        } else {
            s = TaskExecutionJourneyStatus.IN_PROGRESS; retry = TaskExecutionRetryability.UNKNOWN; reason = "RUNTIME_DELIVERY_IN_PROGRESS";
        }
        return stage(TaskExecutionJourneyStageCode.DELIVERY, s, true, reason,
                firstNonBlank(dispatch.getLastError(), dispatch.getReason(), "Dispatch status is " + enumName(status) + "."),
                dispatch.getCreatedAt(), delivered ? first(dispatch.getDispatchedAt(), dispatch.getUpdatedAt()) : null,
                first(dispatch.getUpdatedAt(), dispatch.getDispatchedAt(), dispatch.getCreatedAt()),
                refs(ref("DISPATCH_REQUEST", dispatch.getDispatchRequestId(), "DispatchRequest", first(dispatch.getDispatchedAt(), dispatch.getCreatedAt()), attrs(
                        "status", enumName(status), "agentId", dispatch.getAgentId(), "assignmentId", dispatch.getAssignmentId()))),
                retry, dispatch.getNextRetryAt(), "DispatchRequest", revisionFor(dispatch.getDispatchRequestId(), dispatch.getRowVersion(), status, dispatch.getUpdatedAt(), dispatch.getDispatchedAt()));
    }

    private TaskExecutionJourneyStage ackStage(DispatchRequest dispatch, TaskCallbackRecord ack) {
        if (ack != null) {
            return stage(TaskExecutionJourneyStageCode.ACK, TaskExecutionJourneyStatus.SUCCEEDED, true, "AGENT_ACK_ACCEPTED",
                    firstNonBlank(ack.getMessage(), "Accepted Agent ACK callback is present."), first(ack.getOccurredAt(), ack.getProcessedAt()),
                    first(ack.getProcessedAt(), ack.getOccurredAt()), first(ack.getProcessedAt(), ack.getOccurredAt()),
                    refs(ref("TASK_CALLBACK", ack.getCallbackId(), "TaskCallbackRecord", first(ack.getProcessedAt(), ack.getOccurredAt()), attrs(
                            "callbackType", enumName(ack.getCallbackType()), "dispatchRequestId", ack.getDispatchRequestId(), "agentId", ack.getAgentId()))),
                    TaskExecutionRetryability.NOT_APPLICABLE, null, "Accepted TaskCallbackRecord(ACK)",
                    revisionFor(ack.getCallbackId(), ack.getProcessedAt(), ack.getCallbackFingerprint()));
        }
        if (dispatch != null && (dispatch.getAckedAt() != null || !blank(dispatch.getAckEvidenceId()))) {
            return stage(TaskExecutionJourneyStageCode.ACK, TaskExecutionJourneyStatus.SUCCEEDED, true, "DISPATCH_ACK_EVIDENCE_PRESENT",
                    "DispatchRequest contains authoritative ACK evidence.", dispatch.getDispatchedAt(), first(dispatch.getAckedAt(), dispatch.getUpdatedAt()),
                    first(dispatch.getAckedAt(), dispatch.getUpdatedAt()),
                    refs(ref("DISPATCH_ACK", firstNonBlank(dispatch.getAckEvidenceId(), dispatch.getLastCallbackId(), dispatch.getDispatchRequestId()),
                            "DispatchRequest ACK evidence", first(dispatch.getAckedAt(), dispatch.getUpdatedAt()), attrs("dispatchRequestId", dispatch.getDispatchRequestId()))),
                    TaskExecutionRetryability.NOT_APPLICABLE, null, "DispatchRequest.ackEvidenceId/ackedAt",
                    revisionFor(dispatch.getDispatchRequestId(), dispatch.getAckEvidenceId(), dispatch.getAckedAt(), dispatch.getRowVersion()));
        }
        if (dispatchFailedBeforeCallback(dispatch)) {
            return stage(TaskExecutionJourneyStageCode.ACK, TaskExecutionJourneyStatus.BLOCKED, true, "ACK_NOT_OBSERVED_BEFORE_DISPATCH_FAILURE",
                    "Dispatch terminated before accepted Agent ACK evidence was observed.", dispatch.getDispatchedAt(), null, dispatch.getUpdatedAt(),
                    refs(ref("DISPATCH_REQUEST", dispatch.getDispatchRequestId(), "DispatchRequest", dispatch.getUpdatedAt(), attrs("status", enumName(dispatch.getStatus())))),
                    TaskExecutionRetryability.MANUAL_RECONCILIATION, dispatch.getNextRetryAt(), "Accepted callback / Dispatch ACK evidence",
                    revisionFor(dispatch.getDispatchRequestId(), dispatch.getStatus(), dispatch.getUpdatedAt(), "ack:none"));
        }
        return stage(TaskExecutionJourneyStageCode.ACK, TaskExecutionJourneyStatus.PENDING, true, "AGENT_ACK_NOT_OBSERVED",
                "Waiting for accepted Agent ACK evidence.", dispatch == null ? null : dispatch.getDispatchedAt(), null,
                dispatch == null ? null : dispatch.getUpdatedAt(), List.of(), TaskExecutionRetryability.UNKNOWN,
                dispatch == null ? null : dispatch.getNextRetryAt(), "Accepted TaskCallbackRecord(ACK) / Dispatch ACK evidence",
                revisionFor("ack:none", dispatch == null ? null : dispatch.getRowVersion(), dispatch == null ? null : dispatch.getUpdatedAt()));
    }

    private TaskExecutionJourneyStage resultStage(DispatchRequest dispatch, TaskCallbackRecord result) {
        if (result != null) {
            String reason = result.getCallbackType() == TaskCallbackType.ERROR ? "AGENT_ERROR_ACCEPTED" : "AGENT_RESULT_ACCEPTED";
            return stage(TaskExecutionJourneyStageCode.RESULT, TaskExecutionJourneyStatus.SUCCEEDED, true, reason,
                    firstNonBlank(result.getMessage(), result.getErrorMessage(), "Accepted Agent terminal callback is present."),
                    first(result.getOccurredAt(), result.getProcessedAt()), first(result.getProcessedAt(), result.getOccurredAt()),
                    first(result.getProcessedAt(), result.getOccurredAt()),
                    refs(ref("TASK_CALLBACK", result.getCallbackId(), "TaskCallbackRecord", first(result.getProcessedAt(), result.getOccurredAt()), attrs(
                            "callbackType", enumName(result.getCallbackType()), "errorCode", result.getErrorCode(), "newTaskStatus", result.getNewTaskStatus()))),
                    TaskExecutionRetryability.NOT_APPLICABLE, null, "Accepted TaskCallbackRecord(RESULT|ERROR)",
                    revisionFor(result.getCallbackId(), result.getCallbackType(), result.getProcessedAt(), result.getCallbackFingerprint()));
        }
        if (dispatchFailedBeforeCallback(dispatch)) {
            return stage(TaskExecutionJourneyStageCode.RESULT, TaskExecutionJourneyStatus.BLOCKED, true, "RESULT_NOT_OBSERVED_BEFORE_DISPATCH_FAILURE",
                    "Dispatch terminated without accepted RESULT/ERROR callback evidence.", dispatch.getDispatchedAt(), null, dispatch.getUpdatedAt(),
                    refs(ref("DISPATCH_REQUEST", dispatch.getDispatchRequestId(), "DispatchRequest", dispatch.getUpdatedAt(), attrs("status", enumName(dispatch.getStatus())))),
                    TaskExecutionRetryability.MANUAL_RECONCILIATION, dispatch.getNextRetryAt(), "Accepted TaskCallbackRecord(RESULT|ERROR)",
                    revisionFor(dispatch.getDispatchRequestId(), dispatch.getStatus(), dispatch.getUpdatedAt(), "result:none"));
        }
        return stage(TaskExecutionJourneyStageCode.RESULT, TaskExecutionJourneyStatus.PENDING, true, "AGENT_RESULT_NOT_OBSERVED",
                "Waiting for accepted Agent RESULT or ERROR callback evidence.", dispatch == null ? null : dispatch.getDispatchedAt(), null,
                dispatch == null ? null : dispatch.getUpdatedAt(), List.of(), TaskExecutionRetryability.UNKNOWN,
                dispatch == null ? null : dispatch.getNextRetryAt(), "Accepted TaskCallbackRecord(RESULT|ERROR)",
                revisionFor("result:none", dispatch == null ? null : dispatch.getRowVersion(), dispatch == null ? null : dispatch.getUpdatedAt()));
    }

    private TaskExecutionJourneyStage issuePolicyStage(TaskCallbackRecord result, TaskRecord task, IssuePolicyDecision policy) {
        if (policy == null) {
            boolean terminalEvidence = result != null;
            return stage(TaskExecutionJourneyStageCode.ISSUE_POLICY,
                    terminalEvidence ? TaskExecutionJourneyStatus.PENDING : TaskExecutionJourneyStatus.NOT_STARTED,
                    true, terminalEvidence ? "ISSUE_POLICY_DECISION_PENDING" : "ISSUE_POLICY_WAITING_FOR_RESULT",
                    terminalEvidence ? "Agent terminal callback exists; waiting for durable IssuePolicyDecision." : "Issue policy is evaluated after terminal Agent RESULT/ERROR.",
                    result == null ? null : first(result.getProcessedAt(), result.getOccurredAt()), null, task.getUpdatedAt(), List.of(),
                    TaskExecutionRetryability.UNKNOWN, null, "IssuePolicyDecision", revisionFor("policy:none", task.getVersion(), result == null ? null : result.getCallbackId()));
        }
        TaskExecutionJourneyStatus status;
        TaskExecutionRetryability retryability;
        if (policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) {
            status = TaskExecutionJourneyStatus.SUCCEEDED; retryability = TaskExecutionRetryability.NOT_APPLICABLE;
        } else if (policy.decision() == IssuePolicyDecisionOutcome.MANUAL_DECISION) {
            status = TaskExecutionJourneyStatus.BLOCKED; retryability = TaskExecutionRetryability.MANUAL_RECONCILIATION;
        } else if (policy.bindingStatus() != IssuePolicyBindingStatus.RESOLVED) {
            status = TaskExecutionJourneyStatus.BLOCKED; retryability = TaskExecutionRetryability.MANUAL_RECONCILIATION;
        } else if (policy.automationStatus() == IssuePolicyAutomationStatus.FAILED) {
            status = TaskExecutionJourneyStatus.FAILED_RETRYABLE; retryability = TaskExecutionRetryability.RETRYABLE;
        } else {
            status = TaskExecutionJourneyStatus.SUCCEEDED; retryability = TaskExecutionRetryability.NOT_APPLICABLE;
        }
        return stage(TaskExecutionJourneyStageCode.ISSUE_POLICY, status, true,
                firstNonBlank(policy.reasonCode(), "ISSUE_POLICY_" + policy.decision().name()),
                "Issue policy decision: " + policy.decision().name() + "; binding: " + policy.bindingStatus().name() + ".",
                policy.createdAt(), status == TaskExecutionJourneyStatus.SUCCEEDED ? policy.updatedAt() : null, policy.updatedAt(),
                refs(ref("ISSUE_POLICY_DECISION", policy.decisionId(), "IssuePolicyDecision", policy.updatedAt(), attrs(
                        "decision", policy.decision().name(), "bindingStatus", policy.bindingStatus().name(),
                        "automationStatus", policy.automationStatus().name(), "projectMappingId", policy.projectMappingId()))),
                retryability, null, "IssuePolicyDecision", revisionFor(policy.decisionId(), policy.version(), policy.updatedAt(), policy.decision(), policy.bindingStatus(), policy.automationStatus()));
    }

    /**
     * API-compatible ISSUE_INTENT stage reinterpreted as Route B governed binding authority.
     * The stage code is retained to avoid breaking existing journey consumers.
     */
    private TaskExecutionJourneyStage issueBindingStage(IssuePolicyDecision policy) {
        if (policy == null) return waiting(TaskExecutionJourneyStageCode.ISSUE_INTENT, "ISSUE_BINDING_WAITING_FOR_POLICY",
                "Route B binding waits for the durable Issue policy decision.", false, null, "IssuePolicyDecision(binding)");
        if (policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED)
            return notApplicable(TaskExecutionJourneyStageCode.ISSUE_INTENT, "ISSUE_NOT_REQUIRED", policy.updatedAt(), "IssuePolicyDecision(binding)");
        if (policy.decision() == IssuePolicyDecisionOutcome.MANUAL_DECISION) {
            return stage(TaskExecutionJourneyStageCode.ISSUE_INTENT, TaskExecutionJourneyStatus.BLOCKED, true, "ISSUE_MANUAL_DECISION_REQUIRED",
                    "Issue creation requires an administrator decision before Route B binding can continue.", policy.createdAt(), null, policy.updatedAt(),
                    policyRefs(policy), TaskExecutionRetryability.MANUAL_RECONCILIATION, null, "IssuePolicyDecision(binding)",
                    revisionFor(policy.decisionId(), policy.version(), policy.bindingStatus(), policy.updatedAt()));
        }
        if (policy.bindingStatus() == IssuePolicyBindingStatus.RESOLVED) {
            return stage(TaskExecutionJourneyStageCode.ISSUE_INTENT, TaskExecutionJourneyStatus.SUCCEEDED, true, "ISSUE_BINDING_RESOLVED",
                    "Governed Issue connection and project mapping are resolved for Route B.", policy.createdAt(), policy.updatedAt(), policy.updatedAt(),
                    policyRefs(policy), TaskExecutionRetryability.NOT_APPLICABLE, null, "IssuePolicyDecision(binding)",
                    revisionFor(policy.decisionId(), policy.version(), policy.connectionId(), policy.projectMappingId(), policy.updatedAt()));
        }
        boolean pending = policy.bindingStatus() == IssuePolicyBindingStatus.NOT_EVALUATED;
        return stage(TaskExecutionJourneyStageCode.ISSUE_INTENT,
                pending ? TaskExecutionJourneyStatus.PENDING : TaskExecutionJourneyStatus.BLOCKED,
                true, firstNonBlank(policy.lastErrorCode(), "ISSUE_BINDING_" + policy.bindingStatus().name()),
                firstNonBlank(policy.lastErrorMessage(), "Route B binding status is " + policy.bindingStatus().name() + "."),
                policy.createdAt(), null, policy.updatedAt(), policyRefs(policy),
                pending ? TaskExecutionRetryability.UNKNOWN : TaskExecutionRetryability.MANUAL_RECONCILIATION,
                null, "IssuePolicyDecision(binding)", revisionFor(policy.decisionId(), policy.version(), policy.bindingStatus(), policy.lastErrorCode(), policy.updatedAt()));
    }

    /** API-compatible ISSUE_MATERIALIZATION stage; canonical authority is now AdapterAction. */
    private TaskExecutionJourneyStage issueAdapterActionStage(IssuePolicyDecision policy, AdapterAction action) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED)
            return notApplicable(TaskExecutionJourneyStageCode.ISSUE_MATERIALIZATION, "ISSUE_NOT_REQUIRED", policy.updatedAt(), "IssuePolicyDecision + AdapterAction");
        if (policy == null || policy.decision() == IssuePolicyDecisionOutcome.MANUAL_DECISION || policy.bindingStatus() != IssuePolicyBindingStatus.RESOLVED)
            return waiting(TaskExecutionJourneyStageCode.ISSUE_MATERIALIZATION, "ISSUE_ADAPTER_ACTION_WAITING_FOR_BINDING",
                    "AdapterAction is created only after Route B policy and binding are resolved.", policy != null && policy.decision() == IssuePolicyDecisionOutcome.REQUIRED,
                    null, "IssuePolicyDecision + AdapterAction");
        if (action == null) {
            if (policy.automationStatus() == IssuePolicyAutomationStatus.FAILED) {
                return stage(TaskExecutionJourneyStageCode.ISSUE_MATERIALIZATION, TaskExecutionJourneyStatus.FAILED_RETRYABLE, true,
                        firstNonBlank(policy.lastErrorCode(), "ISSUE_ADAPTER_ACTION_REQUEST_FAILED"),
                        firstNonBlank(policy.lastErrorMessage(), "Route B AdapterAction request failed and awaits retry."), policy.createdAt(), null, policy.updatedAt(),
                        policyRefs(policy), TaskExecutionRetryability.RETRYABLE, null, "IssuePolicyDecision + AdapterAction",
                        revisionFor(policy.decisionId(), policy.version(), policy.automationStatus(), policy.lastErrorCode(), policy.updatedAt()));
            }
            return waiting(TaskExecutionJourneyStageCode.ISSUE_MATERIALIZATION, "ISSUE_ADAPTER_ACTION_NOT_CREATED",
                    "Route B requires an Issue AdapterAction, but no durable action exists yet.", true, null, "AdapterAction");
        }
        TaskExecutionJourneyStatus status = switch (action.getStatus()) {
            case COMPLETED -> TaskExecutionJourneyStatus.SUCCEEDED;
            case FAILED, CANCELLED, SUPPRESSED -> action.getNextAttemptAt() != null ? TaskExecutionJourneyStatus.FAILED_RETRYABLE : TaskExecutionJourneyStatus.FAILED_FINAL;
            case CLAIMED, EXECUTING -> TaskExecutionJourneyStatus.IN_PROGRESS;
            case RETRY_WAITING, EXECUTOR_UNAVAILABLE -> TaskExecutionJourneyStatus.FAILED_RETRYABLE;
            case PENDING -> TaskExecutionJourneyStatus.PENDING;
        };
        TaskExecutionRetryability retry = switch (status) {
            case FAILED_RETRYABLE -> TaskExecutionRetryability.RETRYABLE;
            case FAILED_FINAL -> TaskExecutionRetryability.MANUAL_RECONCILIATION;
            case SUCCEEDED -> TaskExecutionRetryability.NOT_APPLICABLE;
            default -> TaskExecutionRetryability.UNKNOWN;
        };
        return stage(TaskExecutionJourneyStageCode.ISSUE_MATERIALIZATION, status, true, "ISSUE_ADAPTER_ACTION_" + action.getStatus().name(),
                firstNonBlank(action.getLastError(), "Route B Issue AdapterAction status is " + action.getStatus().name() + "."), action.getCreatedAt(),
                status == TaskExecutionJourneyStatus.SUCCEEDED ? action.getCompletedAt() : null, first(action.getUpdatedAt(), action.getCreatedAt()),
                refs(actionRef(action)), retry, action.getNextAttemptAt(), "AdapterAction",
                revisionFor(action.getActionId(), action.getStatus(), action.getAttemptCount(), action.getUpdatedAt(), action.getLastError()));
    }

    /** API-compatible OUTBOX stage; legacy Outbox authority is retired from PRIMARY_ISSUE and this stage represents executor progress. */
    private TaskExecutionJourneyStage issueExecutorStage(IssuePolicyDecision policy, AdapterAction action) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED)
            return notApplicable(TaskExecutionJourneyStageCode.OUTBOX, "ISSUE_NOT_REQUIRED", policy.updatedAt(), "AdapterAction executor");
        if (action == null) return waiting(TaskExecutionJourneyStageCode.OUTBOX, "ISSUE_EXECUTOR_WAITING_FOR_ACTION",
                "Adapter executor waits for the Route B Issue AdapterAction.", false, null, "AdapterAction executor");
        TaskExecutionJourneyStatus status = switch (action.getStatus()) {
            case PENDING -> TaskExecutionJourneyStatus.NOT_STARTED;
            case CLAIMED, EXECUTING -> TaskExecutionJourneyStatus.IN_PROGRESS;
            case RETRY_WAITING, EXECUTOR_UNAVAILABLE -> TaskExecutionJourneyStatus.FAILED_RETRYABLE;
            case COMPLETED -> TaskExecutionJourneyStatus.SUCCEEDED;
            case FAILED, CANCELLED, SUPPRESSED -> action.getNextAttemptAt() != null ? TaskExecutionJourneyStatus.FAILED_RETRYABLE : TaskExecutionJourneyStatus.FAILED_FINAL;
        };
        TaskExecutionRetryability retry = status == TaskExecutionJourneyStatus.FAILED_RETRYABLE ? TaskExecutionRetryability.RETRYABLE
                : status == TaskExecutionJourneyStatus.FAILED_FINAL ? TaskExecutionRetryability.MANUAL_RECONCILIATION
                : status == TaskExecutionJourneyStatus.SUCCEEDED ? TaskExecutionRetryability.NOT_APPLICABLE : TaskExecutionRetryability.UNKNOWN;
        return stage(TaskExecutionJourneyStageCode.OUTBOX, status, true, "ISSUE_EXECUTOR_" + action.getStatus().name(),
                firstNonBlank(action.getLastError(), "Issue adapter executor status is " + action.getStatus().name() + "."),
                first(action.getClaimedAt(), action.getExecutingAt(), action.getCreatedAt()), status == TaskExecutionJourneyStatus.SUCCEEDED ? action.getCompletedAt() : null,
                first(action.getUpdatedAt(), action.getCreatedAt()), refs(actionRef(action)), retry, action.getNextAttemptAt(), "AdapterAction executor",
                revisionFor(action.getActionId(), action.getStatus(), action.getClaimedBy(), action.getExecutorName(), action.getAttemptCount(), action.getUpdatedAt()));
    }

    private TaskExecutionJourneyStage issueProviderStage(IssuePolicyDecision policy, AdapterAction action, List<AdapterExecutorAuditRecord> audits) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED)
            return notApplicable(TaskExecutionJourneyStageCode.PROVIDER, "ISSUE_NOT_REQUIRED", policy.updatedAt(), "AdapterExecutorAuditRecord(provider)");
        if (action == null) return waiting(TaskExecutionJourneyStageCode.PROVIDER, "ISSUE_PROVIDER_WAITING_FOR_ACTION",
                "Provider invocation waits for an Issue AdapterAction.", false, null, "AdapterExecutorAuditRecord(provider)");
        AdapterExecutorAuditRecord latest = latest(audits, AdapterExecutorAuditRecord::getCreatedAt);
        if (latest == null) {
            TaskExecutionJourneyStatus status = action.getStatus() == AdapterActionStatus.COMPLETED ? TaskExecutionJourneyStatus.PENDING : TaskExecutionJourneyStatus.NOT_STARTED;
            return stage(TaskExecutionJourneyStageCode.PROVIDER, status, true, "ISSUE_PROVIDER_EVIDENCE_NOT_OBSERVED",
                    action.getStatus() == AdapterActionStatus.COMPLETED
                            ? "AdapterAction completed but provider execution audit has not been observed yet."
                            : "Provider execution has not started yet.", action.getExecutingAt(), null, action.getUpdatedAt(), refs(actionRef(action)),
                    TaskExecutionRetryability.UNKNOWN, action.getNextAttemptAt(), "AdapterExecutorAuditRecord(provider)",
                    revisionFor(action.getActionId(), action.getStatus(), "provider:none", action.getUpdatedAt()));
        }
        String outcome = safeUpper(latest.getOutcome());
        boolean success = "SUCCESS".equals(outcome) || (latest.getProviderStatusCode() != null && latest.getProviderStatusCode() >= 200 && latest.getProviderStatusCode() < 300);
        boolean retryable = "RETRYABLE_FAILURE".equals(outcome) || "EXECUTOR_UNAVAILABLE".equals(outcome) || "TIMEOUT".equals(outcome) || "OUTCOME_UNCERTAIN".equals(outcome);
        TaskExecutionJourneyStatus status = success ? TaskExecutionJourneyStatus.SUCCEEDED
                : retryable ? TaskExecutionJourneyStatus.FAILED_RETRYABLE : TaskExecutionJourneyStatus.FAILED_FINAL;
        return stage(TaskExecutionJourneyStageCode.PROVIDER, status, true,
                firstNonBlank(latest.getProviderFailureCode(), "ISSUE_PROVIDER_" + outcome),
                firstNonBlank(latest.getMessage(), "Provider execution outcome is " + outcome + "."), latest.getCreatedAt(),
                success ? latest.getCreatedAt() : null, latest.getCreatedAt(), providerAuditRefs(audits),
                retryable ? TaskExecutionRetryability.RETRYABLE : success ? TaskExecutionRetryability.NOT_APPLICABLE : TaskExecutionRetryability.MANUAL_RECONCILIATION,
                action.getNextAttemptAt(), "AdapterExecutorAuditRecord(provider)",
                revisionFor(latest.getAuditId(), latest.getOutcome(), latest.getProviderStatusCode(), latest.getProviderFailureCode(), latest.getCreatedAt()));
    }

    /** API-compatible READBACK stage now represents Route B provider outcome certainty/reconciliation need. */
    private TaskExecutionJourneyStage issueProviderCertaintyStage(IssuePolicyDecision policy, AdapterAction action, List<AdapterExecutorAuditRecord> audits) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED)
            return notApplicable(TaskExecutionJourneyStageCode.READBACK, "ISSUE_NOT_REQUIRED", policy.updatedAt(), "AdapterExecutorAuditRecord(provider certainty)");
        AdapterExecutorAuditRecord latest = latest(audits, AdapterExecutorAuditRecord::getCreatedAt);
        if (latest == null) return waiting(TaskExecutionJourneyStageCode.READBACK, "ISSUE_PROVIDER_CERTAINTY_NOT_OBSERVED",
                "Provider outcome certainty is not available yet.", false, action == null ? null : action.getNextAttemptAt(), "AdapterExecutorAuditRecord(provider certainty)");
        String certainty = safeUpper(latest.getProviderOutcomeCertainty());
        if ("UNCERTAIN".equals(certainty)) {
            return stage(TaskExecutionJourneyStageCode.READBACK, TaskExecutionJourneyStatus.BLOCKED, true, "ISSUE_PROVIDER_OUTCOME_UNCERTAIN",
                    "Provider outcome is uncertain and requires reconciliation before the Issue result can be trusted.", latest.getCreatedAt(), null, latest.getCreatedAt(),
                    refs(providerAuditRef(latest)), TaskExecutionRetryability.MANUAL_RECONCILIATION, action == null ? null : action.getNextAttemptAt(),
                    "AdapterExecutorAuditRecord(provider certainty)", revisionFor(latest.getAuditId(), latest.getProviderOutcomeCertainty(), latest.getCreatedAt()));
        }
        return notApplicable(TaskExecutionJourneyStageCode.READBACK,
                blank(latest.getProviderOutcomeCertainty()) ? "ISSUE_PROVIDER_CERTAINTY_NOT_REPORTED" : "ISSUE_PROVIDER_DIRECT_CONFIRMATION",
                latest.getCreatedAt(), "AdapterExecutorAuditRecord(provider certainty)");
    }

    /** API-compatible PROJECTION_SYNCED stage; canonical completion authority is TaskIssueLink. */
    private TaskExecutionJourneyStage issueLinkStage(IssuePolicyDecision policy, AdapterAction action, List<AdapterExecutorAuditRecord> audits, TaskIssueLink link) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED)
            return notApplicable(TaskExecutionJourneyStageCode.PROJECTION_SYNCED, "ISSUE_NOT_REQUIRED", policy.updatedAt(), "TaskIssueLink");
        if (action == null || action.getStatus() != AdapterActionStatus.COMPLETED) {
            return waiting(TaskExecutionJourneyStageCode.PROJECTION_SYNCED, "TASK_ISSUE_LINK_WAITING_FOR_PROVIDER",
                    "TaskIssueLink is created after the Route B provider operation completes.", false, action == null ? null : action.getNextAttemptAt(), "TaskIssueLink");
        }
        if (link == null) {
            AdapterExecutorAuditRecord latest = latest(audits, AdapterExecutorAuditRecord::getCreatedAt);
            return stage(TaskExecutionJourneyStageCode.PROJECTION_SYNCED, TaskExecutionJourneyStatus.IN_PROGRESS, true, "TASK_ISSUE_LINK_NOT_PERSISTED",
                    "Provider execution completed, but the Task Issue read model is not available yet.", action.getCompletedAt(), null,
                    latest == null ? action.getUpdatedAt() : latest.getCreatedAt(), refs(actionRef(action)), TaskExecutionRetryability.UNKNOWN,
                    null, "TaskIssueLink", revisionFor(action.getActionId(), action.getCompletedAt(), "link:none", latest == null ? null : latest.getAuditId()));
        }
        String syncStatus = safeUpper(link.getSyncStatus());
        boolean linked = TaskIssueLink.LINK_EXTERNAL_CONFIRMED.equalsIgnoreCase(link.getLinkState())
                || !blank(link.getExternalIssueId()) || !blank(link.getIssueId()) || !blank(link.getIssueUrl());
        if (TaskIssueLink.SYNCED.equalsIgnoreCase(syncStatus) && linked) {
            return stage(TaskExecutionJourneyStageCode.PROJECTION_SYNCED, TaskExecutionJourneyStatus.SUCCEEDED, true, "TASK_ISSUE_LINK_EXTERNALLY_CONFIRMED",
                    "External Issue and TaskIssueLink are confirmed from the Route B provider result.", link.getCreatedAt(),
                    first(link.getLastSyncedAt(), link.getUpdatedAt()), link.getUpdatedAt(), refs(linkRef(link)), TaskExecutionRetryability.NOT_APPLICABLE,
                    null, "TaskIssueLink", revisionFor(link.getLinkId(), link.getResourceVersion(), link.getLinkState(), link.getSyncStatus(), link.getExternalIssueId(), link.getUpdatedAt()));
        }
        boolean failed = TaskIssueLink.SYNC_FAILED.equalsIgnoreCase(syncStatus) || !blank(link.getSyncError());
        return stage(TaskExecutionJourneyStageCode.PROJECTION_SYNCED,
                failed ? TaskExecutionJourneyStatus.FAILED_RETRYABLE : TaskExecutionJourneyStatus.IN_PROGRESS,
                true, firstNonBlank(link.getProviderFailureCode(), failed ? "TASK_ISSUE_LINK_SYNC_FAILED" : "TASK_ISSUE_LINK_PENDING"),
                firstNonBlank(link.getSyncError(), link.getMessage(), "TaskIssueLink is waiting for external confirmation."), link.getCreatedAt(), null, link.getUpdatedAt(),
                refs(linkRef(link)), failed ? TaskExecutionRetryability.RETRYABLE : TaskExecutionRetryability.UNKNOWN, null, "TaskIssueLink",
                revisionFor(link.getLinkId(), link.getResourceVersion(), link.getLinkState(), link.getSyncStatus(), link.getProviderFailureCode(), link.getUpdatedAt()));
    }

    private AdapterAction primaryIssueAction(String taskId, IssuePolicyDecision policy) {
        if (policy != null && !blank(policy.adapterActionId())) {
            Optional<AdapterAction> direct = adapterActions.findById(policy.adapterActionId());
            if (direct.isPresent() && direct.get().getAdapterType() == AdapterType.ISSUE_TRACKING) return direct.get();
        }
        return adapterActions.findByTaskId(taskId, 100).stream()
                .filter(value -> value != null && value.getAdapterType() == AdapterType.ISSUE_TRACKING)
                .max(Comparator.comparing(value -> first(value.getUpdatedAt(), value.getCreatedAt(), OffsetDateTime.MIN)))
                .orElse(null);
    }

    private List<TaskExecutionEvidenceRef> policyRefs(IssuePolicyDecision policy) {
        if (policy == null) return List.of();
        return refs(ref("ISSUE_POLICY_DECISION", policy.decisionId(), "IssuePolicyDecision", policy.updatedAt(), attrs(
                "decision", policy.decision().name(), "bindingStatus", policy.bindingStatus().name(), "automationStatus", policy.automationStatus().name(),
                "connectionId", policy.connectionId(), "projectMappingId", policy.projectMappingId(), "adapterActionId", policy.adapterActionId())));
    }

    private TaskExecutionEvidenceRef actionRef(AdapterAction action) {
        if (action == null) return null;
        return ref("ISSUE_ADAPTER_ACTION", action.getActionId(), "AdapterAction", first(action.getUpdatedAt(), action.getCreatedAt()), attrs(
                "status", enumName(action.getStatus()), "adapterType", enumName(action.getAdapterType()), "actionType", enumName(action.getActionType()),
                "attemptCount", action.getAttemptCount(), "executorName", action.getExecutorName()));
    }

    private List<TaskExecutionEvidenceRef> providerAuditRefs(List<AdapterExecutorAuditRecord> audits) {
        if (audits == null || audits.isEmpty()) return List.of();
        return audits.stream().filter(value -> value != null).map(this::providerAuditRef).toList();
    }

    private TaskExecutionEvidenceRef providerAuditRef(AdapterExecutorAuditRecord audit) {
        if (audit == null) return null;
        return ref("ISSUE_PROVIDER_EXECUTION", audit.getAuditId(), "AdapterExecutorAuditRecord", audit.getCreatedAt(), attrs(
                "outcome", audit.getOutcome(), "providerStatusCode", audit.getProviderStatusCode(), "providerFailureCode", audit.getProviderFailureCode(),
                "providerOutcomeCertainty", audit.getProviderOutcomeCertainty(), "externalIssueId", audit.getExternalIssueId(), "connectionId", audit.getConnectionId(),
                "projectMappingId", audit.getProjectMappingId()));
    }

    private TaskExecutionEvidenceRef linkRef(TaskIssueLink link) {
        if (link == null) return null;
        return ref("TASK_ISSUE_LINK", link.getLinkId(), "TaskIssueLink", link.getUpdatedAt(), attrs(
                "linkState", link.getLinkState(), "syncStatus", link.getSyncStatus(), "externalIssueId", link.getExternalIssueId(), "issueId", link.getIssueId(),
                "providerStatusCode", link.getProviderStatusCode(), "providerOutcomeCertainty", link.getProviderOutcomeCertainty()));
    }

    private TaskCallbackRecord latestAcceptedCallback(List<TaskCallbackRecord> callbacks, TaskCallbackType type) {
        if (callbacks == null) return null;
        return callbacks.stream().filter(c -> c != null && c.isAccepted() && !c.isDuplicate() && c.getCallbackType() == type)
                .max(Comparator.comparing(c -> first(c.getProcessedAt(), c.getOccurredAt(), OffsetDateTime.MIN))).orElse(null);
    }

    private TaskCallbackRecord latestAcceptedTerminalCallback(List<TaskCallbackRecord> callbacks) {
        if (callbacks == null) return null;
        return callbacks.stream().filter(c -> c != null && c.isAccepted() && !c.isDuplicate()
                        && (c.getCallbackType() == TaskCallbackType.RESULT || c.getCallbackType() == TaskCallbackType.ERROR))
                .max(Comparator.comparing(c -> first(c.getProcessedAt(), c.getOccurredAt(), OffsetDateTime.MIN))).orElse(null);
    }

    private boolean dispatchFailedBeforeCallback(DispatchRequest dispatch) {
        if (dispatch == null || dispatch.getStatus() == null) return false;
        return dispatch.getStatus() == DispatchRequestStatus.FAILED || dispatch.getStatus() == DispatchRequestStatus.TIMED_OUT
                || dispatch.getStatus() == DispatchRequestStatus.DEAD_LETTER || dispatch.getStatus() == DispatchRequestStatus.CANCELLED
                || dispatch.getStatus() == DispatchRequestStatus.REJECTED;
    }

    private TaskExecutionJourneyStatus overallStatus(List<TaskExecutionJourneyStage> stages) {
        if (stages.stream().anyMatch(s -> s.status() == TaskExecutionJourneyStatus.CONFLICT)) return TaskExecutionJourneyStatus.CONFLICT;
        if (stages.stream().anyMatch(s -> s.status() == TaskExecutionJourneyStatus.FAILED_FINAL)) return TaskExecutionJourneyStatus.FAILED_FINAL;
        if (stages.stream().anyMatch(s -> s.status() == TaskExecutionJourneyStatus.BLOCKED)) return TaskExecutionJourneyStatus.BLOCKED;
        if (stages.stream().anyMatch(s -> s.status() == TaskExecutionJourneyStatus.FAILED_RETRYABLE)) return TaskExecutionJourneyStatus.FAILED_RETRYABLE;
        if (stages.stream().allMatch(s -> s.status() == TaskExecutionJourneyStatus.SUCCEEDED || s.status() == TaskExecutionJourneyStatus.NOT_APPLICABLE)) return TaskExecutionJourneyStatus.SUCCEEDED;
        if (stages.stream().anyMatch(s -> s.status() == TaskExecutionJourneyStatus.IN_PROGRESS)) return TaskExecutionJourneyStatus.IN_PROGRESS;
        return TaskExecutionJourneyStatus.PENDING;
    }

    private TaskExecutionJourneyStage notApplicable(TaskExecutionJourneyStageCode code, String reason, OffsetDateTime at, String authority) {
        return stage(code, TaskExecutionJourneyStatus.NOT_APPLICABLE, false, reason, "This stage is not required for the current Task policy.",
                at, at, at, List.of(), TaskExecutionRetryability.NOT_APPLICABLE, null, authority, revisionFor(code, reason, at));
    }

    private TaskExecutionJourneyStage waiting(TaskExecutionJourneyStageCode code, String reason, String summary, boolean required, OffsetDateTime retryAfter, String authority) {
        return stage(code, TaskExecutionJourneyStatus.NOT_STARTED, required, reason, summary, null, null, null, List.of(),
                TaskExecutionRetryability.UNKNOWN, retryAfter, authority, revisionFor(code, reason, retryAfter));
    }

    private TaskExecutionJourneyStage stage(TaskExecutionJourneyStageCode code, TaskExecutionJourneyStatus status, boolean required,
            String reason, String summary, OffsetDateTime startedAt, OffsetDateTime completedAt, OffsetDateTime changedAt,
            List<TaskExecutionEvidenceRef> refs, TaskExecutionRetryability retryability, OffsetDateTime retryAfter, String authority, long revision) {
        return new TaskExecutionJourneyStage(code, status, required, reason, summary, startedAt, completedAt, changedAt,
                refs, retryability, retryAfter, authority, revision);
    }

    private TaskExecutionEvidenceRef ref(String type, String id, String authority, OffsetDateTime at, Map<String, String> attributes) {
        return new TaskExecutionEvidenceRef(type, id, authority, at, attributes);
    }

    private List<TaskExecutionEvidenceRef> refs(TaskExecutionEvidenceRef... values) {
        List<TaskExecutionEvidenceRef> result = new ArrayList<>();
        if (values != null) for (TaskExecutionEvidenceRef value : values) if (value != null && !blank(value.evidenceId())) result.add(value);
        return result;
    }

    private Map<String, String> attrs(Object... values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values == null) return result;
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i] == null || values[i + 1] == null) continue;
            String value = String.valueOf(values[i + 1]);
            if (!value.isBlank()) result.put(String.valueOf(values[i]), value);
        }
        return result;
    }

    private long revisionFor(Object... values) {
        long hash = 0xcbf29ce484222325L;
        if (values != null) for (Object value : values) {
            String text = String.valueOf(value);
            for (int i = 0; i < text.length(); i++) {
                hash ^= text.charAt(i);
                hash *= 0x100000001b3L;
            }
        }
        return hash == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(hash);
    }

    private <T> T latest(List<T> values, java.util.function.Function<T, OffsetDateTime> time) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().filter(v -> v != null).max(Comparator.comparing(v -> first(time.apply(v), OffsetDateTime.MIN))).orElse(null);
    }

    @SafeVarargs
    private final <T> T first(T... values) {
        if (values == null) return null;
        for (T value : values) if (value != null) return value;
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value.trim();
        return null;
    }

    private String enumName(Object value) { return value == null ? "UNKNOWN" : value.toString(); }
    private String safeUpper(String value) { return blank(value) ? "UNKNOWN" : value.trim().toUpperCase(java.util.Locale.ROOT); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
