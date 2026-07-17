#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    raise SystemExit(f"[FAIL] {message}")


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        fail(f"{label} missing required fragment: {needle}")


def main() -> int:
    baseline_path = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
    mapper_path = "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent/AgentGovernanceDao.xml"
    hotfix_path = "scripts/db/phase24-live-agent-governance-audit-schema-repair.sql"
    doc_path = "docs/PHASE24_AGENT_GOVERNANCE_AUDIT_SCHEMA_REPAIR.md"
    makefile_path = "Makefile"

    baseline = read(baseline_path).lower()
    mapper = read(mapper_path).lower()
    hotfix = read(hotfix_path).lower()
    doc = read(doc_path)
    makefile = read(makefile_path)

    tables = [
        "agent_authorization_scopes",
        "agent_approval_audit",
        "agent_security_events",
        "agent_security_enforcement_policies",
    ]
    for table in tables:
        require(mapper, table, mapper_path)
        require(baseline, f"create table if not exists {table}", baseline_path)
        require(hotfix, f"create table if not exists {table}", hotfix_path)
        require(doc, table, doc_path)

    for fragment in [
        "audit_id varchar(128) primary key",
        "enrollment_id varchar(128)",
        "operator_id varchar(128)",
        "reason text",
        "idx_agent_approval_audit_agent_created",
        "idx_agent_approval_audit_enrollment_created",
    ]:
        require(baseline, fragment, baseline_path)
        require(hotfix, fragment.split()[0] if fragment.startswith("idx_") else fragment, hotfix_path)

    for fragment in [
        "verify-stage24-agent-governance-audit-schema-repair",
        "phase24-agent-governance-audit-schema-repair",
        "verify-stage24-agent-governance-audit-schema-repair.py",
    ]:
        require(makefile, fragment, makefile_path)

    print("Stage 24 Agent governance audit schema repair contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
