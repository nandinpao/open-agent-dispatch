#!/usr/bin/env python3
"""P0 release-gate verification for UI contract drift and production fail-closed guards."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def exists(path: str) -> Path:
    p = ROOT / path
    if not p.is_file():
        fail(f"Missing required file: {path}")
    return p


def contains(path: str, text: str) -> None:
    data = exists(path).read_text(encoding="utf-8")
    if text not in data:
        fail(f"Missing required text in {path}: {text}")


def not_contains(path: str, text: str) -> None:
    data = exists(path).read_text(encoding="utf-8")
    if text in data:
        fail(f"Unexpected text in {path}: {text}")


def main() -> int:
    node_view = "ai-event-gateway-admin-ui/components/cluster/ClusterNodeDetailView.tsx"
    core_validator = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidator.java"
    core_validator_test = "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidatorTest.java"
    core_prod = "ai-event-gateway-core/control-plane-app/src/main/resources/application-prod.yml"
    netty_validator = "ai-event-gateway-netty/gateway-app/src/main/java/com/opensocket/aievent/gateway/netty/NettyProductionDeploymentValidator.java"
    netty_validator_test = "ai-event-gateway-netty/gateway-app/src/test/java/com/opensocket/aievent/gateway/netty/NettyProductionDeploymentValidatorTest.java"
    release = "scripts/verify/verify-release.py"

    for path in (node_view, core_validator, core_validator_test, core_prod, netty_validator, netty_validator_test, release):
        exists(path)

    contains(node_view, 'title="Recent Gateway Relay Diagnostics"')
    contains(node_view, "下方 Recent Gateway Relay Diagnostics 沒資料")
    not_contains(node_view, 'title="Gateway Relay Diagnostics"')

    for text in (
        "validateProductionInternalSecurity",
        "validateProductionDispatchClientBoundary",
        "dispatch.client.internal-token/DISPATCH_INTERNAL_TOKEN",
        "isUnsafeProductionEndpoint",
        'normalized.startsWith("<")',
        'normalized.endsWith(">")',
        'normalized.contains("replace-with")',
    ):
        contains(core_validator, text)

    for text in (
        "shouldRejectAngleBracketInternalTokenInProdProfile",
        "shouldRejectPlaceholderDispatchInternalTokenInProdProfile",
        "shouldRejectLocalhostDispatchGatewayEndpointInProdProfile",
    ):
        contains(core_validator_test, text)

    for text in (
        "enabled: ${CORE_INTERNAL_SECURITY_ENABLED:true}",
        "protect-api-mutations: ${CORE_INTERNAL_PROTECT_API_MUTATIONS:true}",
        "permit-actuator-health-info: ${CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO:false}",
        "internal-token: ${DISPATCH_INTERNAL_TOKEN}",
        "default-gateway-base-url: ${DISPATCH_DEFAULT_GATEWAY_BASE_URL}",
    ):
        contains(core_prod, text)

    for text in (
        'normalized.startsWith("<")',
        'normalized.endsWith(">")',
        'normalized.contains("replace-with")',
        "validateCoreIntegrationTokens",
        "gateway.core-task-callback-relay.require-dispatch-context=true",
    ):
        contains(netty_validator, text)

    for text in (
        "shouldRejectAngleBracketAdminApiTokenInProdProfile",
        "shouldRejectReplaceWithAgentOnboardingTokenInProdProfile",
    ):
        contains(netty_validator_test, text)

    contains(release, "verify-p0-production-release-gate.py")

    print("P0 production release gate verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
