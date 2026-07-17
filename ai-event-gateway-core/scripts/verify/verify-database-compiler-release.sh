#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

# The Core source release must always retain the JDK 25 compiler contract.
grep -q '<maven.compiler.release>25</maven.compiler.release>' "$ROOT_DIR/pom.xml"
grep -q '<version>3.14.1</version>' "$ROOT_DIR/pom.xml"
if grep -R --include='pom.xml' -n '<maven.compiler.source>1.8</maven.compiler.source>\|<source>1.8</source>\|--enable-preview\|<enablePreview>true</enablePreview>' "$ROOT_DIR" --exclude-dir=target >/tmp/aeg_p12_17_compiler_bad.txt; then
  cat /tmp/aeg_p12_17_compiler_bad.txt
  echo 'ERROR: project contains Java 1.8 or preview compiler settings.' >&2
  exit 1
fi

if [[ -f "$ROOT_DIR/shared-utility/database/pom.xml" ]]; then
  for pom in "$ROOT_DIR/shared-utility/pom.xml" "$ROOT_DIR/shared-utility/database/pom.xml" "$ROOT_DIR/shared-utility/redisson-client/pom.xml"; do
    grep -q '<maven.compiler.release>25</maven.compiler.release>' "$pom"
  done
  grep -q 'pg.single' "$ROOT_DIR/shared-utility/database/src/main/java/com/agitg/database/DatabaseClusterConfig.java"
  grep -q '@Conditional(PgRoutingCondition.class)' "$ROOT_DIR/shared-utility/database/src/main/java/com/agitg/database/DatabaseClusterConfig.java"
else
  grep -q '<shared.utility.version>1.0.0</shared.utility.version>' "$ROOT_DIR/pom.xml"
  grep -q '<artifactId>database</artifactId>' "$ROOT_DIR/database-platform/pom.xml"
  echo 'SharedUtility source checkout not present; verified Core JDK 25 and managed artifact contract.'
fi

echo 'P12.17 compiler preview/release verification passed.'
