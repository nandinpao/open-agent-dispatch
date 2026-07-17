#!/usr/bin/env python3
from __future__ import annotations

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
    return path.read_text(encoding="utf-8", errors="ignore")


def require_text(relative: str, *needles: str) -> str:
    text = require_file(relative)
    for needle in needles:
        if needle not in text:
            fail(f"{relative} missing required text: {needle}")
    return text


def main() -> int:
    require_text(
        "Makefile",
        "verify-p2r-dispatch-contract-release-gate",
        "P2-R Dispatch Contract release gate",
    )
    require_text(
        "scripts/verify/verify-p2r-dispatch-contract-release-gate.sh",
        "npm run verify:p2r",
        "npm run typecheck",
        "npm run lint",
        "npm run build",
        "verify-p2m1-dispatch-contract-integrity.sh",
        "verify-p2m2-dispatch-policy-contract-integrity.sh",
        "verify-p2p-cms-content-review-contract.sh",
        "route-smoke.mjs",
        "verify-p2r-dispatch-contract-builder.mjs",
    )
    require_text(
        "ai-event-gateway-admin-ui/package.json",
        '"verify:p2r"',
        "dispatch-contract-builder.test.ts",
    )
    require_text(
        "ai-event-gateway-admin-ui/tests/dispatch-contract-builder.test.ts",
        "/settings/dispatch-contract-builder",
        "CMS_CONTENT_REVIEW_REVIEWER",
        "CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY",
        "p2p-cms-content-review-task",
        "getTaskEligibleAgents",
    )
    require_text(
        "ai-event-gateway-admin-ui/scripts/route-smoke.mjs",
        "/settings/dispatch-contract-builder",
        "/settings/dispatch-task-definitions",
        "/assignment-profiles",
        "/settings/capabilities",
        "/skills",
    )
    require_text(
        "ai-event-gateway-admin-ui/scripts/verify-p2r-dispatch-contract-builder.mjs",
        "p2p-cms-content-review-task",
        "p2p-cms-review-agent-001",
        "/settings/dispatch-contract-builder",
        "eligibleAgents",
    )
    require_text(
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx",
        "P2-Q Dispatch Contract Builder",
        "Policy Binding",
        "Capability Binding",
        "Runtime Feature Trust",
        "Eligibility Result",
        "Run Eligibility",
    )
    require_text(
        "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts",
        "/settings/dispatch-contract-builder",
        "Dispatch Contract Builder",
    )
    require_text(
        "docs/P2_R_DISPATCH_CONTRACT_INTEGRITY_TEST_RELEASE_GATE.md",
        "P2-R",
        "Dispatch Contract Builder",
        "Release Gate",
    )
    verify_release = require_text("scripts/verify/verify-release.py", "verify-p2r-dispatch-contract-release-gate.py")
    if verify_release.count("verify-p2r-dispatch-contract-release-gate.py") != 1:
        fail("verify-release.py should include verify-p2r-dispatch-contract-release-gate.py exactly once")
    print("[PASS] P2-R Dispatch Contract release-gate markers verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
