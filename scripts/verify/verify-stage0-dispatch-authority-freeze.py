#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

REMOVED_APP_ROUTES = [
    "ai-event-gateway-admin-ui/app/assignment-profiles/page.tsx",
    "ai-event-gateway-admin-ui/app/supply-profiles/page.tsx",
    "ai-event-gateway-admin-ui/app/dispatch-capabilities/page.tsx",
    "ai-event-gateway-admin-ui/app/dispatch-contracts/page.tsx",
    "ai-event-gateway-admin-ui/app/dispatch-policies/page.tsx",
    "ai-event-gateway-admin-ui/app/skills/page.tsx",
    "ai-event-gateway-admin-ui/app/settings/dispatch-contract-builder/page.tsx",
    "ai-event-gateway-admin-ui/app/settings/dispatch-governance/page.tsx",
    "ai-event-gateway-admin-ui/app/settings/dispatch-task-definitions/page.tsx",
    "ai-event-gateway-admin-ui/app/settings/migration-readiness/page.tsx",
    "ai-event-gateway-admin-ui/app/settings/release-cutover/page.tsx",
    "ai-event-gateway-admin-ui/app/testing/dispatch-readiness/page.tsx",
    "ai-event-gateway-admin-ui/app/testing/dispatch-recipes/page.tsx",
    "ai-event-gateway-admin-ui/app/testing/dispatch-simulator/page.tsx",
]

STANDARD_FILES = [
    "docs/ADR-Dispatch-Authority.md",
    "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts",
    "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx",
    "ai-event-gateway-admin-ui/components/layout/AppShell.tsx",
    "ai-event-gateway-admin-ui/components/agents/AgentTable.tsx",
    "ai-event-gateway-admin-ui/lib/dispatch-readiness/dispatchOperatorActions.ts",
]

REQUIRED_ADR_TOKENS = [
    "Source System",
    "Dispatch Flow",
    "Flow Rule",
    "Flow Agent Assignment",
    "Optional Required Capability",
    "Assignment",
    "DispatchRequest",
    "Netty",
    "ACK / RESULT",
    "Task Completed",
]

FORBIDDEN_ROUTE_TOKENS = [
    "/assignment-profiles",
    "/supply-profiles",
    "/dispatch-capabilities",
    "/dispatch-contracts",
    "/dispatch-policies",
    "/settings/dispatch-contract-builder",
    "/settings/dispatch-governance",
    "/settings/dispatch-task-definitions",
    "/settings/migration-readiness",
    "/settings/release-cutover",
    "/testing/dispatch-readiness",
    "/testing/dispatch-recipes",
    "/testing/dispatch-simulator",
]

FORBIDDEN_STANDARD_TEXT = [
    "Assign Source Coverage",
    "No ACTIVE and APPROVED Agent Source Coverage",
    "Assign an Agent Service Scope",
    "Open Dispatch Readiness",
    "Run Dispatch Readiness",
]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise SystemExit(f"missing required artifact: {rel}")
    return path.read_text(encoding="utf-8")


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise SystemExit(f"missing {label}: {token}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise SystemExit(f"forbidden {label}: {token}")


adr = read("docs/ADR-Dispatch-Authority.md")
for token in REQUIRED_ADR_TOKENS:
    require(adr, token, "single dispatch authority token")

for rel in REMOVED_APP_ROUTES:
    if (ROOT / rel).exists():
        raise SystemExit(f"removed legacy/parallel route still exists: {rel}")

combined = "\n".join(read(rel) for rel in STANDARD_FILES)
for token in FORBIDDEN_ROUTE_TOKENS:
    forbid(combined, token, "standard UI legacy route")
for token in FORBIDDEN_STANDARD_TEXT:
    forbid(combined, token, "standard UI legacy remediation")

nav = read("ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts")
for token in ["/dashboard", "/source-systems", "/agents", "/dispatch-flows", "/tasks", "/issues-events", "/settings"]:
    require(nav, token, "standard primary navigation")

agent_table = read("ai-event-gateway-admin-ui/components/agents/AgentTable.tsx")
require(agent_table, "Dispatch Flow", "Agent table standard dispatch wording")
forbid(agent_table, "settings/dispatch-governance", "Agent table governance repair link")

print("Stage 0 dispatch authority freeze contract verified.")
