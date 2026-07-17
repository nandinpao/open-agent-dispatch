#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
errors: list[str] = []

for pom in sorted(root.glob("ai-event-gateway-*/pom.xml")):
    module = pom.parent
    source_root = module / "src/main/java"
    if not source_root.exists():
        continue

    imports: set[str] = set()
    for source in source_root.rglob("*.java"):
        text = source.read_text(encoding="utf-8", errors="ignore")
        imports.update(re.findall(r"^import\s+([^;]+);", text, re.MULTILINE))

    if not any(value.startswith("org.springframework.") for value in imports):
        continue

    tree = ET.parse(pom)
    dependencies: set[tuple[str, str]] = set()
    for dependency in tree.findall(".//m:dependencies/m:dependency", ns):
        scope = dependency.findtext("m:scope", default="compile", namespaces=ns)
        if scope in {"test", "provided"}:
            continue
        dependencies.add((
            dependency.findtext("m:groupId", default="", namespaces=ns),
            dependency.findtext("m:artifactId", default="", namespaces=ns),
        ))

    boot_starters = {
        artifact
        for group, artifact in dependencies
        if group == "org.springframework.boot"
        and artifact.startswith("spring-boot-starter")
        and artifact != "spring-boot-starter-test"
    }

    def has(group: str, artifact: str) -> bool:
        return (group, artifact) in dependencies

    required: list[tuple[str, bool]] = []

    uses_context = any(value.startswith((
        "org.springframework.beans.",
        "org.springframework.context.",
        "org.springframework.scheduling.annotation.",
        "org.springframework.stereotype.",
    )) for value in imports)
    required.append((
        "org.springframework:spring-context or a Spring Boot starter",
        not uses_context or has("org.springframework", "spring-context") or bool(boot_starters),
    ))

    uses_boot = any(
        value.startswith("org.springframework.boot.context.properties.")
        or value in {
            "org.springframework.boot.ApplicationArguments",
            "org.springframework.boot.ApplicationRunner",
            "org.springframework.boot.SpringApplication",
        }
        for value in imports
    )
    required.append((
        "org.springframework.boot:spring-boot or a Spring Boot starter",
        not uses_boot or has("org.springframework.boot", "spring-boot") or bool(boot_starters),
    ))

    uses_autoconfigure = any(
        value.startswith("org.springframework.boot.autoconfigure.") for value in imports
    )
    required.append((
        "org.springframework.boot:spring-boot-autoconfigure or a Spring Boot starter",
        not uses_autoconfigure
        or has("org.springframework.boot", "spring-boot-autoconfigure")
        or bool(boot_starters),
    ))

    uses_tx = any(value.startswith("org.springframework.transaction.") for value in imports)
    required.append((
        "org.springframework:spring-tx",
        not uses_tx or has("org.springframework", "spring-tx"),
    ))

    uses_web = any(value.startswith((
        "org.springframework.http.",
        "org.springframework.web.",
    )) for value in imports)
    required.append((
        "org.springframework:spring-web or spring-boot-starter-web/webflux",
        not uses_web
        or has("org.springframework", "spring-web")
        or "spring-boot-starter-web" in boot_starters
        or "spring-boot-starter-webflux" in boot_starters,
    ))

    uses_redis = any(value.startswith("org.springframework.data.redis.") for value in imports)
    required.append((
        "org.springframework.data:spring-data-redis or spring-boot-starter-data-redis",
        not uses_redis
        or has("org.springframework.data", "spring-data-redis")
        or "spring-boot-starter-data-redis" in boot_starters,
    ))

    uses_actuator = any(value.startswith("org.springframework.boot.health.") for value in imports)
    required.append((
        "org.springframework.boot:spring-boot-actuator or spring-boot-starter-actuator",
        not uses_actuator
        or has("org.springframework.boot", "spring-boot-actuator")
        or "spring-boot-starter-actuator" in boot_starters,
    ))

    for dependency, satisfied in required:
        if not satisfied:
            errors.append(
                f"{module.name}: source imports require direct compile dependency {dependency}"
            )

if errors:
    print("Spring compile dependency governance failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Spring compile dependency governance passed.")
PY
