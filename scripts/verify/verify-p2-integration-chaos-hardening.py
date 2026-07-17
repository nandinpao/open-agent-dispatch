#!/usr/bin/env python3
"""Verify P2 integration chaos hardening artifacts are present.

This is a source-level guard. Runtime/JUnit execution still belongs to Maven/CI.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED = {
    "docs/P2_INTEGRATION_CHAOS_HARDENING.md": [
        "AdapterAction idempotency",
        "Runtime reconnect",
        "Metrics cardinality",
    ],
    "ai-event-gateway-core/adapter-action/src/test/java/com/opensocket/aievent/core/action/AdapterActionHighConcurrencyP2Test.java": [
        "saveNewOrGetByIdempotencyKeyShouldRemainSingleUnderConcurrentWriters",
        "concurrentWorkersShouldClaimEachActionAtMostOnce",
    ],
    "ai-event-gateway-netty/gateway-core/src/test/java/com/opensocket/aievent/gateway/netty/agent/AgentRegistryP2ConcurrencyTest.java": [
        "concurrentAgentRegistrationsShouldKeepOneRuntimeRecordPerAgent",
        "staleDisconnectFromOldSessionShouldNotOfflineReconnectedAgent",
    ],
    "ai-event-gateway-netty/transport-server/src/test/java/com/opensocket/aievent/gateway/netty/delivery/CommandDeliveryTrackerP2LoadTest.java": [
        "concurrentDeliveryCompletionsShouldLeaveNoActiveLeaksAndBoundHistory",
        "activeDeliveries",
        "historySize",
    ],
    "ai-event-gateway-core/observability/src/test/java/com/opensocket/aievent/core/ObservabilityMetricsTest.java": [
        "dispatchGatewayStatusMetricsShouldBucketHttpCodesToBoundCardinality",
        "intakeMetricsShouldNotUsePerEventOrPerObjectIdentifiersAsTags",
    ],
    "ai-event-gateway-core/observability/src/main/java/com/opensocket/aievent/core/observability/CoreMetricsService.java": [
        "gatewayStatusTag",
        "http_",
    ],
}


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    for relative_path, markers in REQUIRED.items():
        path = ROOT / relative_path
        if not path.is_file():
            fail(f"Missing required file: {relative_path}")
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                fail(f"Missing marker '{marker}' in {relative_path}")

    print("P2 integration chaos hardening verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
