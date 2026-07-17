#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSION="${VERSION:-local}"
IMAGE_NAME="${IMAGE_NAME:-ai-event-gateway-netty}"
SKIP_TESTS="${SKIP_TESTS:-true}"

cd "${PROJECT_ROOT}"

MVN_ARGS=(-U -f pom.xml -pl gateway-app -am package)
if [[ "${SKIP_TESTS}" == "true" ]]; then
  MVN_ARGS+=(-DskipTests)
fi

mvn "${MVN_ARGS[@]}"
docker build -t "${IMAGE_NAME}:${VERSION}" -f gateway-app/Dockerfile .
