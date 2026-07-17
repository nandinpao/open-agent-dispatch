#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> None:
    text = read(path)
    missing = [token for token in tokens if token not in text]
    if missing:
        raise AssertionError(f"{path} missing tokens: {missing}")

try:
    require(
        "docs/PHASE32_G_SOURCE_FLOW_AGENT_POOL_ADMIN_UI_CONTRACT.md",
        "Agent Pool / Work Queue is the first-version dispatch target",
        "Capability is not the first-version routing gate",
        "Source Flow default Pool",
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java",
        '@GetMapping("/agent-pools")',
        '@PostMapping("/agent-pools")',
        '@PutMapping("/agent-pools/{poolId}")',
        'Select a default Agent Pool before sending a real test event',
    )
    require(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java",
        "public List<AgentPoolView> listAgentPools",
        "public AgentPoolView createOrUpdateAgentPool",
        "validateAgentPoolReferences",
        "CandidatePoolMode.SOURCE_SYSTEM_POOL.name()",
        "An ACTIVE Source Flow requires a default Agent Pool",
    )
    require(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/AgentPoolView.java",
        "private List<AgentPoolMemberView> members",
        "setMembers(List<AgentPoolMemberView> members)",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx",
        "Agent Pool 管理",
        "Pool 是 Phase 32-G 的派單目標",
        "coreAdminApi.createAgentPool",
        "coreAdminApi.updateAgentPool",
    )
    require(
        "ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx",
        "defaultPoolId",
        "targetPoolId",
        "SOURCE_SYSTEM_POOL",
        "Agent Pool / Work Queue",
        "Capability 是 Agent 能力標籤",
    )
    require(
        "ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx",
        "Agent Pool / Work Queue",
        "Phase 32-G 的標準派單關聯是 Agent Pool",
        "Capability 僅是能力標籤參考",
    )
    require(
        "ai-event-gateway-admin-ui/hooks/useAgentDetail.ts",
        "agentPools: CoreAgentPoolView[]",
        "coreAdminApi.getAgentPools",
    )
    require(
        "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts",
        "getAgentPools",
        "createAgentPool",
        "updateAgentPool",
    )
    require(
        "ai-event-gateway-admin-ui/lib/api/endpoints.ts",
        "agentPools: '/admin/dispatch-flows/agent-pools'",
        "agentPool: (poolId: string)",
    )
except AssertionError as exc:
    print(f"Phase 32-G source flow / agent pool admin UI verification failed: {exc}", file=sys.stderr)
    sys.exit(1)

print("Phase 32-G Source Flow / Agent Pool admin UI contract verified.")
