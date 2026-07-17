#!/usr/bin/env bash
set -euo pipefail
MODE="${1:---dry-run}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

case "${MODE}" in
  --dry-run)
    python3 scripts/verify/verify-stage2-sql-tenant-contract.py
    python3 scripts/characterization/stage2_tenant_sql_contract.py --strict
    ;;
  --strict)
    python3 scripts/verify/verify-stage2-sql-tenant-contract.py
    python3 scripts/characterization/stage2_tenant_sql_contract.py --strict
    (cd ai-event-gateway-admin-ui && npm run test:stage2-tenant-context)
    (cd ai-event-gateway-core && mvn -pl control-plane-app -am -Dgroups=container -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -Dtest=Stage2PostgresOptionalFilterContainerTest test)
    ;;
  *)
    echo "Usage: $0 [--dry-run|--strict]" >&2
    exit 2
    ;;
esac
