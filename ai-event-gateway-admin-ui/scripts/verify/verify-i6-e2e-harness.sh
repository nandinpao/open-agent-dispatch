#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$SCRIPT_DIR/../ai-event-gateway-core-app" ] || [ -d "$SCRIPT_DIR/../ai-event-gateway-netty-app" ]; then
  ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
else
  ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
fi
CORE_DIR="${CORE_DIR:-}"
NETTY_DIR="${NETTY_DIR:-}"

fail() { echo "[verify-i6] ERROR: $*" >&2; exit 1; }
contains() { local file="$1" pattern="$2"; grep -qE "$pattern" "$file" || fail "Missing pattern '$pattern' in $file"; }
exists() { [ -e "$1" ] || fail "Missing required file: $1"; }

# Auto-detect when run from a project or from the I6 bundle.
if [ -z "$CORE_DIR" ]; then
  if [ -d "$ROOT_DIR/ai-event-gateway-core-app" ]; then
    CORE_DIR="$ROOT_DIR"
  elif [ -d "$ROOT_DIR/../core" ]; then
    CORE_DIR="$(find "$ROOT_DIR/../core" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
  elif [ -d "$ROOT_DIR/core" ]; then
    CORE_DIR="$(find "$ROOT_DIR/core" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
  fi
fi
if [ -z "$NETTY_DIR" ]; then
  if [ -d "$ROOT_DIR/ai-event-gateway-netty-app" ]; then
    NETTY_DIR="$ROOT_DIR"
  elif [ -d "$ROOT_DIR/../netty" ]; then
    NETTY_DIR="$(find "$ROOT_DIR/../netty" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
  elif [ -d "$ROOT_DIR/netty" ]; then
    NETTY_DIR="$(find "$ROOT_DIR/netty" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
  fi
fi

# When run from the top-level I6 bundle, the project zips may not be extracted.
# In that case the harness files are still verifiable.
HARNESS_DIR="$ROOT_DIR"
exists "$HARNESS_DIR/scripts/e2e/mock_task_agent.py"
exists "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py"
exists "$HARNESS_DIR/scripts/e2e/run-i6-core-netty-agent-e2e.sh"
exists "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml"
exists "$HARNESS_DIR/deploy/env/.env.i6-e2e.example"
exists "$HARNESS_DIR/docs/i6-end-to-end-integration-test.md"

contains "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" 'TASK_DISPATCH'
contains "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" 'TASK_ACK'
contains "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" 'TASK_RESULT'
contains "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" 'dispatchRequestId'
contains "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" 'assignmentId'
contains "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" 'attemptNo'
contains "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" 'dispatchToken'

contains "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py" '/api/gateway-nodes/.*/agents'
contains "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py" '/api/events/intake'
contains "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py" '/api/dispatch-requests/execute-approved'
contains "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py" 'COMPLETED'
contains "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py" 'mock_task_agent.py'

contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'postgres:'
contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'redis:'
contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'core:'
contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'netty:'
contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'mock-agent:'
contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'GATEWAY_CORE_DIRECTORY_SYNC_ENABLED'
contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'GATEWAY_CORE_TASK_CALLBACK_RELAY_ENABLED'
contains "$HARNESS_DIR/deploy/docker/docker-compose.i6-e2e.yml" 'DISPATCH_DEFAULT_GATEWAY_BASE_URL'
contains "$HARNESS_DIR/deploy/env/.env.i6-e2e.example" 'CORE_INTERNAL_SECURITY_ENABLED=true'
contains "$HARNESS_DIR/deploy/env/.env.i6-e2e.example" 'GATEWAY_CORE_TASK_CALLBACK_RELAY_REQUIRE_DISPATCH_CONTEXT=true'

if [ -n "$CORE_DIR" ] && [ -d "$CORE_DIR" ]; then
  exists "$CORE_DIR/scripts/e2e/i6-core-netty-agent-e2e.py"
  exists "$CORE_DIR/scripts/e2e/run-i6-core-netty-agent-e2e.sh"
  exists "$CORE_DIR/deploy/docker/docker-compose.i6-e2e.yml"
  exists "$CORE_DIR/deploy/env/.env.i6-e2e.example"
  exists "$CORE_DIR/docs/i6-end-to-end-integration-test.md"
  contains "$CORE_DIR/ai-event-gateway-core-execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchProperties.java" '/internal/delivery/agents/\{agentId\}/commands'
  contains "$CORE_DIR/ai-event-gateway-core-execution-control/src/main/java/com/opensocket/aievent/core/dispatch/DispatchProperties.java" 'X-Cluster-Token'
  contains "$CORE_DIR/ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRequestClassifier.java" 'GATEWAY'
fi

if [ -n "$NETTY_DIR" ] && [ -d "$NETTY_DIR" ]; then
  exists "$NETTY_DIR/scripts/e2e/i6-core-netty-agent-e2e.py"
  exists "$NETTY_DIR/scripts/e2e/i6-mock-task-agent.py"
  exists "$NETTY_DIR/scripts/e2e/run-i6-core-netty-agent-e2e.sh"
  exists "$NETTY_DIR/deploy/mock-agent/i6-mock-task-agent.py"
  exists "$NETTY_DIR/deploy/docker/docker-compose.i6-e2e.yml"
  exists "$NETTY_DIR/deploy/env/.env.i6-e2e.example"
  exists "$NETTY_DIR/docs/i6-end-to-end-integration-test.md"
  contains "$NETTY_DIR/ai-event-gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/config/CoreTaskCallbackRelayProperties.java" '/internal/control-plane/tasks/\{taskId\}/ack'
  contains "$NETTY_DIR/ai-event-gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/config/CoreTaskCallbackRelayProperties.java" '/internal/control-plane/tasks/\{taskId\}/result'
  contains "$NETTY_DIR/ai-event-gateway-netty-server/src/main/java/com/opensocket/aievent/gateway/netty/callback/TaskCallbackRelay.java" 'callbackUrl' 
  contains "$NETTY_DIR/ai-event-gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/outbound/CoreOutboundDispatcher.java" 'ArrayBlockingQueue'
  contains "$NETTY_DIR/ai-event-gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/config/CoreDirectorySyncProperties.java" '/internal/gateway-nodes'
fi

# Validate Python harness syntax when Python is available.
if command -v python3 >/dev/null 2>&1; then
  python3 -m py_compile "$HARNESS_DIR/scripts/e2e/mock_task_agent.py" "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py"
  python3 "$HARNESS_DIR/scripts/e2e/run_core_netty_agent_e2e.py" --dry-run >/dev/null
fi

echo "I6 Core + Netty + Mock Agent E2E harness static verification passed."
