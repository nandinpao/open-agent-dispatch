#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fail(){ echo "[I7.10.10][FAIL] $1" >&2; exit 1; }

ROOT_POM="$ROOT_DIR/pom.xml"
APP_POM="$ROOT_DIR/ai-event-gateway-core-app/pom.xml"
RUN_SCRIPT="$ROOT_DIR/scripts/dev/run-core-local.sh"
DOC="$ROOT_DIR/docs/i7.10.10-spring-boot-run-reactor-boundary-fix.md"

[[ -f "$ROOT_POM" ]] || fail "Missing root pom.xml"
[[ -f "$APP_POM" ]] || fail "Missing app pom.xml"
[[ -f "$RUN_SCRIPT" ]] || fail "Missing scripts/dev/run-core-local.sh"
[[ -f "$DOC" ]] || fail "Missing I7.10.10 documentation"

grep -q '<artifactId>ai-event-gateway-core-parent</artifactId>' "$ROOT_POM" || fail "Root pom is not the Core parent"
grep -q '<packaging>pom</packaging>' "$ROOT_POM" || fail "Root parent must remain packaging=pom"
grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' "$ROOT_POM" || fail "Root pom must declare spring-boot-maven-plugin skip guard"
grep -q '<skip>true</skip>' "$ROOT_POM" || fail "Root / inherited Spring Boot Maven plugin must default skip=true"

grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' "$APP_POM" || fail "App pom must declare spring-boot-maven-plugin"
grep -q '<skip>false</skip>' "$APP_POM" || fail "App Spring Boot Maven plugin must override skip=false"
grep -q '<mainClass>com.opensocket.aievent.core.AiEventGatewayCoreApplication</mainClass>' "$APP_POM" || fail "App Spring Boot Maven plugin must set the Core mainClass"

grep -q -- '-pl ai-event-gateway-core-app -am spring-boot:run' "$RUN_SCRIPT" || fail "run-core-local.sh must run spring-boot:run against the app module with -am"

# Prevent a future regression where the parent skip guard is removed while keeping docs/scripts unchanged.
if grep -R -n '<skip>false</skip>' "$ROOT_DIR" --include='pom.xml' | grep -v 'ai-event-gateway-core-app/pom.xml' >/dev/null; then
  fail "Only ai-event-gateway-core-app may enable spring-boot:run with skip=false"
fi

echo "I7.10.10 Core Spring Boot run reactor boundary verification passed."
