#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/cluster"
CMD="${1:-start}"
APP_RELEASE_VERSION="${APP_RELEASE_VERSION:-1.2.5}"
FORCE_REBUILD_JAR="${FORCE_REBUILD_JAR:-false}"
PACKAGE_WITH_DOCKER="${PACKAGE_WITH_DOCKER:-auto}"
IMAGE_NAME="${IMAGE_NAME:-ai-event-gateway-netty-local:${APP_RELEASE_VERSION}}"
AGENT_ONBOARDING_TOKEN="${AGENT_ONBOARDING_TOKEN:-local-agent-onboarding-token-change-me}"
CLUSTER_INTERNAL_TOKEN="${CLUSTER_INTERNAL_TOKEN:-local-cluster-token-change-me}"
ADMIN_CORS_ALLOWED_ORIGINS="${ADMIN_CORS_ALLOWED_ORIGINS:-http://localhost:3000,http://127.0.0.1:3000}"
GATEWAY_AGENT_AUTHORIZATION_ENABLED="${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-true}"
GATEWAY_AGENT_AUTHORIZATION_BASE_URL="${GATEWAY_AGENT_AUTHORIZATION_BASE_URL:-http://host.docker.internal:18080}"
GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED="${GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED:-true}"
GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED="${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED:-false}"

mkdir -p "${RUNTIME_DIR}"

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
  else
    echo ""
  fi
}

find_app_jar() {
  find "${ROOT_DIR}/gateway-app/target" -maxdepth 1 -type f \
    -name 'gateway-app-*.jar' \
    ! -name '*sources.jar' ! -name '*javadoc.jar' \
    | sort | tail -n 1
}

build_jar_if_needed() {
  local jar=""
  jar="$(find_app_jar || true)"
  if [[ "${FORCE_REBUILD_JAR}" == "true" || -z "${jar}" ]]; then
    echo "Packaging gateway-app jar..."
    (cd "${ROOT_DIR}" && mvn -U -DskipTests -pl gateway-app -am package)
    jar="$(find_app_jar || true)"
  fi
  if [[ -z "${jar}" ]]; then
    echo "ERROR: app jar not found. Run mvn -DskipTests -pl gateway-app -am package first." >&2
    exit 1
  fi
  cp "${jar}" "${RUNTIME_DIR}/app.jar"
  echo "Using jar: ${jar}"
}

