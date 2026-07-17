#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for script in \
  "${ROOT_DIR}/scripts/build/local-docker-up.sh" \
  "${ROOT_DIR}/scripts/build/local-docker-down.sh" \
  "${ROOT_DIR}/scripts/build/inspect-core-jar-shared-utility.sh"; do
  test -f "${script}"
  test -x "${script}"
  bash -n "${script}"
done

compose="${ROOT_DIR}/deploy/docker/docker-compose.core-local.yml"
test -f "${compose}"
! grep -qE '^  postgres:' "${compose}"
! grep -qE '^  redis:' "${compose}"
! grep -q 'postgres-schema:' "${compose}"
grep -q "ai-event-gateway-core:" "${compose}"
grep -q "AI_EVENT_GATEWAY_CORE_IMAGE" "${compose}"
grep -q "FLYWAY_ENABLED" "${compose}"

grep -q "mvn -U -pl control-plane-app -am" "${ROOT_DIR}/scripts/build/local-docker-up.sh"
grep -q "clean package" "${ROOT_DIR}/scripts/build/local-docker-up.sh"
grep -q "DOCKER_NO_CACHE" "${ROOT_DIR}/scripts/build/local-docker-up.sh"
grep -q "docker compose -f" "${ROOT_DIR}/scripts/build/local-docker-up.sh"
grep -q "pg.single" "${ROOT_DIR}/scripts/build/inspect-core-jar-shared-utility.sh"

grep -q "local-docker-up.sh" "${ROOT_DIR}/deploy/docker/README.md"
grep -q "local-docker-up.sh" "${ROOT_DIR}/scripts/README.md"

echo "P12.22 local Docker packaging verification passed."
