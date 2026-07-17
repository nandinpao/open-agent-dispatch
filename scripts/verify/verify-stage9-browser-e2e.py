#!/usr/bin/env python3
"""Validate Phase 9 Browser E2E contract wiring.

This is a static gate that ensures the repository contains a real Playwright
Browser E2E suite for the standard Dispatch Flow workflow. It does not replace
running the live Browser E2E; it prevents regressions where the suite is deleted,
weakened, or redirected back to legacy dispatch models.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADMIN = ROOT / "ai-event-gateway-admin-ui"

REQUIRED_FILES = [
    ADMIN / "playwright.config.ts",
    ADMIN / "tests/e2e/stage9-standard-workflow.spec.ts",
    ADMIN / "tests/e2e/support/stage9StrictApiMonitor.ts",
    ADMIN / "tests/e2e/support/stage9WorkflowHelpers.ts",
    ROOT / "docs/PHASE9_BROWSER_E2E_STANDARD_WORKFLOW.md",
]

REQUIRED_SPEC_TOKENS = [
    "create Source System through standard UI CRUD page",
    "create active Dispatch Flow through UI",
    "requireSelectableAgent",
    "agent-options",
    "dispatch-flows/by-agent",
    "發送真實測試事件",
    "Runtime Decision Chain",
    "Assignment created",
    "DispatchRequest created",
    "Agent ACK",
    "Agent RESULT",
    "Task completed",
    "dispatch-evidence",
    "SERVICE_SCOPE|ASSIGNMENT_PROFILE|SOURCE_COVERAGE|TASK_SCOPE|OPERATION_PROFILE|QUALIFICATION",
]

REQUIRED_MONITOR_TOKENS = [
    "Unexpected HTTP",
    "[403, 404, 500]",
    "tenantId is required",
    "BadSqlGrammarException",
    "permission is not allowed",
    "Tenant context missing on standard Admin request",
    "Legacy or parallel dispatch API was called",
    "/admin/source-systems",
    "/admin/dispatch-flows",
    "/admin/tasks",
    "/admin/dispatch-requests",
]

FORBIDDEN_SPEC_TOKENS = [
    "CMS",
    "MES",
    "ERP_ANALYST",
    "CMS_CONTENT_REVIEWER",
    "LegacyPreAssignmentHandoff",
    "legacy.pre_assignment",
    "LEGACY_PRE_ASSIGNMENT",
]


def fail(message: str) -> None:
    print(f"[stage9-browser-e2e] FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_file(path: Path) -> str:
    if not path.exists():
        fail(f"Missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def main() -> None:
    contents = {path: require_file(path) for path in REQUIRED_FILES}

    spec = contents[ADMIN / "tests/e2e/stage9-standard-workflow.spec.ts"]
    monitor = contents[ADMIN / "tests/e2e/support/stage9StrictApiMonitor.ts"]
    config = contents[ADMIN / "playwright.config.ts"]

    for token in REQUIRED_SPEC_TOKENS:
        if token not in spec:
            fail(f"Browser E2E spec missing required token: {token}")

    for token in REQUIRED_MONITOR_TOKENS:
        if token not in monitor:
            fail(f"Strict API monitor missing required token: {token}")

    for token in FORBIDDEN_SPEC_TOKENS:
        if re.search(re.escape(token), spec, re.IGNORECASE):
            fail(f"Browser E2E spec must not use source-specific or legacy pre-assignment handoff token: {token}")

    if "1366" not in config or "768" not in config or "1920" not in config or "1080" not in config:
        fail("playwright.config.ts must run both 1366x768 and 1920x1080 projects")
    if "trace: 'retain-on-failure'" not in config or "video: 'retain-on-failure'" not in config:
        fail("playwright.config.ts must retain trace and video on failure")

    package = json.loads((ADMIN / "package.json").read_text(encoding="utf-8"))
    scripts = package.get("scripts", {})
    dev_deps = package.get("devDependencies", {})
    if "@playwright/test" not in dev_deps:
        fail("@playwright/test must be a devDependency")
    if scripts.get("test:e2e:stage9") != "playwright test -c playwright.config.ts tests/e2e/stage9-standard-workflow.spec.ts":
        fail("package.json missing exact test:e2e:stage9 script")
    if scripts.get("verify:stage9-browser-e2e") != "python3 ../scripts/verify/verify-stage9-browser-e2e.py":
        fail("package.json missing exact verify:stage9-browser-e2e script")
    if scripts.get("stage9:browser-e2e") != "npm run verify:stage9-browser-e2e && npm run test:e2e:stage9":
        fail("package.json missing exact stage9:browser-e2e script")

    makefile = (ROOT / "Makefile").read_text(encoding="utf-8")
    for token in ["verify-stage9-browser-e2e", "test-stage9-browser-e2e", "phase9-browser-e2e"]:
        if token not in makefile:
            fail(f"Makefile missing {token} target")

    print("Stage 9 Browser E2E standard workflow contract verified.")


if __name__ == "__main__":
    main()
