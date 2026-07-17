#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(rel: str, token: str, label: str) -> None:
    text = read(rel)
    if token not in text:
        errors.append(f"{rel} missing {label}: {token!r}")


def forbid(rel: str, token: str, label: str) -> None:
    text = read(rel)
    if token in text:
        errors.append(f"{rel} contains forbidden {label}: {token!r}")


current = "docs/CURRENT_DISPATCH_DOMAIN_MODEL.md"
adr = "docs/ADR-Dispatch-Authority.md"
phase = "docs/PHASE32_A_SOURCE_FLOW_AGENT_POOL_CONTRACT.md"
ui = "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx"
makefile = "Makefile"
package = "ai-event-gateway-admin-ui/package.json"

for rel in (current, adr, phase, ui, makefile, package):
    read(rel)

for token in (
    "Flow 是派單流程；Capability 不是派單流程。",
    "SourceSystem",
    "Source Flow",
    "Rule / Category Override",
    "Agent Pool / Work Queue",
    "Capability is an Agent metadata tag",
    "not a required routing gate",
    "Triage Task",
    "Resolution Task",
    "Phase 32-B introduces Agent Pool persistence",
):
    require(current, token, f"Phase 32-A domain token {token}")

for token in (
    "superseded by Phase 32-A contract",
    "Source Flow",
    "Agent Pool / Work Queue",
    "Capability as required routing gate",
    "Create default Triage Pool",
    "Send Event with only sourceSystem",
):
    require(adr, token, f"ADR Phase 32-A token {token}")

for token in (
    "Status: **Completed in this patch as contract/UI guardrail work**",
    "capabilityRequirementMode = NONE",
    "requiredCapabilities = []",
    "requiredSkills = []",
    "Out of scope for Phase 32-A",
    "No production DB migration.",
    "Phase 32-B should implement the database model",
):
    require(phase, token, f"Phase 32-A delivery token {token}")

for token in (
    "能力標籤參考",
    "Capability 不作為第一版派單 gate",
    "phase32aCapabilityReferenceOnly",
    "capabilityRequirementMode: 'NONE'",
    "const selectedRequiredCapabilities: CoreDispatchFlowRequiredCapabilityView[] = []",
    "僅供查詢，不會寫入",
):
    require(ui, token, f"UI Phase 32-A guardrail token {token}")

for token in (
    "editor.capabilityCodes.length ? 'EXPLICIT' : 'NONE'",
    "onChange={() => toggleCapability",
    "toggleCapability(code",
    "Required Capability 只由本頁",
):
    forbid(ui, token, f"standard UI required capability creator {token}")

for rel in (current, adr, phase):
    for token in (
        "optional Capability eligibility",
        "Optional Required Capability",
        "Capability participates in eligibility only when",
    ):
        forbid(rel, token, f"obsolete capability-gate wording {token}")

require(makefile, "verify-phase32a-source-flow-contract", "Makefile Phase 32-A verifier target")
require(makefile, "phase32-a", "Makefile Phase 32-A aggregate target")
require(package, "verify:phase32a-source-flow-contract", "Admin UI package Phase 32-A script")

if errors:
    print("Phase 32-A source flow / agent pool contract verification failed:", file=sys.stderr)
    for err in errors:
        print(f" - {err}", file=sys.stderr)
    sys.exit(1)

print("Phase 32-A source flow / agent pool contract verified.")
