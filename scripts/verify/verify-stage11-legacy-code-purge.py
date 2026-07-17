#!/usr/bin/env python3
from __future__ import annotations
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

RUNTIME_ROOTS = [
    ROOT / "ai-event-gateway-core" / "agent-control" / "src" / "main",
    ROOT / "ai-event-gateway-core" / "task-orchestration" / "src" / "main",
    ROOT / "ai-event-gateway-core" / "execution-control" / "src" / "main",
]
UI_ROOTS = [
    ROOT / "ai-event-gateway-admin-ui" / "app",
    ROOT / "ai-event-gateway-admin-ui" / "components",
    ROOT / "ai-event-gateway-admin-ui" / "lib",
]

PURGED_FILES = [
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/InMemoryAgentAssignmentRepository.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/governance/AgentEnterpriseGovernanceService.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/certification/AgentCertificationService.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/certification/InMemoryAgentCertificationRepository.java",
    "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/recipe/TaskCapabilityResolverService.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/migration/DispatchDataMigrationService.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/governance/DispatchRequirementAuthoritativeService.java",
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/DispatchEligibilityPolicy.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentCertificationController.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentEnterpriseGovernanceController.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchDataMigrationController.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchReadinessController.java",
    "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchRecipeController.java",
    "scripts/verify/verify-p11-legacy-control-path-decommission.py",
    "scripts/verify/verify-p5-legacy-feature-demotion.py",
    "scripts/verify/verify-p6-7-flow-rule-dispatch-bypasses-legacy-profile-gate.py",
    "scripts/verify/verify-p6-data-migration-java.sh",
    "scripts/verify/verify-p6-existing-data-migration-fixture-cleanup.py",
    "scripts/verify/verify-stage7-legacy-isolation.py",
    "scripts/migration/stage7_legacy_dispatch_inventory.py",
    "ai-event-gateway-core/scripts/verify/verify-p25-dispatch-contract-trace-resolution.py",
    "ai-event-gateway-core/scripts/verify/verify-p27-generic-event-task-mapping.py",
    "ai-event-gateway-core/scripts/verify/verify-p29-contract-aware-resolution-repair.py",
    "ai-event-gateway-core/scripts/verify/verify-p30-capability-tasktype-canonical-repair.py",
    "ai-event-gateway-core/scripts/verify/verify-p31-source-contract-fallback-repair.py",
    "ai-event-gateway-core/scripts/verify/verify-p32-capability-binding-approval-compat.py",
]

FORBIDDEN_RUNTIME = re.compile(r"ServiceScope|AssignmentProfile|SourceSystemDispatchDefault|AgentSourceAssignment|OperationProfile|FlowParticipation|Qualification")
FORBIDDEN_UI = re.compile(r"Legacy (Task Types|Service Scope|Rule Diagnostics|Capability|Skill)|Dispatch Governance|Test Dispatch Readiness|Flow Participation", re.IGNORECASE)


def iter_text(root: Path, suffixes: set[str]):
    if not root.exists():
        return
    for path in root.rglob("*"):
        if path.is_file() and path.suffix.lower() in suffixes and "target" not in path.parts and "node_modules" not in path.parts:
            yield path

for rel in PURGED_FILES:
    if (ROOT / rel).exists():
        errors.append(f"purged file still exists: {rel}")

for root in RUNTIME_ROOTS:
    for path in iter_text(root, {".java"}):
        for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if FORBIDDEN_RUNTIME.search(line):
                errors.append(f"runtime authority token remains: {path.relative_to(ROOT)}:{number}: {line.strip()[:240]}")

for root in UI_ROOTS:
    for path in iter_text(root, {".ts", ".tsx", ".js", ".jsx"}):
        for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if FORBIDDEN_UI.search(line):
                errors.append(f"operator legacy workflow token remains: {path.relative_to(ROOT)}:{number}: {line.strip()[:240]}")

char_output = ROOT / ".ci-output" / "stage11" / "stage0-static-characterization.json"
char_output.parent.mkdir(parents=True, exist_ok=True)
result = subprocess.run(
    [sys.executable, "scripts/characterization/stage0_static_characterization.py", "--strict", "--output", str(char_output)],
    cwd=ROOT,
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
if result.returncode != 0:
    errors.append("Stage 0 static characterization is not zero:\n" + result.stdout)
else:
    payload = json.loads(char_output.read_text(encoding="utf-8"))
    if payload.get("summary", {}).get("total") != 0:
        errors.append("Stage 0 static characterization total is not zero")

service = ROOT / "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
if service.exists():
    text = service.read_text(encoding="utf-8", errors="ignore")
    for required in [
        "Phase 11 clean service",
        "findAgentCapabilities",
        "upsertRuntimeBinding",
        "dispatchContractReadiness",
        "DISPATCH_FLOW_DIRECT_ONLY",
    ]:
        if required not in text:
            errors.append(f"clean AgentAssignmentService missing evidence: {required}")

if errors:
    raise SystemExit("Stage 11 legacy code purge contract failed:\n" + "\n".join(f" - {e}" for e in errors))

print("Stage 11 legacy code purge contract verified; Stage 0 characterization findings = 0.")
