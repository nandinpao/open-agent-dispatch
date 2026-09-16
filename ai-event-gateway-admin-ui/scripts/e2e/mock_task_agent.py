#!/usr/bin/env python3
"""
I6 TCP Mock Task Agent for Core -> Netty -> Agent -> Core E2E verification.

The agent:
  1. opens one long-lived TCP connection to ai-event-gateway-netty;
  2. sends AGENT_REGISTER and periodic AGENT_HEARTBEAT;
  3. waits for TASK_DISPATCH envelopes delivered by Netty;
  4. echoes Core dispatch context in TASK_ACK and TASK_RESULT callbacks.

This script uses only the Python standard library so it can run on a developer
machine, inside a minimal container, or from CI without extra packages.
"""
from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import threading
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")



def normalize_capability(value: str) -> str:
    return "" if value is None else value.strip().replace("-", "_").replace(".", "_").upper()


def expand_capability_variants(values):
    expanded = []
    seen = set()
    for raw in values or []:
        if raw is None:
            continue
        text = str(raw).strip()
        if not text:
            continue
        for candidate in (normalize_capability(text), text):
            if candidate and candidate not in seen:
                seen.add(candidate)
                expanded.append(candidate)
    return expanded


def envelope(message_type: str, source: str, target: str, payload: Dict[str, Any], message_id: Optional[str] = None) -> Dict[str, Any]:
    return {
        "messageId": message_id or f"msg-{uuid.uuid4()}",
        "messageType": message_type,
        "source": source,
        "target": target,
        "timestamp": utc_now(),
        "payload": payload,
    }


