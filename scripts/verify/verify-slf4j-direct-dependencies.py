#!/usr/bin/env python3
"""Require Maven modules that use SLF4J in main Java code to declare slf4j-api directly."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SLF4J_API = ("org.slf4j", "slf4j-api")


def find_module(source: Path) -> Path | None:
    current = source.parent
    while current != ROOT and ROOT in current.parents:
        if (current / "pom.xml").is_file():
            return current
        current = current.parent
    return None


def direct_dependencies(pom: Path) -> list[tuple[str, str]]:
    tree = ET.parse(pom)
    project = tree.getroot()
    dependencies = project.find("m:dependencies", NS)
    if dependencies is None:
        return []
    result: list[tuple[str, str]] = []
    for dependency in dependencies.findall("m:dependency", NS):
        group_id = dependency.findtext("m:groupId", default="", namespaces=NS).strip()
        artifact_id = dependency.findtext("m:artifactId", default="", namespaces=NS).strip()
        if group_id and artifact_id:
            result.append((group_id, artifact_id))
    return result


def main() -> int:
    module_sources: dict[Path, list[Path]] = {}
    source_roots = [ROOT / "ai-event-gateway-core", ROOT / "ai-event-gateway-netty"]
    sources = sorted(source for source_root in source_roots for source in source_root.rglob("*.java"))
    for source in sources:
        relative_parts = source.relative_to(ROOT).parts
        if "target" in relative_parts or "src" not in relative_parts:
            continue
        normalized = source.as_posix()
        if "/src/main/" not in normalized:
            continue
        text = source.read_text(errors="ignore")
        if "import org.slf4j." not in text:
            continue
        module = find_module(source)
        if module is None:
            print(f"[FAIL] Cannot locate Maven module for {source.relative_to(ROOT)}", file=sys.stderr)
            return 1
        module_sources.setdefault(module, []).append(source)

    errors: list[str] = []
    for module, sources in sorted(module_sources.items()):
        dependencies = direct_dependencies(module / "pom.xml")
        declarations = dependencies.count(SLF4J_API)
        relative_module = module.relative_to(ROOT)
        if declarations == 0:
            errors.append(
                f"{relative_module}: imports org.slf4j in {len(sources)} main source file(s) "
                "but does not directly declare org.slf4j:slf4j-api"
            )
        elif declarations > 1:
            errors.append(
                f"{relative_module}: declares org.slf4j:slf4j-api {declarations} times; expected exactly once"
            )

    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1

    print(
        f"SLF4J direct dependency policy passed for {len(module_sources)} module(s) "
        f"and {sum(map(len, module_sources.values()))} production source file(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
