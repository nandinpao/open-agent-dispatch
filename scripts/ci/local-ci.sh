#!/usr/bin/env bash
set -euo pipefail

FAST=false
KEEP_STACK="${KEEP_STACK:-true}"
SKIP_ADMIN=false
SKIP_DOCKER=false

for arg in "$@"; do
  case "$arg" in
    --fast) FAST=true ;;
    --keep-stack) KEEP_STACK=true ;;
    --teardown|--no-keep-stack) KEEP_STACK=false ;;
    --skip-admin) SKIP_ADMIN=true ;;
    --skip-docker) SKIP_DOCKER=true ;;
    *) echo "Unknown argument: $arg" >&2; echo "Usage: $0 [--fast] [--keep-stack] [--teardown] [--skip-admin] [--skip-docker]" >&2; exit 2 ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
CI_PROJECT_NAME="${CI_PROJECT_NAME:-opendispatch-ci}"
CI_COMPOSE_FILE="${CI_COMPOSE_FILE:-deploy/docker-compose.ci.yml}"
CI_ENV_FILE="${CI_ENV_FILE:-deploy/env/.env.local.ci}"
ADMIN_UI_NEXT_DIST_DIR="${ADMIN_UI_NEXT_DIST_DIR:-.next-ci}"
CI_DB_MIGRATION_VOLUME="${CI_DB_MIGRATION_VOLUME:-${CI_PROJECT_NAME}-db-migration}"
CI_CORE_RUNTIME_VOLUME="${CI_CORE_RUNTIME_VOLUME:-${CI_PROJECT_NAME}-core-runtime}"
CI_NETTY_RUNTIME_VOLUME="${CI_NETTY_RUNTIME_VOLUME:-${CI_PROJECT_NAME}-netty-runtime}"
CI_ADAPTER_WORKER_RUNTIME_VOLUME="${CI_ADAPTER_WORKER_RUNTIME_VOLUME:-${CI_PROJECT_NAME}-adapter-worker-runtime}"
CI_ADMIN_UI_RUNTIME_VOLUME="${CI_ADMIN_UI_RUNTIME_VOLUME:-${CI_PROJECT_NAME}-admin-ui-runtime}"
CI_OTEL_COLLECTOR_CONFIG_VOLUME="${CI_OTEL_COLLECTOR_CONFIG_VOLUME:-${CI_PROJECT_NAME}-otel-collector-config}"
CI_FLYWAY_DIAGNOSTICS_VOLUME="${CI_FLYWAY_DIAGNOSTICS_VOLUME:-${CI_PROJECT_NAME}-flyway-diagnostics}"
CI_CORE_LOGS_VOLUME="${CI_CORE_LOGS_VOLUME:-${CI_PROJECT_NAME}-core-logs}"
CI_NETTY_LOGS_VOLUME="${CI_NETTY_LOGS_VOLUME:-${CI_PROJECT_NAME}-netty-logs}"
CI_ADAPTER_WORKER_LOGS_VOLUME="${CI_ADAPTER_WORKER_LOGS_VOLUME:-${CI_PROJECT_NAME}-adapter-worker-logs}"
CI_ADMIN_UI_LOGS_VOLUME="${CI_ADMIN_UI_LOGS_VOLUME:-${CI_PROJECT_NAME}-admin-ui-logs}"
CI_MOCK_AGENT_E2E_VOLUME="${CI_MOCK_AGENT_E2E_VOLUME:-${CI_PROJECT_NAME}-mock-agent-e2e}"
CI_VOLUME_SEED_IMAGE="${CI_VOLUME_SEED_IMAGE:-alpine:3.20}"
export CI_DB_MIGRATION_VOLUME CI_CORE_RUNTIME_VOLUME CI_NETTY_RUNTIME_VOLUME CI_ADAPTER_WORKER_RUNTIME_VOLUME CI_ADMIN_UI_RUNTIME_VOLUME CI_OTEL_COLLECTOR_CONFIG_VOLUME CI_FLYWAY_DIAGNOSTICS_VOLUME CI_CORE_LOGS_VOLUME CI_NETTY_LOGS_VOLUME CI_ADAPTER_WORKER_LOGS_VOLUME CI_ADMIN_UI_LOGS_VOLUME CI_MOCK_AGENT_E2E_VOLUME CI_VOLUME_SEED_IMAGE

