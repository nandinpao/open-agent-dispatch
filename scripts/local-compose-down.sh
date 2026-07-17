#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/deploy/env/.env.local" ]]; then
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local"
  else
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local.example"
  fi
fi
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/deploy/docker-compose.local.yml}"
PROJECT_NAME="${PROJECT_NAME:-opendispatch}"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"

cd "${ROOT_DIR}"
bash "${ROOT_DIR}/scripts/ci/local-admin-ui-host.sh" stop --env-file "${ENV_FILE}" --output-dir "${CI_OUTPUT_DIR}" >/dev/null 2>&1 || true
docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" down --remove-orphans
