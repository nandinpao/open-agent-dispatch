package com.opensocket.aievent.gateway.netty.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * W3C trace context carried by the Agent protocol. The envelope is transport neutral and is
 * deliberately separate from business payload fields so TCP and WebSocket messages use the same
 * propagation contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TraceEnvelope(
        String traceparent,
        String tracestate,
        String baggage
) {
    private static final Pattern TRACEPARENT = Pattern.compile(
            "^[0-9a-fA-F]{2}-[0-9a-fA-F]{32}-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}$");

    @JsonCreator
    public TraceEnvelope(
            @JsonProperty("traceparent") String traceparent,
            @JsonProperty("tracestate") String tracestate,
            @JsonProperty("baggage") String baggage
    ) {
        this.traceparent = normalize(traceparent, 512);
        this.tracestate = normalize(tracestate, 512);
        this.baggage = normalize(baggage, 8192);
    }

    public static TraceEnvelope empty() {
        return new TraceEnvelope(null, null, null);
    }

    public boolean present() {
        return traceparent != null || tracestate != null || baggage != null;
    }

    public boolean validTraceparent() {
        if (traceparent == null || !TRACEPARENT.matcher(traceparent).matches()) {
            return false;
        }
        String[] parts = traceparent.split("-");
        return parts.length == 4 && !allZero(parts[1]) && !allZero(parts[2]);
    }

    public String state() {
        if (!present()) {
            return "absent";
        }
        return validTraceparent() ? "valid" : "malformed";
    }

    public static TraceEnvelope fromMap(Map<?, ?> envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return empty();
        }
        Object trace = envelope.get("trace");
        if (trace instanceof Map<?, ?> traceMap) {
            return fromCarrier(traceMap);
        }
        Object metadata = envelope.get("metadata");
        if (metadata instanceof Map<?, ?> metadataMap) {
            TraceEnvelope fromMetadata = fromCarrier(metadataMap);
            if (fromMetadata.present()) {
                return fromMetadata;
            }
        }
        Object payload = envelope.get("payload");
        if (payload instanceof Map<?, ?> payloadMap) {
            TraceEnvelope fromPayload = fromCarrier(payloadMap);
            if (fromPayload.present()) {
                return fromPayload;
            }
            Object payloadMetadata = payloadMap.get("metadata");
            if (payloadMetadata instanceof Map<?, ?> payloadMetadataMap) {
                return fromCarrier(payloadMetadataMap);
            }
        }
        return fromCarrier(envelope);
    }

    private static TraceEnvelope fromCarrier(Map<?, ?> carrier) {
        return new TraceEnvelope(
                stringValue(carrier.get("traceparent")),
                stringValue(carrier.get("tracestate")),
                stringValue(carrier.get("baggage"))
        );
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static boolean allZero(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(ch -> ch == '0');
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
