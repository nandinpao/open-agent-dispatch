package com.opensocket.aievent.gateway.netty.observability;

import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.protocol.TraceEnvelope;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentProtocolTraceServiceTest {

    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    private final OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(
                    io.opentelemetry.context.propagation.TextMapPropagator.composite(
                            W3CTraceContextPropagator.getInstance(),
                            W3CBaggagePropagator.getInstance())))
            .build();
    private final AgentProtocolTraceService service =
            new AgentProtocolTraceService(openTelemetry, ObservationRegistry.NOOP);

    @AfterEach
    void closeProvider() {
        tracerProvider.close();
    }

    @Test
    void shouldInjectW3cTraceAndBaggageForOutboundMessage() {
        Span span = openTelemetry.getTracer("test").spanBuilder("dispatch")
                .setSpanKind(SpanKind.PRODUCER)
                .startSpan();
        Context context = Baggage.current().toBuilder()
                .put("tenant.id", "tenant-a")
                .build()
                .storeInContext(Context.current().with(span));

        TraceEnvelope trace;
        try (Scope ignored = context.makeCurrent()) {
            trace = service.send(message("task-001"), value -> value);
        } finally {
            span.end();
        }

        assertThat(trace.validTraceparent()).isTrue();
        assertThat(trace.traceparent()).contains(span.getSpanContext().getTraceId());
        assertThat(trace.baggage()).contains("tenant.id=tenant-a");
    }

    @Test
    void shouldIsolateInterleavedAgentContextsOnOneEventLoopThread() throws Exception {
        var eventLoop = Executors.newSingleThreadExecutor();
        try {
            String traceA = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-1111111111111111-01";
            String traceB = "00-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb-2222222222222222-01";

            String observedA = eventLoop.submit(() -> service.receive(
                    new TraceEnvelope(traceA, null, "agent.id=agent-a"), message("task-a"),
                    () -> Span.current().getSpanContext().getTraceId())).get(5, TimeUnit.SECONDS);
            boolean clearAfterA = eventLoop.submit(() -> !Span.current().getSpanContext().isValid()).get(5, TimeUnit.SECONDS);
            String observedB = eventLoop.submit(() -> service.receive(
                    new TraceEnvelope(traceB, null, "agent.id=agent-b"), message("task-b"),
                    () -> Span.current().getSpanContext().getTraceId())).get(5, TimeUnit.SECONDS);
            boolean clearAfterB = eventLoop.submit(() -> !Span.current().getSpanContext().isValid()).get(5, TimeUnit.SECONDS);

            assertThat(observedA).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            assertThat(observedB).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
            assertThat(clearAfterA).isTrue();
            assertThat(clearAfterB).isTrue();
        } finally {
            eventLoop.shutdownNow();
        }
    }

    @Test
    void shouldIgnoreMalformedTraceparentWithoutLeakingCurrentContext() {
        String traceId = service.receive(
                new TraceEnvelope("not-a-traceparent", "vendor=value", "tenant.id=tenant-a"),
                message("task-invalid"),
                () -> Span.current().getSpanContext().getTraceId());

        assertThat(traceId).isEqualTo("00000000000000000000000000000000");
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @Test
    void shouldKeepCallbackOnSameTraceWhenSendingInsideReceiveScope() {
        var inbound = new TraceEnvelope(
                "00-cccccccccccccccccccccccccccccccc-3333333333333333-01",
                "vendor=value",
                "tenant.id=tenant-c");

        TraceEnvelope callback = service.receive(inbound, message("task-callback"),
                () -> service.send(message("task-callback"), value -> value));

        assertThat(callback.validTraceparent()).isTrue();
        assertThat(callback.traceparent()).contains("cccccccccccccccccccccccccccccccc");
        assertThat(callback.baggage()).contains("tenant.id=tenant-c");
    }

    private static AgentProtocolTraceService.MessageContext message(String taskId) {
        return AgentProtocolTraceService.MessageContext.of(
                "websocket", MessageType.TASK_RESULT, "task.completed", "msg-001",
                "agent-a", "session-001", taskId);
    }
}
