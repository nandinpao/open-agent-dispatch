package com.opensocket.aievent.core.a2a;

import java.util.List;
import java.util.Optional;

public interface A2ARequestRepository {
    A2ARequest save(A2ARequest request);
    A2ARequest saveExpectedVersion(A2ARequest request, long expectedVersion);
    Optional<A2ARequest> findById(String tenantId, String requestId);
    Optional<A2ARequest> findByIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<A2ARequest> findByChildTask(String tenantId, String childTaskId);
    List<A2ARequest> findByRootTask(String tenantId, String rootTaskId, int limit);
    List<A2ARequest> findBySourceTask(String tenantId, String sourceTaskId, int limit);
    List<A2ARequest> search(String tenantId, String requestStatus, String text, int limit);
    String mode();
}
