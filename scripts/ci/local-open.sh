#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
PROJECT_NAME="${PROJECT_NAME:-opendispatch-ci}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.ci.yml}"
ENV_FILE="${ENV_FILE:-deploy/env/.env.local.ci}"
OPEN_BROWSER=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT_NAME="${2:?}"; shift 2 ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift 2 ;;
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --browser|--open) OPEN_BROWSER=true; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

source "${ROOT_DIR}/scripts/ci/env-utils.sh"
load_dotenv_file "$ENV_FILE"
ADMIN_UI_HTTP_PORT="${ADMIN_UI_HTTP_PORT:-3000}"
ADMIN_URL="http://127.0.0.1:${ADMIN_UI_HTTP_PORT}"

bash scripts/ci/local-status.sh --project "$PROJECT_NAME" --compose-file "$COMPOSE_FILE" --env-file "$ENV_FILE" --urls

if [[ "$OPEN_BROWSER" == "true" ]]; then
  if command -v open >/dev/null 2>&1; then
    open "$ADMIN_URL"
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$ADMIN_URL" >/dev/null 2>&1 || true
  else
    echo "No browser opener found. Open manually: ${ADMIN_URL}"
  fi
fi
