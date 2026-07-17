package com.opensocket.aievent.core.decision;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.core.task.TaskDecisionResult;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.observability.CoreMetricsService;
import com.opensocket.aievent.core.processing.EventProcessingFacade;
import com.opensocket.aievent.core.processing.EventProcessingResult;

@Service
public class DecisionEngine {
    private final EventProcessingFacade eventProcessingFacade;
    private final EventDecisionRepository decisionRepository;
    private final TaskOrchestrationFacade taskOrchestrationFacade;

    @Autowired(required = false)
    private CoreMetricsService metrics;

    public DecisionEngine(EventProcessingFacade eventProcessingFacade,
                          EventDecisionRepository decisionRepository,
                          TaskOrchestrationFacade taskOrchestrationFacade) {
        this.eventProcessingFacade = eventProcessingFacade;
        this.decisionRepository = decisionRepository;
        this.taskOrchestrationFacade = taskOrchestrationFacade;
    }

    @Transactional
    public EventIntakeDecisionResponse ingest(EventIntakeRequest request) {
        long startedAt = System.nanoTime();
        EventProcessingResult processing = eventProcessingFacade.process(request);
        NormalizedEvent event = processing.event();
        String fingerprint = processing.fingerprint();
        DedupDecision dedup = processing.dedup();
        Incident incident = processing.incident();

        List<DecisionAction> actions = new ArrayList<>();
        actions.add(DecisionAction.EVENT_NORMALIZED);
        actions.add(DecisionAction.FINGERPRINT_GENERATED);
        actions.add(DecisionAction.DEDUP_EVALUATED);
        actions.add(DecisionAction.INCIDENT_OBSERVED);
        DecisionType decisionType;
        if (incident.getStatus() == IncidentStatus.ESCALATED) {
            decisionType = DecisionType.INCIDENT_ESCALATED;
            if (dedup.duplicate()) {
                actions.add(DecisionAction.UPDATE_INCIDENT_OCCURRENCE);
            } else {
                actions.add(DecisionAction.CREATE_INCIDENT);
            }
            actions.add(DecisionAction.ESCALATE_INCIDENT);
        } else if (dedup.duplicate()) {
            decisionType = DecisionType.DUPLICATE_AGGREGATED;
            actions.add(DecisionAction.UPDATE_INCIDENT_OCCURRENCE);
        } else {
            decisionType = DecisionType.INCIDENT_CREATED;
            actions.add(DecisionAction.CREATE_INCIDENT);
        }

        TaskDecisionResult taskDecision = taskOrchestrationFacade.decide(incident, event, dedup);
        actions.addAll(taskDecision.actions());

        // P5 creates task records, assignment/routing decisions, and dispatch request contracts when rules allow it. MCP and issue side effects remain explicitly suppressed.
        if (taskDecision.assignmentCreated()) {
            actions.add(DecisionAction.CREATE_ASSIGNMENT);
        } else {
            actions.add(DecisionAction.SUPPRESS_ASSIGNMENT);
        }
        if (taskDecision.dispatchRequestCreated()) {
            actions.add(DecisionAction.CREATE_DISPATCH_REQUEST);
        } else {
            actions.add(DecisionAction.SUPPRESS_DISPATCH);
        }
        actions.add(DecisionAction.SUPPRESS_MCP_CALL);
        actions.add(DecisionAction.SUPPRESS_ISSUE_CREATION);

        String reason = dedup.reason()
                + "; taskDecision=" + taskDecision.reason()
                + "; Event, Incident and Task state were persisted. External MCP and issue side effects remain suppressed unless explicitly enabled.";
        OffsetDateTime decidedAt = OffsetDateTime.now(ZoneOffset.UTC);
        EventDecisionRecord record = new EventDecisionRecord();
        record.setEventId(event.eventId());
        record.setFingerprint(fingerprint);
        record.setIncidentId(incident.getIncidentId());
        record.setDecisionType(decisionType);
        record.setDuplicate(dedup.duplicate());
        record.setOccurrenceCount(dedup.state().getOccurrenceCount());
        record.setActions(List.copyOf(actions));
        record.setReason(reason);
        record.setDecidedAt(decidedAt);
        decisionRepository.save(record);

        EventIntakeDecisionResponse response = new EventIntakeDecisionResponse(
                event.eventId(),
                fingerprint,
                incident.getIncidentId(),
                decisionType,
                dedup.duplicate(),
                dedup.state().getOccurrenceCount(),
                incident.getSeverity().name(),
                List.copyOf(actions),
                taskDecision.taskCreated(),
                taskDecision.taskId(),
                taskDecision.taskType() == null ? null : taskDecision.taskType().name(),
                taskDecision.taskSuppressed(),
                taskDecision.reason(),
                taskDecision.assignmentCreated(),
                taskDecision.assignmentId(),
                taskDecision.selectedAgentId(),
                taskDecision.selectedGatewayNodeId(),
                taskDecision.selectedSiteId(),
                taskDecision.routingDecisionId(),
                taskDecision.assignmentStatus(),
                taskDecision.assignmentReason(),
                taskDecision.dispatchRequestCreated(),
                taskDecision.dispatchRequestId(),
                taskDecision.dispatchStatus(),
                taskDecision.dispatchReviewMode(),
                taskDecision.dispatchEligibilityStatus(),
                taskDecision.dispatchGatewayPath(),
                taskDecision.dispatchReason(),
                !taskDecision.dispatchRequestCreated(),
                false,
                false,
                reason,
                decidedAt,
                event.eventStage(),
                event.originSourceSystem(),
                event.targetSystem(),
                event.requestedSkill(),
                event.handoffMode(),
                event.correlationId(),
                event.parentTaskId(),
                primaryStatus(taskDecision),
                primaryReasonCode(taskDecision),
                nextAction(taskDecision)
        );
        if (metrics != null) {
            metrics.recordIntake(event, dedup, incident, decisionType, taskDecision, Duration.ofNanos(System.nanoTime() - startedAt));
        }
        return response;
    }
    static String primaryStatus(TaskDecisionResult taskDecision) {
        if (taskDecision == null) return "UNKNOWN";
        if (taskDecision.taskSuppressed()) return "TASK_SUPPRESSED";
        if (!taskDecision.taskCreated()) return "NO_TASK_CREATED";
        if (taskDecision.assignmentCreated() && taskDecision.dispatchRequestCreated()) return "DISPATCH_QUEUED";
        String reasonCode = primaryReasonCode(taskDecision);
        if ("NO_ACTIVE_FLOW_RULE".equals(reasonCode) || "REQUIRED_CAPABILITY_MISSING".equals(reasonCode)) {
            return "WAITING_CONFIGURATION";
        }
        if (taskDecision.assignmentCreated()) return "ASSIGNED";
        return "NEEDS_ATTENTION";
    }

