package com.opensocket.aievent.core.a2a;

import java.util.Optional;

public interface A2AParentAggregationRepository {
    A2AParentAggregation save(A2AParentAggregation aggregation);
    A2AParentAggregation saveExpectedVersion(A2AParentAggregation aggregation, long expectedVersion);
    Optional<A2AParentAggregation> findByParentTask(String tenantId, String parentTaskId);
    String mode();
}
