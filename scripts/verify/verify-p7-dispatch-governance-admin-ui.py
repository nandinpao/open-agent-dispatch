#!/usr/bin/env python3
"""Verify P7 Dispatch Governance Admin UI and controlled workflow contracts."""
from __future__ import annotations
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required P7 file: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, *fragments: str) -> str:
    text = read(relative)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{relative} missing contract fragment: {fragment}")
    return text


def main() -> int:
    page = require(
        "ai-event-gateway-admin-ui/app/settings/dispatch-governance/page.tsx",
        "Dispatch Governance",
        "DispatchGovernanceConsole",
        "AdminUiModeNotice",
        "InformationArchitectureGuide",
    )
    console = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/DispatchGovernanceConsole.tsx",
        "Existing Data",
        "Migration Runs",
        "Templates",
        "Source Defaults",
        "Agent Source Coverage",
        "No default tenant is assumed",
    )
    inventory = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/InventoryDashboard.tsx",
        "Admin-modified protected",
        "Legacy-origin records",
        "detectedSourceType",
        "Current snapshot",
    )
    migrations = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/MigrationRunsPanel.tsx",
        "Approve after review",
        "Apply approved plan",
        "Rollback",
        "Before",
        "Planned change",
        "After",
        "Requester cannot approve their own run",
    )
    wizard = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/DryRunWizard.tsx",
        "Step {step} of 3",
        "Nothing is changed until a different person approves",
        "Admin-modified records are protected",
        "No EXECUTE, REMEDIATE, APPROVE, or external action grant is created",
    )
    defaults = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/SourceDefaultsPanel.tsx",
        "Create analysis baseline",
        "Blocked by default",
        "Require explicit Capability for effectful tasks",
        "externalActionAllowed: false",
    )
    assignments = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/SourceAssignmentsPanel.tsx",
        "Three separate permissions",
        "Source coverage",
        "Capability grant",
        "Action grant",
        "approvalStatus: 'PENDING'",
        "status: 'DRAFT'",
    )
    templates = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/TemplateBrowser.tsx",
        "Optional safe-start templates",
        "No template is imported at application startup",
        "Continue to dry-run wizard",
    )
    template_data = require(
        "ai-event-gateway-admin-ui/lib/dispatch-governance/templates.ts",
        "IMPORT_TEMPLATE_ENTITY",
        "DISPATCH_FLOW",
        "DISPATCH_RULE",
        "capabilityRequirementMode: 'SOURCE_DEFAULT'",
        "sideEffectLevel: 'NONE'",
        "candidatePoolMode: 'SOURCE_SYSTEM_POOL'",
    )
    api = require(
        "ai-event-gateway-admin-ui/lib/api/dispatchGovernanceApi.ts",
        "/admin/dispatch-governance",
        "/data-migrations",
        "/dry-runs/inventory",
        "/dry-runs/generic-conversion",
        "/dry-runs/legacy-fixture-cleanup",
        "/dry-runs/manifest",
        "/approve",
        "/apply",
        "/rollback",
        "/source-defaults",
        "/source-assignments",
    )
    navigation = require(
        "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts",
        "/settings/dispatch-governance",
        "Dispatch Governance",
    )
    controller = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchDataMigrationController.java",
        "/admin/dispatch-governance/data-migrations",
        "/dry-runs/inventory",
        "/dry-runs/generic-conversion",
        "/dry-runs/legacy-fixture-cleanup",
        "/dry-runs/manifest",
        "/runs/{runId}/approve",
        "/runs/{runId}/apply",
        "/runs/{runId}/rollback",
    )
    governance_controller = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchGovernanceController.java",
        "/source-defaults",
        "/analysis-baseline",
        "/source-assignments",
        "/approve",
    )

    combined = "\n".join([page, console, inventory, migrations, wizard, defaults, assignments, templates, template_data, api])
    for forbidden in ["window.confirm", "window.prompt", "tenant-a", "agent-cluster-node"]:
        if forbidden in combined:
            fail(f"P7 UI contains forbidden hardcode/native prompt: {forbidden}")
    for source_name in ["ERP", "MES", "CMS", "WMS"]:
        if re.search(rf"\b{source_name}\b", combined):
            fail(f"P7 UI contains source-specific decision/example token: {source_name}")

    if "defaultValue = \"tenant-a\"" in controller + governance_controller:
        fail("P7 backend controller still assumes tenant-a")
    if "externalActionAllowed: true" in template_data:
        fail("P7 optional template enables external actions")
    if "SOURCE_DEFAULT'" in template_data and "DISPATCH_FLOW" not in template_data:
        fail("P7 optional templates must use the P6-supported Draft Flow/Rule import contract")
    for unsupported_template_entity in ["entityType: 'SOURCE_DEFAULT'", "entityType: 'SOURCE_ASSIGNMENT'"]:
        if unsupported_template_entity in template_data:
            fail(f"P7 optional template uses unsupported import entity: {unsupported_template_entity}")

    commands = [
        (["node", str(ROOT / "scripts/verify/verify-p7-admin-ui-typescript.mjs")], "P7 TypeScript syntax verification"),
        ([sys.executable, str(ROOT / "scripts/architecture/zero_special_case_guard.py")], "P0 zero-special-case guard"),
    ]
    for command, label in commands:
        if subprocess.run(command, cwd=ROOT).returncode:
            fail(f"{label} failed")

    print("[PASS] P7 Dispatch Governance Admin UI and controlled operation workflow verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
