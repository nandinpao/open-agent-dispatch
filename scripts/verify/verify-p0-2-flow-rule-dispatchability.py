#!/usr/bin/env python3
"""Verify P0-2 Flow Rule dispatchability validation artifacts.

This is a static repository guard. It does not connect to Postgres; the runtime
DB validation is exposed through the V110 diagnostic views and the SQL runbook.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def require_text(relative: str, *needles: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    text = path.read_text(encoding="utf-8", errors="ignore")
    for needle in needles:
        if needle not in text:
            fail(f"{relative} missing required text: {needle}")
    return text


def main() -> int:
    require_text(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V110__p0_2_flow_rule_dispatchability_validation.sql",
        "dispatch_p0_2_flow_rule_dispatchability_report",
        "dispatch_p0_2_system_dispatchability_summary",
        "dispatch_p0_2_task_routing_evidence_report",
        "dispatch_p0_2_task_routing_defects",
        "dispatch_p0_2_acceptance_report",
        "dispatch_p0_2_acceptance_failures",
        "dispatch_p0_2_flow_rule_dispatchability_failures",
        "FLOW_RULE_DISPATCHABLE",
        "FLOW_RULE_NO_REQUIRED_SKILL",
        "FLOW_RULE_NO_APPROVED_FLOW_AGENT_ASSIGNMENT",
        "FLOW_RULE_AGENT_MISSING_REQUESTED_SKILL_GRANT",
        "FLOW_RULE_MATCH_AVAILABLE_BUT_TASK_NOT_UPDATED",
        "ERP explicit Flow rules dispatchable",
        "ERP wildcard Flow rule dispatchable",
        "agent_capability_assignments",
        "flow_agent_assignments",
        "flow_required_capabilities",
        "routing_decisions",
    )

    require_text(
        "scripts/db/p0-2-flow-rule-dispatchability-check.sql",
        "dispatch_p0_2_acceptance_failures",
        "expected 0 rows",
        "dispatch_p0_2_system_dispatchability_summary",
        "dispatch_p0_2_flow_rule_dispatchability_failures",
        "dispatch_p0_2_task_routing_defects",
        "dispatch_p0_2_task_routing_evidence_report",
    )

    require_text(
        "docs/P0_2_FLOW_RULE_DISPATCHABILITY/README.md",
        "P0-2 — Flow Rule Dispatchability Validation",
        "does not seed or repair data",
        "dispatch_p0_2_acceptance_failures",
        "0 rows",
        "P1：Dispatch Flow 真正 DB-backed CRUD",
    )

    makefile = require_text(
        "Makefile",
        "verify-p0-2-flow-rule-dispatchability",
        "verify-p0-1-r12-14-v108-baseline",
    )
    if "python3 scripts/verify/verify-p0-2-flow-rule-dispatchability.py" not in makefile:
        fail("Makefile target must run the P0-2 verifier script.")

    release = require_text(
        "scripts/verify/verify-release.py",
        "P0-1 R12.14 / V108 baseline validation",
        "P0-2 Flow Rule dispatchability validation",
        "verify-p0-2-flow-rule-dispatchability.py",
    )
    if release.find("verify-p0-1-r12-14-v108-baseline.py") > release.find("verify-p0-2-flow-rule-dispatchability.py"):
        fail("P0-2 release check should run after P0-1 baseline validation.")

    print("[OK] P0-2 Flow Rule dispatchability validation artifacts verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
