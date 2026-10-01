package com.opensocket.aievent.core.integration;

import com.opensocket.aievent.service.events.IntegrationEventEnvelope;
import org.springframework.stereotype.Component;

@Component
public class NoopIntegrationEventSink implements IntegrationEventSink {
    @Override public void deliver(IntegrationEventEnvelope envelope){throw new IllegalStateException("No integration event sink configured");}
    @Override public String name(){return "NONE";}
}
