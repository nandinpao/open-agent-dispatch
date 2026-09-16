package com.opensocket.aievent.core.a2a;

import java.util.List;

public interface A2AResultEvidenceRepository {
    A2AResultEvidence save(A2AResultEvidence evidence);
    List<A2AResultEvidence> findByAttempt(String tenantId, String attemptId, int limit);
    String mode();
}
