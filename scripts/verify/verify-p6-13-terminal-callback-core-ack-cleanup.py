#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

checks = []

def require(path, contains):
    text = (ROOT / path).read_text()
    missing = [item for item in contains if item not in text]
    if missing:
        raise SystemExit(f"{path} missing required content: {missing}")

require('ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelayResult.java', [
    'coreAccepted(String taskId, String callbackType, int httpStatus)',
    'CALLBACK_CORE_ACCEPTED',
    'coreRejected(String taskId, String callbackType, int httpStatus, String message)',
    'CALLBACK_CORE_REJECTED',
])
require('ai-event-gateway-netty/gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/config/CoreTaskCallbackRelayProperties.java', [
    'synchronousTerminalCallbacks',
    'synchronousTerminalCallbacks()',
    'setSynchronousTerminalCallbacks(boolean synchronousTerminalCallbacks)',
])
require('ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelay.java', [
    'relayTerminalCallbackSynchronously',
    'netty_callback_relay_sync_started',
    'netty_callback_relay_sync_response',
    'TaskCallbackRelayResult.coreAccepted',
    'isTerminalCallback(envelope.messageType())',
])
require('ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/tcp/TcpMessageProcessor.java', [
    'CALLBACK_CORE_ACCEPTED',
    'CORE_CALLBACK_REJECTED',
])
require('ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/websocket/WebSocketMessageProcessor.java', [
    'CALLBACK_CORE_ACCEPTED',
    'CORE_CALLBACK_REJECTED',
])
require('ai-event-gateway-netty/scripts/netty-tcp-agent-client.js', [
    'explicitCoreAcceptedStatus(status)',
    'agent_pending_callback_core_accepted',
])
for path in [
    'deploy/docker-compose.local.yml',
    'deploy/docker-compose.ci.yml',
    'deploy/docker-compose.release.yml',
    'ai-event-gateway-netty/gateway-app/src/main/resources/application.yml',
    'ai-event-gateway-netty/gateway-app/src/main/resources/application-prod.yml',
]:
    require(path, ['GATEWAY_CORE_TASK_CALLBACK_RELAY_SYNCHRONOUS_TERMINAL_CALLBACKS'])
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java', [
    'phase=P6_13',
    'netty_callback_relay_sync_started',
])
print('P6.13 terminal callback Core ACK cleanup verification passed')
