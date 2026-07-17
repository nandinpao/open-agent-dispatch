#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

checks = [
    ("phase 32-f docs", "docs/PHASE32_F_TASK_DETAIL_A2A_EVIDENCE_CHAIN_CONTRACT.md", "TRIAGE parent task"),
    ("task detail evidence panel", "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "TaskA2AEvidenceChainPanel"),
    ("task detail chain label", "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "TRIAGE → Classification → RESOLUTION"),
    ("task detail parent link", "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "parentTask"),
    ("task detail child list", "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "childTasks"),
    ("task detail classification evidence", "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "classificationResultJson"),
    ("task detail pool evidence", "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "Target Pool"),
    ("task detail capability boundary", "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "Capability 只作為能力標籤參考"),
    ("task family type", "ai-event-gateway-admin-ui/hooks/useTaskDetail.ts", "CoreTaskFamilyEvidence"),
    ("task family lookup", "ai-event-gateway-admin-ui/hooks/useTaskDetail.ts", "getTasksRuntimeView"),
    ("task family child filter", "ai-event-gateway-admin-ui/hooks/useTaskDetail.ts", "candidate.parentTaskId === effectiveParentId"),
    ("current model docs", "docs/CURRENT_DISPATCH_DOMAIN_MODEL.md", "Phase 32-F Task Detail A2A Evidence Chain"),
    ("adr docs", "docs/ADR-Dispatch-Authority.md", "Phase 32-F Task Detail A2A Evidence Chain"),
]

missing = []
for label, rel, token in checks:
    path = ROOT / rel
    if not path.exists():
        missing.append(f"{label}: missing file {rel}")
        continue
    text = path.read_text(errors="ignore")
    if token not in text:
        missing.append(f"{label}: missing token {token!r} in {rel}")

ui = (ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx").read_text(errors="ignore")
for forbidden in [
    "Review Required Capability",
    "Phase 8 standard Task CTA",
    "Required Capability 與 Retry Dispatch",
]:
    if forbidden in ui:
        missing.append(f"Task Detail standard UX must not present Capability as the routing gate: found {forbidden}")

if "Agent Pool" not in ui or "Source Flow" not in ui:
    missing.append("Task Detail must emphasize Source Flow and Agent Pool in Phase 32-F UX")

if missing:
    print("Phase 32-F Task Detail A2A evidence chain verification failed:")
    for item in missing:
        print(f" - {item}")
    raise SystemExit(1)

print("Phase 32-F Task Detail A2A evidence chain contract verified.")
