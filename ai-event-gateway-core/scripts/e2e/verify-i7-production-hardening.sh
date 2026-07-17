#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$SCRIPT_DIR/../control-plane-app" ] || [ -d "$SCRIPT_DIR/../ai-event-gateway-netty-app" ]; then
  ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
else
  ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
fi

fail() { echo "[verify-i7] ERROR: $*" >&2; exit 1; }
exists() { [ -e "$1" ] || fail "Missing required file: $1"; }
contains() { local file="$1" pattern="$2"; grep -qE "$pattern" "$file" || fail "Missing pattern '$pattern' in $file"; }

exists "$ROOT_DIR/docs/i7-production-hardening.md"
exists "$ROOT_DIR/docs/i7-operations-runbook.md"
exists "$ROOT_DIR/docs/i7-metrics-dashboard.md"
exists "$ROOT_DIR/deploy/env/.env.i7-local-integrated.example"
exists "$ROOT_DIR/deploy/env/.env.i7-dev.example"
exists "$ROOT_DIR/deploy/env/.env.i7-prod.example"
exists "$ROOT_DIR/deploy/docker/docker-compose.i7-local-integrated.yml"
exists "$ROOT_DIR/deploy/docker/docker-compose.i7-owner-routing.yml"
exists "$ROOT_DIR/scripts/e2e/run-i7-runtime-gate.sh"
exists "$ROOT_DIR/scripts/e2e/run_i7_owner_routing_e2e.py"
exists "$ROOT_DIR/scripts/e2e/run_i7_failure_scenarios_e2e.py"
exists "$ROOT_DIR/scripts/ops/os-health-check.sh"
exists "$ROOT_DIR/scripts/ops/os-troubleshoot-agent-directory.sh"
exists "$ROOT_DIR/scripts/ops/os-troubleshoot-dispatch.sh"
exists "$ROOT_DIR/scripts/ops/os-troubleshoot-callback.sh"

contains "$ROOT_DIR/docs/i7-production-hardening.md" 'Metrics / Dashboard'
contains "$ROOT_DIR/docs/i7-production-hardening.md" 'E2E runtime CI gate'
contains "$ROOT_DIR/docs/i7-production-hardening.md" 'multi-Netty owner routing'
contains "$ROOT_DIR/docs/i7-operations-runbook.md" 'Gateway / Agent / Dispatch / Callback troubleshooting'
contains "$ROOT_DIR/docs/i7-metrics-dashboard.md" 'dispatch_success_total'
contains "$ROOT_DIR/docs/i7-metrics-dashboard.md" 'callback_rejected_total'
contains "$ROOT_DIR/deploy/env/.env.i7-prod.example" 'CORE_INTERNAL_SECURITY_ENABLED=true'
contains "$ROOT_DIR/deploy/env/.env.i7-prod.example" 'CORE_INTERNAL_PROTECT_API_MUTATIONS=true'
contains "$ROOT_DIR/deploy/env/.env.i7-local-integrated.example" 'GATEWAY_CORE_TASK_CALLBACK_RELAY_ENABLED=true'
contains "$ROOT_DIR/deploy/docker/docker-compose.i7-owner-routing.yml" 'netty-node-001'
contains "$ROOT_DIR/deploy/docker/docker-compose.i7-owner-routing.yml" 'netty-node-002'
contains "$ROOT_DIR/scripts/e2e/run-i7-runtime-gate.sh" 'run_i7_owner_routing_e2e.py'
contains "$ROOT_DIR/scripts/e2e/run-i7-runtime-gate.sh" 'run_i7_failure_scenarios_e2e.py'
contains "$ROOT_DIR/scripts/e2e/run_i7_failure_scenarios_e2e.py" 'duplicate-callback'
contains "$ROOT_DIR/scripts/e2e/run_i7_failure_scenarios_e2e.py" 'stale-attempt'
contains "$ROOT_DIR/scripts/e2e/run_i7_failure_scenarios_e2e.py" 'core-outage-probe'
contains "$ROOT_DIR/scripts/e2e/run_i7_owner_routing_e2e.py" 'ownerGatewayNodeId'
contains "$ROOT_DIR/scripts/e2e/mock_task_agent.py" 'duplicate-result-count'
contains "$ROOT_DIR/scripts/e2e/mock_task_agent.py" 'stale-attempt-result'
contains "$ROOT_DIR/scripts/ops/os-troubleshoot-agent-directory.sh" '/api/gateway-nodes'
contains "$ROOT_DIR/scripts/ops/os-troubleshoot-dispatch.sh" '/api/dispatch-requests'
contains "$ROOT_DIR/scripts/ops/os-troubleshoot-callback.sh" '/internal/control-plane/tasks'

if command -v python3 >/dev/null 2>&1; then
  python3 -m py_compile \
    "$ROOT_DIR/scripts/e2e/mock_task_agent.py" \
    "$ROOT_DIR/scripts/e2e/run_i7_failure_scenarios_e2e.py" \
    "$ROOT_DIR/scripts/e2e/run_i7_owner_routing_e2e.py"
  python3 "$ROOT_DIR/scripts/e2e/run_i7_failure_scenarios_e2e.py" --dry-run >/dev/null
  python3 "$ROOT_DIR/scripts/e2e/run_i7_owner_routing_e2e.py" --dry-run >/dev/null
fi

echo "I7 production hardening static verification passed."
