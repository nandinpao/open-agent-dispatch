#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${VERSION:-}"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/.ci-output/releases}"
SKIP_TESTS="${SKIP_TESTS:-true}"
SKIP_ADMIN_BUILD="${SKIP_ADMIN_BUILD:-false}"
SKIP_JAVA_BUILD="${SKIP_JAVA_BUILD:-false}"
INCLUDE_ADMIN_RUNTIME_DEPS="${INCLUDE_ADMIN_RUNTIME_DEPS:-false}"
ADMIN_UI_RELEASE_DIST_DIR="${ADMIN_UI_RELEASE_DIST_DIR:-.next-release}"
RELEASE_SOURCE_CLEAN_CHECK="${RELEASE_SOURCE_CLEAN_CHECK:-true}"
ADMIN_RUNTIME_DEPS_TMP=""
cleanup() {
  if [[ -n "${ADMIN_RUNTIME_DEPS_TMP}" && -d "${ADMIN_RUNTIME_DEPS_TMP}" ]]; then
    rm -rf "${ADMIN_RUNTIME_DEPS_TMP}"
  fi
}
trap cleanup EXIT

usage() {
  cat <<USAGE
Usage: $0 [--version <version>] [--output-dir <dir>] [--with-tests] [--skip-java-build] [--skip-admin-build] [--include-admin-runtime-deps] [--skip-source-clean-check]

Build an on-prem/self-hosted OpenDispatch release package without building
per-application Docker images. The package mounts built Core/Netty jars into a
shared Java 25 runtime image and runs Admin UI in a shared Node runtime image.

--include-admin-runtime-deps vendors Admin UI production node_modules as
node_modules-prod.tar.gz so the release package can start in offline/internal
network environments without contacting npm at container startup.

By default the source tree cleanliness gate runs before builds. Use
--skip-source-clean-check only for controlled recovery scenarios where existing
build outputs are intentionally reused with --skip-java-build/--skip-admin-build.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --with-tests)
      SKIP_TESTS="false"
      shift
      ;;
    --skip-java-build)
      SKIP_JAVA_BUILD="true"
      shift
      ;;
    --skip-admin-build)
      SKIP_ADMIN_BUILD="true"
      shift
      ;;
    --include-admin-runtime-deps)
      INCLUDE_ADMIN_RUNTIME_DEPS="true"
      shift
      ;;
    --skip-source-clean-check)
      RELEASE_SOURCE_CLEAN_CHECK="false"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${VERSION}" ]]; then
  if git -C "${ROOT_DIR}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    short_sha="$(git -C "${ROOT_DIR}" rev-parse --short HEAD 2>/dev/null || true)"
  else
    short_sha="local"
  fi
  VERSION="$(date +%Y%m%d)-${short_sha:-local}"
fi

RELEASE_NAME="OpenDispatch-${VERSION}"
STAGING_DIR="${OUTPUT_DIR}/${RELEASE_NAME}"
ARCHIVE_PATH="${OUTPUT_DIR}/${RELEASE_NAME}.tar.gz"
CHECKSUM_PATH="${ARCHIVE_PATH}.sha256"

info() { printf '\n==> %s\n' "$*"; }
fail() { echo "[ERROR] $*" >&2; exit 1; }

assert_tree_has_no_release_pollutants() {
  local tree_dir="$1"
  local report_file="$2"
  find "${tree_dir}"     \(       -name '.DS_Store' -type f -o       -name '._*' -type f -o       -name tsconfig.tsbuildinfo -type f -o       -name '*.pyc' -type f -o       -name '*.pyo' -type f -o       -name __pycache__ -type d -o       -name __MACOSX -type d -o       -name .AppleDouble -type d     \) -print | sort > "${report_file}"
  if [[ -s "${report_file}" ]]; then
    cat "${report_file}" >&2
    fail "Release tree contains OS/archive metadata or generated cache files"
  fi
}

