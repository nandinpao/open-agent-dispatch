#!/usr/bin/env bash
# PostgreSQL client runner for environments where PostgreSQL runs in Docker.
# It intentionally supports host psql, docker exec into an existing PostgreSQL
# container, or a disposable postgres client container. Flyway remains the schema
# lifecycle owner; this helper is only for verify/info SQL queries.
set -euo pipefail

pg_client_host_for_container() {
  local host="${PGHOST:-127.0.0.1}"
  if [[ "${host}" == "127.0.0.1" || "${host}" == "localhost" ]]; then
    echo "${POSTGRES_DOCKER_CLIENT_HOST:-host.docker.internal}"
  else
    echo "${host}"
  fi
}

run_psql_file() {
  local file="$1"
  [[ -f "${file}" ]] || return 0
  local mode="${POSTGRES_CLIENT_MODE:-auto}"
  local image="${POSTGRES_CLIENT_IMAGE:-postgres:18-alpine}"
  local docker_network="${POSTGRES_CLIENT_DOCKER_NETWORK:-}"
  local container="${POSTGRES_CONTAINER_NAME:-${POSTGRES_DOCKER_CONTAINER:-}}"

  case "${mode}" in
    auto)
      if command -v psql >/dev/null 2>&1; then
        mode="local"
      elif [[ -n "${container}" ]] && command -v docker >/dev/null 2>&1; then
        mode="docker-exec"
      elif command -v docker >/dev/null 2>&1; then
        mode="docker-run"
      else
        echo "Missing PostgreSQL client. Install psql or Docker, or set POSTGRES_CLIENT_MODE=docker-exec with POSTGRES_CONTAINER_NAME." >&2
        exit 1
      fi
      ;;
    local|docker-exec|docker-run) ;;
    *) echo "Unsupported POSTGRES_CLIENT_MODE='${mode}'. Use auto, local, docker-exec, or docker-run." >&2; exit 1 ;;
  esac

  echo "Running SQL via PostgreSQL client mode '${mode}': ${file}"
  case "${mode}" in
    local)
      command -v psql >/dev/null 2>&1 || { echo "POSTGRES_CLIENT_MODE=local requires psql on host." >&2; exit 1; }
      PGPASSWORD="${PGPASSWORD}" psql -v ON_ERROR_STOP=1 -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -f "${file}"
      ;;
    docker-exec)
      [[ -n "${container}" ]] || { echo "POSTGRES_CLIENT_MODE=docker-exec requires POSTGRES_CONTAINER_NAME." >&2; exit 1; }
      command -v docker >/dev/null 2>&1 || { echo "POSTGRES_CLIENT_MODE=docker-exec requires docker on host." >&2; exit 1; }
      docker exec -i \
        -e PGPASSWORD="${PGPASSWORD}" \
        "${container}" \
        psql -v ON_ERROR_STOP=1 -U "${PGUSER}" -d "${PGDATABASE}" -f - < "${file}"
      ;;
    docker-run)
      command -v docker >/dev/null 2>&1 || { echo "POSTGRES_CLIENT_MODE=docker-run requires docker on host." >&2; exit 1; }
      local client_host
      client_host="$(pg_client_host_for_container)"
      local network_args=()
      if [[ -n "${docker_network}" ]]; then
        network_args=(--network "${docker_network}")
      fi
      docker run --rm -i \
        "${network_args[@]}" \
        -e PGPASSWORD="${PGPASSWORD}" \
        "${image}" \
        psql -v ON_ERROR_STOP=1 -h "${client_host}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -f - < "${file}"
      ;;
  esac
}

run_psql_query() {
  local query="$1"
  local mode="${POSTGRES_CLIENT_MODE:-auto}"
  local tmp
  tmp="$(mktemp)"
  printf '%s\n' "${query}" > "${tmp}"
  run_psql_file "${tmp}"
  rm -f "${tmp}"
}
