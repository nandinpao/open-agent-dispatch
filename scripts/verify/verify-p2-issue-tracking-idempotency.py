#!/usr/bin/env python3
"""Verify P2 issue tracking contract/idempotency hardening artifacts.

This is a source-level release guard. Maven/JUnit still owns runtime execution.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED = {
    "docs/P2_ISSUE_TRACKING_IDEMPOTENCY_HARDENING.md": [
        "Redmine / GitLab contract tests",
        "X-OpenDispatch-Idempotency-Key",
        "issue-action-idempotency-key",
        "duplicate external issue comments",
    ],
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueExecutorRequest.java": [
        "private String idempotencyKey",
        "request.setIdempotencyKey(action.getIdempotencyKey())",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/AdapterActionService.java": [
        "adapterActionIdempotencyKey",
        "issueActionIdempotencyKey",
        "issueCommentDedupeKey",
        "issueCommentDedupeKey(TaskRecord task",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/AbstractHttpIssueVendorExecutor.java": [
        "X-OpenDispatch-Idempotency-Key",
        "issue-action-idempotency-key=",
        "headersWithIdempotency",
        "withIdempotencyMarker",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/RedmineIssueVendorExecutor.java": [
        "headersWithIdempotency(request, Map.of(\"X-Redmine-API-Key\"",
        "comment(request)",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/GitlabIssueVendorExecutor.java": [
        "headersWithIdempotency(request, Map.of(\"PRIVATE-TOKEN\"",
        "comment(request)",
    ],
    "ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueTrackingAdapterActionExecutor.java": [
        "map.put(\"adapterActionId\"",
        "map.put(\"idempotencyKey\"",
        "responseRef(response, action)",
    ],
    "ai-event-gateway-core/adapter-action/src/test/java/com/opensocket/aievent/core/action/executor/issue/IssueVendorExecutorContractTest.java": [
        "redmineCreateIssueShouldSendExpectedPayloadAndParseIssueReference",
        "gitlabUpdateCommentShouldCreateIssueNoteAgainstLinkedIssue",
        "X-OpenDispatch-Idempotency-Key",
        "OpenDispatch: issue-action-idempotency-key=idem-contract-1",
    ],
    "ai-event-gateway-core/adapter-action/src/test/java/com/opensocket/aievent/core/action/TaskTerminalIssueSyncIntegrationTest.java": [
        "duplicateTerminalIssueCallbackShouldCreateOnlyOneExecutableIssueAction",
        "issueActionIdempotencyKey",
        "issueCommentDedupeKey",
        "OpenDispatch: issue-action-idempotency-key=",
    ],
}


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    for relative_path, markers in REQUIRED.items():
        path = ROOT / relative_path
        if not path.is_file():
            fail(f"Missing required file: {relative_path}")
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                fail(f"Missing marker '{marker}' in {relative_path}")

    print("P2 issue tracking idempotency hardening verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