# Local CI uses a single Netty container by default.  It must remain compatible
# with the transport simulator used by scripts/cluster-run-many-agents.sh.
# Shell variables from previous release/runtime-E2E runs can otherwise leak into
# docker compose and make Netty deny local simulator agents with
# AGENT_AUTHORIZATION_DISABLED.
CI_LOCAL_AGENT_AUTH_MODE="${CI_LOCAL_AGENT_AUTH_MODE:-transport}"
if [[ "${RUN_RUNTIME_LIFECYCLE_E2E:-false}" == "true" || "${CI_LOCAL_AGENT_AUTH_MODE}" == "core" ]]; then
  export GATEWAY_AGENT_AUTHORIZATION_ENABLED="${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-true}"
  export GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED="${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED:-false}"
else
  export GATEWAY_AGENT_AUTHORIZATION_ENABLED="false"
  export GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED="true"
fi
mkdir -p "${CI_OUTPUT_DIR}/logs" "${CI_OUTPUT_DIR}/reports" "${CI_OUTPUT_DIR}/compose" "${CI_OUTPUT_DIR}/images"

log() {
  echo ""
  echo "==> $*"
}

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

compose_ci() {
  docker compose -p "$CI_PROJECT_NAME" --env-file "$CI_ENV_FILE" -f "$CI_COMPOSE_FILE" "$@"
}

reset_docker_volume() {
  local volume="$1"
  docker volume rm "$volume" >/dev/null 2>&1 || true
  docker volume create "$volume" >/dev/null
}

seed_directory_volume() {
  local source_dir="$1"
  local volume="$2"
  local target_dir="$3"
  [[ -d "$source_dir" ]] || fail "Missing source directory for CI Docker volume seed: $source_dir"
  reset_docker_volume "$volume"
  COPYFILE_DISABLE=1 tar --exclude='._*' --exclude='.DS_Store' -C "$source_dir" -cf - . \
    | docker run --rm -i -v "${volume}:${target_dir}" "$CI_VOLUME_SEED_IMAGE" sh -c "cd '${target_dir}' && find . -maxdepth 1 -type f \( -name '._*' -o -name '.DS_Store' \) -exec rm -f {} + && tar -xf - && find . -maxdepth 1 -type f \( -name '._*' -o -name '.DS_Store' \) -exec rm -f {} +"
}

seed_file_volume() {
  local source_file="$1"
  local volume="$2"
  local target_dir="$3"
  local target_name="$4"
  [[ -f "$source_file" ]] || fail "Missing source file for CI Docker volume seed: $source_file"
  reset_docker_volume "$volume"
  tar -C "$(dirname "$source_file")" -cf - "$(basename "$source_file")" \
    | docker run --rm -i -v "${volume}:${target_dir}" "$CI_VOLUME_SEED_IMAGE" sh -c "cd '${target_dir}' && tar -xf - && mv '$(basename "$source_file")' '${target_name}'"
}

seed_ci_runtime_volumes() {
  log "Seeding shared runtime Docker volumes"
  seed_directory_volume "${ROOT_DIR}/ai-event-gateway-core/database-platform/src/main/resources/db/migration" "$CI_DB_MIGRATION_VOLUME" /flyway/sql
  seed_file_volume "${ROOT_DIR}/scripts/db/flyway-migrate-with-diagnostics.sh" "$CI_FLYWAY_DIAGNOSTICS_VOLUME" /flyway/diagnostics flyway-migrate-with-diagnostics.sh
  seed_file_volume "${CI_OUTPUT_DIR}/runtime/core.jar" "$CI_CORE_RUNTIME_VOLUME" /app ai-event-gateway-core.jar
  seed_file_volume "${CI_OUTPUT_DIR}/runtime/netty.jar" "$CI_NETTY_RUNTIME_VOLUME" /app ai-event-gateway-netty.jar
  seed_file_volume "${CI_OUTPUT_DIR}/runtime/adapter-worker.jar" "$CI_ADAPTER_WORKER_RUNTIME_VOLUME" /app ai-event-gateway-adapter-worker.jar
  seed_file_volume "${ROOT_DIR}/deploy/observability/otel-collector-config.yml" "$CI_OTEL_COLLECTOR_CONFIG_VOLUME" /etc/otelcol-contrib/config config.yaml
  seed_directory_volume "${ROOT_DIR}/ai-event-gateway-core/scripts/e2e" "$CI_MOCK_AGENT_E2E_VOLUME" /e2e
  if [[ "$SKIP_ADMIN" != "true" ]]; then
    seed_directory_volume "${CI_OUTPUT_DIR}/runtime/admin-ui" "$CI_ADMIN_UI_RUNTIME_VOLUME" /workspace/admin-ui
  else
    docker volume rm "$CI_ADMIN_UI_RUNTIME_VOLUME" >/dev/null 2>&1 || true
  fi
}

