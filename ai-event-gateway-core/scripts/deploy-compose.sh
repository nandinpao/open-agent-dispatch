#!/usr/bin/env sh
set -eu
ENV_FILE="${1:-.env.production}"
if [ ! -f "$ENV_FILE" ]; then
  echo "Env file not found: $ENV_FILE" >&2
  echo "Create it from .env.production.example first." >&2
  exit 1
fi

docker compose -f docker-compose.prod.yml --env-file "$ENV_FILE" up -d --build
