package com.opensocket.aievent.core.integration;

import com.opensocket.aievent.service.events.IntegrationEventEnvelope;

public interface IntegrationEventSink {
    void deliver(IntegrationEventEnvelope envelope) throws Exception;
    String name();
}