stop_legacy_host_admin() {
  # P13 now runs Admin UI as a shared Node runtime container. This only stops
  # stale host-process Admin UI instances left by older P13 revisions.
  CI_OUTPUT_DIR="$CI_OUTPUT_DIR" bash scripts/ci/local-admin-ui-host.sh stop --env-file "$CI_ENV_FILE" --output-dir "$CI_OUTPUT_DIR" >/dev/null 2>&1 || true
}

collect_reports() {
  mkdir -p "${CI_OUTPUT_DIR}/reports/core-surefire" "${CI_OUTPUT_DIR}/reports/netty-surefire"
  find ai-event-gateway-core -path '*/target/surefire-reports' -type d -print0 2>/dev/null \
    | while IFS= read -r -d '' dir; do
        module="$(basename "$(dirname "$(dirname "$dir")")")"
        mkdir -p "${CI_OUTPUT_DIR}/reports/core-surefire/${module}"
        cp -R "$dir"/. "${CI_OUTPUT_DIR}/reports/core-surefire/${module}/" 2>/dev/null || true
      done
  find ai-event-gateway-netty -path '*/target/surefire-reports' -type d -print0 2>/dev/null \
    | while IFS= read -r -d '' dir; do
        module="$(basename "$(dirname "$(dirname "$dir")")")"
        mkdir -p "${CI_OUTPUT_DIR}/reports/netty-surefire/${module}"
        cp -R "$dir"/. "${CI_OUTPUT_DIR}/reports/netty-surefire/${module}/" 2>/dev/null || true
      done
}

cleanup_on_error() {
  local code=$?
  cd "$ROOT_DIR" || true
  mkdir -p "${CI_OUTPUT_DIR}/logs" "${CI_OUTPUT_DIR}/reports" "${CI_OUTPUT_DIR}/compose"
  echo ""
  echo "[CI] Failed with exit code ${code}. Collecting diagnostics..."
  for service in core netty adapter-worker otel-collector admin-ui; do
    compose_ci logs --no-color --tail=180 "$service" > "${CI_OUTPUT_DIR}/logs/${service}.log" 2>/dev/null || true
    if [[ -s "${CI_OUTPUT_DIR}/logs/${service}.log" ]]; then
      echo "[CI] Recent ${service} container log:"
      tail -120 "${CI_OUTPUT_DIR}/logs/${service}.log" || true
    fi
  done
  compose_ci ps > "${CI_OUTPUT_DIR}/compose/ps.txt" 2>/dev/null || true
  compose_ci logs --no-color > "${CI_OUTPUT_DIR}/logs/compose.log" 2>/dev/null || true
  collect_reports || true
  exit "$code"
}
trap cleanup_on_error ERR

log "Stage 0 - Preflight"
if [[ -x scripts/dev/check-toolchain.sh ]]; then
  if [[ "$FAST" == "true" || "$SKIP_DOCKER" == "true" ]]; then
    bash scripts/dev/check-toolchain.sh --skip-docker
  else
    bash scripts/dev/check-toolchain.sh
  fi
fi
require_cmd java
require_cmd mvn
require_cmd node
require_cmd npm
require_cmd python3
if [[ "$FAST" != "true" && "$SKIP_DOCKER" != "true" ]]; then
  require_cmd docker
  docker compose version >/dev/null || fail "docker compose plugin is not available"
  [[ -f "$CI_COMPOSE_FILE" ]] || fail "Missing compose file: $CI_COMPOSE_FILE"
  [[ -f "$CI_ENV_FILE" ]] || fail "Missing env file: $CI_ENV_FILE"
fi

java -version
mvn -version | head -n 3
node -v
npm -v
if [[ "$FAST" != "true" && "$SKIP_DOCKER" != "true" ]]; then
  docker version --format 'Docker Server {{.Server.Version}}'
  docker compose version
