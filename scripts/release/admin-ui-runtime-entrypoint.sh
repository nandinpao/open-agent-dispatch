#!/usr/bin/env sh
set -eu

APP_DIR="${ADMIN_UI_APP_DIR:-/workspace/admin-ui}"
NODE_MODULES_DIR="${ADMIN_UI_NODE_MODULES_DIR:-${APP_DIR}/node_modules}"
DEPS_ARCHIVE="${ADMIN_UI_DEPS_ARCHIVE:-${APP_DIR}/node_modules-prod.tar.gz}"
ALLOW_NPM_CI="${ADMIN_UI_ALLOW_NPM_CI:-true}"
PORT_VALUE="${PORT:-3000}"
HOST_VALUE="${HOSTNAME:-0.0.0.0}"

log() { printf '%s\n' "[admin-ui-runtime] $*"; }
fail() { printf '%s\n' "[admin-ui-runtime][ERROR] $*" >&2; exit 1; }
truthy() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

cd "${APP_DIR}"

[ -f ".next/BUILD_ID" ] || fail "Missing Next.js production build: ${APP_DIR}/.next/BUILD_ID"
[ -f "package.json" ] || fail "Missing package.json under ${APP_DIR}"
[ -f "package-lock.json" ] || fail "Missing package-lock.json under ${APP_DIR}"

if [ -f "${NODE_MODULES_DIR}/next/package.json" ] || [ -x "${NODE_MODULES_DIR}/.bin/next" ]; then
  log "Using existing production node_modules volume."
elif [ -f "${DEPS_ARCHIVE}" ]; then
  log "Installing Admin UI production dependencies from bundled archive: ${DEPS_ARCHIVE}"
  mkdir -p "${NODE_MODULES_DIR}"
  # The archive contains a top-level node_modules directory. Extract it into
  # the application directory so it lands on the writable node_modules volume
  # mounted at ${APP_DIR}/node_modules, while the rest of ${APP_DIR} remains
  # read-only.
  tar -xzf "${DEPS_ARCHIVE}" -C "${APP_DIR}"
else
  if truthy "${ALLOW_NPM_CI}"; then
    log "Bundled production dependencies not found; falling back to npm ci --omit=dev."
    npm ci --omit=dev --no-audit --no-fund
  else
    fail "No bundled production dependencies found and ADMIN_UI_ALLOW_NPM_CI=false. Build the release with --include-admin-runtime-deps or allow npm ci."
  fi
fi

[ -f "${NODE_MODULES_DIR}/next/package.json" ] || fail "Next.js package is missing after dependency preparation."
[ -x "${NODE_MODULES_DIR}/.bin/next" ] || fail "Next.js CLI is missing after dependency preparation."

if [ -f "${APP_DIR}/scripts/wait-for-core-backend.mjs" ]; then
  log "Verifying Core authentication connectivity before starting Next.js."
  node "${APP_DIR}/scripts/wait-for-core-backend.mjs"
fi

log "Starting Admin UI on ${HOST_VALUE}:${PORT_VALUE}."
exec npm run start -- -p "${PORT_VALUE}" -H "${HOST_VALUE}"
