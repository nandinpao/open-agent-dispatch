package com.opensocket.aievent.core.a2a;

import java.util.List;

public interface A2AAggregationEvidenceRepository {
    A2AAggregationEvidence save(A2AAggregationEvidence evidence);
    List<A2AAggregationEvidence> findByParentTask(String tenantId, String parentTaskId, int limit);
    String mode();
}
