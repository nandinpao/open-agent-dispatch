#!/usr/bin/env python3
"""Stage 2 characterization report for TenantContext and PostgreSQL optional-filter contracts.

This script is intentionally read-only. It does not prove live dispatch success; it produces
an auditable Stage 2 contract map that can be committed before running the Java 25/Docker
PostgreSQL container tests in the target environment.
"""
from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
OUTPUT_DIR = ROOT / ".ci-output" / "stage2-tenant-sql-contract"

UNSAFE_OPTIONAL_FILTER_PATTERNS = {
    "UNTYPED_NULL_OR": re.compile(r"\(:[A-Za-z0-9_]+\s+is\s+null\s+or", re.IGNORECASE),
    "UNTYPED_NULL_AND": re.compile(r"\(:[A-Za-z0-9_]+\s+is\s+null\s+and", re.IGNORECASE),
    "PARAM_IS_NULL_OR": re.compile(r":[A-Za-z0-9_]+\s+is\s+null\s+or", re.IGNORECASE),
}
TENANT_DEFAULT_PATTERNS = {
    "HARDCODED_DEFAULT_PARAM": re.compile(r"tenantId\s*=\s*[\"']default[\"']"),
    "DEFAULT_FALLBACK": re.compile(r"tenantId\s*[:=].{0,120}(?:\?\?|\|\|)\s*[\"']default[\"']", re.IGNORECASE),
    "QUERY_DEFAULT_TENANT": re.compile(r"tenantId[^\n]{0,120}[\"']default[\"']", re.IGNORECASE),
}

REQUIRED_CLIENT_TOKENS = {
    "authoritative tenant resolver": "requireCoreTenantContext",
    "no workspace fail-fast": "TENANT_CONTEXT_REQUIRED",
    "cross tenant fail-fast": "TENANT_CONTEXT_MISMATCH",
    "selected tenant injection": "tenantId: selectedTenantId",
}

REQUIRED_AUTH_PROVIDER_TOKENS = {
    "selected tenant publish/clear": "setCoreTenantContext",
}

REQUIRED_TEST_FILES = [
    "ai-event-gateway-admin-ui/tests/stage2-tenant-context.test.ts",
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage2PostgresOptionalFilterContainerTest.java",
]


@dataclass(frozen=True)
class Finding:
    category: str
    severity: str
    path: str
    line: int
    detail: str


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def iter_files(base: Path, patterns: Iterable[str]) -> Iterable[Path]:
    for pattern in patterns:
        yield from base.rglob(pattern)


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def scan_optional_filter_sql() -> list[Finding]:
    findings: list[Finding] = []
    java_root = ROOT / "ai-event-gateway-core"
    for path in java_root.rglob("*.java"):
        relative = rel(path)
        if "/src/test/" in f"/{relative}/":
            continue
        text = read(path)
        for line_number, line in enumerate(text.splitlines(), 1):
            for category, pattern in UNSAFE_OPTIONAL_FILTER_PATTERNS.items():
                if pattern.search(line):
                    findings.append(Finding(
                        category=category,
                        severity="BLOCKER",
                        path=relative,
                        line=line_number,
                        detail=line.strip(),
                    ))
    return findings


def scan_default_tenant_fallbacks() -> list[Finding]:
    findings: list[Finding] = []
    ui_root = ROOT / "ai-event-gateway-admin-ui"
    for path in iter_files(ui_root, ["*.ts", "*.tsx"]):
        relative = rel(path)
        if "/.next/" in relative or "/node_modules/" in relative:
            continue
        text = read(path)
        for line_number, line in enumerate(text.splitlines(), 1):
            for category, pattern in TENANT_DEFAULT_PATTERNS.items():
                if pattern.search(line):
                    # allow explicit test names/documentation only outside production source
                    if "/tests/" in f"/{relative}/" or "/scripts/" in f"/{relative}/":
                        continue
                    findings.append(Finding(
                        category=category,
                        severity="BLOCKER",
                        path=relative,
                        line=line_number,
                        detail=line.strip(),
                    ))
    return findings


