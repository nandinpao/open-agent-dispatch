#!/usr/bin/env python3
"""P4 Observability / SLO dashboard / runtime metrics hardening verifier."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def require_file(path: str) -> str:
    file_path = ROOT / path
    if not file_path.is_file():
        fail(f"Missing required file: {path}")
    return file_path.read_text(encoding="utf-8")


def require_contains(path: str, *needles: str) -> None:
    content = require_file(path)
    for needle in needles:
        if needle not in content:
            fail(f"Missing `{needle}` in {path}")


def main() -> int:
    require_contains(
        "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/observability/OperationalSummary.java",
        "private Map<String, Object> sloMetrics",
        "getSloMetrics()",
        "setSloMetrics"
    )
    require_contains(
        "ai-event-gateway-core/observability/src/main/java/com/opensocket/aievent/core/observability/ObservabilityProperties.java",
        "private SloMetrics sloMetrics",
        "callbackLagWarning",
        "dispatchRetryWarningThreshold",
        "adapterFailureRatioWarning",
        "routingNoCandidateRatioWarning"
    )
    require_contains(
        "ai-event-gateway-core/observability/src/main/java/com/opensocket/aievent/core/observability/OperationalSummaryService.java",
        "summary.setSloMetrics(sloMetrics(summary, limit))",
        "callbackLagMetrics",
        "dispatchReliabilityMetrics",
        "adapterExecutorMetrics",
        "routingMetrics",
        "RoutingDecisionStatus.NO_CANDIDATE"
    )
    require_contains(
        "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreDashboardController.java",
        "OperationalSummaryService operationalSummaryService",
        "operationalSummaryService.summary().getSloMetrics()",
        "Map<String, Object> operationalSlo"
    )
    require_contains(
        "ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/admin/runtime/dto/RuntimeSloSnapshotResponse.java",
        "deliveryBacklog",
        "gatewayRelayBacklog",
        "callbackRelay",
        "alerts"
    )
    require_contains(
        "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/admin/runtime/AdminRuntimeController.java",
        '@GetMapping("/slo")',
        "RuntimeSloSnapshotResponse",
        "gatewayRelayBacklog",
        "callbackRelayFailureRatio"
    )
    require_contains(
        "ai-event-gateway-admin-ui/components/dashboard/DualPlaneDashboard.tsx",
        "Operational SLO Snapshot",
        "Gateway Runtime SLO Snapshot",
        "callback lag",
        "gateway relay backlog",
        "runtimeSlo"
    )
    require_contains(
        "ai-event-gateway-admin-ui/lib/types/core.ts",
        "operationalSlo?: Record<string, unknown>"
    )
    require_contains(
        "ai-event-gateway-admin-ui/lib/types/nettyRuntime.ts",
        "NettyRuntimeSloSnapshot",
        "gatewayRelayBacklog"
    )
    require_contains(
        "ai-event-gateway-admin-ui/lib/api/endpoints.ts",
        "runtimeSlo: '/api/admin/runtime/slo'"
    )
    require_contains(
        "ai-event-gateway-admin-ui/hooks/useDualDashboard.ts",
        "getRuntimeSlo()",
        "runtimeSlo"
    )
    require_contains(
        "ai-event-gateway-core/observability/src/test/java/com/opensocket/aievent/core/observability/OperationalSummaryServiceTest.java",
        "callbackLaggedSeconds",
        "routingDecision(RoutingDecisionStatus.NO_CANDIDATE)",
        "adapterAction(AdapterActionStatus.FAILED)",
        "getSloMetrics()"
    )
    require_contains(
        "docs/P4_OBSERVABILITY_SLO_HARDENING.md",
        "callback lag",
        "dispatch retry/dead-letter",
        "adapter executor failure",
        "routing no-candidate",
        "gateway relay backlog"
    )
    print("[OK] P4 observability / SLO dashboard / runtime metrics hardening verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
