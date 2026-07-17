#!/usr/bin/env python3
"""Verify P9 effectful Action callback, recovery, compensation and runtime contracts."""
from __future__ import annotations

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
        fail(f"Missing required P9 file: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, *fragments: str) -> str:
    text = read(relative)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{relative} missing contract fragment: {fragment}")
    return text


def run(command: list[str], label: str) -> None:
    if subprocess.run(command, cwd=ROOT).returncode:
        fail(f"{label} failed")


def main() -> int:
    v127 = require(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V127__p9_1_effectful_action_runtime_lifecycle.sql",
        "external_execution_key",
        "create table if not exists effectful_action_callback_receipts",
        "unique (tenant_id, callback_id)",
        "unique (tenant_id, idempotency_key)",
        "CANCELLATION_REQUESTED",
        "GRANT_REVOKED",
        "CALLBACK_REPLAY_REJECTED",
    )
    v128 = require(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V128__p9_2_compensation_and_manual_review.sql",
        "proposal_kind",
        "COMPENSATION",
        "create table if not exists effectful_action_manual_cases",
        "unique (tenant_id, action_task_id, reason_code)",
    )
    v129 = require(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V129__p9_3_action_sla_metrics_alerts.sql",
        "dispatch_p9_effectful_action_runtime",
        "dispatch_p9_effectful_action_metrics",
        "dispatch_p9_effectful_action_alerts",
        "GRANT_NOT_ACTIVE",
        "GRANT_REVOKED",
    )
    combined_sql = "\n".join((v127, v128, v129)).lower()
    for table in [
        "tasks", "effectful_action_task_links", "effectful_action_callback_receipts",
        "proposed_actions", "effectful_action_manual_cases", "action_catalog", "agent_action_grants",
    ]:
        if re.search(rf"(?:insert\s+into|update|delete\s+from)\s+{re.escape(table)}\b", combined_sql):
            fail(f"P9 migration mutates business rows in {table}; P9 migrations must remain schema/view only")

    callback_service = require(
        "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java",
        "List<TaskCallbackAcceptanceGuard> callbackAcceptanceGuards",
        "validateAcceptanceGuards(type, request, dispatchRequest, task)",
        "callbackRepository.save(record)",
        "eventPublisher.publish(toTaskCallbackAcceptedEvent",
        "CALLBACK_ACCEPTANCE_GUARD_FAILED",
    )
    handle_start = callback_service.index("public TaskCallbackResult handle(")
    handle_end = callback_service.index("private TaskCallbackAcceptedEvent", handle_start)
    handle = callback_service[handle_start:handle_end]
    if not (handle.index("validateCallback(type") < handle.index("validateAcceptanceGuards(type") < handle.index("transitionDispatch(type")):
        fail("P9 callback acceptance guard is not evaluated before state transition")
    if handle.index("callbackRepository.save(record)") > handle.index("eventPublisher.publish(toTaskCallbackAcceptedEvent"):
        fail("P9 accepted callback event must be published only after durable callback inbox save")

    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackAcceptanceGuard.java",
        "before",
        "TaskCallbackGuardDecision evaluate",
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/EffectfulActionCallbackAcceptanceGuard.java",
        "EXTERNAL_EXECUTION_KEY_REQUIRED",
        "EXTERNAL_EXECUTION_KEY_NOT_ISSUED",
        "EXTERNAL_EXECUTION_KEY_MISMATCH",
        "CALLBACK_AGENT_MISMATCH",
        "recordCallbackGuardRejection",
        "findTaskLinkByTaskForUpdate",
    )
    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/events/TaskCallbackAcceptedEvent.java",
        "task.callback.accepted.v1",
        "implements ModuleEvent",
        "OffsetDateTime acceptedAt",
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/EffectfulActionCallbackEventHandler.java",
        "ModuleEventHandler<TaskCallbackAcceptedEvent>",
        "service.handleAcceptedCallback(event)",
    )

    service = require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceService.java",
        "recordCallbackGuardRejection",
        "Propagation.REQUIRES_NEW",
        "handleAcceptedCallback",
        "tryReserveCallbackReceipt",
        "findCallbackReceiptByIdempotencyKey",
        "requestCancellation",
        "processRuntimeDeadlines",
        "handleRevokedGrant",
        "prepareCompensationInternal",
        "requiresIndependentApproval",
        "requiresExplicitCompensationGrant",
        "terminalCancelled",
        "externalExecutionKey",
    )
    for forbidden in ["if (\"ERP\"", "if (\"MES\"", "if (\"CMS\"", "tenant-a", "agent-cluster-node"]:
        if forbidden in service:
            fail(f"P9 service contains forbidden source/fixed identity branch: {forbidden}")

    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceRepository.java",
        "findTaskLinkByTaskForUpdate",
        "findRuntimeDeadlineCandidates",
        "tryReserveCallbackReceipt",
        "saveManualCase",
        "findCompensationProposal",
    )
    require(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/action/JdbcActionGovernanceRepository.java",
        "effectful_action_callback_receipts",
        "effectful_action_manual_cases",
        "external_execution_key",
        "for update",
    )
    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskRecord.java",
        "externalExecutionKey",
    )
    require(
        "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchRequestService.java",
        'input.put("externalExecutionKey", task.getExternalExecutionKey())',
    )
    require(
        "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelay.java",
        'nested.put("externalExecutionKey", payload.get("externalExecutionKey"))',
    )
    require(
        "ai-event-gateway-netty/scripts/netty-tcp-agent-client.js",
        "externalExecutionKey: firstNonBlank(payload.externalExecutionKey, input?.externalExecutionKey)",
        "externalExecutionKey: context.externalExecutionKey || undefined",
    )
    require(
        "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml",
        "external_execution_key",
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ScheduledEffectfulActionRuntimeRecovery.java",
        "@Scheduled",
        "processRuntimeDeadlines",
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ActionGovernanceController.java",
        "/runtime/tasks", "/runtime/metrics", "/runtime/recover-now",
        "/tasks/{actionTaskId}/cancel", "/tasks/{actionTaskId}/prepare-compensation",
        "/manual-cases/{caseId}/acknowledge", "/manual-cases/{caseId}/resolve",
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRequestClassifier.java",
        "isActionManualResolutionMutation",
        "RECOVERY_ADMIN",
        "RECOVERY_OPERATOR",
    )

    ui = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/ActionRuntimePanel.tsx",
        "Effectful Action runtime and recovery",
        "Run deadline recovery",
        "Action Task lifecycle",
        "external execution key",
        "Prepare compensation",
        "Manual handling queue",
        "ConfirmDialog",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/ActionGovernancePanel.tsx",
        "Runtime & Recovery",
        "ActionRuntimePanel",
    )
    require(
        "ai-event-gateway-admin-ui/lib/api/dispatchGovernanceApi.ts",
        "actionRuntimeTasks", "actionRuntimeMetrics", "recoverActionRuntime",
        "cancelActionTask", "prepareActionCompensation", "actionManualCases",
    )
    require(
        "ai-event-gateway-admin-ui/lib/types/dispatchGovernance.ts",
        "EffectfulActionRuntimeMetrics", "ActionRuntimeRecoveryResult",
        "EffectfulActionManualCase", "externalExecutionKey",
    )
    if "window.confirm" in ui or "window.prompt" in ui:
        fail("P9 UI uses native confirmation/prompt")

    require(
        "docs/P9_EFFECTFUL_ACTION_RUNTIME_INTEGRATION/README.md",
        "Callback accepted before revocation",
        "Two-layer replay protection",
        "Cancellation after execution starts is cooperative",
    )
    require(
        "docs/P9_EFFECTFUL_ACTION_RUNTIME_INTEGRATION/data-flow.md",
        "SELECT Action Link FOR UPDATE",
        "acceptedAt < grantRevokedAt",
        "separate Compensation Task",
    )
    require(
        "docs/P9_EFFECTFUL_ACTION_RUNTIME_INTEGRATION/api-examples.md",
        "/runtime/metrics",
        "/prepare-compensation",
        "externalExecutionKey",
    )
    require(
        "docs/P9_EFFECTFUL_ACTION_RUNTIME_INTEGRATION/validation-report.md",
        "P9 current:   771",
        "Existing unrelated release-gate failure",
    )

    ownership = require(
        "ai-event-gateway-core/architecture/table-ownership.csv",
        "effectful_action_callback_receipts",
        "effectful_action_manual_cases",
    )
    if "task-orchestration" not in ownership:
        fail("P9 tables are not owned by task-orchestration")

    migration_dir = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
    for version in (127, 128, 129):
        if len(list(migration_dir.glob(f"V{version}__*.sql"))) != 1:
            fail(f"P9 requires exactly one V{version} migration")

    commands = [
        ([sys.executable, str(ROOT / "scripts/architecture/zero_special_case_guard.py")], "P0 zero-special-case guard"),
        (["bash", str(ROOT / "scripts/verify/verify-p9-effectful-action-runtime-java.sh")], "P9 runtime Java harness"),
        (["bash", str(ROOT / "scripts/verify/verify-p9-effectful-action-callback-guard-java.sh")], "P9 callback acceptance guard harness"),
        (["bash", str(ROOT / "scripts/verify/verify-p9-effectful-action-runtime-adapters-java.sh")], "P9 event/recovery adapter compile harness"),
        (["bash", str(ROOT / "scripts/verify/verify-p8-action-adapters-java.sh")], "P9 repository/controller compile harness"),
        (["bash", str(ROOT / "scripts/verify/verify-p8-action-security-java.sh")], "P9 Action runtime RBAC harness"),
        (["node", str(ROOT / "scripts/verify/verify-p9-effectful-action-runtime-ui.mjs")], "P9 runtime UI transpile"),
        (["bash", str(ROOT / "scripts/verify/verify-p10-generic-routing-java.sh")], "authoritative explicit Action Grant eligibility integration"),
    ]
    for command, label in commands:
        run(command, label)

    print("[PASS] P9 effectful Action callback, recovery, compensation, idempotency, SLA and manual handling verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
