package com.opensocket.aievent.core.a2a.application.port.in;

import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.A2ARequestCommand;
import com.opensocket.aievent.core.a2a.A2AResult;
import com.opensocket.aievent.core.a2a.A2AResultSubmission;

/** The only permitted bridge for historical A2A mutation entrypoints during Phase 2I. */
public interface A2ACompatibilityFacade {
    A2ARequest request(A2ARequestCommand command);
    A2ARequest approve(String tenantId, String requestId, String actorType, String actorId, String idempotencyKey);
    A2ARequest reject(String tenantId, String requestId, String reasonCode, String reason,
                      String actorType, String actorId, String idempotencyKey);
    A2ARequest cancel(String tenantId, String requestId, String actorType, String actorId,
                      String reason, String idempotencyKey);
    A2AResult acceptResult(A2AResultSubmission submission);
}
