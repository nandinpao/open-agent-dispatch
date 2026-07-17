#!/usr/bin/env bash
set -euo pipefail

REQUIRED_JAVA_MAJOR="${OPENDISPATCH_REQUIRED_JAVA_MAJOR:-25}"
REQUIRED_MAVEN_MAJOR="${OPENDISPATCH_REQUIRED_MAVEN_MAJOR:-3}"
REQUIRED_MAVEN_MINOR="${OPENDISPATCH_REQUIRED_MAVEN_MINOR:-9}"
# Admin UI package.json supports Node >=18.18.0 and npm >=9.0.0.
# Node 22 / npm 10 remain the recommended CI/release baseline, but local
# verification must not fail on the supported LTS floor.
REQUIRED_NODE_MAJOR="${OPENDISPATCH_REQUIRED_NODE_MAJOR:-18}"
REQUIRED_NODE_MINOR="${OPENDISPATCH_REQUIRED_NODE_MINOR:-18}"
REQUIRED_NPM_MAJOR="${OPENDISPATCH_REQUIRED_NPM_MAJOR:-9}"
REQUIRED_NPM_MINOR="${OPENDISPATCH_REQUIRED_NPM_MINOR:-0}"
RECOMMENDED_NODE_MAJOR="${OPENDISPATCH_RECOMMENDED_NODE_MAJOR:-22}"
RECOMMENDED_NPM_MAJOR="${OPENDISPATCH_RECOMMENDED_NPM_MAJOR:-10}"
STRICT_RECOMMENDED_NODE="${OPENDISPATCH_STRICT_RECOMMENDED_NODE:-false}"
CHECK_DOCKER="${OPENDISPATCH_CHECK_DOCKER:-true}"
SOFT=false

usage() {
  cat <<USAGE
Usage: $0 [--soft] [--skip-docker]

Validate the OpenDispatch development/release toolchain:
  Java ${REQUIRED_JAVA_MAJOR}, Maven ${REQUIRED_MAVEN_MAJOR}.${REQUIRED_MAVEN_MINOR}+, Node ${REQUIRED_NODE_MAJOR}.${REQUIRED_NODE_MINOR}+ (recommended ${RECOMMENDED_NODE_MAJOR}), npm ${REQUIRED_NPM_MAJOR}.${REQUIRED_NPM_MINOR}+ (recommended ${RECOMMENDED_NPM_MAJOR}), Docker Compose v2.

Options:
  --soft          Print failures but exit 0. Useful for documentation/preflight reports.
  --skip-docker   Do not require Docker or Docker Compose.

Environment:
  OPENDISPATCH_STRICT_RECOMMENDED_NODE=true  Require the recommended Node/npm majors.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --soft) SOFT=true; shift ;;
    --skip-docker) CHECK_DOCKER=false; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

failures=()
warnings=()

add_failure() {
  failures+=("$1")
}

add_warning() {
  warnings+=("$1")
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    add_failure "Missing required command: ${cmd}"
    return 1
  fi
  return 0
}

version_number() {
  sed -E 's/[^0-9.].*$//' <<<"$1"
}

major_of() {
  cut -d. -f1 <<<"$(version_number "$1")"
}

minor_of() {
  local value
  value="$(cut -d. -f2 <<<"$(version_number "$1")")"
  if [[ -z "$value" ]]; then
    echo 0
  else
    echo "$value"
  fi
}

version_at_least_major_minor() {
  local version="$1" required_major="$2" required_minor="$3"
  local major minor
  major="$(major_of "$version")"
  minor="$(minor_of "$version")"
  [[ -n "$major" && -n "$minor" ]] || return 1
  if (( major > required_major )); then
    return 0
  fi
  if (( major == required_major && minor >= required_minor )); then
    return 0
  fi
  return 1
}

