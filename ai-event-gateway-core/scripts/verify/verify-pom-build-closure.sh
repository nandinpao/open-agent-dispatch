#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"
MODULES=(
  kernel
  data-model
  contracts
  database-platform
  domain-events
  service-contracts
  integration-events
  incident
  event-processing
  agent-control
  task-orchestration
  execution-control
  adapter-action
  observability
  control-plane-app
  adapter-worker-app
)

for module in "${MODULES[@]}"; do
  test -f "${ROOT_DIR}/${module}/pom.xml"
  grep -q "<artifactId>${module}</artifactId>" "${ROOT_DIR}/${module}/pom.xml"
  grep -q "<version>${VERSION}</version>" "${ROOT_DIR}/${module}/pom.xml"
  grep -q '<relativePath>../pom.xml</relativePath>' "${ROOT_DIR}/${module}/pom.xml"
  grep -q "<module>${module}</module>" "${ROOT_DIR}/pom.xml"
done

grep -q "<version>${VERSION}</version>" "${ROOT_DIR}/pom.xml"

grep -q '<artifactId>kernel</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>contracts</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>database-platform</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
! grep -q '<artifactId>ai-event-gateway-core-db-support</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>domain-events</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>service-contracts</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>integration-events</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>incident</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>event-processing</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>agent-control</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>task-orchestration</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>execution-control</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>adapter-action</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>observability</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"

# M8 has two explicit deployables: the Core control plane and optional adapter worker.
# The root parent also declares spring-boot-maven-plugin with skip=true to protect
# reactor CLI goals such as -am spring-boot:run from executing on packaging=pom.
test "$(grep -R -l --include='pom.xml' '<artifactId>spring-boot-maven-plugin</artifactId>' "${ROOT_DIR}" | wc -l | tr -d ' ')" = "3"
grep -q '<skip>true</skip>' "${ROOT_DIR}/pom.xml"
grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' "${ROOT_DIR}/control-plane-app/pom.xml"
grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' "${ROOT_DIR}/adapter-worker-app/pom.xml"

# SharedUtility, Flyway, and runtime migrations are owned by database-platform.
grep -q '<artifactId>database</artifactId>' "${ROOT_DIR}/database-platform/pom.xml"
grep -q '<version>${shared.utility.version}</version>' "${ROOT_DIR}/database-platform/pom.xml"
! grep -q '<artifactId>ai-event-gateway-core-db-support</artifactId>' "${ROOT_DIR}/database-platform/pom.xml"
grep -q '<artifactId>redisson-client</artifactId>' "${ROOT_DIR}/event-processing/pom.xml"
test -f "${ROOT_DIR}/database-platform/src/main/resources/db/migration/V20__dispatch_claim_lease.sql"
test ! -e "${ROOT_DIR}/ai-event-gateway-core-db-migration"

python3 - <<'PY' "${ROOT_DIR}"
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path(sys.argv[1])
paths = [root / 'pom.xml', *sorted(root.glob('*/pom.xml'))]
for path in paths:
    ET.parse(path)
print(f'POM XML parse check passed for {len(paths)} files.')
PY

echo "M8 Maven reactor build closure verification passed."
