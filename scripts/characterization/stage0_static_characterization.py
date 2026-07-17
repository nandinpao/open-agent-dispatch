#!/usr/bin/env python3
"""Inventory known Stage 0 dispatch conflicts without modifying production code."""
from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = ROOT / ".ci-output" / "stage0-characterization" / "static-findings.json"

@dataclass(frozen=True)
class Finding:
    category: str
    path: str
    line: int
    evidence: str
    desired_contract: str


def iter_text_files(roots: list[Path], suffixes: set[str]):
    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if path.is_file() and path.suffix.lower() in suffixes:
                yield path


def line_findings(path: Path, pattern: re.Pattern[str], category: str, desired: str):
    findings: list[Finding] = []
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return findings
    for number, line in enumerate(lines, 1):
        if pattern.search(line):
            findings.append(Finding(category, str(path.relative_to(ROOT)), number, line.strip()[:500], desired))
    return findings


def collect() -> list[Finding]:
    findings: list[Finding] = []
    java_sql_roots = [ROOT / "ai-event-gateway-core"]
    optional_null = re.compile(r"\(\s*:\w+\s+is\s+null\s+or\s+", re.IGNORECASE)
    for path in iter_text_files(java_sql_roots, {".java", ".sql", ".xml"}):
        if "/target/" in path.as_posix() or "/src/test/" in path.as_posix():
            continue
        findings.extend(line_findings(
            path, optional_null, "UNTYPED_NULL_OPTIONAL_FILTER",
            "Build optional PostgreSQL predicates dynamically; do not bind an untyped null solely for ':value is null'.",
        ))

    ui_roots = [ROOT / "ai-event-gateway-admin-ui" / "app", ROOT / "ai-event-gateway-admin-ui" / "components", ROOT / "ai-event-gateway-admin-ui" / "lib"]
    ui_terms = re.compile(r"Legacy (Task Types|Service Scope|Rule Diagnostics|Capability|Skill)|Dispatch Governance|Test Dispatch Readiness|Flow Participation", re.IGNORECASE)
    for path in iter_text_files(ui_roots, {".ts", ".tsx", ".js", ".jsx"}):
        if any(part in {"node_modules", ".next", "tests", "__tests__"} for part in path.parts):
            continue
        findings.extend(line_findings(
            path, ui_terms, "PARALLEL_OR_LEGACY_OPERATOR_WORKFLOW",
            "The standard operator workflow exposes Source Systems, Agents, Dispatch Flows and Tasks only; support-only internals are not normal navigation.",
        ))

    runtime_roots = [
        ROOT / "ai-event-gateway-core" / "task-orchestration" / "src" / "main",
        ROOT / "ai-event-gateway-core" / "execution-control" / "src" / "main",
        ROOT / "ai-event-gateway-core" / "agent-control" / "src" / "main",
    ]
    authority_terms = re.compile(r"ServiceScope|AssignmentProfile|SourceSystemDispatchDefault|AgentSourceAssignment|OperationProfile|FlowParticipation|Qualification")
    for path in iter_text_files(runtime_roots, {".java"}):
        findings.extend(line_findings(
            path, authority_terms, "MULTIPLE_DISPATCH_AUTHORITY_DEPENDENCY",
            "Stage 1 must prove one Event -> Task -> Flow Rule -> Flow Agent -> Capability -> Runtime path and remove duplicate standard-path gates.",
        ))

    source_specific = re.compile(r"(?:equals\s*\(\s*\"(?:CMS|MES|ERP)\"|case\s+\"(?:CMS|MES|ERP)\"|startsWith\s*\(\s*\"(?:CMS|MES|ERP))")
    for path in iter_text_files(runtime_roots, {".java"}):
        findings.extend(line_findings(
            path, source_specific, "SOURCE_SPECIFIC_RUNTIME_BRANCH",
            "Source System identifiers are tenant-owned opaque values and must not select business-specific runtime code paths.",
        ))

    return sorted(findings, key=lambda item: (item.category, item.path, item.line))


def markdown(findings: list[Finding]) -> str:
    by_category: dict[str, list[Finding]] = {}
    for item in findings:
        by_category.setdefault(item.category, []).append(item)
    lines = [
        "# Stage 0 Static Characterization",
        "",
        "This report records existing conflicts. It is not proof that the Golden Path works.",
        "",
        f"Generated: {datetime.now(timezone.utc).isoformat()}",
        "",
        f"Total findings: **{len(findings)}**",
        "",
    ]
    for category, items in by_category.items():
        lines += [f"## {category} ({len(items)})", ""]
        for item in items[:100]:
            lines.append(f"- `{item.path}:{item.line}` — `{item.evidence}`")
        if len(items) > 100:
            lines.append(f"- … {len(items) - 100} additional findings are available in JSON.")
        lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Fail while any known conflict remains; intended for later gates.")
    args = parser.parse_args()
    findings = collect()
    payload = {
        "schemaVersion": 1,
        "stage": "STAGE_0_STATIC_CHARACTERIZATION",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "root": str(ROOT),
        "summary": {
            "total": len(findings),
            "byCategory": {category: sum(1 for item in findings if item.category == category) for category in sorted({item.category for item in findings})},
        },
        "findings": [asdict(item) for item in findings],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    args.output.with_suffix(".md").write_text(markdown(findings), encoding="utf-8")
    print(json.dumps(payload["summary"], indent=2, ensure_ascii=False))
    print(f"Wrote {args.output}")
    return 1 if args.strict and findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
