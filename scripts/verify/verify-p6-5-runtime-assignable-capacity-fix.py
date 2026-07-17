#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []

def require(path: str, tokens: list[str]) -> None:
    text = (ROOT / path).read_text()
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"{path} missing required tokens: {missing}")
    checks.append((path, len(tokens)))

require(
    "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/AgentSnapshot.java",
    [
        "P6.5: treat capacity facts as the dispatch authority",
        "status == AgentStatus.OFFLINE || status == AgentStatus.EXPIRED || status == AgentStatus.ERROR",
        "if (availableSlots > 0)",
        "getEffectiveTaskCount() < Math.max(1, maxConcurrentTasks)",
    ],
)
require(
    "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent/AgentDirectoryDao.xml",
    [
        "status in ('CONNECTED','IDLE','BUSY_ACCEPTING','BUSY')",
        "available_slots &gt; 0 or current_task_count + reserved_task_count &lt; max_concurrent_tasks",
    ],
)
require(
    "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java",
    [
        'scoreBreakdown.put("agentStatus"',
        'scoreBreakdown.put("runtimeCapacityAvailable"',
        'scoreBreakdown.put("runtimeAssignable"',
    ],
)
require(
    "docs/P6_5_RUNTIME_ASSIGNABLE_CAPACITY_FIX/README.md",
    [
        "P6.5",
        "selectedScore=49",
        "runtimeAssignable=false",
        "availableSlots=3",
    ],
)
print("verify-p6-5-runtime-assignable-capacity-fix: OK")
for path, count in checks:
    print(f"  {path}: {count} checks")