sanitize_release_tree_metadata() {
  local tree_dir="$1"
  find "${tree_dir}"     \( -name __pycache__ -type d -o -name __MACOSX -type d -o -name .AppleDouble -type d \)     -prune -exec rm -rf {} +
  find "${tree_dir}"     \( -name '.DS_Store' -type f -o -name '._*' -type f -o -name tsconfig.tsbuildinfo -type f -o -name '*.pyc' -type f -o -name '*.pyo' -type f \)     -delete
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

copy_if_exists() {
  local src="$1"
  local dst="$2"
  if [[ -e "${src}" ]]; then
    mkdir -p "$(dirname "${dst}")"
    cp -a "${src}" "${dst}"
  fi
}

copy_file_required() {
  local src="$1"
  local dst="$2"
  [[ -f "${src}" ]] || fail "Required file not found: ${src}"
  mkdir -p "$(dirname "${dst}")"
  cp -p "${src}" "${dst}"
}

info "Preflight"
require_cmd tar
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
  fail "Missing required command: sha256sum or shasum"
fi
if [[ "${SKIP_JAVA_BUILD}" != "true" ]]; then
  require_cmd mvn
fi
if [[ "${SKIP_ADMIN_BUILD}" != "true" || "${INCLUDE_ADMIN_RUNTIME_DEPS}" == "true" ]]; then
  require_cmd npm
fi

if [[ "${RELEASE_SOURCE_CLEAN_CHECK}" == "true" ]]; then
  info "Source tree cleanliness gate"
  "${ROOT_DIR}/scripts/ci/source-clean-check.sh"
else
  echo "Skipping source tree cleanliness gate because RELEASE_SOURCE_CLEAN_CHECK=false."
fi

info "Build Core and Netty executable jars"
if [[ "${SKIP_JAVA_BUILD}" != "true" ]]; then
  mvn -U -f "${ROOT_DIR}/ai-event-gateway-core/pom.xml" -pl control-plane-app,adapter-worker-app -am package -DskipTests="${SKIP_TESTS}"
  mvn -U -f "${ROOT_DIR}/ai-event-gateway-netty/pom.xml" -pl gateway-app -am package -DskipTests="${SKIP_TESTS}"
else
  echo "Skipping Java build; existing target jars will be packaged."
fi

core_jar="$(find "${ROOT_DIR}/ai-event-gateway-core/control-plane-app/target" -maxdepth 1 -type f -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | sort | tail -1 || true)"
adapter_worker_jar="$(find "${ROOT_DIR}/ai-event-gateway-core/adapter-worker-app/target" -maxdepth 1 -type f -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | sort | tail -1 || true)"
netty_jar="$(find "${ROOT_DIR}/ai-event-gateway-netty/gateway-app/target" -maxdepth 1 -type f -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | sort | tail -1 || true)"
[[ -n "${core_jar}" && -f "${core_jar}" ]] || fail "Core executable jar not found"
[[ -n "${adapter_worker_jar}" && -f "${adapter_worker_jar}" ]] || fail "Adapter Worker executable jar not found"
[[ -n "${netty_jar}" && -f "${netty_jar}" ]] || fail "Netty executable jar not found"

info "Build Admin UI production assets"
if [[ "${SKIP_ADMIN_BUILD}" != "true" ]]; then
  (cd "${ROOT_DIR}/ai-event-gateway-admin-ui" && npm ci && NEXT_DIST_DIR="${ADMIN_UI_RELEASE_DIST_DIR}" npm run build)
else
  echo "Skipping Admin UI build; existing ${ADMIN_UI_RELEASE_DIST_DIR} build will be packaged."
fi
ADMIN_UI_BUILD_PATH="${ROOT_DIR}/ai-event-gateway-admin-ui/${ADMIN_UI_RELEASE_DIST_DIR}"
[[ -f "${ADMIN_UI_BUILD_PATH}/BUILD_ID" ]] || fail "Admin UI production build missing: ai-event-gateway-admin-ui/${ADMIN_UI_RELEASE_DIST_DIR}/BUILD_ID"

info "Assemble release package: ${RELEASE_NAME}"
rm -rf "${STAGING_DIR}"
mkdir -p \
  "${STAGING_DIR}/bin" \
  "${STAGING_DIR}/deploy/env" \
  "${STAGING_DIR}/runtime/core" \
  "${STAGING_DIR}/runtime/adapter-worker" \
  "${STAGING_DIR}/runtime/netty" \
  "${STAGING_DIR}/runtime/admin-ui" \
  "${STAGING_DIR}/runtime/admin-ui/node_modules" \
  "${STAGING_DIR}/db" \
  "${STAGING_DIR}/docs" \
  "${STAGING_DIR}/reports"

copy_file_required "${core_jar}" "${STAGING_DIR}/runtime/core/ai-event-gateway-core.jar"
copy_file_required "${adapter_worker_jar}" "${STAGING_DIR}/runtime/adapter-worker/ai-event-gateway-adapter-worker.jar"
copy_file_required "${netty_jar}" "${STAGING_DIR}/runtime/netty/ai-event-gateway-netty.jar"

# Admin UI runtime: no node_modules and no application image. The shared Node
# runtime container installs production dependencies into a named Docker volume.
for f in package.json package-lock.json .npmrc; do
  copy_file_required "${ROOT_DIR}/ai-event-gateway-admin-ui/${f}" "${STAGING_DIR}/runtime/admin-ui/${f}"
done
copy_file_required "${ROOT_DIR}/ai-event-gateway-admin-ui/next.config.runtime.mjs" "${STAGING_DIR}/runtime/admin-ui/next.config.mjs"
copy_if_exists "${ROOT_DIR}/ai-event-gateway-admin-ui/scripts" "${STAGING_DIR}/runtime/admin-ui/scripts"
copy_file_required "${ROOT_DIR}/scripts/release/admin-ui-runtime-entrypoint.sh" "${STAGING_DIR}/runtime/admin-ui/scripts/opendispatch-admin-ui-runtime-entrypoint.sh"
chmod +x "${STAGING_DIR}/runtime/admin-ui/scripts/opendispatch-admin-ui-runtime-entrypoint.sh"
copy_if_exists "${ROOT_DIR}/ai-event-gateway-admin-ui/public" "${STAGING_DIR}/runtime/admin-ui/public"
rm -rf "${STAGING_DIR}/runtime/admin-ui/.next"
cp -a "${ADMIN_UI_BUILD_PATH}" "${STAGING_DIR}/runtime/admin-ui/.next"

if [[ "${INCLUDE_ADMIN_RUNTIME_DEPS}" == "true" ]]; then
  info "Package Admin UI production dependencies for offline runtime"
  ADMIN_RUNTIME_DEPS_TMP="$(mktemp -d)"
  cp -p "${ROOT_DIR}/ai-event-gateway-admin-ui/package.json" "${ADMIN_RUNTIME_DEPS_TMP}/package.json"
  cp -p "${ROOT_DIR}/ai-event-gateway-admin-ui/package-lock.json" "${ADMIN_RUNTIME_DEPS_TMP}/package-lock.json"
  copy_if_exists "${ROOT_DIR}/ai-event-gateway-admin-ui/.npmrc" "${ADMIN_RUNTIME_DEPS_TMP}/.npmrc"
  (cd "${ADMIN_RUNTIME_DEPS_TMP}" && npm ci --omit=dev --no-audit --no-fund)
  (cd "${ADMIN_RUNTIME_DEPS_TMP}" && tar -czf "${STAGING_DIR}/runtime/admin-ui/node_modules-prod.tar.gz" node_modules)
fi

copy_if_exists "${ROOT_DIR}/ai-event-gateway-core/database-platform/src/main/resources/db/migration" "${STAGING_DIR}/db/migration"
copy_file_required "${ROOT_DIR}/deploy/docker-compose.release.yml" "${STAGING_DIR}/deploy/docker-compose.release.yml"
copy_file_required "${ROOT_DIR}/deploy/env/.env.release.example" "${STAGING_DIR}/deploy/env/.env.release.example"
copy_file_required "${ROOT_DIR}/deploy/docker-compose.observability.release.yml" "${STAGING_DIR}/deploy/docker-compose.observability.release.yml"
copy_file_required "${ROOT_DIR}/deploy/env/.env.observability.release.example" "${STAGING_DIR}/deploy/env/.env.observability.release.example"
copy_if_exists "${ROOT_DIR}/deploy/observability" "${STAGING_DIR}/deploy/observability"
copy_file_required "${ROOT_DIR}/scripts/observability/generate-otel-pki.sh" "${STAGING_DIR}/bin/generate-otel-pki.sh"
copy_file_required "${ROOT_DIR}/scripts/observability/validate-production-otel.sh" "${STAGING_DIR}/bin/validate-production-otel.sh"
chmod +x "${STAGING_DIR}/bin/generate-otel-pki.sh" "${STAGING_DIR}/bin/validate-production-otel.sh"
copy_file_required "${ROOT_DIR}/scripts/release/verify-release-package.sh" "${STAGING_DIR}/bin/verify-release-package.sh"
copy_file_required "${ROOT_DIR}/scripts/release/generate-release-notes.sh" "${STAGING_DIR}/bin/generate-release-notes.sh"
copy_file_required "${ROOT_DIR}/scripts/release/release-preflight.sh" "${STAGING_DIR}/bin/opendispatch-preflight.sh"
copy_file_required "${ROOT_DIR}/scripts/release/release-backup.sh" "${STAGING_DIR}/bin/opendispatch-backup.sh"
copy_file_required "${ROOT_DIR}/scripts/release/release-restore.sh" "${STAGING_DIR}/bin/opendispatch-restore.sh"
copy_file_required "${ROOT_DIR}/scripts/release/release-status.sh" "${STAGING_DIR}/bin/opendispatch-status.sh"
copy_file_required "${ROOT_DIR}/scripts/release/release-upgrade.sh" "${STAGING_DIR}/bin/opendispatch-upgrade.sh"
copy_file_required "${ROOT_DIR}/scripts/release/release-rollback.sh" "${STAGING_DIR}/bin/opendispatch-rollback.sh"
copy_file_required "${ROOT_DIR}/scripts/ci/local-smoke.sh" "${STAGING_DIR}/bin/local-smoke.sh"
copy_file_required "${ROOT_DIR}/scripts/ci/env-utils.sh" "${STAGING_DIR}/bin/env-utils.sh"

cp -a "${ROOT_DIR}"/P*_DELIVERY_SUMMARY.md "${STAGING_DIR}/reports/" 2>/dev/null || true
copy_if_exists "${ROOT_DIR}/P14_DELIVERY_SUMMARY.md" "${STAGING_DIR}/reports/P14_DELIVERY_SUMMARY.md"
copy_if_exists "${ROOT_DIR}/docs/P15_RELEASE_AND_EXTERNAL_CICD.md" "${STAGING_DIR}/docs/P15_RELEASE_AND_EXTERNAL_CICD.md"
copy_if_exists "${ROOT_DIR}/docs/P16_ONPREM_OFFLINE_RELEASE_READINESS.md" "${STAGING_DIR}/docs/P16_ONPREM_OFFLINE_RELEASE_READINESS.md"
copy_if_exists "${ROOT_DIR}/docs/P17_RELEASE_OPERATIONS_SAFETY.md" "${STAGING_DIR}/docs/P17_RELEASE_OPERATIONS_SAFETY.md"
copy_file_required "${ROOT_DIR}/docs/architecture/P5-A_PRODUCTION_OTEL_HARDENING.md" "${STAGING_DIR}/docs/P5-A_PRODUCTION_OTEL_HARDENING.md"
copy_file_required "${ROOT_DIR}/README.md" "${STAGING_DIR}/README-project.md"

cat > "${STAGING_DIR}/README.md" <<EOF_README
# ${RELEASE_NAME}

This is an on-prem/self-hosted OpenDispatch release package.

Production observability is provided as a separate hardened Compose overlay:

- deploy/docker-compose.observability.release.yml
- deploy/env/.env.observability.release.example
- deploy/observability/*
- bin/generate-otel-pki.sh
- bin/validate-production-otel.sh

It does not contain locally built per-application Docker images. Runtime uses:

- Core: shared Java 25 runtime image + mounted jar
- Adapter Worker (optional profile): shared Java 25 runtime image + mounted jar
- Netty: shared Java 25 runtime image + mounted jar
- Admin UI: shared Node 22 runtime image + mounted Next.js production build
- PostgreSQL: official PostgreSQL 18 image
- Redis: official Redis 8 image

## Start

Copy the env file once and edit secrets/ports if needed:

\`\`\`bash
cp deploy/env/.env.release.example deploy/env/.env.release
./bin/opendispatch-preflight.sh
./bin/opendispatch-up.sh
./bin/opendispatch-smoke.sh
\`\`\`

## URLs

- Admin UI: http://127.0.0.1:3000
- Core: http://127.0.0.1:18080
- Netty Admin: http://127.0.0.1:18081

## Stop

\`\`\`bash
./bin/opendispatch-down.sh
\`\`\`

## Operations

Create a pre-upgrade or regular operations backup:

\`\`\`bash
./bin/opendispatch-backup.sh
\`\`\`

Restore a backup after confirming the target package and database:

\`\`\`bash
./bin/opendispatch-restore.sh --backup backups/<backup>.tar.gz --yes
\`\`\`

Check current release status:

\`\`\`bash
./bin/opendispatch-status.sh
\`\`\`

Guarded package upgrade / rollback helpers are also included:

\`\`\`bash
./bin/opendispatch-upgrade.sh --current-package /opt/opendispatch-current --new-package /opt/opendispatch-new --yes
./bin/opendispatch-rollback.sh --current-package /opt/opendispatch-new --target-package /opt/opendispatch-current --backup backups/<backup>.tar.gz --yes
\`\`\`

## Verify package layout

\`\`\`bash
./bin/verify-release-package.sh
./bin/opendispatch-smoke.sh --self-check
\`\`\`
EOF_README

cat > "${STAGING_DIR}/bin/opendispatch-up.sh" <<'EOF_UP'
#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/deploy/env/.env.release}"
if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}" >&2
  echo "Create it from deploy/env/.env.release.example first." >&2
  exit 1
fi
if [[ "${SKIP_PREFLIGHT:-false}" != "true" ]]; then
  "${ROOT_DIR}/bin/opendispatch-preflight.sh" --package-dir "${ROOT_DIR}" --env-file "${ENV_FILE}" ${OPENDISPATCH_PREFLIGHT_ARGS:-}
fi
exec docker compose -p "${PROJECT_NAME:-opendispatch-release}" --env-file "${ENV_FILE}" -f "${ROOT_DIR}/deploy/docker-compose.release.yml" up -d --remove-orphans
EOF_UP

cat > "${STAGING_DIR}/bin/opendispatch-down.sh" <<'EOF_DOWN'
#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/deploy/env/.env.release}"
[[ -f "${ENV_FILE}" ]] || ENV_FILE="${ROOT_DIR}/deploy/env/.env.release.example"
exec docker compose -p "${PROJECT_NAME:-opendispatch-release}" --env-file "${ENV_FILE}" -f "${ROOT_DIR}/deploy/docker-compose.release.yml" down --remove-orphans
EOF_DOWN

cat > "${STAGING_DIR}/bin/opendispatch-logs.sh" <<'EOF_LOGS'
#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/deploy/env/.env.release}"
[[ -f "${ENV_FILE}" ]] || ENV_FILE="${ROOT_DIR}/deploy/env/.env.release.example"
exec docker compose -p "${PROJECT_NAME:-opendispatch-release}" --env-file "${ENV_FILE}" -f "${ROOT_DIR}/deploy/docker-compose.release.yml" logs -f --tail=300 "$@"
EOF_LOGS

cat > "${STAGING_DIR}/bin/opendispatch-smoke.sh" <<'EOF_SMOKE'
#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/deploy/env/.env.release}"
[[ -f "${ENV_FILE}" ]] || ENV_FILE="${ROOT_DIR}/deploy/env/.env.release.example"
exec "${ROOT_DIR}/bin/local-smoke.sh" --project "${PROJECT_NAME:-opendispatch-release}" --compose-file "${ROOT_DIR}/deploy/docker-compose.release.yml" --env-file "${ENV_FILE}" "$@"
EOF_SMOKE

chmod +x "${STAGING_DIR}/bin"/*.sh

info "Sanitize staged release metadata"
sanitize_release_tree_metadata "${STAGING_DIR}"
assert_tree_has_no_release_pollutants "${STAGING_DIR}" "${STAGING_DIR}/reports/release-tree-clean-check.txt"

cat > "${STAGING_DIR}/release-manifest.txt" <<EOF_MANIFEST
name=${RELEASE_NAME}
version=${VERSION}
created_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
core_jar=$(basename "${core_jar}")
adapter_worker_jar=$(basename "${adapter_worker_jar}")
netty_jar=$(basename "${netty_jar}")
admin_ui_build_id=$(cat "${ADMIN_UI_BUILD_PATH}/BUILD_ID")
admin_runtime_deps_bundle=${INCLUDE_ADMIN_RUNTIME_DEPS}
admin_runtime_entrypoint=scripts/opendispatch-admin-ui-runtime-entrypoint.sh
offline_admin_ui_supported=${INCLUDE_ADMIN_RUNTIME_DEPS}
release_operations_scripts_included=true
adapter_worker_runtime_included=true
production_observability_overlay_included=true
postgres_backup_format=pg_dump_custom
upgrade_strategy=backup_preflight_skip_port_down_portcheck_up_smoke
rollback_strategy=down_restore_up_smoke
java_runtime_image=eclipse-temurin:25-jre
node_runtime_image=node:22-bookworm-slim
postgres_image=postgres:18-alpine
redis_image=redis:8-alpine
application_images_built=false
EOF_MANIFEST

info "Verify staged release package"
"${STAGING_DIR}/bin/verify-release-package.sh" --package-dir "${STAGING_DIR}"

info "Archive release package"
mkdir -p "${OUTPUT_DIR}"
rm -f "${ARCHIVE_PATH}" "${CHECKSUM_PATH}"
(
  cd "${OUTPUT_DIR}"
  tar -czf "${RELEASE_NAME}.tar.gz" "${RELEASE_NAME}"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${RELEASE_NAME}.tar.gz" > "${RELEASE_NAME}.tar.gz.sha256"
  else
    shasum -a 256 "${RELEASE_NAME}.tar.gz" > "${RELEASE_NAME}.tar.gz.sha256"
  fi
)

info "Release package completed"
echo "Package: ${ARCHIVE_PATH}"
echo "Checksum: ${CHECKSUM_PATH}"
