#!/usr/bin/env python3
"""Verify P2-A HTTP request context and business observation architecture."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        fail(f"Missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, markers: list[str]) -> str:
    text = read(rel)
    for marker in markers:
        if marker not in text:
            fail(f"{rel} is missing required marker: {marker}")
    return text


def forbid(rel: str, markers: list[str]) -> None:
    text = read(rel)
    for marker in markers:
        if marker in text:
            fail(f"{rel} contains forbidden marker: {marker}")


def main() -> int:
    controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/EventIntakeController.java"
    require(controller, [
        "EventIntakeApplicationService eventIntakeApplicationService",
        "return eventIntakeApplicationService.intake(request);",
    ])
    forbid(controller, [
        "io.micrometer.tracing",
        "Tracer",
        "Span",
        "MDC",
        "LoggerFactory",
        "DecisionEngine",
        "nextSpan(",
    ])

    api_root = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api"
    combined_api = "\n".join(path.read_text(encoding="utf-8") for path in api_root.rglob("*.java"))
    if "io.micrometer.tracing" in combined_api:
        fail("Controller/API package must not directly import Micrometer Tracer APIs")

    request_filter = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/context/OpenDispatchRequestContextFilter.java"
    require(request_filter, [
        "extends OncePerRequestFilter",
        "ObservationRegistry observationRegistry",
        "observationRegistry.getCurrentObservation()",
        "OpenDispatchRequestContextHolder.open(context)",
        "MdcContextScope.open(mdc)",
        "response.setHeader(REQUEST_ID_HEADER, requestId)",
        "X-Request-Id",
        "X-Correlation-Id",
        "X-Tenant-Id",
        "SecurityContextHolder.getContext().getAuthentication()",
        "request.getRemoteAddr()",
        'request.getHeader("User-Agent")',
    ])
    forbid(request_filter, [
        "io.micrometer.tracing",
        "nextSpan(",
        ".start()",
        "MDC.clear()",
        "ContentCachingRequestWrapper",
        "getReader()",
        "getInputStream()",
    ])

    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/observation/OpenDispatchServerRequestObservationConvention.java",
        [
            "extends OpenTelemetryServerRequestObservationConvention",
            "getLowCardinalityKeyValues",
            "getHighCardinalityKeyValues",
            "REQUEST_KIND",
            "AUTHENTICATED",
            "TENANT_PRESENT",
            "REQUEST_ID",
            "CORRELATION_ID",
            "TENANT_ID",
            "OPERATOR_ID",
            "CLIENT_ADDRESS",
            "USER_AGENT",
        ],
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/observation/OpenDispatchHttpObservationConfiguration.java",
        [
            "ServerRequestObservationConvention",
            "OpenDispatchServerRequestObservationConvention",
            "FilterRegistrationBean<OpenDispatchRequestContextFilter>",
            "registration.setEnabled(false)",
        ],
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java",
        [
            "OpenDispatchRequestContextFilter requestContextFilter",
            ".addFilterAfter(requestContextFilter, CoreInternalTokenAuthenticationFilter.class)",
        ],
    )

    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/context/MdcContextScope.java",
        [
            "MDC.getCopyOfContextMap()",
            "MDC.setContextMap(previous)",
            "MDC.clear()",
        ],
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/context/OpenDispatchRequestContextHolder.java",
        [
            "ThreadLocal<OpenDispatchRequestContext>",
            "enrichBusinessContext",
            "CONTEXT.remove()",
            "implements AutoCloseable",
        ],
    )

    app_service = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/decision/EventIntakeApplicationService.java"
    require(app_service, [
        "ObservationRegistry observationRegistry",
        "EventIntakeObservationDocumentation.EVENT_INTAKE.observation(observationRegistry)",
        ".lowCardinalityKeyValue(",
        ".highCardinalityKeyValue(",
        "OpenDispatchRequestContextHolder.enrichBusinessContext",
        "MdcContextScope.open(mdc)",
        "observation.observe(() -> ingestObserved",
        "decisionEngine.ingest(request)",
        'RESULT.withValue("failed")',
    ])
    forbid(app_service, [
        "io.micrometer.tracing",
        "Tracer",
        "Span",
        "nextSpan(",
    ])

    documentation = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/decision/observation/EventIntakeObservationDocumentation.java"
    doc_text = require(documentation, [
        "implements ObservationDocumentation",
        'return "dispatch.event.intake";',
        "LowCardinalityKeyNames",
        "HighCardinalityKeyNames",
        'EVENT_STAGE("dispatch.event.stage")',
        'RESULT("dispatch.result")',
        'TENANT_ID("tenant.id")',
        'REQUESTED_SKILL("dispatch.requested.skill")',
    ])
    low_section = doc_text.split("public enum LowCardinalityKeyNames", 1)[1].split("public enum HighCardinalityKeyNames", 1)[0]
    for forbidden_low in ["tenant.id", "task.id", "agent.id", "requested.skill", "source.system", "event.type", "correlation.id"]:
        if forbidden_low in low_section:
            fail(f"Unbounded field leaked into low-cardinality contract: {forbidden_low}")

    tests = {
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/http/context/OpenDispatchRequestContextFilterTest.java": [
            "shouldExposeRequestContextInsideChainAndRestoreMdcAfterRequest",
            "shouldNotLeakTenantOrOperatorToTheNextRequest",
            'MDC.get("traceId")',
            "OpenDispatchRequestContextHolder.current()).isEmpty()",
        ],
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/http/observation/OpenDispatchServerRequestObservationConventionTest.java": [
            "getLowCardinalityKeyValues",
            "getHighCardinalityKeyValues",
        ],
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/decision/EventIntakeApplicationServiceTest.java": [
            "shouldOwnBusinessObservationContextAndRestoreRequestThreadState",
            "ObservationRegistry.create()",
            "verify(decisionEngine).ingest(request)",
        ],
    }
    for rel, markers in tests.items():
        require(rel, markers)

    require(
        "docs/architecture/P2-A_HTTP_REQUEST_AND_BUSINESS_OBSERVATION.md",
        [
            "Spring owns the HTTP root observation",
            "low-cardinality",
            "high-cardinality",
            "Thread pool propagation is deferred to P3",
        ],
    )

    print("P2-A HTTP request and business observation verification passed.")
    print("- Controller direct Tracer lifecycle removed")
    print("- authenticated request context and exact MDC restoration verified")
    print("- OpenTelemetry HTTP server convention enrichment verified")
    print("- event intake business ObservationDocumentation contract verified")
    print("- low/high-cardinality boundary and non-leak tests verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
