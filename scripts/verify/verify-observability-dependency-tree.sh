#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/.ci-output/dependency-policy"
mkdir -p "${OUTPUT_DIR}"

if ! command -v mvn >/dev/null 2>&1; then
  echo "[FAIL] Maven is required for the observability dependency-tree gate." >&2
  exit 1
fi

INCLUDES="org.springframework.boot:spring-boot-starter-opentelemetry,io.micrometer:micrometer-tracing-bridge-otel,io.micrometer:micrometer-tracing-bridge-brave,org.springframework.cloud:spring-cloud-starter-sleuth,org.springframework.cloud:spring-cloud-sleuth-*,io.zipkin.reporter2:zipkin-reporter-brave,org.springframework.boot:spring-boot-starter-zipkin,io.opentelemetry:opentelemetry-exporter-zipkin"
CORE_TREE="${OUTPUT_DIR}/core-observability-dependency-tree.txt"
NETTY_TREE="${OUTPUT_DIR}/netty-observability-dependency-tree.txt"
rm -f "${CORE_TREE}" "${NETTY_TREE}"

mvn -B -ntp -Dstyle.color=never -f "${ROOT_DIR}/ai-event-gateway-core/pom.xml" \
  -pl control-plane-app,adapter-worker-app -am \
  dependency:tree \
  -Dverbose \
  -Dincludes="${INCLUDES}" | tee "${CORE_TREE}"

mvn -B -ntp -Dstyle.color=never -f "${ROOT_DIR}/ai-event-gateway-netty/pom.xml" \
  -pl gateway-app -am \
  dependency:tree \
  -Dverbose \
  -Dincludes="${INCLUDES}" | tee "${NETTY_TREE}"

COMBINED="${OUTPUT_DIR}/observability-dependency-tree.txt"
cat "${CORE_TREE}" "${NETTY_TREE}" > "${COMBINED}"

python3 "${SCRIPT_DIR}/verify-observability-resolved-tree.py" "${CORE_TREE}" "${NETTY_TREE}"
python3 "${SCRIPT_DIR}/verify-observability-dependency-policy.py"
echo "P1-A resolved OpenTelemetry dependency-tree gate passed. Report: ${COMBINED}"
