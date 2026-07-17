#!/usr/bin/env bash
set -euo pipefail

CURRENT_PACKAGE=""
NEW_PACKAGE=""
PROJECT_NAME="${PROJECT_NAME:-opendispatch-release}"
BACKUP_DIR=""
YES="false"
OFFLINE="false"
STRICT_SECRETS="false"
SKIP_SMOKE="false"

usage() {
  cat <<USAGE
Usage: $0 --current-package <dir> --new-package <dir> [--project <name>] [--backup-dir <dir>] [--yes] [--offline] [--strict-secrets] [--skip-smoke]

Perform a guarded OpenDispatch on-prem package upgrade:
1. copy current .env.release to the new package if needed
2. run new package preflight without host port availability checks
3. create a backup from the current package
4. stop current stack
5. run host port availability preflight after current stack is down
6. start new stack
7. run smoke unless --skip-smoke is used
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --current-package) CURRENT_PACKAGE="$2"; shift 2 ;;
    --new-package) NEW_PACKAGE="$2"; shift 2 ;;
    --project) PROJECT_NAME="$2"; shift 2 ;;
    --backup-dir) BACKUP_DIR="$2"; shift 2 ;;
    --yes) YES="true"; shift ;;
    --offline) OFFLINE="true"; shift ;;
    --strict-secrets) STRICT_SECRETS="true"; shift ;;
    --skip-smoke) SKIP_SMOKE="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() { echo "[ERROR] $*" >&2; exit 1; }
info() { echo "[INFO] $*"; }
[[ -n "${CURRENT_PACKAGE}" && -n "${NEW_PACKAGE}" ]] || fail "--current-package and --new-package are required"
CURRENT_PACKAGE="$(cd "${CURRENT_PACKAGE}" && pwd)"
NEW_PACKAGE="$(cd "${NEW_PACKAGE}" && pwd)"
[[ -x "${CURRENT_PACKAGE}/bin/opendispatch-backup.sh" ]] || fail "Missing current backup script"
[[ -x "${CURRENT_PACKAGE}/bin/opendispatch-down.sh" ]] || fail "Missing current down script"
[[ -x "${NEW_PACKAGE}/bin/opendispatch-preflight.sh" ]] || fail "Missing new preflight script"
[[ -x "${NEW_PACKAGE}/bin/opendispatch-up.sh" ]] || fail "Missing new up script"

if [[ "${YES}" != "true" ]]; then
  cat >&2 <<WARN
This will upgrade project '${PROJECT_NAME}' from:
  current: ${CURRENT_PACKAGE}
  new:     ${NEW_PACKAGE}
Re-run with --yes after confirming the paths and rollback plan.
WARN
  exit 3
fi

CURRENT_ENV="${CURRENT_PACKAGE}/deploy/env/.env.release"
if [[ ! -f "${CURRENT_ENV}" ]]; then
  CURRENT_ENV="${CURRENT_PACKAGE}/deploy/env/.env.release.example"
fi
NEW_ENV="${NEW_PACKAGE}/deploy/env/.env.release"
if [[ ! -f "${NEW_ENV}" && -f "${CURRENT_ENV}" ]]; then
  info "Copying current release env into new package"
  cp -p "${CURRENT_ENV}" "${NEW_ENV}"
fi
[[ -f "${NEW_ENV}" ]] || fail "New package env file is missing: ${NEW_ENV}"

PREFLIGHT_ARGS=(--package-dir "${NEW_PACKAGE}" --env-file "${NEW_ENV}" --skip-port-check)
[[ "${OFFLINE}" == "true" ]] && PREFLIGHT_ARGS+=(--offline)
[[ "${STRICT_SECRETS}" == "true" ]] && PREFLIGHT_ARGS+=(--strict-secrets)
info "Running new package preflight"
"${NEW_PACKAGE}/bin/opendispatch-preflight.sh" "${PREFLIGHT_ARGS[@]}"

if [[ -z "${BACKUP_DIR}" ]]; then
  BACKUP_DIR="${CURRENT_PACKAGE}/backups"
fi
info "Creating pre-upgrade backup"
"${CURRENT_PACKAGE}/bin/opendispatch-backup.sh" --package-dir "${CURRENT_PACKAGE}" --env-file "${CURRENT_ENV}" --project "${PROJECT_NAME}" --output-dir "${BACKUP_DIR}"

info "Stopping current stack"
PROJECT_NAME="${PROJECT_NAME}" ENV_FILE="${CURRENT_ENV}" "${CURRENT_PACKAGE}/bin/opendispatch-down.sh"

PORT_PREFLIGHT_ARGS=(--package-dir "${NEW_PACKAGE}" --env-file "${NEW_ENV}" --skip-docker)
[[ "${STRICT_SECRETS}" == "true" ]] && PORT_PREFLIGHT_ARGS+=(--strict-secrets)
info "Running post-stop host port availability preflight"
"${NEW_PACKAGE}/bin/opendispatch-preflight.sh" "${PORT_PREFLIGHT_ARGS[@]}"

info "Starting new stack"
PROJECT_NAME="${PROJECT_NAME}" ENV_FILE="${NEW_ENV}" "${NEW_PACKAGE}/bin/opendispatch-up.sh"

if [[ "${SKIP_SMOKE}" != "true" ]]; then
  info "Running new stack smoke"
  PROJECT_NAME="${PROJECT_NAME}" ENV_FILE="${NEW_ENV}" "${NEW_PACKAGE}/bin/opendispatch-smoke.sh"
fi

echo "OpenDispatch upgrade completed."
