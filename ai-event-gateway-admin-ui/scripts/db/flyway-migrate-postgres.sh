#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/_pg_env.sh"
source "${SCRIPT_DIR}/_maven_reactor.sh"
resolve_pg_env
ensure_reactor_artifacts_installed_for_app "${ROOT_DIR}"

: "${FLYWAY_BASELINE_ON_MIGRATE:=false}"
: "${FLYWAY_BASELINE_VERSION:=1}"
: "${FLYWAY_VALIDATE_ON_MIGRATE:=true}"
: "${FLYWAY_OUT_OF_ORDER:=false}"
: "${FLYWAY_CLEAN_DISABLED:=true}"

JDBC_URL="$(pg_jdbc_url)"
MIGRATION_DIR="${ROOT_DIR}/ai-event-gateway-database-platform/src/main/resources/db/migration"
[[ -d "${MIGRATION_DIR}" ]] || { echo "Missing Flyway migration directory: ${MIGRATION_DIR}" >&2; exit 1; }

echo "Running Flyway migrate against ${JDBC_URL} as ${PGUSER}"
echo "Migration source: ${MIGRATION_DIR#${ROOT_DIR}/}"
if [[ "${FLYWAY_BASELINE_ON_MIGRATE}" == "true" ]]; then
  echo "[WARN] FLYWAY_BASELINE_ON_MIGRATE=true. Use this only after DBA approval for a pre-existing schema. Baseline version=${FLYWAY_BASELINE_VERSION}." >&2
fi

cd "${ROOT_DIR}"
exec mvn -q -f ai-event-gateway-core-app/pom.xml \
  -Dflyway.url="${JDBC_URL}" \
  -Dflyway.user="${PGUSER}" \
  -Dflyway.password="${PGPASSWORD}" \
  -Dflyway.locations="filesystem:${MIGRATION_DIR}" \
  -Dflyway.baselineOnMigrate="${FLYWAY_BASELINE_ON_MIGRATE}" \
  -Dflyway.baselineVersion="${FLYWAY_BASELINE_VERSION}" \
  -Dflyway.validateOnMigrate="${FLYWAY_VALIDATE_ON_MIGRATE}" \
  -Dflyway.outOfOrder="${FLYWAY_OUT_OF_ORDER}" \
  -Dflyway.cleanDisabled="${FLYWAY_CLEAN_DISABLED}" \
  flyway:migrate
