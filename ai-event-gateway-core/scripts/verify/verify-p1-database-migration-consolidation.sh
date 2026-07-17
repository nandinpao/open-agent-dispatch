#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PLATFORM=database-platform
MIGRATIONS="$PLATFORM/src/main/resources/db/migration"

# The legacy module and all current build dependencies must be gone.
test ! -e ai-event-gateway-core-db-migration
! grep -q '<module>ai-event-gateway-core-db-migration</module>' pom.xml
! grep -q '<artifactId>ai-event-gateway-core-db-migration</artifactId>' pom.xml
! grep -q '<artifactId>ai-event-gateway-core-db-migration</artifactId>' control-plane-app/pom.xml
! grep -q '<artifactId>ai-event-gateway-core-db-migration</artifactId>' "$PLATFORM/pom.xml"

# All immutable migration resources now belong to database-platform.
test -f "$PLATFORM/src/main/resources/db/README.md"
test -d "$MIGRATIONS"
test "$(find "$MIGRATIONS" -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')" -ge "19"
for version in $(seq 1 19); do
  find "$MIGRATIONS" -maxdepth 1 -type f -name "V${version}__*.sql" | grep -q .
done

test -f "$MIGRATIONS/V1__incident_store.sql"
test -f "$MIGRATIONS/V17__agent_capacity_reservation.sql"
test -f "$MIGRATIONS/V18__module_domain_events_outbox.sql"
test -f "$MIGRATIONS/V19__integration_event_outbox.sql"

grep -R -q 'classpath:db/migration' control-plane-app/src/main/resources

test -f control-plane-app/src/test/java/com/opensocket/aievent/core/architecture/P1DatabaseMigrationConsolidationTest.java

echo "P1 database migration consolidation verification passed."
