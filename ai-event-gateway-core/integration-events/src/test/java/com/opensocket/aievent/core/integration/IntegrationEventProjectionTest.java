package com.opensocket.aievent.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.opensocket.aievent.core.events.IncidentEscalatedEvent;
import org.junit.jupiter.api.Test;

class IntegrationEventProjectionTest {

    @Test
    void projectionIsIdempotentAndDeliveryUsesIndependentOutbox() {
        InMemoryIntegrationEventRepository repository = new InMemoryIntegrationEventRepository();
        IntegrationEventProperties properties = new IntegrationEventProperties();
        properties.setProjectionEnabled(true);
        properties.setDeliveryEnabled(true);
        ObjectMapper mapper = JsonMapper.builder().build();
        IntegrationEventProjector projector = new IntegrationEventProjector(repository, properties, mapper);
        IncidentEscalatedEvent event = new IncidentEscalatedEvent(
                "event-1", "incident-1", "fp", "CRITICAL", 4,
                "source-1", "tenant-1", "site-1", OffsetDateTime.now());

        projector.project(event);
        projector.project(event);

        assertThat(repository.statusCounts(100)).containsEntry("PENDING", 1);

        AtomicInteger deliveries = new AtomicInteger();
        IntegrationEventSink sink = new IntegrationEventSink() {
            @Override public void deliver(com.opensocket.aievent.service.events.IntegrationEventEnvelope envelope) {
                deliveries.incrementAndGet();
            }
            @Override public String name() { return "TEST"; }
        };
        IntegrationEventDeliveryResult result = new IntegrationEventDeliveryService(
                repository, properties, sink, mapper).deliverPending();

        assertThat(result.delivered()).isEqualTo(1);
        assertThat(deliveries.get()).isEqualTo(1);
        assertThat(repository.statusCounts(100)).containsEntry("DELIVERED", 1);
    }
}
