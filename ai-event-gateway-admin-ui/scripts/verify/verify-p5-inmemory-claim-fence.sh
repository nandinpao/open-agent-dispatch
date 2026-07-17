#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

grep -q "$VERSION" pom.xml
grep -q 'synchronizeClaimState(action)' ai-event-gateway-core-adapter-action/src/main/java/com/opensocket/aievent/core/action/InMemoryAdapterActionRepository.java
grep -q 'new ClaimOwnership(action.getClaimedBy(), action.getLeaseExpiresAt())' ai-event-gateway-core-adapter-action/src/main/java/com/opensocket/aievent/core/action/InMemoryAdapterActionRepository.java
grep -q 'persistedClaimedActionShouldRestoreOwnershipFence' ai-event-gateway-core-adapter-action/src/test/java/com/opensocket/aievent/core/action/AdapterExternalWorkerContractTest.java
grep -q 'synchronizeClaimState(request)' ai-event-gateway-core-execution-control/src/main/java/com/opensocket/aievent/core/dispatch/InMemoryDispatchRequestRepository.java
grep -q 'persistedDispatchingRequestShouldRestoreOwnershipFence' ai-event-gateway-core-execution-control/src/test/java/com/opensocket/aievent/core/dispatch/InMemoryDispatchClaimStateTest.java

echo "P5 in-memory claim fence verification passed."
