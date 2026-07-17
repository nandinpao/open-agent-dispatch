#!/usr/bin/env python3
"""Static verification for Phase 3H-P0 Callback Truth Architecture Correction."""
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


def not_contains(path: str, text: str) -> None:
    data = exists(path).read_text(encoding="utf-8")
    if text in data:
        fail(f"Unexpected legacy text in {path}: {text}")


def main() -> int:
    helper = "ai-event-gateway-admin-ui/lib/cluster/nodeTaskCorrelation.ts"
    truth = "ai-event-gateway-admin-ui/lib/runtime/callbackTruth.ts"
    node_view = "ai-event-gateway-admin-ui/components/cluster/ClusterNodeDetailView.tsx"
    task_view = "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
    test = "ai-event-gateway-admin-ui/tests/cluster-node-task-correlation.test.ts"
    doc = "docs/PHASE3H_P0_CALLBACK_TRUTH_ARCHITECTURE.md"

    for path in (helper, truth, node_view, task_view, test, doc):
        exists(path)

    for text in (
        "TELEMETRY_MISSING",
        "GATEWAY_RUNTIME_DIAGNOSTICS",
        "coreRecentTasksCount",
        "gatewayTelemetryMissingEmptyText",
        "must not be mixed",
    ):
        contains(helper, text)

    for text in (
        "callbackTruthPrinciples",
        "callbackTruthSummary",
        "gatewayDiagnosticsDisclaimer",
        "taskAuthorityDisclaimer",
        "Callback Inbox",
    ):
        contains(truth, text)

    for text in (
        "Recent Gateway Relay Diagnostics",
        "不混入 Core recent tasks",
        "Callback Truth Boundary",
        "gatewayTelemetryMissingEmptyText",
    ):
        contains(node_view, text)

    for text in (
        "Callback Truth Boundary",
        "taskAuthorityDisclaimer",
        "Gateway runtime 只提供 transport observation",
    ):
        contains(task_view, text)

    for text in (
        "does not mix Core recent tasks",
        "does not infer Gateway node deliveries from Agent ownership",
        "Core DB / Dispatch Ledger / Callback Inbox",
    ):
        contains(test, text)

    for legacy in (
        "CORE_SINGLE_NODE_FALLBACK",
        "CORE_NODE_MATCH",
        "Core fallback · single-node",
        "falls back to Core recent tasks",
    ):
        not_contains(helper, legacy)
        not_contains(test, legacy)
        not_contains(node_view, legacy)

    contains("scripts/verify/verify-release.py", "verify-phase3h-p0-callback-truth-architecture.py")

    print("Phase 3H-P0 Callback Truth Architecture Correction verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
