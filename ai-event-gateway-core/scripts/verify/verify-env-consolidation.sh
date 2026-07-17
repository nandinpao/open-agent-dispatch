#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_env=(
  ".env.core-common.example"
  ".env.core-local.example"
  ".env.core-dev.example"
  ".env.core-prod.example"
  "README.md"
)

for file in "${required_env[@]}"; do
  test -f "${ROOT_DIR}/deploy/env/${file}"
done

required_compose=(
  "docker-compose.core-local.yml"
  "docker-compose.core-dev.yml"
  "docker-compose.core-prod.yml"
)

for file in "${required_compose[@]}"; do
  test -f "${ROOT_DIR}/deploy/docker/${file}"
  grep -q ".env.core-common.example" "${ROOT_DIR}/deploy/docker/${file}"
done

# Local and dev target existing PostgreSQL / Redis services. They must not create
# database containers or run direct SQL schema installers. Flyway owns schema lifecycle.
! grep -qE '^  postgres:' "${ROOT_DIR}/deploy/docker/docker-compose.core-local.yml"
! grep -qE '^  postgres:' "${ROOT_DIR}/deploy/docker/docker-compose.core-dev.yml"
! grep -q 'postgres-schema:' "${ROOT_DIR}/deploy/docker/docker-compose.core-local.yml"
! grep -q 'postgres-schema:' "${ROOT_DIR}/deploy/docker/docker-compose.core-dev.yml"
grep -q "FLYWAY_ENABLED" "${ROOT_DIR}/deploy/docker/docker-compose.core-local.yml"
grep -q "FLYWAY_ENABLED" "${ROOT_DIR}/deploy/docker/docker-compose.core-dev.yml"

# Runtime deploy folders must not contain historical phase deployment files.
if find "${ROOT_DIR}/deploy/env" -maxdepth 1 -type f -name '.env.core-p[0-9]*.example' | grep -q .; then
  echo "Legacy phase env files still exist in deploy/env" >&2
  exit 1
fi

if find "${ROOT_DIR}/deploy/docker" -maxdepth 1 -type f -name 'docker-compose.core-p[0-9]*.yml' | grep -q .; then
  echo "Legacy phase docker compose files still exist in deploy/docker" >&2
  exit 1
fi

# Legacy files should be retained as migration references only.
test -d "${ROOT_DIR}/docs/legacy-env"
test -d "${ROOT_DIR}/docs/legacy-docker-compose"

# Basic reference checks.
grep -R "\.env.core-p[0-9]" -n "${ROOT_DIR}/deploy" && {
  echo "Runtime deploy folder still references phase env files" >&2
  exit 1
} || true

echo "P12.5 env consolidation verification passed."
