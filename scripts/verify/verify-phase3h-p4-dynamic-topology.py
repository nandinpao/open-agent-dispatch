#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "docs/PHASE3H_P4_DYNAMIC_TOPOLOGY_RECOVERY.md",
    "scripts/agents/run-dynamic-topology-recovery-test.sh",
    "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh",
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts",
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx",
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts",
]

REQUIRED_SNIPPETS = {
    "scripts/agents/run-dynamic-topology-recovery-test.sh": [
        "single-to-cluster",
        "cluster-node-failover",
        "cluster-to-single",
        "Core Dispatch Ledger + Callback Inbox",
        "reconnect-agent-to-node",
        "stop-node",
    ],
    "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh": [
        "start-single-worker",
        "restart-single-worker",
        "start-cluster-worker",
        "restart-cluster-worker",
        "stop-node",
        "reconnect-agent-to-node",
        "pending callback store remains",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts": [
        "recipeDynamicTopologyRecoveryCommand",
        "run-dynamic-topology-recovery-test.sh",
        "cluster-node-failover",
        "AGENT_CALLBACK_REPLAY_ENABLED=true",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx": [
        "topologyCommand",
        "拓樸切換 / reconnect replay 測試",
        "Core Dispatch Ledger / Callback Inbox",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "recipeDynamicTopologyRecoveryCommand",
        "Phase 3H-P4 dynamic topology recovery helpers",
    ],
}


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def run(cmd: list[str]) -> None:
    result = subprocess.run(cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        print(result.stdout)
        fail(f"Command failed: {' '.join(cmd)}")


def main() -> int:
    for rel in REQUIRED_FILES:
        if not (ROOT / rel).is_file():
            fail(f"Missing required file: {rel}")

    for rel, snippets in REQUIRED_SNIPPETS.items():
        text = (ROOT / rel).read_text(encoding="utf-8")
        for snippet in snippets:
            if snippet not in text:
                fail(f"Missing snippet in {rel}: {snippet}")

    run(["bash", "-n", str(ROOT / "scripts/agents/run-dynamic-topology-recovery-test.sh")])
    run(["bash", "-n", str(ROOT / "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh")])

    print("[OK] Phase 3H-P4 dynamic topology recovery verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