def check_required_tokens() -> list[Finding]:
    findings: list[Finding] = []
    client_path = ROOT / "ai-event-gateway-admin-ui/lib/api/client.ts"
    client = read(client_path) if client_path.exists() else ""
    for label, token in REQUIRED_CLIENT_TOKENS.items():
        if token not in client:
            findings.append(Finding("MISSING_TENANT_CONTRACT_TOKEN", "BLOCKER", rel(client_path), 0, f"missing {label}: {token}"))
    auth_path = ROOT / "ai-event-gateway-admin-ui/components/auth/AuthProvider.tsx"
    auth = read(auth_path) if auth_path.exists() else ""
    for label, token in REQUIRED_AUTH_PROVIDER_TOKENS.items():
        if token not in auth:
            findings.append(Finding("MISSING_AUTH_PROVIDER_TOKEN", "BLOCKER", rel(auth_path), 0, f"missing {label}: {token}"))
    package_path = ROOT / "ai-event-gateway-admin-ui/package.json"
    package = read(package_path) if package_path.exists() else ""
    if "test:stage2-tenant-context" not in package:
        findings.append(Finding("MISSING_STAGE2_NPM_SCRIPT", "BLOCKER", rel(package_path), 0, "missing test:stage2-tenant-context script"))
    for file_name in REQUIRED_TEST_FILES:
        path = ROOT / file_name
        if not path.is_file():
            findings.append(Finding("MISSING_STAGE2_TEST", "BLOCKER", file_name, 0, "required Stage 2 TDD file is absent"))
    return findings


def build_report() -> dict:
    findings = scan_optional_filter_sql() + scan_default_tenant_fallbacks() + check_required_tokens()
    by_category: dict[str, int] = {}
    by_severity: dict[str, int] = {}
    for finding in findings:
        by_category[finding.category] = by_category.get(finding.category, 0) + 1
        by_severity[finding.severity] = by_severity.get(finding.severity, 0) + 1
    return {
        "stage": "Stage 2",
        "name": "Tenant Contract and PostgreSQL Optional Filter Characterization",
        "status": "PASS" if not findings else "FAIL",
        "completion_semantics": "static characterization only; Java 25/Maven/Docker live gates are still required",
        "checks": {
            "unsafe_optional_filter_sql": len(scan_optional_filter_sql()),
            "hardcoded_default_tenant_fallbacks": len(scan_default_tenant_fallbacks()),
            "missing_contract_tokens_or_tests": len(check_required_tokens()),
        },
        "summary": {
            "total_findings": len(findings),
            "by_category": by_category,
            "by_severity": by_severity,
        },
        "findings": [asdict(finding) for finding in findings],
        "required_live_gates": [
            "make test-stage2-admin-tenant-contract",
            "make test-stage2-postgres-optional-filters",
            "manual live regression: capability catalog consistency, applied dispatch rule save, tenant switch reload",
        ],
    }


def write_markdown(report: dict, path: Path) -> None:
    lines = [
        "# Stage 2 Tenant / SQL Contract Report",
        "",
        f"Status: **{report['status']}**",
        "",
        "## Scope",
        "",
        "This report is generated from source without changing production data. It checks for PostgreSQL-unsafe optional filters, hard-coded default tenant fallbacks, and the required Admin UI TenantContext contract.",
        "",
        "## Summary",
        "",
        f"- Total findings: {report['summary']['total_findings']}",
        f"- Unsafe optional-filter SQL findings: {report['checks']['unsafe_optional_filter_sql']}",
        f"- Hard-coded default tenant fallbacks: {report['checks']['hardcoded_default_tenant_fallbacks']}",
        f"- Missing contract tokens/tests: {report['checks']['missing_contract_tokens_or_tests']}",
        "",
        "## Required live gates",
        "",
    ]
    lines.extend(f"- `{gate}`" for gate in report["required_live_gates"])
    lines.extend(["", "## Findings", ""])
    if not report["findings"]:
        lines.append("No static Stage 2 contract findings were detected.")
    else:
        for item in report["findings"]:
            line = item["line"]
            location = item["path"] if not line else f"{item['path']}:{line}"
            lines.append(f"- **{item['severity']} {item['category']}** `{location}` — {item['detail']}")
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="exit non-zero when static Stage 2 contract findings exist")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    report = build_report()
    (OUTPUT_DIR / "contract-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    write_markdown(report, OUTPUT_DIR / "contract-report.md")
    print(f"Stage 2 tenant/sql contract report written to {OUTPUT_DIR.relative_to(ROOT)}")
    print(f"Status: {report['status']} findings={report['summary']['total_findings']}")
    return 1 if args.strict and report["findings"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
