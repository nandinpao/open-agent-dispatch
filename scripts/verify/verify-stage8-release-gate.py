#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise SystemExit(f"missing Stage 8 artifact: {rel}")
    return path.read_text(encoding="utf-8")


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise SystemExit(f"Stage 8 contract missing {label}: {token}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise SystemExit(f"Stage 8 contract still contains {label}: {token}")

release_gate = read("scripts/release/stage8-release-gate.sh")
runner = read("scripts/characterization/run-stage8.sh")
contract = read("scripts/characterization/stage8_release_gate_contract.py")
makefile = read("Makefile")
package = read("ai-event-gateway-admin-ui/package.json")
acceptance = read("scripts/acceptance/stage0-dispatch-characterization.mjs")
stage7 = read("scripts/characterization/stage7_legacy_isolation_retirement_contract.py")
zero_guard = read("scripts/architecture/zero_special_case_guard.py")
sidebar = read("ai-event-gateway-admin-ui/components/layout/Sidebar.tsx")
dispatch_unification = read("scripts/verify/verify-stage8-dispatch-authority-unification.py")
p5a_verify = read("scripts/verify/verify-p5a-production-otel-hardening.py")
p5_fail_closed = read("scripts/verify/verify-p5-runtime-fail-closed-java.sh")
p5_runtime_verify = read("scripts/verify/verify-p5-runtime-hardcode-removal.py")

for token in (
    "Fresh DB Golden Path",
    "No-Capability Golden Path",
    "Explicit-Capability Golden Path",
    "Upgrade DB Golden Path",
    "Multi-Tenant Isolation",
    "Restart Recovery",
    "Browser E2E",
    "No Legacy Runtime Dependency",
    "No Source-Specific Hardcode",
):
    require(release_gate, token, f"release evidence {token}")
    require(contract, token, f"contract evidence {token}")

for token in (
    "make check-toolchain",
    "scripts/release/stage8-managed-local-stack.sh up",
    "scripts/release/stage8-local-runtime-preflight.sh",
    "scripts/release/stage8-managed-local-stack.sh down",
    "make characterize-stage1-strict",
    "make characterize-stage2-strict",
    "make characterize-stage3-strict",
    "make characterize-stage5-strict",
    "make characterize-stage6-strict",
    "make characterize-stage7-strict",
    "make test-stage2-postgres-optional-filters",
    "make test-stage3-dispatch-flow-aggregate",
    "make test-stage5-core",
    "make test-stage6-core",
    "make test-stage7-core",
    "make ci-release",
    "python3 scripts/architecture/zero_special_case_guard.py",
):
    require(release_gate, token, f"strict gate command {token}")

for token in (
    "verify-stage8-release-gate",
    "characterize-stage8-report",
    "characterize-stage8-dry-run",
    "characterize-stage8-strict",
    "stage8-release-gate",
    "scripts/release/stage8-release-gate.sh --strict",
):
    require(makefile, token, f"Makefile target {token}")

require(runner, "scripts/release/stage8-release-gate.sh", "Stage 8 runner delegates to release script")
require(runner, "--dry-run", "Stage 8 dry-run mode")
require(runner, "--strict", "Stage 8 strict mode")
require(package, "verify:stage8-release-gate", "Admin UI Stage 8 verification script")
require(package, "stage8:release-gate", "Admin UI Stage 8 aggregate script")
require(acceptance, "taskCompleted", "strict task completion assertion")
require(acceptance, "noReadinessSimulator", "no simulator evidence")
require(acceptance, "noLegacyFallbackExpected", "no legacy fallback evidence")
require(acceptance, "ensureAgentProfile", "Stage 8-F0d Agent profile precondition")
require(acceptance, "FLOW_CREATE_REJECTED_AGENT_NOT_FOUND", "Stage 8-F0d Flow create failure classification")
require(acceptance, "apiEnvelopeOk", "Stage 8-F0d semantic API envelope failure handling")
require(stage7, "NO_LEGACY_RUNTIME_AUTHORITY", "Stage 7 no Legacy runtime gate")
require(zero_guard, "sourceSystem", "source-specific guard scans sourceSystem")
require(release_gate, "RELEASE_READY=false", "release readiness defaults false")
require(release_gate, "stage8_record_step.py", "step recorder")
require(release_gate, "stage8_release_report.py", "failure-map/release report builder")
require(release_gate, "steps.jsonl", "captured step result JSONL")
require(release_gate, "logs", "per-step log directory")
require(release_gate, "failures.json", "failure JSON output")
require(release_gate, "failure-map.md", "failure map markdown output")
require(release_gate, "release-ready.json", "release report JSON")
require(release_gate, "release-ready.md", "release report markdown")
require(release_gate, "STAGE8_MANAGED_LOCAL_STACK", "managed local stack switch")
require(release_gate, "Local Runtime Readiness", "Core readiness preflight step")
for rel in (
    "scripts/release/stage8_record_step.py",
    "scripts/release/stage8_release_report.py",
    "scripts/release/stage8-local-runtime-preflight.sh",
    "scripts/release/stage8-managed-local-stack.sh",
):
    read(rel)
preflight = read("scripts/release/stage8-local-runtime-preflight.sh")
managed_stack = read("scripts/release/stage8-managed-local-stack.sh")
require(preflight, "/api/auth/csrf", "Core Admin CSRF readiness probe")
require(preflight, "Stage 8 local runtime preflight failed", "actionable runtime readiness failure")
require(preflight, "STAGE8_CORE_READY_ATTEMPTS", "readiness retry tuning")
require(managed_stack, "opendispatch-stage8", "isolated Stage 8 compose project")
require(managed_stack, "WITH_AGENT=true ./scripts/local-compose-up.sh", "managed stack starts mock agent")
require(managed_stack, "compose_down -v", "managed stack starts from fresh isolated volumes")
require(dispatch_unification, "Stage 8 dispatch authority unification contract verified.", "dispatch authority unification verifier")

require(p5a_verify, "_minimal_yaml_safe_load", "P5-A verifier must run without PyYAML in minimal release environments")
require(p5a_verify, "yaml is not None", "P5-A verifier still uses PyYAML when available")
forbid(p5a_verify, "PyYAML is required", "hard dependency on PyYAML in P5-A release verification")

require(p5_fail_closed, "while IFS= read -r stub", "P5 Java fail-closed verifier must support macOS Bash 3.2 without mapfile")
require(p5_fail_closed, "properties.isFlowRuleLegacyFallbackEnabled()", "P5 Java fail-closed verifier checks Stage 8 Flow legacy fallback disabled")
forbid(p5_fail_closed, "mapfile", "Bash 4-only mapfile in P5 Java fail-closed verifier")
forbid(p5_fail_closed, "isPersistedLegacyEvidenceRecoveryEnabled()) throw", "P5 Java fail-closed verifier must not require legacy persisted evidence recovery as Stage 8 safety flag")

for shell_script in (ROOT / "scripts" / "verify").glob("*.sh"):
    forbid(shell_script.read_text(encoding="utf-8"), "mapfile", f"Bash 4-only mapfile in {shell_script.relative_to(ROOT)}")

require(p5_runtime_verify, "tenantAliases(rawTenantId)", "P5 runtime verifier aligns with Stage 8 tenant alias lookup")
require(p5_runtime_verify, "flowRuleLegacyFallbackEnabled = false", "P5 runtime verifier checks Flow legacy fallback disabled")
require(p5_runtime_verify, "decideWithGenericAuthority(task", "P5 runtime verifier requires Stage 8 generic authority path")
forbid(p5_runtime_verify, "List.of(rawTenantId)", "obsolete exact tenant-list verifier contract")
forbid(p5_runtime_verify, "persistedLegacyEvidenceRecoveryEnabled = true", "obsolete legacy recovery default requirement")
forbid(p5_runtime_verify, "isPersistedLegacyRecovery(task)", "obsolete P5 requirement for persisted Legacy recovery")

for route in (
    "/assignment-profiles",
    "/supply-profiles",
    "/dispatch-policies",
    "/testing/dispatch-readiness",
    "/testing/dispatch-simulator",
):
    forbid(sidebar, route, f"standard sidebar legacy route {route}")

for rel in (
    "docs/STAGE8_RELEASE_GATE/README.md",
    "docs/STAGE8_RELEASE_GATE/test-matrix.md",
    "docs/STAGE8_RELEASE_GATE/validation-report.md",
    "docs/STAGE8_RELEASE_GATE/next-stage.md",
    "docs/STAGE8_RELEASE_GATE/changed-files.md",
):
    read(rel)

stage8_migrations = list((ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration").glob("*stage8*"))
if stage8_migrations:
    raise SystemExit("Stage 8 release gate must not add production migration: " + ", ".join(p.name for p in stage8_migrations))

print("Stage 8 release gate contract verified.")
