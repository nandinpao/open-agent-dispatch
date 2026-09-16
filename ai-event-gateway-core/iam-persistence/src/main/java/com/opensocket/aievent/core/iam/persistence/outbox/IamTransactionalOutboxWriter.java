package com.opensocket.aievent.core.iam.persistence.outbox;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.opensocket.aievent.core.iam.persistence.dao.IamOutboxDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class IamTransactionalOutboxWriter {
    public static final String PROPAGATED = "PROPAGATED";
    public static final String LEGACY_CORRELATION_FALLBACK = "LEGACY_CORRELATION_FALLBACK";

    private final IamOutboxDao dao;

    public IamTransactionalOutboxWriter(IamOutboxDao dao) {
        this.dao = dao;
    }

    /**
     * Compatibility entry point. IAM publishers should prefer the metadata-aware overload so
     * business correlation and Tenant authority are persisted in the canonical module outbox.
     */
    public void append(String eventId, String eventType, String aggregateType, String aggregateId,
                       String payloadJson, Instant occurredAt) {
        append(eventId, eventType, aggregateType, aggregateId, payloadJson, occurredAt,
                null, null, null);
    }

    public void append(String eventId, String eventType, String aggregateType, String aggregateId,
                       String payloadJson, Instant occurredAt, String tenantId,
                       String correlationId, String actorId) {
        String normalizedEventId = required(eventId, "eventId");
        String normalizedCorrelation = normalize(correlationId);
        String lineageStatus = PROPAGATED;
        if (normalizedCorrelation == null) {
            normalizedCorrelation = normalizedEventId;
            lineageStatus = LEGACY_CORRELATION_FALLBACK;
        }

        Map<String,Object> row = new HashMap<>();
        row.put("outboxId", "iam-outbox-" + UUID.randomUUID());
        row.put("eventId", normalizedEventId);
        row.put("eventType", required(eventType, "eventType"));
        row.put("aggregateType", required(aggregateType, "aggregateType"));
        row.put("aggregateId", required(aggregateId, "aggregateId"));
        row.put("payloadJson", payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
        row.put("payloadVersion", "1");
        row.put("tenantId", normalize(tenantId));
        row.put("correlationId", normalizedCorrelation);
        row.put("actorType", "SYSTEM");
        row.put("actorId", defaultValue(actorId, "opendispatch"));
        row.put("lineageStatus", lineageStatus);
        row.put("createdAt", occurredAt == null ? Instant.now() : occurredAt);
        dao.insert(row);
    }

    private static String required(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    private static String defaultValue(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
