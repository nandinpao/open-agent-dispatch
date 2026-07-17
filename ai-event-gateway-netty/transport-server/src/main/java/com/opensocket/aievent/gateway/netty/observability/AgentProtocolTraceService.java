package com.opensocket.aievent.gateway.netty.observability;

import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.protocol.TraceEnvelope;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opens bounded Producer/Consumer observations around Agent protocol messages and bridges their
 * W3C trace envelope to the OpenTelemetry propagator. Context is never stored on a Channel or on an
 * EventLoop ThreadLocal beyond the synchronous scope of one message.
 */
@Component
public class AgentProtocolTraceService {

    private static final TextMapGetter<TraceEnvelope> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(TraceEnvelope carrier) {
            return java.util.List.of("traceparent", "tracestate", "baggage");
        }

        @Override
        public String get(TraceEnvelope carrier, String key) {
            if (carrier == null || key == null) {
                return null;
            }
            return switch (key.toLowerCase(java.util.Locale.ROOT)) {
                case "traceparent" -> carrier.traceparent();
                case "tracestate" -> carrier.tracestate();
                case "baggage" -> carrier.baggage();
                default -> null;
            };
        }
    };

    private static final TextMapSetter<Map<String, String>> SETTER = (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) {
            carrier.put(key.toLowerCase(java.util.Locale.ROOT), value);
        }
    };

    private final OpenTelemetry openTelemetry;
    private final ObservationRegistry observationRegistry;

    public AgentProtocolTraceService(OpenTelemetry openTelemetry, ObservationRegistry observationRegistry) {
        this.openTelemetry = openTelemetry == null ? OpenTelemetry.noop() : openTelemetry;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
    }

    public static AgentProtocolTraceService noop() {
        return new AgentProtocolTraceService(OpenTelemetry.noop(), ObservationRegistry.NOOP);
    }

    public <T> T receive(TraceEnvelope trace, MessageContext message, Supplier<T> action) {
        TraceEnvelope safeTrace = trace == null ? TraceEnvelope.empty() : trace;
        Context remoteParent = safeTrace.validTraceparent()
                ? openTelemetry.getPropagators().getTextMapPropagator().extract(Context.root(), safeTrace, GETTER)
                : Context.root();

        try (Scope ignored = remoteParent.makeCurrent()) {
            Observation observation = observation("agent.protocol.receive", "receive", safeTrace.state(), message).start();
            try (Observation.Scope observationScope = observation.openScope()) {
                T result = action.get();
                observation.lowCardinalityKeyValue("opendispatch.protocol.result", "success");
                return result;
            } catch (RuntimeException | Error error) {
                observation.lowCardinalityKeyValue("opendispatch.protocol.result", "error");
                observation.error(error);
                throw error;
            } finally {
                observation.stop();
            }
        }
    }

    public <T> T send(MessageContext message, Function<TraceEnvelope, T> action) {
        Observation observation = observation("agent.protocol.send", "publish", "generated", message).start();
        try (Observation.Scope ignored = observation.openScope()) {
            Map<String, String> carrier = new LinkedHashMap<>();
            openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), carrier, SETTER);
            TraceEnvelope trace = new TraceEnvelope(
                    carrier.get("traceparent"), carrier.get("tracestate"), carrier.get("baggage"));
            T result = action.apply(trace);
            observation.lowCardinalityKeyValue("opendispatch.protocol.result", "success");
            return result;
        } catch (RuntimeException | Error error) {
            observation.lowCardinalityKeyValue("opendispatch.protocol.result", "error");
            observation.error(error);
            throw error;
        } finally {
            observation.stop();
        }
    }

    private Observation observation(String name, String operation, String traceState, MessageContext message) {
        MessageContext context = message == null ? MessageContext.empty() : message;
        Observation observation = Observation.createNotStarted(name, observationRegistry)
                .contextualName(operation + " " + context.messageTypeValue())
                .lowCardinalityKeyValue("messaging.system", "opensocket-agent-protocol")
                .lowCardinalityKeyValue("messaging.operation.name", operation)
                .lowCardinalityKeyValue("network.transport", context.transportValue())
                .lowCardinalityKeyValue("opendispatch.protocol.message_type", context.messageTypeValue())
                .lowCardinalityKeyValue("opendispatch.trace.envelope", traceState);
        addHigh(observation, "messaging.message.id", context.messageId());
        addHigh(observation, "opendispatch.agent.id", context.agentId());
        addHigh(observation, "opendispatch.connection.id", context.connectionId());
        addHigh(observation, "opendispatch.task.id", context.taskId());
        return observation;
    }

    private static void addHigh(Observation observation, String key, String value) {
        if (value != null && !value.isBlank()) {
            observation.highCardinalityKeyValue(key, value);
        }
    }

    public record MessageContext(
            String transport,
            MessageType messageType,
            String messageName,
            String messageId,
            String agentId,
            String connectionId,
            String taskId
    ) {
        public static MessageContext of(
                String transport,
                MessageType messageType,
                String messageName,
                String messageId,
                String agentId,
                String connectionId,
                String taskId
        ) {
            return new MessageContext(transport, messageType, messageName, messageId, agentId, connectionId, taskId);
        }

        static MessageContext empty() {
            return new MessageContext("unknown", null, null, null, null, null, null);
        }

        String transportValue() {
            return transport == null || transport.isBlank() ? "unknown" : transport.toLowerCase(java.util.Locale.ROOT);
        }

        String messageTypeValue() {
            if (messageType != null) {
                return messageType.name().toLowerCase(java.util.Locale.ROOT);
            }
            return messageName == null || messageName.isBlank() ? "unknown" : messageName.toLowerCase(java.util.Locale.ROOT);
        }
    }
}
