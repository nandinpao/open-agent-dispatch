#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")


def require(path: str, token: str) -> None:
    text = read(path)
    if token not in text:
        raise AssertionError(f"Expected token not found in {path}: {token}")


def forbid(path: str, token: str) -> None:
    text = read(path)
    if token in text:
        raise AssertionError(f"Forbidden token found in {path}: {token}")


def main() -> None:
    subprocess.run([sys.executable, str(ROOT / "scripts/verify/verify-phase4-3-legacy-api-deprecation-headers.py")], check=True)

    capability_selector = "ai-event-gateway-admin-ui/components/agents/CapabilityCardSelector.tsx"
    agent_detail = "ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx"
    evidence_panel = "ai-event-gateway-admin-ui/components/dispatch-evidence/DispatchAssignmentEvidencePanel.tsx"
    failure_panel = "ai-event-gateway-admin-ui/components/trace/FailureAnalysisPanel.tsx"
    core_api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
    core_types = "ai-event-gateway-admin-ui/lib/types/core.ts"
    doc = "docs/archive/phase-series/current-history/development/phase4-4-admin-ui-legacy-surface-labeling.md"
    changelog = "docs/archive/phase-series/current-history/phase4-4-change-log.md"
    modified = "docs/archive/phase-series/current-history/phase4-4-modified-files.md"
    readme = "docs/current/README.md"
    makefile = "Makefile"

    for path in (capability_selector, agent_detail, evidence_panel, failure_panel, core_api, core_types, doc, changelog, modified, readme, makefile):
        read(path)

    # Visible UI labels.
    for token in (
        "Reference-only capability catalog",
        "Current setup path: Source Flow -> Agent Pool -> Pool Member Agent",
        "reference-only metadata; Current dispatch eligibility is managed through Source Flow and Agent Pool configuration",
    ):
        require(capability_selector, token)

    for token in (
        "特殊能力（Reference-only）",
        "Reference-only / diagnostic-only section",
        "Reference-only capability labels",
        "Open Source Flow / Agent Pool setup",
        "Capability Labels",
    ):
        require(agent_detail, token)

    for token in (
        "Capability evidence: diagnostic-only",
        "Legacy/diagnostic requirement",
        "Reference-only capabilities",
        "Runtime diagnostics",
        "not as the Current setup path",
    ):
        require(evidence_panel, token)

    for token in (
        "Diagnostic-only legacy fields",
        "Source Flow, Agent Pool, Pool membership and runtime eligibility first",
        "reference-only / diagnostic-only",
    ):
        require(failure_panel, token)

    # Client/type comments prevent future UI code from treating legacy surfaces as Current setup APIs.
    for token in (
        "Capability catalog APIs are reference-only / diagnostic-only for Current setup",
        "Legacy/governance eligibility diagnostic",
        "Legacy eligible-agent diagnostic",
        "Governance eligibility diagnostic-only surface",
        "Legacy dispatch-contract resolver",
        "Capability resolver is diagnostic/reference-only",
        "Legacy dispatch-contract readiness repair surface",
        "Legacy dispatch-contract repair surface",
    ):
        require(core_api, token)

    for token in (
        "Capability catalog is reference-only metadata for Current dispatch setup",
        "Agent capability assignments are reference-only labels / diagnostic evidence",
        "Assignment Profile is a legacy/governance type",
        "Eligible-agents responses are diagnostic-only",
        "Task dispatch-contract resolution is legacy/reference evidence",
        "Dispatch-contract readiness is a legacy repair/readiness surface",
    ):
        require(core_types, token)

    # Documentation and Make target.
    for token in (
        "Phase 4-4：Admin UI Legacy Surface Labeling",
        "Source Flow -> Agent Pool -> Pool Member Agent",
        "reference-only / diagnostic-only",
        "No routing behavior changed",
    ):
        require(doc, token)
        require(changelog, "Phase 4-4")

    # Phase 8A removes historical phase links from docs/current/README.md.
    # Keep this verifier focused on the Phase 4-4 artifact itself and Make target.
    require("docs/archive/historical-asset-governance.md", "Phase 8B")
    require(makefile, "verify-phase4-4-admin-ui-legacy-surface-labeling")
    require(makefile, "verify-phase4-3-legacy-api-deprecation-headers")

    # Phase 4-4 must not introduce schema migrations.
    migration_names = [p.name for p in (ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration").glob("V*__*.sql")]
    if any(name.startswith("V7__") for name in migration_names):
        raise AssertionError("Phase 4-4 must not introduce a V7 Flyway migration")

    print("Phase 4-4 Admin UI legacy surface labeling contract verified.")


if __name__ == "__main__":
    main()
