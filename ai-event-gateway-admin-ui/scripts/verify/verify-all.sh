#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

"${SCRIPT_DIR}/verify-profile-consolidation.sh"
"${SCRIPT_DIR}/verify-env-consolidation.sh"
"${SCRIPT_DIR}/verify-sql-consolidation.sh"
"${SCRIPT_DIR}/verify-script-consolidation.sh"
"${SCRIPT_DIR}/verify-pom-build-closure.sh"
"${SCRIPT_DIR}/verify-maven-repository-resolution.sh"
"${SCRIPT_DIR}/verify-local-docker-packaging.sh"
"${SCRIPT_DIR}/verify-docker-stale-jar-guard.sh"
"${SCRIPT_DIR}/verify-local-docker-schema-init.sh"

# Runtime SharedUtility configuration is always validated. Source implementation checks
# run only when the SharedUtility source tree is checked out beside this project.
"${SCRIPT_DIR}/verify-shared-utility-yml.sh"
if [[ -d "${ROOT_DIR}/shared-utility" ]]; then
  "${SCRIPT_DIR}/verify-database-compiler-release.sh"
  "${SCRIPT_DIR}/verify-shared-utility-dependency-management.sh"
  "${SCRIPT_DIR}/verify-lombok-jdk25.sh"
  "${SCRIPT_DIR}/verify-aop-dependencies.sh"
  "${SCRIPT_DIR}/verify-boot4-hibernate-customizer.sh"
  "${SCRIPT_DIR}/verify-redisson-spring-dependencies.sh"
else
  echo "SharedUtility source tree is not bundled; validating versioned external dependencies through M8 POM checks."
fi

"${SCRIPT_DIR}/verify-m0-modularization-baseline.sh"
"${SCRIPT_DIR}/verify-m1-foundation-modules.sh"
"${SCRIPT_DIR}/verify-m2-event-incident-modules.sh"
"${SCRIPT_DIR}/verify-m3-agent-task-modules.sh"
"${SCRIPT_DIR}/verify-m4-execution-control-module.sh"
"${SCRIPT_DIR}/verify-m5-adapter-action-module.sh"
"${SCRIPT_DIR}/verify-m6-observability-governance.sh"
"${SCRIPT_DIR}/verify-m7-domain-events-outbox.sh"
"${SCRIPT_DIR}/verify-m8-service-extraction-readiness.sh"
echo "All consolidation and M8 service-extraction checks passed."
"${SCRIPT_DIR}/verify-i7.10-production-readiness-gate.sh"
