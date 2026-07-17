#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POM="$ROOT_DIR/shared-utility/redisson-client/pom.xml"
PARENT_POM="$ROOT_DIR/shared-utility/pom.xml"

if [[ -f "$POM" ]]; then
  for artifact in spring-boot spring-boot-autoconfigure spring-context spring-beans redisson jackson-databind micrometer-core micrometer-registry-prometheus; do
    grep -q "<artifactId>${artifact}</artifactId>" "$POM" || {
      echo "ERROR: redisson-client is missing dependency artifact: ${artifact}" >&2; exit 1; }
  done
  for artifact in spring-boot spring-boot-autoconfigure spring-context spring-beans; do
    grep -q "<artifactId>${artifact}</artifactId>" "$PARENT_POM" || {
      echo "ERROR: shared-utility parent is missing dependency management for: ${artifact}" >&2; exit 1; }
  done
  if grep -R --include='pom.xml' -n '1.16.0-M3\|2.19.0-rc2\|spring-boot-starter-aop' "$ROOT_DIR/shared-utility"; then
    echo 'ERROR: unsupported milestone/RC or AOP starter dependency remains.' >&2; exit 1
  fi
else
  grep -q '<artifactId>redisson-client</artifactId>' "$ROOT_DIR/control-plane-app/pom.xml"
  grep -q '<version>${shared.utility.version}</version>' "$ROOT_DIR/control-plane-app/pom.xml"
  echo 'SharedUtility source checkout not present; verified managed redisson-client integration.'
fi

echo "P12.20 Redisson Spring dependency verification passed."