    static String primaryReasonCode(TaskDecisionResult taskDecision) {
        String evidence = ((taskDecision == null ? "" : taskDecision.reason()) + " "
                + (taskDecision == null ? "" : taskDecision.assignmentReason()) + " "
                + (taskDecision == null ? "" : taskDecision.dispatchReason())).toUpperCase();
        boolean flowMatched = hasFlowMatchEvidence(evidence);
        if (flowMatched) {
            if (evidence.contains("REQUIRED_CAPABILITY_MISSING") || evidence.contains("CAPABILITY_MISSING")) return "REQUIRED_CAPABILITY_MISSING";
            if (evidence.contains("AGENT_OFFLINE") || evidence.contains("RUNTIME_NOT_CONNECTED")
                    || evidence.contains("NO_ACTIVE_RUNTIME_SESSION") || evidence.contains("AGENT_RUNTIME_NOT_FOUND")) return "AGENT_OFFLINE";
            if (evidence.contains("NO_CAPACITY") || evidence.contains("CAPACITY")) return "AGENT_NO_CAPACITY";
            if (taskDecision != null && taskDecision.assignmentCreated()) return "AGENT_SELECTED";
            return "NO_ELIGIBLE_AGENT";
        }
        if (evidence.contains("NO_ACTIVE_FLOW_RULE") || evidence.contains("FLOW_RULE_REQUIRED_BLOCKED")) return "NO_ACTIVE_FLOW_RULE";
        if (evidence.contains("REQUIRED_CAPABILITY_MISSING") || evidence.contains("CAPABILITY_MISSING")) return "REQUIRED_CAPABILITY_MISSING";
        if (evidence.contains("AGENT_OFFLINE") || evidence.contains("RUNTIME_NOT_CONNECTED")
                || evidence.contains("NO_ACTIVE_RUNTIME_SESSION") || evidence.contains("AGENT_RUNTIME_NOT_FOUND")) return "AGENT_OFFLINE";
        if (evidence.contains("NO_CAPACITY") || evidence.contains("CAPACITY")) return "AGENT_NO_CAPACITY";
        if (taskDecision != null && taskDecision.assignmentCreated()) return "AGENT_SELECTED";
        return taskDecision != null && taskDecision.taskSuppressed() ? "TASK_SUPPRESSED" : "NO_ELIGIBLE_AGENT";
    }


    private static boolean hasFlowMatchEvidence(String evidence) {
        return evidence != null && evidence.contains("FLOW RULE MATCHED");
    }

    static String nextAction(TaskDecisionResult taskDecision) {
        return switch (primaryReasonCode(taskDecision)) {
            case "NO_ACTIVE_FLOW_RULE" -> "CREATE_OR_ACTIVATE_DISPATCH_FLOW";
            case "REQUIRED_CAPABILITY_MISSING" -> "APPROVE_REQUIRED_CAPABILITY";
            case "AGENT_OFFLINE" -> "RESTORE_AGENT_RUNTIME";
            case "AGENT_NO_CAPACITY" -> "WAIT_OR_ADD_AGENT_CAPACITY";
            case "AGENT_SELECTED" -> "MONITOR_TASK_DELIVERY";
            case "TASK_SUPPRESSED" -> "REVIEW_TASK_SUPPRESSION_POLICY";
            default -> "REVIEW_TASK_DIAGNOSTICS";
        };
    }

}
