#!/usr/bin/env python3
"""Fail when Stage 0 adds new dispatch production artifacts beyond the frozen baseline."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_BASELINE = ROOT / "scripts" / "architecture" / "stage0-dispatch-feature-freeze-baseline.json"
TOKENS = (
    "dispatch", "flow", "routing", "readiness", "governance", "participation",
    "service-scope", "service_scope", "task-scope", "task_scope", "qualification",
    "assignment-profile", "assignment_profile", "capability", "skill",
)

MODULE_NAMES = (
    "ai-event-gateway-core",
    "ai-event-gateway-netty",
    "ai-event-gateway-admin-ui",
)


def nested_module_roots() -> list[str]:
    results: set[str] = set()
    for host_name in MODULE_NAMES:
        host = ROOT / host_name
        if not host.is_dir():
            continue
        for module_name in MODULE_NAMES:
            candidate = host / module_name
            canonical = ROOT / module_name
            if candidate.is_dir() and candidate.resolve() != canonical.resolve():
                results.add(candidate.relative_to(ROOT).as_posix())
    return sorted(results)


def production_artifacts() -> list[str]:
    roots = [
        ROOT / "ai-event-gateway-core",
        ROOT / "ai-event-gateway-netty",
        ROOT / "ai-event-gateway-admin-ui" / "app",
        ROOT / "ai-event-gateway-admin-ui" / "components",
        ROOT / "ai-event-gateway-admin-ui" / "lib",
    ]
    allowed_suffixes = {".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".sql", ".xml", ".yml", ".yaml"}
    result: list[str] = []
    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in allowed_suffixes:
                continue
            relative = path.relative_to(ROOT).as_posix()
            parts = set(path.parts)
            if parts.intersection({"target", "node_modules", ".next", "test", "tests", "__tests__"}) or "/src/test/" in f"/{relative}/":
                continue
            lower = relative.lower()
            if any(token in lower for token in TOKENS):
                result.append(relative)
    return sorted(set(result))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--write-baseline", action="store_true")
    args = parser.parse_args()
    nested = nested_module_roots()
    if nested:
        print("ERROR: nested OpenDispatch module copies were detected before feature-freeze evaluation:")
        for item in nested:
            print(f"  + {item}")
        print("This usually means the project archive was extracted inside an existing module directory.")
        print("Do not update the feature-freeze baseline. Run:")
        print("  python3 scripts/maintenance/repair_nested_project_layout.py")
        print("  python3 scripts/maintenance/repair_nested_project_layout.py --apply")
        return 3

    current = production_artifacts()
    if args.write_baseline:
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_text(json.dumps({
            "schemaVersion": 1,
            "purpose": "Stage 0 feature freeze. Removals are allowed; new dispatch production artifacts require an explicit architecture decision.",
            "artifacts": current,
        }, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {args.baseline} with {len(current)} frozen artifacts")
        return 0
    if not args.baseline.exists():
        print(f"Missing baseline: {args.baseline}")
        return 2
    baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
    frozen = set(baseline.get("artifacts", []))
    additions = sorted(set(current) - frozen)
    removals = sorted(frozen - set(current))
    print(f"Stage 0 dispatch feature freeze: baseline={len(frozen)} current={len(current)} additions={len(additions)} removals={len(removals)}")
    if removals:
        print("Removed production artifacts are allowed during convergence:")
        for item in removals:
            print(f"  - {item}")
    if additions:
        print("ERROR: new dispatch production artifacts were added while Stage 0 freeze is active:")
        for item in additions:
            print(f"  + {item}")
        print("Do not update the baseline merely to make CI green. Record and approve an architecture decision first.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
