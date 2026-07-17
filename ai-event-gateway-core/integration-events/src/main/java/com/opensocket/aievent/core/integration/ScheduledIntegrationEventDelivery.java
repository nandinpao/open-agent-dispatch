package com.opensocket.aievent.core.integration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class ScheduledIntegrationEventDelivery {
    private final IntegrationEventDeliveryService service;
    public ScheduledIntegrationEventDelivery(IntegrationEventDeliveryService service){this.service=service;}
    @Scheduled(fixedDelayString="${core.integration-events.scan-interval-ms:1000}")
    public void deliver(){service.deliverPending();}
}
