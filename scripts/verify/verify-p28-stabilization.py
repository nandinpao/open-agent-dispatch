#!/usr/bin/env python3
"""P28 stabilization verifier.

This check guards the release-gate governance added after P27:
- one standard verify entrypoint includes remediation P6-P12 plus release P15-P22 checks;
- old phase verifiers are current and do not depend on visible UI phase wording;
- toolchain and release dry-run entrypoints exist;
- runtime governance E2E covers revoke/reconnect denial;
- API envelope errors are observable by envelope code, not only HTTP status;
- Admin UI no longer exposes TODO placeholders in key task/remediation views.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.is_file():
        fail(f"Missing required file: {relative}")
    return path


def require_text(relative: str, *needles: str) -> str:
    path = require_file(relative)
    text = path.read_text(encoding="utf-8", errors="ignore")
    for needle in needles:
        if needle not in text:
            fail(f"{relative} missing required text: {needle}")
    return text


def optional_text(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def warn(message: str) -> None:
    print(f"[WARN] {message}", file=sys.stderr)


def forbid_text(relative: str, *needles: str) -> None:
    text = require_file(relative).read_text(encoding="utf-8", errors="ignore")
    for needle in needles:
        if needle in text:
            fail(f"{relative} still contains forbidden visible placeholder text: {needle}")


def main() -> int:
    require_text("Makefile", "check-toolchain:", "ci-release-dry-run:", "scripts/release/ci-release-dry-run.sh")
    require_text("scripts/dev/check-toolchain.sh", "REQUIRED_JAVA_MAJOR", "REQUIRED_NODE_MAJOR", "RECOMMENDED_NODE_MAJOR", "Docker Compose v2")

    java_version = optional_text(".java-version").strip()
    tool_versions = optional_text(".tool-versions")
    pom = optional_text("pom.xml")
    if java_version != "25" and "java 25" not in tool_versions and "<maven.compiler.release>25</maven.compiler.release>" not in pom:
        fail("Missing Java 25 toolchain marker: expected .java-version=25, .tool-versions java 25, or pom.xml maven.compiler.release=25")
    if not java_version:
        warn(".java-version is missing; continuing because another Java 25 marker is present. Add .java-version for asdf/jenv users.")
    if not tool_versions:
        warn(".tool-versions is missing; continuing because another Java 25 marker is present. Add .tool-versions for asdf users.")

    require_text(".devcontainer/devcontainer.json", "OpenDispatch Java 25 / Node 22", "check-toolchain.sh --soft")
    require_text("scripts/release/ci-release-dry-run.sh", "verify-release.py", "runtime-lifecycle-e2e.sh --dry-run")

    verify_release = require_text("scripts/verify/verify-release.py", "verify-p6-remediation-workflow.py", "verify-p28-stabilization.py")
    for script in [
        "verify-p6-remediation-workflow.py",
        "verify-p7-remediation-persistence.py",
        "verify-p8-remediation-execution.py",
        "verify-p9-remediation-idempotency.py",
        "verify-p10-remediation-execution-lease.py",
        "verify-p11-stale-lease-recovery.py",
        "verify-p12-remediation-metrics-alerting.py",
        "verify-p15-release-automation.py",
        "verify-p16-offline-release-readiness.py",
        "verify-p17-release-operations.py",
        "verify-p18-model-boundaries.py",
        "verify-p19-api-response-contract.py",
        "verify-p20-api-contract-alignment.py",
        "verify-p21-api-runtime-acceptance.py",
        "verify-p22-docker-image-policy.py",
    ]:
        if script not in verify_release:
            fail(f"verify-release.py does not include {script}")

    require_text("scripts/acceptance/agent-governance-lifecycle-e2e.mjs", "revoke -> authorization denial", "expectRegistrationRejected", "/revoke")
    canonical_runner = require_file("ai-event-gateway-core/scripts/e2e/run_core_netty_agent_e2e.py").read_text(encoding="utf-8", errors="ignore")
    for duplicate_runner in [
        "ai-event-gateway-core/scripts/e2e/i6-core-netty-agent-e2e.py",
        "ai-event-gateway-admin-ui/scripts/e2e/run_core_netty_agent_e2e.py",
        "ai-event-gateway-admin-ui/scripts/e2e/i6-core-netty-agent-e2e.py",
    ]:
        duplicate_text = require_file(duplicate_runner).read_text(encoding="utf-8", errors="ignore")
        if duplicate_text != canonical_runner:
            fail(f"{duplicate_runner} has drifted from canonical Core i6 runner")
    require_text("ai-event-gateway-core/scripts/e2e/run_core_netty_agent_e2e.py",
                 "collect_dispatch_absence_diagnostics",
                 "collect_dispatch_completion_diagnostics",
                 "wait_for_approved_governance_capabilities",
                 "wait_for_netty_local_agent_connected",
                 "wait_for_core_agent_session",
                 "execute-approved did not deliver dispatch to a connected Netty Agent",
                 "Core suppressed dispatch before request creation",
                 "Dispatch request was created but is not executable",
                 "TASK_SUCCESS_STATUSES",
                 "is_successful_task_status",
                 "/api/dispatch-requests/task/{urllib.parse.quote(task_id)}",
                 "/internal/delivery/history?limit=20")
    require_text("ai-event-gateway-core/scripts/e2e/run_i7_failure_scenarios_e2e.py", "I7_AGENT_ID_SUFFIX", "agent-i7-", "os.getpid()")
    require_text("deploy/docker-compose.ci.yml", "CORE_DEFAULT_TASK_CAPABILITIES")
    require_text("ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/skill/AgentDispatchSkillEvaluationService.java",
                 "approvedCapabilities(agent.getAgentId(), profile)",
                 "assignmentService.findAgentCapabilities(agentId)",
                 "governed capability assignments are the working source of truth")
    require_text("ai-event-gateway-core/task-orchestration/src/test/java/com/opensocket/aievent/core/routing/SkillAwareRoutingDecisionServiceTest.java",
                 "shouldRouteWhenApprovedCapabilitiesAreLoadedFromGovernanceCapabilityRows",
                 "governance.replaceCapabilities")
    require_text("ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/HttpGatewayDispatchClient.java",
                 'payload.put("agentId", targetAgentId(request))',
                 'payload.put("targetAgentId", targetAgentId(request))',
                 "unwrapStandardApiEnvelope",
                 'payload.put("fencingToken", command.getFencingToken())')
    require_text("ai-event-gateway-core/execution-control/src/test/java/com/opensocket/aievent/core/dispatch/HttpGatewayDispatchClientContractTest.java",
                 'containsEntry("agentId", "agent-001")',
                 "shouldUnwrapStandardApiEnvelopeForNettyDeliveryResponse")
    require_text("ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelay.java",
                 'copyFirstPresent(payload, request, "fencingToken"',
                 '"assignmentFencingToken"')
    require_text("ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/NettyDispatchCommand.java",
                 "private String fencingToken",
                 "getFencingToken")
    require_text("ai-event-gateway-core/scripts/e2e/mock_task_agent.py",
                 '"fencingToken": payload.get("fencingToken")')
    require_text("ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/delivery/CommandDeliveryService.java",
                 'stringValue(payload.get("targetAgentId"))',
                 'stringValue(payload.get("selectedAgentId"))')
    require_text("ai-event-gateway-netty/transport-server/src/test/java/com/opensocket/aievent/gateway/netty/delivery/CommandDeliveryServiceTest.java",
                 "shouldAcceptTargetAgentIdAliasForBackwardCompatibleCoreDeliveryPayload")
    require_text("scripts/ci/local-ci.sh", "for service in core netty admin-ui")

    require_text("ai-event-gateway-admin-ui/lib/api/adminApi.ts",
                 "nettyApiPost<LoginResponse>(adminEndpoints.authLogin",
                 "nettyApiGet<AdminUser>(adminEndpoints.authMe",
                 "nettyApiPost<LoginResponse>(adminEndpoints.authRefresh",
                 "nettyApiPost<CommandResult>(adminEndpoints.authLogout")
    require_text("ai-event-gateway-admin-ui/app/api/admin/[...path]/route.ts",
                 "Legacy Admin API compatibility route",
                 "proxyToBackend(request, context, 'netty')")
    require_text("scripts/ci/local-smoke.sh",
                 "OPENDISPATCH_PUBLIC_HOST",
                 "is_loopback_endpoint",
                 'CORE_URL="${PUBLIC_SCHEME}://${PUBLIC_HOST}:${CORE_HTTP_PORT:-18080}"',
                 'NETTY_TCP_HOST="${PUBLIC_HOST}"',
                 "SMOKE_TCP_CHECK_MODE",
                 "tcp_probe_python",
                 "SMOKE_ENABLE_DEV_TCP_FALLBACK")
    require_text("scripts/local-smoke.sh",
                 "SMOKE_TCP_CHECK_MODE",
                 "tcp_probe_python",
                 "SMOKE_ENABLE_DEV_TCP_FALLBACK")
    require_text("scripts/acceptance/runtime-lifecycle-e2e.sh",
                 "OPENDISPATCH_PUBLIC_HOST",
                 "is_loopback_endpoint",
                 'GATEWAY_TCP_HOST="${PUBLIC_HOST}"')
    require_text("scripts/cluster-run-many-agents.sh",
                 "single-Netty development",
                 "GATEWAY_CLUSTER_NODE_COUNT",
                 "OPENDISPATCH_PUBLIC_HOST",
                 "ai-event-gateway-netty/scripts/cluster-run-many-agents.sh")
    require_text("ai-event-gateway-netty/scripts/cluster-run-many-agents.sh",
                 "CORE_BOOTSTRAP_AGENTS",
                 "core-bootstrap-cluster-agents.js",
                 "netty-tcp-agent-client.js",
                 "AGENT_ONBOARDING_TOKEN",
                 "log_files")
    require_text("deploy/docker-compose.ci.yml",
                 "GATEWAY_AGENT_AUTHORIZATION_ENABLED: ${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-false}",
                 "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED: ${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED:-true}")
    require_text("deploy/docker-compose.local.yml",
                 "GATEWAY_AGENT_AUTHORIZATION_ENABLED: ${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-false}",
                 "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED: ${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED:-true}")
    require_text("scripts/ci/local-ci.sh",
                 "CI_LOCAL_AGENT_AUTH_MODE",
                 'GATEWAY_AGENT_AUTHORIZATION_ENABLED="false"',
                 'GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED="true"')
    require_text("scripts/ci/local-cd.sh",
                 "CI_LOCAL_AGENT_AUTH_MODE",
                 'GATEWAY_AGENT_AUTHORIZATION_ENABLED="false"',
                 'GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED="true"')
    require_text("deploy/env/.env.local.ci",
                 "GATEWAY_AGENT_AUTHORIZATION_ENABLED=false",
                 "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED=true",
                 "CI_LOCAL_AGENT_AUTH_MODE=transport")
    require_text("deploy/env/.env.local.example",
                 "GATEWAY_AGENT_AUTHORIZATION_ENABLED=false",
                 "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED=true",
                 "CI_LOCAL_AGENT_AUTH_MODE=transport")
    require_text("scripts/agents/run-manual-mock-agent.sh",
                 "delegating to original cluster simulator",
                 "CORE_BOOTSTRAP_AGENTS",
                 "CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL",
                 "CORE_BOOTSTRAP_VERIFY_AUTHORIZATION",
                 "AGENTS_PER_NODE",
                 "scripts/cluster-run-many-agents.sh")
    require_text("ai-event-gateway-core/scripts/e2e/mock_task_agent.py",
                 "gateway-error-detail",
                 "credentialToken",
                 "I6_AGENT_FAIL_FAST_ON_ERROR",
                 "errorCode=payload.get")
    require_text("deploy/env/.env.baofire.local.example",
                 "OPENDISPATCH_PUBLIC_HOST=baofire.com",
                 "ADMIN_UI_URL=http://baofire.com:3000",
                 "NEXT_PUBLIC_CORE_API_BASE_URL=/core-api")
    require_text("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationWorkflowExecutionPolicy.java", "WORKFLOW_EXECUTION_LEASE_DURATION", "isCompletedActionStatus", "isTerminalWorkflowStatus")
    require_text("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java", "opendispatch.api.envelope.total", 'tag("plane", "core")', 'tag("code", normalizeMetricTag(code))')
    require_text("ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/api/GatewayApiExceptionHandler.java", "opendispatch.api.envelope.total", 'tag("plane", "netty")', 'tag("code", normalizeMetricTag(code))')

    forbid_text("ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx", "TODO 15-E")
    forbid_text("ai-event-gateway-admin-ui/components/tasks/TaskFailureQueuePanel.tsx", "TODO 15-E")
    forbid_text("ai-event-gateway-admin-ui/app/tasks/failure-queue/page.tsx", "TODO 15-E")

    # Documentation is intentionally non-blocking for main program verification.
    # The release gate must validate executable contracts, source files and scripts;
    # missing or relocated Markdown files should not fail `make verify`.
    release_gates_doc = optional_text("docs/CURRENT_RELEASE_GATES.md")
    if release_gates_doc:
        for needle in ["make verify", "make ci-release", "make ci-release-dry-run"]:
            if needle not in release_gates_doc:
                warn(f"docs/CURRENT_RELEASE_GATES.md missing recommended text: {needle}")
    else:
        warn("docs/CURRENT_RELEASE_GATES.md is missing; documentation is advisory and not part of the executable gate.")

    p28_doc = optional_text("docs/P28_STABILIZATION_VERIFICATION_GOVERNANCE.md")
    if p28_doc:
        for needle in ["P28-A", "P28-G", "opendispatch.api.envelope.total"]:
            if needle not in p28_doc:
                warn(f"docs/P28_STABILIZATION_VERIFICATION_GOVERNANCE.md missing recommended text: {needle}")
    else:
        warn("docs/P28_STABILIZATION_VERIFICATION_GOVERNANCE.md is missing; documentation is advisory and not part of the executable gate.")

    print("P28 stabilization / verification governance check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
