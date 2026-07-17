package com.opensocket.aievent.core.normalize;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Service
public class EventNormalizer {
    public static final String UNKNOWN = "UNKNOWN";
    public static final String CLASSIFICATION_UNCLASSIFIED = "UNCLASSIFIED";
    public static final String CLASSIFICATION_CLASSIFIED = "CLASSIFIED";

    private static final Logger log = LoggerFactory.getLogger(EventNormalizer.class);

    public NormalizedEvent normalize(EventIntakeRequest request) {
        NormalizedEvent event = new NormalizedEvent(
                UUID.randomUUID().toString(),
                cleanUpper(request.getTenantId()),
                cleanUpper(request.getSourceSystem()),
                normalizeEventStage(request.getEventStage()),
                cleanUpper(defaultString(request.getOriginSourceSystem(), request.getSourceSystem())),
                cleanUpperNullable(request.getTargetSystem()),
                cleanUpper(defaultString(request.getSiteId(), "UNKNOWN_SITE")),
                cleanUpper(defaultString(request.getPlantId(), "UNKNOWN_PLANT")),
                cleanUpper(defaultString(request.getObjectType(), UNKNOWN)),
                cleanUpper(defaultString(request.getObjectId(), "UNKNOWN_OBJECT_ID")),
                cleanUpper(defaultString(request.getEventType(), UNKNOWN)),
                cleanUpper(defaultString(request.getErrorCode(), UNKNOWN)),
                cleanUpperNullable(request.getRequestedSkill()),
                cleanUpperNullable(request.getHandoffMode()),
                cleanIdentifierNullable(request.getCorrelationId()),
                cleanIdentifierNullable(request.getParentTaskId()),
                EventSeverity.parse(request.getSeverity()),
                normalizeMessage(request.getMessage()),
                request.getOccurredAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.getOccurredAt(),
                request.getAttributes() == null ? Map.of() : Map.copyOf(request.getAttributes())
        );
        String classificationStatus = UNKNOWN.equals(event.eventType()) ? CLASSIFICATION_UNCLASSIFIED : CLASSIFICATION_CLASSIFIED;
        log.debug("event_normalized eventId={} tenantId={} sourceSystem={} eventStage={} originSourceSystem={} targetSystem={} objectType={} eventType={} errorCode={} classificationStatus={} requestedSkill={} handoffMode={} correlationId={} parentTaskId={} attributeKeys={}",
                event.eventId(), event.tenantId(), event.sourceSystem(), event.eventStage(), event.originSourceSystem(), event.targetSystem(),
                event.objectType(), event.eventType(), event.errorCode(), classificationStatus, event.requestedSkill(), event.handoffMode(), event.correlationId(), event.parentTaskId(), event.attributes().keySet());
        return event;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String cleanUpper(String value) {
        return defaultString(value, "UNKNOWN").trim().replaceAll("\\s+", "_").toUpperCase();
    }


    private String cleanUpperNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().replaceAll("\\s+", "_").toUpperCase();
    }

    private String cleanIdentifierNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().replaceAll("\\s+", "_");
    }

    private String normalizeEventStage(String value) {
        String stage = cleanUpper(defaultString(value, "EXTERNAL"));
        return switch (stage) {
            case "A2A", "RESULT", "ISSUE", "CALLBACK" -> stage;
            default -> "EXTERNAL";
        };
    }

    private String cleanIdentifier(String value) {
        return defaultString(value, "UNKNOWN").trim().replaceAll("\\s+", "_");
    }

    private String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
