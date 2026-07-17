#!/usr/bin/env python3
"""Validate the P1-A OpenTelemetry-only application assembly policy.

P1-A atomically replaces the legacy Micrometer Brave bridge and Zipkin reporter
with Spring Boot 4's managed OpenTelemetry starter in exactly three executable
applications. Library modules remain vendor-neutral and must not assemble a
tracer SDK or exporter themselves.
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

EXECUTABLE_APP_POMS = {
    Path("ai-event-gateway-core/control-plane-app/pom.xml"),
    Path("ai-event-gateway-core/adapter-worker-app/pom.xml"),
    Path("ai-event-gateway-netty/gateway-app/pom.xml"),
}
OTEL_STARTER = ("org.springframework.boot", "spring-boot-starter-opentelemetry")
OTEL_BRIDGE = ("io.micrometer", "micrometer-tracing-bridge-otel")
BRAVE_BRIDGE = ("io.micrometer", "micrometer-tracing-bridge-brave")
ZIPKIN_REPORTER = ("io.zipkin.reporter2", "zipkin-reporter-brave")
FORBIDDEN_DECLARED = {
    ("org.springframework.cloud", "spring-cloud-starter-sleuth"),
    ("org.springframework.boot", "spring-boot-starter-zipkin"),
    ("io.opentelemetry", "opentelemetry-exporter-zipkin"),
    BRAVE_BRIDGE,
    ZIPKIN_REPORTER,
}


@dataclass(frozen=True)
class PomDependencies:
    path: Path
    direct_dependencies: frozenset[tuple[str, str]]
    all_declared_dependencies: frozenset[tuple[str, str]]


def fail(messages: list[str]) -> None:
    for message in messages:
        print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def dependency_coordinate(dependency: ET.Element) -> tuple[str, str] | None:
    group_id = dependency.findtext("m:groupId", default="", namespaces=NS).strip()
    artifact_id = dependency.findtext("m:artifactId", default="", namespaces=NS).strip()
    if group_id and artifact_id:
        return group_id, artifact_id
    return None


def pom_dependencies(path: Path) -> PomDependencies:
    root = ET.parse(path).getroot()
    direct: set[tuple[str, str]] = set()
    direct_section = root.find("m:dependencies", NS)
    if direct_section is not None:
        for dependency in direct_section.findall("m:dependency", NS):
            coordinate = dependency_coordinate(dependency)
            if coordinate:
                direct.add(coordinate)

    declared: set[tuple[str, str]] = set()
    for dependency in root.findall(".//m:dependency", NS):
        coordinate = dependency_coordinate(dependency)
        if coordinate:
            declared.add(coordinate)

    return PomDependencies(path.relative_to(ROOT), frozenset(direct), frozenset(declared))


def reactor_poms() -> list[Path]:
    poms = [ROOT / "pom.xml"]
    for reactor in (ROOT / "ai-event-gateway-core", ROOT / "ai-event-gateway-netty"):
        poms.extend(
            path
            for path in reactor.rglob("pom.xml")
            if "target" not in path.relative_to(reactor).parts
        )
    return sorted(set(poms))


def main() -> int:
    errors: list[str] = []
    poms = [pom_dependencies(path) for path in reactor_poms()]
    pom_by_path = {pom.path: pom for pom in poms}

    for pom in poms:
        for dependency in sorted(pom.all_declared_dependencies):
            group_id, artifact_id = dependency
            if dependency in FORBIDDEN_DECLARED or (
                group_id == "org.springframework.cloud" and artifact_id.startswith("spring-cloud-sleuth-")
            ):
                errors.append(f"{pom.path}: forbidden dependency {group_id}:{artifact_id}")

        if OTEL_BRIDGE in pom.direct_dependencies:
            errors.append(
                f"{pom.path}: declare {OTEL_STARTER[0]}:{OTEL_STARTER[1]} instead of the raw "
                f"{OTEL_BRIDGE[0]}:{OTEL_BRIDGE[1]} bridge"
            )

        has_starter = OTEL_STARTER in pom.direct_dependencies
        if pom.path in EXECUTABLE_APP_POMS:
            if not has_starter:
                errors.append(
                    f"{pom.path}: executable application must directly declare "
                    f"{OTEL_STARTER[0]}:{OTEL_STARTER[1]}"
                )
        elif has_starter:
            errors.append(
                f"{pom.path}: OpenTelemetry application assembly is not allowed in a library/parent module"
            )

    missing_app_poms = sorted(path for path in EXECUTABLE_APP_POMS if path not in pom_by_path)
    if missing_app_poms:
        errors.append(f"Missing P1-A executable application POMs: {missing_app_poms}")

    required_files = [
        ROOT / "scripts/verify/verify-observability-resolved-tree.py",
        ROOT / "scripts/verify/verify-observability-dependency-tree.sh",
        ROOT / "scripts/verify/verify-slf4j-direct-dependencies.py",
        ROOT / "docs/architecture/P1-A_OPENTELEMETRY_BRIDGE_CUTOVER.md",
    ]
    for required_file in required_files:
        if not required_file.is_file():
            errors.append(f"Missing P1-A policy artifact: {required_file.relative_to(ROOT)}")

    parent_poms = [
        ROOT / "ai-event-gateway-core/pom.xml",
        ROOT / "ai-event-gateway-netty/pom.xml",
    ]
    required_enforcer_tokens = [
        "enforce-observability-dependency-policy",
        "org.springframework.cloud:spring-cloud-starter-sleuth",
        "org.springframework.cloud:spring-cloud-sleuth-*",
        "org.springframework.boot:spring-boot-starter-zipkin",
        "io.opentelemetry:opentelemetry-exporter-zipkin",
        "io.micrometer:micrometer-tracing-bridge-brave",
        "io.zipkin.reporter2:zipkin-reporter-brave",
        "<searchTransitive>true</searchTransitive>",
    ]
    for path in parent_poms:
        text = path.read_text()
        for token in required_enforcer_tokens:
            if token not in text:
                errors.append(f"{path.relative_to(ROOT)}: missing Maven Enforcer policy token {token}")
        if "<exclude>io.micrometer:micrometer-tracing-bridge-otel</exclude>" in text:
            errors.append(
                f"{path.relative_to(ROOT)}: P0-B still bans the OpenTelemetry bridge after the P1-A cutover"
            )

    if errors:
        fail(errors)

    print("P1-A OpenTelemetry dependency policy passed.")
    print("Spring Boot's OpenTelemetry starter is declared by exactly three executable applications.")
    print("Sleuth, Brave, Zipkin reporters/exporters, and raw bridge assembly are blocked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
