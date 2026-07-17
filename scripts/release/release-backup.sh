#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Source-tree layout: scripts/release/release-backup.sh -> repo root is ../..
if [[ -d "${ROOT_DIR}/scripts" && -d "${ROOT_DIR}/deploy" ]]; then
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi
PACKAGE_DIR="${PACKAGE_DIR:-${ROOT_DIR}}"
ENV_FILE="${ENV_FILE:-${PACKAGE_DIR}/deploy/env/.env.release}"
PROJECT_NAME="${PROJECT_NAME:-opendispatch-release}"
OUTPUT_DIR="${OUTPUT_DIR:-${PACKAGE_DIR}/backups}"
BACKUP_NAME=""
INCLUDE_REDIS="false"
SKIP_DOCKER="false"

usage() {
  cat <<USAGE
Usage: $0 [--package-dir <dir>] [--env-file <file>] [--project <name>] [--output-dir <dir>] [--name <backup-name>] [--include-redis] [--skip-docker]

Create an OpenDispatch release backup. By default this backs up PostgreSQL with
pg_dump custom format and captures release metadata. Redis is optional because
it should only contain runtime/cache state.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package-dir) PACKAGE_DIR="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --project) PROJECT_NAME="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --name) BACKUP_NAME="$2"; shift 2 ;;
    --include-redis) INCLUDE_REDIS="true"; shift ;;
    --skip-docker) SKIP_DOCKER="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() { echo "[ERROR] $*" >&2; exit 1; }
info() { echo "[INFO] $*"; }
require_file() { [[ -f "$1" ]] || fail "Missing file: $1"; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"; }
checksum_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}"
  else
    fail "Missing required command: sha256sum or shasum"
  fi
}

PACKAGE_DIR="$(cd "${PACKAGE_DIR}" && pwd)"
if [[ ! -f "${ENV_FILE}" && -f "${PACKAGE_DIR}/deploy/env/.env.release.example" ]]; then
  ENV_FILE="${PACKAGE_DIR}/deploy/env/.env.release.example"
fi
if [[ -f "${ENV_FILE}" ]]; then
  ENV_FILE="$(cd "$(dirname "${ENV_FILE}")" && pwd)/$(basename "${ENV_FILE}")"
fi

require_file "${PACKAGE_DIR}/deploy/docker-compose.release.yml"
require_file "${ENV_FILE}"
require_cmd tar

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

POSTGRES_DB="${POSTGRES_DB:-ai_event_gateway_release}"
POSTGRES_USER="${POSTGRES_USER:-ai_event_release}"

if [[ "${SKIP_DOCKER}" != "true" ]]; then
  require_cmd docker
  docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is not available"
fi

if [[ -z "${BACKUP_NAME}" ]]; then
  BACKUP_NAME="opendispatch-backup-$(date -u +%Y%m%dT%H%M%SZ)"
fi

mkdir -p "${OUTPUT_DIR}"
chmod go-rwx "${OUTPUT_DIR}" 2>/dev/null || true
BACKUP_DIR="${OUTPUT_DIR}/${BACKUP_NAME}"
ARCHIVE_PATH="${OUTPUT_DIR}/${BACKUP_NAME}.tar.gz"
CHECKSUM_PATH="${ARCHIVE_PATH}.sha256"
rm -rf "${BACKUP_DIR}" "${ARCHIVE_PATH}" "${CHECKSUM_PATH}"
mkdir -p "${BACKUP_DIR}/metadata" "${BACKUP_DIR}/postgres" "${BACKUP_DIR}/redis"
chmod -R go-rwx "${BACKUP_DIR}" 2>/dev/null || true

info "Capturing release metadata"
cp -p "${ENV_FILE}" "${BACKUP_DIR}/metadata/.env.release.backup"
cp -p "${PACKAGE_DIR}/deploy/docker-compose.release.yml" "${BACKUP_DIR}/metadata/docker-compose.release.yml"
if [[ -f "${PACKAGE_DIR}/release-manifest.txt" ]]; then
  cp -p "${PACKAGE_DIR}/release-manifest.txt" "${BACKUP_DIR}/metadata/release-manifest.txt"
fi

cat > "${BACKUP_DIR}/metadata/backup-manifest.txt" <<MANIFEST
name=${BACKUP_NAME}
created_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
project=${PROJECT_NAME}
package_dir=${PACKAGE_DIR}
env_file=${ENV_FILE}
postgres_db=${POSTGRES_DB}
postgres_user=${POSTGRES_USER}
include_redis=${INCLUDE_REDIS}
MANIFEST

if [[ "${SKIP_DOCKER}" == "true" ]]; then
  info "Skipping docker backup actions by request. Metadata-only backup will be created."
  echo "metadata_only=true" >> "${BACKUP_DIR}/metadata/backup-manifest.txt"
else
  COMPOSE=(docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${PACKAGE_DIR}/deploy/docker-compose.release.yml")
  info "Creating PostgreSQL pg_dump custom-format backup"
  "${COMPOSE[@]}" exec -T postgres pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Fc > "${BACKUP_DIR}/postgres/postgres.dump"

  info "Capturing docker compose service state"
  "${COMPOSE[@]}" ps > "${BACKUP_DIR}/metadata/docker-compose-ps.txt" || true

  if [[ "${INCLUDE_REDIS}" == "true" ]]; then
    info "Capturing Redis dump.rdb"
    redis_container="$("${COMPOSE[@]}" ps -q redis || true)"
    if [[ -n "${redis_container}" ]]; then
      "${COMPOSE[@]}" exec -T redis redis-cli SAVE >/dev/null
      docker cp "${redis_container}:/data/dump.rdb" "${BACKUP_DIR}/redis/dump.rdb"
    else
      echo "redis_container_not_found=true" > "${BACKUP_DIR}/redis/README.txt"
    fi
  else
    echo "Redis backup was not requested. Use --include-redis if runtime/cache state must be captured." > "${BACKUP_DIR}/redis/README.txt"
  fi
fi

info "Writing checksums"
(
  cd "${BACKUP_DIR}"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 -I{} sh -c 'if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi' sh {} > SHA256SUMS
)

info "Creating backup archive"
(
  cd "${OUTPUT_DIR}"
  tar -czf "$(basename "${ARCHIVE_PATH}")" "${BACKUP_NAME}"
)
(
  cd "${OUTPUT_DIR}"
  checksum_file "$(basename "${ARCHIVE_PATH}")" > "$(basename "${CHECKSUM_PATH}")"
)
chmod -R go-rwx "${BACKUP_DIR}" 2>/dev/null || true
chmod 600 "${ARCHIVE_PATH}" "${CHECKSUM_PATH}" 2>/dev/null || true

echo "OpenDispatch backup completed."
echo "Backup archive: ${ARCHIVE_PATH}"
echo "Checksum: ${CHECKSUM_PATH}"
