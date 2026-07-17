#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() { echo "[I7.10][CORE][FAIL] $1" >&2; exit 1; }

require_file() { [[ -f "$1" ]] || fail "Missing required file: $1"; }

require_file "docs/i7.10-documentation-release-gate.md"
require_file "release/i7.10/production-release-checklist.md"
require_file "scripts/verify/verify-i7.5-mock-executor-purge.sh"
require_file "scripts/verify/verify-i7.6-persistent-store-enforcement.sh"
require_file "scripts/verify/verify-i7.7-production-security-audit.sh"
require_file "scripts/verify/verify-i7.9-test-harness-separation.sh"
require_file "scripts/verify/verify-i7.10.1-test-profile-store-overrides.sh"
require_file "scripts/verify/verify-i7.10.6-mybatis-jdbc-jpa-autoconfig-boundary.sh"
require_file "scripts/verify/verify-i7.10.7-jpa-repository-autoconfig-boundary.sh"
require_file "scripts/verify/verify-i7.10.9-maven-reactor-parent-layout.sh"
require_file "scripts/verify/verify-i7.10.10-spring-boot-run-reactor-boundary.sh"
require_file "scripts/verify/verify-i7.10.11-redis-local-credential-address-alignment.sh"
require_file "scripts/verify/verify-i7.10.12-local-schema-preflight.sh"

scripts/verify/verify-i7.5-mock-executor-purge.sh
scripts/verify/verify-i7.6-persistent-store-enforcement.sh
scripts/verify/verify-i7.7-production-security-audit.sh
scripts/verify/verify-i7.9-test-harness-separation.sh
scripts/verify/verify-i7.10.1-test-profile-store-overrides.sh
scripts/verify/verify-i7.10.6-mybatis-jdbc-jpa-autoconfig-boundary.sh
scripts/verify/verify-i7.10.7-jpa-repository-autoconfig-boundary.sh
scripts/verify/verify-i7.10.9-maven-reactor-parent-layout.sh
scripts/verify/verify-i7.10.10-spring-boot-run-reactor-boundary.sh
scripts/verify/verify-i7.10.11-redis-local-credential-address-alignment.sh
scripts/verify/verify-i7.10.12-local-schema-preflight.sh

APP_PROD="ai-event-gateway-core-app/src/main/resources/application-prod.yml"
APP_COMMON="ai-event-gateway-core-app/src/main/resources/application.yml"
VALIDATOR="ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidator.java"
MOCK_MCP="ai-event-gateway-core-adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/MockMcpActionExecutor.java"
ISSUE_RESOLVER="ai-event-gateway-core-adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueVendorResolver.java"

for f in "$APP_PROD" "$APP_COMMON" "$VALIDATOR" "$MOCK_MCP" "$ISSUE_RESOLVER"; do
  require_file "$f"
done

if grep -Eq '\$\{[^}]+:MEMORY\}|store: MEMORY|=MEMORY' "$APP_PROD"; then
  fail "prod profile must not default any source-of-truth store to MEMORY"
fi

if grep -Eq '\$\{[^}]+:MOCK\}|default-vendor:.*MOCK|=MOCK' "$APP_PROD"; then
  fail "prod profile must not default any issue executor vendor to MOCK"
fi

if grep -q 'matchIfMissing = true' "$MOCK_MCP"; then
  fail "MockMcpActionExecutor must not use matchIfMissing=true"
fi

if grep -R "matchIfMissing = true" ai-event-gateway-core-adapter-action/src/main/java/com/opensocket/aievent/core/action/executor \
  | grep -E 'Mock|mock|MOCK' >/dev/null; then
  fail "mock executor code must not use matchIfMissing=true"
fi

grep -q 'enabled: ${CORE_INTERNAL_SECURITY_ENABLED:true}' "$APP_PROD" || fail "Core internal security must default true in prod"
grep -q 'protect-api-mutations: ${CORE_INTERNAL_PROTECT_API_MUTATIONS:true}' "$APP_PROD" || fail "API mutations must be protected in prod"
grep -q 'permit-actuator-health-info: ${CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO:false}' "$APP_PROD" || fail "actuator health/info must not be anonymously permitted by prod default"
grep -q 'validateProductionInternalSecurity' "$VALIDATOR" || fail "Core validator must validate production internal security"
grep -q 'validateProductionPersistentStores' "$VALIDATOR" || fail "Core validator must validate production persistent stores"
grep -q 'validateProductionAdapterExecutorBoundary' "$VALIDATOR" || fail "Core validator must validate production mock executor settings"
grep -q 'isUnsafeProductionToken' "$VALIDATOR" || fail "Core validator must reject unsafe production tokens"

if grep -R -n -E 'Standalone MVP|Submit task to Gateway|Netty local assignment|Task Registry as production|TASK_SUBMIT as production' docs README.md release 2>/dev/null; then
  fail "documentation still contains MVP or legacy task-intake wording as production guidance"
fi

grep -q 'Core as the control-plane assignment authority' docs/i7.10-documentation-release-gate.md || fail "I7.10 doc must state Core assignment authority"
grep -q 'No source-of-truth store uses `MEMORY`' release/i7.10/production-release-checklist.md || fail "release checklist must include memory-store gate"

echo "I7.10 Core production readiness gate passed."
