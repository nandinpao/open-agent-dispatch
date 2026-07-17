#!/usr/bin/env bash
# Shared Maven helpers for local Flyway scripts.
#
# Why this exists:
# Running the Flyway Maven plugin from ai-event-gateway-core-app/pom.xml alone
# needs the internal com.opensocket modules to be resolvable from ~/.m2. In a
# freshly extracted source tree those reactor artifacts are not installed yet,
# so Maven tries remote resolution and fails through maven-default-http-blocker.
# Build/install the required reactor closure first, then run the Flyway goal only
# from the app module.

set -euo pipefail

maven_fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

ensure_core_reactor_root() {
  local root_dir="$1"
  [[ -f "${root_dir}/pom.xml" ]] || maven_fail "Missing root pom.xml: ${root_dir}/pom.xml"
  grep -q "<artifactId>ai-event-gateway-core-parent</artifactId>" "${root_dir}/pom.xml" \
    || maven_fail "${root_dir}/pom.xml is not ai-event-gateway-core-parent. Run from the full Core reactor root."
  [[ -f "${root_dir}/ai-event-gateway-core-app/pom.xml" ]] \
    || maven_fail "Missing ai-event-gateway-core-app/pom.xml under ${root_dir}. Re-extract the full Core zip."
}

clean_opensocket_failed_resolution_cache() {
  local m2_base="${HOME}/.m2/repository/com/opensocket"
  [[ -d "${m2_base}" ]] || return 0
  find "${m2_base}" -name "*.lastUpdated" -delete 2>/dev/null || true
}

ensure_reactor_artifacts_installed_for_app() {
  local root_dir="$1"
  ensure_core_reactor_root "${root_dir}"

  if [[ "${MAVEN_REACTOR_PREINSTALL_ENABLED:-true}" != "true" ]]; then
    echo "[WARN] MAVEN_REACTOR_PREINSTALL_ENABLED=false; skipping reactor preinstall before Flyway." >&2
    return 0
  fi

  echo "[INFO] Ensuring Core reactor artifacts are installed locally before running Flyway Maven plugin."
  echo "[INFO] This prevents Maven from resolving internal com.opensocket modules from remote repositories."
  clean_opensocket_failed_resolution_cache

  (
    cd "${root_dir}"
    mvn ${MAVEN_BATCH_ARGS:--q} -U -f pom.xml \
      -pl ai-event-gateway-core-app -am \
      -DskipTests \
      -Dmaven.test.skip=true \
      -Dspring-boot.skip=true \
      install
  )
}
