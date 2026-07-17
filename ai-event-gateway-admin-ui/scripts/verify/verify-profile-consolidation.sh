#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
RESOURCES_DIR="${ROOT_DIR}/ai-event-gateway-core-app/src/main/resources"

for file in application.yml application-local.yml application-dev.yml application-prod.yml; do
  test -f "${RESOURCES_DIR}/${file}"
done

if find "${RESOURCES_DIR}" -maxdepth 1 -name 'application-p[0-9]*.yml' | grep -q .; then
  echo "Legacy phase profile YAML still exists under runtime resources." >&2
  exit 1
fi

test -d "${ROOT_DIR}/docs/legacy-profile-yml"
test -f "${ROOT_DIR}/deploy/env/.env.core-common.example"
test -f "${ROOT_DIR}/deploy/env/.env.core-local.example"
test -f "${ROOT_DIR}/deploy/env/.env.core-dev.example"
test -f "${ROOT_DIR}/deploy/env/.env.core-prod.example"

echo "P12.4 profile consolidation verified."
