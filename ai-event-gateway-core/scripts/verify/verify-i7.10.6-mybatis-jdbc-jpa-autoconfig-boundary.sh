#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() { echo "[I7.10.6][CORE][FAIL] $1" >&2; exit 1; }
require_file() { [[ -f "$1" ]] || fail "Missing required file: $1"; }

APP_POM="control-plane-app/pom.xml"
DB_POM="database-platform/pom.xml"
APP_YML="control-plane-app/src/main/resources/application.yml"
APP_CLASS="control-plane-app/src/main/java/com/opensocket/aievent/core/AiEventGatewayCoreApplication.java"
DOC="docs/i7.10.6-mybatis-jdbc-jpa-autoconfig-boundary.md"

for f in "$APP_POM" "$DB_POM" "$APP_YML" "$APP_CLASS" "$DOC"; do
  require_file "$f"
done

# spring-jdbc must be available at runtime. A direct test-scoped dependency in core-app
# can override transitive compile/runtime dependencies, so reject that configuration.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def find_dependencies(path: str, artifact: str):
    root = ET.parse(path).getroot()
    blocks = []
    for dep in root.findall(".//m:dependency", NS):
        aid = dep.findtext("m:artifactId", default="", namespaces=NS)
        if aid == artifact:
            scope = dep.findtext("m:scope", default="", namespaces=NS)
            blocks.append(scope.strip())
    return blocks

app_scopes = find_dependencies("control-plane-app/pom.xml", "spring-jdbc")
db_scopes = find_dependencies("database-platform/pom.xml", "spring-jdbc")
if not app_scopes:
    raise SystemExit("core app pom must declare spring-jdbc")
if not db_scopes:
    raise SystemExit("database platform pom must declare spring-jdbc")
if any(scope == "test" for scope in app_scopes):
    raise SystemExit("core app spring-jdbc must not be test-scoped")
if any(scope == "test" for scope in db_scopes):
    raise SystemExit("database platform spring-jdbc must not be test-scoped")
PY

grep -q 'org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration' "$APP_YML" \
  || fail "application.yml must exclude Boot 4 HibernateJpaAutoConfiguration"
grep -q 'org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration' "$APP_YML" \
  || fail "application.yml must exclude Boot 4 DataJpaRepositoriesAutoConfiguration"
grep -q 'org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration' "$APP_CLASS" \
  || fail "AiEventGatewayCoreApplication must explicitly exclude Boot 4 HibernateJpaAutoConfiguration"
grep -q 'org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration' "$APP_CLASS" \
  || fail "AiEventGatewayCoreApplication must explicitly exclude Boot 4 DataJpaRepositoriesAutoConfiguration"

if grep -R -n '@Entity\|EntityManager\|JpaRepository\|spring-boot-starter-data-jpa' \
  */src/main/java */pom.xml \
  --include='*.java' --include='pom.xml' 2>/dev/null; then
  fail "Core runtime must remain MyBatis-only and must not introduce JPA entities/repositories/starter"
fi

grep -q 'MyBatis' "$DOC" || fail "I7.10.6 doc must explain MyBatis runtime boundary"
grep -q 'spring-jdbc' "$DOC" || fail "I7.10.6 doc must explain spring-jdbc runtime dependency"
grep -q 'HibernateJpaAutoConfiguration' "$DOC" || fail "I7.10.6 doc must explain Hibernate JPA auto-config exclusion"

echo "I7.10.6 Core MyBatis JDBC / JPA auto-configuration boundary verification passed."
