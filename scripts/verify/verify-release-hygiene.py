#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path

BANNED_DIRS = {
    "node_modules",
    ".next",
    ".next-ci",
    "target",
    "__pycache__",
}
BANNED_FILES = {
    "tsconfig.tsbuildinfo",
}
BANNED_SUFFIXES = {
    ".pyc",
    ".pyo",
}
DEFAULT_IGNORE_PARTS = {
    ".git",
    ".ci-output",
    "docs/archive",
}


def should_ignore(path: Path, root: Path) -> bool:
    rel = path.relative_to(root)
    rel_text = str(rel)
    if rel_text.startswith("docs/archive/"):
        return True
    return any(part in DEFAULT_IGNORE_PARTS for part in rel.parts)


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify OpenDispatch release source hygiene.")
    parser.add_argument("--project-root", default=".", help="Project root to scan")
    args = parser.parse_args()
    root = Path(args.project_root).resolve()
    failures: list[str] = []
    for path in root.rglob("*"):
        if should_ignore(path, root):
            continue
        name = path.name
        if path.is_dir() and name in BANNED_DIRS:
            failures.append(f"banned directory: {path.relative_to(root)}")
        elif path.is_file() and (name in BANNED_FILES or path.suffix in BANNED_SUFFIXES):
            failures.append(f"banned file: {path.relative_to(root)}")
    if failures:
        print("Release hygiene failed:", file=sys.stderr)
        for failure in failures[:100]:
            print(f" - {failure}", file=sys.stderr)
        if len(failures) > 100:
            print(f" - ... and {len(failures) - 100} more", file=sys.stderr)
        return 1
    print("Release hygiene verified: no generated/build/cache artifacts in source package.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
