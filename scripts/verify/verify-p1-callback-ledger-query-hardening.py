#!/usr/bin/env python3
"""P1 callback inbox / dispatch ledger query hardening verifier."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def exists(path: str) -> Path:
    p = ROOT / path
    if not p.is_file():
        fail(f"Missing required file: {path}")
    return p


def contains(path: str, text: str) -> None:
    data = exists(path).read_text(encoding="utf-8")
    if text not in data:
        fail(f"Missing required text in {path}: {text}")


def not_contains(path: str, text: str) -> None:
    data = exists(path).read_text(encoding="utf-8")
    if text in data:
        fail(f"Unexpected text in {path}: {text}")


def main() -> int:
    callback_repo = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackRepository.java"
    callback_service = "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/callback/CallbackInboxService.java"
    ledger_service = "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchAttemptLedgerService.java"
    dao = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/execution/dao/TaskCallbackDao.java"
    xml = "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/execution/TaskCallbackDao.xml"
    mybatis_repo = "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/execution/repository/MybatisTaskCallbackRepository.java"
    memory_repo = "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/callback/InMemoryTaskCallbackRepository.java"
    history_repo = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchAttemptHistoryRepository.java"
    history_xml = "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/execution/DispatchAttemptHistoryDao.xml"
    migration = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V48__p1_callback_ledger_query_indexes.sql"
    callback_test = "ai-event-gateway-core/execution-control/src/test/java/com/opensocket/aievent/core/callback/CallbackInboxServiceTest.java"
    ledger_test = "ai-event-gateway-core/execution-control/src/test/java/com/opensocket/aievent/core/dispatch/DispatchAttemptLedgerServiceTest.java"
    release = "scripts/verify/verify-release.py"

    for path in (
        callback_repo,
        callback_service,
        ledger_service,
        dao,
        xml,
        mybatis_repo,
        memory_repo,
        history_repo,
        history_xml,
        migration,
        callback_test,
        ledger_test,
        release,
    ):
        exists(path)

    contains(callback_repo, "findByDispatchRequestId(String dispatchRequestId, int limit)")
    contains(dao, "findByDispatchRequestId(@Param(\"dispatchRequestId\") String dispatchRequestId")
    contains(xml, '<select id="findByDispatchRequestId"')
    contains(xml, "where dispatch_request_id = #{dispatchRequestId}")
    contains(xml, "order by processed_at desc nulls last, occurred_at desc nulls last")
    contains(mybatis_repo, "dao.findByDispatchRequestId(dispatchRequestId, cap(limit))")
    contains(memory_repo, "public List<TaskCallbackRecord> findByDispatchRequestId")

    contains(callback_service, "callbackRepository.findByDispatchRequestId(dispatchRequestId, safeLimit(limit))")
    not_contains(callback_service, "callbackRepository.recent(Math.max(safeLimit(limit) * 10, 100))")
    contains(ledger_service, "callbacksForDispatch(dispatch, capped, List.of())")
    contains(ledger_service, "callbackRepository.findByDispatchRequestId(dispatch.getDispatchRequestId()")

    contains(history_repo, "findByDispatchRequestId(String dispatchRequestId, int limit)")
    contains(history_xml, '<select id="findByDispatchRequestId"')
    contains(history_xml, "where dispatch_request_id = #{dispatchRequestId}")

    for text in (
        "idx_task_callbacks_dispatch_lookup_p1",
        "on task_callbacks(dispatch_request_id, processed_at desc, occurred_at desc)",
        "idx_task_callbacks_task_lookup_p1",
        "idx_dispatch_attempt_history_dispatch_lookup_p1",
        "idx_dispatch_attempt_history_recent_p1",
    ):
        contains(migration, text)

    for text in (
        "dispatchScopedInboxUsesDirectDispatchRequestQueryInsteadOfRecentWindowScan",
        "recentQueries",
        "dispatch-scoped callback inbox must not use recent-window scans",
    ):
        contains(callback_test, text)

    for text in (
        "dispatchScopedLedgerUsesDirectDispatchCallbackQueryBeforeLegacyTaskFallback",
        "dispatchScopedQueries",
        "dispatch-scoped ledger must not query all task callbacks",
    ):
        contains(ledger_test, text)

    contains(release, "verify-p1-callback-ledger-query-hardening.py")

    print("P1 callback inbox / dispatch ledger query hardening verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
