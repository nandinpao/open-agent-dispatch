package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

public record RedispatchDecision(
        RedispatchAction action,
        RedispatchFailureType failureType,
        boolean retryable,
        boolean applyAgentCooldown,
        int nextAttemptNo,
        OffsetDateTime nextRetryAt,
        String reason) {
}
