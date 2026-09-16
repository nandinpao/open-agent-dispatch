package com.opensocket.aievent.core.integration.issue;
public enum IntegrationOutboxStatus { PENDING, CLAIMED, IN_PROGRESS, COMPLETED, SKIPPED, FAILED_RETRYABLE, FAILED_PERMANENT, DEAD_LETTER, CANCELLED }
