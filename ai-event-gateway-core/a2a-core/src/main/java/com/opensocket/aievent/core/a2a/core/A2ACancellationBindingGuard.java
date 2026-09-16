package com.opensocket.aievent.core.a2a.core;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Pure cancellation binding and Cancel/Complete race guard. */
public final class A2ACancellationBindingGuard {
    public Decision validateAcknowledgement(Binding expected, Binding actual) {
        if (expected == null || actual == null) return Decision.reject("MISSING_CANCELLATION_BINDING");
        if (!Objects.equals(expected.assignmentId(), actual.assignmentId())) return Decision.reject("STALE_ASSIGNMENT");
        if (!Objects.equals(expected.executionAttemptId(), actual.executionAttemptId())) return Decision.reject("STALE_EXECUTION_ATTEMPT");
        if (!Objects.equals(expected.attemptNo(), actual.attemptNo())) return Decision.reject("STALE_ATTEMPT");
        if (!Objects.equals(expected.agentSessionId(), actual.agentSessionId())) return Decision.reject("STALE_AGENT_SESSION");
        if (!Objects.equals(expected.activeFencingTokenHash(), actual.activeFencingTokenHash())) return Decision.reject("FENCING_TOKEN_MISMATCH");
        return Decision.accept();
    }

    public boolean resultWinsCancelCompleteRace(OffsetDateTime resultAcceptedAt,
            OffsetDateTime resultCutoffAt, Binding cancellationBinding, Binding resultBinding) {
        if (resultAcceptedAt == null || resultCutoffAt == null || resultAcceptedAt.isAfter(resultCutoffAt)) return false;
        return validateAcknowledgement(cancellationBinding, resultBinding).accepted();
    }

    public record Binding(String assignmentId, String executionAttemptId, Integer attemptNo,
            String agentSessionId, String activeFencingTokenHash) {}

    public record Decision(boolean accepted, String reasonCode) {
        public static Decision accept() { return new Decision(true, null); }
        public static Decision reject(String reasonCode) { return new Decision(false, reasonCode); }
    }
}
