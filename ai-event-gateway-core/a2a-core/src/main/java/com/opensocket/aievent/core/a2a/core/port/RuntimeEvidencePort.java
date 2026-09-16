package com.opensocket.aievent.core.a2a.core.port;

import java.util.Optional;

/** Read-only runtime evidence port. It cannot select an Agent or mutate Task/Assignment state. */
public interface RuntimeEvidencePort {
    Optional<RuntimeEvidence> findEvidence(String tenantId, String assignmentId, long assignmentAttempt);
    record RuntimeEvidence(String agentId, String sessionId, boolean acknowledged, String deliveryEvidenceId) {}
}
