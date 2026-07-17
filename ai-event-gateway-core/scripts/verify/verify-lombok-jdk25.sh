#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CORE_POM="$ROOT_DIR/pom.xml"

grep -q '<lombok.version>1.18.46</lombok.version>' "$CORE_POM"
grep -q '<maven.compiler.release>25</maven.compiler.release>' "$CORE_POM"
grep -q '<version>3.14.1</version>' "$CORE_POM"

if [[ -f "$ROOT_DIR/shared-utility/pom.xml" ]]; then
  SHARED_POM="$ROOT_DIR/shared-utility/pom.xml"
  DB_POM="$ROOT_DIR/shared-utility/database/pom.xml"
  REDISSON_POM="$ROOT_DIR/shared-utility/redisson-client/pom.xml"
  grep -q '<lombok.version>1.18.46</lombok.version>' "$SHARED_POM"
  for pom in "$DB_POM" "$REDISSON_POM"; do
    grep -q '<artifactId>lombok</artifactId>' "$pom"
    grep -q '<annotationProcessorPaths>' "$pom"
    grep -q '<version>3.14.1</version>' "$pom"
    grep -q '<maven.compiler.release>25</maven.compiler.release>' "$pom"
  done
else
  grep -q '<shared.utility.version>1.0.0</shared.utility.version>' "$CORE_POM"
  echo 'SharedUtility source checkout not present; verified Core Lombok/JDK 25 and managed artifact version.'
fi

echo 'P12.17 Lombok JDK 25 verification passed.'
