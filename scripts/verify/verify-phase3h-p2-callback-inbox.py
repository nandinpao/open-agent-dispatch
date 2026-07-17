#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/CallbackInboxEntry.java",
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/CallbackInboxSummary.java",
    "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/callback/CallbackInboxService.java",
    "ai-event-gateway-core/execution-control/src/test/java/com/opensocket/aievent/core/callback/CallbackInboxServiceTest.java",
    "docs/PHASE3H_P2_DURABLE_CALLBACK_INBOX.md",
]

REQUIRED_SNIPPETS = {
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java": [
        "/callback-inbox",
        "CallbackInboxService",
        "CallbackInboxEntry",
        "CallbackInboxSummary",
    ],
    "ai-event-gateway-admin-ui/lib/api/endpoints.ts": [
        "taskCallbackInbox",
        "dispatchRequestCallbackInbox",
        "callbackInboxRecent",
    ],
    "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts": [
        "getTaskCallbackInbox",
        "getTaskCallbackInboxSummary",
    ],
    "ai-event-gateway-admin-ui/lib/types/core.ts": [
        "CoreCallbackInboxEntry",
        "CoreCallbackInboxSummary",
    ],
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx": [
        "Durable Callback Inbox",
        "Callback Inbox",
        "callback inbox records",
    ],
    "ai-event-gateway-admin-ui/hooks/useTaskDetail.ts": [
        "callbackInbox",
        "getTaskCallbackInbox",
        "getTaskCallbackInboxSummary",
    ],
}


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    for rel in REQUIRED_FILES:
        path = ROOT / rel
        if not path.is_file():
            fail(f"Missing required file: {rel}")

    for rel, snippets in REQUIRED_SNIPPETS.items():
        path = ROOT / rel
        if not path.is_file():
            fail(f"Missing required file: {rel}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            if snippet not in text:
                fail(f"Missing snippet in {rel}: {snippet}")

    print("[OK] Phase 3H-P2 durable callback inbox verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