java_major() {
  local raw version major
  raw="$(java -version 2>&1 | head -n 1)"
  version="$(sed -E 's/.*version "([^"]+)".*/\1/' <<<"$raw")"
  major="$(major_of "$version")"
  if [[ "$major" == "1" ]]; then
    cut -d. -f2 <<<"$version"
  else
    echo "$major"
  fi
}

if require_cmd java; then
  JAVA_MAJOR="$(java_major || true)"
  if [[ -z "${JAVA_MAJOR}" || "${JAVA_MAJOR}" -ne "${REQUIRED_JAVA_MAJOR}" ]]; then
    add_failure "Java ${REQUIRED_JAVA_MAJOR} is required; detected major=${JAVA_MAJOR:-unknown}."
  fi
fi

if require_cmd mvn; then
  MAVEN_VERSION="$(mvn -version 2>/dev/null | head -n 1 | awk '{print $3}')"
  if ! version_at_least_major_minor "$MAVEN_VERSION" "$REQUIRED_MAVEN_MAJOR" "$REQUIRED_MAVEN_MINOR"; then
    add_failure "Maven ${REQUIRED_MAVEN_MAJOR}.${REQUIRED_MAVEN_MINOR}+ is required; detected ${MAVEN_VERSION:-unknown}."
  fi
fi

if require_cmd node; then
  NODE_VERSION="$(node -v | sed 's/^v//')"
  NODE_MAJOR="$(major_of "$NODE_VERSION")"
  if ! version_at_least_major_minor "$NODE_VERSION" "$REQUIRED_NODE_MAJOR" "$REQUIRED_NODE_MINOR"; then
    add_failure "Node ${REQUIRED_NODE_MAJOR}.${REQUIRED_NODE_MINOR}+ is required; detected ${NODE_VERSION:-unknown}."
  elif [[ "${STRICT_RECOMMENDED_NODE}" == "true" && "${NODE_MAJOR}" -ne "${RECOMMENDED_NODE_MAJOR}" ]]; then
    add_failure "Node ${RECOMMENDED_NODE_MAJOR} is required when OPENDISPATCH_STRICT_RECOMMENDED_NODE=true; detected ${NODE_VERSION:-unknown}."
  elif [[ "${NODE_MAJOR}" -ne "${RECOMMENDED_NODE_MAJOR}" ]]; then
    add_warning "Node ${NODE_VERSION} is supported, but CI/release images use Node ${RECOMMENDED_NODE_MAJOR}."
  fi
fi

if require_cmd npm; then
  NPM_VERSION="$(npm -v)"
  NPM_MAJOR="$(major_of "$NPM_VERSION")"
  if ! version_at_least_major_minor "$NPM_VERSION" "$REQUIRED_NPM_MAJOR" "$REQUIRED_NPM_MINOR"; then
    add_failure "npm ${REQUIRED_NPM_MAJOR}.${REQUIRED_NPM_MINOR}+ is required; detected ${NPM_VERSION:-unknown}."
  elif [[ "${STRICT_RECOMMENDED_NODE}" == "true" && "${NPM_MAJOR}" -ne "${RECOMMENDED_NPM_MAJOR}" ]]; then
    add_failure "npm ${RECOMMENDED_NPM_MAJOR} is required when OPENDISPATCH_STRICT_RECOMMENDED_NODE=true; detected ${NPM_VERSION:-unknown}."
  elif [[ "${NPM_MAJOR}" -ne "${RECOMMENDED_NPM_MAJOR}" ]]; then
    add_warning "npm ${NPM_VERSION} is supported, but CI/release images use npm ${RECOMMENDED_NPM_MAJOR}."
  fi
fi

if [[ "${CHECK_DOCKER}" == "true" ]]; then
  if require_cmd docker; then
    if ! docker compose version >/dev/null 2>&1; then
      add_failure "Docker Compose v2 plugin is required."
    fi
  fi
fi

if [[ ${#warnings[@]} -gt 0 ]]; then
  printf '[WARN] OpenDispatch toolchain check warnings:\n' >&2
  printf ' - %s\n' "${warnings[@]}" >&2
fi

if [[ ${#failures[@]} -gt 0 ]]; then
  if [[ "${SOFT}" == "true" ]]; then
    printf '[WARN] OpenDispatch toolchain check found issues in soft mode:\n' >&2
    printf ' - %s\n' "${failures[@]}" >&2
    exit 0
  fi
  printf '[FAIL] OpenDispatch toolchain check failed:\n' >&2
  printf ' - %s\n' "${failures[@]}" >&2
  exit 1
fi

echo "OpenDispatch toolchain check passed."