generate_docker_assets() {
  cat > "${RUNTIME_DIR}/Dockerfile" <<DOCKERFILE
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY app.jar /app/app.jar
EXPOSE 18081 19090 19091 19100/udp
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
DOCKERFILE

  local peers="gateway-node-001@gateway-node-001:18081:19090:19091:19100,gateway-node-002@gateway-node-002:18081:19090:19091:19100,gateway-node-003@gateway-node-003:18081:19090:19091:19100"
  cat > "${RUNTIME_DIR}/docker-compose.yml" <<COMPOSE
services:
  gateway-node-001:
    image: ${IMAGE_NAME}
    container_name: ai-event-gateway-netty-node-001
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 18081
      GATEWAY_NODE_ID: gateway-node-001
      GATEWAY_ENVIRONMENT: local-docker
      GATEWAY_VERSION: ${APP_RELEASE_VERSION}
      GATEWAY_TCP_PORT: 19090
      GATEWAY_WS_PORT: 19091
      GATEWAY_CLUSTER_ENABLED: "true"
      GATEWAY_CLUSTER_DISCOVERY_MODE: HYBRID
      GATEWAY_CLUSTER_STATIC_PEERS: ${peers}
      GATEWAY_CLUSTER_SYNC_ENABLED: "true"
      GATEWAY_CLUSTER_SYNC_INTERVAL_MS: 2000
      GATEWAY_CLUSTER_SYNC_REQUEST_TIMEOUT_MS: 3000
      GATEWAY_CLUSTER_REMOTE_STATE_TTL_MS: 15000
      GATEWAY_CLUSTER_UDP_PORT: 19100
      GATEWAY_CLUSTER_BROADCAST_HOST: 255.255.255.255
      GATEWAY_CLUSTER_BROADCAST_PORT: 19100
      GATEWAY_CLUSTER_ANNOUNCE_HOST: gateway-node-001
      GATEWAY_AGENT_AUTHORIZATION_ENABLED: ${GATEWAY_AGENT_AUTHORIZATION_ENABLED}
      GATEWAY_AGENT_AUTHORIZATION_BASE_URL: ${GATEWAY_AGENT_AUTHORIZATION_BASE_URL}
      GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED: ${GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED}
      GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED: ${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED}
      AGENT_AUTH_ENABLED: "true"
      AGENT_ONBOARDING_TOKEN: ${AGENT_ONBOARDING_TOKEN}
      CLUSTER_INTERNAL_TOKEN: ${CLUSTER_INTERNAL_TOKEN}
      ADMIN_CORS_ALLOWED_ORIGINS: ${ADMIN_CORS_ALLOWED_ORIGINS}
    ports:
      - "18081:18081"
      - "19090:19090"
      - "19091:19091"
      - "19100:19100/udp"

  gateway-node-002:
    image: ${IMAGE_NAME}
    container_name: ai-event-gateway-netty-node-002
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 18081
      GATEWAY_NODE_ID: gateway-node-002
      GATEWAY_ENVIRONMENT: local-docker
      GATEWAY_VERSION: ${APP_RELEASE_VERSION}
      GATEWAY_TCP_PORT: 19090
      GATEWAY_WS_PORT: 19091
      GATEWAY_CLUSTER_ENABLED: "true"
      GATEWAY_CLUSTER_DISCOVERY_MODE: HYBRID
      GATEWAY_CLUSTER_STATIC_PEERS: ${peers}
      GATEWAY_CLUSTER_SYNC_ENABLED: "true"
      GATEWAY_CLUSTER_SYNC_INTERVAL_MS: 2000
      GATEWAY_CLUSTER_SYNC_REQUEST_TIMEOUT_MS: 3000
      GATEWAY_CLUSTER_REMOTE_STATE_TTL_MS: 15000
      GATEWAY_CLUSTER_UDP_PORT: 19100
      GATEWAY_CLUSTER_BROADCAST_HOST: 255.255.255.255
      GATEWAY_CLUSTER_BROADCAST_PORT: 19100
      GATEWAY_CLUSTER_ANNOUNCE_HOST: gateway-node-002
      GATEWAY_AGENT_AUTHORIZATION_ENABLED: ${GATEWAY_AGENT_AUTHORIZATION_ENABLED}
      GATEWAY_AGENT_AUTHORIZATION_BASE_URL: ${GATEWAY_AGENT_AUTHORIZATION_BASE_URL}
      GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED: ${GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED}
      GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED: ${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED}
      AGENT_AUTH_ENABLED: "true"
      AGENT_ONBOARDING_TOKEN: ${AGENT_ONBOARDING_TOKEN}
      CLUSTER_INTERNAL_TOKEN: ${CLUSTER_INTERNAL_TOKEN}
      ADMIN_CORS_ALLOWED_ORIGINS: ${ADMIN_CORS_ALLOWED_ORIGINS}
    ports:
      - "18082:18081"
      - "19092:19090"
      - "19093:19091"
      - "19102:19100/udp"

  gateway-node-003:
    image: ${IMAGE_NAME}
    container_name: ai-event-gateway-netty-node-003
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 18081
      GATEWAY_NODE_ID: gateway-node-003
      GATEWAY_ENVIRONMENT: local-docker
      GATEWAY_VERSION: ${APP_RELEASE_VERSION}
      GATEWAY_TCP_PORT: 19090
      GATEWAY_WS_PORT: 19091
      GATEWAY_CLUSTER_ENABLED: "true"
      GATEWAY_CLUSTER_DISCOVERY_MODE: HYBRID
      GATEWAY_CLUSTER_STATIC_PEERS: ${peers}
      GATEWAY_CLUSTER_SYNC_ENABLED: "true"
      GATEWAY_CLUSTER_SYNC_INTERVAL_MS: 2000
      GATEWAY_CLUSTER_SYNC_REQUEST_TIMEOUT_MS: 3000
      GATEWAY_CLUSTER_REMOTE_STATE_TTL_MS: 15000
      GATEWAY_CLUSTER_UDP_PORT: 19100
      GATEWAY_CLUSTER_BROADCAST_HOST: 255.255.255.255
      GATEWAY_CLUSTER_BROADCAST_PORT: 19100
      GATEWAY_CLUSTER_ANNOUNCE_HOST: gateway-node-003
      GATEWAY_AGENT_AUTHORIZATION_ENABLED: ${GATEWAY_AGENT_AUTHORIZATION_ENABLED}
      GATEWAY_AGENT_AUTHORIZATION_BASE_URL: ${GATEWAY_AGENT_AUTHORIZATION_BASE_URL}
      GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED: ${GATEWAY_AGENT_AUTHORIZATION_FAIL_CLOSED}
      GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED: ${GATEWAY_AGENT_AUTHORIZATION_ALLOW_WHEN_DISABLED}
      AGENT_AUTH_ENABLED: "true"
      AGENT_ONBOARDING_TOKEN: ${AGENT_ONBOARDING_TOKEN}
      CLUSTER_INTERNAL_TOKEN: ${CLUSTER_INTERNAL_TOKEN}
      ADMIN_CORS_ALLOWED_ORIGINS: ${ADMIN_CORS_ALLOWED_ORIGINS}
    ports:
      - "18083:18081"
      - "19094:19090"
      - "19095:19091"
      - "19104:19100/udp"
COMPOSE
}

