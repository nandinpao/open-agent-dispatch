package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface A2AResultProcessingRepository {
    A2AResultProcessing save(A2AResultProcessing processing);
    A2AResultProcessing saveExpectedVersion(A2AResultProcessing processing, long expectedVersion);
    Optional<A2AResultProcessing> findByResult(String tenantId, String resultId);
    List<A2AResultProcessing> findDue(OffsetDateTime dueAt, int limit);
    List<A2AResultProcessing> findByTask(String tenantId, String taskId, int limit);
    String mode();
}
