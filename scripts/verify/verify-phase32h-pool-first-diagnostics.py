#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

checks = []

def read(path: str) -> str:
    file = ROOT / path
    if not file.exists():
        raise AssertionError(f"Missing required file: {path}")
    return file.read_text(encoding="utf-8")

def require(path: str, *needles: str):
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise AssertionError(f"{path} missing: {missing}")

def forbid_in_standard_ui(path: str, *needles: str):
    text = read(path)
    found = [needle for needle in needles if needle in text]
    if found:
        raise AssertionError(f"{path} still exposes legacy capability-gate vocabulary: {found}")

require(
    "docs/PHASE32_H_POOL_FIRST_DIAGNOSTICS_CONTRACT.md",
    "Pool-first Diagnostics",
    "SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
    "POOL_HAS_NO_ACTIVE_MEMBER",
    "POOL_AGENT_RUNTIME_NOT_FOUND",
    "POOL_AGENT_OFFLINE",
    "POOL_AGENT_CAPACITY_FULL",
    "NO_ELIGIBLE_AGENT_IN_POOL",
    "Capability remains an Agent metadata/tag surface only",
)

require(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
    "phase32PoolFirst=true",
    "routing_pool_snapshot",
    "routing_pool_blocker",
    "poolMemberCount",
    "eligibleAgentCount",
    "poolFirstNoCandidateMessage",
    "poolFirstNoCandidateAction",
    "runbooks/dispatch/pool-first-troubleshooting",
)

require(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentService.java",
    "POOL_AGENT_RUNTIME_NOT_FOUND",
    "POOL_AGENT_OFFLINE",
    "POOL_AGENT_CAPACITY_FULL",
    "POOL_AGENT_BACKOFF",
    "NO_ELIGIBLE_AGENT_IN_POOL",
    "runbooks/dispatch/pool-first-delayed-no-eligible-agent",
)

require(
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "Pool Blocker",
    "POOL_FIRST_READY",
    "taskPoolBlockerText",
    "Fix Source Flow",
    "Fix Rule Target Pool",
    "Manage Pool Members",
    "Start Agent Runtime",
    "Run Pool Trace",
    "NO_POOL_AGENT_AVAILABLE",
    "POOL_AGENT_CAPACITY_FULL",
    "POOL_AGENT_BACKOFF",
)

forbid_in_standard_ui(
    "ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx",
    "MISSING_REQUIRED_CAPABILITY",
    "AGENT_REQUIRED_CAPABILITY_MISSING",
    "NO_FLOW_AGENT_ASSIGNMENT",
    "focus=required-capability",
)

require(
    "ai-event-gateway-admin-ui/lib/types/core.ts",
    "targetPoolCode?: string",
    "poolMemberCount?: number",
    "eligibleAgentCount?: number",
    "blockerCode?: string",
    "blockerReason?: string",
)

print("Phase 32-H Pool-first diagnostics contract verified.")
