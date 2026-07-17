#!/usr/bin/env python3
"""Stage 3 Dispatch Flow aggregate contract report.

This report is intentionally static/dry-run friendly. It does not prove the
PostgreSQL transaction tests passed; it proves the repository contains the
Stage 3 contract hooks and produces an auditable report before live/container
verification.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".ci-output" / "stage3-flow-aggregate-contract"

SERVICE = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
CONTROLLER = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java"
API = ROOT / "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
FLOW_VIEW = ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowView.java"
RULE_VIEW = ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRuleView.java"
CANDIDATES = ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/JdbcGenericCandidateAgentRepository.java"
RULES = ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java"
TESTS = [
    ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage3DispatchFlowAggregateTransactionContainerTest.java",
    ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/DispatchFlowControllerAggregateMutationTest.java",
]
MIGRATIONS = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"


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
            findings.append(Finding("ERROR", category, rel(path), f"Forbidden partial/legacy token still exposed: {token}"))


def main() -> None:
    findings: list[Finding] = []

    require_tokens(findings, SERVICE, [
        "@Transactional\n    public DispatchFlowView createOrUpdateFlow",
        "transactionMode=FULL_REPLACEMENT",
        "delete from flow_agent_assignments",
        "delete from flow_required_skills",
        "delete from dispatch_policies",
        "pg_advisory_xact_lock",
        "validateAggregate(normalized)",
        "verifyPersistedAggregate(normalized, saved)",
        "CandidatePoolMode.EXPLICIT_FLOW_AGENTS.name()",
        "CapabilityRequirementMode.NONE.name()",
        "Required Capabilities are not active in this tenant's Capability Catalog",
        "Agent does not exist in the selected tenant",
        "ID is already owned by another Dispatch Flow",
        "requestedSkill is a Runtime compatibility projection",
    ], "FLOW_AGGREGATE_SERVICE")

    require_tokens(findings, CONTROLLER, [
        "Partial Dispatch Flow mutation is disabled",
        "HttpStatus.CONFLICT",
        "requireTenantMatch",
    ], "FLOW_AGGREGATE_CONTROLLER")

    reject_tokens(findings, API, [
        "upsertDispatchFlowRule(",
        "upsertDispatchFlowSkill(",
        "upsertDispatchFlowAgent(",
    ], "ADMIN_UI_PARTIAL_MUTATION")
    require_tokens(findings, API, [
        "createDispatchFlow(body: CoreDispatchFlowView",
        "updateDispatchFlow(flowId: string, body: CoreDispatchFlowView",
        "getDispatchFlowRules(flowId: string",
        "getDispatchFlowSkills(flowId: string",
        "getDispatchFlowAgents(flowId: string",
    ], "ADMIN_UI_FLOW_FACADE")

    require_tokens(findings, FLOW_VIEW, [
        'defaultCapabilityRequirementMode = "NONE"',
        'defaultCandidatePoolMode = "EXPLICIT_FLOW_AGENTS"',
    ], "FLOW_DTO_DEFAULTS")
    require_tokens(findings, RULE_VIEW, [
        "private String capabilityRequirementMode;",
        'candidatePoolMode = "EXPLICIT_FLOW_AGENTS"',
    ], "RULE_DTO_DEFAULTS")
    reject_tokens(findings, FLOW_VIEW, [
        'defaultCapabilityRequirementMode = "SOURCE_DEFAULT"',
        'defaultCandidatePoolMode = "SOURCE_SYSTEM_POOL"',
    ], "FLOW_DTO_DEFAULTS")
    reject_tokens(findings, RULE_VIEW, [
        'capabilityRequirementMode = "SOURCE_DEFAULT"',
        'candidatePoolMode = "SOURCE_SYSTEM_POOL"',
    ], "RULE_DTO_DEFAULTS")

    require_tokens(findings, CANDIDATES, [
        "from flow_agent_assignments",
        "assignment_status",
        "approval_status",
    ], "RUNTIME_CANDIDATE_REPOSITORY")
    require_tokens(findings, RULES, [
        "from dispatch_policies p",
        "flow_required_skills",
        "candidate_pool_mode",
    ], "RUNTIME_RULE_REPOSITORY")

    for test in TESTS:
        if not test.exists():
            findings.append(Finding("ERROR", "STAGE3_TDD", rel(test), "Missing Stage 3 container/controller TDD test"))

    if MIGRATIONS.exists():
        stage3_migrations = sorted(path for path in MIGRATIONS.glob("*stage3*") if path.is_file())
        for migration in stage3_migrations:
            findings.append(Finding("ERROR", "NO_NEW_MODEL_OR_MIGRATION", rel(migration), "Stage 3 must not add a new migration or Dispatch model"))

    status = "PASS" if not findings else "FAIL"
    OUT.mkdir(parents=True, exist_ok=True)
    report = {
        "stage": "Stage 3 - Dispatch Flow Aggregate Facade",
        "status": status,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "summary": {
            "findingCount": len(findings),
            "errorCount": sum(1 for f in findings if f.severity == "ERROR"),
            "contract": "Dispatch Flow create/update is the only standard mutation facade for Flow, Rule, selected Agents, and Required Capabilities.",
        },
        "findings": [asdict(f) for f in findings],
    }
    (OUT / "contract-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    lines = [
        "# Stage 3 Dispatch Flow Aggregate Contract Report",
        "",
        f"Status: **{status}**",
        f"Generated at: `{report['generatedAt']}`",
        "",
        "## Contract",
        "",
        "Dispatch Flow create/update is the single standard mutation facade. The request is authoritative for:",
        "",
        "- Flow basic data",
        "- Flow-owned Rules",
        "- selected Agents",
        "- Required Capabilities",
        "- activation status",
        "",
        "Runtime repositories must read the same saved child rows shown by Admin UI read models.",
        "",
        "## Findings",
        "",
    ]
    if findings:
        for f in findings:
            lines.append(f"- **{f.severity}** `{f.category}` `{f.file}` — {f.detail}")
    else:
        lines.append("No Stage 3 static contract findings.")
    lines.append("")
    lines.extend([
        "## Release-grade gate still required",
        "",
        "This static report does not replace PostgreSQL Testcontainers. Run:",
        "",
        "```bash",
        "make test-stage3-dispatch-flow-aggregate",
        "```",
        "",
    ])
    (OUT / "contract-report.md").write_text("\n".join(lines), encoding="utf-8")

    print(f"Stage 3 flow aggregate contract report: {status} findings={len(findings)}")
    if findings:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
