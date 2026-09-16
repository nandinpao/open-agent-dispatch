#!/usr/bin/env python3
"""
I7 failure-scenario E2E runner.

This runner reuses the I6 happy-path flow but enables fault injection in the mock
agent. It is designed to prove the I3 CAS guards and I4 backpressure semantics
remain wired in the production-hardening baseline.

Scenarios:
  duplicate-callback  - Agent sends duplicate TASK_RESULT callbacks.
  stale-attempt       - Agent sends one stale-attempt TASK_RESULT callback.
  core-outage-probe   - Probe Netty/Core health and verify the harness can run
                        when Core is restored. The actual outage window is
                        intentionally operator-controlled to avoid destructive CI.
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import List


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


def run_cmd(cmd: List[str], extra_env: dict[str, str]) -> None:
    merged = os.environ.copy()
    merged.update(extra_env)
    print("[i7-failure] running", " ".join(cmd), flush=True)
    subprocess.run(cmd, env=merged, check=True)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Run I7 duplicate/stale/Core-outage E2E scenarios")
    p.add_argument("--scenario", action="append", choices=["duplicate-callback", "stale-attempt", "core-outage-probe"], default=None)
    p.add_argument("--i6-runner", default=env("I7_I6_RUNNER", str(Path(__file__).resolve().parent / "run_core_netty_agent_e2e.py")))
    p.add_argument("--mock-agent-script", default=env("I7_MOCK_AGENT_SCRIPT", str(Path(__file__).resolve().parent / "mock_task_agent.py")))
    p.add_argument("--dry-run", action="store_true", default=env("I7_DRY_RUN", "false").lower() == "true")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    scenarios = args.scenario or ["duplicate-callback", "stale-attempt", "core-outage-probe"]
    runner = Path(args.i6_runner)
    mock = Path(args.mock_agent_script)
    if not runner.exists():
        raise FileNotFoundError(runner)
    if not mock.exists():
        raise FileNotFoundError(mock)

    if args.dry_run:
        print("[i7-failure] dry-run scenarios=", ",".join(scenarios), flush=True)
        return 0

    run_suffix = env("I7_AGENT_ID_SUFFIX", f"{int(time.time())}-{os.getpid()}")
    for index, scenario in enumerate(scenarios, start=1):
        scenario_name = scenario.replace('-', '-')
        common = {
            "I6_MOCK_AGENT_SCRIPT": str(mock),
            # Use a unique Agent id for every runtime gate execution. Reusing a
            # fixed id across failed/retried CI runs can inherit stale reserved
            # capacity, revoked credentials, or open dispatch state from the
            # previous database volume when developers keep the stack around.
            "I6_AGENT_ID": env(f"I7_AGENT_ID_{scenario.upper().replace('-', '_')}", f"agent-i7-{scenario_name}-{run_suffix}-{index}"),
        }
        if scenario == "duplicate-callback":
            run_cmd([sys.executable, str(runner)], {**common, "I7_AGENT_DUPLICATE_RESULT_COUNT": "2"})
        elif scenario == "stale-attempt":
            run_cmd([sys.executable, str(runner)], {**common, "I7_AGENT_STALE_ATTEMPT_RESULT": "true"})
        elif scenario == "core-outage-probe":
            # Non-destructive probe: operators may stop Core before invoking this
            # with I7_EXPECT_CORE_UNAVAILABLE=true. The runtime gate itself does
            # not kill containers, so it is safe for shared dev clusters.
            if env("I7_EXPECT_CORE_UNAVAILABLE", "false").lower() == "true":
                print("[i7-failure] Core outage expectation is enabled; restore Core then run happy path gate", flush=True)
            run_cmd([sys.executable, str(runner)], common)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"[i7-failure] FAILED: command exited {exc.returncode}", file=sys.stderr, flush=True)
        raise SystemExit(exc.returncode)
    except Exception as exc:
        print(f"[i7-failure] FAILED: {exc}", file=sys.stderr, flush=True)
        raise SystemExit(1)
