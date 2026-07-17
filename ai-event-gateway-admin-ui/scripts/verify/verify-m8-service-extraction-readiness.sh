#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

./scripts/verify/verify-pom-build-closure.sh
./scripts/verify/verify-spring-compile-dependencies.sh
./scripts/architecture/verify-dependency-baseline.py
./scripts/verify/verify-jackson3-migration.sh
./scripts/verify/verify-sharedutility-mybatis-persistence.sh
./scripts/verify/verify-p0-database-platform-bootstrap.sh
./scripts/verify/verify-p1-database-migration-consolidation.sh
./scripts/verify/verify-p2-database-dao-po-consolidation.sh
./scripts/verify/verify-p3-persistence-adapter-consolidation.sh
./scripts/verify/verify-p4-persistence-boundary-hardening.sh
./scripts/verify/verify-p5-transaction-claim-lease-hardening.sh
./scripts/verify/verify-p5-inmemory-claim-fence.sh
./scripts/verify/verify-p5-callback-transition-governance-fix.sh

SERVICE_CONTRACTS=ai-event-gateway-core-service-contracts
INTEGRATION=ai-event-gateway-core-integration-events
WORKER=ai-event-gateway-adapter-worker-app

test -f "$SERVICE_CONTRACTS/pom.xml"
test -f "$INTEGRATION/pom.xml"
test -f "$WORKER/pom.xml"
test -f "$SERVICE_CONTRACTS/src/main/java/com/opensocket/aievent/service/adapter/AdapterWorkItem.java"
test -f "data-model/src/main/java/com/opensocket/aievent/service/events/IntegrationEventEnvelope.java"
test -f "$INTEGRATION/src/main/java/com/opensocket/aievent/core/integration/IntegrationEventProjector.java"
test -f "$INTEGRATION/src/main/java/com/opensocket/aievent/core/integration/IntegrationEventDeliveryService.java"
test -f "$WORKER/src/main/java/com/opensocket/aievent/worker/AdapterWorkerApplication.java"
test -f ai-event-gateway-database-platform/src/main/resources/db/migration/V19__integration_event_outbox.sql

grep -q '^integration_event_outbox,ai-event-gateway-core-integration-events,' architecture/table-ownership.csv
grep -q '^ai-event-gateway-adapter-worker-app,worker,' architecture/deployable-boundaries.csv
grep -q '^task.terminal.v1,' architecture/integration-event-catalog.csv

# The external worker must use the stable service contract only. It owns no Core database tables.
grep -q '<artifactId>ai-event-gateway-core-service-contracts</artifactId>' "$WORKER/pom.xml"
if grep -Eq '(ai-event-gateway-core-(adapter-action|db-support|db-migration|domain-events|incident|task-orchestration|execution-control)|ai-event-gateway-database-platform)' "$WORKER/pom.xml"; then
  echo "Adapter worker must not depend on Core domain or persistence modules" >&2
  exit 1
fi

# M8 intentionally introduces exactly two executable Spring Boot applications.
test "$(grep -R -l --include='pom.xml' '<artifactId>spring-boot-maven-plugin</artifactId>' . | wc -l | tr -d ' ')" = "2"
grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' ai-event-gateway-core-app/pom.xml
grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' "$WORKER/pom.xml"

# Multiple handlers for a module event are required for internal consumption plus external projection.
grep -Eq 'Map<String,[[:space:]]*List<ModuleEventHandler' ai-event-gateway-core-domain-events/src/main/java/com/opensocket/aievent/core/outbox/OutboxEventDispatcher.java
if grep -q 'Duplicate module event handler' ai-event-gateway-core-domain-events/src/main/java/com/opensocket/aievent/core/outbox/OutboxEventDispatcher.java; then
  echo "M8 dispatcher must permit multiple handlers per event type" >&2
  exit 1
fi

grep -q 'IntegrationEventProperties.class' ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/AiEventGatewayCoreApplication.java
grep -q 'integration-events:' ai-event-gateway-core-app/src/main/resources/application.yml
grep -q 'adapter-executor:' ai-event-gateway-core-app/src/main/resources/application-hybrid-worker.yml
grep -q "$VERSION" README.md
grep -q "$VERSION" ai-event-gateway-core-kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

echo "M8 service extraction readiness verification passed."

