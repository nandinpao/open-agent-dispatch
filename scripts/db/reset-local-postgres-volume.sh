#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${PROJECT_NAME:-opendispatch}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.local.yml}"
ENV_FILE="${ENV_FILE:-}"
POSTGRES_COMPOSE_SERVICE="${POSTGRES_COMPOSE_SERVICE:-postgres}"
POSTGRES_COMPOSE_VOLUME="${POSTGRES_COMPOSE_VOLUME:-opendispatch-postgres18-data}"
POSTGRES_CONTAINER_DESTINATION="${POSTGRES_CONTAINER_DESTINATION:-/var/lib/postgresql}"

if [[ -z "$ENV_FILE" ]]; then
  if [[ -f deploy/env/.env.local ]]; then
    ENV_FILE="deploy/env/.env.local"
  else
    ENV_FILE="deploy/env/.env.local.example"
  fi
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] Env file not found: $ENV_FILE" >&2
  exit 1
fi

compose_local() {
  docker compose -p "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

log() {
  printf '%s %s\n' '[local-db-reset]' "$*"
}

add_candidate() {
  local value="$1"
  [[ -n "$value" ]] || return 0
  if ! printf '%s\n' "${postgres_volumes:-}" | grep -Fxq "$value"; then
    if [[ -n "${postgres_volumes:-}" ]]; then
      postgres_volumes="${postgres_volumes}"$'\n'"${value}"
    else
      postgres_volumes="$value"
    fi
  fi
}

postgres_volumes=""

# Capture the actual mounted volume before compose down. This is the most
# reliable source when users have changed PROJECT_NAME, COMPOSE_PROJECT_NAME,
# or the Compose volume key over multiple local revisions.
while IFS= read -r container_id; do
  [[ -n "$container_id" ]] || continue
  while IFS= read -r mounted_volume; do
    add_candidate "$mounted_volume"
  done < <(
    docker inspect \
      --format '{{range .Mounts}}{{if and (eq .Type "volume") (eq .Destination "'"${POSTGRES_CONTAINER_DESTINATION}"'" )}}{{.Name}}{{"\n"}}{{end}}{{end}}' \
      "$container_id" 2>/dev/null || true
  )
done < <(
  docker ps -aq \
    --filter "label=com.docker.compose.project=${PROJECT_NAME}" \
    --filter "label=com.docker.compose.service=${POSTGRES_COMPOSE_SERVICE}" \
    2>/dev/null || true
)

log "stopping the local compose project before PostgreSQL volume removal"
compose_local down --remove-orphans >/dev/null 2>&1 || true

# Compose labels the named volume when it is created by this project.
while IFS= read -r labeled_volume; do
  add_candidate "$labeled_volume"
done < <(
  docker volume ls -q \
    --filter "label=com.docker.compose.project=${PROJECT_NAME}" \
    --filter "label=com.docker.compose.volume=${POSTGRES_COMPOSE_VOLUME}" \
    2>/dev/null || true
)

# Deterministic Compose names. Keep several fallbacks because historical local
# revisions and manual docker compose invocations may have created the volume
# with different project names.
add_candidate "${PROJECT_NAME}_${POSTGRES_COMPOSE_VOLUME}"
add_candidate "${POSTGRES_COMPOSE_VOLUME}"

# If the env file defines COMPOSE_PROJECT_NAME and a caller later omits -p,
# Docker Compose may have created another project-scoped volume. Remove that
# local-development candidate too, but never match release/prod names broadly.
env_project_name="$(grep -E '^COMPOSE_PROJECT_NAME=' "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '"'"'"'' || true)"
if [[ -n "$env_project_name" ]]; then
  add_candidate "${env_project_name}_${POSTGRES_COMPOSE_VOLUME}"
fi

existing_volumes=""
while IFS= read -r volume; do
  [[ -n "$volume" ]] || continue
  if docker volume inspect "$volume" >/dev/null 2>&1; then
    if [[ -n "$existing_volumes" ]]; then
      existing_volumes="${existing_volumes}"$'\n'"${volume}"
    else
      existing_volumes="$volume"
    fi
  fi
done <<< "$postgres_volumes"

if [[ -z "$existing_volumes" ]]; then
  log "no local PostgreSQL volume exists for project=${PROJECT_NAME}; nothing to reset"
  log "checked candidates for compose volume=${POSTGRES_COMPOSE_VOLUME}"
  exit 0
fi

while IFS= read -r volume; do
  [[ -n "$volume" ]] || continue
  log "removing PostgreSQL volume name=${volume}"
  docker volume rm "$volume" >/dev/null
done <<< "$existing_volumes"

log "local PostgreSQL volume reset complete"
log "next command: make cd-local"
