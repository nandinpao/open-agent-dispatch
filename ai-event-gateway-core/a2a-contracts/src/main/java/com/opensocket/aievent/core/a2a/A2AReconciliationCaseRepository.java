package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface A2AReconciliationCaseRepository {
    A2AReconciliationCase save(A2AReconciliationCase value);
    A2AReconciliationCase saveExpectedVersion(A2AReconciliationCase value, long expectedVersion);
    Optional<A2AReconciliationCase> findById(String tenantId, String caseId);
    Optional<A2AReconciliationCase> findOpenByCancellation(String tenantId, String cancellationId);
    Optional<A2AReconciliationCase> findOpenByRequest(String tenantId, String requestId);
    List<A2AReconciliationCase> findOpen(String tenantId, int limit);
    List<A2AReconciliationCase> claimDue(String workerId, OffsetDateTime now, OffsetDateTime claimUntil, int limit);
    List<A2AReconciliationCase> claimExecutable(String workerId, OffsetDateTime now, OffsetDateTime claimUntil, int limit);
    boolean heartbeat(String tenantId, String caseId, String workerId, long expectedVersion,
                      OffsetDateTime heartbeatAt, OffsetDateTime claimUntil);
    List<A2AReconciliationCase> search(A2AReconciliationSearchQuery query);
    long count(A2AReconciliationSearchQuery query);
    String mode();
}
