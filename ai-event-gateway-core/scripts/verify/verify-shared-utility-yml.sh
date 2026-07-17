#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESOURCES_DIR="${ROOT_DIR}/control-plane-app/src/main/resources"
ENV_DIR="${ROOT_DIR}/deploy/env"
DB_CONFIG="${ROOT_DIR}/shared-utility/database/src/main/java/com/agitg/database/DatabaseClusterConfig.java"

# local/dev/prod must use SharedUtility DatabaseClusterConfig pg.single and RedissonAutoConfig keys.
for profile in local dev prod; do
  yml="${RESOURCES_DIR}/application-${profile}.yml"
  test -f "${yml}"
  grep -q "^pg:" "${yml}"
  grep -q "  single:" "${yml}"
  grep -q "PG_SINGLE_URL" "${yml}"
  grep -q "PG_SINGLE_USERNAME" "${yml}"
  grep -q "PG_SINGLE_HIKARI_MAXIMUM_POOL_SIZE" "${yml}"
  grep -q "^redis:" "${yml}"
  grep -q "pg.mybatis" "${yml}" || grep -q "  mybatis:" "${yml}"
  grep -q "base-packages:" "${yml}"
  grep -q "mapper-scan-packages:" "${yml}"
  grep -q "store: \${EVENT_DEDUP_STORE:MYBATIS}" "${yml}"
  grep -q "cache-store: \${EVENT_DEDUP_CACHE_STORE:REDISSON}" "${yml}"

  if grep -q "PG_WRITE_0\|PG_READ_0\|^  write:\|^  read:" "${yml}"; then
    echo "application-${profile}.yml still uses pg.write/pg.read or PG_WRITE_0/PG_READ_0." >&2
    exit 1
  fi
  if grep -q "spring.data.redis" "${yml}"; then
    echo "application-${profile}.yml still references spring.data.redis" >&2
    exit 1
  fi
  grep -q "REDIS_SINGLE_ADDRESS" "${yml}"
  grep -q "REDIS_POOL_MAX_IDLE" "${yml}"
done

# Local must not fall back to memory/H2 mode.
if grep -q "CORE_MEMORY_DATASOURCE\|jdbc:h2\|EVENT_DEDUP_STORE:MEMORY\|PG_ENABLED:false\|REDIS_ENABLED:false" "${RESOURCES_DIR}/application-local.yml"; then
  echo "application-local.yml still contains old memory/H2 or disabled SharedUtility defaults" >&2
  exit 1
fi

# Env examples must use SharedUtility pg.single variables and default dedup source to MYBATIS with Redisson as cache in local/dev/prod.
for env in local dev prod; do
  f="${ENV_DIR}/.env.core-${env}.example"
  test -f "${f}"
  grep -q "PG_ENABLED=true" "${f}"
  grep -q "PG_SINGLE_URL" "${f}"
  grep -q "PG_SINGLE_USERNAME" "${f}"
  grep -q "PG_SINGLE_HIKARI_MAXIMUM_POOL_SIZE" "${f}"
  grep -q "REDIS_ENABLED=true" "${f}"
  grep -q "REDIS_SINGLE_ADDRESS" "${f}"
  grep -q "REDIS_POOL_MAX_IDLE" "${f}"
  grep -q "EVENT_DEDUP_STORE=MYBATIS" "${f}"
  grep -q "EVENT_DEDUP_CACHE_STORE=REDISSON" "${f}"
  if grep -q "PG_WRITE_0\|PG_READ_0\|SPRING_REDIS\|REDIS_HOST=\|REDIS_PORT=\|REDIS_TIMEOUT_MS\|POSTGRES_HIKARI\|POSTGRES_URL=" "${f}"; then
    echo ".env.core-${env}.example still contains old pg.write/read, Core/Spring Redis, or POSTGRES_* aliases" >&2
    exit 1
  fi
done

# When SharedUtility sources are checked out beside this project, verify their implementation details.
# Release source bundles normally consume SharedUtility as Maven dependencies and do not embed its sources.
if [[ -f "${DB_CONFIG}" ]]; then
  grep -q "private DataSourceProp single" "${DB_CONFIG}"
  grep -q "createSingleRoutingDataSource" "${DB_CONFIG}"
  grep -q "pg.single.url" "${DB_CONFIG}"
  grep -q "No PostgreSQL datasource configuration found" "${DB_CONFIG}"
  if grep -q "No pg.master or pg.read configuration found" "${DB_CONFIG}"; then
    echo "Bundled DatabaseClusterConfig still contains old pg.master/pg.read failure message" >&2
    exit 1
  fi
  grep -q "@Conditional(PgRoutingCondition.class)" "${DB_CONFIG}"
  grep -q "pg.enabled" "${ROOT_DIR}/shared-utility/database/src/main/java/com/agitg/database/PgRoutingCondition.java"

  # Shared Redisson starter must tolerate blank optional cluster node placeholders.
  grep -q "filter(node -> node != null && !node.isBlank())" "${ROOT_DIR}/shared-utility/redisson-client/src/main/java/com/agitg/redisson/config/RedissonAutoConfig.java"
  grep -q "redis.cluster.nodes must not be empty" "${ROOT_DIR}/shared-utility/redisson-client/src/main/java/com/agitg/redisson/config/RedissonAutoConfig.java"
else
  grep -q '<artifactId>database</artifactId>' "${ROOT_DIR}/database-platform/pom.xml"
  grep -q '<artifactId>redisson-client</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
  echo "SharedUtility source checkout not present; verified external Maven integration and runtime configuration."
fi

echo "P12.17 SharedUtility latest database pg.single verification passed."
