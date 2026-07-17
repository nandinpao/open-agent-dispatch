#!/usr/bin/env python3
"""
I7 multi-Netty owner-routing E2E runner.

The test expects two Netty nodes and one Core instance. It starts a mock agent on
the owner node TCP port, waits until Core sees that agent owned by the expected
gateway node, then runs the normal I6 dispatch flow against that owner.

It intentionally avoids any non-standard Python dependency.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Optional


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


def http_json(url: str, timeout: float = 10.0) -> tuple[int, Any]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw) if raw else None
    except Exception:
        return 0, None


def list_payload(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [x for x in payload if isinstance(x, dict)]
    if isinstance(payload, dict):
        for key in ("items", "records", "content", "data"):
            if isinstance(payload.get(key), list):
                return [x for x in payload[key] if isinstance(x, dict)]
        return [payload]
    return []


def wait_for_owner(core_base_url: str, owner_gateway_node_id: str, agent_id: str, timeout: float) -> dict[str, Any]:
    deadline = time.time() + timeout
    url = f"{core_base_url}/api/gateway-nodes/{urllib.parse.quote(owner_gateway_node_id)}/agents"
    while time.time() < deadline:
        status, payload = http_json(url)
        if 200 <= status < 300:
            for record in list_payload(payload):
                if str(record.get("agentId")) == agent_id and str(record.get("ownerGatewayNodeId", owner_gateway_node_id)) == owner_gateway_node_id:
                    return record
        time.sleep(2)
    raise TimeoutError(f"Agent {agent_id} did not appear under owner {owner_gateway_node_id}")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Run I7 two-Netty owner-routing E2E flow")
    p.add_argument("--core-base-url", default=env("I7_CORE_BASE_URL", env("I6_CORE_BASE_URL", "http://127.0.0.1:18080")))
    p.add_argument("--owner-gateway-node-id", default=env("I7_OWNER_GATEWAY_NODE_ID", "gateway-node-002"))
    p.add_argument("--owner-gateway-tcp-host", default=env("I7_OWNER_GATEWAY_TCP_HOST", "127.0.0.1"))
    p.add_argument("--owner-gateway-tcp-port", type=int, default=int(env("I7_OWNER_GATEWAY_TCP_PORT", "19092")))
    p.add_argument("--agent-id", default=env("I7_OWNER_AGENT_ID", "agent-i7-owner-001"))
    p.add_argument("--wait-timeout", type=float, default=float(env("I7_WAIT_TIMEOUT_SECONDS", "120")))
    p.add_argument("--i6-runner", default=env("I7_I6_RUNNER", str(Path(__file__).resolve().parent / "run_core_netty_agent_e2e.py")))
    p.add_argument("--dry-run", action="store_true", default=env("I7_DRY_RUN", "false").lower() == "true")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    runner = Path(args.i6_runner)
    if not runner.exists():
        raise FileNotFoundError(runner)
    if args.dry_run:
        print(json.dumps(vars(args), indent=2, sort_keys=True))
        return 0

    env_map = os.environ.copy()
    env_map.update({
        "I6_CORE_BASE_URL": args.core_base_url,
        "I6_GATEWAY_NODE_ID": args.owner_gateway_node_id,
        "I6_GATEWAY_TCP_HOST": args.owner_gateway_tcp_host,
        "I6_GATEWAY_TCP_PORT": str(args.owner_gateway_tcp_port),
        "I6_AGENT_ID": args.agent_id,
    })
    subprocess.run([sys.executable, str(runner)], env=env_map, check=True)
    owner_record = wait_for_owner(args.core_base_url, args.owner_gateway_node_id, args.agent_id, args.wait_timeout)
    print("[i7-owner-routing] owner verified", json.dumps(owner_record, sort_keys=True), flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"[i7-owner-routing] FAILED: command exited {exc.returncode}", file=sys.stderr, flush=True)
        raise SystemExit(exc.returncode)
    except Exception as exc:
        print(f"[i7-owner-routing] FAILED: {exc}", file=sys.stderr, flush=True)
        raise SystemExit(1)
