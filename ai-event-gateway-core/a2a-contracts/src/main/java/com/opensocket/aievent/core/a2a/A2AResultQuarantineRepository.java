package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface A2AResultQuarantineRepository {
    A2AResultQuarantine save(A2AResultQuarantine quarantine);
    Optional<A2AResultQuarantine> findById(String tenantId, String quarantineId);
    Optional<A2AResultQuarantine> findByAttempt(String tenantId, String attemptId);
    List<A2AResultQuarantine> findOpen(String tenantId, int limit);
    List<A2AResultQuarantine> findByRequest(String tenantId, String requestId, int limit);
    boolean resolveExpectedStatus(String tenantId, String quarantineId, String expectedStatus,
                                  String targetStatus, String resolvedBy, String reason, OffsetDateTime resolvedAt);
    String mode();
}
