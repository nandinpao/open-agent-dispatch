#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
EXPECTED_GROUP_ID="com.opensocket"
EXPECTED_ARTIFACT_ID="control-plane-parent"
EXPECTED_VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "Required file not found: $1"
}

require_file "$ROOT_DIR/pom.xml"
require_file "$ROOT_DIR/control-plane-app/pom.xml"

if ! grep -q "<artifactId>${EXPECTED_ARTIFACT_ID}</artifactId>" "$ROOT_DIR/pom.xml"; then
  fail "This script must be run from a complete ai-event-gateway-core source tree. $ROOT_DIR/pom.xml is not ${EXPECTED_ARTIFACT_ID}. Re-extract the full Core zip; do not copy only module directories over an older workspace."
fi

if ! grep -q "<version>${EXPECTED_VERSION}</version>" "$ROOT_DIR/pom.xml"; then
  fail "Core parent version mismatch in $ROOT_DIR/pom.xml. Re-extract the full I7.10.9 Core zip."
fi

bad_modules=0
while IFS= read -r pom; do
  if [[ "$pom" == "$ROOT_DIR/pom.xml" ]]; then
    continue
  fi
  if grep -q "<artifactId>${EXPECTED_ARTIFACT_ID}</artifactId>" "$pom"; then
    parent_path="$(grep -oE '<relativePath>[^<]+</relativePath>' "$pom" | sed -E 's#</?relativePath>##g' | head -1 || true)"
    if [[ "$parent_path" != "../pom.xml" ]]; then
      echo "[ERROR] Invalid parent relativePath in ${pom#$ROOT_DIR/}: ${parent_path}" >&2
      bad_modules=$((bad_modules + 1))
    fi
  fi
done < <(find "$ROOT_DIR" -mindepth 2 -maxdepth 2 -name pom.xml | sort)

if [[ "$bad_modules" -ne 0 ]]; then
  fail "One or more module POMs do not point to ../pom.xml."
fi


HOST_LOCAL_ENV="$ROOT_DIR/deploy/env/.env.core-host-local.example"
if [[ -f "$HOST_LOCAL_ENV" ]]; then
  echo "[INFO] Loading host-local defaults from ${HOST_LOCAL_ENV}. Existing exported variables still take precedence when explicitly set before running this script."
  while IFS='=' read -r key value; do
    [[ -z "${key}" || "${key}" =~ ^# ]] && continue
    if [[ -z "${!key:-}" ]]; then
      export "$key=$value"
    fi
  done < "$HOST_LOCAL_ENV"
fi



# Maven caches failed remote model lookups. Remove only this project-local parent cache to avoid stale failure reuse.
if [[ -d "$HOME/.m2/repository/com/opensocket/control-plane-parent" ]]; then
  echo "[INFO] Removing cached control-plane-parent from ~/.m2 to avoid stale failed parent resolution."
  rm -rf "$HOME/.m2/repository/com/opensocket/control-plane-parent"
fi

# Install required reactor artifacts before invoking Flyway from the app module.
# Flyway Maven plugin is executed from control-plane-app/pom.xml only; without
# this preinstall Maven may try to resolve internal com.opensocket modules remotely.
source "$ROOT_DIR/scripts/db/_maven_reactor.sh"
ensure_reactor_artifacts_installed_for_app "$ROOT_DIR"
export MAVEN_REACTOR_PREINSTALL_ENABLED=false


if [[ "${FLYWAY_PRESTART_ENABLED:-true}" == "true" ]]; then
  echo "[INFO] Running Flyway migrate before starting Core."
  if ! SQL_ENV=local "$ROOT_DIR/scripts/db/flyway-migrate-postgres.sh"; then
    cat >&2 <<'MSG'
[ERROR] Flyway migration failed.
For an existing non-empty database without flyway_schema_history, do NOT let the application silently create tables.
Choose one of these explicit paths:
  1) Empty / disposable local DB: drop/recreate the schema/database, then run this script again.
  2) Existing schema that exactly matches a previous release: run Flyway baseline with an approved FLYWAY_BASELINE_VERSION, then migrate.
  3) DBA-managed production-like DB: run scripts/db/flyway-info-postgres.sh and resolve manually.
MSG
    exit 1
  fi
  SQL_ENV=local "$ROOT_DIR/scripts/db/flyway-validate-postgres.sh"
  echo "[INFO] Verifying schema objects. Host psql is optional; verify-postgres-schema.sh can use Docker PostgreSQL client modes."
  SQL_ENV=local "$ROOT_DIR/scripts/db/verify-postgres-schema.sh"
else
  echo "[WARN] FLYWAY_PRESTART_ENABLED=false; skipping Flyway prestart migration." >&2
fi


cd "$ROOT_DIR"
exec mvn -U -f "$ROOT_DIR/pom.xml" -pl control-plane-app -am spring-boot:run -Dspring-boot.run.profiles=local "$@"
