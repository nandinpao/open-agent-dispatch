#!/usr/bin/env python3
"""Static verification for Phase 3H-P1 Durable Dispatch Attempt Ledger."""
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
    model = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchAttemptLedger.java"
    event = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchAttemptLedgerEvent.java"
    service = "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchAttemptLedgerService.java"
    controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java"
    test = "ai-event-gateway-core/execution-control/src/test/java/com/opensocket/aievent/core/dispatch/DispatchAttemptLedgerServiceTest.java"
    types = "ai-event-gateway-admin-ui/lib/types/core.ts"
    api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    hook = "ai-event-gateway-admin-ui/hooks/useTaskDetail.ts"
    view = "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx"
    doc = "docs/PHASE3H_P1_DURABLE_DISPATCH_ATTEMPT_LEDGER.md"

    for path in (model, event, service, controller, test, types, api, hook, view, doc):
        exists(path)

    for text in (
        "lastKnownGatewayNodeId",
        "callbackState",
        "resultState",
        "recoveryRequired",
        "nextAction",
        "events",
    ):
        contains(model, text)

    for text in (
        "SOURCE_CORE_DISPATCH",
        "SOURCE_CALLBACK_INBOX",
        "findByTaskId",
        "findByDispatchRequestId",
        "CALLBACK_REJECTED",
        "WAIT_FOR_AGENT_RESULT",
    ):
        contains(service, text)

    for text in (
        "/tasks/{taskId}/dispatch-ledger",
        "/dispatch-requests/{dispatchRequestId}/ledger",
        "DispatchAttemptLedgerService",
    ):
        contains(controller, text)

    for text in (
        "CoreDispatchAttemptLedger",
        "CoreDispatchAttemptLedgerEvent",
        "getTaskDispatchLedger",
    ):
        contains(types if text.startswith("Core") else api, text)

    for text in (
        "dispatchLedger",
        "Durable Dispatch Attempt Ledger",
        "Gateway node diagnostics",
    ):
        contains(view if text != "dispatchLedger" else hook, text)

    contains("scripts/verify/verify-release.py", "verify-phase3h-p1-dispatch-ledger.py")
    print("Phase 3H-P1 Durable Dispatch Attempt Ledger verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
