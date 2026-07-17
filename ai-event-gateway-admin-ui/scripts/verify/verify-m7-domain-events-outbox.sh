#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

./scripts/verify/verify-pom-build-closure.sh
./scripts/architecture/verify-dependency-baseline.py

MODULE=ai-event-gateway-core-domain-events
CONTRACTS=ai-event-gateway-core-contracts/src/main/java/com/opensocket/aievent/core/events
OUTBOX="$MODULE/src/main/java/com/opensocket/aievent/core/outbox"

test -f "$MODULE/pom.xml"
test -f "$CONTRACTS/ModuleEvent.java"
test -f "$CONTRACTS/IncidentEscalatedEvent.java"
test -f "$CONTRACTS/TaskTerminalEvent.java"
test -f "$CONTRACTS/AdapterActionRequestedEvent.java"
test -f "$CONTRACTS/DispatchDeadLetteredEvent.java"
test -f "$OUTBOX/TransactionalOutboxPublisher.java"
test -f ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence/domainevent/repository/MybatisOutboxEventRepository.java
test -f "$OUTBOX/OutboxEventDispatcher.java"
test -f "$OUTBOX/ScheduledOutboxDispatcher.java"
test -f "$OUTBOX/IncidentEscalatedAuditHandler.java"
test -f "$OUTBOX/AdapterActionRequestedAuditHandler.java"
test -f "$OUTBOX/DispatchDeadLetteredAuditHandler.java"
test -f ai-event-gateway-core-adapter-action/src/main/java/com/opensocket/aievent/core/action/TaskTerminalEventHandler.java
test -f ai-event-gateway-core-execution-control/src/test/java/com/opensocket/aievent/core/dispatch/DispatchDeadLetterEventTest.java
test -f ai-event-gateway-database-platform/src/main/resources/db/migration/V18__module_domain_events_outbox.sql

grep -q '^module_outbox_events,ai-event-gateway-core-domain-events,' architecture/table-ownership.csv

# M7 removes every cross-module persistence exception. The registry must contain its header only.
if [[ "$(awk 'NF {count++} END {print count+0}' architecture/repository-access-exceptions.csv)" -ne 1 ]]; then
  echo "M7 repository-access-exceptions.csv must contain only the header" >&2
  cat architecture/repository-access-exceptions.csv >&2
  exit 1
fi

# Lifecycle coordinators must call module facades rather than foreign repositories/mappers.
if grep -R --include='*.java' -nE '^import .*\.(Repository|Dao);' \
    ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/lifecycle; then
  echo "Lifecycle coordinators may not import repositories or mappers after M7" >&2
  exit 1
fi

grep -q 'IncidentFacade' ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/lifecycle/IncidentLifecycleService.java
grep -q 'TaskOrchestrationFacade' ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/lifecycle/TaskLifecycleService.java
grep -q 'ModuleEventPublisher' ai-event-gateway-core-execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java
if grep -q 'AdapterActionService' ai-event-gateway-core-execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java; then
  echo "Execution Control must not depend on AdapterActionService after M7" >&2
  exit 1
fi

grep -q '<module>ai-event-gateway-core-domain-events</module>' pom.xml
grep -q "$VERSION" README.md
grep -q "$VERSION" ai-event-gateway-core-kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

echo "M7 module events and transactional outbox verification passed."
