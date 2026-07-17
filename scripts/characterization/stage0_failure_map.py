#!/usr/bin/env python3
"""Build the Stage 0 Failure Map from static and live/dry-run characterization evidence."""
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DIR = ROOT / ".ci-output" / "stage0-characterization"
DEFAULT_STATIC = DEFAULT_DIR / "static-findings.json"
DEFAULT_LATEST = DEFAULT_DIR / "latest.json"
DEFAULT_OUTPUT = DEFAULT_DIR / "failure-map.json"


def load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return {"loadError": f"{path}: {exc}"}


def scenario_rows(latest: dict[str, Any]) -> list[dict[str, Any]]:
    rows = []
    for item in latest.get("scenarios", []) or []:
        blockers = item.get("blockers") or []
        rows.append({
            "name": item.get("name"),
            "status": item.get("status"),
            "passed": bool(item.get("passed")),
            "blockers": blockers,
            "error": item.get("error"),
            "desired": item.get("desired"),
        })
    return rows


def top_static_findings(static: dict[str, Any], limit: int = 25) -> dict[str, list[dict[str, Any]]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in static.get("findings", []) or []:
        category = str(item.get("category") or "UNKNOWN")
        if len(grouped[category]) < limit:
            grouped[category].append({
                "path": item.get("path"),
                "line": item.get("line"),
                "evidence": item.get("evidence"),
                "desiredContract": item.get("desired_contract"),
            })
    return dict(grouped)


def markdown(payload: dict[str, Any]) -> str:
    lines = [
        "# Stage 0 Failure Map",
        "",
        "This file is evidence, not a release approval. Stage 0 is complete when the project can reproduce and categorize the current dispatch failures without adding another dispatch model.",
        "",
        f"Generated: {payload['generatedAt']}",
        f"Mode: `{payload['latest'].get('mode', 'NO_LIVE_REPORT')}`",
        f"Run ID: `{payload['latest'].get('runId', '-')}`",
        "",
        "## Static conflict summary",
        "",
    ]
    by_category = payload["static"].get("summary", {}).get("byCategory", {})
    if by_category:
        for category, count in sorted(by_category.items()):
            lines.append(f"- `{category}`: {count}")
    else:
        lines.append("- No static report was available.")
    lines += ["", "## Characterization scenarios", ""]
    rows = payload.get("scenarios", [])
    if not rows:
        lines.append("- No live/dry-run scenario report was available.")
    for row in rows:
        blockers = ", ".join(row.get("blockers") or []) or "-"
        lines.append(f"- **{row.get('name')}** — `{row.get('status')}`; blockers: `{blockers}`")
        if row.get("error"):
            lines.append(f"  - error: `{str(row['error'])[:500]}`")
    lines += ["", "## First Stage 1 target", ""]
    failed = [row for row in rows if not row.get("passed")]
    if failed:
        first = failed[0]
        lines.append(f"Start Stage 1 with `{first.get('name')}` and remove only the first runtime blocker shown in its evidence.")
    else:
        lines.append("Dry-run passed all desired-behavior contracts. Run live characterization on the Java 25 / PostgreSQL / Redis / Netty stack before changing production code.")
    lines += ["", "## Guardrails", "", "- Do not add Profile, Scope, Grant, Participation, Readiness or Simulator concepts.", "- Do not update the feature-freeze baseline to hide new production artifacts.", "- Do not declare release readiness until strict live Golden Path completes Event → Task → Agent → Result.", ""]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--static", type=Path, default=DEFAULT_STATIC)
    parser.add_argument("--latest", type=Path, default=DEFAULT_LATEST)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    static = load_json(args.static)
    latest = load_json(args.latest)
    blockers = Counter()
    for row in scenario_rows(latest):
        blockers.update(row.get("blockers") or [])

    payload = {
        "schemaVersion": 1,
        "stage": "STAGE_0_FAILURE_MAP",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "static": {
            "path": str(args.static.relative_to(ROOT)) if args.static.exists() else str(args.static),
            "summary": static.get("summary", {}),
            "topFindings": top_static_findings(static),
        },
        "latest": {
            "path": str(args.latest.relative_to(ROOT)) if args.latest.exists() else str(args.latest),
            "mode": latest.get("mode"),
            "runId": latest.get("runId"),
            "summary": latest.get("summary", {}),
            "fatalError": latest.get("fatalError"),
        },
        "scenarios": scenario_rows(latest),
        "blockerSummary": dict(sorted(blockers.items())),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    args.output.with_suffix(".md").write_text(markdown(payload), encoding="utf-8")
    print(f"Stage 0 Failure Map written: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
