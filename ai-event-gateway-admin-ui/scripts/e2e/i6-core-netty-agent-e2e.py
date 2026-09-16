#!/usr/bin/env python3
"""
I6 E2E runner for OpenSocket / ai-event-gateway.

Expected already-running services:
  - ai-event-gateway-core on I6_CORE_BASE_URL, default http://127.0.0.1:18080
  - ai-event-gateway-netty admin API on I6_NETTY_ADMIN_BASE_URL, default http://127.0.0.1:18081
  - ai-event-gateway-netty TCP listener on I6_GATEWAY_TCP_HOST:I6_GATEWAY_TCP_PORT

The runner starts one local mock task agent, waits until Netty synchronizes it to
Core, pushes a domain event into Core, executes approved dispatch requests, then
waits for Agent ACK/RESULT to close the task in Core.

It intentionally uses only the Python standard library.
"""
from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


def is_standard_envelope(value: Any) -> bool:
    return (
        isinstance(value, dict)
        and isinstance(value.get("code"), str)
        and isinstance(value.get("message"), str)
        and "data" in value
        and isinstance(value.get("timestamp"), str)
    )


def unwrap_standard_envelope(value: Any, description: str) -> Any:
    if not is_standard_envelope(value):
        return value
    if value.get("code") != "OK":
        raise RuntimeError(f"{description} failed with API envelope code={value.get('code')}: {value.get('message')}")
    return value.get("data")


class HttpClient:
    def __init__(self, timeout: float, token: str = "", token_header: str = "X-Cluster-Token") -> None:
        self.timeout = timeout
        self.token = token
        self.token_header = token_header

    def request(self, method: str, url: str, body: Optional[Dict[str, Any]] = None, internal: bool = False) -> Tuple[int, Any]:
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if internal and self.token:
            headers[self.token_header] = self.token
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        description = f"{method} {url}"
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
                if not raw:
                    return resp.status, None
                try:
                    parsed = json.loads(raw)
                    return resp.status, unwrap_standard_envelope(parsed, description)
                except json.JSONDecodeError:
                    return resp.status, raw
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            try:
                parsed = json.loads(raw) if raw else None
                parsed = unwrap_standard_envelope(parsed, description)
            except Exception:
                parsed = raw
            return exc.code, parsed


def log(message: str, **fields: Any) -> None:
    suffix = " ".join(f"{k}={v}" for k, v in fields.items() if v is not None)
    print(f"[i6-e2e] {message}" + (f" {suffix}" if suffix else ""), flush=True)


def require_2xx(status: int, payload: Any, description: str) -> Any:
    if status < 200 or status >= 300:
        raise RuntimeError(f"{description} failed: HTTP {status}, payload={payload}")
    return unwrap_standard_envelope(payload, description)


def wait_until(description: str, timeout: float, interval: float, fn):
    deadline = time.time() + timeout
    last_error: Optional[Exception] = None
    while time.time() < deadline:
        try:
            value = fn()
            if value:
                return value
        except Exception as exc:
            last_error = exc
        time.sleep(interval)
    raise TimeoutError(f"Timed out waiting for {description}" + (f"; last_error={last_error}" if last_error else ""))


def list_payload(payload: Any) -> List[Dict[str, Any]]:
    if payload is None:
        return []
    if isinstance(payload, list):
        return [x for x in payload if isinstance(x, dict)]
    if isinstance(payload, dict):
        for key in ("items", "records", "content", "data"):
            value = payload.get(key)
            if isinstance(value, list):
                return [x for x in value if isinstance(x, dict)]
        return [payload]
    return []


def find_first(records: Iterable[Dict[str, Any]], **criteria: str) -> Optional[Dict[str, Any]]:
    for record in records:
        if all(str(record.get(k)) == str(v) for k, v in criteria.items()):
            return record
    return None


def compact_json(value: Any, limit: int = 2000) -> str:
    try:
        text = json.dumps(value, ensure_ascii=False, sort_keys=True, default=str)
    except Exception:
        text = str(value)
    return text if len(text) <= limit else text[:limit] + "...<truncated>"


