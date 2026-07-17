#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TASK_TYPE_FILE="${CORE_DIR}/data-model/src/main/java/com/opensocket/aievent/core/task/TaskType.java"

if ! grep -q 'CMS_CONTENT_REVIEW' "${TASK_TYPE_FILE}"; then
  echo "[FAIL] TaskType enum does not contain CMS_CONTENT_REVIEW. Eligibility API cannot read the P2-P seeded task." >&2
  exit 1
fi

source "${CORE_DIR}/scripts/db/_pg_env.sh"
source "${CORE_DIR}/scripts/db/_pg_client.sh"
resolve_pg_env
run_psql_file "${SCRIPT_DIR}/verify-p2p-cms-content-review-contract.sql"
