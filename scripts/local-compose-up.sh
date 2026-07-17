#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/deploy/env/.env.local" ]]; then
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local"
  else
    ENV_FILE="${ROOT_DIR}/deploy/env/.env.local.example"
  fi
fi
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/deploy/docker-compose.local.yml}"
PROJECT_NAME="${PROJECT_NAME:-opendispatch}"
WITH_AGENT="${WITH_AGENT:-false}"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
ADMIN_UI_NEXT_DIST_DIR="${ADMIN_UI_NEXT_DIST_DIR:-.next-ci}"
LOCAL_VOLUME_SEED_IMAGE="${LOCAL_VOLUME_SEED_IMAGE:-alpine:3.20}"
LOCAL_DB_MIGRATION_VOLUME="${LOCAL_DB_MIGRATION_VOLUME:-${PROJECT_NAME}-db-migration-sql}"
LOCAL_CORE_RUNTIME_VOLUME="${LOCAL_CORE_RUNTIME_VOLUME:-${PROJECT_NAME}-core-runtime}"
LOCAL_NETTY_RUNTIME_VOLUME="${LOCAL_NETTY_RUNTIME_VOLUME:-${PROJECT_NAME}-netty-runtime}"
LOCAL_ADAPTER_WORKER_RUNTIME_VOLUME="${LOCAL_ADAPTER_WORKER_RUNTIME_VOLUME:-${PROJECT_NAME}-adapter-worker-runtime}"
LOCAL_ADMIN_UI_RUNTIME_VOLUME="${LOCAL_ADMIN_UI_RUNTIME_VOLUME:-${PROJECT_NAME}-admin-ui-runtime}"
LOCAL_MOCK_AGENT_E2E_VOLUME="${LOCAL_MOCK_AGENT_E2E_VOLUME:-${PROJECT_NAME}-mock-agent-e2e}"
LOCAL_OTEL_COLLECTOR_CONFIG_VOLUME="${LOCAL_OTEL_COLLECTOR_CONFIG_VOLUME:-${PROJECT_NAME}-otel-collector-config}"
LOCAL_FLYWAY_DIAGNOSTICS_VOLUME="${LOCAL_FLYWAY_DIAGNOSTICS_VOLUME:-${PROJECT_NAME}-flyway-diagnostics}"
LOCAL_CORE_LOGS_VOLUME="${LOCAL_CORE_LOGS_VOLUME:-${PROJECT_NAME}-core-logs}"
LOCAL_NETTY_LOGS_VOLUME="${LOCAL_NETTY_LOGS_VOLUME:-${PROJECT_NAME}-netty-logs}"
LOCAL_ADAPTER_WORKER_LOGS_VOLUME="${LOCAL_ADAPTER_WORKER_LOGS_VOLUME:-${PROJECT_NAME}-adapter-worker-logs}"
LOCAL_ADMIN_UI_LOGS_VOLUME="${LOCAL_ADMIN_UI_LOGS_VOLUME:-${PROJECT_NAME}-admin-ui-logs}"
export LOCAL_DB_MIGRATION_VOLUME LOCAL_CORE_RUNTIME_VOLUME LOCAL_NETTY_RUNTIME_VOLUME LOCAL_ADAPTER_WORKER_RUNTIME_VOLUME LOCAL_ADMIN_UI_RUNTIME_VOLUME LOCAL_MOCK_AGENT_E2E_VOLUME LOCAL_OTEL_COLLECTOR_CONFIG_VOLUME LOCAL_FLYWAY_DIAGNOSTICS_VOLUME LOCAL_CORE_LOGS_VOLUME LOCAL_NETTY_LOGS_VOLUME LOCAL_ADAPTER_WORKER_LOGS_VOLUME LOCAL_ADMIN_UI_LOGS_VOLUME

cd "${ROOT_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Env file not found: ${ENV_FILE}" >&2
  exit 1
fi

fail() {
  echo "[ERROR] $*" >&2
  exit 1
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
  [[ -d "$source_dir" ]] || fail "Missing source directory for local Docker volume seed: $source_dir"
  reset_docker_volume "$volume"
  COPYFILE_DISABLE=1 tar --exclude='._*' --exclude='.DS_Store' -C "$source_dir" -cf - . \
    | docker run --rm -i -v "${volume}:${target_dir}" "$LOCAL_VOLUME_SEED_IMAGE" sh -c "cd '${target_dir}' && find . -maxdepth 1 -type f \( -name '._*' -o -name '.DS_Store' \) -exec rm -f {} + && tar -xf - && find . -maxdepth 1 -type f \( -name '._*' -o -name '.DS_Store' \) -exec rm -f {} +"
}

seed_file_volume() {
  local source_file="$1"
  local volume="$2"
  local target_dir="$3"
  local target_name="$4"
  [[ -f "$source_file" ]] || fail "Missing source file for local Docker volume seed: $source_file"
  reset_docker_volume "$volume"
  tar -C "$(dirname "$source_file")" -cf - "$(basename "$source_file")" \
    | docker run --rm -i -v "${volume}:${target_dir}" "$LOCAL_VOLUME_SEED_IMAGE" sh -c "cd '${target_dir}' && tar -xf - && mv '$(basename "$source_file")' '${target_name}'"
}

