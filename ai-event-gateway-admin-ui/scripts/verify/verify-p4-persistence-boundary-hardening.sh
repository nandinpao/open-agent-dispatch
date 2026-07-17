#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"
PLATFORM="ai-event-gateway-database-platform"
JAVA_ROOT="$PLATFORM/src/main/java/com/opensocket/aievent/database"
PERSISTENCE_ROOT="$JAVA_ROOT/persistence"

# Infrastructure auto-configuration must not scan the entire persistence tree.
if grep -q '@ComponentScan' "$JAVA_ROOT/config/DatabasePlatformAutoConfiguration.java"; then
  echo "DatabasePlatformAutoConfiguration must not own persistence component scanning" >&2
  exit 1
fi
grep -q 'basePackageClasses = DatabasePersistenceModule.class' \
  "$JAVA_ROOT/config/DatabasePersistenceAutoConfiguration.java"
grep -q 'useDefaultFilters = false' \
  "$JAVA_ROOT/config/DatabasePersistenceAutoConfiguration.java"
grep -q 'DatabaseRepositoryAdapter.class' \
  "$JAVA_ROOT/config/DatabasePersistenceAutoConfiguration.java"
grep -q 'DatabasePersistenceConverter.class' \
  "$JAVA_ROOT/config/DatabasePersistenceAutoConfiguration.java"

grep -q 'DatabasePlatformAutoConfiguration' \
  "$PLATFORM/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
grep -q 'DatabasePersistenceAutoConfiguration' \
  "$PLATFORM/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

# All persistence adapters/converters must use the approved custom stereotypes.
test "$(find "$PERSISTENCE_ROOT" -path '*/repository/Mybatis*Repository.java' | wc -l | tr -d ' ')" = "15"
test "$(find "$PERSISTENCE_ROOT" -path '*/converter/*PersistenceConverter.java' | wc -l | tr -d ' ')" = "15"
while IFS= read -r file; do
  grep -q '@DatabaseRepositoryAdapter' "$file" || {
    echo "$file does not use @DatabaseRepositoryAdapter" >&2
    exit 1
  }
  if grep -q 'org.springframework.stereotype.Repository' "$file"; then
    echo "$file must not use raw @Repository" >&2
    exit 1
  fi
done < <(find "$PERSISTENCE_ROOT" -path '*/repository/Mybatis*Repository.java' | sort)
while IFS= read -r file; do
  grep -q '@DatabasePersistenceConverter' "$file" || {
    echo "$file does not use @DatabasePersistenceConverter" >&2
    exit 1
  }
  if grep -q 'org.springframework.stereotype.Component' "$file"; then
    echo "$file must not use raw @Component" >&2
    exit 1
  fi
done < <(find "$PERSISTENCE_ROOT" -path '*/converter/*PersistenceConverter.java' | sort)

# Core/application layers must never import database DAO, PO, or converter types.
if grep -RIn --include='*.java' -E \
  'com\.opensocket\.aievent\.database\.persistence\..*\.(dao|po|converter)\.' \
  ai-event-gateway-core-*/src/main/java; then
  echo "Core application/domain code must not import DAO, PO, or persistence converter types" >&2
  exit 1
fi

# Feature dependencies are compile-time contracts only and must not be re-exported by the platform.
FEATURE_MODULES=(
  ai-event-gateway-core-contracts
  ai-event-gateway-core-domain-events
  ai-event-gateway-core-integration-events
  ai-event-gateway-core-incident
  ai-event-gateway-core-event-processing
  ai-event-gateway-core-agent-control
  ai-event-gateway-core-task-orchestration
  ai-event-gateway-core-execution-control
  ai-event-gateway-core-adapter-action
)
python3 - "${FEATURE_MODULES[@]}" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
pom=Path('ai-event-gateway-database-platform/pom.xml')
root=ET.parse(pom).getroot()
ns={'m':'http://maven.apache.org/POM/4.0.0'}
deps={d.findtext('m:artifactId',namespaces=ns): d for d in root.findall('./m:dependencies/m:dependency',ns)}
for artifact in sys.argv[1:]:
    dep=deps.get(artifact)
    if dep is None:
        raise SystemExit(f'missing platform contract dependency: {artifact}')
    optional=dep.findtext('m:optional',namespaces=ns)
    if optional != 'true':
        raise SystemExit(f'platform contract dependency must be optional: {artifact}')
PY

# Platform imports from Core are frozen to reviewed Repository/domain contracts.
python3 <<'PY'
import csv
import re
import sys
from pathlib import Path
root=Path('.')
java=root/'ai-event-gateway-database-platform/src/main/java'
pattern=re.compile(r'^import\s+(com\.opensocket\.aievent\.core\.[\w.]+);',re.M)
actual=set()
for path in java.rglob('*.java'):
    actual.update(pattern.findall(path.read_text()))
with (root/'architecture/baseline/p4-database-platform-contract-imports.csv').open(newline='') as handle:
    approved={row['imported_type'] for row in csv.DictReader(handle)}
if actual != approved:
    new=sorted(actual-approved)
    stale=sorted(approved-actual)
    if new:
        print('Unreviewed database-platform Core imports:', *new, sep='\n  ', file=sys.stderr)
    if stale:
        print('Stale database-platform import baseline:', *stale, sep='\n  ', file=sys.stderr)
    raise SystemExit(1)
for imported in actual:
    if imported.endswith(('Service','Facade','Controller','Properties','Configuration','Publisher','Client')):
        raise SystemExit(f'Runtime/application type is not a persistence contract: {imported}')
PY

# Remove transition-only aliases and duplicate database location settings.
test ! -f ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/store/CoreStoreMode.java
if grep -q 'STORE: POSTGRES' deploy/docker/docker-compose.core-hybrid-worker.yml; then
  echo 'Hybrid worker compose still uses the deprecated POSTGRES store alias' >&2
  exit 1
fi
if grep -RqE 'DATABASE_PLATFORM_(MAPPER|MIGRATION)_LOCATIONS' \
  ai-event-gateway-core-app/src/main/resources deploy/env \
  "$PLATFORM/src/main"; then
  echo 'Duplicate database-platform mapper/migration location settings remain' >&2
  exit 1
fi

grep -q "$VERSION" README.md
grep -q "$VERSION" ai-event-gateway-core-kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

echo "P4 persistence boundary hardening verification passed."
