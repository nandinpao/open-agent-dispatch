#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SHARED_POM="$ROOT_DIR/shared-utility/pom.xml"
DB_POM="$ROOT_DIR/shared-utility/database/pom.xml"

if [[ -f "$SHARED_POM" && -f "$DB_POM" ]]; then
  grep -q '<spring.boot.version>4.0.6</spring.boot.version>' "$SHARED_POM"
  grep -q '<spring.framework.version>7.0.7</spring.framework.version>' "$SHARED_POM"
  grep -q '<artifactId>spring-jdbc</artifactId>' "$DB_POM"
  grep -A3 '<artifactId>spring-jdbc</artifactId>' "$DB_POM" | grep -q '<version>${spring.framework.version}</version>'
  grep -q '<artifactId>spring-boot-starter-data-jpa</artifactId>' "$DB_POM"
  grep -A3 '<artifactId>spring-boot-starter-data-jpa</artifactId>' "$DB_POM" | grep -q '<version>${spring.boot.version}</version>'
else
  grep -q '<parent>' "$ROOT_DIR/pom.xml"
  grep -q '<version>4.0.6</version>' "$ROOT_DIR/pom.xml"
  grep -q '<shared.utility.version>1.0.0</shared.utility.version>' "$ROOT_DIR/pom.xml"
  grep -q '<artifactId>database</artifactId>' "$ROOT_DIR/ai-event-gateway-database-platform/pom.xml"
  grep -q '<version>${shared.utility.version}</version>' "$ROOT_DIR/ai-event-gateway-database-platform/pom.xml"
  echo 'SharedUtility source checkout not present; verified Boot 4 and managed external database artifact.'
fi

echo "P12.17 SharedUtility dependency management verification passed."