class MockTaskAgent:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.sock: Optional[socket.socket] = None
        self.stop = threading.Event()
        self.capabilities = expand_capability_variants(self.args.capability)
        self.handled_dispatches = 0
        self.last_task_id: Optional[str] = None

    def log(self, message: str, **fields: Any) -> None:
        suffix = " ".join(f"{k}={v}" for k, v in fields.items() if v is not None)
        print(f"[mock-task-agent] {message}" + (f" {suffix}" if suffix else ""), flush=True)

    def send(self, msg: Dict[str, Any]) -> None:
        if self.sock is None:
            return
        raw = json.dumps(msg, separators=(",", ":"), ensure_ascii=False) + "\n"
        self.sock.sendall(raw.encode("utf-8"))
        self.log("sent", messageType=msg.get("messageType"), taskId=(msg.get("payload") or {}).get("taskId"))

    def register(self) -> None:
        payload = {
            "agentId": self.args.agent_id,
            "agentType": self.args.agent_type,
            "connectionType": "TCP",
            "capabilities": self.capabilities,
            "metadata": {
                "i6E2E": "true",
                "maxConcurrentTasks": str(self.args.max_concurrent_tasks),
                "siteId": os.getenv("I6_AGENT_SITE_ID", os.getenv("I6_EVENT_SITE_ID", "LOCAL")),
                "siteName": os.getenv("I6_AGENT_SITE_NAME", os.getenv("I6_AGENT_SITE_ID", os.getenv("I6_EVENT_SITE_ID", "LOCAL"))),
                # Keep token aliases for older gateway builds and external agent SDKs.
                # AgentRegistry sanitizes sensitive metadata before exposing runtime snapshots.
                "onboardingToken": self.args.onboarding_token,
                "credentialToken": self.args.onboarding_token,
                "agentToken": self.args.onboarding_token,
                "token": self.args.onboarding_token,
            },
            "onboardingToken": self.args.onboarding_token,
        }
        self.send(envelope("AGENT_REGISTER", self.args.agent_id, self.args.gateway_node_id, payload))

    def heartbeat_payload(self) -> Dict[str, Any]:
        active_tasks = 0 if self.last_task_id is None else 1
        return {
            "agentId": self.args.agent_id,
            "status": "IDLE",
            "currentTaskId": self.last_task_id,
            "sentAt": utc_now(),
            "capabilities": self.capabilities,
            "capabilityProfile": {
                "maxConcurrentTasks": self.args.max_concurrent_tasks,
                "supportedTaskTypes": self.capabilities,
                "placement": {
                    "siteId": os.getenv("I6_AGENT_SITE_ID", os.getenv("I6_EVENT_SITE_ID", "LOCAL")),
                    "siteName": os.getenv("I6_AGENT_SITE_NAME", os.getenv("I6_AGENT_SITE_ID", os.getenv("I6_EVENT_SITE_ID", "LOCAL"))),
                },
            },
            "runtimeLoad": {
                "activeTasks": active_tasks,
                "maxConcurrentTasks": self.args.max_concurrent_tasks,
                "availableSlots": max(0, self.args.max_concurrent_tasks - active_tasks),
                "capacityUtilization": 0.0 if active_tasks == 0 else 1.0 / max(1, self.args.max_concurrent_tasks),
                "draining": False,
            },
        }

    def send_heartbeat(self) -> None:
        self.send(envelope("AGENT_HEARTBEAT", self.args.agent_id, self.args.gateway_node_id, self.heartbeat_payload()))

    def heartbeat_loop(self) -> None:
        while not self.stop.wait(self.args.heartbeat_interval):
            self.send_heartbeat()

    def read_loop(self) -> None:
        assert self.sock is not None
        buffer = b""
        while not self.stop.is_set():
            data = self.sock.recv(65536)
            if not data:
                raise ConnectionError("gateway closed TCP connection")
            buffer += data
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                if not line.strip():
                    continue
                raw_line = line.decode("utf-8", errors="replace")
                try:
                    msg = json.loads(raw_line)
                except Exception as exc:  # pragma: no cover - runtime diagnostic
                    self.log("invalid-json-from-gateway", error=exc, raw=raw_line[:1000])
                    continue
                self.handle_gateway_message(msg)

    def handle_gateway_message(self, msg: Dict[str, Any]) -> None:
        message_type = msg.get("messageType")
        payload = msg.get("payload") or {}
        self.log("received",
                 messageType=message_type,
                 taskId=payload.get("taskId"),
                 gatewayStatus=payload.get("status"),
                 originalMessageType=payload.get("originalMessageType"),
                 errorCode=payload.get("errorCode") or payload.get("code"),
                 reason=payload.get("reason") or payload.get("message"))
        if message_type == "ERROR":
            self.log("gateway-error-detail", payload=json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
            # Registration/auth failures cannot recover by sending more heartbeats.
            # Keep the connection open for diagnostics unless fail-fast is requested.
            if os.getenv("I6_AGENT_FAIL_FAST_ON_ERROR", "true").lower() in ("1", "true", "yes", "y"):
                self.stop.set()
        if message_type == "TASK_DISPATCH" or msg.get("eventType") == "agent.task.assigned":
            self.handle_task_dispatch(msg, payload)

    def handle_task_dispatch(self, msg: Dict[str, Any], payload: Dict[str, Any]) -> None:
        task_id = payload.get("taskId")
        if not task_id:
            self.log("dispatch-missing-task-id", payload=payload)
            return
        self.last_task_id = task_id
        context = {
            "taskId": task_id,
            "agentId": self.args.agent_id,
            "dispatchRequestId": payload.get("dispatchRequestId"),
            "assignmentId": payload.get("assignmentId"),
            "attemptNo": payload.get("attemptNo", 1),
            "dispatchToken": payload.get("dispatchToken"),
            "fencingToken": payload.get("fencingToken"),
            "ownerGatewayNodeId": payload.get("ownerGatewayNodeId") or self.args.gateway_node_id,
            "agentSessionId": payload.get("agentSessionId"),
        }
        self.send(envelope(
            "TASK_ACK",
            self.args.agent_id,
            self.args.gateway_node_id,
            {**context, "accepted": True, "message": "I6 mock agent accepted task", "occurredAt": utc_now()},
        ))
        time.sleep(self.args.result_delay)
        result_payload = {
            **context,
            "resultStatus": "SUCCESS",
            "message": "I6/I7 mock agent completed task",
            "occurredAt": utc_now(),
            "result": {
                "summary": "mock result from I6/I7 E2E agent",
                "receivedCommandId": msg.get("messageId"),
                "input": payload.get("input"),
            },
        }
        self.send(envelope(
            "TASK_RESULT",
            self.args.agent_id,
            self.args.gateway_node_id,
            result_payload,
        ))
        for index in range(max(0, self.args.duplicate_result_count)):
            duplicate_payload = dict(result_payload)
            duplicate_payload["message"] = f"I7 duplicate result injection #{index + 1}"
            duplicate_payload["occurredAt"] = utc_now()
            duplicate_payload["result"] = {**result_payload.get("result", {}), "duplicateInjection": index + 1}
            self.send(envelope("TASK_RESULT", self.args.agent_id, self.args.gateway_node_id, duplicate_payload))
        if self.args.stale_attempt_result:
            stale_payload = dict(result_payload)
            stale_payload["attemptNo"] = int(context.get("attemptNo") or 1) - 1
            stale_payload["dispatchToken"] = "stale-" + str(context.get("dispatchToken") or "missing-token")
            stale_payload["message"] = "I7 stale attempt result injection"
            stale_payload["occurredAt"] = utc_now()
            stale_payload["result"] = {**result_payload.get("result", {}), "staleAttemptInjection": True}
            self.send(envelope("TASK_RESULT", self.args.agent_id, self.args.gateway_node_id, stale_payload))
        self.handled_dispatches += 1
        if self.args.exit_after_dispatch and self.handled_dispatches >= self.args.exit_after_dispatch:
            self.log("exit-after-dispatch", count=self.handled_dispatches)
            self.stop.set()

    def run_once(self) -> int:
        self.log("connecting", host=self.args.gateway_host, port=self.args.gateway_tcp_port, agent=self.args.agent_id)
        self.sock = socket.create_connection((self.args.gateway_host, self.args.gateway_tcp_port), timeout=self.args.connect_timeout)
        self.sock.settimeout(None)
        heartbeat_thread = threading.Thread(target=self.heartbeat_loop, daemon=True)
        heartbeat_thread.start()
        self.register()
        # Send a full heartbeat immediately after registration so Core receives
        # runtimeLoad/capabilityProfile before the E2E runner posts the event.
        # TCP preserves ordering, so this heartbeat follows AGENT_REGISTER.
        self.send_heartbeat()
        try:
            self.read_loop()
        finally:
            self.stop.set()
            try:
                self.sock.close()
            except Exception:
                pass
        return 0 if self.handled_dispatches >= self.args.min_dispatches else 2


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Run I6 TCP mock task agent")
    p.add_argument("--gateway-host", default=os.getenv("I6_GATEWAY_TCP_HOST", os.getenv("GATEWAY_HOST", "127.0.0.1")))
    p.add_argument("--gateway-tcp-port", type=int, default=int(os.getenv("I6_GATEWAY_TCP_PORT", os.getenv("GATEWAY_TCP_PORT", "19090"))))
    p.add_argument("--gateway-node-id", default=os.getenv("I6_GATEWAY_NODE_ID", os.getenv("GATEWAY_NODE_ID", "gateway-node-001")))
    p.add_argument("--agent-id", default=os.getenv("I6_AGENT_ID", "agent-i6-001"))
    p.add_argument("--agent-type", default=os.getenv("I6_AGENT_TYPE", "OPENCLAW"))
    p.add_argument("--capability", action="append", default=os.getenv("I6_AGENT_CAPABILITIES", "INCIDENT_ANALYSIS,TASK_EXECUTION,GENERAL_AGENT").split(","))
    p.add_argument("--max-concurrent-tasks", type=int, default=int(os.getenv("I6_AGENT_MAX_CONCURRENT_TASKS", "1")))
    p.add_argument("--onboarding-token", default=os.getenv("AGENT_ONBOARDING_TOKEN", ""))
    p.add_argument("--heartbeat-interval", type=float, default=float(os.getenv("I6_AGENT_HEARTBEAT_INTERVAL_SECONDS", "5")))
    p.add_argument("--connect-timeout", type=float, default=float(os.getenv("I6_AGENT_CONNECT_TIMEOUT_SECONDS", "10")))
    p.add_argument("--result-delay", type=float, default=float(os.getenv("I6_AGENT_RESULT_DELAY_SECONDS", "0.5")))
    p.add_argument("--min-dispatches", type=int, default=int(os.getenv("I6_AGENT_MIN_DISPATCHES", "1")))
    p.add_argument("--exit-after-dispatch", type=int, default=int(os.getenv("I6_AGENT_EXIT_AFTER_DISPATCH", "1")))
    p.add_argument("--duplicate-result-count", type=int, default=int(os.getenv("I7_AGENT_DUPLICATE_RESULT_COUNT", "0")), help="I7 fault injection: send duplicate TASK_RESULT callbacks after the first result")
    p.add_argument("--stale-attempt-result", action="store_true", default=os.getenv("I7_AGENT_STALE_ATTEMPT_RESULT", "false").lower() == "true", help="I7 fault injection: send one stale-attempt TASK_RESULT callback")
    return p.parse_args()


if __name__ == "__main__":
    try:
        sys.exit(MockTaskAgent(parse_args()).run_once())
    except KeyboardInterrupt:
        sys.exit(130)
    except Exception as exc:
        print(f"[mock-task-agent] fatal error={exc}", file=sys.stderr, flush=True)
        sys.exit(1)
