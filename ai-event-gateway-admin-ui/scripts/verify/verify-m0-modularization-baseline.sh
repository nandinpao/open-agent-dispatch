#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

./scripts/architecture/verify-dependency-baseline.py

grep -q '1.0.0-p25.7.4-p5-callback-transition-governance-fix' ai-event-gateway-core-kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java
grep -q 'CoreVersion.CURRENT' ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/api/CoreStatusController.java
grep -q '@ActiveProfiles("test")' ai-event-gateway-core-app/src/test/java/com/opensocket/aievent/core/EventIntakeApiSmokeTest.java
grep -q '@ActiveProfiles("test")' ai-event-gateway-core-app/src/test/java/com/opensocket/aievent/core/GatewayAgentDirectoryApiSmokeTest.java
test -f ai-event-gateway-core-app/src/test/resources/application-test.yml
test -f ai-event-gateway-core-app/src/test/java/com/opensocket/aievent/core/architecture/ModularizationArchitectureBaselineTest.java
test -f ai-event-gateway-core-app/src/test/java/com/opensocket/aievent/core/container/CorePostgresRedisBaselineContainerTest.java

echo "M0 behavioral and dependency safety baseline remains active under M8."
