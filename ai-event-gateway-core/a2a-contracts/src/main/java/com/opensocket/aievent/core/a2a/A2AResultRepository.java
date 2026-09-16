package com.opensocket.aievent.core.a2a;

import java.util.List;
import java.util.Optional;

public interface A2AResultRepository {
    A2AResult save(A2AResult result);
    Optional<A2AResult> findById(String tenantId, String resultId);
    Optional<A2AResult> findByRequest(String tenantId, String requestId);
    Optional<A2AResult> findByIdempotencyKey(String tenantId, String idempotencyKey);
    List<A2AResult> findByParentTask(String tenantId, String parentTaskId, int limit);
    String mode();
}
