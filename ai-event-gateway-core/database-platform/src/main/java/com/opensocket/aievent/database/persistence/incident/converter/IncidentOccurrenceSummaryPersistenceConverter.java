package com.opensocket.aievent.database.persistence.incident.converter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummary;
import com.opensocket.aievent.database.persistence.incident.po.IncidentOccurrenceSummaryPo;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "incident.summary", name = "store", havingValue = "MYBATIS")
public class IncidentOccurrenceSummaryPersistenceConverter {
    private final ObjectMapper objectMapper;

    public IncidentOccurrenceSummaryPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IncidentOccurrenceSummaryPo toOccurrencePo(
            Incident incident,
            NormalizedEvent event,
            String fingerprint,
            Duration window) {
        Duration effectiveWindow = window == null || window.isZero() || window.isNegative()
                ? Duration.ofMinutes(5)
                : window;
        OffsetDateTime start = truncate(event.occurredAt(), effectiveWindow);
        IncidentOccurrenceSummaryPo po = new IncidentOccurrenceSummaryPo();
        po.setSummaryId("sum-" + UUID.randomUUID());
        po.setIncidentId(incident.getIncidentId());
        po.setFingerprint(fingerprint);
        po.setTenantId(event.tenantId());
        po.setSourceSystem(event.sourceSystem());
        po.setSiteId(event.siteId());
        po.setPlantId(event.plantId());
        po.setObjectType(event.objectType());
        po.setObjectId(event.objectId());
        po.setEventType(event.eventType());
        po.setErrorCode(event.errorCode());
        po.setWindowStart(start);
        po.setWindowEnd(start.plus(effectiveWindow));
        po.setOccurrenceCount(1);
        po.setMaxSeverity(event.severity().name());
        po.setLatestEventId(event.eventId());
        po.setLatestMessage(event.normalizedMessage());
        po.setLatestPayloadJson(write(event.attributes() == null ? Map.of() : event.attributes()));
        return po;
    }

    public IncidentOccurrenceSummary toDomain(IncidentOccurrenceSummaryPo po) {
        IncidentOccurrenceSummary summary = new IncidentOccurrenceSummary();
        summary.setSummaryId(po.getSummaryId());
        summary.setIncidentId(po.getIncidentId());
        summary.setFingerprint(po.getFingerprint());
        summary.setTenantId(po.getTenantId());
        summary.setSourceSystem(po.getSourceSystem());
        summary.setSiteId(po.getSiteId());
        summary.setPlantId(po.getPlantId());
        summary.setObjectType(po.getObjectType());
        summary.setObjectId(po.getObjectId());
        summary.setEventType(po.getEventType());
        summary.setErrorCode(po.getErrorCode());
        summary.setWindowStart(po.getWindowStart());
        summary.setWindowEnd(po.getWindowEnd());
        summary.setOccurrenceCount(po.getOccurrenceCount());
        summary.setMaxSeverity(EventSeverity.parse(po.getMaxSeverity()));
        summary.setLatestEventId(po.getLatestEventId());
        summary.setLatestMessage(po.getLatestMessage());
        summary.setLatestPayloadJson(po.getLatestPayloadJson());
        return summary;
    }

    private OffsetDateTime truncate(OffsetDateTime value, Duration window) {
        long seconds = Math.max(1, window.getSeconds());
        long epoch = value.toEpochSecond();
        return OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochSecond((epoch / seconds) * seconds),
                value.getOffset());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize incident occurrence payload", exception);
        }
    }
}
