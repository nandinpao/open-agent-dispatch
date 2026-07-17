#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

CORE_PROD="$ROOT_DIR/control-plane-app/src/main/resources/application-prod.yml"
VALIDATOR="$ROOT_DIR/control-plane-app/src/main/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidator.java"
DOC="$ROOT_DIR/docs/i7.7-production-security-audit-fail-fast.md"

for file in "$CORE_PROD" "$VALIDATOR" "$DOC"; do
  [[ -f "$file" ]] || { echo "Missing required I7.7 file: $file" >&2; exit 1; }
done

grep -q "enabled: \${CORE_INTERNAL_SECURITY_ENABLED:true}" "$CORE_PROD" || { echo "prod must default Core internal security to true" >&2; exit 1; }
grep -q "protect-api-mutations: \${CORE_INTERNAL_PROTECT_API_MUTATIONS:true}" "$CORE_PROD" || { echo "prod must protect API mutations" >&2; exit 1; }
grep -q "permit-actuator-health-info: \${CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO:false}" "$CORE_PROD" || { echo "prod must not permit unauthenticated actuator health/info" >&2; exit 1; }
grep -q "allow-legacy-token-header: \${CORE_INTERNAL_ALLOW_LEGACY_TOKEN_HEADER:false}" "$CORE_PROD" || { echo "prod must disable legacy internal token header by default" >&2; exit 1; }
grep -q "validateProductionInternalSecurity" "$VALIDATOR" || { echo "validator must check production internal security" >&2; exit 1; }
grep -q "isUnsafeProductionToken" "$VALIDATOR" || { echo "validator must reject placeholder tokens" >&2; exit 1; }
grep -q "CoreInternalSecurityRole.values" "$VALIDATOR" || { echo "validator must check every internal role token" >&2; exit 1; }

echo "I7.7 Core production security static verification passed."
