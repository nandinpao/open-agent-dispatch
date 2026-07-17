#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
ADMIN_UI_DIR="${ROOT_DIR}/ai-event-gateway-admin-ui"
ADMIN_UI_BUILD_DIR="${NEXT_DIST_DIR:-.next}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)
      CI_OUTPUT_DIR="$2"
      shift 2
      ;;
    --admin-ui-dir)
      ADMIN_UI_DIR="$2"
      shift 2
      ;;
    --build-dir)
      ADMIN_UI_BUILD_DIR="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

ADMIN_UI_BUILD_PATH="${ADMIN_UI_DIR}/${ADMIN_UI_BUILD_DIR}"

if [[ ! -f "${ADMIN_UI_BUILD_PATH}/BUILD_ID" ]]; then
  echo "Admin UI production build is missing: ${ADMIN_UI_BUILD_PATH}/BUILD_ID" >&2
  echo "Run npm run build in ai-event-gateway-admin-ui before starting the shared Node runtime container." >&2
  echo "If CI uses an alternate build dir, pass --build-dir or set NEXT_DIST_DIR consistently." >&2
  exit 1
fi

RUNTIME_DIR="${CI_OUTPUT_DIR}/runtime/admin-ui"
rm -rf "${RUNTIME_DIR}/.next"
mkdir -p "${RUNTIME_DIR}"
# The Admin UI runtime directory is mounted read-only in Compose, while a
# writable named volume is mounted at /workspace/admin-ui/node_modules.
# Docker must find the nested mount target already present inside the
# read-only parent mount; otherwise container creation fails before the
# entrypoint can unpack node_modules-prod.tar.gz.
mkdir -p "${RUNTIME_DIR}/node_modules"
cp -a "${ADMIN_UI_BUILD_PATH}" "${RUNTIME_DIR}/.next"

if [[ -d "${ADMIN_UI_DIR}/public" ]]; then
  rm -rf "${RUNTIME_DIR}/public"
  cp -a "${ADMIN_UI_DIR}/public" "${RUNTIME_DIR}/public"
fi

for runtime_file in package.json package-lock.json .npmrc; do
  if [[ -f "${ADMIN_UI_DIR}/${runtime_file}" ]]; then
    cp -p "${ADMIN_UI_DIR}/${runtime_file}" "${RUNTIME_DIR}/${runtime_file}"
  fi
done

if [[ -f "${ADMIN_UI_DIR}/next.config.runtime.mjs" ]]; then
  cp -p "${ADMIN_UI_DIR}/next.config.runtime.mjs" "${RUNTIME_DIR}/next.config.mjs"
elif [[ -f "${ADMIN_UI_DIR}/next.config.mjs" ]]; then
  cp -p "${ADMIN_UI_DIR}/next.config.mjs" "${RUNTIME_DIR}/next.config.mjs"
else
  echo "Admin UI runtime config is missing: next.config.runtime.mjs" >&2
  exit 1
fi

rm -rf "${RUNTIME_DIR}/scripts"
cp -a "${ADMIN_UI_DIR}/scripts" "${RUNTIME_DIR}/scripts"
cp -p "${ROOT_DIR}/scripts/release/admin-ui-runtime-entrypoint.sh" "${RUNTIME_DIR}/scripts/opendispatch-admin-ui-runtime-entrypoint.sh"
chmod +x "${RUNTIME_DIR}/scripts/opendispatch-admin-ui-runtime-entrypoint.sh"

if [[ -d "${ADMIN_UI_DIR}/node_modules" && -f "${ADMIN_UI_DIR}/node_modules/next/package.json" ]]; then
  rm -f "${RUNTIME_DIR}/node_modules-prod.tar.gz"
  (cd "${ADMIN_UI_DIR}" && tar -czf "${RUNTIME_DIR}/node_modules-prod.tar.gz" node_modules)
  echo "Bundled Admin UI node_modules for offline shared Node runtime: ${RUNTIME_DIR}/node_modules-prod.tar.gz"
else
  echo "Admin UI node_modules are missing; runtime container will fail fast without network fallback." >&2
fi

cat > "${RUNTIME_DIR}/runtime.env" <<EOF
ADMIN_UI_BUILD_ID=$(cat "${ADMIN_UI_BUILD_PATH}/BUILD_ID")
ADMIN_UI_BUILD_DIR=${ADMIN_UI_BUILD_DIR}
ADMIN_UI_RUNTIME_DIR=${RUNTIME_DIR}
ADMIN_UI_SOURCE_DIR=${ADMIN_UI_DIR}
EOF

echo "Prepared Admin UI production build for shared Node runtime: ${RUNTIME_DIR}/.next"
