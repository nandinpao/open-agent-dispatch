#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
ENV_FILE="${ENV_FILE:-deploy/env/.env.local.ci}"
ALLOW_DOCKER=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --allow-docker) ALLOW_DOCKER=true; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"

ports=(
  "ADMIN_UI_HTTP_PORT:${ADMIN_UI_HTTP_PORT:-3000}"
  "CORE_HTTP_PORT:${CORE_HTTP_PORT:-18080}"
  "NETTY_ADMIN_HTTP_PORT:${NETTY_ADMIN_HTTP_PORT:-18081}"
  "NETTY_TCP_PORT:${NETTY_TCP_PORT:-19090}"
  "NETTY_WS_PORT:${NETTY_WS_PORT:-19091}"
  "POSTGRES_PUBLIC_PORT:${POSTGRES_PUBLIC_PORT:-15432}"
  "REDIS_PUBLIC_PORT:${REDIS_PUBLIC_PORT:-16379}"
  "ADAPTER_WORKER_HTTP_PORT:${ADAPTER_WORKER_HTTP_PORT:-18090}"
  "OTEL_COLLECTOR_GRPC_PORT:${OTEL_COLLECTOR_GRPC_PORT:-14317}"
  "OTEL_COLLECTOR_HTTP_PORT:${OTEL_COLLECTOR_HTTP_PORT:-14318}"
  "OTEL_COLLECTOR_HEALTH_PORT:${OTEL_COLLECTOR_HEALTH_PORT:-13133}"
)

busy=0
for entry in "${ports[@]}"; do
  name="${entry%%:*}"
  port="${entry##*:}"
  holders=""
  if command -v lsof >/dev/null 2>&1; then
    holders="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  elif command -v ss >/dev/null 2>&1; then
    holders="$(ss -ltnp 2>/dev/null | grep ":$port " || true)"
  fi

  if [[ -n "$holders" ]]; then
    if [[ "$ALLOW_DOCKER" == "true" && "$holders" == *Docker* ]]; then
      printf '[OK]   %-24s port %-6s used by Docker\n' "$name" "$port"
    else
      printf '[BUSY] %-24s port %-6s is already in use\n' "$name" "$port"
      echo "$holders" | sed 's/^/       /'
      busy=1
    fi
  else
    printf '[OK]   %-24s port %-6s available\n' "$name" "$port"
  fi
done

exit "$busy"
