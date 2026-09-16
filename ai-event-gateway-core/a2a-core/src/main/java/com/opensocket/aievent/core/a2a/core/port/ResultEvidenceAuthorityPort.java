package com.opensocket.aievent.core.a2a.core.port;

import com.opensocket.aievent.core.a2a.A2AResultAcceptanceDecision;
import com.opensocket.aievent.core.a2a.A2AResultClassification;
import com.opensocket.aievent.core.a2a.A2AResultSubmission;

/** Read-only authority bridge used before A2A accepts a Canonical Result. */
public interface ResultEvidenceAuthorityPort {
    Verification verify(String childTaskId, A2AResultSubmission submission);

    record Verification(
            A2AResultAcceptanceDecision decision,
            A2AResultClassification classification,
            String reasonCode,
            String reason,
            String authoritativeAssignmentId,
            String authoritativeExecutionAttemptId,
            Integer authoritativeAttemptNo,
            String authoritativeDispatchRequestId,
            String authoritativeAgentId,
            String authoritativeAgentSessionId,
            String callbackFingerprint) {
        public boolean accepted() { return decision == A2AResultAcceptanceDecision.ACCEPTED; }
    }
}
