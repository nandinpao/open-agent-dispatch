#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"
PLATFORM="database-platform"
PERSISTENCE="$PLATFORM/src/main/java/com/opensocket/aievent/database/persistence"

FEATURE_MODULES=(
  domain-events
  integration-events
  incident
  event-processing
  agent-control
  task-orchestration
  execution-control
)

test "$(find "$PERSISTENCE" -path '*/repository/Mybatis*Repository.java' | wc -l | tr -d ' ')" = "23"
test "$(find "$PERSISTENCE" -path '*/converter/*PersistenceConverter.java' | wc -l | tr -d ' ')" = "23"

for module in "${FEATURE_MODULES[@]}"; do
  if find "$module/src/main/java" -name 'Mybatis*Repository.java' | grep -q .; then
    echo "$module still owns a MyBatis Repository adapter" >&2
    exit 1
  fi
  if grep -RIn --include='*.java' 'com.opensocket.aievent.database.persistence' "$module/src/main/java"; then
    echo "$module production code still imports database-platform persistence types" >&2
    exit 1
  fi
  if grep -q '<artifactId>database-platform</artifactId>' "$module/pom.xml"; then
    echo "$module must not depend on database-platform after P3" >&2
    exit 1
  fi
  grep -q "<artifactId>${module}</artifactId>" "$PLATFORM/pom.xml"
done

! grep -q '@ComponentScan' \
  "$PLATFORM/src/main/java/com/opensocket/aievent/database/config/DatabasePlatformAutoConfiguration.java"
grep -q 'basePackageClasses = DatabasePersistenceModule.class' \
  "$PLATFORM/src/main/java/com/opensocket/aievent/database/config/DatabasePersistenceAutoConfiguration.java"

test -f data-model/src/main/java/com/opensocket/aievent/core/decision/EventDecisionRepository.java
test -f data-model/src/main/java/com/opensocket/aievent/core/decision/EventDecisionRecord.java
test ! -f control-plane-app/src/main/java/com/opensocket/aievent/core/decision/EventDecisionRepository.java

grep -q "$VERSION" README.md
grep -q "$VERSION" kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

echo "P3 persistence adapter/converter consolidation verification passed."
