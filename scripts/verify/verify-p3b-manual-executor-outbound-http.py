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
        fail(f"Missing P3-B file: {path}")
    return file.read_text(encoding="utf-8")


def require(path: str, markers: list[str]) -> str:
    text = read(path)
    for marker in markers:
        if marker not in text:
            fail(f"{path} is missing required P3-B marker: {marker}")
    return text


def main() -> int:
    dispatcher_path = "ai-event-gateway-netty/gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/outbound/CoreOutboundDispatcher.java"
    dispatcher = require(dispatcher_path, [
        "ContextSnapshot submissionContext = contextSnapshotFactory.captureAll()",
        "executor.execute(() -> runSubmittedTask(",
        "submissionContext.setThreadLocals()",
        "ContextSnapshotFactory.builder().clearMissing(true).build()",
        '@Qualifier("coreOutboundRestClient") RestClient restClient',
        "opendispatch.core.outbound.queue.wait",
        "opendispatch.core.outbound.execution",
        "opendispatch.core.outbound.rejected",
        "opendispatch.core.outbound.callback.failures",
        "ExecutorServiceMetrics",
        "invokeCompletion(operation, result, completionHandler)",
        "executeSynchronously(String operation, CoreOutboundRequest request)",
        "import org.springframework.web.client.ResourceAccessException;",
    ])
    if "org.springframework.core.io.ResourceAccessException" in dispatcher:
        fail("CoreOutboundDispatcher imports ResourceAccessException from spring-core; use org.springframework.web.client.ResourceAccessException")
    if dispatcher.index("captureAll()") > dispatcher.index("executor.execute"):
        fail("CoreOutboundDispatcher must capture context before queue submission")
    if "HttpClient.newBuilder" in dispatcher or "HttpClient.newHttpClient" in dispatcher:
        fail("CoreOutboundDispatcher must not build a raw JDK HttpClient")
    if re.search(r'\.tag\("(?:taskId|agentId|tenantId|operation|uri|callbackId)"', dispatcher):
        fail("Core outbound executor metrics contain an unbounded tag")

    request = require(
        "ai-event-gateway-netty/gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/outbound/CoreOutboundRequest.java",
        ["record CoreOutboundRequest", "jsonPost", "Collections.unmodifiableMap"],
    )
    if "HttpRequest" in request:
        fail("CoreOutboundRequest must not expose java.net.http.HttpRequest")

    client_config = require(
        "ai-event-gateway-netty/gateway-app/src/main/java/com/opensocket/aievent/gateway/netty/outbound/CoreOutboundHttpClientConfiguration.java",
        [
            '@Bean("coreOutboundJdkHttpClient")',
            '@Bean("coreOutboundRestClient")',
            "RestClient.Builder builder",
            "builder.clone()",
            "JdkClientHttpRequestFactory",
            "requestFactory.setReadTimeout(properties.requestTimeout())",
        ],
    )
    for header in ("traceparent", "tracestate", "baggage"):
        if re.search(rf'header\("{header}"|set\("{header}"', client_config, flags=re.IGNORECASE):
            fail(f"{header} must be injected by observability, not manually in client configuration")

    production_netty = ROOT / "ai-event-gateway-netty"
    manual_dispatcher_new = []
    manual_trace_headers = []
    for source in production_netty.rglob("src/main/java/**/*.java"):
        text = source.read_text(encoding="utf-8")
        relative = str(source.relative_to(ROOT))
        if "new CoreOutboundDispatcher(" in text and source.name != "CoreOutboundDispatcher.java":
            manual_dispatcher_new.append(relative)
        for header in ("traceparent", "tracestate", "baggage"):
            if re.search(rf'header\("{header}"|set\("{header}"', text, flags=re.IGNORECASE):
                manual_trace_headers.append(f"{relative}:{header}")
    if manual_dispatcher_new:
        fail("Production services manually instantiate CoreOutboundDispatcher: " + ", ".join(manual_dispatcher_new))
    if manual_trace_headers:
        fail("Production code manually injects tracing headers: " + ", ".join(manual_trace_headers))

    for caller in [
        "ai-event-gateway-netty/gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/directory/CoreDirectorySyncService.java",
        "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelay.java",
        "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/inbound/InboundEventForwarder.java",
    ]:
        markers = ["CoreOutboundRequest.jsonPost", "coreOutboundDispatcher.submit"]
        if caller.endswith("TaskCallbackRelay.java"):
            markers.append("coreOutboundDispatcher.executeSynchronously")
        text = require(caller, markers)
        if "new CoreOutboundDispatcher(" in text:
            fail(f"{caller} bypasses Spring-managed dispatcher wiring")
        if caller.endswith("TaskCallbackRelay.java") and ("HttpClient.newBuilder" in text or "HttpClient.newHttpClient" in text):
            fail("TaskCallbackRelay synchronous terminal callbacks must use the observable dispatcher client")

    heartbeat = require(
        "ai-event-gateway-core/adapter-worker-app/src/main/java/com/opensocket/aievent/worker/LeaseHeartbeatGuard.java",
        [
            "TaskScheduler",
            "ScheduledFuture",
            'Observation.createNotStarted("adapter.lease.heartbeat"',
            ".parentObservation(null)",
            "future.cancel(false)",
        ],
    )
    if "Executors." in heartbeat or "ScheduledExecutorService" in heartbeat or "shutdownNow" in heartbeat:
        fail("LeaseHeartbeatGuard must not own a scheduled executor after P3-B")

    worker_client = require(
        "ai-event-gateway-core/adapter-worker-app/src/main/java/com/opensocket/aievent/worker/CoreAdapterActionClient.java",
        [
            '@Qualifier("adapterWorkerCoreRestClient") RestClient restClient',
            "restClient.method(method)",
            "ServiceContractVersions.CONTRACT_HEADER",
        ],
    )
    if "HttpClient.newBuilder" in worker_client or "HttpClient.newHttpClient" in worker_client:
        fail("CoreAdapterActionClient must use observable RestClient")

    require(
        "ai-event-gateway-core/adapter-worker-app/src/main/java/com/opensocket/aievent/worker/AdapterWorkerCoreHttpClientConfiguration.java",
        ["RestClient.Builder", "JdkClientHttpRequestFactory", "adapterWorkerCoreRestClient"],
    )

    for pom in [
        "ai-event-gateway-netty/gateway-app/pom.xml",
        "ai-event-gateway-core/adapter-worker-app/pom.xml",
    ]:
        pom_text = read(pom)
        runtime_dependency = re.search(
            r"<dependency>\s*<groupId>org\.springframework\.boot</groupId>\s*"
            r"<artifactId>spring-boot-restclient</artifactId>\s*</dependency>",
            pom_text,
        )
        if runtime_dependency is None:
            fail(f"{pom} must include spring-boot-restclient at compile/runtime scope for Boot 4 RestClient.Builder auto-configuration")

    require(
        "ai-event-gateway-netty/gateway-app/src/test/java/com/opensocket/aievent/gateway/netty/outbound/CoreOutboundRestClientRuntimeWiringTest.java",
        ["RestClientAutoConfiguration", "coreOutboundRestClient", "RestClient.Builder.class"],
    )
    require(
        "ai-event-gateway-core/adapter-worker-app/src/test/java/com/opensocket/aievent/worker/AdapterWorkerRestClientRuntimeWiringTest.java",
        ["RestClientAutoConfiguration", "adapterWorkerCoreRestClient", "RestClient.Builder.class"],
    )

    require(
        "ai-event-gateway-netty/gateway-core/src/test/java/com/opensocket/aievent/gateway/netty/outbound/CoreOutboundDispatcherTest.java",
        [
            "shouldCaptureContextAtSubmissionInjectHeadersAndClearWorkerForNextTask",
            "shouldUseTheSameObservableClientForSynchronousExecution",
            "shouldReportTimeoutWithoutLosingCompletionCallback",
            "shouldRejectWhenBoundedQueueIsFullAndCountCallbackFailure",
            "traceparent",
            "tracestate",
            "baggage",
        ],
    )
    require(
        "ai-event-gateway-core/adapter-worker-app/src/test/java/com/opensocket/aievent/worker/LeaseHeartbeatGuardTest.java",
        [
            "shouldUseSharedSchedulerCreateShortObservationAndCancelWithoutOwningPool",
            "shouldStartHeartbeatAsRootEvenWhenCallerObservationIsCurrent",
            "isShutdown()).isFalse()",
        ],
    )

    for env_file in [
        "deploy/env/.env.local.example",
        "deploy/env/.env.local.ci",
        "deploy/env/.env.release.example",
    ]:
        require(env_file, [
            "GATEWAY_CORE_OUTBOUND_CONNECT_TIMEOUT=3s",
            "GATEWAY_CORE_OUTBOUND_REQUEST_TIMEOUT=3s",
        ])

    require(
        "docs/architecture/P3-B_MANUAL_EXECUTOR_OUTBOUND_HTTP_CONTEXT_PROPAGATION.md",
        [
            "captures a `ContextSnapshot` on the submitting thread",
            "traceparent",
            "tracestate",
            "baggage",
            "parentObservation(null)",
            "P3-C remains responsible",
        ],
    )

    print("P3-B manual executor and outbound HTTP verification passed.")
    print("- Core outbound context is captured before bounded queue submission")
    print("- Spring-managed RestClient provides observable W3C and baggage propagation")
    print("- native executor queue, execution, rejection, callback, and result metrics are bounded")
    print("- adapter heartbeat uses shared TaskScheduler and short root observations")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
