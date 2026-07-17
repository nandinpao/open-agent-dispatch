#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
CI_PROJECT_NAME="${CI_PROJECT_NAME:-opendispatch-ci}"
CI_COMPOSE_FILE="${CI_COMPOSE_FILE:-deploy/docker-compose.ci.yml}"
CI_ENV_FILE="${CI_ENV_FILE:-deploy/env/.env.local.ci}"
REMOVE_VOLUMES="${REMOVE_VOLUMES:-true}"

log() {
  echo "==> $*"
}

warn() {
  echo "[WARN] $*" >&2
}

compose_ci_down() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 \
      && [[ -f "$CI_COMPOSE_FILE" && -f "$CI_ENV_FILE" ]]; then
    if [[ "${REMOVE_VOLUMES}" == "true" ]]; then
      docker compose -p "$CI_PROJECT_NAME" --env-file "$CI_ENV_FILE" -f "$CI_COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
    else
      docker compose -p "$CI_PROJECT_NAME" --env-file "$CI_ENV_FILE" -f "$CI_COMPOSE_FILE" down --remove-orphans >/dev/null 2>&1 || true
    fi
  fi
}

maven_clean() {
  if [[ -f pom.xml && -f ai-event-gateway-core/pom.xml && -f ai-event-gateway-netty/pom.xml ]]; then
    if command -v mvn >/dev/null 2>&1; then
      log "Cleaning Maven modules through root aggregator"
      mvn -q -f pom.xml clean
    else
      warn "mvn is not available; falling back to deleting Maven target directories"
      find ai-event-gateway-core ai-event-gateway-netty -name target -type d -prune -exec rm -rf {} +
    fi
  fi
}

admin_ui_clean() {
  local admin_dir="ai-event-gateway-admin-ui"
  [[ -d "$admin_dir" ]] || return 0

  log "Cleaning Admin UI generated artifacts"
  bash "$ROOT_DIR/scripts/ci/admin-ui-clean-generated.sh" --output-dir "$CI_OUTPUT_DIR" || true
  rm -rf "$admin_dir/node_modules"
  find "$admin_dir" -name tsconfig.tsbuildinfo -type f -delete
}

log "Stopping local CI runtime processes"
bash "$ROOT_DIR/scripts/ci/local-admin-ui-host.sh" stop --output-dir "$CI_OUTPUT_DIR" >/dev/null 2>&1 || true
compose_ci_down

log "Removing CI output directory"
rm -rf "${CI_OUTPUT_DIR}"

maven_clean
admin_ui_clean

log "Removing common generated metadata and source-package pollutants"
if [[ -x "$ROOT_DIR/scripts/ci/clean-artifacts.sh" ]]; then
  bash "$ROOT_DIR/scripts/ci/clean-artifacts.sh" --best-effort
else
  find . -name tsconfig.tsbuildinfo -type f -delete
  find . -name __pycache__ -type d -prune -exec rm -rf {} +
  find . -name __MACOSX -type d -prune -exec rm -rf {} +
  find . -name '.DS_Store' -type f -delete
  find . -name '._*' -type f -delete
fi

log "Local CI Docker volumes cleaned when REMOVE_VOLUMES=true."
log "Local CI, Maven, and Admin UI generated artifacts cleaned."
log "Source-package pollutants cleaned."