start_docker() {
  local dc
  dc="$(compose_cmd)"
  if [[ -z "${dc}" ]]; then
    echo "ERROR: docker compose is required when PACKAGE_WITH_DOCKER=${PACKAGE_WITH_DOCKER}." >&2
    exit 1
  fi
  build_jar_if_needed
  generate_docker_assets
  echo "Building Docker image ${IMAGE_NAME}..."
  docker build -t "${IMAGE_NAME}" "${RUNTIME_DIR}"
  echo "Starting 3-node local cluster..."
  ${dc} -f "${RUNTIME_DIR}/docker-compose.yml" up -d
  echo "Cluster started. Node URLs: http://localhost:18081, http://localhost:18082, http://localhost:18083"
}

stop_docker() {
  local dc
  dc="$(compose_cmd)"
  if [[ -n "${dc}" && -f "${RUNTIME_DIR}/docker-compose.yml" ]]; then
    ${dc} -f "${RUNTIME_DIR}/docker-compose.yml" down
  else
    docker rm -f ai-event-gateway-netty-node-001 ai-event-gateway-netty-node-002 ai-event-gateway-netty-node-003 2>/dev/null || true
  fi
}

status_docker() {
  docker ps --filter 'name=ai-event-gateway-netty-node-' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' || true
  if command -v curl >/dev/null 2>&1; then
    for port in 18081 18082 18083; do
      echo "--- http://localhost:${port}/api/gateway/status"
      curl -sS "http://localhost:${port}/api/gateway/status" || true
      echo
    done
  fi
}

logs_docker() {
  local dc
  dc="$(compose_cmd)"
  if [[ -n "${dc}" && -f "${RUNTIME_DIR}/docker-compose.yml" ]]; then
    ${dc} -f "${RUNTIME_DIR}/docker-compose.yml" logs -f --tail="${TAIL_LINES:-200}"
  else
    docker logs -f ai-event-gateway-netty-node-001
  fi
}

case "${CMD}" in
  start)
    if [[ "${PACKAGE_WITH_DOCKER}" == "false" ]]; then
      echo "PACKAGE_WITH_DOCKER=false is not implemented for this script. Use scripts/run-netty-local.sh for single-node local run." >&2
      exit 2
    fi
    start_docker
    ;;
  stop) stop_docker ;;
  restart) stop_docker; start_docker ;;
  status) status_docker ;;
  logs) logs_docker ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|logs}" >&2
    exit 2
    ;;
esac