def dispatch_matches(record: Dict[str, Any], task_id: Optional[str], agent_id: str) -> bool:
    if not isinstance(record, dict):
        return False
    if task_id and str(record.get("taskId")) == str(task_id):
        return True
    for key in ("agentId", "targetAgentId", "selectedAgentId"):
        if str(record.get(key)) == str(agent_id):
            return True
    command = record.get("command")
    if isinstance(command, dict) and str(command.get("targetAgentId")) == str(agent_id):
        return True
    return False


def fetch_optional(client: HttpClient, url: str) -> Any:
    status, payload = client.request("GET", url)
    if 200 <= status < 300:
        return payload
    return {"httpStatus": status, "payload": payload}


def collect_dispatch_absence_diagnostics(client: HttpClient, args: argparse.Namespace, task_id: Optional[str], response: Dict[str, Any]) -> Dict[str, Any]:
    query = urllib.parse.urlencode({
        "siteId": e2e_site_id(),
        "requiredCapabilities": e2e_required_capabilities(),
        "limit": 20,
    }, doseq=True)
    diagnostics: Dict[str, Any] = {
        "eventResponse": response,
        "dispatchMetadata": fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests/metadata"),
        "coreGatewayAgents": fetch_optional(client, f"{args.core_base_url}/api/gateway-nodes/{urllib.parse.quote(args.gateway_node_id)}/agents"),
        "assignableAgents": fetch_optional(client, f"{args.core_base_url}/api/agents/available?{query}"),
        "recentDispatchRequests": fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests?limit=20"),
    }
    if task_id:
        diagnostics["task"] = fetch_optional(client, f"{args.core_base_url}/api/tasks/{urllib.parse.quote(task_id)}")
        diagnostics["taskDispatchRequests"] = fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests/task/{urllib.parse.quote(task_id)}?limit=20")
        diagnostics["taskDispatchHistory"] = fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests/task/{urllib.parse.quote(task_id)}/history?limit=20")
    return diagnostics


def collect_dispatch_completion_diagnostics(client: HttpClient, args: argparse.Namespace, task_id: Optional[str], dispatch_request_id: Optional[str], execute_payload: Any) -> Dict[str, Any]:
    diagnostics: Dict[str, Any] = {
        "executeApprovedResponse": execute_payload,
        "dispatchMetadata": fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests/metadata"),
        "recentDispatchRequests": fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests?limit=20"),
        "recentCallbacks": fetch_optional(client, f"{args.core_base_url}/internal/control-plane/tasks/callbacks/recent?limit=20"),
        "nettyDeliveryMetrics": fetch_optional(client, f"{args.netty_admin_base_url}/internal/delivery/metrics"),
        "nettyDeliveryHistory": fetch_optional(client, f"{args.netty_admin_base_url}/internal/delivery/history?limit=20"),
        "nettyAdminDeliveryHistory": fetch_optional(client, f"{args.netty_admin_base_url}/admin/delivery/history?limit=20"),
    }
    if dispatch_request_id:
        diagnostics["dispatchRequest"] = fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests/{urllib.parse.quote(dispatch_request_id)}")
    if task_id:
        diagnostics["task"] = fetch_optional(client, f"{args.core_base_url}/api/tasks/{urllib.parse.quote(task_id)}")
        diagnostics["taskCallbacks"] = fetch_optional(client, f"{args.core_base_url}/internal/control-plane/tasks/{urllib.parse.quote(task_id)}/callbacks?limit=50")
        diagnostics["taskDispatchRequests"] = fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests/task/{urllib.parse.quote(task_id)}?limit=20")
        diagnostics["taskDispatchHistory"] = fetch_optional(client, f"{args.core_base_url}/api/dispatch-requests/task/{urllib.parse.quote(task_id)}/history?limit=20")
    return diagnostics


def assert_dispatch_executable(dispatch: Dict[str, Any]) -> None:
    status = str(dispatch.get("status") or "")
    if status in ("APPROVED", "DISPATCHED", "ACKED", "RUNNING", "COMPLETED"):
        return
    raise RuntimeError("Dispatch request was created but is not executable; "
                       f"status={status or '<missing>'}; request={compact_json(dispatch)}")



TASK_SUCCESS_STATUSES = {"SUCCEEDED", "COMPLETED"}