fi

log "Stage 1 - Static code verify / repository hygiene"
python3 scripts/verify/verify-local-cicd.py

# Local CI focuses on executable code/test/runtime validation. Local cache/archive
# metadata is cleaned and reported, not treated as a hard-fail gate. Release
# packaging can still enforce a stricter artifact-pollution policy separately.
find . \( -path './.git' -o -path './.ci-output' -o -path './node_modules' \) -prune -o -name 'tsconfig.tsbuildinfo' -type f -print -delete \
  | tee "${CI_OUTPUT_DIR}/reports/cleaned-tsbuildinfo.txt" >/dev/null
find . \( -path './.git' -o -path './.ci-output' -o -path './node_modules' \) -prune -o -name '__MACOSX' -type d -print -exec rm -rf {} + \
  | tee "${CI_OUTPUT_DIR}/reports/cleaned-macosx-dirs.txt" >/dev/null
find . \( -path './.git' -o -path './.ci-output' -o -path './node_modules' \) -prune -o -name '._*' -type f -print -delete \
  | tee "${CI_OUTPUT_DIR}/reports/cleaned-appledouble-files.txt" >/dev/null

if grep -R "com\.fasterxml\.jackson" ai-event-gateway-core/*/src/main/java > "${CI_OUTPUT_DIR}/reports/jackson2-imports.txt" 2>/dev/null; then
  cat "${CI_OUTPUT_DIR}/reports/jackson2-imports.txt"
  fail "Jackson 2 imports found in production code. Use tools.jackson.*"
fi

log "Stage 2 - Core Java tests by module group"
pushd ai-event-gateway-core >/dev/null
mvn -pl kernel,data-model,contracts,domain-events,service-contracts,integration-events -am test
mvn -pl incident,event-processing,agent-control -am test
mvn -pl task-orchestration -am test
mvn -pl execution-control -am test
mvn -pl adapter-action,database-platform,observability -am test
mvn -pl control-plane-app,adapter-worker-app -am test
popd >/dev/null
collect_reports

log "Stage 3 - Netty Java tests"
pushd ai-event-gateway-netty >/dev/null
mvn test
popd >/dev/null
collect_reports

if [[ "$SKIP_ADMIN" != "true" ]]; then
  log "Stage 4 - Admin UI checks"
  bash scripts/ci/admin-ui-clean-generated.sh --best-effort --output-dir "$CI_OUTPUT_DIR" --project "$CI_PROJECT_NAME" --compose-file "$CI_COMPOSE_FILE" --env-file "$CI_ENV_FILE"
  pushd ai-event-gateway-admin-ui >/dev/null
  npm ci
  npm run test:normalizers
  npm run typecheck
  npm run lint
  npm run test:api-envelope
  NEXT_DIST_DIR="$ADMIN_UI_NEXT_DIST_DIR" npm run build
  if [[ "$FAST" != "true" ]]; then
    popd >/dev/null
    bash scripts/ci/prepare-admin-ui-runtime.sh --output-dir "$CI_OUTPUT_DIR" --build-dir "$ADMIN_UI_NEXT_DIST_DIR"
  else
    popd >/dev/null
  fi
else
  log "Stage 4 - Admin UI checks skipped by --skip-admin"
fi

if [[ "$FAST" == "true" || "$SKIP_DOCKER" == "true" ]]; then
  log "Fast/static mode completed. Skipping shared-runtime Compose smoke."
  exit 0
fi

log "Stage 5 - Package executable jars for shared Java 25 runtime"
mvn -U -f ai-event-gateway-core/pom.xml -pl control-plane-app,adapter-worker-app -am package -DskipTests
mvn -U -f ai-event-gateway-netty/pom.xml -pl gateway-app -am package -DskipTests
mkdir -p "${CI_OUTPUT_DIR}/runtime"
CORE_JAR="$(find ai-event-gateway-core/control-plane-app/target -maxdepth 1 -type f -name 'control-plane-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -n 1)"
ADAPTER_WORKER_JAR="$(find ai-event-gateway-core/adapter-worker-app/target -maxdepth 1 -type f -name 'adapter-worker-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -n 1)"
NETTY_JAR="$(find ai-event-gateway-netty/gateway-app/target -maxdepth 1 -type f -name 'gateway-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -n 1)"
[[ -n "$CORE_JAR" ]] || fail "Core executable jar was not produced"
[[ -n "$ADAPTER_WORKER_JAR" ]] || fail "Adapter Worker executable jar was not produced"
[[ -n "$NETTY_JAR" ]] || fail "Netty executable jar was not produced"
cp "$CORE_JAR" "${CI_OUTPUT_DIR}/runtime/core.jar"
cp "$ADAPTER_WORKER_JAR" "${CI_OUTPUT_DIR}/runtime/adapter-worker.jar"
cp "$NETTY_JAR" "${CI_OUTPUT_DIR}/runtime/netty.jar"

log "Stage 6 - Prepare shared runtime images"
cat > "${CI_OUTPUT_DIR}/images/runtime.env" <<IMGEOF
JAVA25_RUNTIME_IMAGE=${JAVA25_RUNTIME_IMAGE:-eclipse-temurin:25-jre}
POSTGRES_IMAGE=${POSTGRES_IMAGE:-postgres:18-alpine}
REDIS_IMAGE=${REDIS_IMAGE:-redis:8-alpine}
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:11-alpine}
NODE_RUNTIME_IMAGE=${NODE_RUNTIME_IMAGE:-node:22-bookworm-slim}
CORE_JAR=${CORE_JAR}
NETTY_JAR=${NETTY_JAR}
ADAPTER_WORKER_JAR=${ADAPTER_WORKER_JAR}
OTEL_COLLECTOR_IMAGE=${OTEL_COLLECTOR_IMAGE:-otel/opentelemetry-collector-contrib:0.156.0}
ADMIN_UI_RUNTIME=node-container
IMGEOF

log "Stage 7 - Compose up shared-runtime stack"
# Use a fresh local-CI stack so tmpfs-backed PostgreSQL and one-shot migration
# services are recreated on every full ci-local run. Core/Netty mount built jars
# into a single Java 25 runtime image and Admin UI mounts the existing Next.js
# production build into a shared Node runtime container. No application image is built.
stop_legacy_host_admin
compose_ci down --remove-orphans >/dev/null 2>&1 || true
seed_ci_runtime_volumes
if [[ "$SKIP_ADMIN" == "true" ]]; then
  compose_services=(postgres redis otel-collector core-db-migrate core netty)
else
  compose_services=(postgres redis otel-collector core-db-migrate core netty admin-ui)
fi
if ! compose_ci up -d --remove-orphans --force-recreate "${compose_services[@]}"; then
  mkdir -p "${CI_OUTPUT_DIR}/logs" "${CI_OUTPUT_DIR}/compose"
  compose_ci ps > "${CI_OUTPUT_DIR}/compose/ps-after-failed-up.txt" 2>/dev/null || true
  compose_ci logs --no-color --tail=800 core-db-migrate postgres > "${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" 2>/dev/null || true
  echo "" >&2
  echo "[ERROR] Local CI compose failed before smoke test." >&2
  echo "[ERROR] Captured migration diagnostics: ${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" >&2
  if [[ -s "${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" ]]; then
    echo "" >&2
    echo "========== core-db-migrate/postgres diagnostics ==========" >&2
    tail -n 240 "${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" >&2 || true
    echo "=========================================================" >&2
  fi
  fail "local CI compose startup failed; inspect the migration diagnostics above"
fi

log "Stage 8 - Smoke test"
SKIP_ADMIN_UI_SMOKE="$SKIP_ADMIN" bash scripts/ci/local-smoke.sh --project "$CI_PROJECT_NAME" --compose-file "$CI_COMPOSE_FILE" --env-file "$CI_ENV_FILE"

log "Stage 8.1 - OTLP export smoke"
bash scripts/observability/otlp-export-smoke.sh --project "$CI_PROJECT_NAME" --compose-file "$CI_COMPOSE_FILE" --env-file "$CI_ENV_FILE" --output-dir "$CI_OUTPUT_DIR"

if [[ "${RUN_P3M_ENFORCE_ACCEPTANCE:-false}" == "true" ]]; then
  log "Stage 8.5 - P3-M ENFORCE runtime acceptance"
  P3M_RUNTIME_ACCEPTANCE_MODE="${P3M_RUNTIME_ACCEPTANCE_MODE:-live}"   P3M_CORE_BASE_URL="${P3M_CORE_BASE_URL:-http://127.0.0.1:18080}"   P3M_ADMIN_BASE_URL="${P3M_ADMIN_BASE_URL:-http://127.0.0.1:3000}"   P3M_ACCEPTANCE_OUTPUT="${P3M_ACCEPTANCE_OUTPUT:-${CI_OUTPUT_DIR}/reports/p3m-enforce-runtime-acceptance.json}"   bash scripts/acceptance/p3m-enforce-runtime-acceptance.sh
fi

if [[ "${RUN_P3N_FULL_ENFORCE_ACCEPTANCE:-false}" == "true" ]]; then
  log "Stage 8.6 - P3-N full ENFORCE acceptance automation"
  P3N_RUNTIME_ACCEPTANCE_MODE="${P3N_RUNTIME_ACCEPTANCE_MODE:-live}" \
    P3N_CORE_BASE_URL="${P3N_CORE_BASE_URL:-http://127.0.0.1:18080}" \
    P3N_ADMIN_BASE_URL="${P3N_ADMIN_BASE_URL:-http://127.0.0.1:3000}" \
    P3N_ACCEPTANCE_OUTPUT_DIR="${P3N_ACCEPTANCE_OUTPUT_DIR:-${CI_OUTPUT_DIR}/reports/p3n-full-enforce}" \
    bash scripts/acceptance/p3n-full-enforce-acceptance.sh
fi


if [[ "${RUN_P3O_RELEASE_ARTIFACT_GATE:-false}" == "true" ]]; then
  log "Stage 8.7 - P3-O release artifact archive and gate"
  (
    cd ai-event-gateway-admin-ui
    P3N_ACCEPTANCE_OUTPUT_DIR="${P3N_ACCEPTANCE_OUTPUT_DIR:-${CI_OUTPUT_DIR}/reports/p3n-full-enforce}"       P3O_RELEASE_ARTIFACT_ARCHIVE_DIR="${P3O_RELEASE_ARTIFACT_ARCHIVE_DIR:-${CI_OUTPUT_DIR}/release-artifacts/p3o-enforce}"       npm run archive:p3o-release-artifacts
    P3O_RELEASE_ARTIFACT_ARCHIVE_DIR="${P3O_RELEASE_ARTIFACT_ARCHIVE_DIR:-${CI_OUTPUT_DIR}/release-artifacts/p3o-enforce}"       P3O_ALLOW_FIXTURE_ARTIFACT="${P3O_ALLOW_FIXTURE_ARTIFACT:-false}"       npm run release:verify:p3o-enforce-artifact
  )
fi


if [[ "${RUN_P3P_POST_CUTOVER_OBSERVABILITY:-false}" == "true" ]]; then
  log "Stage 8.8 - P3-P post-cutover ENFORCE observability export"
  (
    cd ai-event-gateway-admin-ui
    P3P_OBSERVABILITY_MODE="${P3P_OBSERVABILITY_MODE:-live}" \
      P3P_OBSERVABILITY_OUTPUT="${P3P_OBSERVABILITY_OUTPUT:-${CI_OUTPUT_DIR}/reports/p3p-post-cutover-observability.json}" \
      npm run export:p3p-observability
  )
fi

log "Stage 9 - Collect compose diagnostics"
compose_ci ps > "${CI_OUTPUT_DIR}/compose/ps.txt"
compose_ci logs --no-color > "${CI_OUTPUT_DIR}/logs/compose.log"
collect_reports
bash scripts/ci/local-report.sh --project "$CI_PROJECT_NAME" --compose-file "$CI_COMPOSE_FILE" --env-file "$CI_ENV_FILE" --output-dir "$CI_OUTPUT_DIR" >/dev/null 2>&1 || true

if [[ "$KEEP_STACK" != "true" ]]; then
  log "Stage 10 - Teardown requested"
  compose_ci down
  log "Local CI completed successfully. Reports are under ${CI_OUTPUT_DIR}."
else
  log "Stage 10 - Keep local stack running"
  echo "[CI] Local shared-runtime stack remains running."
  echo "[CI] Use make ci-logs to follow logs."
  echo "[CI] Use make ci-ps, make ci-report, or make ci-diagnose for runtime diagnostics."
echo "[CI] Use make ci-down or make ci-down-v to stop it."
  echo "[CI] Admin UI is running as a shared Node runtime container."
  log "Local CI completed successfully. Reports are under ${CI_OUTPUT_DIR}."
fi
