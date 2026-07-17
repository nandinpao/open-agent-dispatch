#!/usr/bin/env python3
from __future__ import annotations

import csv
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

CORE_BASE_PACKAGE = "com.opensocket.aievent.core"
DATABASE_BASE_PACKAGE = "com.opensocket.aievent.database"
IMPORT_RE = re.compile(r"^import\s+([\w.]+);", re.MULTILINE)
PACKAGE_RE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)

@dataclass(frozen=True, order=True)
class DetailedDependency:
    source_context: str
    target_context: str
    source_file: str
    imported_type: str


def repo_root(start: Path | None = None) -> Path:
    current = (start or Path.cwd()).resolve()
    for candidate in (current, *current.parents):
        if (candidate / "architecture/module-candidates.csv").is_file() and (candidate / "pom.xml").is_file():
            return candidate
    raise RuntimeError("Could not locate repository root containing architecture/module-candidates.csv")


def production_source_roots(root: Path) -> list[Path]:
    roots: list[Path] = []
    for module in sorted(root.iterdir()):
        if not (module / "pom.xml").is_file():
            continue
        source_root = module / "src/main/java/com/opensocket/aievent/core"
        if source_root.is_dir():
            roots.append(source_root)
    database_root = root / "database-platform/src/main/java/com/opensocket/aievent/database"
    if database_root.is_dir():
        roots.append(database_root)
    if not roots:
        raise RuntimeError("No production Java source roots found")
    return roots


def load_context_map(root: Path) -> dict[str, str]:
    with (root / "architecture/module-candidates.csv").open(newline="", encoding="utf-8") as handle:
        return {row["package_segment"].strip(): row["module_candidate"].strip() for row in csv.DictReader(handle)}


def context_for_type(fqcn: str, mapping: dict[str, str]) -> str | None:
    database_prefix = DATABASE_BASE_PACKAGE + "."
    if fqcn == DATABASE_BASE_PACKAGE or fqcn.startswith(database_prefix):
        return "database-platform"
    core_prefix = CORE_BASE_PACKAGE + "."
    if fqcn == CORE_BASE_PACKAGE or not fqcn.startswith(core_prefix):
        return None
    remainder = fqcn[len(core_prefix):]
    segment = remainder.split(".", 1)[0]
    if segment and segment[0].isupper():
        segment = "__root__"
    return mapping.get(segment, "unmapped:" + segment)


def is_persistence_dao(imported_type: str) -> bool:
    return (".database.persistence." in imported_type
            and ".dao." in imported_type
            and imported_type.endswith("Dao"))


def scan_dependencies(root: Path) -> tuple[Counter[tuple[str, str]], list[DetailedDependency], list[DetailedDependency]]:
    mapping = load_context_map(root)
    classes: dict[str, tuple[Path, Path]] = {}
    for source_root in production_source_roots(root):
        for java_file in source_root.rglob("*.java"):
            text = java_file.read_text(encoding="utf-8")
            package_match = PACKAGE_RE.search(text)
            if package_match:
                fqcn = f"{package_match.group(1)}.{java_file.stem}"
                if fqcn in classes:
                    raise RuntimeError(f"Duplicate production type {fqcn}: {classes[fqcn][1]} and {java_file}")
                classes[fqcn] = (source_root, java_file)

    context_edges: Counter[tuple[str, str]] = Counter()
    details: list[DetailedDependency] = []
    repository_details: list[DetailedDependency] = []
    for source_type, (source_root, java_file) in sorted(classes.items()):
        source_context = context_for_type(source_type, mapping)
        text = java_file.read_text(encoding="utf-8")
        relative = java_file.relative_to(source_root).as_posix()
        for imported_type in IMPORT_RE.findall(text):
            if imported_type not in classes:
                continue
            target_context = context_for_type(imported_type, mapping)
            if source_context is None or target_context is None or source_context == target_context:
                continue
            dependency = DetailedDependency(source_context, target_context, relative, imported_type)
            context_edges[(source_context, target_context)] += 1
            details.append(dependency)
            simple_name = imported_type.rsplit(".", 1)[-1]
            if simple_name.endswith("Repository") or is_persistence_dao(imported_type):
                repository_details.append(dependency)
    return context_edges, sorted(set(details)), sorted(set(repository_details))


def read_context_baseline(path: Path) -> set[tuple[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return {(row["source_context"], row["target_context"]) for row in csv.DictReader(handle)}


def read_detail_baseline(path: Path) -> set[DetailedDependency]:
    with path.open(newline="", encoding="utf-8") as handle:
        return {DetailedDependency(row["source_context"], row["target_context"], row["source_file"], row["imported_type"]) for row in csv.DictReader(handle)}


def write_context_csv(path: Path, edges: Counter[tuple[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["source_context", "target_context", "import_count"])
        for (source, target), count in sorted(edges.items()):
            writer.writerow([source, target, count])


def write_detail_csv(path: Path, dependencies: Iterable[DetailedDependency]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["source_context", "target_context", "source_file", "imported_type"])
        for dependency in sorted(dependencies):
            writer.writerow([dependency.source_context, dependency.target_context, dependency.source_file, dependency.imported_type])
