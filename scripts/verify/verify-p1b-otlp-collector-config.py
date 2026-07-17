#!/usr/bin/env python3
"""Verify P1-B OpenTelemetry OTLP/Collector runtime configuration."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

APP_CONFIGS = [
    Path("ai-event-gateway-core/control-plane-app/src/main/resources/application.yml"),
    Path("ai-event-gateway-core/adapter-worker-app/src/main/resources/application.yml"),
    Path("ai-event-gateway-netty/gateway-app/src/main/resources/application.yml"),
]
COMPOSE_FILES = [
    Path("deploy/docker-compose.local.yml"),
    Path("deploy/docker-compose.ci.yml"),
]
ENV_FILES = [
    Path("deploy/env/.env.local.example"),
    Path("deploy/env/.env.local.ci"),
]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(rel: Path | str) -> str:
    path = ROOT / rel
    if not path.is_file():
        fail(f"Missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: Path | str, markers: list[str]) -> str:
    text = read(rel)
    for marker in markers:
        if marker not in text:
            fail(f"{rel} is missing required marker: {marker}")
    return text


def forbid(rel: Path | str, markers: list[str]) -> None:
    text = read(rel)
    for marker in markers:
        if marker in text:
            fail(f"{rel} still contains forbidden marker: {marker}")


def main() -> int:
    app_markers = [
        "enabled: ${MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED:false}",
        "consume: ${MANAGEMENT_TRACING_PROPAGATION_CONSUME:w3c,b3}",
        "produce: ${MANAGEMENT_TRACING_PROPAGATION_PRODUCE:w3c}",
        "service.name: ${spring.application.name}",
        "service.namespace: ${OTEL_SERVICE_NAMESPACE:opendispatch}",
        "service.version: ${OTEL_SERVICE_VERSION:dev}",
        "deployment.environment: ${DEPLOYMENT_ENVIRONMENT:local}",
        "deployment.environment.name: ${DEPLOYMENT_ENVIRONMENT:local}",
        "endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}",
        "enabled: ${MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED:false}",
        "url: ${OTEL_EXPORTER_OTLP_METRICS_ENDPOINT:http://localhost:4318/v1/metrics}",
    ]
    forbidden_runtime = [
        "MANAGEMENT_TRACING_PROPAGATION_TYPE",
        "MANAGEMENT_ZIPKIN_TRACING_EXPORT_ENABLED",
        "MANAGEMENT_ZIPKIN_TRACING_ENDPOINT",
        "management.zipkin",
        "http://localhost:9411/api/v2/spans",
    ]
    for rel in APP_CONFIGS:
        require(rel, app_markers)
        forbid(rel, forbidden_runtime + ["  zipkin:\n"])

    collector = Path("deploy/observability/otel-collector-config.yml")
    require(
        collector,
        [
            "health_check:",
            "endpoint: 0.0.0.0:13133",
            "otlp:",
            "grpc:",
            "endpoint: 0.0.0.0:4317",
            "http:",
            "endpoint: 0.0.0.0:4318",
            "memory_limiter:",
            "resource:",
            "batch:",
            "debug:",
            "verbosity: detailed",
            "traces:",
            "metrics:",
        ],
    )

    compose_markers = [
        "\n  otel-collector:\n",
        "otel/opentelemetry-collector-contrib:0.156.0",
        'command: ["--config=/etc/otelcol-contrib/config/config.yaml"]',
        "otel-collector-config:/etc/otelcol-contrib/config:ro",
        "${OTEL_COLLECTOR_GRPC_PORT:-14317}:4317",
        "${OTEL_COLLECTOR_HTTP_PORT:-14318}:4318",
        "${OTEL_COLLECTOR_HEALTH_PORT:-13133}:13133",
        "MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED:",
        "MANAGEMENT_TRACING_PROPAGATION_CONSUME:",
        "MANAGEMENT_TRACING_PROPAGATION_PRODUCE:",
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:",
        "MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED:",
        "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT:",
        "OTEL_SERVICE_NAMESPACE:",
        "DEPLOYMENT_ENVIRONMENT:",
        "adapter-worker:",
        'profiles: ["observability-smoke"]',
        "ADAPTER_WORKER_ENABLED: \"false\"",
        "ai-event-gateway-adapter-worker.jar",
    ]
    for rel in COMPOSE_FILES:
        require(rel, compose_markers)
        forbid(rel, forbidden_runtime + ["http://zipkin:9411"])

    env_markers = [
        "OTEL_COLLECTOR_IMAGE=otel/opentelemetry-collector-contrib:0.156.0",
        "MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=true",
        "MANAGEMENT_TRACING_PROPAGATION_CONSUME=w3c,b3",
        "MANAGEMENT_TRACING_PROPAGATION_PRODUCE=w3c",
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces",
        "MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true",
        "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://otel-collector:4318/v1/metrics",
        "OTEL_SERVICE_NAMESPACE=opendispatch",
        "DEPLOYMENT_ENVIRONMENT=",
    ]
    for rel in ENV_FILES:
        require(rel, env_markers)
        forbid(rel, forbidden_runtime + ["Sleuth-style"])

    require(
        "scripts/local-compose-up.sh",
        [
            "LOCAL_ADAPTER_WORKER_RUNTIME_VOLUME",
            "control-plane-app,adapter-worker-app",
            "runtime/adapter-worker.jar",
            "ai-event-gateway-adapter-worker.jar",
        ],
    )
    require(
        "scripts/ci/local-ci.sh",
        [
            "CI_ADAPTER_WORKER_RUNTIME_VOLUME",
            "CI_OTEL_COLLECTOR_CONFIG_VOLUME",
            "otel-collector-config.yml",
            "control-plane-app,adapter-worker-app",
            "runtime/adapter-worker.jar",
            "Stage 8.1 - OTLP export smoke",
            "scripts/observability/otlp-export-smoke.sh",
        ],
    )
    smoke = ROOT / "scripts/observability/otlp-export-smoke.sh"
    if not smoke.is_file() or not smoke.stat().st_mode & 0o111:
        fail("OTLP export smoke script is missing or not executable")
    require(
        smoke.relative_to(ROOT),
        [
            "--profile observability-smoke",
            "ai-event-gateway-core",
            "ai-event-gateway-netty",
            "ai-event-gateway-adapter-worker",
            "TracesExporter",
            "MetricsExporter",
        ],
    )
    require(
        "docs/architecture/P1-B_OTLP_COLLECTOR_ARCHITECTURE.md",
        [
            "Micrometer Tracing + OpenTelemetry + OTLP + Collector",
            "management.tracing.propagation.consume=w3c,b3",
            "management.tracing.propagation.produce=w3c",
            "make smoke-otlp",
        ],
    )

    print("P1-B OTLP/Collector configuration verification passed.")
    print("- 3 executable application OTLP configurations verified")
    print("- local and CI Collector topology verified")
    print("- Adapter Worker observability smoke profile verified")
    print("- legacy Zipkin runtime configuration rejected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
