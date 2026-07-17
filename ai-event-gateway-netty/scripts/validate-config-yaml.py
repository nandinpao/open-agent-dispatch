#!/usr/bin/env python3
"""Validate Spring YAML files and fail on duplicate mapping keys.

This lightweight check is intentionally dependency-minimal. It uses PyYAML when available and
should be run in CI before packaging production images/artifacts.
"""
from __future__ import annotations

import glob
import os
import sys
from pathlib import Path

try:
    import yaml
except ImportError as exc:  # pragma: no cover - CI environment problem
    print("PyYAML is required for scripts/validate-config-yaml.py", file=sys.stderr)
    raise SystemExit(2) from exc


class UniqueKeyLoader(yaml.SafeLoader):
    """SafeLoader variant that rejects duplicate mapping keys."""


def construct_mapping(loader: UniqueKeyLoader, node: yaml.nodes.MappingNode, deep: bool = False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise yaml.constructor.ConstructorError(
                "while constructing a mapping",
                node.start_mark,
                f"duplicate key: {key}",
                key_node.start_mark,
            )
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


UniqueKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, construct_mapping)


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    patterns = ["**/application*.yml", "**/application*.yaml"]
    files: list[Path] = []
    for pattern in patterns:
        files.extend(Path(path) for path in glob.glob(str(root / pattern), recursive=True))
    files = sorted(set(path for path in files if path.is_file()))

    if not files:
        print(f"No application YAML files found under {root}")
        return 0

    failed = False
    for path in files:
        try:
            with path.open("r", encoding="utf-8") as handle:
                yaml.load(handle, Loader=UniqueKeyLoader)
            print(f"OK  {path.relative_to(root)}")
        except Exception as exc:  # pragma: no cover - command line reporting
            failed = True
            print(f"ERR {path.relative_to(root)}: {exc}", file=sys.stderr)

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
