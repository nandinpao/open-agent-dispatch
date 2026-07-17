#!/usr/bin/env python3
"""Freeze and burn down source-specific dispatch behavior.

The committed baseline describes findings already present in the repository. The
verification rule is monotonic: a finding count may fall, but it may not grow,
move to a new production file, or introduce a new semantic key.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
BASELINE = Path(__file__).with_name("zero-special-case-baseline.json")

CODE_EXTENSIONS = {".java", ".kt", ".ts", ".tsx", ".js", ".mjs", ".yml", ".yaml", ".properties"}
UI_ROOTS = (
    ROOT / "ai-event-gateway-admin-ui" / "app",
    ROOT / "ai-event-gateway-admin-ui" / "components",
    ROOT / "ai-event-gateway-admin-ui" / "lib",
)
SOURCE_ROOTS = (
    ROOT / "ai-event-gateway-core",
    ROOT / "ai-event-gateway-netty",
    *UI_ROOTS,
)

BUSINESS_TABLES = {
    "dispatch_flows",
    "dispatch_policies",
    "flow_required_skills",
    "flow_agent_assignments",
    "agent_capability_catalog",
    "agent_capability_assignments",
    "agent_assignment_profiles",
    "assignment_profile_capability_bindings",
    "agent_skill_registry",
    "task_definitions",
    "source_systems",
    "source_system_catalog",
    "source_system_dispatch_defaults",
    "agent_source_assignments",
    "task_requirement_evidence",
    "action_catalog",
    "agent_action_grants",
    "proposed_actions",
    "action_approval_requests",
    "action_approval_decisions",
    "effectful_action_task_links",
    "effectful_action_evidence",
    "agents",
    "tasks",
}

UPPER_LITERAL = re.compile(r"['\"]([A-Z][A-Z0-9_.-]{1,})['\"]")
SOURCE_CONTEXT = re.compile(
    r"source[_A-Za-z]*system|sourceSystem|originSource|targetSystem|\bdomain\b|\bprovider\b|objectType|eventType|errorCode",
    re.IGNORECASE,
)
DECISION_MARKER = re.compile(
    r"\bif\b|\bcase\b|\bswitch\b|\.equals(?:IgnoreCase)?\s*\(|\.contains\s*\(|\.startsWith\s*\(|\.endsWith\s*\(|firstNonBlank\s*\(|\?",
)
CAPABILITY_CONTEXT = re.compile(r"capabilit|requestedSkill|requiredSkill|\bskill\b", re.IGNORECASE)
NAME_INFERENCE = re.compile(r"\.(startsWith|endsWith|contains)\s*\(\s*['\"]([^'\"]+)['\"]")
FIXED_AGENT = re.compile(r"agent-cluster-node-[A-Za-z0-9_.-]+", re.IGNORECASE)
FIXED_TENANT = re.compile(r"tenant-a", re.IGNORECASE)
AUTO_SEED = re.compile(r"\bseedDefaults\s*\(")
SOURCE_ASSIGNMENT = re.compile(
    r"(?:sourceSystem|source_system|source|domain|provider)\s*[:=]\s*['\"]([A-Z][A-Z0-9_.-]{1,})['\"]",
    re.IGNORECASE,
)
DOMAIN_PREFIX = re.compile(r"\b(ERP|MES|CMS|HR)_[A-Z0-9_]+\b")

SAFE_SOURCE_LITERAL_DECISIONS = {
    "UNKNOWN",
    "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS",
    "LOW", "MIDDLE", "MEDIUM", "HIGH", "CRITICAL",
    "PASS", "PENDING", "FAIL",
    "OPEN_DISPATCH_FLOW", "REVIEW_DISPATCH_FLOW", "OPEN_AGENT", "RETRY_DISPATCH",
    "RETRY_LATEST_DISPATCH_REQUEST",
}
SQL_DML = re.compile(
    r"\b(insert\s+into|update|delete\s+from)\s+(?:public\.)?([a-zA-Z_][a-zA-Z0-9_]*)",
    re.IGNORECASE,
)

SKIP_PARTS = {
    "src/test",
    "target",
    "node_modules",
    ".next",
    "coverage",
    "dist",
    "build",
}


@dataclass(frozen=True)
class Finding:
    rule: str
    path: str
    value: str
    line: int
    evidence: str

    @property
    def key(self) -> str:
        return f"{self.rule}|{self.path}|{self.value}"


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def is_production_file(path: Path) -> bool:
    rel = relative(path)
    if any(part in rel for part in SKIP_PARTS):
        return False
    if "/src/main/" in f"/{rel}":
        return path.suffix.lower() in CODE_EXTENSIONS or path.suffix.lower() == ".sql"
    return any(path.is_relative_to(root) for root in UI_ROOTS) and path.suffix.lower() in CODE_EXTENSIONS


def iter_production_files() -> Iterable[Path]:
    seen: set[Path] = set()
    for root in SOURCE_ROOTS:
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if path.is_file() and path not in seen and is_production_file(path):
                seen.add(path)
                yield path


def add(findings: list[Finding], rule: str, path: Path, value: str, line: int, evidence: str) -> None:
    findings.append(Finding(rule, relative(path), value.strip(), line, evidence.strip()[:240]))


def scan_code(path: Path, text: str) -> list[Finding]:
    findings: list[Finding] = []
    lines = text.splitlines()
    domain_sensitive_file = bool(re.search(r"capabilit|skill|recipe|routing|eligibil", path.name, re.IGNORECASE))

    for index, line in enumerate(lines, start=1):
        stripped = line.strip()
        if not stripped:
            continue

        if AUTO_SEED.search(line):
            add(findings, "AUTO_RUNTIME_SEED", path, "seedDefaults", index, stripped)

        for match in FIXED_TENANT.finditer(line):
            add(findings, "FIXED_TENANT_LITERAL", path, match.group(0).lower(), index, stripped)

        for match in FIXED_AGENT.finditer(line):
            add(findings, "FIXED_AGENT_LITERAL", path, match.group(0).lower(), index, stripped)

        for match in SOURCE_ASSIGNMENT.finditer(line):
            add(findings, "FIXED_SOURCE_ASSIGNMENT", path, match.group(1).upper(), index, stripped)

        # JSX display fallbacks and optional chaining are presentation, not routing decisions.
        jsx_display_only = path.suffix.lower() == ".tsx" and stripped.startswith("<")
        if not jsx_display_only and SOURCE_CONTEXT.search(line) and DECISION_MARKER.search(line):
            for literal in UPPER_LITERAL.findall(line):
                normalized_literal = literal.upper()
                if normalized_literal in SAFE_SOURCE_LITERAL_DECISIONS:
                    continue
                add(findings, "SOURCE_LITERAL_DECISION", path, normalized_literal, index, stripped)

        context_start = max(0, index - 3)
        context_end = min(len(lines), index + 2)
        capability_window = "\n".join(lines[context_start:context_end])
        blocker_normalization = (
            path.name in {"TaskAssignmentService.java"}
            and any(code in line for code in (
                "NO_ACTIVE_FLOW_RULE", "FLOW_RULE_REQUIRED_BLOCKED",
                "REQUIRED_CAPABILITY_MISSING", "MISSING_REQUIRED_CAPABILITY", "CAPABILITY_MISSING",
                "NO_FLOW_SELECTED_AGENT", "FLOW_SELECTED_AGENT_REQUIRED"
            ))
        )
        # Mapping stable runtime blocker codes into a public API reason is not
        # capability-name inference.  Keep the zero-special-case guard focused
        # on business/source naming decisions rather than response normalization.
        response_reason_normalization = (
            path.name == "DecisionEngine.java"
            and "evidence.contains" in line
            and any(code in line for code in (
                "NO_ACTIVE_FLOW_RULE", "FLOW_RULE_REQUIRED_BLOCKED",
                "REQUIRED_CAPABILITY_MISSING", "CAPABILITY_MISSING",
                "AGENT_OFFLINE", "RUNTIME_NOT_CONNECTED", "NO_ACTIVE_RUNTIME_SESSION",
                "NO_CAPACITY", "CAPACITY"
            ))
        )
        if CAPABILITY_CONTEXT.search(capability_window) and not blocker_normalization and not response_reason_normalization:
            for match in NAME_INFERENCE.finditer(line):
                inferred = match.group(2)
                if "_" in inferred or inferred.upper() == inferred:
                    add(
                        findings,
                        "CAPABILITY_NAME_INFERENCE",
                        path,
                        f"{match.group(1)}:{inferred}",
                        index,
                        stripped,
                    )

        for match in DOMAIN_PREFIX.finditer(line):
            if domain_sensitive_file or CAPABILITY_CONTEXT.search(line) or DECISION_MARKER.search(line) or "enum" in text[:1000].lower():
                add(findings, "DOMAIN_SPECIFIC_CODE_TOKEN", path, match.group(0).upper(), index, stripped)

    return findings


def statement_line(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def scan_sql(path: Path, text: str) -> list[Finding]:
    findings = scan_code(path, text)
    for match in SQL_DML.finditer(text):
        operation = re.sub(r"\s+", "_", match.group(1).strip().upper())
        table = match.group(2).lower()
        if table in BUSINESS_TABLES:
            start = max(0, match.start() - 80)
            end = min(len(text), match.end() + 160)
            evidence = re.sub(r"\s+", " ", text[start:end]).strip()
            add(
                findings,
                "BUSINESS_DATA_MIGRATION",
                path,
                f"{operation}:{table}",
                statement_line(text, match.start()),
                evidence,
            )
    return findings


def scan() -> list[Finding]:
    findings: list[Finding] = []
    for path in sorted(iter_production_files()):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        findings.extend(scan_sql(path, text) if path.suffix.lower() == ".sql" else scan_code(path, text))
    return findings


def counts(findings: Iterable[Finding]) -> Counter[str]:
    return Counter(finding.key for finding in findings)


def load_baseline() -> dict:
    if not BASELINE.is_file():
        raise FileNotFoundError(f"Missing baseline: {relative(BASELINE)}")
    return json.loads(BASELINE.read_text(encoding="utf-8"))


def write_baseline(findings: list[Finding]) -> None:
    grouped: dict[str, list[Finding]] = defaultdict(list)
    for finding in findings:
        grouped[finding.key].append(finding)

    payload = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "policy": "Existing finding counts may decrease but may not increase; new semantic keys fail.",
        "allowedCounts": dict(sorted((key, len(items)) for key, items in grouped.items())),
        "examples": {
            key: [
                {"line": item.line, "evidence": item.evidence}
                for item in sorted(items, key=lambda value: value.line)[:3]
            ]
            for key, items in sorted(grouped.items())
        },
    }
    BASELINE.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {relative(BASELINE)} with {len(payload['allowedCounts'])} keys and {len(findings)} findings.")


def verify(findings: list[Finding], report: bool) -> int:
    baseline = load_baseline()
    allowed = Counter({key: int(value) for key, value in baseline.get("allowedCounts", {}).items()})
    current = counts(findings)
    by_key: dict[str, list[Finding]] = defaultdict(list)
    for finding in findings:
        by_key[finding.key].append(finding)

    violations: list[tuple[str, int, int]] = []
    for key, count in sorted(current.items()):
        maximum = allowed.get(key, 0)
        if count > maximum:
            violations.append((key, count, maximum))

    removed = sum(maximum - current.get(key, 0) for key, maximum in allowed.items() if current.get(key, 0) < maximum)

    if report or violations:
        print(f"Current findings: {sum(current.values())} across {len(current)} semantic keys")
        print(f"Baseline allowance: {sum(allowed.values())} across {len(allowed)} semantic keys")
        print(f"Burned-down findings: {removed}")

    if report:
        for key in sorted(current):
            print(f"  {current[key]:3d}/{allowed.get(key, 0):3d}  {key}")

    if violations:
        print("[FAIL] New or increased special-case dispatch findings detected:", file=sys.stderr)
        for key, count, maximum in violations:
            print(f"  {count}/{maximum} {key}", file=sys.stderr)
            for finding in by_key[key][:5]:
                print(f"    {finding.path}:{finding.line}: {finding.evidence}", file=sys.stderr)
        print(
            "Remove the special case. Do not update the baseline unless an architecture review explicitly changes ADR-001.",
            file=sys.stderr,
        )
        return 1

    print(
        f"[PASS] Zero-special-case guard: {sum(current.values())} current findings; "
        f"baseline {sum(allowed.values())}; burned down {removed}."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--update-baseline", action="store_true", help="Rewrite the committed baseline from the current tree.")
    parser.add_argument("--report", action="store_true", help="Print all finding keys and counts.")
    args = parser.parse_args()

    findings = scan()
    if args.update_baseline:
        write_baseline(findings)
        return 0
    return verify(findings, args.report)


if __name__ == "__main__":
    raise SystemExit(main())
