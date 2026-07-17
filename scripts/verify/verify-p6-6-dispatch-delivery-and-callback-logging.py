#!/usr/bin/env python3
from pathlib import Path

checks = {
    "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchRequestService.java": [
        "dispatch_request_created",
        "dispatch_request_existing",
        "executionPolicy",
    ],
    "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/ScheduledDispatchExecutor.java": [
        "dispatch_executor_batch_executed",
        "dispatch_executor_no_claimable",
        "dispatch_executor_skipped",
    ],
    "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchExecutionService.java": [
        "dispatch_execute_approved_scan_started",
        "dispatch_request_claimed",
        "dispatch_delivery_attempt_started",
        "dispatch_delivery_attempt_result",
        "dispatch_delivery_marked_dispatched",
        "dispatch_delivery_failed",
    ],
    "ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/dispatch/HttpGatewayDispatchClient.java": [
        "gateway_dispatch_http_started",
        "gateway_dispatch_http_response",
        "gateway_dispatch_http_exception",
    ],
    "ai-event-gateway-netty/admin-api/src/main/java/com/opensocket/aievent/gateway/netty/delivery/CommandDeliveryController.java": [
        "netty_delivery_request_received",
        "netty_delivery_request_completed",
    ],
    "ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/delivery/CommandDeliveryService.java": [
        "netty_command_delivery_started",
        "netty_command_delivery_agent_not_connected",
        "netty_command_delivery_completed",
        "netty_command_delivery_exception",
    ],
}

missing = []
for path, needles in checks.items():
    text = Path(path).read_text()
    for needle in needles:
        if needle not in text:
            missing.append(f"{path}: missing {needle}")

if missing:
    raise SystemExit("\n".join(missing))

print("verify-p6-6-dispatch-delivery-and-callback-logging: OK")
