#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors = []

def require(path, token):
    p = ROOT / path
    if not p.exists():
        errors.append(f"missing file: {path}")
        return
    text = p.read_text(errors='ignore')
    if token not in text:
        errors.append(f"{path}: missing token {token!r}")

# Agent process log markers
agent = 'ai-event-gateway-netty/scripts/netty-tcp-agent-client.js'
for token in [
    'agent_runtime_connected',
    'agent_register_accepted',
    'agent_task_dispatch_received',
    'agent_task_worker_started',
    'agent_task_ack_callback_started',
    'agent_task_progress_callback_started',
    'agent_task_result_callback_started',
    'agent_callback_frame_sent',
    'agent_gateway_ack_received',
    'agent_pending_callback_saved',
    'agent_pending_callback_accepted',
    'agent_task_worker_completed',
]:
    require(agent, token)

# Agent logs collected under .local/opendispatch-logs/agents by default.
require('ai-event-gateway-netty/scripts/cluster-run-many-agents.sh', 'LOG_ROOT="${OPENDISPATCH_LOG_ROOT:-${PROJECT_ROOT}/.local/opendispatch-logs}"')
require('ai-event-gateway-netty/scripts/cluster-run-many-agents.sh', 'LOG_DIR="${AGENT_LOG_DIR:-${OPENDISPATCH_AGENT_LOG_DIR:-${LOG_ROOT}/agents}}"')
require('scripts/diagnostics/collect-dispatch-logs.sh', '"${LOG_ROOT}/agents"')
require('scripts/diagnostics/collect-dispatch-logs.sh', 'LEGACY_AGENT_LOG_DIR')
require('scripts/diagnostics/collect-dispatch-logs.sh', 'pending-callbacks')

# Netty callback relay markers.
relay = 'ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelay.java'
for token in [
    'netty_callback_relay_received',
    'netty_callback_relay_submit_started',
    'netty_callback_relay_submitted',
    'netty_callback_relay_response',
    'netty_callback_relay_failed',
    'netty_callback_relay_exception',
]:
    require(relay, token)

require('ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/tcp/TcpMessageProcessor.java', 'netty_callback_frame_received')
require('ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/tcp/TcpMessageProcessor.java', 'netty_callback_gateway_ack_returned')

# Core callback inbox markers.
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskCallbackController.java', 'callback_inbox_http_received')
callback_service = 'ai-event-gateway-core/execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java'
for token in [
    'callback_inbox_processing_started',
    'callback_inbox_dispatch_resolved',
    'callback_inbox_rejected',
    'callback_inbox_ignored',
    'callback_task_transition_applied',
    'callback_terminal_event_published',
    'callback_inbox_accepted',
]:
    require(callback_service, token)

# Issue / adapter action markers.
for path, tokens in {
    'ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/TaskTerminalEventHandler.java': ['issue_sync_terminal_event_received'],
    'ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/AdapterActionService.java': ['issue_sync_evaluation_started','issue_sync_action_orchestrated','adapter_action_requested_event_published','issue_sync_read_model_saved'],
    'ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/AdapterActionExecutionService.java': ['adapter_action_execute_pending_scan','adapter_action_execution_started','adapter_action_executor_invoked','adapter_action_execution_completed','adapter_action_execution_failed','issue_sync_read_model_saved'],
}.items():
    for token in tokens:
        require(path, token)

# dispatch-trace must capture callback/action markers.
logback = 'ai-event-gateway-core/control-plane-app/src/main/resources/logback-spring.xml'
for token in ['com.opensocket.aievent.core.callback', 'com.opensocket.aievent.core.api.TaskCallbackController', 'com.opensocket.aievent.core.action']:
    require(logback, token)

require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java', 'phase=P6_10')

if errors:
    print('P6.9 verification failed:')
    for error in errors:
        print(' -', error)
    sys.exit(1)
print('P6.9 verification passed: Agent worker, Netty relay, Core callback inbox, and issue sync trace markers are present.')
