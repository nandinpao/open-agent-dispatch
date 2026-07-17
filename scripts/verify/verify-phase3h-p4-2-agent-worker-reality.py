#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def require(path: str, *needles: str) -> None:
    file_path = ROOT / path
    if not file_path.is_file():
        fail(f"Missing required file: {path}")
    text = file_path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path} missing required text: {needle}")


def main() -> int:
    require(
        "ai-event-gateway-netty/scripts/netty-tcp-agent-client.js",
        "process-result",
        "AGENT_WORKER_PROCESSING_MS",
        "queuedTasks",
        "TASK_PROGRESS",
        "workerMode === 'work-only'",
        "maxConcurrentTasks",
    )
    require(
        "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh",
        "AGENT_WORKER_MODE=\"${AGENT_WORKER_MODE:-process-result}\"",
        "AGENT_WORKER_PROCESSING_MS",
        "AGENT_MAX_CONCURRENT_TASKS",
        "doctor()",
        "worker received task -> TASK_ACK -> TASK_PROGRESS 25/50/75 -> TASK_RESULT",
    )
    require(
        "ai-event-gateway-admin-ui/hooks/useClusterNodeDetail.ts",
        "nettyRuntimeApi.getDeliveryRuntime()",
        "gatewayTasksFromDeliveryRuntime",
    )
    require(
        "ai-event-gateway-admin-ui/lib/cluster/nodeTaskCorrelation.ts",
        "gatewayTasksFromDeliveryRuntime",
        "deliveryStatusToGatewayTaskStatus",
        "runtimeDeliveryRecords",
    )
    require(
        "ai-event-gateway-admin-ui/components/cluster/ClusterNodeDetailView.tsx",
        "Gateway diagnostics 不是 Task truth，但應能看到 delivery history",
        "AGENT_WORKER_MODE=process-result",
        "Gateway Relay Diagnostics",
    )
    require(
        "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts",
        "AGENT_WORKER_MODE=process-result",
        "AGENT_WORKER_PROCESSING_MS=8000",
    )
    require(
        "docs/PHASE3H_P4_2_AGENT_WORKER_REALITY.md",
        "process-result",
        "work-only",
        "Gateway Relay Diagnostics",
        "CORE_BOOTSTRAP_AGENTS=true",
    )
    print("[OK] Phase 3H-P4.2 agent worker reality and gateway diagnostics checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
