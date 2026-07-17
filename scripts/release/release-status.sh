#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -d "${ROOT_DIR}/scripts" && -d "${ROOT_DIR}/deploy" ]]; then
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi
PACKAGE_DIR="${PACKAGE_DIR:-${ROOT_DIR}}"
ENV_FILE="${ENV_FILE:-${PACKAGE_DIR}/deploy/env/.env.release}"
PROJECT_NAME="${PROJECT_NAME:-opendispatch-release}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package-dir) PACKAGE_DIR="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --project) PROJECT_NAME="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--package-dir <dir>] [--env-file <file>] [--project <name>]"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

PACKAGE_DIR="$(cd "${PACKAGE_DIR}" && pwd)"
if [[ ! -f "${ENV_FILE}" && -f "${PACKAGE_DIR}/deploy/env/.env.release.example" ]]; then
  ENV_FILE="${PACKAGE_DIR}/deploy/env/.env.release.example"
fi
[[ -f "${ENV_FILE}" ]] || { echo "Missing env file: ${ENV_FILE}" >&2; exit 1; }
ENV_FILE="$(cd "$(dirname "${ENV_FILE}")" && pwd)/$(basename "${ENV_FILE}")"

if [[ -f "${PACKAGE_DIR}/release-manifest.txt" ]]; then
  echo "== Release manifest =="
  sed -n '1,120p' "${PACKAGE_DIR}/release-manifest.txt"
fi

echo "== Compose services =="
exec docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${PACKAGE_DIR}/deploy/docker-compose.release.yml" ps
