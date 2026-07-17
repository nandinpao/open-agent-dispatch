#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

log() {
  echo "==> $*"
}

log "Toolchain report"
if [[ -x scripts/dev/check-toolchain.sh ]]; then
  scripts/dev/check-toolchain.sh --soft
fi

log "Static release verification"
python3 scripts/verify/verify-release.py

log "Script syntax checks"
find scripts -type f \( -name '*.sh' -o -name '*.bash' \) -print0 | while IFS= read -r -d '' script; do
  bash -n "$script"
done
find scripts ai-event-gateway-admin-ui/scripts ai-event-gateway-netty/scripts -type f -name '*.mjs' -print0 2>/dev/null | while IFS= read -r -d '' script; do
  node --check "$script"
done

log "Runtime lifecycle dry-run"
P26_DRY_RUN=true I7_DRY_RUN=true I6_DRY_RUN=true GATEWAY_AGENT_AUTHORIZATION_ENABLED=true \
  bash scripts/acceptance/runtime-lifecycle-e2e.sh --dry-run --scenarios happy,duplicate,stale

log "Release gate dry-run passed. Run 'make ci-release' on a Java 25 + Docker host for the full runtime gate."
