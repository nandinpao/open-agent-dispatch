#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "docs/PHASE3H_P3_AGENT_RECONNECT_REPLAY.md",
    "ai-event-gateway-netty/scripts/netty-tcp-agent-client.js",
    "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh",
    "ai-event-gateway-core/scripts/e2e/mock_task_agent.py",
    "scripts/agents/run-task-worker-agent.sh",
]

REQUIRED_SNIPPETS = {
    "ai-event-gateway-netty/scripts/netty-tcp-agent-client.js": [
        "AGENT_CALLBACK_REPLAY_ENABLED",
        "AGENT_CALLBACK_REPLAY_ON_CONNECT",
        "AGENT_PENDING_CALLBACK_STORE",
        "pending callback saved",
        "replayPendingCallbacks",
        "idempotencyKey",
        "callbackId",
        "replayDetected",
        "markPendingCallbackAccepted",
    ],
    "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh": [
        "PENDING_DIR",
        "pending-callbacks",
        "AGENT_PENDING_CALLBACK_STORE",
        "AGENT_CALLBACK_REPLAY_ENABLED",
        "clear-pending",
        "pendingCallbacks",
    ],
    "ai-event-gateway-core/scripts/e2e/mock_task_agent.py": [
        "pending_callbacks_file",
        "replay_pending_callbacks",
        "remember_terminal_callback",
        "idempotencyKey",
        "replayDetected",
        "mark_pending_callback_accepted",
    ],
    "scripts/agents/run-task-worker-agent.sh": [
        "I6_AGENT_CALLBACK_REPLAY_ENABLED",
        "I6_AGENT_PENDING_CALLBACKS_FILE",
        "replayed after reconnect",
    ],
    "ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts": [
        "recipeReplayWorkerCommand",
        "AGENT_CALLBACK_REPLAY_ENABLED=true",
        "AGENT_CALLBACK_REPLAY_ON_CONNECT=true",
        "pending callback replay store",
    ],
    "ai-event-gateway-admin-ui/tests/dispatch-beginner-workflow.test.ts": [
        "recipeReplayWorkerCommand",
        "AGENT_CALLBACK_REPLAY_ENABLED=true",
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
        path = ROOT / rel
        if not path.is_file():
            fail(f"Missing required file: {rel}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            if snippet not in text:
                fail(f"Missing snippet in {rel}: {snippet}")

    run(["bash", "-n", str(ROOT / "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh")])
    run(["bash", "-n", str(ROOT / "scripts/agents/run-task-worker-agent.sh")])
    if subprocess.run(["node", "--version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        run(["node", "--check", str(ROOT / "ai-event-gateway-netty/scripts/netty-tcp-agent-client.js")])
    if subprocess.run(["python3", "--version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        run(["python3", "-m", "py_compile", str(ROOT / "ai-event-gateway-core/scripts/e2e/mock_task_agent.py")])

    print("[OK] Phase 3H-P3 agent reconnect replay verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
