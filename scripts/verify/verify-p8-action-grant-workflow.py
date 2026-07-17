#!/usr/bin/env python3
"""Verify P8 explicit Action Grant, approval, and effectful Task workflow contracts."""
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
        fail(f"Missing required P8 file: {relative}")
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
    v124 = require(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V124__p8_1_action_catalog_and_agent_grants.sql",
        "create table if not exists action_catalog",
        "create table if not exists agent_action_grants",
        "Action grant requester cannot approve the same grant",
        "IRREVERSIBLE_WRITE",
        "required_approval_count >= 2",
        "Source coverage and Capability grants never imply an Action grant",
    )
    v125 = require(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V125__p8_2_proposed_actions_and_approval.sql",
        "create table if not exists proposed_actions",
        "create table if not exists action_approval_requests",
        "create table if not exists action_approval_decisions",
        "Proposal creator/requester cannot approve or reject the same proposed action",
    )
    v126 = require(
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V126__p8_3_effectful_action_task_handoff.sql",
        "create table if not exists effectful_action_task_links",
        "create table if not exists effectful_action_evidence",
        "unique (tenant_id, idempotency_key)",
        "dispatch_p8_reject_effectful_evidence_mutation",
    )
    combined_sql = "\n".join([v124, v125, v126]).lower()
    for table in [
        "action_catalog",
        "agent_action_grants",
        "proposed_actions",
        "action_approval_requests",
        "action_approval_decisions",
        "effectful_action_task_links",
        "effectful_action_evidence",
    ]:
        if re.search(rf"insert\s+into\s+{re.escape(table)}\b", combined_sql):
            fail(f"P8 migration seeds business data into {table}; Action data must be admin-managed")

    service = require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceService.java",
        "Action grant requester cannot approve the same grant",
        "Proposal creator/requester cannot decide the same Action Approval Request",
        "findActiveGrantForUpdate",
        "findProposalByIdForUpdate",
        "findApprovalRequestByIdForUpdate",
        "EFFECTFUL_ACTION",
        "assignToSpecificAgent",
        "RequirementResolutionMode.EXPLICIT_CAPABILITY",
        "Materialization and dispatch are idempotent",
        "setApprovalStatus(DispatchApprovalStatus.PENDING)",
        "setStatus(\"DRAFT\")",
    )
    repository = require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/action/ActionGovernanceRepository.java",
        "findActiveGrantForUpdate",
        "findProposalByIdForUpdate",
        "findApprovalRequestByIdForUpdate",
        "appendEvidence",
    )
    jdbc = require(
        "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/action/JdbcActionGovernanceRepository.java",
        "for update",
        "effectful_action_task_links",
        "effectful_action_evidence",
    )
    controller = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ActionGovernanceController.java",
        "/admin/dispatch-governance/actions",
        "/catalog/{actionCode}",
        "/grants/{grantId}/approve",
        "/grants/{grantId}/revoke",
        "/proposals/{proposalId}/submit",
        "/approval-requests/{requestId}/decide",
        "/proposals/{proposalId}/materialize-and-dispatch",
        "/tasks/{actionTaskId}/execution-result",
    )
    evaluator = require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/ActionAuthorizationEvaluator.java",
        "ACTION_GRANT_NOT_FOUND",
        "ACTION_GRANT_NOT_ACTIVE",
        "ACTION_GRANT_APPROVED",
    )
    eligibility_service = require(
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/eligibility/GenericDispatchEligibilityService.java",
        "ActionGovernanceRepository",
        "findActiveGrant",
        "setActionGrant",
    )

    classifier = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRequestClassifier.java",
        "/admin/dispatch-governance/actions",
        "RECOVERY_APPROVER",
        "RECOVERY_ADMIN",
        "RECOVERY_OPERATOR",
    )
    security = require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java",
        "/admin/dispatch-governance/actions/grants/*/approve",
        "/admin/dispatch-governance/actions/approval-requests/*/decide",
        "/admin/dispatch-governance/actions/catalog/*",
        "RECOVERY_APPROVER",
        "RECOVERY_ADMIN",
        "RECOVERY_OPERATOR",
    )
    proxy = require(
        "ai-event-gateway-admin-ui/lib/server/backendProxy.ts",
        "isActionGovernancePath",
        "isActionApprovalPath",
        "isActionAdminPath",
        "RECOVERY_APPROVER",
        "RECOVERY_ADMIN",
        "RECOVERY_OPERATOR",
    )

    ui = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/ActionGovernancePanel.tsx",
        "Action Catalog",
        "Agent Action Grants",
        "Proposed Actions",
        "Approval Queue",
        "Nothing was executed",
        "Create and dispatch Action Task",
        "ConfirmDialog",
        "Source coverage and Capability grants allow analysis, but never grant external side effects",
    )
    console = require(
        "ai-event-gateway-admin-ui/components/dispatch-governance/DispatchGovernanceConsole.tsx",
        "Action Governance",
        "ActionGovernancePanel",
    )
    api = require(
        "ai-event-gateway-admin-ui/lib/api/dispatchGovernanceApi.ts",
        "const governanceBase = '/admin/dispatch-governance'",
        "${governanceBase}/actions",
        "saveActionCatalog",
        "approveActionGrant",
        "submitProposedAction",
        "decideActionApproval",
        "materializeAndDispatchAction",
        "recordActionExecutionResult",
    )
    types = require(
        "ai-event-gateway-admin-ui/lib/types/dispatchGovernance.ts",
        "ActionCatalogEntry",
        "AgentActionGrant",
        "ProposedAction",
        "ActionApprovalRequest",
        "ActionTaskMaterializationResult",
        "EffectfulActionEvidence",
        "EffectfulActionTaskLink",
    )

    combined_runtime = "\n".join([service, repository, jdbc, controller, evaluator, eligibility_service, classifier, security, proxy, ui, console, api, types])
    for forbidden in ["window.confirm", "window.prompt", "tenant-a", "agent-cluster-node"]:
        if forbidden in combined_runtime:
            fail(f"P8 contains forbidden native prompt or fixed identity: {forbidden}")
    for source_name in ["ERP", "MES", "CMS", "WMS"]:
        if re.search(rf"\b{source_name}\b", combined_runtime):
            fail(f"P8 contains source-specific decision/example token: {source_name}")

    ownership = require(
        "ai-event-gateway-core/architecture/table-ownership.csv",
        "action_catalog",
        "agent_action_grants",
        "proposed_actions",
        "action_approval_requests",
        "action_approval_decisions",
        "effectful_action_task_links",
        "effectful_action_evidence",
    )
    if "task-orchestration" not in ownership:
        fail("P8 action tables are not assigned to task-orchestration ownership")

    migration_dir = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
    for version in (124, 125, 126):
        if len(list(migration_dir.glob(f"V{version}__*.sql"))) != 1:
            fail(f"P8 requires exactly one V{version} migration")

    commands = [
        ([sys.executable, str(ROOT / "scripts/architecture/zero_special_case_guard.py")], "P0 zero-special-case guard"),
        (["bash", str(ROOT / "scripts/verify/verify-p8-action-governance-java.sh")], "P8 action workflow Java harness"),
        (["bash", str(ROOT / "scripts/verify/verify-p8-action-adapters-java.sh")], "P8 repository/controller compile harness"),
        (["bash", str(ROOT / "scripts/verify/verify-p8-action-security-java.sh")], "P8 action RBAC Java harness"),
        (["node", str(ROOT / "scripts/verify/verify-p8-action-ui.mjs")], "P8 Action Governance TypeScript verification"),
        (["bash", str(ROOT / "scripts/verify/verify-p10-generic-routing-java.sh")], "authoritative Action Grant eligibility integration"),
    ]
    for command, label in commands:
        run(command, label)

    print("[PASS] P8 explicit Action Grant, independent approval, and effectful Task workflow verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
