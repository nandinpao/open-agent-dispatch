#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "Missing required file: $file" >&2
    exit 1
  fi
}

require_pattern() {
  local file="$1"
  local pattern="$2"
  if ! grep -qE "$pattern" "$file"; then
    echo "Missing pattern in $file: $pattern" >&2
    exit 1
  fi
}

require_file control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityProperties.java
require_file control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java
require_file control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalTokenAuthenticationFilter.java
require_file control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRequestClassifier.java
require_file control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRole.java
require_file control-plane-app/src/test/java/com/opensocket/aievent/core/CoreInternalSecurityClassifierTest.java
require_file docs/i5-core-internal-api-security.md

require_pattern control-plane-app/pom.xml "spring-boot-starter-security"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/AiEventGatewayCoreApplication.java "CoreInternalSecurityProperties.class"
require_pattern control-plane-app/src/main/resources/application.yml "CORE_INTERNAL_SECURITY_ENABLED"
require_pattern control-plane-app/src/main/resources/application.yml "CORE_GATEWAY_INTERNAL_TOKEN"
require_pattern control-plane-app/src/main/resources/application.yml "CORE_ADAPTER_WORKER_INTERNAL_TOKEN"
require_pattern control-plane-app/src/main/resources/application.yml "CORE_OPERATOR_INTERNAL_TOKEN"
require_pattern control-plane-app/src/main/resources/application.yml "CORE_ACTUATOR_INTERNAL_TOKEN"

require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java "/internal/gateway-nodes/\*\*"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java "/internal/control-plane/tasks/\*/result"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java "/internal/adapter-actions/claim"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java "/actuator/\*\*"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java "SessionCreationPolicy.STATELESS"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalTokenAuthenticationFilter.java "MessageDigest.isEqual"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalTokenAuthenticationFilter.java "getLegacyTokenHeaderName"
require_pattern control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalTokenAuthenticationFilter.java "Authorization"

require_pattern deploy/env/.env.core-common.example "CORE_INTERNAL_SECURITY_ENABLED"
require_pattern deploy/env/.env.core-dev.example "CORE_INTERNAL_SECURITY_ENABLED=true"
require_pattern deploy/env/.env.core-prod.example "CORE_INTERNAL_PROTECT_API_MUTATIONS=true"
require_pattern deploy/env/.env.adapter-worker.example "ADAPTER_WORKER_TOKEN_HEADER=X-Cluster-Token"

bash -n scripts/verify/verify-i5-core-internal-security.sh

echo "I5 Core internal API security static verification passed."
