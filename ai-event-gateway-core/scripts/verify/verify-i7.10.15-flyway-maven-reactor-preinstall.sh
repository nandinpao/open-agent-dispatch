#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fail(){ echo "[ERROR] $*" >&2; exit 1; }

[[ -f "$ROOT_DIR/scripts/db/_maven_reactor.sh" ]] || fail "Missing scripts/db/_maven_reactor.sh"
grep -q "ensure_reactor_artifacts_installed_for_app" "$ROOT_DIR/scripts/db/_maven_reactor.sh" || fail "_maven_reactor.sh must define ensure_reactor_artifacts_installed_for_app."
grep -q -- "-pl control-plane-app -am" "$ROOT_DIR/scripts/db/_maven_reactor.sh" || fail "_maven_reactor.sh must install the app reactor closure with -pl control-plane-app -am."
grep -q -- "-Dspring-boot.skip=true" "$ROOT_DIR/scripts/db/_maven_reactor.sh" || fail "Reactor preinstall must skip Spring Boot run/repackage side effects."
grep -q "clean_opensocket_failed_resolution_cache" "$ROOT_DIR/scripts/db/_maven_reactor.sh" || fail "_maven_reactor.sh must clear stale com.opensocket resolution cache markers."

for f in flyway-migrate-postgres.sh flyway-validate-postgres.sh flyway-info-postgres.sh flyway-repair-postgres.sh; do
  path="$ROOT_DIR/scripts/db/$f"
  [[ -f "$path" ]] || fail "Missing $path"
  bash -n "$path" || fail "Shell syntax failed: scripts/db/$f"
  grep -q 'source "${SCRIPT_DIR}/_maven_reactor.sh"' "$path" || fail "$f must source _maven_reactor.sh."
  grep -q 'ensure_reactor_artifacts_installed_for_app "${ROOT_DIR}"' "$path" || fail "$f must preinstall reactor artifacts before invoking Flyway Maven plugin."
  grep -q 'control-plane-app/pom.xml' "$path" || fail "$f must keep Flyway goal scoped to the app module pom."
done

bash -n "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "Shell syntax failed: scripts/dev/run-core-local.sh"
grep -q 'ensure_reactor_artifacts_installed_for_app "$ROOT_DIR"' "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "run-core-local.sh must preinstall reactor artifacts once before Flyway."
grep -q 'MAVEN_REACTOR_PREINSTALL_ENABLED=false' "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "run-core-local.sh must disable duplicate preinstall in nested Flyway scripts."
grep -q 'flyway-migrate-postgres.sh' "$ROOT_DIR/scripts/dev/run-core-local.sh" || fail "run-core-local.sh must keep Flyway migrate prestart."

[[ -f "$ROOT_DIR/docs/i7.10.15-flyway-maven-reactor-preinstall.md" ]] || fail "Missing I7.10.15 docs."
grep -q "maven-default-http-blocker" "$ROOT_DIR/docs/i7.10.15-flyway-maven-reactor-preinstall.md" || fail "I7.10.15 docs must explain the maven-default-http-blocker symptom."

echo "I7.10.15 Flyway Maven reactor preinstall verification passed."
