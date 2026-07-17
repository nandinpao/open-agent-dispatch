package com.opensocket.aievent.core.integration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.events.ModuleEvent;
import com.opensocket.aievent.service.events.IntegrationEventEnvelope;
import org.springframework.stereotype.Service;

@Service
public class IntegrationEventProjector {
    private final IntegrationEventRepository repository;
    private final IntegrationEventProperties properties;
    private final ObjectMapper mapper;

    public IntegrationEventProjector(IntegrationEventRepository repository, IntegrationEventProperties properties, ObjectMapper mapper) {
        this.repository = repository;
        this.properties = properties;
        this.mapper = mapper;
    }

    public void project(ModuleEvent event) {
        if (!properties.isProjectionEnabled() || !properties.getExportedEventTypes().contains(event.eventType())) return;
        try {
            IntegrationEventEnvelope envelope = new IntegrationEventEnvelope(
                    "1.0", event.eventId(), event.eventType(), properties.getSource(),
                    event.aggregateType(), event.aggregateId(), event.occurredAt(),
                    mapper.convertValue(event, Map.class),
                    Map.of("delivery", "at-least-once", "schema", event.eventType()));
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            IntegrationEventRecord record = new IntegrationEventRecord();
            record.setIntegrationEventId("integration-" + UUID.randomUUID());
            record.setEventId(event.eventId());
            record.setEventType(event.eventType());
            record.setAggregateType(event.aggregateType());
            record.setAggregateId(event.aggregateId());
            record.setEnvelopeJson(mapper.writeValueAsString(envelope));
            record.setStatus(IntegrationEventStatus.PENDING);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            repository.save(record);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to project integration event " + event.eventType(), ex);
        }
    }
}
