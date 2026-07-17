#!/usr/bin/env python3
"""Stage 8 release gate contract report.

This dry-run friendly report does not claim that the product is release-ready.
It validates that the repository exposes a single explicit Stage 8 gate that
requires the release-grade evidence OpenDispatch needs before shipping:
Fresh DB, no-Capability, explicit-Capability, upgrade DB, multi-tenant,
restart recovery, browser E2E, no Legacy runtime dependency, and no
source-specific hardcode.
"""
from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".ci-output" / "stage8-release-gate"

MAKEFILE = ROOT / "Makefile"
PACKAGE = ROOT / "ai-event-gateway-admin-ui/package.json"
RELEASE_GATE = ROOT / "scripts/release/stage8-release-gate.sh"
STEP_RECORDER = ROOT / "scripts/release/stage8_record_step.py"
RELEASE_REPORT = ROOT / "scripts/release/stage8_release_report.py"
LOCAL_RUNTIME_PREFLIGHT = ROOT / "scripts/release/stage8-local-runtime-preflight.sh"
MANAGED_LOCAL_STACK = ROOT / "scripts/release/stage8-managed-local-stack.sh"
VERIFY = ROOT / "scripts/verify/verify-stage8-release-gate.py"
RUNNER = ROOT / "scripts/characterization/run-stage8.sh"
ZERO_GUARD = ROOT / "scripts/architecture/zero_special_case_guard.py"
STAGE1_ACCEPTANCE = ROOT / "scripts/acceptance/stage0-dispatch-characterization.mjs"
STAGE1_DRILLDOWN = ROOT / "scripts/characterization/stage1_golden_path_drilldown_report.py"
STAGE7_REPORT = ROOT / "scripts/characterization/stage7_legacy_isolation_retirement_contract.py"
LEGACY_INVENTORY = ROOT / "scripts/migration/stage7_legacy_dispatch_inventory.py"
APP_SHELL = ROOT / "ai-event-gateway-admin-ui/components/layout/AppShell.tsx"
SIDEBAR = ROOT / "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx"
COMPOSE_LOCAL = ROOT / "deploy/docker-compose.local.yml"
COMPOSE_CI = ROOT / "deploy/docker-compose.ci.yml"
LOCAL_ENV = ROOT / "deploy/env/.env.local.example"
DISPATCH_AUTHORITY_UNIFICATION_VERIFY = ROOT / "scripts/verify/verify-stage8-dispatch-authority-unification.py"
DISPATCH_AUTHORITY_UNIFICATION_DOC = ROOT / "docs/STAGE8_DISPATCH_AUTHORITY_UNIFICATION/README.md"
P5A_VERIFY = ROOT / "scripts/verify/verify-p5a-production-otel-hardening.py"
P5_FAIL_CLOSED_VERIFY = ROOT / "scripts/verify/verify-p5-runtime-fail-closed-java.sh"
P5_RUNTIME_VERIFY = ROOT / "scripts/verify/verify-p5-runtime-hardcode-removal.py"

DOCS = [
    ROOT / "docs/STAGE8_RELEASE_GATE/README.md",
    ROOT / "docs/STAGE8_RELEASE_GATE/test-matrix.md",
    ROOT / "docs/STAGE8_RELEASE_GATE/validation-report.md",
    ROOT / "docs/STAGE8_RELEASE_GATE/next-stage.md",
    ROOT / "docs/STAGE8_RELEASE_GATE/changed-files.md",
]

REQUIRED_RELEASE_GATES = [
    "Fresh DB Golden Path",
    "No-Capability Golden Path",
    "Explicit-Capability Golden Path",
    "Upgrade DB Golden Path",
    "Multi-Tenant Isolation",
    "Restart Recovery",
    "Browser E2E",
    "No Legacy Runtime Dependency",
    "No Source-Specific Hardcode",
]

STRICT_COMMAND_TOKENS = [
    "make check-toolchain",
    "scripts/release/stage8-managed-local-stack.sh up",
    "scripts/release/stage8-local-runtime-preflight.sh",
    "scripts/release/stage8-managed-local-stack.sh down",
    "make characterize-stage1-strict",
    "make characterize-stage2-strict",
    "make characterize-stage3-strict",
    "make characterize-stage5-strict",
    "make characterize-stage6-strict",
    "make characterize-stage7-strict",
    "make test-stage2-postgres-optional-filters",
    "make test-stage3-dispatch-flow-aggregate",
    "make test-stage5-core",
    "make test-stage6-core",
    "make test-stage7-core",
    "make ci-release",
    "make report-stage7-legacy-inventory",
    "python3 scripts/architecture/zero_special_case_guard.py",
]

