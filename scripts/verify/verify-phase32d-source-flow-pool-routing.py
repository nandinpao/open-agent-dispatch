#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

checks = []

def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")

def require(path: str, *needles: str):
    text = read(path)
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"{path} missing expected text: {needle}")
    checks.append(path)

def require_not(path: str, *needles: str):
    text = read(path)
    for needle in needles:
        if needle in text:
            raise AssertionError(f"{path} must not contain: {needle}")
    checks.append(path)

require(
    "docs/PHASE32_D_SOURCE_FLOW_POOL_ROUTING_CONTRACT.md",
    "Source Flow.default_pool_id",
    "Flow Rule.target_pool_id",
    "POOL_HAS_NO_ACTIVE_MEMBER",
    "Capability remains Agent metadata",
)

require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingPlan.java",
    "private String targetPoolId;",
    "private String defaultPoolId;",
    "private boolean sourceDefaultPool;",
)

require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRuntimeMatch.java",
    "private String targetPoolId;",
    "private String selectionStrategy",
    "private boolean sourceDefaultPool;",
)

require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/AgentPoolRoutingRepository.java",
    "findActivePool",
)
require(
    "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcAgentPoolRoutingRepository.java",
    "from agent_pools p",
    "left join agent_pool_members m",
    "upper(coalesce(m.member_status, 'ACTIVE'))",
)

repo = "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java"
require(
    repo,
    "source_default as",
    "f.default_pool_id as target_pool_id",
    "coalesce(p.target_pool_id, f.default_pool_id) as target_pool_id",
    "source_default_pool",
    "match.setTargetPoolId",
    "match.setSourceDefaultPool",
)
# Phase 32-D must not use capability_requirement_mode as the final routing gate.
require_not(repo, "where capability_requirement_mode in ('SOURCE_DEFAULT', 'NONE')")

routing = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java"
require(
    routing,
    "private AgentPoolRoutingRepository agentPoolRoutingRepository;",
    "resolveCandidatePool",
    "agentPoolRoutingRepository.findActivePool",
    "RULE_TARGET_POOL_NOT_FOUND",
    "POOL_HAS_NO_ACTIVE_MEMBER",
    "POOL_AGENT_OFFLINE",
    "POOL_AGENT_CAPACITY_FULL",
    "NO_ELIGIBLE_AGENT_IN_POOL",
    "return List.of();",
    "SOURCE_FLOW_DEFAULT_POOL",
)

flow_service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java"
require(
    flow_service,
    "task.setTargetPoolId",
    "task.setAssignedPoolId",
    "plan.setTargetPoolId(match.getTargetPoolId())",
    "plan.setSourceDefaultPool(match.isSourceDefaultPool())",
    "SOURCE_FLOW_NOT_FOUND",
)

require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignment.java",
    "private String assignedPoolId;",
    "private String targetPoolId;",
)
require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/task/po/TaskAssignmentPo.java",
    "assignedPoolId",
    "targetPoolId",
)
require(
    "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskAssignmentDao.xml",
    "assigned_pool_id",
    "target_pool_id",
)
require(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentService.java",
    "assignment.setAssignedPoolId",
    "assignment.setTargetPoolId",
    "SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
    "RULE_TARGET_POOL_NOT_FOUND",
    "POOL_HAS_NO_ACTIVE_MEMBER",
)

print("Phase 32-D Source Flow default Pool / Agent Pool routing contract verified.")
