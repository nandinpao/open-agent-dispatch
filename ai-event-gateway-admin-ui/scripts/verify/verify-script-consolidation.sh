#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPTS_DIR="${ROOT_DIR}/scripts"

required_files=(
  "README.md"
  "build/build.sh"
  "build/docker-build.sh"
  "build/local-docker-up.sh"
  "build/local-docker-down.sh"
  "db/create-postgres-database.sh"
  "db/install-postgres-schema.sh"
  "db/verify-postgres-schema.sh"
  "db/drop-postgres-schema.sh"
  "api/core/status.sh"
  "api/agent/agent-query.sh"
  "api/incident/incident-query.sh"
  "api/task/task-query.sh"
  "api/dispatch/dispatch-request-query.sh"
  "api/adapter/action-query.sh"
  "smoke/event-intake-smoke.sh"
  "smoke/event-result-relay-smoke.sh"
  "smoke/gateway-agent-directory-smoke.sh"
  "verify/verify-profile-consolidation.sh"
  "verify/verify-env-consolidation.sh"
  "verify/verify-sql-consolidation.sh"
  "verify/verify-script-consolidation.sh"
  "verify/verify-all.sh"
  "verify/verify-pom-build-closure.sh"
  "verify/verify-p0-database-platform-bootstrap.sh"
  "verify/verify-p1-database-migration-consolidation.sh"
  "verify/verify-p2-database-dao-po-consolidation.sh"
  "verify/verify-p3-persistence-adapter-consolidation.sh"
  "verify/verify-p4-persistence-boundary-hardening.sh"
  "verify/verify-p5-transaction-claim-lease-hardening.sh"
  "verify/verify-p5-inmemory-claim-fence.sh"
  "verify/verify-p5-callback-transition-governance-fix.sh"
  "verify/verify-shared-utility-yml.sh"
  "verify/verify-local-docker-packaging.sh"
)

for file in "${required_files[@]}"; do
  test -f "${SCRIPTS_DIR}/${file}"
done

# Runtime script root must not contain phase-era or feature-era flat .sh files.
if find "${SCRIPTS_DIR}" -maxdepth 1 -type f -name '*.sh' | grep -q .; then
  echo "Top-level scripts/*.sh still exists; scripts must be grouped by purpose." >&2
  find "${SCRIPTS_DIR}" -maxdepth 1 -type f -name '*.sh' >&2
  exit 1
fi

# Historical phase verification scripts should not remain in runtime scripts.
# P0-P5 database-platform and reliability gates are active architecture checks, not legacy scripts.
legacy_phase_scripts="$(find "${SCRIPTS_DIR}/verify" -maxdepth 1 -type f \
  \( -name 'verify-p[0-9]*.sh' -o -name 'verify-docker-p*.sh' \) \
  ! -name 'verify-p0-database-platform-bootstrap.sh' \
  ! -name 'verify-p1-database-migration-consolidation.sh' \
  ! -name 'verify-p2-database-dao-po-consolidation.sh' \
  ! -name 'verify-p3-persistence-adapter-consolidation.sh' \
  ! -name 'verify-p4-persistence-boundary-hardening.sh' \
  ! -name 'verify-p5-transaction-claim-lease-hardening.sh' \
  ! -name 'verify-p5-inmemory-claim-fence.sh' \
  ! -name 'verify-p5-callback-transition-governance-fix.sh' -print)"
if [[ -n "${legacy_phase_scripts}" ]]; then
  echo "Legacy phase verification scripts still exist under scripts/verify." >&2
  printf '%s\n' "${legacy_phase_scripts}" >&2
  exit 1
fi

test -d "${ROOT_DIR}/docs/legacy-scripts"

# Check shell syntax for all supported runtime shell scripts.
while IFS= read -r script; do
  bash -n "${script}"
done < <(find "${SCRIPTS_DIR}" -type f -name '*.sh' | sort)

echo "P12.7 script consolidation verification passed."
