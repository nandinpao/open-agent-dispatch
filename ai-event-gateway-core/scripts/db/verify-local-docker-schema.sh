#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

cat >&2 <<'MSG'
[INFO] verify-local-docker-schema.sh no longer expects docker-compose.core-local.yml to create a postgres service.
[INFO] It verifies an existing Docker-hosted PostgreSQL server through scripts/db/verify-postgres-schema.sh.
[INFO] Set POSTGRES_CLIENT_MODE=docker-exec and POSTGRES_CONTAINER_NAME=<container>, or use POSTGRES_CLIENT_MODE=docker-run.
MSG

: "${POSTGRES_CLIENT_MODE:=auto}"
SQL_ENV=local "${ROOT_DIR}/scripts/db/verify-postgres-schema.sh"
