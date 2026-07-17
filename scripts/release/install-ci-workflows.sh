#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMPLATE_DIR="${ROOT_DIR}/ci/github/workflows"
TARGET_DIR="${ROOT_DIR}/.github/workflows"

if [[ ! -d "${TEMPLATE_DIR}" ]]; then
  echo "[ERROR] Missing workflow template directory: ${TEMPLATE_DIR}" >&2
  exit 1
fi

mkdir -p "${TARGET_DIR}"
cp -f "${TEMPLATE_DIR}/opendispatch-ci.yml" "${TARGET_DIR}/opendispatch-ci.yml"
cp -f "${TEMPLATE_DIR}/opendispatch-release-package.yml" "${TARGET_DIR}/opendispatch-release-package.yml"

echo "Installed GitHub workflow files:"
echo "  .github/workflows/opendispatch-ci.yml"
echo "  .github/workflows/opendispatch-release-package.yml"
