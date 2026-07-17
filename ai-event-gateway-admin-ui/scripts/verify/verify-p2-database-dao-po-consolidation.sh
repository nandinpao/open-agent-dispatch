#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"
PLATFORM=ai-event-gateway-database-platform

test ! -e ai-event-gateway-core-db-support
! grep -q 'ai-event-gateway-core-db-support' pom.xml
if grep -R --include='pom.xml' -q 'ai-event-gateway-core-db-support' ai-event-gateway-*; then
  echo 'Obsolete db-support dependency remains in a module POM.' >&2
  exit 1
fi

test "$(find "$PLATFORM/src/main/java/com/opensocket/aievent/database/persistence" -path '*/dao/*Dao.java' | wc -l | tr -d ' ')" = "15"
test "$(find "$PLATFORM/src/main/java/com/opensocket/aievent/database/persistence" -path '*/po/*Po.java' | wc -l | tr -d ' ')" = "16"
test "$(find "$PLATFORM/src/main/resources/mybatis/postgresql" -type f -name '*Dao.xml' | wc -l | tr -d ' ')" = "15"

grep -R -q '^package com.opensocket.aievent.database.persistence..*\.dao;' "$PLATFORM/src/main/java"
grep -R -q '^package com.opensocket.aievent.database.persistence..*\.po;' "$PLATFORM/src/main/java"
! grep -R --include='*.java' -q 'com.opensocket.aievent.core.infrastructure.mybatis' ai-event-gateway-core-*/src/main/java ai-event-gateway-database-platform/src/main/java
grep -q 'com.opensocket.aievent.database.persistence' ai-event-gateway-core-app/src/main/resources/application-prod.yml
grep -q 'classpath\*:mybatis/postgresql/\*\*/\*.xml' ai-event-gateway-core-app/src/main/resources/application-prod.yml
! grep -q 'DATABASE_PLATFORM_MAPPER_LOCATIONS' deploy/env/.env.core-common.example

grep -q "$VERSION" README.md
grep -q "$VERSION" ai-event-gateway-core-kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java
./scripts/verify/verify-sharedutility-mybatis-persistence.sh

echo "P2 database DAO/PO consolidation verification passed."
