#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

./scripts/verify/verify-pom-build-closure.sh
./scripts/architecture/verify-dependency-baseline.py

# Kernel and contracts are framework-neutral. Validation annotations are allowed in contracts.
! grep -R --include='pom.xml' -q 'spring-boot-starter' kernel contracts
! grep -R --include='*.java' -q 'org.springframework' kernel/src/main/java contracts/src/main/java

# Physical ownership of moved contracts and database resources.
test -f data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java
test -f data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackRequest.java
test -f data-model/src/main/java/com/opensocket/aievent/core/dispatch/NettyDispatchCommand.java
test -f data-model/src/main/java/com/opensocket/aievent/core/api/ApiErrorResponse.java

test ! -e control-plane-app/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java
test ! -e control-plane-app/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackRequest.java
test ! -e control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/NettyDispatchCommand.java

test "$(find database-platform/src/main/resources/db/migration -type f -name 'V*.sql' | wc -l | tr -d ' ')" -ge "17"
test ! -d control-plane-app/src/main/resources/db/migration

test "$(find data-model/src/main/java/com/opensocket/aievent/database/persistence -path '*/dao/*Dao.java' | wc -l | tr -d ' ')" -ge "15"
test "$(find database-platform/src/main/resources/mybatis/postgresql -type f -name '*Dao.xml' | wc -l | tr -d ' ')" -ge "15"
test ! -d control-plane-app/src/main/resources/mybatis/postgresql
test -f database-platform/pom.xml
test -f database-platform/src/main/java/com/opensocket/aievent/database/DatabasePlatformModule.java

grep -q "${VERSION}" README.md
grep -q "${VERSION}" kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java
test -f control-plane-app/src/test/java/com/opensocket/aievent/core/architecture/M1FoundationModuleStructureTest.java

echo "M1 foundation module structure verification passed."
