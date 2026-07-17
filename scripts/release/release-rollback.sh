#!/usr/bin/env bash
set -euo pipefail

CURRENT_PACKAGE=""
TARGET_PACKAGE=""
BACKUP=""
PROJECT_NAME="${PROJECT_NAME:-opendispatch-release}"
YES="false"
SKIP_SMOKE="false"
RESTORE_REDIS="false"

usage() {
  cat <<USAGE
Usage: $0 --current-package <dir> --target-package <dir> --backup <backup-dir-or-tar.gz> [--project <name>] [--yes] [--skip-smoke] [--restore-redis]

Rollback a failed release by stopping the current package, restoring a backup,
and starting the target package.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --current-package) CURRENT_PACKAGE="$2"; shift 2 ;;
    --target-package) TARGET_PACKAGE="$2"; shift 2 ;;
    --backup) BACKUP="$2"; shift 2 ;;
    --project) PROJECT_NAME="$2"; shift 2 ;;
    --yes) YES="true"; shift ;;
    --skip-smoke) SKIP_SMOKE="true"; shift ;;
    --restore-redis) RESTORE_REDIS="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() { echo "[ERROR] $*" >&2; exit 1; }
info() { echo "[INFO] $*"; }
[[ -n "${CURRENT_PACKAGE}" && -n "${TARGET_PACKAGE}" && -n "${BACKUP}" ]] || fail "--current-package, --target-package, and --backup are required"
CURRENT_PACKAGE="$(cd "${CURRENT_PACKAGE}" && pwd)"
TARGET_PACKAGE="$(cd "${TARGET_PACKAGE}" && pwd)"
[[ -x "${CURRENT_PACKAGE}/bin/opendispatch-down.sh" ]] || fail "Missing current down script"
[[ -x "${TARGET_PACKAGE}/bin/opendispatch-restore.sh" ]] || fail "Missing target restore script"
[[ -x "${TARGET_PACKAGE}/bin/opendispatch-up.sh" ]] || fail "Missing target up script"

if [[ "${YES}" != "true" ]]; then
  cat >&2 <<WARN
This will rollback project '${PROJECT_NAME}' to:
  target package: ${TARGET_PACKAGE}
  backup:         ${BACKUP}
Re-run with --yes after confirming the target package and backup.
WARN
  exit 3
fi

TARGET_ENV="${TARGET_PACKAGE}/deploy/env/.env.release"
[[ -f "${TARGET_ENV}" ]] || TARGET_ENV="${TARGET_PACKAGE}/deploy/env/.env.release.example"

info "Stopping current stack"
PROJECT_NAME="${PROJECT_NAME}" "${CURRENT_PACKAGE}/bin/opendispatch-down.sh" || true

RESTORE_ARGS=(--backup "${BACKUP}" --package-dir "${TARGET_PACKAGE}" --env-file "${TARGET_ENV}" --project "${PROJECT_NAME}" --yes)
[[ "${RESTORE_REDIS}" == "true" ]] && RESTORE_ARGS+=(--restore-redis)
info "Restoring backup into target package runtime"
"${TARGET_PACKAGE}/bin/opendispatch-restore.sh" "${RESTORE_ARGS[@]}"

info "Starting target stack"
PROJECT_NAME="${PROJECT_NAME}" ENV_FILE="${TARGET_ENV}" "${TARGET_PACKAGE}/bin/opendispatch-up.sh"

if [[ "${SKIP_SMOKE}" != "true" ]]; then
  info "Running target stack smoke"
  PROJECT_NAME="${PROJECT_NAME}" ENV_FILE="${TARGET_ENV}" "${TARGET_PACKAGE}/bin/opendispatch-smoke.sh"
fi

echo "OpenDispatch rollback completed."
