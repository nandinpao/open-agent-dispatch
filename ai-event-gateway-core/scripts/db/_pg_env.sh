#!/usr/bin/env bash
# Shared PostgreSQL environment resolver for local/dev/prod Flyway and schema checks.
set -euo pipefail

parse_pg_single_url() {
  local url="${PG_SINGLE_URL:-}"
  [[ -n "$url" ]] || return 0
  local rest="${url#jdbc:postgresql://}"
  [[ "$rest" != "$url" ]] || return 0
  local hostport="${rest%%/*}"
  local dbpart="${rest#*/}"
  dbpart="${dbpart%%\?*}"
  if [[ -z "${PGHOST:-}" ]]; then export PGHOST="${hostport%%:*}"; fi
  if [[ -z "${PGPORT:-}" && "$hostport" == *:* ]]; then export PGPORT="${hostport##*:}"; fi
  if [[ -z "${PGDATABASE:-}" ]]; then export PGDATABASE="$dbpart"; fi
}

pg_jdbc_url() {
  echo "${PG_SINGLE_URL:-jdbc:postgresql://${PGHOST:-127.0.0.1}:${PGPORT:-5432}/${PGDATABASE:-aeg_core}}"
}

resolve_pg_env() {
  parse_pg_single_url
  : "${PGHOST:=127.0.0.1}"
  : "${PGPORT:=5432}"
  : "${PGDATABASE:=aeg_core}"
  : "${PGUSER:=${PG_SINGLE_USERNAME:-admin}}"
  : "${PGPASSWORD:=${PG_SINGLE_PASSWORD:-123456}}"
  export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
}
