#!/usr/bin/env python3
"""Verify the additive P1 generic dispatch governance model."""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, fragments: list[str]) -> str:
    text = read(relative)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{relative} is missing contract fragment: {fragment}")
    return text


def forbid_named_sources(relative: str) -> None:
    text = read(relative)
    for token in ('"ERP"', '"MES"', '"CMS"', '"HR"', "tenant-a", "agent-cluster-node"):
        if token in text:
            fail(f"P1 generic file contains forbidden source-specific token {token}: {relative}")


def compile_domain_package() -> None:
    javac = shutil.which("javac")
    if not javac:
        print("[WARN] javac unavailable; dependency-free P1 domain compilation skipped")
        return
    source_dir = ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance"
    sources = sorted(str(path) for path in source_dir.glob("*.java"))
    if not sources:
        fail("P1 governance domain sources are missing")
    with tempfile.TemporaryDirectory(prefix="p1-governance-javac-") as output:
        result = subprocess.run([javac, "-d", output, *sources], cwd=ROOT)
        if result.returncode != 0:
            fail("Dependency-free P1 governance domain compilation failed")


def main() -> int:
    migrations = {
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V111__p1_1_dispatch_operation_profiles.sql": [
            "dispatch_operation_profiles",
            "dispatch_operation_profile_operations",
            "ANALYSIS_ONLY",
            "ANALYSIS_AND_PROPOSAL",
            "CONTROLLED_EXECUTION",
            "REMEDIATION_OPERATOR",
            "READ",
            "ANALYZE",
            "PROPOSE",
            "EXECUTE",
            "REMEDIATE",
            "APPROVE",
            "dispatch_validate_operation_profile_operation",
            "dispatch_validate_operation_profile_flag",
        ],
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V112__p1_2_source_system_dispatch_defaults.sql": [
            "source_system_dispatch_defaults",
            "SOURCE_BASELINE",
            "ALL_SOURCE_TASKS",
            "explicit_capability_required_for_effectful_task",
            "external_action_allowed boolean not null default false",
            "dispatch_validate_source_default_profile_scope",
        ],
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V113__p1_3_agent_source_assignments.sql": [
            "agent_source_assignments",
            "coverage_scope",
            "operation_profile_id",
            "approval_status",
            "SOURCE_COVERAGE_READY",
            "ck_agent_source_assignments_active_requires_approval",
            "dispatch_validate_agent_source_profile_scope",
            "ck_agent_source_assignments_task_types",
            "dispatch_validate_operation_profile_scope_change",
        ],
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V114__p1_4_flow_requirement_contract.sql": [
            "capability_requirement_mode",
            "required_operation",
            "side_effect_level",
            "candidate_pool_mode",
            "explicit_action_authorization_required",
            "default 'LEGACY'",
            "P1_SCHEMA_ONLY_LEGACY_RUNTIME",
        ],
        "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V115__p1_5_task_requirement_evidence.sql": [
            "task_requirement_evidence",
            "required_operations_json",
            "required_capabilities_json",
            "SHADOW_ONLY",
            "dispatch_p1_latest_task_requirement_evidence",
            "ck_task_requirement_evidence_effectful_authorization",
            "dispatch_validate_task_evidence_profile_scope",
            "dispatch_reject_task_requirement_evidence_update",
        ],
    }
    for relative, fragments in migrations.items():
        require(relative, fragments)
        forbid_named_sources(relative)

    # P1 must not seed any source, Agent, Capability, Flow, or Task business row.
    v111 = read(next(iter(migrations)))
    allowed_insert_tables = {
        "dispatch_operation_profiles",
        "dispatch_operation_profile_operations",
    }
    for match in re.finditer(r"insert\s+into\s+([a-zA-Z_][a-zA-Z0-9_]*)", v111, re.IGNORECASE):
        if match.group(1).lower() not in allowed_insert_tables:
            fail(f"V111 inserts non-reference business table: {match.group(1)}")
    for relative in list(migrations)[1:]:
        if re.search(r"\binsert\s+into\b", read(relative), re.IGNORECASE):
            fail(f"P1 source/assignment/evidence migration must not seed business rows: {relative}")

    model_files = [
        "DispatchOperation.java",
        "DispatchOperationProfile.java",
        "SourceSystemDispatchDefault.java",
        "AgentSourceAssignment.java",
        "TaskRequirementEvidence.java",
    ]
    model_base = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/governance/"
    for name in model_files:
        forbid_named_sources(model_base + name)

    repository_base = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/"
    for name in [
        "DispatchOperationProfileRepository.java",
        "SourceSystemDispatchDefaultRepository.java",
        "AgentSourceAssignmentRepository.java",
        "TaskRequirementEvidenceRepository.java",
    ]:
        forbid_named_sources(repository_base + name)

    require(model_base + "SourceSystemDispatchDefault.java", [
        "analysisBaseline",
        "PlatformOperationProfiles.ANALYSIS_AND_PROPOSAL_ID",
        "SourceCoverageScope.ALL_SOURCE_TASKS",
        "setExternalActionAllowed(false)",
    ])
    require(model_base + "DispatchOperation.java", [
        "READ(false)", "ANALYZE(false)", "PROPOSE(false)",
        "EXECUTE(true)", "REMEDIATE(true)", "APPROVE(true)",
    ])

    adapter_base = "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/governance/repository/"
    for name in [
        "JdbcDispatchOperationProfileRepository.java",
        "JdbcSourceSystemDispatchDefaultRepository.java",
        "JdbcAgentSourceAssignmentRepository.java",
        "JdbcTaskRequirementEvidenceRepository.java",
    ]:
        forbid_named_sources(adapter_base + name)

    service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchGovernanceConfigurationService.java"
    require(service, [
        "createAnalysisBaseline",
        "saveSourceAssignment",
        "approveSourceAssignment",
        "This service is deliberately not called by the runtime routing path",
    ])
    forbid_named_sources(service)

    controller = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchGovernanceController.java"
    controller_text = require(controller, [
        '@RequestMapping("/admin/dispatch-governance")',
        '@PostMapping("/source-defaults/{sourceSystem}/analysis-baseline")',
        '@PostMapping("/source-assignments/{assignmentId}/approve")',
        '@RequestParam String tenantId',
    ])
    if "defaultValue = \"tenant-a\"" in controller_text:
        fail("P1 management API must not provide a fallback tenant")
    forbid_named_sources(controller)

    flow_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRuleView.java"
    require(flow_view, [
        "capabilityRequirementMode",
        "requiredOperation",
        "sideEffectLevel",
        "candidatePoolMode",
        "explicitActionAuthorizationRequired",
        "requirementModelVersion",
    ])
    management = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
    management_text = require(management, [
        "capability_requirement_mode",
        "explicit_action_authorization_required",
    ])
    p2_present = (ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V116__p2_1_requirement_shadow_comparison.sql").is_file()
    p10_present = (ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V133__p10_4_remove_shadow_compatibility_guard.sql").is_file()
    p11_present = (ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V134__p11_1_legacy_shadow_archive_and_writer_decommission.sql").is_file()
    if p10_present:
        if "CapabilityRequirementMode.SOURCE_DEFAULT" not in management_text or "Math.max(10, rule.getRequirementModelVersion())" not in management_text:
            fail("P10 build must use the authoritative generic Requirement contract")
    elif p2_present:
        if "P2 shadow mode requires requestedSkill as an authoritative legacy compatibility bridge" not in management_text:
            fail("P2 build must preserve the authoritative legacy compatibility bridge")
    elif "P1 requirement modes are schema-only and must remain DRAFT until the P2 resolver is enabled" not in management_text:
        fail("P1 build must keep non-legacy modes DRAFT")

    # Before P2, P1 repositories must not be wired into runtime. P2 may consume
    # them only through an observational shadow service.
    runtime_files = [
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java",
        "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
        "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java",
        "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchEligibilityService.java",
    ]
    if not p2_present:
        for relative in runtime_files:
            text = read(relative)
            for fragment in ("SourceSystemDispatchDefaultRepository", "AgentSourceAssignmentRepository", "TaskRequirementEvidenceRepository"):
                if fragment in text:
                    fail(f"P1 repository was activated in production runtime path: {relative}")
    elif p11_present:
        require(
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementAuthoritativeService.java",
            ["resolveAndPersist"],
        )
        require(
            "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V134__p11_1_legacy_shadow_archive_and_writer_decommission.sql",
            ["dispatch_reject_decommissioned_shadow_insert", "dispatch_p11_legacy_shadow_archive_summary"],
        )
    else:
        require(
            "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementShadowService.java",
            ["authoritativeRoutingUnchanged=true", "RequirementDecisionStatus.SHADOW_ONLY"],
        )

    require(
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/governance/DispatchGovernanceModelTest.java",
        [
            "analysisBaselineAllowsAllSourceTasksWithoutGrantingExternalAction",
            "analysisProfileDoesNotAllowEffectfulOperation",
            "effectfulRequirementEvidenceRequiresExplicitActionAuthorization",
            "activeSourceAssignmentRequiresApprovalAudit",
            "SRC_RANDOM_8F2A",
        ],
    )
    require(
        "ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/governance/DispatchGovernanceConfigurationServiceTest.java",
        [
            "createsAnalysisBaselineForOpaqueSourceIdentifier",
            "sourceAssignmentUsesCoverageAndOperationProfileInsteadOfSourceSpecificCapability",
            "approvalMakesSourceAssignmentActiveButDoesNotChangeOperationProfile",
        ],
    )

    require("ai-event-gateway-core/architecture/baseline/m8-cross-context-repository-imports.csv", [
        "database-platform,task-orchestration,persistence/dispatch/governance/repository/JdbcAgentSourceAssignmentRepository.java,com.opensocket.aievent.core.routing.governance.AgentSourceAssignmentRepository",
        "database-platform,task-orchestration,persistence/dispatch/governance/repository/JdbcDispatchOperationProfileRepository.java,com.opensocket.aievent.core.routing.governance.DispatchOperationProfileRepository",
        "database-platform,task-orchestration,persistence/dispatch/governance/repository/JdbcSourceSystemDispatchDefaultRepository.java,com.opensocket.aievent.core.routing.governance.SourceSystemDispatchDefaultRepository",
        "database-platform,task-orchestration,persistence/dispatch/governance/repository/JdbcTaskRequirementEvidenceRepository.java,com.opensocket.aievent.core.routing.governance.TaskRequirementEvidenceRepository",
    ])

    ownership = require("ai-event-gateway-core/architecture/table-ownership.csv", [
        "dispatch_operation_profiles,task-orchestration,com.opensocket.aievent.core.routing.governance.DispatchOperationProfileRepository",
        "dispatch_operation_profile_operations,task-orchestration,com.opensocket.aievent.core.routing.governance.DispatchOperationProfileRepository",
        "source_system_dispatch_defaults,task-orchestration,com.opensocket.aievent.core.routing.governance.SourceSystemDispatchDefaultRepository",
        "agent_source_assignments,task-orchestration,com.opensocket.aievent.core.routing.governance.AgentSourceAssignmentRepository",
        "task_requirement_evidence,task-orchestration,com.opensocket.aievent.core.routing.governance.TaskRequirementEvidenceRepository",
    ])

    require("docs/P1_GENERIC_DISPATCH_GOVERNANCE_MODEL/README.md", [
        "does not grant `EXECUTE`, `REMEDIATE`, or `APPROVE`",
        "No Source System owns or limits the Capability Catalog",
        "P2 will consume these records in shadow mode",
    ])

    compile_domain_package()

    p0_guard = ROOT / "scripts/architecture/zero_special_case_guard.py"
    result = subprocess.run([sys.executable, str(p0_guard)], cwd=ROOT)
    if result.returncode != 0:
        fail("P0 zero-special-case guard failed after P1 changes")

    print("[PASS] P1 generic dispatch governance data model, persistence, management API, and safety contracts verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
