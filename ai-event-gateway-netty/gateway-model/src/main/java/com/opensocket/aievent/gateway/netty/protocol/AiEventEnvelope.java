package com.opensocket.aievent.gateway.netty.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiEventEnvelope<T>(
        String messageId,
        MessageType messageType,
        String eventType,
        String source,
        String target,
        OffsetDateTime timestamp,
        T payload,
        TraceEnvelope trace
) {
    @JsonCreator
    public AiEventEnvelope(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("messageType") MessageType messageType,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("source") String source,
            @JsonProperty("target") String target,
            @JsonProperty("timestamp") OffsetDateTime timestamp,
            @JsonProperty("payload") T payload,
            @JsonProperty("trace") TraceEnvelope trace
    ) {
        this.messageId = blank(messageId) ? "msg-" + UUID.randomUUID() : messageId;
        this.messageType = messageType == null ? MessageType.fromDomainEventName(eventType) : messageType;
        this.eventType = blank(eventType) ? MessageType.toDomainEventName(this.messageType) : eventType;
        this.source = source;
        this.target = target;
        this.timestamp = timestamp == null ? OffsetDateTime.now() : timestamp;
        this.payload = payload;
        this.trace = trace == null || !trace.present() ? null : trace;
    }

    /** Compatibility constructor for protocol callers created before the trace envelope existed. */
    public AiEventEnvelope(
            String messageId,
            MessageType messageType,
            String eventType,
            String source,
            String target,
            OffsetDateTime timestamp,
            T payload
    ) {
        this(messageId, messageType, eventType, source, target, timestamp, payload, null);
    }

    public static <T> AiEventEnvelope<T> of(MessageType messageType, String source, String target, T payload) {
        return new AiEventEnvelope<>(
                "msg-" + UUID.randomUUID(),
                messageType,
                MessageType.toDomainEventName(messageType),
                source,
                target,
                OffsetDateTime.now(),
                payload,
                null
        );
    }

    public AiEventEnvelope<T> withTrace(TraceEnvelope trace) {
        return new AiEventEnvelope<>(messageId, messageType, eventType, source, target, timestamp, payload, trace);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
