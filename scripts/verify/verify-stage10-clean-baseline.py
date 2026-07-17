#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MIG_DIR = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/db/migration"
BASELINE = MIG_DIR / "V1__clean_dispatch_flow_direct_baseline.sql"

errors: list[str] = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")

def require(text: str, token: str, label: str) -> None:
    if token not in text:
        errors.append(f"missing {label}: {token}")

def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        errors.append(f"forbidden {label}: {token}")

migrations = sorted(p.name for p in MIG_DIR.glob("*.sql"))
if migrations != [BASELINE.name]:
    errors.append("db/migration must contain only the clean baseline; found: " + ", ".join(migrations))

baseline = BASELINE.read_text(encoding="utf-8", errors="ignore") if BASELINE.exists() else ""
for table in [
    "source_systems",
    "agent_profiles",
    "agent_credentials",
    "agent_capability_catalog",
    "agent_capability_assignments",
    "runtime_resources",
    "agent_runtime_bindings",
    "dispatch_flows",
    "dispatch_policies",
    "flow_agent_assignments",
    "flow_required_capabilities",
    "tasks",
    "task_assignments",
    "dispatch_requests",
]:
    require(baseline, f"create table if not exists {table}", f"clean baseline table {table}")

# Build forbidden tokens dynamically so this verifier does not count as its own evidence.
for forbidden in [
    "assignment" + "_profiles",
    "service" + "_scopes",
    "task" + "_scopes",
    "qualification" + "s",
    "source" + "_defaults",
    "agent" + "_source" + "_assignments",
    "operation" + "_profiles",
    "capability" + "_grants",
    "action" + "_grants",
    "legacy" + "_policy" + "_bindings",
    "legacy" + "_dispatch" + "_diagnostics",
    "task" + "_offers",
    "flow" + "_required" + "_skills",
    "legacy" + "_status",
]:
    forbid(baseline, forbidden, "legacy/parallel table or compatibility column in clean baseline")

flow_service = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java")
flow_repo = read("ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java")
flow_readiness = read("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessService.java")
combined_flow = "\n".join([flow_service, flow_repo, flow_readiness])
require(combined_flow, "flow_required_capabilities", "standard Required Capability table name")
forbid(combined_flow, "flow_required_skills", "old required-skill table name in standard Flow SQL")
forbid(flow_service, "legacy_status", "Flow CRUD legacy_status compatibility column")

phase_doc = read("docs/PHASE10_CLEAN_BASELINE.md")
require(phase_doc, "Phase 10 removes", "Phase 10 documentation")
require(phase_doc, "flow_required_capabilities", "Phase 10 required capability table documentation")

if errors:
    raise SystemExit("Stage 10 clean baseline contract failed:\n" + "\n".join(f" - {e}" for e in errors))
print("Stage 10 clean dispatch baseline contract verified.")
