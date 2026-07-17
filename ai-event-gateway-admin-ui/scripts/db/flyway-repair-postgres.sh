#!/usr/bin/env bash
set -euo pipefail

if [[ "${ALLOW_FLYWAY_REPAIR:-false}" != "true" ]]; then
  echo "Refusing to run Flyway repair without ALLOW_FLYWAY_REPAIR=true." >&2
  echo "Use repair only after reviewing flyway_schema_history with DBA / release owner." >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/_pg_env.sh"
source "${SCRIPT_DIR}/_maven_reactor.sh"
resolve_pg_env
ensure_reactor_artifacts_installed_for_app "${ROOT_DIR}"
JDBC_URL="$(pg_jdbc_url)"
MIGRATION_DIR="${ROOT_DIR}/ai-event-gateway-database-platform/src/main/resources/db/migration"

cd "${ROOT_DIR}"
exec mvn -q -f ai-event-gateway-core-app/pom.xml \
  -Dflyway.url="${JDBC_URL}" \
  -Dflyway.user="${PGUSER}" \
  -Dflyway.password="${PGPASSWORD}" \
  -Dflyway.locations="filesystem:${MIGRATION_DIR}" \
  flyway:repair
