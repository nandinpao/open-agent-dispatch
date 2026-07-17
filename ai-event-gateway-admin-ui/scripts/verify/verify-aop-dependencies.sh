#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if grep -R --include='pom.xml' "spring-boot-starter-aop" -n . --exclude-dir=target 2>/dev/null; then
  echo "ERROR: spring-boot-starter-aop must not be used with Spring Boot 4.x." >&2
  exit 1
fi
if grep -R --include='pom.xml' "repository.springsource.com\|com.springsource.repository" -n . --exclude-dir=target 2>/dev/null; then
  echo "ERROR: obsolete SpringSource HTTP repository remains in project POMs." >&2
  exit 1
fi

if [[ -f shared-utility/database/pom.xml ]]; then
  grep -R -q '<artifactId>spring-aop</artifactId>' shared-utility/pom.xml shared-utility/database/pom.xml
  grep -R -q '<artifactId>aspectjweaver</artifactId>' shared-utility/pom.xml shared-utility/database/pom.xml
else
  grep -q '<artifactId>database</artifactId>' ai-event-gateway-database-platform/pom.xml
  grep -q '<version>${shared.utility.version}</version>' ai-event-gateway-database-platform/pom.xml
  echo 'SharedUtility source checkout not present; verified external database artifact and forbidden AOP dependencies.'
fi

echo "P12.18 AOP dependency verification passed."
