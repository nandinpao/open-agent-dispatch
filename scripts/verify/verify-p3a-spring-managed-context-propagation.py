#!/usr/bin/env python3
"""Verify P3-A Spring-managed task context propagation and bounded metrics."""
from __future__ import annotations

import re
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


def main() -> int:
    apps = [
        (
            "ai-event-gateway-core/control-plane-app",
            "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/AiEventGatewayCoreApplication.java",
            "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/async/SpringManagedTaskPoolConfiguration.java",
            "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/context/OpenDispatchContextPropagationConfiguration.java",
            "core-async-",
            "core-scheduler-",
        ),
        (
            "ai-event-gateway-core/adapter-worker-app",
            "ai-event-gateway-core/adapter-worker-app/src/main/java/com/opensocket/aievent/worker/AdapterWorkerApplication.java",
            "ai-event-gateway-core/adapter-worker-app/src/main/java/com/opensocket/aievent/worker/async/SpringManagedTaskPoolConfiguration.java",
            "ai-event-gateway-core/adapter-worker-app/src/main/java/com/opensocket/aievent/worker/async/OpenDispatchMdcContextPropagationConfiguration.java",
            "adapter-worker-async-",
            "adapter-worker-scheduler-",
        ),
        (
            "ai-event-gateway-netty/gateway-app",
            "ai-event-gateway-netty/gateway-app/src/main/java/com/opensocket/aievent/gateway/netty/AiEventGatewayNettyApplication.java",
            "ai-event-gateway-netty/gateway-app/src/main/java/com/opensocket/aievent/gateway/netty/async/SpringManagedTaskPoolConfiguration.java",
            "ai-event-gateway-netty/gateway-app/src/main/java/com/opensocket/aievent/gateway/netty/async/OpenDispatchMdcContextPropagationConfiguration.java",
            "netty-async-",
            "netty-scheduler-",
        ),
    ]
    for module, application, configuration, mdc_configuration, async_prefix, scheduler_prefix in apps:
        require(f"{module}/pom.xml", [
            "<artifactId>context-propagation</artifactId>",
            "<artifactId>micrometer-core</artifactId>",
        ])
        require(application, ["@EnableAsync", "@EnableScheduling"])
        configuration_text = require(configuration, [
            "TaskDecorator openDispatchTaskMetricsDecorator",
            "ThreadPoolTaskExecutorCustomizer",
            "ThreadPoolTaskSchedulerCustomizer",
            "opendispatch.task.execution",
            "opendispatch.task.queue.wait",
            "opendispatch.task.failures",
            "opendispatch.task.rejected",
            '"application".equals(pool)',
            "setRemoveOnCancelPolicy(true)",
        ])
        for forbidden_tag in ("task.class", "task.name", "method.name", "thread.name"):
            if forbidden_tag in configuration_text:
                fail(f"{configuration} introduces unbounded task metric tag: {forbidden_tag}")
        require(mdc_configuration, [
            "ContextRegistry.getInstance()",
            "Slf4jThreadLocalAccessor",
            '"taskId"',
            '"tenantId"',
            '"operatorId"',
            '"requestId"',
            '"correlationId"',
            "removeThreadLocalAccessor(Slf4jThreadLocalAccessor.KEY)",
        ])
        yaml = require(f"{module}/src/main/resources/application.yml", [
            "propagate-context: true",
            f"thread-name-prefix: {async_prefix}",
            f"thread-name-prefix: {scheduler_prefix}",
            "await-termination: true",
        ])
        if yaml.count("propagate-context: true") != 1:
            fail(f"{module} must declare exactly one context propagation switch")

    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/context/OpenDispatchRequestContextThreadLocalAccessor.java",
        [
            "implements ThreadLocalAccessor<OpenDispatchRequestContext>",
            'KEY = "opendispatch.request-context"',
            "OpenDispatchRequestContextHolder.getValue()",
            "OpenDispatchRequestContextHolder.setValue(value)",
            "OpenDispatchRequestContextHolder.clear()",
        ],
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/http/context/OpenDispatchContextPropagationConfiguration.java",
        [
            "ContextRegistry.getInstance()",
            "registerThreadLocalAccessor",
            "Slf4jThreadLocalAccessor",
            '"taskId"',
            '"tenantId"',
            "removeThreadLocalAccessor",
        ],
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/http/context/OpenDispatchAsyncContextPropagationTest.java",
        [
            "newSingleThreadExecutor",
            "new Slf4jThreadLocalAccessor",
            '"traceId", "trace-a"',
            '"taskId", "task-a"',
            "assertThat(clean.requestContext()).isNull()",
            "assertThat(clean.mdc()).isNullOrEmpty()",
        ],
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/async/SpringManagedTaskPoolConfigurationTest.java",
        [
            "opendispatch.task.queue.wait",
            "opendispatch.task.execution",
            "opendispatch.task.failures",
            "opendispatch.task.rejected",
            "core-scheduler-test",
        ],
    )

    production_roots = [ROOT / "ai-event-gateway-core", ROOT / "ai-event-gateway-netty"]
    scheduled = 0
    async_methods = 0
    custom_executor_files: set[str] = set()
    for base in production_roots:
        for source in base.rglob("src/main/java/**/*.java"):
            text = source.read_text(encoding="utf-8")
            scheduled += text.count("@Scheduled")
            async_methods += len(re.findall(r"@Async\b", text))
            if "Executors." in text or re.search(r"new\s+ThreadPoolExecutor\s*\(", text) or "ScheduledExecutorService" in text:
                custom_executor_files.add(str(source.relative_to(ROOT)))
    if scheduled != 21:
        fail(f"Expected the reviewed inventory of 21 @Scheduled methods, found {scheduled}")
    if async_methods != 0:
        fail(f"P3-A expected no production @Async methods before opt-in adoption, found {async_methods}")

    expected_manual = {
        "ai-event-gateway-netty/gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/outbound/CoreOutboundDispatcher.java",
    }
    unexpected = custom_executor_files - expected_manual
    if unexpected:
        fail("Unexpected manually-created production executors added during P3-A: " + ", ".join(sorted(unexpected)))
    if not expected_manual.issubset(custom_executor_files):
        fail("P3-B deferred executor inventory changed unexpectedly")

    doc = require("docs/architecture/P3-A_SPRING_MANAGED_ASYNC_CONTEXT_PROPAGATION.md", [
        "21 `@Scheduled` methods",
        "default event multicaster therefore remains synchronous",
        "CoreOutboundDispatcher` is handled by P3-B",
        "LeaseHeartbeatGuard` was migrated to the shared Spring TaskScheduler in P3-B",
        "Netty EventLoop and Agent protocol propagation remain P3-C",
        "opendispatch.task.rejected",
        "Queue-wait duration is recorded only for application executor tasks",
    ])
    if "task.class" in doc or "method.name" in doc:
        fail("P3-A metrics documentation must not introduce unbounded task-name tags")

    print("P3-A Spring-managed context propagation verification passed.")
    print("- three executable apps use Boot ContextPropagatingTaskDecorator wiring")
    print("- OpenDispatch request context is registered with Micrometer ContextRegistry")
    print("- scheduler/application pools expose bounded execution, failure, rejection, and queue metrics")
    print("- thread reuse tests guard tenant/operator/task/trace context isolation")
    print("- only the bounded CoreOutboundDispatcher executor remains manual after P3-B heartbeat migration")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
