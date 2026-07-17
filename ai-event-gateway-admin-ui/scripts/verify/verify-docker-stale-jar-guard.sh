#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

fail() { echo "ERROR: $*" >&2; exit 1; }

EXPECTED_VERSION="1.0.0"

[[ -f ai-event-gateway-core-app/Dockerfile ]] || fail "Dockerfile missing"

grep -q "EXPECTED_SHARED_UTILITY_VERSION=${EXPECTED_VERSION}" ai-event-gateway-core-app/Dockerfile \
  || fail "Dockerfile must define EXPECTED_SHARED_UTILITY_VERSION=${EXPECTED_VERSION}"

grep -q "database-\${EXPECTED_SHARED_UTILITY_VERSION}.jar" ai-event-gateway-core-app/Dockerfile \
  || fail "Dockerfile must verify exact database artifact version"

grep -q "redisson-client-\${EXPECTED_SHARED_UTILITY_VERSION}.jar" ai-event-gateway-core-app/Dockerfile \
  || fail "Dockerfile must verify exact redisson-client artifact version"

grep -Fq "database|redisson-client)-1\.0-SNAPSHOT" ai-event-gateway-core-app/Dockerfile \
  || fail "Dockerfile must reject stale 1.0-SNAPSHOT SharedUtility jars"

grep -q "context: ../.." deploy/docker/docker-compose.core-local.yml \
  || fail "local compose build context must be repository root"

grep -q "dockerfile: ai-event-gateway-core-app/Dockerfile" deploy/docker/docker-compose.core-local.yml \
  || fail "local compose must reference app Dockerfile from root context"

grep -q "EXPECTED_SHARED_UTILITY_VERSION" deploy/docker/docker-compose.core-local.yml \
  || fail "local compose must pass EXPECTED_SHARED_UTILITY_VERSION build arg"

grep -q "inspect-core-jar-shared-utility.sh" scripts/build/local-docker-up.sh \
  || fail "local-docker-up.sh must inspect packaged jar before Docker build"

grep -q "docker image rm -f" scripts/build/local-docker-up.sh \
  || fail "local-docker-up.sh should remove old local image before rebuild"

grep -q -- "--force-recreate" scripts/build/local-docker-up.sh \
  || fail "local-docker-up.sh should force container recreation"

grep -Fq "1\.0-SNAPSHOT" scripts/build/inspect-core-jar-shared-utility.sh \
  || fail "inspect script must reject stale 1.0-SNAPSHOT jars"

echo "P12.21 Docker stale jar guard verification passed."
