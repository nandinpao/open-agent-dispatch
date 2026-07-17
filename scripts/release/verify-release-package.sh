#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="$(pwd)"
OFFLINE="false"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --package-dir)
      PACKAGE_DIR="$2"
      shift 2
      ;;
    --offline)
      OFFLINE="true"
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [--package-dir <dir>] [--offline]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

fail() { echo "[FAIL] $*" >&2; exit 1; }
info() { echo "[INFO] $*"; }
require_file() { [[ -f "${PACKAGE_DIR}/$1" ]] || fail "Missing file: $1"; }
require_dir() { [[ -d "${PACKAGE_DIR}/$1" ]] || fail "Missing directory: $1"; }
require_text() {
  local file="$1"
  local text="$2"
  grep -Fq -- "$text" "${PACKAGE_DIR}/${file}" || fail "${file} missing required text: ${text}"
}
reject_text() {
  local file="$1"
  local text="$2"
  if grep -Fq -- "$text" "${PACKAGE_DIR}/${file}"; then
    fail "${file} must not contain: ${text}"
  fi
}


assert_package_tree_clean() {
  local report_file="${PACKAGE_DIR}/reports/release-package-clean-check.txt"
  mkdir -p "${PACKAGE_DIR}/reports"
  find "${PACKAGE_DIR}"     \(       -name '.DS_Store' -type f -o       -name '._*' -type f -o       -name tsconfig.tsbuildinfo -type f -o       -name '*.pyc' -type f -o       -name '*.pyo' -type f -o       -name __pycache__ -type d -o       -name __MACOSX -type d -o       -name .AppleDouble -type d     \) -print | sort > "${report_file}"
  if [[ -s "${report_file}" ]]; then
    cat "${report_file}" >&2
    fail "Release package contains OS/archive metadata or generated cache files"
  fi
}

validate_jar_like_zip() {
  local rel="$1"
  local file="${PACKAGE_DIR}/${rel}"
  if command -v jar >/dev/null 2>&1; then
    jar tf "$file" >/dev/null || fail "Invalid jar archive: ${rel}"
  elif command -v unzip >/dev/null 2>&1; then
    unzip -tq "$file" >/dev/null || fail "Invalid jar archive: ${rel}"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$file" <<'PY' || exit 1
import sys, zipfile
path = sys.argv[1]
if not zipfile.is_zipfile(path):
    print(f"[FAIL] Invalid jar archive: {path}", file=sys.stderr)
    sys.exit(1)
with zipfile.ZipFile(path) as zf:
    bad = zf.testzip()
    if bad:
        print(f"[FAIL] Invalid zip member in jar {path}: {bad}", file=sys.stderr)
        sys.exit(1)
PY
  else
    info "Skipping jar archive integrity check for ${rel}; jar/unzip/python3 not found."
  fi
}

assert_package_tree_clean

require_file "runtime/core/ai-event-gateway-core.jar"
require_file "runtime/adapter-worker/ai-event-gateway-adapter-worker.jar"
require_file "runtime/netty/ai-event-gateway-netty.jar"
require_file "runtime/admin-ui/package.json"
require_file "runtime/admin-ui/package-lock.json"
require_file "runtime/admin-ui/.next/BUILD_ID"
require_file "runtime/admin-ui/scripts/opendispatch-admin-ui-runtime-entrypoint.sh"
for op_script in \
  "bin/opendispatch-backup.sh" \
  "bin/opendispatch-restore.sh" \
  "bin/opendispatch-status.sh" \
  "bin/opendispatch-upgrade.sh" \
  "bin/opendispatch-rollback.sh"; do
  require_file "${op_script}"
done
require_file "deploy/docker-compose.release.yml"
require_file "deploy/env/.env.release.example"
require_file "deploy/docker-compose.observability.release.yml"
require_file "deploy/env/.env.observability.release.example"
require_file "deploy/observability/otel-collector-gateway.yml"
require_file "deploy/observability/otel-collector-sampler.yml"
require_file "deploy/observability/haproxy/otel-lb.cfg"
require_file "deploy/observability/secrets/README.md"
require_file "deploy/observability/prometheus/alerts/opendispatch-observability-slo.rules.yml"
require_file "deploy/observability/grafana/dashboards/opendispatch-observability-overview.json"
require_file "docs/P5-A_PRODUCTION_OTEL_HARDENING.md"
require_file "bin/generate-otel-pki.sh"
require_file "bin/validate-production-otel.sh"
require_file "release-manifest.txt"
require_dir "runtime/admin-ui/.next"
require_dir "db/migration"
require_dir "bin"

validate_jar_like_zip "runtime/core/ai-event-gateway-core.jar"
validate_jar_like_zip "runtime/adapter-worker/ai-event-gateway-adapter-worker.jar"
validate_jar_like_zip "runtime/netty/ai-event-gateway-netty.jar"

