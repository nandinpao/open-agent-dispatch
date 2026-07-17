package com.opensocket.aievent.gateway.netty.protocol;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceEnvelopeJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldSerializeAndDeserializeTopLevelTraceEnvelope() throws Exception {
        var envelope = new AiEventEnvelope<>(
                "msg-trace-001",
                MessageType.TASK_PROGRESS,
                "task.progress",
                "agent-a",
                "gateway-a",
                null,
                Map.of("taskId", "task-001"),
                new TraceEnvelope(
                        "00-11111111111111111111111111111111-2222222222222222-01",
                        "vendor=value",
                        "tenant.id=tenant-a")
        );

        String json = objectMapper.writeValueAsString(envelope);
        var decoded = objectMapper.readValue(json, new TypeReference<AiEventEnvelope<JsonNode>>() {});

        assertThat(json).contains("\"trace\"").contains("\"traceparent\"");
        assertThat(decoded.trace()).isNotNull();
        assertThat(decoded.trace().validTraceparent()).isTrue();
        assertThat(decoded.trace().tracestate()).isEqualTo("vendor=value");
        assertThat(decoded.trace().baggage()).isEqualTo("tenant.id=tenant-a");
    }

    @Test
    void shouldRejectMalformedTraceparentAsRemoteParent() {
        var malformed = new TraceEnvelope("00-00000000000000000000000000000000-2222222222222222-01", null, null);

        assertThat(malformed.present()).isTrue();
        assertThat(malformed.validTraceparent()).isFalse();
        assertThat(malformed.state()).isEqualTo("malformed");
    }

    @Test
    void shouldReadCompatibilityTraceFromPayloadMetadata() {
        var trace = TraceEnvelope.fromMap(Map.of(
                "payload", Map.of(
                        "taskId", "task-001",
                        "metadata", Map.of(
                                "traceparent", "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01",
                                "tracestate", "vendor=value",
                                "baggage", "tenant.id=tenant-a"))));

        assertThat(trace.validTraceparent()).isTrue();
        assertThat(trace.tracestate()).isEqualTo("vendor=value");
        assertThat(trace.baggage()).isEqualTo("tenant.id=tenant-a");
    }
}