seed_local_runtime_volumes() {
  echo ""
  echo "==> Seed local Docker volumes to avoid host bind-mount failures"
  seed_directory_volume "${ROOT_DIR}/ai-event-gateway-core/database-platform/src/main/resources/db/migration" "$LOCAL_DB_MIGRATION_VOLUME" /flyway/sql
  seed_file_volume "${ROOT_DIR}/scripts/db/flyway-migrate-with-diagnostics.sh" "$LOCAL_FLYWAY_DIAGNOSTICS_VOLUME" /flyway/diagnostics flyway-migrate-with-diagnostics.sh
  seed_file_volume "${CI_OUTPUT_DIR}/runtime/core.jar" "$LOCAL_CORE_RUNTIME_VOLUME" /app ai-event-gateway-core.jar
  seed_file_volume "${CI_OUTPUT_DIR}/runtime/netty.jar" "$LOCAL_NETTY_RUNTIME_VOLUME" /app ai-event-gateway-netty.jar
  seed_file_volume "${CI_OUTPUT_DIR}/runtime/adapter-worker.jar" "$LOCAL_ADAPTER_WORKER_RUNTIME_VOLUME" /app ai-event-gateway-adapter-worker.jar
  seed_directory_volume "${CI_OUTPUT_DIR}/runtime/admin-ui" "$LOCAL_ADMIN_UI_RUNTIME_VOLUME" /workspace/admin-ui
  seed_directory_volume "${ROOT_DIR}/ai-event-gateway-core/scripts/e2e" "$LOCAL_MOCK_AGENT_E2E_VOLUME" /e2e
  seed_file_volume "${ROOT_DIR}/deploy/observability/otel-collector-config.yml" "$LOCAL_OTEL_COLLECTOR_CONFIG_VOLUME" /etc/otelcol-contrib/config config.yaml
}

mkdir -p "${CI_OUTPUT_DIR}/runtime" "${CI_OUTPUT_DIR}/logs" "${CI_OUTPUT_DIR}/compose"

mvn -U -f ai-event-gateway-core/pom.xml -pl control-plane-app,adapter-worker-app -am package -DskipTests
mvn -U -f ai-event-gateway-netty/pom.xml -pl gateway-app -am package -DskipTests
CORE_JAR="$(find ai-event-gateway-core/control-plane-app/target -maxdepth 1 -type f -name 'control-plane-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -n 1)"
ADAPTER_WORKER_JAR="$(find ai-event-gateway-core/adapter-worker-app/target -maxdepth 1 -type f -name 'adapter-worker-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -n 1)"
NETTY_JAR="$(find ai-event-gateway-netty/gateway-app/target -maxdepth 1 -type f -name 'gateway-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -n 1)"
[[ -n "$CORE_JAR" ]] || fail "Core executable jar was not produced"
[[ -n "$ADAPTER_WORKER_JAR" ]] || fail "Adapter Worker executable jar was not produced"
[[ -n "$NETTY_JAR" ]] || fail "Netty executable jar was not produced"
cp "$CORE_JAR" "${CI_OUTPUT_DIR}/runtime/core.jar"
cp "$ADAPTER_WORKER_JAR" "${CI_OUTPUT_DIR}/runtime/adapter-worker.jar"
cp "$NETTY_JAR" "${CI_OUTPUT_DIR}/runtime/netty.jar"

pushd ai-event-gateway-admin-ui >/dev/null
bash "${ROOT_DIR}/scripts/ci/admin-ui-clean-generated.sh" --best-effort --output-dir "${CI_OUTPUT_DIR}" --project "${PROJECT_NAME}" --compose-file "${COMPOSE_FILE}" --env-file "${ENV_FILE}"
npm ci
NEXT_DIST_DIR="$ADMIN_UI_NEXT_DIST_DIR" npm run build
popd >/dev/null
bash "${ROOT_DIR}/scripts/ci/prepare-admin-ui-runtime.sh" --output-dir "${CI_OUTPUT_DIR}" --build-dir "${ADMIN_UI_NEXT_DIST_DIR}"

seed_local_runtime_volumes

# Stop stale host-process Admin UI instances left by older P13 revisions.
bash "${ROOT_DIR}/scripts/ci/local-admin-ui-host.sh" stop --env-file "${ENV_FILE}" --output-dir "${CI_OUTPUT_DIR}" >/dev/null 2>&1 || true

args=(--env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --remove-orphans --force-recreate)
if [[ "${WITH_AGENT}" == "true" ]]; then
  args=(--env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" --profile agent up -d --remove-orphans --force-recreate)
fi

if ! docker compose -p "${PROJECT_NAME}" "${args[@]}"; then
  mkdir -p "${CI_OUTPUT_DIR}/logs" "${CI_OUTPUT_DIR}/compose"
  docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps > "${CI_OUTPUT_DIR}/compose/ps-after-failed-up.txt" 2>/dev/null || true
  docker compose -p "${PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --no-color --tail=800 core-db-migrate postgres > "${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" 2>/dev/null || true
  echo "" >&2
  echo "[ERROR] Local compose failed before smoke test." >&2
  echo "[ERROR] Captured migration diagnostics: ${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" >&2
  if [[ -s "${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" ]]; then
    echo "" >&2
    echo "========== core-db-migrate/postgres diagnostics ==========" >&2
    tail -n 240 "${CI_OUTPUT_DIR}/logs/core-db-migrate-failure.log" >&2 || true
    echo "=========================================================" >&2
  fi
  fail "local compose startup failed; inspect the migration diagnostics above"
fi
"${ROOT_DIR}/scripts/local-smoke.sh"
