#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADMIN_UI_DIR="${ROOT_DIR}/ai-event-gateway-admin-ui"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
CI_PROJECT_NAME="${CI_PROJECT_NAME:-opendispatch-ci}"
CI_COMPOSE_FILE="${CI_COMPOSE_FILE:-deploy/docker-compose.ci.yml}"
CI_ENV_FILE="${CI_ENV_FILE:-deploy/env/.env.local.ci}"
ALLOW_DOCKER_REPAIR="${ALLOW_DOCKER_REPAIR:-true}"
BEST_EFFORT=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --admin-ui-dir)
      ADMIN_UI_DIR="$2"
      shift 2
      ;;
    --output-dir)
      CI_OUTPUT_DIR="$2"
      shift 2
      ;;
    --project)
      CI_PROJECT_NAME="$2"
      shift 2
      ;;
    --compose-file)
      CI_COMPOSE_FILE="$2"
      shift 2
      ;;
    --env-file)
      CI_ENV_FILE="$2"
      shift 2
      ;;
    --no-docker-repair)
      ALLOW_DOCKER_REPAIR=false
      shift
      ;;
    --best-effort)
      BEST_EFFORT=true
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

mkdir -p "${CI_OUTPUT_DIR}/reports"
REPORT_FILE="${CI_OUTPUT_DIR}/reports/admin-ui-clean-generated.txt"
: > "$REPORT_FILE"

log() {
  echo "[admin-ui-clean] $*" | tee -a "$REPORT_FILE"
}

warn() {
  echo "[admin-ui-clean][WARN] $*" | tee -a "$REPORT_FILE" >&2
}

if [[ ! -d "$ADMIN_UI_DIR" ]]; then
  log "Admin UI directory does not exist; skipping: $ADMIN_UI_DIR"
  exit 0
fi

relative_admin_dir() {
  python3 - "$ROOT_DIR" "$ADMIN_UI_DIR" <<'PY'
import os, sys
root = os.path.realpath(sys.argv[1])
admin = os.path.realpath(sys.argv[2])
print(os.path.relpath(admin, root))
PY
}

ADMIN_UI_RELATIVE_DIR="$(relative_admin_dir)"

stop_admin_container_if_possible() {
  [[ "$ALLOW_DOCKER_REPAIR" == "true" ]] || return 0
  command -v docker >/dev/null 2>&1 || return 0
  docker compose version >/dev/null 2>&1 || return 0
  [[ -f "${ROOT_DIR}/${CI_COMPOSE_FILE}" && -f "${ROOT_DIR}/${CI_ENV_FILE}" ]] || return 0

  log "Stopping CI Admin UI container before host-side cleanup, if it exists."
  docker compose -p "$CI_PROJECT_NAME" --env-file "${ROOT_DIR}/${CI_ENV_FILE}" -f "${ROOT_DIR}/${CI_COMPOSE_FILE}" stop admin-ui >/dev/null 2>&1 || true
}

remove_with_host_permissions() {
  local path="$1"
  [[ -e "$path" || -L "$path" ]] || return 0
  rm -rf "$path" 2>>"$REPORT_FILE"
}

remove_generated_artifacts() {
  local failed=false
  local paths=(
    "$ADMIN_UI_DIR/.next"
    "$ADMIN_UI_DIR/.next-ci"
    "$ADMIN_UI_DIR/.next-local"
    "$ADMIN_UI_DIR/.next-release"
    "$ADMIN_UI_DIR/out"
    "$ADMIN_UI_DIR/dist"
    "$ADMIN_UI_DIR/coverage"
    "$ADMIN_UI_DIR/.turbo"
    "$ADMIN_UI_DIR/tsconfig.tsbuildinfo"
  )
  for path in "${paths[@]}"; do
    if ! remove_with_host_permissions "$path"; then
      failed=true
    fi
  done

  if [[ "$failed" == "true" ]]; then
    return 1
  fi
  return 0
}

repair_with_docker_root() {
  [[ "$ALLOW_DOCKER_REPAIR" == "true" ]] || return 1
  command -v docker >/dev/null 2>&1 || return 1

  log "Host cleanup could not remove all generated Admin UI artifacts; retrying with a one-shot Docker cleanup container."
  docker run --rm \
    -v "${ROOT_DIR}:/workspace" \
    -w /workspace \
    alpine:3.20 \
    sh -c "rm -rf '${ADMIN_UI_RELATIVE_DIR}/.next' '${ADMIN_UI_RELATIVE_DIR}/.next-ci' '${ADMIN_UI_RELATIVE_DIR}/.next-local' '${ADMIN_UI_RELATIVE_DIR}/.next-release' '${ADMIN_UI_RELATIVE_DIR}/out' '${ADMIN_UI_RELATIVE_DIR}/dist' '${ADMIN_UI_RELATIVE_DIR}/coverage' '${ADMIN_UI_RELATIVE_DIR}/.turbo' '${ADMIN_UI_RELATIVE_DIR}/tsconfig.tsbuildinfo'" \
    >>"$REPORT_FILE" 2>&1
}

stop_admin_container_if_possible
if remove_generated_artifacts; then
  log "Admin UI generated artifacts cleaned with host permissions."
  exit 0
fi

if repair_with_docker_root && remove_generated_artifacts; then
  log "Admin UI generated artifacts cleaned after Docker root repair."
  exit 0
fi

warn "Unable to remove generated Admin UI artifacts. Check ownership of ${ADMIN_UI_DIR}/.next."
if [[ "$BEST_EFFORT" == "true" ]]; then
  warn "Continuing because --best-effort was requested. CI builds should use NEXT_DIST_DIR=.next-ci and must not depend on source-tree .next."
  exit 0
fi
warn "Manual recovery: sudo rm -rf '${ADMIN_UI_DIR}/.next' '${ADMIN_UI_DIR}/.next-ci' '${ADMIN_UI_DIR}/.next-local' '${ADMIN_UI_DIR}/.next-release' '${ADMIN_UI_DIR}/out' '${ADMIN_UI_DIR}/dist' '${ADMIN_UI_DIR}/coverage' '${ADMIN_UI_DIR}/.turbo' '${ADMIN_UI_DIR}/tsconfig.tsbuildinfo'"
exit 1
