package com.opensocket.aievent.core.integration.issue;
public enum ProjectionOutboxReliabilityStatus {
 PENDING, CLAIMED, SENDING, SENT, VERIFYING, ACKNOWLEDGED, FAILED_RETRYABLE, DEAD_LETTER, CANCELLED
}
