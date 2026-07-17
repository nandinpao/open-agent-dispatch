#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

# Build required upstream reactor modules first, then run only the executable app module.
# This avoids applying spring-boot:run to the parent POM, which has no main class.
exec mvn -U -f pom.xml \
  -pl gateway-app \
  -am \
  -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-local}" \
  spring-boot:run
