#!/usr/bin/env sh
set -eu
ENV_FILE="${1:-.env.production}"
if [ ! -f "$ENV_FILE" ]; then
  echo "Env file not found: $ENV_FILE" >&2
  echo "Create it from .env.production.example first." >&2
  exit 1
fi

python3 ../scripts/release/verify-production-image-refs.py --env-file "$ENV_FILE" --required AI_EVENT_GATEWAY_ADMIN_UI_IMAGE --required NGINX_IMAGE
docker compose -f docker-compose.prod.yml --env-file "$ENV_FILE" up -d
