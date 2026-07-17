#!/usr/bin/env python3
"""Verify P0-1 R12.14 / V108 baseline validation artifacts.

This is a static repository guard.  It does not connect to Postgres; the runtime
DB validation is exposed through the V109 diagnostic views and the SQL runbook.
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
    v108 = require_text(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V108__r12_13_flow_rule_seed_rerun_and_task_repair.sql",
        "drop view if exists dispatch_r12_13_flow_rule_repair_diagnostics",
        "drop view if exists dispatch_r12_11_existing_task_flow_repair_report",
        "rt.event_stage::varchar(64) as event_stage",
        "create or replace view dispatch_r12_13_flow_rule_repair_diagnostics",
    )
    if "CREATE OR REPLACE a view when a column type changes" not in v108:
        fail("V108 should document the R12.14 PostgreSQL view type fix.")

    require_text(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V109__p0_1_r12_14_v108_baseline_validation.sql",
        "dispatch_p0_1_r12_14_v108_baseline_report",
        "dispatch_p0_1_r12_14_v108_baseline_failures",
        "dispatch_p0_1_r12_14_v108_task_defects",
        "Flyway V108 successful",
        "No failed V108 history entry",
        "tenant-a ERP active Flow-owned rules",
        "tenant-a ERP required Flow skills",
        "tenant-a ERP Flow Agent assignment ready",
        "Non-terminal ERP/MES/CMS task legacy-evidence defects",
        "FLOW_RULE_MATCH_AVAILABLE_REASSIGN_REQUIRED",
        "STILL_MISSING_FLOW_RULE_EVIDENCE",
    )

    require_text(
        "scripts/db/p0-1-r12-14-v108-baseline-check.sql",
        "flyway_schema_history",
        "dispatch_p0_1_r12_14_v108_baseline_report",
        "dispatch_p0_1_r12_14_v108_baseline_failures",
        "dispatch_policies",
        "flow_required_capabilities",
        "flow_agent_assignments",
        "agent_capability_assignments",
        "dispatch_r12_11_existing_task_flow_repair_report",
    )

    require_text(
        "docs/P0_1_R12_14_V108_BASELINE/README.md",
        "P0-1 — R12.14 / V108 Baseline Validation",
        "does **not** modify the existing V108 migration",
        "dispatch_p0_1_r12_14_v108_baseline_failures",
        "0 rows",
        "The next phase is **P0-2**",
    )

    print("[OK] P0-1 R12.14 / V108 baseline validation artifacts verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
