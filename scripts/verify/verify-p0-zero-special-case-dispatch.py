#!/usr/bin/env python3
"""P0 verification for the zero-special-case dispatch architecture freeze."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def require_file(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require_fragments(relative: str, fragments: list[str]) -> None:
    text = require_file(relative)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{relative} is missing required contract fragment: {fragment}")


def forbid_fragments(relative: str, fragments: list[str]) -> None:
    text = require_file(relative)
    for fragment in fragments:
        if fragment in text:
            fail(f"Generic Flow Rule path contains source-specific literal {fragment}: {relative}")


def main() -> int:
    require_fragments(
        "docs/P0_ZERO_SPECIAL_CASE_DISPATCH/ADR-001-zero-special-case-dispatch.md",
        [
            "Source systems are opaque identifiers",
            "Capability Catalog is globally selectable within a tenant",
            "Existing violations are a burn-down baseline",
            "Allowed by default: read, analyze, and propose",
        ],
    )

    require_fragments(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java",
        [
            "query.setTenantId(task.getTenantId());",
            "query.setSourceSystem(source);",
            "query.setOriginSourceSystem(task.getOriginSourceSystem());",
            "query.setTargetSystem(target);",
            "query.setEventStage(eventStage);",
            "query.setEventType(eventType);",
            "query.setObjectType(task.getObjectType());",
            "query.setErrorCode(task.getErrorCode());",
            "query.setRequestedSkill(requestedSkill);",
        ],
    )

    require_fragments(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingPlan.java",
        ["FLOW_RULE_REQUIRED_BLOCKED"],
    )

    require_fragments(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java",
        [
            "dispatch_flows",
            "dispatch_policies",
            "flow_required_capabilities",
            "query.getSourceSystem()",
            "query.getEventType()",
            "query.getObjectType()",
            "query.getErrorCode()",
        ],
    )

    generic_files = [
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java",
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java",
    ]
    for relative in generic_files:
        forbid_fragments(relative, ['"ERP"', '"MES"', '"CMS"', '"HR"', 'tenant-a', 'agent-cluster-node'])

    require_fragments(
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingServiceCharacterizationTest.java",
        [
            "shouldForwardOpaqueSourceAndCapabilityToPersistedRuleRepository",
            "shouldRemainFailClosedWhenNoPersistedRuleMatchesSyntheticSource",
            "shouldApplyPersistedFlowEvidenceWithoutInterpretingSourceName",
            "SRC_RANDOM_8F2A",
            "CAP_RANDOM_719CD",
        ],
    )

    guard = ROOT / "scripts/architecture/zero_special_case_guard.py"
    result = subprocess.run([sys.executable, str(guard)], cwd=ROOT)
    if result.returncode != 0:
        fail("Zero-special-case baseline guard failed")

    print("[PASS] P0 zero-special-case dispatch architecture freeze and characterization verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
