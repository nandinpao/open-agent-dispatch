package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

/**
 * TODO 15-D: centralizes immediate redispatch / retry wait / fail / escalate / DLQ decisions.
 */
@Service
public class RedispatchDecisionEngine {
    private final DispatchProperties properties;
    private final TaskRetryBackoffPolicy backoffPolicy;

    public RedispatchDecisionEngine(DispatchProperties properties, TaskRetryBackoffPolicy backoffPolicy) {
        this.properties = properties == null ? new DispatchProperties() : properties;
        this.backoffPolicy = backoffPolicy == null ? new TaskRetryBackoffPolicy(this.properties) : backoffPolicy;
    }

    public RedispatchDecision decide(RedispatchFailureType failureType, int currentAttemptNo, OffsetDateTime now) {
        OffsetDateTime at = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        RedispatchFailureType type = failureType == null ? RedispatchFailureType.UNKNOWN : failureType;
        int nextAttempt = Math.max(1, currentAttemptNo + 1);
        int maxAttempts = properties.getRetry().getMaxAttempts();
        if (type == RedispatchFailureType.MAX_RETRY_EXCEEDED || nextAttempt > maxAttempts) {
            return new RedispatchDecision(RedispatchAction.DEAD_LETTER, type, false, false, nextAttempt, null,
                    "Max retry attempts exceeded; move task to dead letter");
        }
        return switch (type) {
            case CAPACITY_EXCEEDED, TEMPORARY_NETWORK_ERROR -> new RedispatchDecision(
                    RedispatchAction.RETRY_WAIT, type, true, type != RedispatchFailureType.CAPACITY_EXCEEDED,
                    nextAttempt, backoffPolicy.nextRetryAt(nextAttempt, at), "Temporary dispatch failure; retry after task backoff");
            case AGENT_DISCONNECTED, RUNTIME_UNAUTHORIZED, AGENT_EXECUTION_FAILED_RETRYABLE -> new RedispatchDecision(
                    RedispatchAction.RETRY_WAIT, type, true, true, nextAttempt,
                    backoffPolicy.nextRetryAt(nextAttempt, at), "Agent/runtime failure; apply agent cooldown and retry task later");
            case BUSINESS_VALIDATION_FAILED, AGENT_EXECUTION_FAILED_NON_RETRYABLE -> new RedispatchDecision(
                    RedispatchAction.FAILED, type, false, false, nextAttempt, null, "Non-retryable business failure");
            case GOVERNANCE_REVOKED_DURING_EXECUTION, STALE_CALLBACK_REJECTED -> new RedispatchDecision(
                    RedispatchAction.ESCALATED, type, false, true, nextAttempt, null, "Governance or fencing conflict requires operator review");
            case UNKNOWN -> new RedispatchDecision(RedispatchAction.RETRY_WAIT, type, true, false, nextAttempt,
                    backoffPolicy.nextRetryAt(nextAttempt, at), "Unknown failure; use conservative retry wait");
            case MAX_RETRY_EXCEEDED -> throw new IllegalStateException("handled above");
        };
    }
}
