package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;

public record A2ACancellationAckCommand(
        String tenantId, String cancellationId, String decision, String callbackId,
        String assignmentId, String executionAttemptId, Integer attemptNo,
        String agentSessionId, String activeFencingTokenHash, String reason,
        OffsetDateTime occurredAt) {
    public A2ACancellationAckCommand(String tenantId,String cancellationId,String decision,
            String callbackId,String agentSessionId,String activeFencingTokenHash,String reason,
            OffsetDateTime occurredAt){
        this(tenantId,cancellationId,decision,callbackId,null,null,null,agentSessionId,
                activeFencingTokenHash,reason,occurredAt);
    }
}
