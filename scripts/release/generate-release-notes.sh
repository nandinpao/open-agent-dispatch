#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${VERSION:-local}"
OUTPUT="${OUTPUT:-${ROOT_DIR}/.ci-output/release-notes-${VERSION}.md}"
mkdir -p "$(dirname "${OUTPUT}")"
{
  echo "# OpenDispatch Release Notes - ${VERSION}"
  echo
  echo "Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## Included delivery summaries"
  echo
  find "${ROOT_DIR}" -maxdepth 1 -type f \( -name 'P*_DELIVERY_SUMMARY.md' -o -name '*_FIX_SUMMARY.md' \) | sort | while read -r file; do
    name="$(basename "${file}")"
    first_heading="$(grep -m 1 '^#' "${file}" 2>/dev/null | sed 's/^#\{1,6\} *//' || true)"
    if [[ -n "${first_heading}" ]]; then
      echo "- ${name}: ${first_heading}"
    else
      echo "- ${name}"
    fi
  done
  echo
  echo "## Runtime policy"
  echo
  echo "- Local/release packages do not build per-application Docker images."
  echo "- Core and Netty run on shared Java 25 runtime images with mounted jars."
  echo "- Admin UI runs on a shared Node 22 runtime image with mounted Next.js build assets."
  echo "- PostgreSQL uses 18-alpine and Redis uses 8-alpine by default."
} > "${OUTPUT}"
echo "Release notes written: ${OUTPUT}"
