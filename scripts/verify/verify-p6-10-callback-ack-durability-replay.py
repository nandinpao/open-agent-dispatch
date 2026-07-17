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

agent = 'ai-event-gateway-netty/scripts/netty-tcp-agent-client.js'
for token in [
    'explicitCoreAcceptedStatus',
    'provisionalRelayStatus',
    'agent_pending_callback_retained',
    'terminal_callback_waiting_for_core_acceptance',
    'agent_pending_callback_replay_started',
    'agent_pending_callback_replayed',
    'agent_pending_callback_core_accepted',
    'AGENT_CALLBACK_REPLAY_INTERVAL_MS',
    'AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS',
    "replayPendingCallbacks('interval')",
]:
    require(agent, token)

cluster = 'ai-event-gateway-netty/scripts/cluster-run-many-agents.sh'
for token in [
    'OPENDISPATCH_AGENT_PENDING_CALLBACK_DIR',
    '${LOG_DIR}/pending-callbacks',
    'AGENT_CALLBACK_REPLAY_INTERVAL_MS',
    'AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS',
]:
    require(cluster, token)

# Netty should expose relay-level ack status rather than masking it as RELAY_QUEUED.
for path in [
    'ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/tcp/TcpMessageProcessor.java',
    'ai-event-gateway-netty/transport-server/src/main/java/com/opensocket/aievent/gateway/netty/websocket/WebSocketMessageProcessor.java',
]:
    require(path, 'return callbackRelayResult.status();')

require('docs/P6_10_CALLBACK_ACK_DURABILITY_REPLAY/README.md', 'RELAY_QUEUED')

startup = ROOT / 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java'
if not startup.exists():
    errors.append('missing file: ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java')
else:
    startup_text = startup.read_text(errors='ignore')
    if 'phase=P6_10' not in startup_text and 'phase=P6_11' not in startup_text and 'phase=P6_12' not in startup_text:
        errors.append('DispatchDiagnosticsStartupLogger.java: missing phase=P6_10, phase=P6_11, or phase=P6_12')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java', 'agent_pending_callback_replayed')

if errors:
    print('P6.10 verification failed:')
    for error in errors:
        print(' -', error)
    sys.exit(1)
print('P6.10 verification passed: terminal callbacks are retained on relay-level ack and replayed periodically until explicit Core acceptance.')
