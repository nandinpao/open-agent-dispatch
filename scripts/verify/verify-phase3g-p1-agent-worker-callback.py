#!/usr/bin/env python3
"""Static verification for Phase 3G-P1 Agent Worker / Callback Simulation."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def exists(path: str) -> Path:
    p = ROOT / path
    if not p.exists():
        fail(f"Missing required file: {path}")
    return p


def contains(path: str, text: str) -> None:
    p = exists(path)
    data = p.read_text(encoding="utf-8")
    if text not in data:
        fail(f"Missing required text in {path}: {text}")


def run(cmd: list[str]) -> None:
    result = subprocess.run(cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        print(result.stdout)
        fail(f"Command failed: {' '.join(cmd)}")


def main() -> int:
    client = "ai-event-gateway-netty/scripts/netty-tcp-agent-client.js"
    cluster = "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh"
    wrapper = "scripts/agents/run-task-worker-agent.sh"
    callback = "scripts/agents/simulate-agent-result-callback.sh"
    doc = "docs/PHASE3G_P1_AGENT_WORKER_CALLBACK_SIMULATION.md"

    for path in (client, cluster, wrapper, callback, doc):
        exists(path)

    for text in (
        "AGENT_WORKER_MODE",
        "process-result",
        "ack-only",
        "observe",
        "TASK_ACK",
        "TASK_PROGRESS",
        "TASK_RESULT",
        "TASK_ERROR",
        "isTaskAssignment",
        "dispatchRequestId",
        "assignmentId",
        "dispatchToken",
        "attemptNo",
        "ownerGatewayNodeId",
    ):
        contains(client, text)

    for text in (
        "AGENT_WORKER_MODE",
        "start-worker",
        "start-observer",
        "restart-worker",
        "AGENT_CAPABILITIES",
        "INCIDENT_ANALYSIS",
    ):
        contains(cluster, text)

    for text in (
        "mock_task_agent.py",
        "TASK_ACK, TASK_PROGRESS, and TASK_RESULT",
        "I6_AGENT_MIN_DISPATCHES",
    ):
        contains(wrapper, text)

    for text in (
        "/internal/control-plane/tasks/",
        "DISPATCH_REQUEST_ID",
        "DISPATCH_TOKEN",
        "SIMULATE_ALLOW_SYNTHETIC_CONTEXT",
    ):
        contains(callback, text)

    contains("ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js", "ERP_PURCHASE_ORDER_REVIEW")
    contains("scripts/verify/verify-release.py", "verify-phase3g-p1-agent-worker-callback.py")

    run(["bash", "-n", str(ROOT / cluster)])
    run(["bash", "-n", str(ROOT / wrapper)])
    run(["bash", "-n", str(ROOT / callback)])
    if subprocess.run(["node", "--version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        run(["node", "--check", str(ROOT / client)])
        run(["node", "--check", str(ROOT / "ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js")])
    if subprocess.run(["python3", "--version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        run(["python3", "-m", "py_compile", str(ROOT / "ai-event-gateway-core/scripts/e2e/mock_task_agent.py")])

    print("Phase 3G-P1 Agent Worker / Callback Simulation verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
