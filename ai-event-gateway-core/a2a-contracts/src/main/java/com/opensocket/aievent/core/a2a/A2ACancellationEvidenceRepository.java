package com.opensocket.aievent.core.a2a;

import java.util.List;

public interface A2ACancellationEvidenceRepository {
    A2ACancellationEvidence append(A2ACancellationEvidence value);
    List<A2ACancellationEvidence> findByCancellation(String tenantId, String cancellationId, int limit);
    java.util.Optional<A2ACancellationEvidence> findByEventKey(String tenantId, String eventKey);
    String mode();
}
