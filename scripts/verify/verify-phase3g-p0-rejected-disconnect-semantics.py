#!/usr/bin/env python3
"""Static verification for Phase 3G-P0 rejected/disconnect runtime semantics."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def exists(path: str) -> Path:
    p = ROOT / path
    if not p.exists():
        fail(f"Missing required file: {path}")
    return p


def contains(path: str, text: str) -> None:
    data = exists(path).read_text(encoding="utf-8")
    if text not in data:
        fail(f"Missing required text in {path}: {text}")


def main() -> int:
    helper = "ai-event-gateway-admin-ui/lib/runtime/rejectedConnectionSemantics.ts"
    rejected_table = "ai-event-gateway-admin-ui/components/runtime/RejectedConnectionsTable.tsx"
    rejected_page = "ai-event-gateway-admin-ui/app/runtime/rejected-connections/page.tsx"
    dashboard = "ai-event-gateway-admin-ui/components/dashboard/DualPlaneDashboard.tsx"
    agent_detail_hook = "ai-event-gateway-admin-ui/hooks/useAgentDetail.ts"
    agent_detail_view = "ai-event-gateway-admin-ui/components/agents/AgentDetailView.tsx"
    governance_actions = "ai-event-gateway-admin-ui/components/agents/AgentGovernanceWorkflowActions.tsx"
    test = "ai-event-gateway-admin-ui/tests/rejected-connection-semantics.test.ts"
    doc = "docs/PHASE3G_P0_REJECTED_DISCONNECT_SEMANTICS.md"

    for path in (helper, rejected_table, rejected_page, dashboard, agent_detail_hook, agent_detail_view, governance_actions, test, doc):
        exists(path)

    for text in (
        "Rejected Connections 只代表連線握手被拒絕",
        "manual disconnect",
        "Runtime Events / Core security events",
        "Handshake denied only; excludes manual disconnect",
        "appendManualDisconnectNotice",
    ):
        contains(helper, text)

    for text in (
        "rejectedConnectionsEmptyDescription",
        "rejectedConnectionSemantics().examples",
        "不會出現在這裡",
    ):
        contains(rejected_table, text)

    for text in (
        "rejectedConnectionMetricSubtitle",
        "latestRejectedConnectionsEmptyText",
        "manual disconnect 請看 Runtime Events",
    ):
        contains(dashboard, text)

    contains(agent_detail_hook, "appendManualDisconnectNotice")
    contains(agent_detail_view, "不會列入 Rejected Connections")
    contains(governance_actions, "manualDisconnectResultNotice")
    contains(test, "rejected connection and disconnect semantics")
    contains("scripts/verify/verify-release.py", "verify-phase3g-p0-rejected-disconnect-semantics.py")

    print("Phase 3G-P0 rejected/disconnect semantics verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
