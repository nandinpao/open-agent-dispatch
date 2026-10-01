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
    private final IntegrationEventProperties startupProperties;
    private final IntegrationEventsRuntimeConfigurationView runtimeConfiguration;
    private final ObjectMapper mapper;

    public IntegrationEventProjector(IntegrationEventRepository repository,
                                     IntegrationEventProperties startupProperties,
                                     IntegrationEventsRuntimeConfigurationView runtimeConfiguration,
                                     ObjectMapper mapper) {
        this.repository = repository; this.startupProperties = startupProperties; this.runtimeConfiguration=runtimeConfiguration; this.mapper = mapper;
    }

    public void project(ModuleEvent event) {
        // projection-enabled remains TEST_RELEASE_ONLY. Event selection/source identity are runtime configuration.
        if (!startupProperties.isProjectionEnabled() || !runtimeConfiguration.exportedEventTypes().contains(event.eventType())) return;
        try {
            IntegrationEventEnvelope envelope = new IntegrationEventEnvelope(
                    "1.0", event.eventId(), event.eventType(), runtimeConfiguration.source(), event.tenantId(),
                    event.aggregateType(), event.aggregateId(), event.rootTaskId(), event.correlationId(), event.causationId(),
                    event.actorType(), event.actorId(), event.occurredAt(), event.payloadVersion(),
                    mapper.convertValue(event, Map.class),
                    Map.of("delivery", "at-least-once", "schema", event.eventType(), "payloadVersion", event.payloadVersion()))
                    .requireExportable();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            IntegrationEventRecord record = new IntegrationEventRecord();
            record.setIntegrationEventId("integration-" + UUID.randomUUID());
            record.setEventId(event.eventId()); record.setEventType(event.eventType());
            record.setAggregateType(event.aggregateType()); record.setAggregateId(event.aggregateId());
            record.setEnvelopeJson(mapper.writeValueAsString(envelope)); record.setStatus(IntegrationEventStatus.PENDING);
            record.setCreatedAt(now); record.setUpdatedAt(now); repository.save(record);
        } catch (Exception ex) { throw new IllegalStateException("Unable to project integration event " + event.eventType(), ex); }
    }
}
