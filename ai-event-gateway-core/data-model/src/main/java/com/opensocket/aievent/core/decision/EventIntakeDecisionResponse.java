package com.opensocket.aievent.core.decision;

import java.time.OffsetDateTime;
import java.util.List;

import com.opensocket.aievent.core.intake.IntakeAuthorityView;

public record EventIntakeDecisionResponse(
        String eventId,
        String fingerprint,
        String incidentId,
        DecisionType decisionType,
        boolean duplicate,
        long occurrenceCount,
        String severity,
        List<DecisionAction> actions,
        boolean taskCreated,
        String taskId,
        String taskType,
        boolean taskSuppressed,
        String taskDecisionReason,
        boolean assignmentCreated,
        String assignmentId,
        String selectedAgentId,
        String selectedGatewayNodeId,
        String selectedSiteId,
        String routingDecisionId,
        String assignmentStatus,
        String assignmentReason,
        boolean dispatchRequestCreated,
        String dispatchRequestId,
        String dispatchStatus,
        String dispatchReviewMode,
        String dispatchEligibilityStatus,
        String dispatchGatewayPath,
        String dispatchReason,
        boolean dispatchSuppressed,
        boolean mcpCalled,
        boolean issueCreated,
        String reason,
        OffsetDateTime decidedAt,
        String eventStage,
        String originSourceSystem,
        String targetSystem,
        String requestedSkill,
        String handoffMode,
        String correlationId,
        String parentTaskId,
        String primaryStatus,
        String primaryReasonCode,
        String nextAction,
        IntakeAuthorityView intakeAuthority
) {
    public EventIntakeDecisionResponse {
        if (intakeAuthority == null) intakeAuthority = IntakeAuthorityView.notEvaluated();
    }

    public EventIntakeDecisionResponse withIntakeAuthority(IntakeAuthorityView value) {
        return new EventIntakeDecisionResponse(eventId, fingerprint, incidentId, decisionType, duplicate, occurrenceCount, severity, actions,
                taskCreated, taskId, taskType, taskSuppressed, taskDecisionReason, assignmentCreated, assignmentId, selectedAgentId,
                selectedGatewayNodeId, selectedSiteId, routingDecisionId, assignmentStatus, assignmentReason, dispatchRequestCreated,
                dispatchRequestId, dispatchStatus, dispatchReviewMode, dispatchEligibilityStatus, dispatchGatewayPath, dispatchReason,
                dispatchSuppressed, mcpCalled, issueCreated, reason, decidedAt, eventStage, originSourceSystem, targetSystem, requestedSkill,
                handoffMode, correlationId, parentTaskId, primaryStatus, primaryReasonCode, nextAction,
                value == null ? IntakeAuthorityView.notEvaluated() : value);
    }
}
