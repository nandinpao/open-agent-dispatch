#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/_pg_env.sh"
source "${SCRIPT_DIR}/_maven_reactor.sh"
resolve_pg_env
ensure_reactor_artifacts_installed_for_app "${ROOT_DIR}"

JDBC_URL="$(pg_jdbc_url)"
MIGRATION_DIR="${ROOT_DIR}/database-platform/src/main/resources/db/migration"

echo "Running Flyway info against ${JDBC_URL} as ${PGUSER}"
cd "${ROOT_DIR}"
exec mvn -q -f control-plane-app/pom.xml \
  -Dflyway.url="${JDBC_URL}" \
  -Dflyway.user="${PGUSER}" \
  -Dflyway.password="${PGPASSWORD}" \
  -Dflyway.locations="filesystem:${MIGRATION_DIR}" \
  flyway:info
