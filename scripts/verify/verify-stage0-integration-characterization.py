#!/usr/bin/env python3
"""Static contract verification for Stage 0 integration characterization."""
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = [
    "scripts/acceptance/stage0-dispatch-characterization.mjs",
    "scripts/characterization/stage0_static_characterization.py",
    "scripts/characterization/stage0_failure_map.py",
    "scripts/characterization/run-stage0.sh",
    "scripts/architecture/stage0_dispatch_feature_freeze.py",
    "scripts/architecture/stage0-dispatch-feature-freeze-baseline.json",
    "docs/STAGE0_INTEGRATION_CHARACTERIZATION/README.md",
    "docs/STAGE0_INTEGRATION_CHARACTERIZATION/test-matrix.md",
    "docs/STAGE0_INTEGRATION_CHARACTERIZATION/feature-freeze.md",
    "docs/CURRENT_DISPATCH_DOMAIN_MODEL.md",
]


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    missing = [item for item in REQUIRED if not (ROOT / item).is_file()]
    if missing:
        fail("missing Stage 0 artifacts: " + ", ".join(missing))

    runner = (ROOT / REQUIRED[0]).read_text(encoding="utf-8")
    forbidden_business_defaults = ["sourceSystem: 'CMS'", 'sourceSystem: "CMS"', "sourceSystem: 'MES'", "sourceSystem: 'ERP'"]
    for marker in forbidden_business_defaults:
        if marker in runner:
            fail(f"Stage 0 runner hardcodes a business Source System: {marker}")

    forbidden_test_paths = ["/dry-run", "/readiness", "/test-chain", "/dispatch-contracts/test-task"]
    for marker in forbidden_test_paths:
        if marker in runner:
            fail(f"Stage 0 runner calls a readiness/simulator path: {marker}")

    required_markers = [
        "capabilityRequirementMode: mode",
        "mode = capabilityCode ? 'EXPLICIT' : 'NONE'",
        "/api/events/intake",
        "/admin/agents/setup",
        "/admin/dispatch-flows?tenantId=",
        "/admin/dispatch-governance/operation-profiles?tenantId=",
        "/admin/dispatch-governance/source-defaults",
        "/admin/dispatch-governance/actions/grants",
        "/admin/capabilities?tenantId=",
        "AGENT_OFFLINE",
        "TENANT_CONTEXT_REQUIRED",
        "-10 tenant contract",
        "--strict",
        "latest.json",
    ]
    for marker in required_markers:
        if marker not in runner:
            fail(f"Stage 0 runner is missing required contract marker: {marker}")

    runner_shell = (ROOT / "scripts/characterization/run-stage0.sh").read_text(encoding="utf-8")
    if "stage0_failure_map.py" not in runner_shell:
        fail("Stage 0 shell runner must always generate the Failure Map")

    makefile = (ROOT / "Makefile").read_text(encoding="utf-8")
    for target in [
        "verify-stage0-dispatch-feature-freeze:",
        "verify-stage0-integration-characterization:",
        "characterize-stage0-static:",
        "characterize-stage0-dry-run:",
        "characterize-stage0-live:",
        "characterize-stage0-strict:",
    ]:
        if target not in makefile:
            fail(f"Makefile missing target {target}")

    domain = (ROOT / "docs/CURRENT_DISPATCH_DOMAIN_MODEL.md").read_text(encoding="utf-8")
    for marker in [
        "Dispatch Flows is the only dispatch setup surface",
        "CMS, MES and ERP are examples only",
        "Flow Participation` is not an active product concept",
        "Stage 0 feature freeze",
    ]:
        if marker not in domain:
            fail(f"Current domain model missing authoritative statement: {marker}")

    print("Stage 0 integration characterization contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