DRY_RUN_TOKENS = [
    "make verify-stage0-dispatch-feature-freeze",
    "make verify-stage1-backend-golden-path",
    "make characterize-stage2-dry-run",
    "make characterize-stage3-dry-run",
    "make verify-stage4-beginner-ui-navigation",
    "make verify-stage5-real-event-task-diagnosis",
    "make characterize-stage6-dry-run",
    "make characterize-stage7-dry-run",
    "python3 scripts/characterization/stage8_release_gate_contract.py",
]

@dataclass
class Finding:
    severity: str
    category: str
    file: str
    detail: str


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def read(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8")


def require_tokens(findings: list[Finding], path: Path, tokens: Iterable[str], category: str) -> None:
    text = read(path)
    if not text:
        findings.append(Finding("ERROR", category, rel(path), "Required file is missing or empty"))
        return
    for token in tokens:
        if token not in text:
            findings.append(Finding("ERROR", category, rel(path), f"Missing contract token: {token}"))


def reject_tokens(findings: list[Finding], path: Path, tokens: Iterable[str], category: str) -> None:
    text = read(path)
    if not text:
        findings.append(Finding("ERROR", category, rel(path), "Required file is missing or empty"))
        return
    for token in tokens:
        if token in text:
            findings.append(Finding("ERROR", category, rel(path), f"Forbidden token remains: {token}"))


def check_no_stage8_production_migration(findings: list[Finding]) -> None:
    migration_root = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
    if not migration_root.exists():
        findings.append(Finding("ERROR", "NO_STAGE8_MIGRATION", rel(migration_root), "Migration directory is missing"))
        return
    stage8_files = [p.name for p in migration_root.glob("*stage8*")]
    if stage8_files:
        findings.append(Finding("ERROR", "NO_STAGE8_MIGRATION", rel(migration_root), "Stage 8 release gate must not add production migration: " + ", ".join(stage8_files)))


def check_release_gate_script_mode(findings: list[Finding]) -> None:
    text = read(RELEASE_GATE)
    if not text:
        findings.append(Finding("ERROR", "RELEASE_GATE_SCRIPT", rel(RELEASE_GATE), "Missing Stage 8 release gate script"))
        return
    require_tokens(findings, RELEASE_GATE, REQUIRED_RELEASE_GATES, "RELEASE_GATE_MATRIX")
    require_tokens(findings, RELEASE_GATE, STRICT_COMMAND_TOKENS, "STRICT_RELEASE_COMMANDS")
    require_tokens(findings, RELEASE_GATE, DRY_RUN_TOKENS, "DRY_RUN_RELEASE_COMMANDS")
    require_tokens(findings, RELEASE_GATE, [
        "STAGE8_MODE",
        "--dry-run",
        "--strict",
        "RELEASE_READY=false",
        "stage8_record_step.py",
        "stage8_release_report.py",
        "steps.jsonl",
        "failures.json",
        "failure-map.md",
        "logs",
        "release-ready.json",
        "release-ready.md",
        "STAGE8_MANAGED_LOCAL_STACK",
        "Local Runtime Readiness",
    ], "RELEASE_GATE_REPORTING")
    require_tokens(findings, LOCAL_RUNTIME_PREFLIGHT, [
        "/api/auth/csrf",
        "Stage 8 local runtime preflight failed",
        "STAGE8_CORE_READY_ATTEMPTS",
        "STAGE8_MANAGED_LOCAL_STACK=true make stage8-release-gate",
    ], "LOCAL_RUNTIME_PREFLIGHT")
    require_tokens(findings, MANAGED_LOCAL_STACK, [
        "opendispatch-stage8",
        "stage8-managed-local",
        "WITH_AGENT=true ./scripts/local-compose-up.sh",
        "compose_down -v",
    ], "MANAGED_LOCAL_STACK")
    require_tokens(findings, COMPOSE_LOCAL, [
        "LEGACY_DIRECT",
    ], "STAGE8_LOCAL_DELIVERY_MODE")
    require_tokens(findings, COMPOSE_CI, [
        "LEGACY_DIRECT",
    ], "STAGE8_CI_DELIVERY_MODE")
    require_tokens(findings, LOCAL_ENV, [
    ], "STAGE8_LOCAL_ENV_DELIVERY_MODE")
    require_tokens(findings, DISPATCH_AUTHORITY_UNIFICATION_VERIFY, [
        "Stage 8 dispatch authority unification contract verified.",
        "STANDARD_FLOW_NO_CAPABILITY_REQUIREMENT_RESOLVED",
        "STANDARD_FLOW_EXPLICIT_CAPABILITY_REQUIREMENT_RESOLVED",
        "coalesce(p.candidate_pool_mode, 'EXPLICIT_FLOW_AGENTS')",
    ], "STAGE8_DISPATCH_AUTHORITY_UNIFICATION")
    require_tokens(findings, DISPATCH_AUTHORITY_UNIFICATION_DOC, [
        "Dispatch Flow",
        "Flow Rule",
        "Flow Agent Assignments",
        "Task → Assignment → DispatchRequest → Netty",
    ], "STAGE8_DISPATCH_AUTHORITY_UNIFICATION_DOC")
    require_tokens(findings, P5A_VERIFY, [
        "_minimal_yaml_safe_load",
        "yaml is not None",
    ], "P5A_RELEASE_VERIFY_DEPENDENCY")
    reject_tokens(findings, P5A_VERIFY, [
        "PyYAML is required",
    ], "P5A_RELEASE_VERIFY_DEPENDENCY")

    require_tokens(findings, P5_FAIL_CLOSED_VERIFY, [
        "while IFS= read -r stub",
        "properties.isFlowRuleLegacyFallbackEnabled()",
    ], "P5_FAIL_CLOSED_VERIFY_PORTABILITY")
    reject_tokens(findings, P5_FAIL_CLOSED_VERIFY, [
        "mapfile",
        "isPersistedLegacyEvidenceRecoveryEnabled()) throw",
    ], "P5_FAIL_CLOSED_VERIFY_PORTABILITY")

    for shell_script in sorted((ROOT / "scripts" / "verify").glob("*.sh")):
        reject_tokens(findings, shell_script, ["mapfile"], "BASH32_VERIFY_PORTABILITY")

    require_tokens(findings, P5_RUNTIME_VERIFY, [
        "tenantAliases(rawTenantId)",
        "flowRuleLegacyFallbackEnabled = false",
        "decideWithGenericAuthority(task",
    ], "P5_RUNTIME_VERIFY_STAGE8_ALIGNMENT")
    reject_tokens(findings, P5_RUNTIME_VERIFY, [
        "List.of(rawTenantId)",
        "persistedLegacyEvidenceRecoveryEnabled = true",
        "isPersistedLegacyRecovery(task)",
    ], "P5_RUNTIME_VERIFY_STAGE8_ALIGNMENT")
    require_tokens(findings, STEP_RECORDER, [
        "--step",
        "--command",
        "--exit-code",
        "steps-file",
        "json.dumps",
    ], "RELEASE_GATE_STEP_RECORDER")
    require_tokens(findings, RELEASE_REPORT, [
        "failures.json",
        "failure-map.md",
        "release-ready.json",
        "release-ready.md",
        "firstErrorSnippet",
        "recommendedRollbackStage",
        "requiredEvidence",
        "stage1Drilldown",
        "stage1-golden-path-drilldown.md",
    ], "RELEASE_GATE_FAILURE_CAPTURE")


def check_source_specific_guard(findings: list[Finding]) -> None:
    text = read(ZERO_GUARD)
    if not text:
        findings.append(Finding("ERROR", "NO_SOURCE_SPECIFIC_HARDCODE", rel(ZERO_GUARD), "Source-specific guard is missing"))
        return
    for token in ["CMS", "MES", "ERP", "sourceSystem"]:
        if token not in text:
            findings.append(Finding("ERROR", "NO_SOURCE_SPECIFIC_HARDCODE", rel(ZERO_GUARD), f"Missing source-specific guard marker: {token}"))


def check_runner(findings: list[Finding]) -> None:
    require_tokens(findings, RUNNER, [
        "--dry-run",
        "--strict",
        "scripts/release/stage8-release-gate.sh",
        "scripts/verify/verify-stage8-release-gate.py",
    ], "STAGE8_RUNNER")
    require_tokens(findings, STAGE1_DRILLDOWN, [
        "Stage 8-F0c",
        "FLOW_RULE_NOT_PERSISTED",
        "RULE_EVENT_CONDITION_MISMATCH",
        "FLOW_AGENT_ASSIGNMENT_NOT_PERSISTED",
        "RUNTIME_LOOKUP_OR_ASSIGNMENT_DID_NOT_USE_FLOW_AGGREGATE",
        "LEGACY_BLOCKER_LEAKED_IN_STANDARD_EVIDENCE",
    ], "STAGE8_F0C_DRILLDOWN")


def main() -> None:
    findings: list[Finding] = []

    check_release_gate_script_mode(findings)
    check_runner(findings)
    check_source_specific_guard(findings)
    check_no_stage8_production_migration(findings)

    require_tokens(findings, VERIFY, [
        "Stage 8 release gate contract verified.",
        "stage8-release-gate",
        "Fresh DB Golden Path",
        "No-Capability Golden Path",
        "Explicit-Capability Golden Path",
        "Upgrade DB Golden Path",
        "Multi-Tenant Isolation",
        "Restart Recovery",
        "Browser E2E",
        "No Legacy Runtime Dependency",
        "No Source-Specific Hardcode",
    ], "VERIFY_STAGE8_CONTRACT")

    require_tokens(findings, MAKEFILE, [
        "verify-stage8-release-gate",
        "characterize-stage8-report",
        "characterize-stage8-dry-run",
        "characterize-stage8-strict",
        "stage8-release-gate",
        "scripts/release/stage8-release-gate.sh --strict",
    ], "MAKEFILE_STAGE8_TARGETS")

    require_tokens(findings, PACKAGE, [
        "verify:stage8-release-gate",
        "stage8:release-gate",
    ], "ADMIN_UI_STAGE8_SCRIPTS")

    require_tokens(findings, STAGE1_ACCEPTANCE, [
        "No-Capability Golden Path",
        "Explicit-Capability Golden Path",
        "taskCompleted",
        "noReadinessSimulator",
        "noLegacyFallbackExpected",
    ], "STAGE1_STRICT_ACCEPTANCE_EVIDENCE")

    require_tokens(findings, STAGE7_REPORT, [
        "NO_LEGACY_RUNTIME_AUTHORITY",
        "SUPPORT_ONLY_CORE_SECURITY",
        "READ_ONLY_INVENTORY",
    ], "NO_LEGACY_RUNTIME_DEPENDENCY_GATE")
    require_tokens(findings, LEGACY_INVENTORY, ["mutationsPerformed"], "LEGACY_INVENTORY_GATE")

    require_tokens(findings, APP_SHELL, ["SUPPORT_ONLY_PREFIXES", "router.replace('/dashboard')"], "BROWSER_SUPPORT_ROUTE_GUARD")
    reject_tokens(findings, SIDEBAR, [
        "/assignment-profiles",
        "/supply-profiles",
        "/dispatch-policies",
        "/testing/dispatch-readiness",
        "/testing/dispatch-simulator",
    ], "BROWSER_STANDARD_NAVIGATION_GATE")

    for doc in DOCS:
        if not doc.exists():
            findings.append(Finding("ERROR", "STAGE8_DOCUMENTATION", rel(doc), "Missing Stage 8 documentation file"))

    status = "PASS" if not findings else "FAIL"
    OUT.mkdir(parents=True, exist_ok=True)
    report = {
        "stage": "Stage 8 - Release Gate",
        "status": status,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "findingCount": len(findings),
            "errorCount": sum(1 for finding in findings if finding.severity == "ERROR"),
            "contract": "Release readiness can only be declared after strict evidence for Fresh DB, No-Capability, Explicit-Capability, Upgrade, Multi-Tenant, Restart, Browser E2E, no Legacy runtime dependency, and no source-specific hardcode.",
        },
        "requiredReleaseEvidence": REQUIRED_RELEASE_GATES,
        "dryRunGate": "make characterize-stage8-dry-run",
        "strictGate": "make stage8-release-gate",
        "releaseReadyDefault": False,
        "findings": [asdict(finding) for finding in findings],
    }
    (OUT / "contract-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    lines = [
        "# Stage 8 Release Gate Contract Report",
        "",
        f"Generated at: `{report['generatedAt']}`",
        "",
        f"Status: **{status}**",
        "",
        "## Required release evidence",
        "",
    ]
    lines += [f"- {item}" for item in REQUIRED_RELEASE_GATES]
    lines += [
        "",
        "## Summary",
        "",
        f"- Findings: {report['summary']['findingCount']}",
        f"- Errors: {report['summary']['errorCount']}",
        "- Dry-run gate: `make characterize-stage8-dry-run`",
        "- Strict release gate: `make stage8-release-gate`",
        "- Release-ready default: `false`; strict evidence must flip it.",
        "",
    ]
    if findings:
        lines += ["## Findings", ""]
        for finding in findings:
            lines.append(f"- **{finding.severity}** `{finding.category}` `{finding.file}` — {finding.detail}")
        lines.append("")
    else:
        lines += ["## Findings", "", "No Stage 8 contract findings.", ""]
    (OUT / "contract-report.md").write_text("\n".join(lines), encoding="utf-8")

    print(f"Stage 8 release gate contract report: {status} findings={len(findings)}")
    if findings:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
