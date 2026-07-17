#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

BEST_EFFORT=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --best-effort)
      BEST_EFFORT=true
      shift
      ;;
    -h|--help)
      cat <<'USAGE'
Usage: scripts/ci/clean-artifacts.sh [--best-effort]

Remove generated build outputs, dependency directories, Python caches, and
archive/OS metadata from the OpenDispatch source tree. This script is intended
to be safe before CI/release packaging; it removes only reproducible artifacts.
USAGE
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

log() {
  echo "[clean-artifacts] $*"
}

run_or_best_effort() {
  if "$@"; then
    return 0
  fi
  if [[ "$BEST_EFFORT" == "true" ]]; then
    echo "[clean-artifacts][WARN] Command failed but --best-effort is enabled: $*" >&2
    return 0
  fi
  return 1
}

if [[ -x "$ROOT_DIR/scripts/ci/admin-ui-clean-generated.sh" ]]; then
  log "Cleaning Admin UI generated build outputs"
  run_or_best_effort "$ROOT_DIR/scripts/ci/admin-ui-clean-generated.sh" --best-effort
fi

log "Removing Maven, Node, and front-end generated directories"
find . \
  \( -path './.git' -o -path './.ci-output' \) -prune -o \
  \( \
    -name target -type d -o \
    -name node_modules -type d -o \
    -name .next -type d -o \
    -name .next-ci -type d -o \
    -name .next-local -type d -o \
    -name .next-release -type d -o \
    -name out -type d -o \
    -name dist -type d -o \
    -name coverage -type d -o \
    -name .turbo -type d \
  \) -prune -exec rm -rf {} +

log "Removing Python caches and bytecode"
find . \
  \( -path './.git' -o -path './.ci-output' \) -prune -o \
  \( \
    -name __pycache__ -type d -o \
    -name .pytest_cache -type d -o \
    -name .mypy_cache -type d -o \
    -name .ruff_cache -type d \
  \) -prune -exec rm -rf {} +
find . \
  \( -path './.git' -o -path './.ci-output' \) -prune -o \
  \( -name '*.pyc' -type f -o -name '*.pyo' -type f \) -exec rm -f {} +

log "Removing OS/archive metadata and incremental build files"
find . \
  \( -path './.git' -o -path './.ci-output' \) -prune -o \
  \( \
    -name __MACOSX -type d -o \
    -name '.AppleDouble' -type d \
  \) -prune -exec rm -rf {} +
find . \
  \( -path './.git' -o -path './.ci-output' \) -prune -o \
  \( \
    -name '.DS_Store' -type f -o \
    -name '._*' -type f -o \
    -name tsconfig.tsbuildinfo -type f \
  \) -exec rm -f {} +

log "Artifact cleanup completed. Run 'make test-source-clean' to verify."
