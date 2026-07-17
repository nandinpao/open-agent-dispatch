package com.opensocket.aievent.core.integration;

public enum IntegrationEventStatus {
    PENDING,
    RETRY_WAITING,
    DELIVERING,
    DELIVERED,
    DEAD_LETTER
}
