#!/usr/bin/env python3
"""Stage 7 Legacy isolation and retirement contract report.

This report is static and dry-run friendly. It validates that the standard
Dispatch Flow runtime no longer depends on historical Assignment Profile,
Service Scope, Task Scope, Qualification, or legacy Capability Binding
models, while legacy data remains readable only through SUPPORT-only,
read-only compatibility surfaces.
"""
from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".ci-output" / "stage7-legacy-isolation-retirement"

ROUTING = ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"
OFFER_GATE = ROOT / "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/DispatchEligibilityPolicy.java"
DISPATCH_GATE = ROOT / "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchEligibilityService.java"
SECURITY = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java"
ROLES = ROOT / "ai-event-gateway-core/identity-access/src/main/java/com/opensocket/aievent/core/identity/AdminRole.java"
ADMIN_PERMISSION = ROOT / "ai-event-gateway-core/identity-access/src/main/java/com/opensocket/aievent/core/identity/AdminPermission.java"
APPSHELL = ROOT / "ai-event-gateway-admin-ui/components/layout/AppShell.tsx"
SIDEBAR = ROOT / "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx"
ADMIN_TYPES = ROOT / "ai-event-gateway-admin-ui/lib/types/admin.ts"
PACKAGE = ROOT / "ai-event-gateway-admin-ui/package.json"
INVENTORY = ROOT / "scripts/migration/stage7_legacy_dispatch_inventory.py"
TASK_FACADE = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java"
TASK_FACADE_TEST = ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeControllerMockMvcTest.java"

JAVA_CONTAINER_TEST = ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage7LegacyIsolationContainerTest.java"
JAVA_ROLE_TEST = ROOT / "ai-event-gateway-core/identity-access/src/test/java/com/opensocket/aievent/core/identity/AdminRoleStage7LegacySupportTest.java"
JAVA_SERVICE_TEST = ROOT / "ai-event-gateway-core/execution-control/src/test/java/com/opensocket/aievent/core/dispatch/DispatchEligibilityServiceStage7Test.java"
JAVA_POLICY_TEST = ROOT / "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/assignment/DispatchEligibilityPolicyFlowRuleTest.java"
UI_TEST = ROOT / "ai-event-gateway-admin-ui/tests/stage7-legacy-isolation.test.ts"

DOCS = [
    ROOT / "docs/STAGE7_LEGACY_ISOLATION_RETIREMENT/README.md",
    ROOT / "docs/STAGE7_LEGACY_ISOLATION_RETIREMENT/test-matrix.md",
    ROOT / "docs/STAGE7_LEGACY_ISOLATION_RETIREMENT/validation-report.md",
    ROOT / "docs/STAGE7_LEGACY_ISOLATION_RETIREMENT/next-stage.md",
    ROOT / "docs/STAGE7_LEGACY_ISOLATION_RETIREMENT/changed-files.md",
]

LEGACY_STANDARD_ROUTE_TOKENS = [
    "/assignment-profiles",
    "/supply-profiles",
    "/dispatch-policies",
    "/settings/dispatch-governance",
    "/testing/dispatch-readiness",
    "/testing/dispatch-simulator",
]

