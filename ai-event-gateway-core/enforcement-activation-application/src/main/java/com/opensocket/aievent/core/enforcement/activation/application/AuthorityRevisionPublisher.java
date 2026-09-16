package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Instant;

public interface AuthorityRevisionPublisher {
    PublishedAuthorityRevision publish(CutoverPlan plan, String actorId, String auditReason, String correlationId,
                                       String idempotencyKey, String requestHash, Instant now);
}
