#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PLATFORM=ai-event-gateway-database-platform

test -f "$PLATFORM/pom.xml"
test -f "$PLATFORM/src/main/java/com/opensocket/aievent/database/DatabasePlatformModule.java"
test -f "$PLATFORM/src/main/java/com/opensocket/aievent/database/config/DatabasePlatformAutoConfiguration.java"
test -f "$PLATFORM/src/main/java/com/opensocket/aievent/database/config/DatabasePlatformProperties.java"
test -f "$PLATFORM/src/main/java/com/opensocket/aievent/database/config/DatabasePlatformRuntimeInspector.java"
test -f "$PLATFORM/src/main/java/com/opensocket/aievent/database/health/DatabasePlatformHealthIndicator.java"
test -f "$PLATFORM/src/main/java/com/opensocket/aievent/database/mybatis/typehandler/JsonMapTypeHandler.java"
test -f "$PLATFORM/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

! grep -q '<artifactId>ai-event-gateway-core-db-support</artifactId>' "$PLATFORM/pom.xml"
! grep -q '<artifactId>ai-event-gateway-core-db-migration</artifactId>' "$PLATFORM/pom.xml"
grep -q '<artifactId>database</artifactId>' "$PLATFORM/pom.xml"
grep -q '<artifactId>flyway-core</artifactId>' "$PLATFORM/pom.xml"
grep -q '<artifactId>flyway-database-postgresql</artifactId>' "$PLATFORM/pom.xml"

grep -q '<module>ai-event-gateway-database-platform</module>' pom.xml
grep -q '<artifactId>ai-event-gateway-database-platform</artifactId>' ai-event-gateway-core-app/pom.xml
! grep -q '<artifactId>ai-event-gateway-core-db-support</artifactId>' ai-event-gateway-core-app/pom.xml
! grep -q '<artifactId>ai-event-gateway-core-db-migration</artifactId>' ai-event-gateway-core-app/pom.xml

test -f ai-event-gateway-core-app/src/test/java/com/opensocket/aievent/core/architecture/P0DatabasePlatformStructureTest.java

echo "P0 database platform bootstrap verification passed under P1 ownership."
