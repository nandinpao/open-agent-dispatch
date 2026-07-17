#!/usr/bin/env python3
"""Static verification for Phase 3G-P2/P3H corrected Gateway runtime diagnostics boundary."""
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
    helper = "ai-event-gateway-admin-ui/lib/cluster/nodeTaskCorrelation.ts"
    hook = "ai-event-gateway-admin-ui/hooks/useClusterNodeDetail.ts"
    view = "ai-event-gateway-admin-ui/components/cluster/ClusterNodeDetailView.tsx"
    test = "ai-event-gateway-admin-ui/tests/cluster-node-task-correlation.test.ts"

    for path in (helper, hook, view, test):
        exists(path)

    for text in (
        "NETTY_NODE",
        "TELEMETRY_MISSING",
        "correlateGatewayNodeTasks",
        "gatewayTelemetryMissingEmptyText",
    ):
        contains(helper, text)

    for text in (
        "coreAdminApi.getTasksRuntimeView",
        "taskCorrelation",
    ):
        contains(hook, text)

    for text in (
        "Recent Gateway Relay Diagnostics",
        "Task Detail / Dispatch Ledger / Callback Inbox",
        "Core recent tasks detected",
    ):
        contains(view, text)

    for text in (
        "prefers Gateway node runtime telemetry",
        "does not mix Core recent tasks",
        "does not infer Gateway node deliveries",
    ):
        contains(test, text)

    print("Phase 3G-P2 corrected Gateway runtime diagnostics verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
