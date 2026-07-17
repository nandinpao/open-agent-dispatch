#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        fail(f"Missing P3-C file: {path}")
    return file.read_text(encoding="utf-8")


def require(path: str, markers: list[str]) -> str:
    text = read(path)
    for marker in markers:
        if marker not in text:
            fail(f"{path} is missing required P3-C marker: {marker}")
    return text


def main() -> int:
    trace = require(
        "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/protocol/TraceEnvelope.java",
        ["traceparent", "tracestate", "baggage", "validTraceparent()", "fromMap(Map<?, ?> envelope)", "state()"],
    )
    if "ThreadLocal" in trace or "AttributeKey" in trace:
        fail("TraceEnvelope must remain transport data and must not own thread/channel state")

    envelope = require(
        "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/protocol/AiEventEnvelope.java",
        ["TraceEnvelope trace", '@JsonProperty("trace") TraceEnvelope trace', "withTrace(TraceEnvelope trace)"],
    )
    if "static final ThreadLocal" in envelope:
        fail("AiEventEnvelope must not persist trace context in a ThreadLocal")

    service_path = "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/observability/AgentProtocolTraceService.java"
    service = require(service_path, [
        'observation("agent.protocol.receive"',
        'observation("agent.protocol.send"',
        ".extract(Context.root(), safeTrace, GETTER)",
        "remoteParent.makeCurrent()",
        ".inject(Context.current(), carrier, SETTER)",
        "observation.openScope()",
        "observation.stop()",
        "messaging.system",
        "opendispatch.trace.envelope",
    ])
    if "static final ThreadLocal" in service or "AttributeKey" in service:
        fail("AgentProtocolTraceService must not store trace context on EventLoop ThreadLocal or Channel attributes")
    if "extract(Context.current()" in service:
        fail("Inbound protocol extraction must start from Context.root() to isolate EventLoop messages")
    if re.search(r'lowCardinalityKeyValue\([^\n]*(?:agentId|taskId|connectionId|messageId)', service):
        fail("Agent protocol high-cardinality identifiers are exposed as metric tags")

    tcp = require(
        "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/tcp/TcpMessageProcessor.java",
        ["agentProtocolTraceService.receive(inbound.trace()", "agentProtocolTraceService.send(sendContext", "ack.withTrace(trace)"],
    )
    websocket = require(
        "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/websocket/WebSocketMessageProcessor.java",
        ["TraceEnvelope.fromMap(rawEnvelope)", "agentProtocolTraceService.receive", "agentProtocolTraceService.send", "addTraceToJson"],
    )
    delivery = require(
        "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/delivery/CommandDeliveryService.java",
        ["agentProtocolTraceService.send(messageContext", "legacyEnvelope.trace()", 'event.put("trace", legacyEnvelope.trace())', "metadata.put(\"traceparent\""],
    )
    for path, text in [("TcpMessageProcessor", tcp), ("WebSocketMessageProcessor", websocket), ("CommandDeliveryService", delivery)]:
        if re.search(r'AttributeKey<[^>]*(?:Context|Span|Trace)', text, flags=re.IGNORECASE):
            fail(f"{path} stores trace state on a Netty Channel")

    openclaw_envelope = require(
        "openclaw/src/protocol/Envelope.ts",
        ["export interface TraceEnvelope", "  trace?: TraceEnvelope;", "traceparent?: string", "tracestate?: string", "baggage?: string", "validTraceEnvelope", "mergeTraceIntoMetadata"],
    )
    require(
        "openclaw/src/netty/OpenSocketClient.ts",
        ["mergeTraceIntoMetadata", "envelope.trace"],
    )
    require(
        "openclaw/src/protocol/TaskResult.ts",
        ["traceEnvelopeProperty(options.task)", "traceEnvelopeProperty(task)", "payload.traceparent", "payload.tracestate", "payload.baggage"],
    )
    require(
        "openclaw/src/reliability/ResultOutbox.ts",
        ["traceparent?: string", "tracestate?: string", "baggage?: string"],
    )
    if "AsyncLocalStorage" in openclaw_envelope or "globalThis" in openclaw_envelope:
        fail("Protocol envelope helpers must not create hidden global trace state")

    require(
        "ai-event-gateway-netty/gateway-model/src/test/java/com/opensocket/aievent/gateway/netty/protocol/TraceEnvelopeJsonTest.java",
        ["shouldSerializeAndDeserializeTopLevelTraceEnvelope", "shouldRejectMalformedTraceparentAsRemoteParent", "shouldReadCompatibilityTraceFromPayloadMetadata"],
    )
    require(
        "ai-event-gateway-netty/transport-server/src/test/java/com/opensocket/aievent/gateway/netty/observability/AgentProtocolTraceServiceTest.java",
        [
            "shouldInjectW3cTraceAndBaggageForOutboundMessage",
            "shouldIsolateInterleavedAgentContextsOnOneEventLoopThread",
            "shouldIgnoreMalformedTraceparentWithoutLeakingCurrentContext",
            "shouldKeepCallbackOnSameTraceWhenSendingInsideReceiveScope",
            "Executors.newSingleThreadExecutor()",
        ],
    )
    require(
        "openclaw/test/p3c-trace-envelope.test.ts",
        ["normalizes and validates the Agent protocol trace envelope", "task accepted callback echoes", "terminal outbox callback preserves"],
    )

    transport_pom = require(
        "ai-event-gateway-netty/transport-server/pom.xml",
        ["micrometer-observation", "opentelemetry-api", "opentelemetry-sdk"],
    )
    if transport_pom.count("opentelemetry-sdk") != 1 or "<scope>test</scope>" not in transport_pom:
        fail("OpenTelemetry SDK must be a transport-server test-only dependency")

    # No production Netty source may retain OpenTelemetry Context, Span, or trace headers in a Channel attribute.
    findings: list[str] = []
    root = ROOT / "ai-event-gateway-netty"
    for source in root.rglob("src/main/java/**/*.java"):
        text = source.read_text(encoding="utf-8")
        if re.search(r'AttributeKey[^\n]*(?:trace|span|context)', text, flags=re.IGNORECASE):
            findings.append(str(source.relative_to(ROOT)))
    if findings:
        fail("Trace context is stored in Netty Channel attributes: " + ", ".join(findings))

    print("[PASS] P3-C Agent protocol trace envelope, EventLoop isolation, callback continuity, and regression tests")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
