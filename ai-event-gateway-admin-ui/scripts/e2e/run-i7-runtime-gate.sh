#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIOS="${I7_RUNTIME_SCENARIOS:-happy,duplicate,stale,owner}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

echo "[i7-runtime-gate] scenarios=${SCENARIOS}"

contains_scenario() {
  case ",${SCENARIOS}," in
    *",$1,"*) return 0 ;;
    *) return 1 ;;
  esac
}

if contains_scenario happy; then
  "$PYTHON_BIN" "$SCRIPT_DIR/run_core_netty_agent_e2e.py"
fi
if contains_scenario duplicate; then
  "$PYTHON_BIN" "$SCRIPT_DIR/run_i7_failure_scenarios_e2e.py" --scenario duplicate-callback
fi
if contains_scenario stale; then
  "$PYTHON_BIN" "$SCRIPT_DIR/run_i7_failure_scenarios_e2e.py" --scenario stale-attempt
fi
if contains_scenario outage; then
  "$PYTHON_BIN" "$SCRIPT_DIR/run_i7_failure_scenarios_e2e.py" --scenario core-outage-probe
fi
if contains_scenario owner; then
  "$PYTHON_BIN" "$SCRIPT_DIR/run_i7_owner_routing_e2e.py"
fi

echo "[i7-runtime-gate] passed"
