#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_FILE="/tmp/aeg_p12_17_blocked_repositories.txt"
: > "${TMP_FILE}"

# Active project POMs must not declare obsolete SpringSource HTTP repositories.
if grep -R --include='pom.xml' -n \
  "repository.springsource.com\|com.springsource.repository.bundles\|http://repository.springsource.com" \
  "${ROOT_DIR}" > "${TMP_FILE}"; then
  cat "${TMP_FILE}"
  echo "ERROR: Project POM still references obsolete SpringSource HTTP repositories. Maven 3.8+ blocks these via maven-default-http-blocker." >&2
  exit 1
fi

# If SharedUtility source is bundled, it must not declare custom milestone repositories.
if [[ -d "${ROOT_DIR}/shared-utility" ]]; then
  if grep -R --include='pom.xml' -n \
    "<repositories>\|<pluginRepositories>\|repo.spring.io/milestone" \
    "${ROOT_DIR}/shared-utility" > "${TMP_FILE}"; then
    cat "${TMP_FILE}"
    echo "ERROR: Bundled SharedUtility POMs should not declare custom repositories; use Maven default repositories and managed dependency versions." >&2
    exit 1
  fi
fi

# User-level Maven settings can also inject obsolete HTTP repositories even if project POMs are clean.
SETTINGS_FILE="${HOME}/.m2/settings.xml"
if [[ -f "${SETTINGS_FILE}" ]]; then
  if grep -n "repository.springsource.com\|com.springsource.repository.bundles" "${SETTINGS_FILE}" > "${TMP_FILE}"; then
    cat "${TMP_FILE}"
    echo "ERROR: ~/.m2/settings.xml still injects obsolete SpringSource HTTP repositories. Remove those repositories or deactivate that Maven profile." >&2
    exit 1
  fi
fi

echo "P12.17 Maven repository resolution verification passed."
