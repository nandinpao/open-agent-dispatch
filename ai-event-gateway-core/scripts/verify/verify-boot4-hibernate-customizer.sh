#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVA_FILE="$ROOT_DIR/shared-utility/database/src/main/java/com/agitg/database/aop/jpa/JpaExtAutoConfiguration.java"

if [[ -f "$JAVA_FILE" ]]; then
  if grep -R "org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer" -n "$ROOT_DIR/shared-utility/database/src/main/java" 2>/dev/null; then
    echo "ERROR: Spring Boot 3.x HibernatePropertiesCustomizer package is still used." >&2
    exit 1
  fi
  grep -q "org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer" "$JAVA_FILE"
  grep -R --include='pom.xml' -q '<artifactId>spring-boot-hibernate</artifactId>' "$ROOT_DIR/shared-utility"
  if grep -R --include='pom.xml' -q 'spring-boot-starter-aop' "$ROOT_DIR/shared-utility"; then
    echo "ERROR: spring-boot-starter-aop must not be used." >&2
    exit 1
  fi
else
  grep -q '<artifactId>database</artifactId>' "$ROOT_DIR/database-platform/pom.xml"
  grep -q '<version>${shared.utility.version}</version>' "$ROOT_DIR/database-platform/pom.xml"
  echo 'SharedUtility source checkout not present; verified the managed external database artifact.'
fi

echo "P12.19 Boot 4 Hibernate customizer verification passed."
