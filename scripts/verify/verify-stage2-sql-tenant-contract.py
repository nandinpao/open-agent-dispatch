#!/usr/bin/env python3
"""Static Stage 2 gate for PostgreSQL optional filters and authoritative tenant context."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, token: str, reason: str) -> None:
    if token not in read(path):
        raise SystemExit(f"ERROR: {reason}: missing {token!r} in {path}")


def main() -> int:
    java_root = ROOT / "ai-event-gateway-core"
    nullable_pattern = re.compile(r"\(:[A-Za-z0-9_]+\s+is\s+null\s+or", re.IGNORECASE)
    offenders: list[str] = []
    for path in java_root.rglob("*.java"):
        if "/src/test/" in f"/{path.relative_to(ROOT).as_posix()}/":
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if nullable_pattern.search(line):
                offenders.append(f"{path.relative_to(ROOT)}:{number}:{line.strip()}")
    if offenders:
        print("ERROR: PostgreSQL-untyped nullable optional-filter SQL remains:")
        for item in offenders:
            print(f"  {item}")
        return 1

    require("ai-event-gateway-admin-ui/lib/api/client.ts", "requireCoreTenantContext", "missing authoritative tenant resolver")
    require("ai-event-gateway-admin-ui/lib/api/client.ts", "TENANT_CONTEXT_REQUIRED", "missing no-workspace fail-fast")
    require("ai-event-gateway-admin-ui/lib/api/client.ts", "TENANT_CONTEXT_MISMATCH", "missing cross-tenant fail-fast")
    require("ai-event-gateway-admin-ui/lib/api/client.ts", "tenantId: selectedTenantId", "selected tenant is not injected")
    require("ai-event-gateway-admin-ui/components/auth/AuthProvider.tsx", "setCoreTenantContext", "AuthProvider does not publish selected tenant")
    require("ai-event-gateway-admin-ui/components/capabilities/CapabilityCatalogConsole.tsx", "getCapabilities(undefined, undefined, selectedTenantId)", "Capability catalog is not tenant-scoped")
    require("ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx", "}, tenantId);", "Applied Dispatch Rules still omits tenantId")


    tenant_request_files = [
        "ai-event-gateway-admin-ui/lib/agents/enrollmentWorkflow.ts",
        "ai-event-gateway-admin-ui/components/agents/AgentEnrollmentTable.tsx",
        "ai-event-gateway-admin-ui/components/agents/AgentEnrollmentReviewDialog.tsx",
        "ai-event-gateway-admin-ui/components/agents/AgentApprovedCapabilityPolicyPanel.tsx",
        "ai-event-gateway-admin-ui/components/assignment-profiles/PolicyBindingModal.tsx",
        "ai-event-gateway-admin-ui/components/assignment-profiles/CapabilityBindingModal.tsx",
        "ai-event-gateway-admin-ui/components/assignment-profiles/CreateProfileDrawer.tsx",
    ]
    tenant_default_pattern = re.compile(
        r"tenantId\s*[:=].{0,80}(?:\?\?|\|\|)\s*[\"']default[\"']",
        re.IGNORECASE,
    )
    for path in tenant_request_files:
        if tenant_default_pattern.search(read(path)):
            raise SystemExit(f"ERROR: tenant-scoped request still falls back to hard-coded default in {path}")

    core_admin = read("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts")
    if 'tenantId = "default"' in core_admin or "tenantId = 'default'" in core_admin:
        raise SystemExit("ERROR: coreAdminApi still contains a hard-coded default tenant parameter")

    required_stage2_artifacts = [
        "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage2PostgresOptionalFilterContainerTest.java",
        "ai-event-gateway-admin-ui/tests/stage2-tenant-context.test.ts",
        "scripts/characterization/stage2_tenant_sql_contract.py",
        "scripts/characterization/run-stage2.sh",
        "docs/STAGE2_SQL_TENANT_CONTRACT/README.md",
        "docs/STAGE2_SQL_TENANT_CONTRACT/test-matrix.md",
        "docs/STAGE2_SQL_TENANT_CONTRACT/validation-report.md",
        "docs/STAGE2_SQL_TENANT_CONTRACT/next-stage.md",
        "docs/STAGE2_SQL_TENANT_CONTRACT/changed-files.md",
    ]
    for artifact in required_stage2_artifacts:
        if not (ROOT / artifact).is_file():
            raise SystemExit(f"ERROR: missing Stage 2 artifact {artifact}")

    makefile = read("Makefile")
    for target in [
        "characterize-stage2-dry-run",
        "characterize-stage2-strict",
        "characterize-stage2-report",
    ]:
        if target not in makefile:
            raise SystemExit(f"ERROR: Makefile missing Stage 2 target {target}")

    print("Stage 2 SQL and tenant contract static verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
