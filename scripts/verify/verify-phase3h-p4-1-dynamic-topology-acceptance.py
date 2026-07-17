#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "docs/PHASE3H_P4_1_DYNAMIC_TOPOLOGY_ACCEPTANCE.md",
    "scripts/agents/assert-dynamic-topology-recovery.sh",
    "scripts/agents/run-dynamic-topology-recovery-test.sh",
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts",
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx",
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts",
]

REQUIRED_SNIPPETS = {
    "scripts/agents/assert-dynamic-topology-recovery.sh": [
        "Dispatch Ledger exists",
        "Callback Inbox has callback records",
        "Gateway node diagnostics are never treated as task/callback truth",
        "/admin/tasks/${encoded_task}/dispatch-ledger",
        "/admin/tasks/${encoded_task}/callback-inbox",
        "ACCEPTED dynamic topology recovery",
    ],
    "scripts/agents/run-dynamic-topology-recovery-test.sh": [
        "acceptance_hint",
        "assert-dynamic-topology-recovery.sh",
        "TASK_ID=<created-task-id>",
        "Core Dispatch Ledger + Callback Inbox",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts": [
        "recipeDynamicTopologyAcceptanceCommand",
        "assert-dynamic-topology-recovery.sh",
        "TASK_ID=<created-task-id>",
    ],
    "ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx": [
        "topologyAcceptanceCommand",
        "驗收檢查 · Core persisted truth",
        "Core Dispatch Ledger",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "recipeDynamicTopologyAcceptanceCommand",
        "Core persisted-truth acceptance command",
        "assert-dynamic-topology-recovery",
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

    run(["bash", "-n", str(ROOT / "scripts/agents/assert-dynamic-topology-recovery.sh")])
    run(["bash", "-n", str(ROOT / "scripts/agents/run-dynamic-topology-recovery-test.sh")])

    print("[OK] Phase 3H-P4.1 dynamic topology acceptance verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