for script in "${PACKAGE_DIR}"/bin/*.sh; do
  [[ -f "$script" ]] || fail "No shell scripts found under bin"
  bash -n "$script" || fail "Shell syntax check failed: bin/$(basename "$script")"
  [[ -x "$script" ]] || fail "Script is not executable: bin/$(basename "$script")"
done
sh -n "${PACKAGE_DIR}/runtime/admin-ui/scripts/opendispatch-admin-ui-runtime-entrypoint.sh" \
  || fail "Shell syntax check failed: runtime/admin-ui/scripts/opendispatch-admin-ui-runtime-entrypoint.sh"
[[ -x "${PACKAGE_DIR}/runtime/admin-ui/scripts/opendispatch-admin-ui-runtime-entrypoint.sh" ]] \
  || fail "Admin UI runtime entrypoint is not executable"

if [[ -x "${PACKAGE_DIR}/bin/opendispatch-smoke.sh" ]]; then
  "${PACKAGE_DIR}/bin/opendispatch-smoke.sh" --self-check >/dev/null || fail "Release smoke script self-check failed"
else
  fail "Missing executable smoke wrapper: bin/opendispatch-smoke.sh"
fi

compose_text="$(cat "${PACKAGE_DIR}/deploy/docker-compose.release.yml")"
case "${compose_text}" in
  *"build:"*) fail "Release compose must not build application images" ;;
esac
for forbidden in "opendispatch/core" "opendispatch/netty" "opendispatch/admin-ui" "ai-event-gateway-core:local" "ai-event-gateway-netty:local" "ai-event-gateway-admin-ui:local"; do
  reject_text "deploy/docker-compose.release.yml" "${forbidden}"
done

require_text "deploy/docker-compose.release.yml" "eclipse-temurin:25-jre"
require_text "deploy/docker-compose.release.yml" "node:22-bookworm-slim"
require_text "deploy/docker-compose.release.yml" "postgres:18-alpine"
require_text "deploy/docker-compose.release.yml" "redis:8-alpine"
require_text "deploy/docker-compose.release.yml" "/var/lib/postgresql"
reject_text "deploy/docker-compose.release.yml" "/var/lib/postgresql/data"

# P15.1 runtime correctness gates: Core and Netty must use the env keys that the
# Spring Boot applications actually read. Do not accept superficial aliases such
# as PG_HOST/PG_DATABASE or CORE_BASE_URL only.
for required in \
  "SPRING_PROFILES_ACTIVE: \${CORE_PROFILES:-prod}" \
  "PG_ENABLED: \"true\"" \
  "PG_SINGLE_URL: jdbc:postgresql://postgres:5432/\${POSTGRES_DB:-ai_event_gateway_release}" \
  "PG_SINGLE_USERNAME: \${POSTGRES_USER:-ai_event_release}" \
  "PG_SINGLE_PASSWORD: \${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}" \
  "PG_MYBATIS_ENABLED: \"true\"" \
  "REDIS_ENABLED: \"true\"" \
  "REDIS_MODE: single" \
  "REDIS_SINGLE_ADDRESS:" \
  "GATEWAY_CORE_FORWARD_BASE_URL: http://core:18080" \
  "GATEWAY_CORE_DIRECTORY_SYNC_BASE_URL: http://core:18080" \
  "GATEWAY_CORE_TASK_CALLBACK_RELAY_BASE_URL: http://core:18080" \
  "GATEWAY_AGENT_AUTHORIZATION_BASE_URL: http://core:18080"; do
  require_text "deploy/docker-compose.release.yml" "$required"
done
reject_text "deploy/docker-compose.release.yml" "CORE_BASE_URL:"

# P16 offline/on-prem readiness gates. The compose file must call a release-owned
# Admin UI runtime entrypoint instead of embedding npm install/start logic inline.
require_text "deploy/docker-compose.release.yml" "opendispatch-admin-ui-runtime-entrypoint.sh"
require_text "deploy/docker-compose.release.yml" "ADMIN_UI_ALLOW_NPM_CI"
reject_text "deploy/docker-compose.release.yml" "npm ci --omit=dev"

# Phase D production security fail-fast gates. Release compose must not provide
# permissive auth defaults or placeholder token fallbacks.
for required in \
  "POSTGRES_PASSWORD: \${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}" \
  "REDIS_PASSWORD: \${REDIS_PASSWORD:?REDIS_PASSWORD is required}" \
  "--requirepass" \
  "CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO: \${CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO:-false}" \
  "CORE_INTERNAL_ALLOW_LEGACY_TOKEN_HEADER: \${CORE_INTERNAL_ALLOW_LEGACY_TOKEN_HEADER:-false}" \
  "CORE_INTERNAL_SECURITY_AUDIT_LOG_ENABLED: \${CORE_INTERNAL_SECURITY_AUDIT_LOG_ENABLED:-true}" \
  "GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED: \${GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED:-true}" \
  "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED: \${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED:-false}" \
  "AGENT_WS_HANDSHAKE_AUTH_ENABLED: \${AGENT_WS_HANDSHAKE_AUTH_ENABLED:-true}" \
  "ADMIN_UI_SECURITY_PROFILE: \${ADMIN_UI_SECURITY_PROFILE:-prod}" \
  "ADMIN_UI_FAIL_CLOSED: \${ADMIN_UI_FAIL_CLOSED:-true}" \
  "NEXT_PUBLIC_AUTH_ENABLED: \${NEXT_PUBLIC_AUTH_ENABLED:-true}" \
  "CORE_FORWARD_BROWSER_AUTHORIZATION: \${CORE_FORWARD_BROWSER_AUTHORIZATION:-false}" \
  "ADMIN_UI_COOKIE_SECURE: \${ADMIN_UI_COOKIE_SECURE:-true}" \
  "NETTY_MACHINE_AUTH_ENABLED: \${NETTY_MACHINE_AUTH_ENABLED:-true}" \
  "NETTY_MACHINE_WS_HANDSHAKE_AUTH_ENABLED: \${NETTY_MACHINE_WS_HANDSHAKE_AUTH_ENABLED:-true}" \
  "CORE_ADMIN_AUTH_ENABLED: \${CORE_ADMIN_AUTH_ENABLED:-true}"; do
  require_text "deploy/docker-compose.release.yml" "$required"
done
for forbidden in \
  "release-cluster-token-change-me" \
  "release-agent-onboarding-token-change-me" \
  "release-admin-api-token-change-me" \
  "NEXT_PUBLIC_AUTH_ENABLED: \${NEXT_PUBLIC_AUTH_ENABLED:-false}" \
  "CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO: \${CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO:-true}"; do
  reject_text "deploy/docker-compose.release.yml" "$forbidden"
done
require_text "deploy/env/.env.release.example" "REPLACE_WITH_STRONG_POSTGRES_PASSWORD"
require_text "deploy/env/.env.release.example" "REPLACE_WITH_STRONG_REDIS_PASSWORD"
require_text "deploy/env/.env.release.example" "ADMIN_UI_SECURITY_PROFILE=prod"
require_text "deploy/env/.env.release.example" "NEXT_PUBLIC_AUTH_ENABLED=true"
require_text "deploy/env/.env.release.example" "GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED=true"
require_text "deploy/env/.env.release.example" "GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED=false"
require_text "deploy/env/.env.release.example" "CORE_INTERNAL_PERMIT_ACTUATOR_HEALTH_INFO=false"
require_text "deploy/env/.env.release.example" "CORE_INTERNAL_ALLOW_LEGACY_TOKEN_HEADER=false"
require_text "deploy/env/.env.observability.release.example" "OTEL_SECRET_DIR=REPLACE_WITH_ABSOLUTE_OBSERVABILITY_SECRET_DIRECTORY"
require_text "deploy/env/.env.observability.release.example" "OTEL_INGEST_TOKEN=REPLACE_WITH_TOKEN_FROM_ingest.tokens"
require_text "deploy/docker-compose.observability.release.yml" "otel-gateway-a"
require_text "deploy/docker-compose.observability.release.yml" "otel-gateway-b"
require_text "deploy/docker-compose.observability.release.yml" "otel-sampler-a"
require_text "deploy/docker-compose.observability.release.yml" "otel-sampler-b"
require_text "deploy/observability/otel-collector-gateway.yml" "routing_key: traceID"
require_text "deploy/observability/otel-collector-sampler.yml" "tail_sampling:"

if [[ "${OFFLINE}" == "true" ]]; then
  require_file "runtime/admin-ui/node_modules-prod.tar.gz"
  if command -v tar >/dev/null 2>&1; then
    tar -tzf "${PACKAGE_DIR}/runtime/admin-ui/node_modules-prod.tar.gz" | grep -Eq '^node_modules/(\.bin/next|next/package\.json)' \
      || fail "Admin UI dependency bundle does not contain Next.js runtime dependencies"
  fi
  require_text "release-manifest.txt" "offline_admin_ui_supported=true"
fi

require_text "release-manifest.txt" "application_images_built=false"
require_text "release-manifest.txt" "admin_runtime_entrypoint=scripts/opendispatch-admin-ui-runtime-entrypoint.sh"
require_text "release-manifest.txt" "release_operations_scripts_included=true"
require_text "release-manifest.txt" "adapter_worker_runtime_included=true"
require_text "release-manifest.txt" "production_observability_overlay_included=true"
require_text "release-manifest.txt" "postgres_backup_format=pg_dump_custom"
require_text "release-manifest.txt" "upgrade_strategy=backup_preflight_skip_port_down_portcheck_up_smoke"
require_text "release-manifest.txt" "rollback_strategy=down_restore_up_smoke"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  docker compose --env-file "${PACKAGE_DIR}/deploy/env/.env.release.example" -f "${PACKAGE_DIR}/deploy/docker-compose.release.yml" config >/dev/null \
    || fail "docker compose config validation failed"
else
  info "Skipping docker compose config validation; docker compose is not available."
fi

echo "Release package verification passed: ${PACKAGE_DIR}"
