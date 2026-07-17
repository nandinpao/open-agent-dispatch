#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fail(){ echo "ERROR: $*" >&2; exit 1; }
LOCAL_YML="$ROOT_DIR/ai-event-gateway-core-app/src/main/resources/application-local.yml"
DEV_YML="$ROOT_DIR/ai-event-gateway-core-app/src/main/resources/application-dev.yml"
HOST_ENV="$ROOT_DIR/deploy/env/.env.core-host-local.example"
COMPOSE_LOCAL="$ROOT_DIR/deploy/docker/docker-compose.core-local.yml"
COMPOSE_DEV="$ROOT_DIR/deploy/docker/docker-compose.core-dev.yml"
for f in "$LOCAL_YML" "$DEV_YML" "$HOST_ENV" "$COMPOSE_LOCAL" "$COMPOSE_DEV"; do [[ -f "$f" ]] || fail "missing required file: ${f#$ROOT_DIR/}"; done
for yml in "$LOCAL_YML" "$DEV_YML"; do
  grep -q 'password: ${REDIS_PASSWORD:123456}' "$yml" || fail "${yml#$ROOT_DIR/} must default REDIS_PASSWORD to 123456"
  grep -q 'address: ${REDIS_SINGLE_ADDRESS:baofire.com:6379}' "$yml" || fail "${yml#$ROOT_DIR/} must default REDIS_SINGLE_ADDRESS to baofire.com:6379"
  grep -q 'maxIdle: ${REDIS_POOL_MAX_IDLE:16}' "$yml" || fail "${yml#$ROOT_DIR/} must expose camelCase maxIdle alias for SharedUtility compatibility"
  grep -q 'connTimeout: ${REDIS_POOL_CONN_TIMEOUT:3000}' "$yml" || fail "${yml#$ROOT_DIR/} must expose camelCase connTimeout alias"
  grep -q 'scanInterval: ${REDIS_CLUSTER_SCAN_INTERVAL:1000}' "$yml" || fail "${yml#$ROOT_DIR/} must expose camelCase scanInterval alias"
  grep -q 'readMode: ${REDIS_CLUSTER_READ_MODE:SLAVE}' "$yml" || fail "${yml#$ROOT_DIR/} must expose camelCase readMode alias"
  grep -q 'slaveConnection-pool-size: ${REDIS_CLUSTER_SLAVE_CONNECTION_POOL_SIZE:64}' "$yml" || fail "${yml#$ROOT_DIR/} must expose SharedUtility slaveConnection-pool-size alias"
  grep -q 'masterConnection-pool-size: ${REDIS_CLUSTER_MASTER_CONNECTION_POOL_SIZE:64}' "$yml" || fail "${yml#$ROOT_DIR/} must expose SharedUtility masterConnection-pool-size alias"
  grep -q 'baofire.com:7001' "$yml" || fail "${yml#$ROOT_DIR/} must default Redis cluster nodes to baofire.com"
done
grep -q '^REDIS_PASSWORD=123456$' "$HOST_ENV" || fail "host-local env must define Redis password"
grep -q '^REDIS_SINGLE_ADDRESS=baofire.com:6379$' "$HOST_ENV" || fail "host-local env must define baofire Redis address"
# I7.10.13 switched local/dev compose to use an existing Redis server instead of creating one.
! grep -qE '^  redis:' "$COMPOSE_LOCAL" || fail "local compose must not create Redis; it must connect to an existing Redis server"
! grep -qE '^  redis:' "$COMPOSE_DEV" || fail "dev compose must not create Redis; it must connect to an existing Redis server"
grep -q '.env.core-local.example' "$COMPOSE_LOCAL" || fail "local compose must load env file containing Redis settings"
grep -q '.env.core-dev.example' "$COMPOSE_DEV" || fail "dev compose must load env file containing Redis settings"
grep -q 'HOST_LOCAL_ENV=' "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "run-core-local.sh must load host-local env defaults"
echo "I7.10.11 Core Redis local credential/address alignment verification passed."
