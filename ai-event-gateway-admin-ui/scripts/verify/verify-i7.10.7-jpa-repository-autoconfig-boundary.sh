#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() { echo "[I7.10.7][CORE][FAIL] $1" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || fail "Missing required file: $1"; }

APP_YML="ai-event-gateway-core-app/src/main/resources/application.yml"
PROD_YML="ai-event-gateway-core-app/src/main/resources/application-prod.yml"
APP_CLASS="ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/AiEventGatewayCoreApplication.java"
DOC="docs/i7.10.7-jpa-repository-autoconfig-boundary.md"

for f in "$APP_YML" "$PROD_YML" "$APP_CLASS" "$DOC"; do
  require_file "$f"
done

for cls in \
  'org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration' \
  'org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration'; do
  grep -q "$cls" "$APP_YML" || fail "application.yml must exclude $cls"
  grep -q "$cls" "$APP_CLASS" || fail "AiEventGatewayCoreApplication must exclude $cls"
done

grep -q 'repositories:' "$APP_YML" || fail "application.yml must disable spring.data.jpa.repositories"
grep -q 'enabled: false' "$APP_YML" || fail "application.yml must set spring.data.jpa.repositories.enabled=false"
grep -q 'repositories:' "$PROD_YML" || fail "application-prod.yml must disable spring.data.jpa.repositories"
grep -q 'enabled: false' "$PROD_YML" || fail "application-prod.yml must set spring.data.jpa.repositories.enabled=false"

if grep -R -n '@EnableJpaRepositories\|JpaRepository\|EntityManager\|@Entity\|spring-boot-starter-data-jpa' \
  ai-event-gateway-core-* ai-event-gateway-database-platform ai-event-gateway-adapter-worker-app \
  --include='*.java' --include='pom.xml' 2>/dev/null; then
  fail "Core runtime must remain MyBatis-only and must not introduce JPA repository/entity APIs"
fi

grep -q 'DataJpaRepositoriesAutoConfiguration' "$DOC" || fail "I7.10.7 doc must explain DataJpaRepositoriesAutoConfiguration exclusion"
grep -q 'jpaSharedEM_entityManagerFactory' "$DOC" || fail "I7.10.7 doc must mention the jpaSharedEM failure mode"
grep -q 'spring.data.jpa.repositories.enabled=false' "$DOC" || fail "I7.10.7 doc must document repository disable property"

echo "I7.10.7 Core JPA repository auto-configuration boundary verification passed."
