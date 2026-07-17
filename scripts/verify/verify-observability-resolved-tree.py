#!/usr/bin/env python3
"""Validate resolved Maven dependency trees after the P1-A OTel cutover."""
from __future__ import annotations

import re
import sys
from pathlib import Path

EXPECTED_OTEL_MODULES = {"control-plane-app", "adapter-worker-app", "gateway-app"}
MODULE_HEADER = re.compile(r"---\s+dependency:[^@]+@\s+([^\s]+)\s+---")


def fail(errors: list[str]) -> int:
    for error in errors:
        print(f"[FAIL] {error}", file=sys.stderr)
    return 1


def main(arguments: list[str]) -> int:
    if not arguments:
        print("usage: verify-observability-resolved-tree.py <dependency-tree-log>...", file=sys.stderr)
        return 2

    current_module: str | None = None
    starter_modules: set[str] = set()
    otel_bridge_modules: set[str] = set()
    errors: list[str] = []

    for argument in arguments:
        path = Path(argument)
        if not path.is_file():
            errors.append(f"Missing dependency-tree log: {path}")
            continue
        for line_number, line in enumerate(path.read_text(errors="replace").splitlines(), start=1):
            header = MODULE_HEADER.search(line)
            if header:
                current_module = header.group(1)
                continue

            lowered = line.lower()
            location = f"{path}:{line_number}"
            module = current_module or "unknown"
            if "spring-cloud-starter-sleuth" in lowered or "spring-cloud-sleuth-" in lowered:
                errors.append(f"{location}: Spring Cloud Sleuth resolved in module {module}")
            if "micrometer-tracing-bridge-brave" in lowered:
                errors.append(f"{location}: Brave tracing bridge resolved in module {module}")
            if "zipkin-reporter-brave" in lowered:
                errors.append(f"{location}: Zipkin Reporter Brave resolved in module {module}")
            if "spring-boot-starter-zipkin" in lowered or "opentelemetry-exporter-zipkin" in lowered:
                errors.append(f"{location}: forbidden Zipkin starter/exporter resolved in module {module}")
            if "spring-boot-starter-opentelemetry" in lowered:
                if current_module is None:
                    errors.append(f"{location}: cannot identify module for Spring Boot OpenTelemetry starter")
                else:
                    starter_modules.add(current_module)
            if "micrometer-tracing-bridge-otel" in lowered:
                if current_module is None:
                    errors.append(f"{location}: cannot identify module for OpenTelemetry bridge")
                else:
                    otel_bridge_modules.add(current_module)

    if starter_modules != EXPECTED_OTEL_MODULES:
        errors.append(
            "Resolved Spring Boot OpenTelemetry starter placement changed: "
            f"expected={sorted(EXPECTED_OTEL_MODULES)}, actual={sorted(starter_modules)}"
        )
    if otel_bridge_modules != EXPECTED_OTEL_MODULES:
        errors.append(
            "Resolved Micrometer OpenTelemetry bridge placement changed: "
            f"expected={sorted(EXPECTED_OTEL_MODULES)}, actual={sorted(otel_bridge_modules)}"
        )
    if starter_modules != otel_bridge_modules:
        errors.append(
            "Spring Boot OpenTelemetry starter and Micrometer OTel bridge module sets differ: "
            f"starter={sorted(starter_modules)}, bridge={sorted(otel_bridge_modules)}"
        )

    if errors:
        return fail(errors)

    print(
        "P1-A resolved dependency-tree policy passed for modules: "
        + ", ".join(sorted(EXPECTED_OTEL_MODULES))
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
