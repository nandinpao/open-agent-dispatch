#!/usr/bin/env python3
"""Detect and safely remove accidentally nested OpenDispatch module copies.

A common extraction mistake is placing the repository archive inside an existing
module directory, for example:

    ai-event-gateway-core/ai-event-gateway-netty

The Stage 0 feature-freeze correctly treats production sources in that nested
copy as new artifacts. This utility never changes the feature-freeze baseline.
It removes a nested module only when every relevant file is byte-for-byte
identical to the canonical top-level module. Conflicting files are reported and
left untouched.
"""
from __future__ import annotations

import argparse
import hashlib
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE_NAMES = (
    "ai-event-gateway-core",
    "ai-event-gateway-netty",
    "ai-event-gateway-admin-ui",
)
IGNORED_PARTS = {
    ".git",
    ".idea",
    ".vscode",
    ".next",
    ".next-ci",
    ".ci-output",
    "node_modules",
    "target",
}


@dataclass(frozen=True)
class NestedModuleResult:
    nested: Path
    canonical: Path
    safe_duplicate: bool
    conflicts: tuple[str, ...]
    compared_files: int


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def relevant_files(root: Path):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        if any(part in IGNORED_PARTS for part in relative.parts):
            continue
        yield path, relative


def nested_module_roots() -> list[Path]:
    results: set[Path] = set()
    for host_name in MODULE_NAMES:
        host = ROOT / host_name
        if not host.is_dir():
            continue
        for module_name in MODULE_NAMES:
            candidate = host / module_name
            if candidate.is_dir() and candidate.resolve() != (ROOT / module_name).resolve():
                results.add(candidate)
    return sorted(results)


def inspect(nested: Path) -> NestedModuleResult:
    canonical = ROOT / nested.name
    conflicts: list[str] = []
    compared = 0
    if not canonical.is_dir():
        return NestedModuleResult(
            nested=nested,
            canonical=canonical,
            safe_duplicate=False,
            conflicts=("canonical top-level module is missing",),
            compared_files=0,
        )

    for nested_file, relative in relevant_files(nested):
        canonical_file = canonical / relative
        if not canonical_file.is_file():
            conflicts.append(f"missing canonical file: {relative.as_posix()}")
            continue
        compared += 1
        if nested_file.stat().st_size != canonical_file.stat().st_size or sha256(nested_file) != sha256(canonical_file):
            conflicts.append(f"content differs: {relative.as_posix()}")
        if len(conflicts) >= 20:
            conflicts.append("additional conflicts omitted")
            break

    return NestedModuleResult(
        nested=nested,
        canonical=canonical,
        safe_duplicate=not conflicts,
        conflicts=tuple(conflicts),
        compared_files=compared,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Remove only nested modules proven byte-for-byte identical to their canonical top-level modules.",
    )
    args = parser.parse_args()

    nested = nested_module_roots()
    if not nested:
        print("OpenDispatch project layout is clean: no nested module copies detected.")
        return 0

    print("Detected nested OpenDispatch module copies:")
    results = [inspect(path) for path in nested]
    unresolved = False
    has_conflict = False
    for result in results:
        relative_nested = result.nested.relative_to(ROOT).as_posix()
        relative_canonical = result.canonical.relative_to(ROOT).as_posix()
        if result.safe_duplicate:
            print(
                f"  SAFE DUPLICATE: {relative_nested} -> {relative_canonical} "
                f"({result.compared_files} files matched)"
            )
            if args.apply:
                shutil.rmtree(result.nested)
                print(f"    removed {relative_nested}")
            else:
                unresolved = True
        else:
            unresolved = True
            has_conflict = True
            print(f"  CONFLICT: {relative_nested} -> {relative_canonical}")
            for conflict in result.conflicts:
                print(f"    - {conflict}")

    if unresolved:
        if not args.apply:
            print("Run again with --apply to remove only SAFE DUPLICATE entries.")
        if has_conflict:
            print("Conflicting nested modules are never removed automatically.", file=sys.stderr)
        return 1

    print("Nested duplicate cleanup completed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