def is_successful_task_status(status: Any) -> bool:
    return str(status or "").upper() in TASK_SUCCESS_STATUSES

def split_csv(value: str) -> List[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def e2e_tenant_id() -> str:
    return env("I6_AGENT_TENANT_ID", env("I6_EVENT_TENANT_ID", "tenant-a"))


def e2e_site_id() -> str:
    return env("I6_AGENT_SITE_ID", env("I6_EVENT_SITE_ID", "LOCAL"))


def e2e_site_name() -> str:
    return env("I6_AGENT_SITE_NAME", e2e_site_id())


def normalize_capability(value: str) -> str:
    return "" if value is None else value.strip().replace("-", "_").replace(".", "_").upper()


def expand_capability_variants(values: Iterable[str]) -> List[str]:
    expanded: List[str] = []
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


def e2e_capabilities() -> List[str]:
    return expand_capability_variants(split_csv(env("I6_AGENT_CAPABILITIES", "INCIDENT_ANALYSIS,TASK_EXECUTION,GENERAL_AGENT")))


def e2e_required_capabilities() -> List[str]:
    values = split_csv(env("I6_REQUIRED_CAPABILITIES", env("I6_EVENT_REQUIRED_CAPABILITIES", "INCIDENT_ANALYSIS")))
    return [normalize_capability(value) for value in values if normalize_capability(value)]


def capability_code(value: Any) -> Optional[str]:
    if value is None:
        return None
    if isinstance(value, dict):
        for key in ("capabilityCode", "code", "name", "skillCode", "capability"):
            candidate = capability_code(value.get(key))
            if candidate:
                return candidate
        return None
    normalized = normalize_capability(str(value))
    return normalized or None


def capability_codes(values: Any) -> List[str]:
    if values is None:
        return []
    records = values if isinstance(values, list) else [values]
    codes: List[str] = []
    seen = set()
    for item in records:
        code = capability_code(item)
        if code and code not in seen:
            seen.add(code)
            codes.append(code)
    return codes


def wait_for_approved_governance_capabilities(client: HttpClient, args: argparse.Namespace) -> Dict[str, Any]:
    required = set(e2e_required_capabilities())

    def profile_ready() -> Optional[Dict[str, Any]]:
        status, payload = client.request("GET", f"{args.core_base_url}/admin/agents/{urllib.parse.quote(args.agent_id)}")
        if status < 200 or status >= 300 or not isinstance(payload, dict):
            return None
        approval_status = str(payload.get("approvalStatus") or payload.get("status") or "")
        enabled = payload.get("enabled") is True
        approved = approval_status.upper() == "APPROVED"
        approved_caps = set(capability_codes(payload.get("capabilities")))
        if approved and enabled and required.issubset(approved_caps):
            return payload
        return None

    return wait_until("approved Core governance capabilities " + ",".join(sorted(required)),
                      args.wait_timeout, 2, profile_ready)


def netty_runtime_session_id(agent: Dict[str, Any]) -> Optional[str]:
    if not isinstance(agent, dict):
        return None
    connection_type = str(agent.get("connectionType") or "").upper()
    if connection_type == "TCP":
        return str(agent.get("connectionId") or "") or None
    if connection_type == "WEBSOCKET":
        return str(agent.get("sessionId") or "") or None
    return str(agent.get("sessionId") or agent.get("connectionId") or "") or None


def wait_for_netty_local_agent_connected(client: HttpClient, args: argparse.Namespace) -> Dict[str, Any]:
    """Wait until the Netty runtime has the real local transport session.

    Core can be pre-warmed with a synthetic directory row, but Netty command
    delivery is backed by AgentRegistry/TcpConnectionRegistry. The I6 gate must
    not create or execute dispatch until Netty itself reports the Agent as
    connected with a concrete connectionId/sessionId.
    """
    def connected() -> Optional[Dict[str, Any]]:
        status, payload = client.request("GET", f"{args.netty_admin_base_url}/api/agents/{urllib.parse.quote(args.agent_id)}")
        if status < 200 or status >= 300 or not isinstance(payload, dict):
            return None
        runtime_session_id = netty_runtime_session_id(payload)
        runtime_status = str(payload.get("status") or "").upper()
        if runtime_session_id and runtime_status not in {"OFFLINE", "TIMEOUT", "ERROR"}:
            return payload
        return None

    return wait_until("Netty local AgentRegistry connection for " + args.agent_id,
                      args.wait_timeout, 1, connected)


def wait_for_core_agent_session(client: HttpClient, args: argparse.Namespace, expected_session_id: str) -> Dict[str, Any]:
    """Wait for Core directory to reflect the same session Netty will deliver to."""
    def synced() -> Optional[Dict[str, Any]]:
        status, payload = client.request("GET", f"{args.core_base_url}/api/gateway-nodes/{urllib.parse.quote(args.gateway_node_id)}/agents")
        if status < 200 or status >= 300:
            return None
        for agent in list_payload(payload):
            if agent.get("agentId") != args.agent_id:
                continue
            runtime_status = str(agent.get("status") or "").upper()
            if str(agent.get("agentSessionId") or "") == str(expected_session_id) and runtime_status not in {"OFFLINE", "TIMEOUT", "ERROR"}:
                return agent
        return None

    return wait_until("Core directory session synchronized from Netty for " + args.agent_id,
                      args.wait_timeout, 1, synced)


def assert_execute_delivered_or_running(client: HttpClient, args: argparse.Namespace, task_id: Optional[str], dispatch_request_id: Optional[str], execute_payload: Any) -> None:
    records = list_payload(execute_payload)
    relevant: List[Dict[str, Any]] = []
    for record in records:
        if dispatch_request_id and str(record.get("dispatchRequestId")) == str(dispatch_request_id):
            relevant.append(record)
            continue
        if str(record.get("agentId") or "") == str(args.agent_id):
            relevant.append(record)
    if not relevant:
        return
    bad_statuses = {"RETRY_WAIT", "RETRY_WAITING", "FAILED", "DEAD_LETTER", "DEAD_LETTERED"}
    for record in relevant:
        status = str(record.get("dispatchStatus") or record.get("status") or "").upper()
        executed = record.get("executed")
        if executed is False or status in bad_statuses:
            diagnostics = collect_dispatch_completion_diagnostics(client, args, task_id, dispatch_request_id, execute_payload)
            raise RuntimeError("execute-approved did not deliver dispatch to a connected Netty Agent; "
                               f"dispatchStatus={status or '<missing>'}; executed={executed}; "
                               f"message={record.get('message')}; diagnostics={compact_json(diagnostics, 8000)}")

def governance_approval_body(args: argparse.Namespace, reason: str) -> Dict[str, Any]:
    capabilities = e2e_capabilities()
    tenant_id = e2e_tenant_id()
    return {
        "agentId": args.agent_id,
        "approvedBy": env("I6_OPERATOR_ID", "i6-runtime-lifecycle-e2e"),
        "tenantId": tenant_id,
        "agentName": args.agent_id,
        "agentType": env("I6_AGENT_TYPE", "OPENCLAW"),
        "ownerTeam": env("I6_AGENT_OWNER_TEAM", "i6-runtime-e2e"),
        "description": "I6/I7 runtime lifecycle E2E agent",
        "comment": reason,
        "capabilities": capabilities,
        "scopes": [{"tenantId": tenant_id, "systemCode": "*", "taskType": "*", "enabled": True}],
        "credentialType": "TOKEN",
        "credentialToken": args.agent_onboarding_token,
        "revokeExisting": True,
        "enabled": True,
    }


def ensure_core_agent_approved(client: HttpClient, args: argparse.Namespace) -> None:
    if not args.agent_onboarding_token:
        raise RuntimeError("AGENT_ONBOARDING_TOKEN/I6 agent onboarding token is required for Core-authorized E2E agents")

    body = {
        "claimedAgentId": args.agent_id,
        "tenantId": e2e_tenant_id(),
        "agentName": args.agent_id,
        "agentType": env("I6_AGENT_TYPE", "OPENCLAW"),
        "submittedMetadata": {"source": "I6/I7 runtime lifecycle E2E", "gatewayNodeId": args.gateway_node_id},
        "evidence": {"generatedBy": "ai-event-gateway-core/scripts/e2e/run_core_netty_agent_e2e.py"},
    }
    status, enrollment = client.request("POST", f"{args.core_base_url}/admin/agent-enrollments", body)
    require_2xx(status, enrollment, "submit E2E agent enrollment")
    enrollment_id = enrollment.get("enrollmentId") if isinstance(enrollment, dict) else None
    approval = governance_approval_body(args, "I6/I7 approved Agent profile for runtime lifecycle E2E")

    approved = False
    if enrollment_id:
        try:
            status, payload = client.request("POST", f"{args.core_base_url}/admin/agent-enrollments/{urllib.parse.quote(str(enrollment_id))}/approve", approval)
            require_2xx(status, payload, "approve E2E agent enrollment")
            approved = True
        except Exception as exc:
            log("enrollment approval fallback to profile approval", reason=exc)
    if not approved:
        status, payload = client.request("POST", f"{args.core_base_url}/admin/agents/{urllib.parse.quote(args.agent_id)}/approve", approval)
        require_2xx(status, payload, "approve E2E agent profile")

    status, payload = client.request("POST", f"{args.core_base_url}/admin/agents/{urllib.parse.quote(args.agent_id)}/credentials/issue", {
        "operatorId": env("I6_OPERATOR_ID", "i6-runtime-lifecycle-e2e"),
        "reason": "I6/I7 runtime lifecycle E2E credential sync",
        "credentialType": "TOKEN",
        "credentialToken": args.agent_onboarding_token,
        "revokeExisting": True,
    })
    require_2xx(status, payload, "issue E2E agent credential")

    # Do not prewarm Core runtime directory here. Prewarming without the real
    # Netty connectionId/sessionId creates a synthetic agentSessionId that can
    # race ahead of Netty registration and cause Core to dispatch to a session
    # that is not deliverable. Runtime prewarm is performed only after Netty
    # reports the concrete local transport session.

    status, payload = client.request("POST", f"{args.core_base_url}/admin/agents/{urllib.parse.quote(args.agent_id)}/enable", {
        "operatorId": env("I6_OPERATOR_ID", "i6-runtime-lifecycle-e2e"),
        "reason": "I6/I7 runtime lifecycle E2E setup",
    })
    require_2xx(status, payload, "enable E2E agent profile")
    log("core governance profile ready", agentId=args.agent_id)


def prewarm_core_agent_directory(client: HttpClient, args: argparse.Namespace, agent_session_id: Optional[str] = None) -> None:
    """Seed Core directory placement/capacity before Netty runtime sync.

    Netty's first connected snapshot and the first heartbeat are asynchronous.
    The I6/I7 lifecycle gate must not ingest an event until Core can actually
    return this agent from the same assignable candidate query used by routing.
    When a real Netty session already exists, preserve it so command delivery is
    still addressed to the active transport session.
    """
    max_concurrent = int(env("I6_AGENT_MAX_CONCURRENT_TASKS", "1"))
    available_slots = int(env("I6_AGENT_AVAILABLE_SLOTS", str(max_concurrent)))
    body = {
        "agentId": args.agent_id,
        "agentType": env("I6_AGENT_TYPE", "OPENCLAW"),
        "ownerGatewayNodeId": args.gateway_node_id,
        "siteId": e2e_site_id(),
        "siteName": e2e_site_name(),
        "status": "IDLE",
        "capabilities": e2e_capabilities(),
        "currentTaskCount": 0,
        "reservedTaskCount": 0,
        "maxConcurrentTasks": max_concurrent,
        "healthScore": 100,
        "availableSlots": available_slots,
        "capabilityProfile": {
            "supportedTaskTypes": e2e_capabilities(),
            "placement": {"siteId": e2e_site_id(), "siteName": e2e_site_name()},
            "maxConcurrentTasks": max_concurrent,
        },
        "runtimeLoad": {
            "activeTasks": 0,
            "maxConcurrentTasks": max_concurrent,
            "availableSlots": available_slots,
            "capacityUtilization": 0.0,
            "draining": False,
        },
    }
    if agent_session_id:
        body["agentSessionId"] = agent_session_id
    status, payload = client.request("POST", f"{args.core_base_url}/api/agents/register", body)
    require_2xx(status, payload, "prewarm Core Agent directory for E2E routing")


def wait_for_assignable_agent_candidate(client: HttpClient, args: argparse.Namespace, agent_session_id: Optional[str]) -> Dict[str, Any]:
    required = e2e_required_capabilities()

    def candidate() -> Optional[Dict[str, Any]]:
        # Re-assert the Core directory snapshot with the active Netty session before
        # probing the exact assignable query. This removes the race where an event
        # is ingested between AGENT_REGISTER and the first full heartbeat sync.
        prewarm_core_agent_directory(client, args, agent_session_id)
        query = urllib.parse.urlencode({
            "siteId": e2e_site_id(),
            "requiredCapabilities": required,
            "limit": 20,
        }, doseq=True)
        status, payload = client.request("GET", f"{args.core_base_url}/api/agents/available?{query}")
        if status < 200 or status >= 300:
            return None
        for record in list_payload(payload):
            if record.get("agentId") == args.agent_id:
                return record
        return None

    return wait_until("assignable Core agent candidate for required capabilities " + ",".join(required),
                      args.wait_timeout, 2, candidate)


def start_mock_agent(args: argparse.Namespace) -> subprocess.Popen:
    script = Path(args.mock_agent_script).resolve()
    if not script.exists():
        raise FileNotFoundError(f"Mock agent script not found: {script}")
    cmd = [
        sys.executable,
        str(script),
        "--gateway-host", args.gateway_tcp_host,
        "--gateway-tcp-port", str(args.gateway_tcp_port),
        "--gateway-node-id", args.gateway_node_id,
        "--agent-id", args.agent_id,
        "--min-dispatches", "1",
        "--exit-after-dispatch", "1",
    ]
    env_map = os.environ.copy()
    env_map["AGENT_ONBOARDING_TOKEN"] = args.agent_onboarding_token
    log("starting mock task agent", command=" ".join(cmd))
    return subprocess.Popen(cmd, env=env_map)


def post_intake_event(client: HttpClient, core_base_url: str, unique_suffix: str) -> Dict[str, Any]:
    body = {
        "tenantId": env("I6_EVENT_TENANT_ID", e2e_tenant_id()),
        "sourceSystem": env("I6_EVENT_SOURCE_SYSTEM", "MES"),
        "siteId": env("I6_EVENT_SITE_ID", e2e_site_id()),
        "plantId": env("I6_EVENT_PLANT_ID", "LOCAL-FAB-01"),
        "objectType": "EQUIPMENT",
        "objectId": f"EQP-I6-{unique_suffix}",
        "eventType": "EQUIPMENT_ALARM",
        "errorCode": "TEMP_HIGH",
        "severity": "CRITICAL",
        "message": f"I6 E2E chamber temperature over threshold {unique_suffix}",
        "attributes": {"i6E2E": True, "uniqueSuffix": unique_suffix},
    }
    status, payload = client.request("POST", f"{core_base_url}/api/events/intake", body)
    return require_2xx(status, payload, "event intake")


def run(args: argparse.Namespace) -> int:
    client = HttpClient(args.http_timeout, args.cluster_internal_token, args.cluster_internal_token_header)
    unique_suffix = str(int(time.time()))

    if args.dry_run:
        log("dry-run enabled; checking local script and endpoint configuration only")
        if not Path(args.mock_agent_script).exists():
            raise FileNotFoundError(args.mock_agent_script)
        print(json.dumps(vars(args), indent=2, sort_keys=True))
        return 0

    log("checking core health", url=f"{args.core_base_url}/actuator/health")
    wait_until("core health", args.wait_timeout, 2, lambda: client.request("GET", f"{args.core_base_url}/actuator/health")[0] < 500)
    log("checking netty health", url=f"{args.netty_admin_base_url}/actuator/health")
    wait_until("netty health", args.wait_timeout, 2, lambda: client.request("GET", f"{args.netty_admin_base_url}/actuator/health")[0] < 500)

    ensure_core_agent_approved(client, args)
    profile = wait_for_approved_governance_capabilities(client, args)
    log("core governance approved capabilities ready",
        agentId=profile.get("agentId"),
        capabilities=capability_codes(profile.get("capabilities")),
        approvalStatus=profile.get("approvalStatus"),
        enabled=profile.get("enabled"))
    agent_proc = start_mock_agent(args)
    try:
        netty_agent = wait_for_netty_local_agent_connected(client, args)
        active_session_id = netty_runtime_session_id(netty_agent)
        log("agent connected in Netty local registry",
            agentId=netty_agent.get("agentId"),
            connectionType=netty_agent.get("connectionType"),
            connectionId=netty_agent.get("connectionId"),
            sessionId=netty_agent.get("sessionId"),
            status=netty_agent.get("status"))

        # Re-assert Core runtime directory with the concrete Netty session before
        # routing. This is intentionally after Netty local readiness, never before.
        prewarm_core_agent_directory(client, args, active_session_id)
        synced_agent = wait_for_core_agent_session(client, args, active_session_id or "")
        log("agent visible in core directory with Netty session", agentId=synced_agent.get("agentId"), session=synced_agent.get("agentSessionId"), status=synced_agent.get("status"))
        assignable_agent = wait_for_assignable_agent_candidate(client, args, active_session_id)
        log("agent assignable in Core directory", agentId=assignable_agent.get("agentId"), siteId=assignable_agent.get("siteId"), capabilities=assignable_agent.get("capabilities"), availableSlots=assignable_agent.get("availableSlots"))

        response = post_intake_event(client, args.core_base_url, unique_suffix)
        task_id = response.get("taskId")
        dispatch_request_id = response.get("dispatchRequestId")
        log("event ingested", taskId=task_id, dispatchRequestId=dispatch_request_id, actions=response.get("actions"), assignmentReason=response.get("assignmentReason"), dispatchReason=response.get("dispatchReason"))

        if (not dispatch_request_id
                and response.get("dispatchSuppressed") is True
                and str(response.get("assignmentStatus") or "").upper() in {"NO_CANDIDATE", "SUPPRESSED", "MANUAL_REVIEW_REQUIRED"}):
            diagnostics = collect_dispatch_absence_diagnostics(client, args, task_id, response)
            raise RuntimeError("Core suppressed dispatch before request creation; "
                               f"assignmentStatus={response.get('assignmentStatus')}; "
                               f"assignmentReason={response.get('assignmentReason')}; "
                               f"diagnostics={compact_json(diagnostics, 6000)}")

        if not dispatch_request_id:
            # Dispatch creation is normally synchronous with task assignment. Poll
            # both by task id and recent requests to tolerate older controller
            # responses, but surface Core's routing/eligibility reason instead of
            # timing out with no actionable diagnostics.
            def latest_dispatch() -> Optional[Dict[str, Any]]:
                records: List[Dict[str, Any]] = []
                if task_id:
                    status, payload = client.request("GET", f"{args.core_base_url}/api/dispatch-requests/task/{urllib.parse.quote(task_id)}?limit=20")
                    if 200 <= status < 300:
                        records.extend(list_payload(payload))
                status, payload = client.request("GET", f"{args.core_base_url}/api/dispatch-requests?limit=50")
                if 200 <= status < 300:
                    records.extend(list_payload(payload))
                for record in records:
                    if dispatch_matches(record, task_id, args.agent_id):
                        return record
                return None
            try:
                dispatch = wait_until("dispatch request creation", args.wait_timeout, 2, latest_dispatch)
            except TimeoutError as exc:
                diagnostics = collect_dispatch_absence_diagnostics(client, args, task_id, response)
                raise TimeoutError(str(exc) + "; diagnostics=" + compact_json(diagnostics, 6000)) from exc
            assert_dispatch_executable(dispatch)
            dispatch_request_id = dispatch.get("dispatchRequestId")
            task_id = task_id or dispatch.get("taskId")

        status, payload = client.request("POST", f"{args.core_base_url}/api/dispatch-requests/execute-approved?limit=20")
        execute_payload = require_2xx(status, payload, "execute approved dispatch requests")
        log("execute-approved called", payload=execute_payload)
        assert_execute_delivered_or_running(client, args, task_id, dispatch_request_id, execute_payload)

        def dispatch_completed() -> Optional[Dict[str, Any]]:
            if dispatch_request_id:
                status, payload = client.request("GET", f"{args.core_base_url}/api/dispatch-requests/{urllib.parse.quote(dispatch_request_id)}")
                if status >= 200 and status < 300 and isinstance(payload, dict) and payload.get("status") == "COMPLETED":
                    return payload
            status, payload = client.request("GET", f"{args.core_base_url}/api/dispatch-requests?limit=20")
            if status >= 200 and status < 300:
                for record in list_payload(payload):
                    if record.get("agentId") == args.agent_id and record.get("status") == "COMPLETED":
                        return record
            return None

        try:
            completed_dispatch = wait_until("dispatch COMPLETED from Agent RESULT callback", args.wait_timeout, 2, dispatch_completed)
        except TimeoutError as exc:
            diagnostics = collect_dispatch_completion_diagnostics(client, args, task_id, dispatch_request_id, execute_payload)
            raise TimeoutError(str(exc) + "; diagnostics=" + compact_json(diagnostics, 8000)) from exc
        task_id = task_id or completed_dispatch.get("taskId")
        log("dispatch completed", dispatchRequestId=completed_dispatch.get("dispatchRequestId"), taskId=task_id)

        if task_id:
            status, task = client.request("GET", f"{args.core_base_url}/api/tasks/{urllib.parse.quote(task_id)}")
            require_2xx(status, task, "query task")
            if isinstance(task, dict) and not is_successful_task_status(task.get("status")):
                raise RuntimeError(f"Expected task success status {sorted(TASK_SUCCESS_STATUSES)}, got {task.get('status')}: {task}")
            log("task completed", taskId=task_id, status=task.get("status") if isinstance(task, dict) else None)

        return 0
    finally:
        if agent_proc.poll() is None:
            agent_proc.send_signal(signal.SIGTERM)
            try:
                agent_proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                agent_proc.kill()


def parse_args() -> argparse.Namespace:
    script_default = Path(__file__).resolve().parent / "mock_task_agent.py"
    p = argparse.ArgumentParser(description="Run I6 Core + Netty + Mock Agent E2E flow")
    p.add_argument("--core-base-url", default=env("I6_CORE_BASE_URL", env("CORE_BASE_URL", "http://127.0.0.1:18080")))
    p.add_argument("--netty-admin-base-url", default=env("I6_NETTY_ADMIN_BASE_URL", env("NETTY_ADMIN_BASE_URL", "http://127.0.0.1:18081")))
    p.add_argument("--gateway-tcp-host", default=env("I6_GATEWAY_TCP_HOST", env("GATEWAY_TCP_HOST", "127.0.0.1")))
    p.add_argument("--gateway-tcp-port", type=int, default=int(env("I6_GATEWAY_TCP_PORT", env("GATEWAY_TCP_PORT", "19090"))))
    p.add_argument("--gateway-node-id", default=env("I6_GATEWAY_NODE_ID", env("GATEWAY_NODE_ID", "gateway-node-001")))
    p.add_argument("--agent-id", default=env("I6_AGENT_ID", "agent-i6-001"))
    p.add_argument("--agent-onboarding-token", default=env("AGENT_ONBOARDING_TOKEN", ""))
    p.add_argument("--cluster-internal-token", default=env("CLUSTER_INTERNAL_TOKEN", env("I6_CLUSTER_INTERNAL_TOKEN", "local-i6-internal-token")))
    p.add_argument("--cluster-internal-token-header", default=env("I6_CLUSTER_INTERNAL_TOKEN_HEADER", "X-Cluster-Token"))
    p.add_argument("--mock-agent-script", default=env("I6_MOCK_AGENT_SCRIPT", str(script_default)))
    p.add_argument("--http-timeout", type=float, default=float(env("I6_HTTP_TIMEOUT_SECONDS", "10")))
    p.add_argument("--wait-timeout", type=float, default=float(env("I6_WAIT_TIMEOUT_SECONDS", "120")))
    p.add_argument("--dry-run", action="store_true", default=env("I6_DRY_RUN", "false").lower() == "true")
    return p.parse_args()


if __name__ == "__main__":
    try:
        sys.exit(run(parse_args()))
    except Exception as exc:
        print(f"[i6-e2e] FAILED: {exc}", file=sys.stderr, flush=True)
        sys.exit(1)
