#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${CORE_DIR}/scripts/db/_pg_env.sh"
source "${CORE_DIR}/scripts/db/_pg_client.sh"
resolve_pg_env
run_psql_file "${SCRIPT_DIR}/verify-p2m1-dispatch-contract-integrity.sql"