RUNTIME_LEGACY_AUTHORITY_TOKENS = [
    "backendDispatchEligibilityService",
    "dispatchEligibilityServiceV2",
    "TaskDispatchContractResolverService",
    "dispatchContractResolverService.resolve",
    "serviceScopeEligibility",
    "assignmentProfileEligibility",
    "qualificationEligibility",
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
            findings.append(Finding("ERROR", category, rel(path), f"Forbidden token remains in standard contract: {token}"))


def check_no_stage7_migration(findings: list[Finding]) -> None:
    migration_root = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
    if not migration_root.exists():
        findings.append(Finding("ERROR", "NO_STAGE7_DROP_MIGRATION", rel(migration_root), "Migration directory is missing"))
        return
    stage7_files = [p.name for p in migration_root.glob("*stage7*")]
    if stage7_files:
        findings.append(Finding("ERROR", "NO_STAGE7_DROP_MIGRATION", rel(migration_root), "Stage 7 must not add table-drop or retirement migration: " + ", ".join(stage7_files)))


def check_inventory_read_only(findings: list[Finding]) -> None:
    text = read(INVENTORY)
    if not text:
        findings.append(Finding("ERROR", "READ_ONLY_INVENTORY", rel(INVENTORY), "Inventory script is missing or empty"))
        return
    if '"mutationsPerformed": False' not in text:
        findings.append(Finding("ERROR", "READ_ONLY_INVENTORY", rel(INVENTORY), "Missing read-only mutationsPerformed=false marker"))
    for sql_verb in ("update ", "delete from ", "insert into ", "drop table", "truncate "):
        if re.search(rf"(?i)[\"']\s*{re.escape(sql_verb)}", text):
            findings.append(Finding("ERROR", "READ_ONLY_INVENTORY", rel(INVENTORY), f"Inventory contains mutation SQL: {sql_verb.strip()}"))


def main() -> None:
    findings: list[Finding] = []

    for runtime_file in [ROUTING, OFFER_GATE, DISPATCH_GATE]:
        reject_tokens(findings, runtime_file, RUNTIME_LEGACY_AUTHORITY_TOKENS, "NO_LEGACY_RUNTIME_AUTHORITY")

    require_tokens(findings, ROUTING, [
        "return RoutingPolicy.MANUAL_REVIEW",
        "return flowRuleRequiredSkills(task)",
    ], "FLOW_ONLY_ROUTING_CONTRACT")
    require_tokens(findings, OFFER_GATE, [
        "FLOW_RULE_AGENT_CAPABILITY_RUNTIME",
        'facts.put("legacyEligibility", "DECOMMISSIONED")',
    ], "OFFER_ELIGIBILITY_AUTHORITY_MARKER")
    require_tokens(findings, DISPATCH_GATE, [
        "FLOW_RULE_AGENT_CAPABILITY_RUNTIME",
    ], "DISPATCH_ELIGIBILITY_AUTHORITY_MARKER")

    require_tokens(findings, SECURITY, [
        '"/admin/assignment-profiles/**"',
        '"/admin/dispatch-task-definitions/**"',
        '"/admin/supply-profiles/**"',
        '"/admin/dispatch-policies/**"',
        ".requestMatchers(HttpMethod.GET, LEGACY_SUPPORT_PATHS)",
        '.hasRole("SUPPORT")',
        ".denyAll()",
    ], "SUPPORT_ONLY_CORE_SECURITY")
    require_tokens(findings, ROLES, [
        "SUPPORT(EnumSet.of",
        "SUPPORT_LEGACY_READ",
        "ADMIN(EnumSet.complementOf(EnumSet.of(AdminPermission.SUPPORT_LEGACY_READ)))",
    ], "SUPPORT_ROLE_CONTRACT")
    require_tokens(findings, ADMIN_PERMISSION, ["SUPPORT_LEGACY_READ"], "SUPPORT_PERMISSION_CONTRACT")

    require_tokens(findings, APPSHELL, [
        "SUPPORT_ONLY_PREFIXES",
        "hasRole('SUPPORT')",
        "router.replace('/dashboard')",
        "Legacy mutation API 已停用",
    ], "SUPPORT_ONLY_ADMIN_UI_GUARD")
    reject_tokens(findings, SIDEBAR, LEGACY_STANDARD_ROUTE_TOKENS, "NO_LEGACY_STANDARD_SIDEBAR_ROUTES")
    require_tokens(findings, ADMIN_TYPES, ["'SUPPORT'"], "ADMIN_UI_SUPPORT_ROLE_TYPE")
    require_tokens(findings, PACKAGE, [
        "verify:stage7-legacy-isolation",
        "test:stage7-legacy-isolation",
        "stage7:legacy-isolation",
    ], "ADMIN_UI_STAGE7_SCRIPTS")

    check_inventory_read_only(findings)
    check_no_stage7_migration(findings)

    require_tokens(findings, TASK_FACADE, [
        "task.getRequiredCapabilities()",
        "hasResolvedCapabilityRequirement",
    ], "TASK_TIMELINE_PERSISTED_CAPABILITY_EVIDENCE")
    reject_tokens(findings, TASK_FACADE, [
        "task.getCapabilityRequirementMode()",
    ], "NO_NON_EXISTENT_TASK_CAPABILITY_MODE")
    require_tokens(findings, TASK_FACADE_TEST, [
        "shouldBuildCaseTimelineFromPersistedRequiredCapabilitiesWithoutTaskModeField",
        "shouldReportMalformedPersistedCapabilityRequirementWithoutUsingMissingTaskModeGetter",
    ], "TASK_TIMELINE_REGRESSION_TDD")

    for path, category, tokens in [
        (JAVA_CONTAINER_TEST, "POSTGRES_LEGACY_ISOLATION_TDD", ["standardFlowRoutingWorksWithTenantLegacyRowsEmpty", "conflictingLegacyRowsCannotChangeFlowRuleOrCandidateSelection"]),
        (JAVA_ROLE_TEST, "SUPPORT_ROLE_TDD", ["onlySupportRoleHasLegacyReadPermission", "doesNotContain(AdminPermission.SUPPORT_LEGACY_READ)"]),
        (JAVA_SERVICE_TEST, "DISPATCH_SERVICE_LEGACY_ISOLATION_TDD", ["FLOW_RULE_AGENT_CAPABILITY_RUNTIME", "DECOMMISSIONED"]),
        (JAVA_POLICY_TEST, "OFFER_POLICY_FLOW_RULE_TDD", ["FLOW_RULE_AGENT_CAPABILITY_RUNTIME"]),
        (UI_TEST, "ADMIN_UI_STAGE7_TDD", ["protects historical routes with the SUPPORT role", "keeps legacy routes out of the standard sidebar"]),
    ]:
        require_tokens(findings, path, tokens, category)

    for doc in DOCS:
        if not doc.exists():
            findings.append(Finding("ERROR", "STAGE7_DOCUMENTATION", rel(doc), "Missing Stage 7 documentation file"))

    status = "PASS" if not findings else "FAIL"
    OUT.mkdir(parents=True, exist_ok=True)
    report = {
        "stage": "Stage 7 - Legacy Isolation and Retirement",
        "status": status,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "findingCount": len(findings),
            "errorCount": sum(1 for finding in findings if finding.severity == "ERROR"),
            "contract": "Standard Dispatch Flow runtime must not depend on Legacy Profile, Scope, Qualification, or standalone policy authority; legacy data remains SUPPORT-only and read-only until later release-gated retirement.",
        },
        "standardRuntimeAuthority": [
            "Dispatch Flow",
            "Flow-owned Rule",
            "Flow-selected Agent",
            "Optional approved Capability",
            "Agent Runtime / Capacity",
            "Assignment",
        ],
        "forbiddenStandardAuthorities": [
            "Assignment Profile",
            "Service Scope",
            "Task Scope",
            "Qualification",
            "Source Default",
            "Operation Profile",
            "Legacy Capability Binding",
            "Standalone Dispatch Governance",
            "Readiness Simulator",
        ],
        "gates": [
            "make verify-stage7-legacy-isolation",
            "make characterize-stage7-dry-run",
            "make report-stage7-legacy-inventory",
            "make test-stage7-admin-ui",
            "make test-stage7-core",
        ],
        "findings": [asdict(finding) for finding in findings],
    }
    (OUT / "contract-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    lines = [
        "# Stage 7 Legacy Isolation and Retirement Contract Report",
        "",
        f"Generated at: `{report['generatedAt']}`",
        "",
        f"Status: **{status}**",
        "",
        "## Summary",
        "",
        f"- Findings: {report['summary']['findingCount']}",
        f"- Errors: {report['summary']['errorCount']}",
        "- Contract: standard Dispatch Flow runtime is the only authority; legacy data is read-only SUPPORT evidence until later release-gated retirement.",
        "",
        "## Runtime boundary",
        "",
        "- Standard routing uses Dispatch Flow, Flow-owned Rule, Flow-selected Agent, optional approved Capability, Runtime/Capacity, and Assignment.",
        "- Legacy Profile, Service Scope, Task Scope, Qualification, and legacy Capability Binding must not change Flow, Rule, or candidate selection.",
        "- Tasks without authoritative Flow/Rule evidence fail closed to manual review; legacy evidence is not used to reconstruct routing.",
        "",
        "## Product boundary",
        "",
        "- Standard Sidebar excludes legacy support routes.",
        "- SUPPORT role is required for historical read-only APIs and direct UI routes.",
        "- Legacy mutations are denied by the Core security chain.",
        "",
        "## Data treatment",
        "",
        "- Stage 7 does not delete or convert customer legacy tables.",
        "- The inventory command is SELECT-only and emits `mutationsPerformed=false`.",
        "- Table retirement is deferred until Stage 8 release evidence and an explicit migration plan are complete.",
        "",
    ]
    if findings:
        lines.extend(["## Findings", ""])
        for finding in findings:
            lines.append(f"- **{finding.severity}** `{finding.category}` `{finding.file}` — {finding.detail}")
    else:
        lines.append("No contract findings.")
    lines.append("")
    (OUT / "contract-report.md").write_text("\n".join(lines), encoding="utf-8")

    print(f"Stage 7 legacy isolation/retirement contract report: {status} findings={len(findings)}")
    if findings:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
