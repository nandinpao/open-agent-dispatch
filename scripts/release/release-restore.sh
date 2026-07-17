#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -d "${ROOT_DIR}/scripts" && -d "${ROOT_DIR}/deploy" ]]; then
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi
PACKAGE_DIR="${PACKAGE_DIR:-${ROOT_DIR}}"
ENV_FILE="${ENV_FILE:-${PACKAGE_DIR}/deploy/env/.env.release}"
PROJECT_NAME="${PROJECT_NAME:-opendispatch-release}"
BACKUP=""
YES="false"
RESTORE_REDIS="false"
WORK_DIR=""

usage() {
  cat <<USAGE
Usage: $0 --backup <backup-dir-or-tar.gz> [--package-dir <dir>] [--env-file <file>] [--project <name>] [--yes] [--restore-redis]

Restore an OpenDispatch release backup. PostgreSQL restore is destructive for
the target database. Use --yes for non-interactive automation.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) BACKUP="$2"; shift 2 ;;
    --package-dir) PACKAGE_DIR="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --project) PROJECT_NAME="$2"; shift 2 ;;
    --yes) YES="true"; shift ;;
    --restore-redis) RESTORE_REDIS="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() { echo "[ERROR] $*" >&2; exit 1; }
info() { echo "[INFO] $*"; }
require_file() { [[ -f "$1" ]] || fail "Missing file: $1"; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"; }
cleanup() { [[ -n "${WORK_DIR}" && -d "${WORK_DIR}" ]] && rm -rf "${WORK_DIR}"; }
trap cleanup EXIT

verify_checksum_manifest() {
  local dir="$1"
  require_file "${dir}/SHA256SUMS"
  info "Verifying backup payload checksums"
  (
    cd "${dir}"
    if command -v sha256sum >/dev/null 2>&1; then
      sha256sum -c SHA256SUMS
    elif command -v shasum >/dev/null 2>&1; then
      shasum -a 256 -c SHA256SUMS
    else
      fail "Missing required command: sha256sum or shasum"
    fi
  )
}

verify_archive_checksum_if_present() {
  local archive="$1"
  local checksum="${archive}.sha256"
  if [[ ! -f "${checksum}" ]]; then
    info "Backup archive checksum sidecar not found; relying on embedded SHA256SUMS after extraction: ${checksum}"
    return 0
  fi
  info "Verifying backup archive checksum"
  (
    cd "$(dirname "${archive}")"
    if command -v sha256sum >/dev/null 2>&1; then
      sha256sum -c "$(basename "${checksum}")"
    elif command -v shasum >/dev/null 2>&1; then
      shasum -a 256 -c "$(basename "${checksum}")"
    else
      fail "Missing required command: sha256sum or shasum"
    fi
  )
}

[[ -n "${BACKUP}" ]] || fail "--backup is required"
PACKAGE_DIR="$(cd "${PACKAGE_DIR}" && pwd)"
if [[ ! -f "${ENV_FILE}" && -f "${PACKAGE_DIR}/deploy/env/.env.release.example" ]]; then
  ENV_FILE="${PACKAGE_DIR}/deploy/env/.env.release.example"
fi
ENV_FILE="$(cd "$(dirname "${ENV_FILE}")" && pwd)/$(basename "${ENV_FILE}")"

require_file "${PACKAGE_DIR}/deploy/docker-compose.release.yml"
require_file "${ENV_FILE}"

if [[ -d "${BACKUP}" ]]; then
  BACKUP_DIR="$(cd "${BACKUP}" && pwd)"
elif [[ -f "${BACKUP}" ]]; then
  require_cmd tar
  verify_archive_checksum_if_present "$(cd "$(dirname "${BACKUP}")" && pwd)/$(basename "${BACKUP}")"
  WORK_DIR="$(mktemp -d)"
  tar -xzf "${BACKUP}" -C "${WORK_DIR}"
  BACKUP_DIR="$(find "${WORK_DIR}" -mindepth 1 -maxdepth 1 -type d | head -1)"
else
  fail "Backup does not exist: ${BACKUP}"
fi

verify_checksum_manifest "${BACKUP_DIR}"
require_file "${BACKUP_DIR}/postgres/postgres.dump"

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a
POSTGRES_DB="${POSTGRES_DB:-ai_event_gateway_release}"
POSTGRES_USER="${POSTGRES_USER:-ai_event_release}"

if [[ "${YES}" != "true" ]]; then
  cat >&2 <<WARN
This will destructively restore PostgreSQL database '${POSTGRES_DB}' for project '${PROJECT_NAME}'.
Re-run with --yes after confirming that the target stack and backup are correct.
WARN
  exit 3
fi

require_cmd docker
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is not available"

COMPOSE=(docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${PACKAGE_DIR}/deploy/docker-compose.release.yml")

info "Starting PostgreSQL and Redis dependencies"
"${COMPOSE[@]}" up -d postgres redis

info "Waiting for PostgreSQL readiness"
for _ in $(seq 1 90); do
  if "${COMPOSE[@]}" exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
"${COMPOSE[@]}" exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1 \
  || fail "PostgreSQL did not become ready"

info "Stopping application services before restore"
"${COMPOSE[@]}" stop admin-ui netty core >/dev/null 2>&1 || true

info "Restoring PostgreSQL dump"
cat "${BACKUP_DIR}/postgres/postgres.dump" | "${COMPOSE[@]}" exec -T postgres pg_restore -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" --clean --if-exists --no-owner --no-privileges

if [[ "${RESTORE_REDIS}" == "true" && -f "${BACKUP_DIR}/redis/dump.rdb" ]]; then
  info "Restoring Redis dump.rdb"
  redis_container="$("${COMPOSE[@]}" ps -q redis || true)"
  [[ -n "${redis_container}" ]] || fail "Redis container not found"
  "${COMPOSE[@]}" stop redis
  docker cp "${BACKUP_DIR}/redis/dump.rdb" "${redis_container}:/data/dump.rdb"
  "${COMPOSE[@]}" up -d redis
fi

echo "OpenDispatch restore completed. Start the stack with: ${PACKAGE_DIR}/bin/opendispatch-up.sh"
